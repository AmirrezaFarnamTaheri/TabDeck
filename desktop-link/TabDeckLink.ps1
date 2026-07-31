#requires -Version 7.2
<#
TabDeck Desktop Link
A Windows WPF companion for user-authorized Android Debug Bridge + Chromium DevTools sessions.
It never reads browser profile databases and never enables debugging on the user's behalf.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $IsWindows) { throw 'TabDeck Desktop Link requires Windows.' }
if ([Threading.Thread]::CurrentThread.ApartmentState -ne 'STA') {
    $args = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-Sta', '-File', $PSCommandPath)
    Start-Process -FilePath (Get-Process -Id $PID).Path -ArgumentList $args
    exit
}

Add-Type -AssemblyName PresentationFramework, PresentationCore, WindowsBase

function Find-Adb {
    $candidates = @(
        (Get-Command adb.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue),
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'),
        $(if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe' }),
        $(if ($env:ANDROID_SDK_ROOT) { Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe' })
    ) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -Unique
    if (-not $candidates) { throw 'adb.exe was not found. Install Android Platform Tools and enable USB debugging on the device.' }
    return $candidates[0]
}

$script:Adb = Find-Adb
$script:Tabs = [System.Collections.ObjectModel.ObservableCollection[object]]::new()
$script:SocketMap = @{}
$script:BridgePort = $null
$script:SourceSessionId = ""
$script:ForwardSerial = $null
$script:OwnedForwards = [Collections.Generic.List[object]]::new()
$script:StateDirectory = Join-Path $env:LOCALAPPDATA 'TabDeck'
$script:ForwardStatePath = Join-Path $script:StateDirectory 'desktop-link-forwards.json'
$script:TabsView = $null


function Get-Sha256Hex {
    param([Parameter(Mandatory)][string]$Value)
    $bytes = [Text.Encoding]::UTF8.GetBytes($Value)
    $hash = [Security.Cryptography.SHA256]::HashData($bytes)
    return [Convert]::ToHexString($hash).ToLowerInvariant()
}

function Test-SupportedDevToolsSocket {
    param([Parameter(Mandatory)][string]$Socket)
    return $Socket -match '(?i)(chrome|chromium|brave|edge|opera|vivaldi|sbrowser).*devtools_remote'
}
function Get-FreeTcpPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return ([Net.IPEndPoint]$listener.LocalEndpoint).Port } finally { $listener.Stop() }
}

function Invoke-Adb {
    param([Parameter(Mandatory)][string[]]$Arguments, [switch]$AllowFailure)
    $psi = [Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $script:Adb
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    foreach ($arg in $Arguments) { [void]$psi.ArgumentList.Add($arg) }
    $process = [Diagnostics.Process]::Start($psi)
    if (-not $process.WaitForExit(20000)) {
        try { $process.Kill($true) } catch { }
        throw 'adb timed out after 20 seconds.'
    }
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    if ($process.ExitCode -ne 0 -and -not $AllowFailure) {
        throw "adb failed ($($process.ExitCode)): $stderr"
    }
    [pscustomobject]@{ ExitCode = $process.ExitCode; StdOut = $stdout.Trim(); StdErr = $stderr.Trim() }
}

function Get-DeviceSerials {
    $lines = (Invoke-Adb @('devices', '-l')).StdOut -split "`r?`n"
    foreach ($line in $lines | Select-Object -Skip 1) {
        if ($line -match '^(\S+)\s+device\b') { $matches[1] }
    }
}

function Get-DevToolsSockets {
    param([string]$Serial)
    $result = Invoke-Adb @('-s', $Serial, 'shell', 'cat', '/proc/net/unix') -AllowFailure
    if ($result.ExitCode -ne 0) { return @() }
    $sockets = foreach ($line in ($result.StdOut -split "`r?`n")) {
        if ($line -match '@?([^\s]*devtools_remote[^\s]*)\s*$') { $matches[1].TrimStart('@') }
    }
    @($sockets | Where-Object { $_ } | Sort-Object -Unique)
}

function Save-ForwardState {
    if ($script:OwnedForwards.Count -eq 0) {
        Remove-Item -LiteralPath $script:ForwardStatePath -Force -ErrorAction SilentlyContinue
        return
    }
    [void](New-Item -ItemType Directory -Path $script:StateDirectory -Force)
    $temporary = "$($script:ForwardStatePath).tmp"
    @($script:OwnedForwards) | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $temporary -Encoding utf8
    Move-Item -LiteralPath $temporary -Destination $script:ForwardStatePath -Force
}

function Register-OwnedForward {
    param([string]$Serial, [int]$LocalPort)
    if (-not ($script:OwnedForwards | Where-Object { $_.Serial -eq $Serial -and $_.LocalPort -eq $LocalPort })) {
        $script:OwnedForwards.Add([pscustomobject]@{ Serial = $Serial; LocalPort = $LocalPort })
        Save-ForwardState
    }
}

function Add-Forward {
    param([string]$Serial, [int]$LocalPort, [string]$Remote)
    [void](Invoke-Adb @('-s', $Serial, 'forward', "tcp:$LocalPort", $Remote))
    Register-OwnedForward $Serial $LocalPort
}

function Remove-Forward {
    param([string]$Serial, [int]$LocalPort)
    [void](Invoke-Adb @('-s', $Serial, 'forward', '--remove', "tcp:$LocalPort") -AllowFailure)
    $remaining = @($script:OwnedForwards | Where-Object { -not ($_.Serial -eq $Serial -and $_.LocalPort -eq $LocalPort) })
    $script:OwnedForwards.Clear()
    foreach ($entry in $remaining) { $script:OwnedForwards.Add($entry) }
    Save-ForwardState
}

function Recover-StaleForwards {
    if (-not (Test-Path -LiteralPath $script:ForwardStatePath)) { return }
    try {
        $stale = @(Get-Content -LiteralPath $script:ForwardStatePath -Raw | ConvertFrom-Json)
        foreach ($entry in $stale) {
            if ($entry.Serial -and $entry.LocalPort) {
                [void](Invoke-Adb @('-s', [string]$entry.Serial, 'forward', '--remove', "tcp:$([int]$entry.LocalPort)") -AllowFailure)
            }
        }
    } finally {
        Remove-Item -LiteralPath $script:ForwardStatePath -Force -ErrorAction SilentlyContinue
        $script:OwnedForwards.Clear()
    }
}

function Invoke-JsonEndpoint {
    param(
        [Parameter(Mandatory)][string]$Uri,
        [ValidateSet('GET','PUT','POST')][string]$Method = 'GET',
        [hashtable]$Headers,
        [object]$Body
    )
    $params = @{ Uri = $Uri; Method = $Method; TimeoutSec = 10; ErrorAction = 'Stop' }
    if ($Headers) { $params.Headers = $Headers }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json'
        $params.Body = ($Body | ConvertTo-Json -Depth 8 -Compress)
    }
    Invoke-RestMethod @params
}

function Get-CanonicalUrl {
    param([string]$Url)
    try {
        $uri = [Uri]$Url
        $builder = [UriBuilder]$uri
        $builder.Fragment = ''
        $host = $builder.Host.ToLowerInvariant()
        if ($host.StartsWith('www.')) { $builder.Host = $host.Substring(4) }
        $tracking = @(
            'fbclid','gclid','dclid','msclkid','mc_cid','mc_eid','igshid','ref_src','ref_url',
            'srsltid','mkt_tok','vero_conv','vero_id','_hsenc','_hsmi','oly_anon_id','oly_enc_id',
            'rb_clickid','wickedid'
        )
        $pairs = @()
        foreach ($part in $builder.Query.TrimStart('?').Split('&', [StringSplitOptions]::RemoveEmptyEntries)) {
            $key = [Uri]::UnescapeDataString(($part -split '=', 2)[0])
            if ($key.ToLowerInvariant().StartsWith('utm_') -or $tracking -contains $key.ToLowerInvariant()) { continue }
            $pairs += $part
        }
        $builder.Query = ($pairs | Sort-Object) -join '&'
        $value = $builder.Uri.AbsoluteUri.TrimEnd('/')
        return $value
    } catch { return $Url.Trim() }
}


function Remove-AllTabDeckForwards {
    if (-not $script:ForwardSerial) { return }
    foreach ($oldPort in @($script:SocketMap.Values)) { Remove-Forward $script:ForwardSerial ([int]$oldPort) }
    if ($script:BridgePort) { Remove-Forward $script:ForwardSerial ([int]$script:BridgePort) }
    $script:SocketMap.Clear()
    $script:BridgePort = $null
}

function Refresh-Devices {
    $DeviceBox.Items.Clear()
    foreach ($serial in Get-DeviceSerials) { [void]$DeviceBox.Items.Add($serial) }
    if ($DeviceBox.Items.Count -gt 0) { $DeviceBox.SelectedIndex = 0 }
    Set-Status "Found $($DeviceBox.Items.Count) authorized Android device(s)."
    Update-Summary
}

function Refresh-Tabs {
    $serial = [string]$DeviceBox.SelectedItem
    if (-not $serial) { throw 'Select an authorized Android device.' }
    if ($script:ForwardSerial) {
        foreach ($oldPort in @($script:SocketMap.Values)) { Remove-Forward $script:ForwardSerial ([int]$oldPort) }
        if ($script:BridgePort) { Remove-Forward $script:ForwardSerial ([int]$script:BridgePort) }
    }
    $script:Tabs.Clear()
    $script:SocketMap.Clear()
    $DestinationBox.Items.Clear()

    $discoveredSockets = @(Get-DevToolsSockets $serial)
    $sockets = @($discoveredSockets | Where-Object { Test-SupportedDevToolsSocket $_ })
    $unsupported = @($discoveredSockets | Where-Object { -not (Test-SupportedDevToolsSocket $_) })
    if ($unsupported.Count -gt 0) {
        Set-Status "Ignored $($unsupported.Count) unsupported or unrecognized DevTools socket(s): $($unsupported -join ', ')"
    }
    if (-not $sockets) {
        throw 'No Chromium DevTools socket is visible. On the device, enable Developer options + USB debugging, open the target Chromium browser, and enable USB debugging / remote inspection where the browser requires it.'
    }

    $script:ForwardSerial = $serial
    $script:SourceSessionId = Get-Sha256Hex ("{0}|{1}" -f $serial, (($sockets | Sort-Object) -join '|'))
    foreach ($socket in $sockets) {
        $port = Get-FreeTcpPort
        Add-Forward $serial $port "localabstract:$socket"
        $script:SocketMap[$socket] = $port
        [void]$DestinationBox.Items.Add($socket)
        try {
            $targets = @(Invoke-JsonEndpoint "http://127.0.0.1:$port/json")
            foreach ($target in $targets) {
                if ($target.type -ne 'page' -or $target.url -notmatch '^https?://') { continue }
                $script:Tabs.Add([pscustomobject]@{
                    Selected = $false
                    Title = [string]$target.title
                    Url = [string]$target.url
                    CanonicalUrl = Get-CanonicalUrl ([string]$target.url)
                    Source = $socket
                    TargetId = [string]$target.id
                    LocalPort = $port
                })
            }
        } catch {
            Set-Status "Could not query $socket on port ${port}: $($_.Exception.Message)"
        }
    }
    if ($DestinationBox.Items.Count -gt 0) { $DestinationBox.SelectedIndex = 0 }
    $script:TabsView.Refresh()
    Set-Status "Loaded $($script:Tabs.Count) page targets from $($sockets.Count) browser session(s)."
    Update-Summary
}

function Select-AllTabs {
    param([bool]$Selected)
    $targets = if ($SearchBox.Text.Trim()) { @($script:TabsView) } else { @($script:Tabs) }
    foreach ($tab in $targets) { $tab.Selected = $Selected }
    $Grid.Items.Refresh()
    $verb = if ($Selected) { 'selected' } else { 'cleared' }
    Set-Status "$($targets.Count) visible tab(s) $verb."
    Update-Summary
}

function Get-SelectedTabs {
    return @($script:Tabs | Where-Object Selected)
}

function Pump-Ui {
    $Window.Dispatcher.Invoke([Action]{}, [Windows.Threading.DispatcherPriority]::Background)
}

function Select-Duplicates {
    Select-AllTabs $false
    $groups = $script:Tabs | Group-Object CanonicalUrl | Where-Object Count -gt 1
    foreach ($group in $groups) {
        foreach ($tab in ($group.Group | Select-Object -Skip 1)) { $tab.Selected = $true }
    }
    $Grid.Items.Refresh()
    Set-Status "Selected $(@($script:Tabs | Where-Object Selected).Count) duplicate copies; one survivor per normalized URL remains unselected."
    Update-Summary
}

function Close-SelectedTabs {
    $selected = @(Get-SelectedTabs)
    if (-not $selected) { throw 'Select one or more targets to close.' }
    $answer = [Windows.MessageBox]::Show(
        "Close $($selected.Count) live Android browser tab(s)? This action affects the source browsers immediately.",
        'Confirm close', 'YesNo', 'Warning'
    )
    if ($answer -ne 'Yes') { return }
    $closed = 0
    foreach ($tab in $selected) {
        try {
            [void](Invoke-JsonEndpoint "http://127.0.0.1:$($tab.LocalPort)/json/close/$([Uri]::EscapeDataString($tab.TargetId))" -Method PUT)
            $closed++
        } catch { }
        if ($closed -gt 0 -and $closed % 20 -eq 0) { Set-Status "Closing tabs: $closed/$($selected.Count)"; Pump-Ui }
    }
    Set-Status "Requested closure of $closed/$($selected.Count) selected targets."
    Start-Sleep -Milliseconds 500
    Refresh-Tabs
}

function Test-DestinationTarget {
    param(
        [Parameter(Mandatory)][int]$Port,
        [Parameter(Mandatory)][string]$TargetId,
        [Parameter(Mandatory)][string]$ExpectedUrl
    )
    $expected = Get-CanonicalUrl $ExpectedUrl
    for ($attempt = 0; $attempt -lt 10; $attempt++) {
        try {
            $targets = @(Invoke-JsonEndpoint "http://127.0.0.1:$Port/json")
            $match = $targets | Where-Object {
                ([string]$_.id -eq $TargetId) -and (Get-CanonicalUrl ([string]$_.url) -eq $expected)
            } | Select-Object -First 1
            if ($match) { return $true }
        } catch { }
        Start-Sleep -Milliseconds 150
    }
    return $false
}

function Transfer-SelectedTabs {
    $selected = @(Get-SelectedTabs)
    if (-not $selected) { throw 'Select tabs to transfer.' }
    $destination = [string]$DestinationBox.SelectedItem
    if (-not $destination) { throw 'Choose a destination DevTools socket.' }
    $port = [int]$script:SocketMap[$destination]
    $closeMessage = if ($CloseAfterTransfer.IsChecked) { ' Successfully opened source tabs will then be closed.' } else { '' }
    $answer = [Windows.MessageBox]::Show(
        "Open $($selected.Count) selected tab(s) in $destination?$closeMessage",
        'Confirm transfer', 'YesNo', 'Question'
    )
    if ($answer -ne 'Yes') { return }
    $opened = 0
    $processed = 0
    $verified = [Collections.Generic.List[object]]::new()
    foreach ($tab in $selected) {
        $processed++
        try {
            $encoded = [Uri]::EscapeDataString($tab.Url)
            $created = Invoke-JsonEndpoint "http://127.0.0.1:$port/json/new?$encoded" -Method PUT
            if (-not $created.id -or $created.url -notmatch '^https?://') { throw 'Destination did not confirm a page target.' }
            if (-not (Test-DestinationTarget -Port $port -TargetId ([string]$created.id) -ExpectedUrl $tab.Url)) {
                throw 'Destination target was not observable after creation; source remains open.'
            }
            $opened++
            $verified.Add($tab)
            Start-Sleep -Milliseconds 120
        } catch { }
        if ($processed % 10 -eq 0 -or $processed -eq $selected.Count) {
            Set-Status "Processed tabs: $processed/$($selected.Count) ($opened opened)"
            Pump-Ui
        }
    }
    if ($CloseAfterTransfer.IsChecked) {
        foreach ($tab in ($verified | Where-Object Source -ne $destination)) {
            try { [void](Invoke-JsonEndpoint "http://127.0.0.1:$($tab.LocalPort)/json/close/$([Uri]::EscapeDataString($tab.TargetId))" -Method PUT) } catch { }
        }
    }
    $suffix = if ($CloseAfterTransfer.IsChecked) { ' and requested source closure.' } else { '.' }
    Set-Status "Opened $opened/$($selected.Count) tabs in $destination$suffix"
    Start-Sleep -Milliseconds 500
    Refresh-Tabs
}

function New-BridgeTabPayload {
    param([Parameter(Mandatory)]$Tab, [Parameter(Mandatory)][string]$Serial)
    [ordered]@{
        id = $Tab.TargetId
        title = $Tab.Title
        url = $Tab.Url
        group = $Tab.Source
        deviceId = $Serial
        browser = 'Desktop Link'
    }
}

function Split-BridgeBatches {
    param([Parameter(Mandatory)][object[]]$Tabs, [Parameter(Mandatory)][string]$Serial)
    $targetBytes = 4 * 1024 * 1024
    $batch = [Collections.Generic.List[object]]::new()
    $batchBytes = 0
    foreach ($tab in $Tabs) {
        $entry = New-BridgeTabPayload -Tab $tab -Serial $Serial
        $entryBytes = [Text.Encoding]::UTF8.GetByteCount(($entry | ConvertTo-Json -Depth 4 -Compress)) + 1
        if ($batch.Count -gt 0 -and ($batchBytes + $entryBytes) -gt $targetBytes) {
            Write-Output -NoEnumerate @($batch)
            $batch = [Collections.Generic.List[object]]::new()
            $batchBytes = 0
        }
        $batch.Add($entry)
        $batchBytes += $entryBytes
    }
    if ($batch.Count -gt 0) { Write-Output -NoEnumerate @($batch) }
}

function Push-To-TabDeck {
    $serial = [string]$DeviceBox.SelectedItem
    $selected = @(Get-SelectedTabs)
    if (-not $selected) { throw 'Select tabs to send to TabDeck.' }
    if (-not $TokenBox.Password) { throw 'Paste the bridge token from TabDeck → Capture.' }

    if ($script:BridgePort -and $script:ForwardSerial) { Remove-Forward $script:ForwardSerial ([int]$script:BridgePort) }
    $script:BridgePort = Get-FreeTcpPort
    $script:ForwardSerial = $serial
    Add-Forward $serial $script:BridgePort 'tcp:48721'
    $headers = @{ 'X-TabDeck-Token' = $TokenBox.Password }
    $batches = @(Split-BridgeBatches -Tabs $selected -Serial $serial)
    $received = 0
    $imported = 0
    $batchNumber = 0
    foreach ($tabsBatch in $batches) {
        $batchNumber++
        $payload = [ordered]@{
            browser = 'Desktop Link'
            sourceLabel = 'Windows Desktop Link'
            deviceName = $serial
            sourceSessionId = $script:SourceSessionId
            identityVersion = 1
            tabs = @($tabsBatch)
        }
        try {
            $response = Invoke-JsonEndpoint "http://127.0.0.1:$script:BridgePort/api/v3/import" -Method POST -Headers $headers -Body $payload
            $received += [int]$response.received
            $imported += [int]$response.imported
            Set-Status "Sending batch $batchNumber/$($batches.Count): $imported tabs accepted."
            Pump-Ui
        } catch {
            $message = "Batch $batchNumber/$($batches.Count) failed after $imported of $received tab(s) were already accepted: $($_.Exception.Message)"
            Set-Status $message
            Update-Summary
            throw $message
        }
    }
    Set-Status "TabDeck accepted $imported of $received selected tabs."
    Update-Summary
}

function Export-SelectedTabs {
    $selected = @(Get-SelectedTabs)
    if (-not $selected) { throw 'Select targets to export.' }
    $dialog = [Microsoft.Win32.SaveFileDialog]::new()
    $dialog.Filter = 'TabDeck JSON (*.json)|*.json|URL list (*.txt)|*.txt'
    $dialog.FileName = "TabDeck-Android-tabs-$(Get-Date -Format yyyyMMdd-HHmmss).json"
    if (-not $dialog.ShowDialog()) { return }
    if ($dialog.FileName.EndsWith('.txt', [StringComparison]::OrdinalIgnoreCase)) {
        $selected.Url | Set-Content -LiteralPath $dialog.FileName -Encoding utf8
    } else {
        [ordered]@{
            format = 'tabdeck-desktop-link'
            version = 1
            exportedAt = (Get-Date).ToUniversalTime().ToString('o')
            tabs = @($selected | Select-Object Title, Url, Source, TargetId)
        } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $dialog.FileName -Encoding utf8
    }
    Set-Status "Exported $($selected.Count) targets."
}

function Update-Summary {
    if ($null -ne $DeviceStatusText) {
        $DeviceStatusText.Text = if ($DeviceBox.Items.Count -gt 0) { "$($DeviceBox.Items.Count) device(s) ready" } else { 'No authorized device' }
    }
    if ($null -ne $BrowserStatusText) {
        $BrowserStatusText.Text = if ($script:Tabs.Count -gt 0) { "$($script:Tabs.Count) tabs from $($script:SocketMap.Count) session(s)" } else { 'No tabs loaded' }
    }
    if ($null -ne $SelectionStatusText) {
        $SelectionStatusText.Text = "$(@($script:Tabs | Where-Object Selected).Count) selected"
    }
}

function Set-Status([string]$Message) {
    $StatusText.Text = $Message
    $StatusText.ToolTip = $Message
}

function Invoke-UiAction([scriptblock]$Action) {
    try {
        $Window.Cursor = [Windows.Input.Cursors]::Wait
        & $Action
    } catch {
        Set-Status $_.Exception.Message
        [void][Windows.MessageBox]::Show($_.Exception.Message, 'TabDeck Desktop Link', 'OK', 'Error')
    } finally {
        $Window.Cursor = [Windows.Input.Cursors]::Arrow
    }
}

[xml]$xaml = @'
<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
        xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
        Title="TabDeck Desktop Link — Capture workspace" Width="1240" Height="840" MinWidth="980" MinHeight="660"
        WindowStartupLocation="CenterScreen" Background="#101517" Foreground="#E7EEEC">
  <Window.Resources>
    <Style TargetType="Button">
      <Setter Property="Margin" Value="0,0,8,0"/><Setter Property="Padding" Value="14,9"/>
      <Setter Property="Background" Value="#166B68"/><Setter Property="Foreground" Value="White"/>
      <Setter Property="BorderThickness" Value="0"/><Setter Property="Cursor" Value="Hand"/>
    </Style>
    <Style TargetType="ComboBox"><Setter Property="Margin" Value="0,0,8,0"/><Setter Property="MinWidth" Value="190"/><Setter Property="Padding" Value="8"/></Style>
    <Style TargetType="TextBox"><Setter Property="Padding" Value="8"/></Style>
    <Style TargetType="PasswordBox"><Setter Property="Padding" Value="8"/></Style>
  </Window.Resources>
  <Grid Margin="22">
    <Grid.RowDefinitions><RowDefinition Height="Auto"/><RowDefinition Height="Auto"/><RowDefinition Height="Auto"/><RowDefinition Height="*"/><RowDefinition Height="Auto"/></Grid.RowDefinitions>
    <Grid Grid.Row="0" Margin="0,0,0,16">
      <Grid.ColumnDefinitions><ColumnDefinition Width="*"/><ColumnDefinition Width="Auto"/></Grid.ColumnDefinitions>
      <StackPanel>
        <TextBlock Text="CAPTURE WORKSPACE" Foreground="#6ECAC2" FontWeight="Bold" FontSize="12"/>
        <TextBlock Text="TabDeck Desktop Link" FontSize="30" FontWeight="Bold" Margin="0,2,0,4"/>
        <TextBlock Text="Connect a device, load supported browser sessions, choose tabs, then send the complete selection to TabDeck." Foreground="#A8B5B1" FontSize="14"/>
      </StackPanel>
      <Border Grid.Column="1" Background="#192326" CornerRadius="10" Padding="14,9"><TextBlock Text="ADB + browser DevTools" Foreground="#E2B45C" FontWeight="Bold"/></Border>
    </Grid>

    <Grid Grid.Row="1" Margin="0,0,0,12">
      <Grid.ColumnDefinitions><ColumnDefinition Width="*"/><ColumnDefinition Width="*"/><ColumnDefinition Width="*"/></Grid.ColumnDefinitions>
      <Border Grid.Column="0" Background="#182023" CornerRadius="10" Padding="12" Margin="0,0,8,0"><StackPanel><TextBlock Text="1 · Device" FontWeight="Bold"/><TextBlock x:Name="DeviceStatusText" Text="Checking devices" Foreground="#A8B5B1" Margin="0,4,0,0"/></StackPanel></Border>
      <Border Grid.Column="1" Background="#182023" CornerRadius="10" Padding="12" Margin="0,0,8,0"><StackPanel><TextBlock Text="2 · Browser tabs" FontWeight="Bold"/><TextBlock x:Name="BrowserStatusText" Text="No tabs loaded" Foreground="#A8B5B1" Margin="0,4,0,0"/></StackPanel></Border>
      <Border Grid.Column="2" Background="#182023" CornerRadius="10" Padding="12"><StackPanel><TextBlock Text="3 · Selection" FontWeight="Bold"/><TextBlock x:Name="SelectionStatusText" Text="0 selected" Foreground="#A8B5B1" Margin="0,4,0,0"/></StackPanel></Border>
    </Grid>

    <Border Grid.Row="2" Background="#182023" CornerRadius="12" Padding="14" Margin="0,0,0,12">
      <StackPanel>
        <WrapPanel Margin="0,0,0,10">
          <TextBlock Text="Device" VerticalAlignment="Center" Margin="0,0,8,0" FontWeight="Bold"/>
          <ComboBox x:Name="DeviceBox"/>
          <Button x:Name="RefreshDevicesButton" Content="Refresh devices"/>
          <Button x:Name="LoadTabsButton" Content="Load browser tabs"/>
          <TextBlock Text="Filter" VerticalAlignment="Center" Margin="16,0,8,0" FontWeight="Bold"/>
          <TextBox x:Name="SearchBox" Width="260" Margin="0,0,10,0" ToolTip="Filter by title, URL, or browser session"/>
        </WrapPanel>
        <WrapPanel>
          <Button x:Name="SelectAllButton" Content="Select visible"/>
          <Button x:Name="ClearButton" Content="Clear visible"/>
          <Button x:Name="DedupeButton" Content="Select duplicate copies"/>
          <TextBlock Text="More tools" VerticalAlignment="Center" Margin="16,0,8,0" FontWeight="Bold"/>
          <ComboBox x:Name="DestinationBox" ToolTip="Destination browser session"/>
          <CheckBox x:Name="CloseAfterTransfer" Content="Close source after verified open" VerticalAlignment="Center" Margin="8,0,8,0"/>
          <Button x:Name="TransferButton" Content="Open in another session" Background="#3E5E78"/>
          <Button x:Name="CloseButton" Content="Close selected" Background="#8C3E47"/>
          <Button x:Name="ExportButton" Content="Export selected" Background="#53665F"/>
        </WrapPanel>
      </StackPanel>
    </Border>

    <DataGrid x:Name="Grid" Grid.Row="3" AutoGenerateColumns="False" CanUserAddRows="False" IsReadOnly="False"
              Background="#101517" Foreground="#E7EEEC" RowBackground="#182023" AlternatingRowBackground="#141B1D"
              GridLinesVisibility="Horizontal" BorderBrush="#344247" HeadersVisibility="Column" SelectionMode="Extended">
      <DataGrid.Columns>
        <DataGridCheckBoxColumn Header="Use" Binding="{Binding Selected, Mode=TwoWay, UpdateSourceTrigger=PropertyChanged}" Width="55"/>
        <DataGridTextColumn Header="Title" Binding="{Binding Title}" Width="2*" IsReadOnly="True"/>
        <DataGridTextColumn Header="URL" Binding="{Binding Url}" Width="3*" IsReadOnly="True"/>
        <DataGridTextColumn Header="Browser session" Binding="{Binding Source}" Width="1.5*" IsReadOnly="True"/>
      </DataGrid.Columns>
    </DataGrid>

    <Border Grid.Row="4" Background="#182023" CornerRadius="12" Padding="14" Margin="0,12,0,0">
      <Grid>
        <Grid.ColumnDefinitions><ColumnDefinition Width="Auto"/><ColumnDefinition Width="280"/><ColumnDefinition Width="Auto"/><ColumnDefinition Width="*"/></Grid.ColumnDefinitions>
        <StackPanel><TextBlock Text="4 · Send to TabDeck" FontWeight="Bold"/><TextBlock Text="Token from TabDeck → Capture" Foreground="#A8B5B1" FontSize="11"/></StackPanel>
        <PasswordBox x:Name="TokenBox" Grid.Column="1" Margin="12,0,8,0"/>
        <Button x:Name="PushButton" Grid.Column="2" Content="Send selected tabs" Background="#C78B2C" Foreground="#111111"/>
        <TextBlock x:Name="StatusText" Grid.Column="3" Text="Ready. USB debugging must already be authorized." Foreground="#A8B5B1" VerticalAlignment="Center" TextTrimming="CharacterEllipsis"/>
      </Grid>
    </Border>
  </Grid>
</Window>
'@

$reader = [Xml.XmlNodeReader]::new($xaml)
$Window = [Windows.Markup.XamlReader]::Load($reader)
foreach ($name in @('DeviceBox','DestinationBox','CloseAfterTransfer','SearchBox','Grid','TokenBox','StatusText','DeviceStatusText','BrowserStatusText','SelectionStatusText','RefreshDevicesButton','LoadTabsButton','SelectAllButton','ClearButton','DedupeButton','TransferButton','CloseButton','ExportButton','PushButton')) {
    Set-Variable -Name $name -Value $Window.FindName($name) -Scope Script
}

$script:TabsView = [Windows.Data.CollectionViewSource]::GetDefaultView($script:Tabs)
$script:TabsView.Filter = [Predicate[object]]{
    param($item)
    $query = $SearchBox.Text.Trim()
    if (-not $query) { return $true }
    return ([string]$item.Title).Contains($query, [StringComparison]::OrdinalIgnoreCase) -or
        ([string]$item.Url).Contains($query, [StringComparison]::OrdinalIgnoreCase) -or
        ([string]$item.Source).Contains($query, [StringComparison]::OrdinalIgnoreCase)
}
$Grid.ItemsSource = $script:TabsView
$SearchBox.Add_TextChanged({
    $script:TabsView.Refresh()
    Set-Status "$(@($script:TabsView).Count) of $($script:Tabs.Count) tabs visible."
    Update-Summary
})

$RefreshDevicesButton.Add_Click({ Invoke-UiAction { Refresh-Devices } })
$LoadTabsButton.Add_Click({ Invoke-UiAction { Refresh-Tabs } })
$SelectAllButton.Add_Click({ Select-AllTabs $true })
$ClearButton.Add_Click({ Select-AllTabs $false })
$DedupeButton.Add_Click({ Select-Duplicates })
$TransferButton.Add_Click({ Invoke-UiAction { Transfer-SelectedTabs } })
$CloseButton.Add_Click({ Invoke-UiAction { Close-SelectedTabs } })
$ExportButton.Add_Click({ Invoke-UiAction { Export-SelectedTabs } })
$PushButton.Add_Click({ Invoke-UiAction { Push-To-TabDeck } })
$Grid.Add_CurrentCellChanged({ Update-Summary })
$Window.Add_Closed({ Remove-AllTabDeckForwards })

Recover-StaleForwards
Refresh-Devices
[void]$Window.ShowDialog()
