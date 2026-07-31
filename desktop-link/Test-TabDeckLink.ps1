Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot 'TabDeckLink.ps1'
$text = Get-Content -LiteralPath $scriptPath -Raw
$errors = [Collections.Generic.List[string]]::new()
$tokens = $null
$parseErrors = $null
[void][Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref]$tokens, [ref]$parseErrors)
foreach ($problem in @($parseErrors)) { $errors.Add($problem.Message) }
$required = @(
    'function Remove-AllTabDeckForwards',
    'function Recover-StaleForwards',
    'desktop-link-forwards.json',
    'function Test-DestinationTarget',
    'function Test-SupportedDevToolsSocket',
    'sourceSessionId = $script:SourceSessionId',
    'Destination target was not observable after creation; source remains open.'
)
foreach ($needle in $required) {
    if (-not $text.Contains($needle)) { $errors.Add("Missing safety contract: $needle") }
}
if ($text -match '(?i)dotnet\s+(run|publish)|Microsoft\.NETCore\.App') {
    $errors.Add('Portable PowerShell fallback unexpectedly requires a machine-wide .NET application runtime.')
}
if ($errors.Count -gt 0) { throw ($errors -join [Environment]::NewLine) }
Write-Host 'Desktop Link safety and portability contracts passed.'
