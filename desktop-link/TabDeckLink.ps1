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
$script:ForwardSerial = $null
$script:TabsView = $null
$script:MaxLiveActionTabs = 250
$script:MaxBridgeTabs = 25000

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

function Add-Forward {
    param([string]$Serial, [int]$LocalPort, [string]$Remote)
    [void](Invoke-Adb @('-s', $Serial, 'forward', "tcp:$LocalPort", $Remote))
}

function Remove-Forward {
    param([string]$Serial, [int]$LocalPort)
    [void](Invoke-Adb @('-s', $Serial, 'forward', '--remove', "tcp:$LocalPort") -AllowFailure)
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

function Refresh-Devices {
    $DeviceBox.Items.Clear()
    foreach ($serial in Get-DeviceSerials) { [void]$DeviceBox.Items.Add($serial) }
    if ($DeviceBox.Items.Count -gt 0) { $DeviceBox.SelectedIndex = 0 }
    Set-Status "Found $($DeviceBox.Items.Count) authorized Android device(s)."
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

    $sockets = @(Get-DevToolsSockets $serial | Select-Object -First 32)
    if (-not $sockets) {
        throw 'No Chromium DevTools socket is visible. On the device, enable Developer options + USB debugging, open the target Chromium browser, and enable USB debugging / remote inspection where the browser requires it.'
    }

    $script:ForwardSerial = $serial
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
    Set-Status "Loaded $($script:Tabs.Count) page targets from $($sockets.Count) DevTools socket(s)."
}

function Select-AllTabs {
    param([bool]$Selected)
    $targets = if ($SearchBox.Text.Trim()) { @($script:TabsView) } else { @($script:Tabs) }
    foreach ($tab in $targets) { $tab.Selected = $Selected }
    $Grid.Items.Refresh()
    Set-Status "$($targets.Count) visible target(s) marked $Selected."
}

function Get-SelectedTabs {
    param([switch]$LiveAction)
    $selected = @($script:Tabs | Where-Object Selected)
    if ($LiveAction -and $selected.Count -gt $script:MaxLiveActionTabs) {
        throw "Live open/close actions are capped at $($script:MaxLiveActionTabs) tabs per run. Narrow the selection and retry."
    }
    return $selected
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
}

function Close-SelectedTabs {
    $selected = @(Get-SelectedTabs -LiveAction)
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

function Transfer-SelectedTabs {
    $selected = @(Get-SelectedTabs -LiveAction)
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
    $verified = [Collections.Generic.List[object]]::new()
    foreach ($tab in $selected) {
        try {
            $encoded = [Uri]::EscapeDataString($tab.Url)
            $created = Invoke-JsonEndpoint "http://127.0.0.1:$port/json/new?$encoded" -Method PUT
            if (-not $created.id -or $created.url -notmatch '^https?://') { throw 'Destination did not confirm a page target.' }
            $opened++
            $verified.Add($tab)
            if ($opened % 20 -eq 0) { Set-Status "Opening tabs: $opened/$($selected.Count)"; Pump-Ui }
            Start-Sleep -Milliseconds 120
        } catch { }
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

function Push-To-TabDeck {
    $serial = [string]$DeviceBox.SelectedItem
    $selected = @(Get-SelectedTabs)
    if (-not $selected) { throw 'Select targets to send to TabDeck.' }
    if ($selected.Count -gt $script:MaxBridgeTabs) { throw "Bridge imports are capped at $($script:MaxBridgeTabs) tabs." }
    if (-not $TokenBox.Password) { throw 'Paste the bridge token from TabDeck.' }

    if ($script:BridgePort -and $script:ForwardSerial) { Remove-Forward $script:ForwardSerial ([int]$script:BridgePort) }
    $script:BridgePort = Get-FreeTcpPort
    $script:ForwardSerial = $serial
    Add-Forward $serial $script:BridgePort 'tcp:48721'
    $payload = [ordered]@{
        browser = 'Desktop Link'
        sourceLabel = 'Windows Desktop Link'
        deviceName = $serial
        tabs = @($selected | ForEach-Object {
            [ordered]@{
                id = $_.TargetId
                title = $_.Title
                url = $_.Url
                group = $_.Source
                deviceId = $serial
                browser = 'Desktop Link'
            }
        })
    }
    $headers = @{ 'X-TabDeck-Token' = $TokenBox.Password }
    $response = Invoke-JsonEndpoint "http://127.0.0.1:$script:BridgePort/api/v3/import" -Method POST -Headers $headers -Body $payload
    Set-Status "TabDeck accepted $($response.imported) of $($response.received) targets (request $($response.requestId))."
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
        Title="TabDeck Desktop Link" Width="1240" Height="820" MinWidth="940" MinHeight="620"
        WindowStartupLocation="CenterScreen" Background="#0D1117" Foreground="#E6EDF3">
  <Window.Resources>
    <Style TargetType="Button">
      <Setter Property="Margin" Value="0,0,8,0"/><Setter Property="Padding" Value="14,9"/>
      <Setter Property="Background" Value="#5D45B5"/><Setter Property="Foreground" Value="White"/>
      <Setter Property="BorderThickness" Value="0"/><Setter Property="Cursor" Value="Hand"/>
    </Style>
    <Style TargetType="ComboBox"><Setter Property="Margin" Value="0,0,8,0"/><Setter Property="MinWidth" Value="190"/><Setter Property="Padding" Value="8"/></Style>
    <Style TargetType="TextBox"><Setter Property="Padding" Value="8"/></Style>
    <Style TargetType="PasswordBox"><Setter Property="Padding" Value="8"/></Style>
  </Window.Resources>
  <Grid Margin="22">
    <Grid.RowDefinitions><RowDefinition Height="Auto"/><RowDefinition Height="Auto"/><RowDefinition Height="*"/><RowDefinition Height="Auto"/></Grid.RowDefinitions>
    <Grid Grid.Row="0" Margin="0,0,0,18">
      <Grid.ColumnDefinitions><ColumnDefinition Width="*"/><ColumnDefinition Width="Auto"/></Grid.ColumnDefinitions>
      <StackPanel>
        <TextBlock Text="ANDROID BROWSER CONTROL" Foreground="#B9A7FF" FontWeight="Bold" FontSize="12"/>
        <TextBlock Text="TabDeck Desktop Link" FontSize="30" FontWeight="ExtraBold" Margin="0,2,0,4"/>
        <TextBlock Text="Inspect user-authorized Android Chromium targets, deduplicate, transfer between debuggable browsers, and push to TabDeck." Foreground="#9DA7B3" FontSize="14"/>
      </StackPanel>
      <Border Grid.Column="1" Background="#162032" CornerRadius="14" Padding="14,9"><TextBlock Text="ADB + official DevTools endpoints" Foreground="#78D6C6" FontWeight="Bold"/></Border>
    </Grid>

    <Border Grid.Row="1" Background="#161B22" CornerRadius="18" Padding="14" Margin="0,0,0,14">
      <StackPanel>
        <WrapPanel Margin="0,0,0,10">
          <TextBlock Text="Device" VerticalAlignment="Center" Margin="0,0,8,0" FontWeight="Bold"/>
          <ComboBox x:Name="DeviceBox"/>
          <Button x:Name="RefreshDevicesButton" Content="Refresh devices"/>
          <Button x:Name="LoadTabsButton" Content="Discover Android tabs"/>
          <TextBlock Text="Destination" VerticalAlignment="Center" Margin="16,0,8,0" FontWeight="Bold"/>
          <ComboBox x:Name="DestinationBox"/>
          <CheckBox x:Name="CloseAfterTransfer" Content="Close source after verified open" VerticalAlignment="Center" Margin="8,0,0,0"/>
        </WrapPanel>
        <WrapPanel>
          <TextBlock Text="Filter" VerticalAlignment="Center" Margin="0,0,8,0" FontWeight="Bold"/>
          <TextBox x:Name="SearchBox" Width="260" Margin="0,0,10,0" ToolTip="Filter by title, URL, or DevTools socket"/>
          <Button x:Name="SelectAllButton" Content="Select visible"/>
          <Button x:Name="ClearButton" Content="Clear visible"/>
          <Button x:Name="DedupeButton" Content="Select duplicate copies"/>
          <Button x:Name="TransferButton" Content="Transfer selected"/>
          <Button x:Name="CloseButton" Content="Close selected" Background="#9E3944"/>
          <Button x:Name="ExportButton" Content="Export selected" Background="#2E6B62"/>
        </WrapPanel>
      </StackPanel>
    </Border>

    <DataGrid x:Name="Grid" Grid.Row="2" AutoGenerateColumns="False" CanUserAddRows="False" IsReadOnly="False"
              Background="#0D1117" Foreground="#E6EDF3" RowBackground="#161B22" AlternatingRowBackground="#11161D"
              GridLinesVisibility="Horizontal" BorderBrush="#30363D" HeadersVisibility="Column" SelectionMode="Extended">
      <DataGrid.Columns>
        <DataGridCheckBoxColumn Header="Use" Binding="{Binding Selected, Mode=TwoWay, UpdateSourceTrigger=PropertyChanged}" Width="55"/>
        <DataGridTextColumn Header="Title" Binding="{Binding Title}" Width="2*" IsReadOnly="True"/>
        <DataGridTextColumn Header="URL" Binding="{Binding Url}" Width="3*" IsReadOnly="True"/>
        <DataGridTextColumn Header="Android DevTools socket" Binding="{Binding Source}" Width="1.6*" IsReadOnly="True"/>
      </DataGrid.Columns>
    </DataGrid>

    <Border Grid.Row="3" Background="#161B22" CornerRadius="18" Padding="14" Margin="0,14,0,0">
      <Grid>
        <Grid.ColumnDefinitions><ColumnDefinition Width="Auto"/><ColumnDefinition Width="260"/><ColumnDefinition Width="Auto"/><ColumnDefinition Width="*"/></Grid.ColumnDefinitions>
        <TextBlock Text="TabDeck bridge token" VerticalAlignment="Center" Margin="0,0,8,0" FontWeight="Bold"/>
        <PasswordBox x:Name="TokenBox" Grid.Column="1" Margin="0,0,8,0"/>
        <Button x:Name="PushButton" Grid.Column="2" Content="Push selected into Android TabDeck"/>
        <TextBlock x:Name="StatusText" Grid.Column="3" Text="Ready. USB debugging must already be authorized." Foreground="#9DA7B3" VerticalAlignment="Center" TextTrimming="CharacterEllipsis"/>
      </Grid>
    </Border>
  </Grid>
</Window>
'@

$reader = [Xml.XmlNodeReader]::new($xaml)
$Window = [Windows.Markup.XamlReader]::Load($reader)
foreach ($name in @('DeviceBox','DestinationBox','CloseAfterTransfer','SearchBox','Grid','TokenBox','StatusText','RefreshDevicesButton','LoadTabsButton','SelectAllButton','ClearButton','DedupeButton','TransferButton','CloseButton','ExportButton','PushButton')) {
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
    Set-Status "$(@($script:TabsView).Count) of $($script:Tabs.Count) targets visible."
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
$Window.Add_Closed({
    $serial = $script:ForwardSerial
    if ($serial) {
        foreach ($port in $script:SocketMap.Values) { Remove-Forward $serial ([int]$port) }
        if ($script:BridgePort) { Remove-Forward $serial ([int]$script:BridgePort) }
    }
})

Refresh-Devices
[void]$Window.ShowDialog()
