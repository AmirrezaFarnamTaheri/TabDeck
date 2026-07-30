$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Destination = Join-Path $Root "gradle\wrapper\gradle-wrapper.jar"
$Temporary = "$Destination.tmp"
$Url = "https://services.gradle.org/distributions/gradle-9.1.0-wrapper.jar"
$Expected = "76805e32c009c0cf0dd5d206bddc9fb22ea42e84db904b764f3047de095493f3"
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Destination) | Out-Null

function Get-NormalizedHash([string]$Path) {
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

try {
    if (Test-Path -LiteralPath $Destination) {
        if ((Get-NormalizedHash $Destination) -eq $Expected) {
            Write-Host "Gradle wrapper already present and verified: $Destination"
            & (Join-Path $Root "gradlew.bat") --version
            exit $LASTEXITCODE
        }
        Write-Warning "Existing Gradle wrapper checksum is unexpected; replacing it."
    }

    Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile $Temporary
    $Actual = Get-NormalizedHash $Temporary
    if ($Actual -ne $Expected) { throw "Gradle wrapper checksum mismatch." }
    Move-Item -Force -LiteralPath $Temporary -Destination $Destination
    Write-Host "Installed and verified Gradle wrapper: $Destination"
    & (Join-Path $Root "gradlew.bat") --version
    exit $LASTEXITCODE
}
finally {
    Remove-Item -Force -ErrorAction SilentlyContinue -LiteralPath $Temporary
}
