param(
    [string]$AvdName = 'Pixel_9',
    [string]$AdbPath,
    [string]$EmulatorPath,
    [int]$BootTimeoutSeconds = 300
)

$ErrorActionPreference = 'Stop'

$sdkDirectory = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
if (-not $AdbPath) {
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    $sdkAdb = Join-Path $sdkDirectory 'platform-tools\adb.exe'
    if ($adbCommand) {
        $AdbPath = $adbCommand.Source
    } elseif (Test-Path -LiteralPath $sdkAdb) {
        $AdbPath = $sdkAdb
    } else {
        throw 'ADB was not found. Set -AdbPath to your Android SDK platform-tools\adb.exe.'
    }
}
if (-not $EmulatorPath) {
    $EmulatorPath = Join-Path $sdkDirectory 'emulator\emulator.exe'
}
if (-not (Test-Path -LiteralPath $EmulatorPath)) {
    throw 'Android Emulator was not found. Set -EmulatorPath to your Android SDK emulator\emulator.exe.'
}

$avds = @(& $EmulatorPath -list-avds)
if ($avds -notcontains $AvdName) {
    throw "AVD $AvdName was not found. Create it in Android Studio Device Manager or pass -AvdName. Available: $($avds -join ', ')"
}

function Find-RunningEmulator {
    $deviceOutput = & $AdbPath devices
    if ($LASTEXITCODE -ne 0) { throw 'Could not list Android devices with ADB.' }
    foreach ($line in $deviceOutput) {
        if ($line -match '^(emulator-\d+)\s+device$') {
            $serial = $Matches[1]
            $name = @(& $AdbPath -s $serial emu avd name)
            if ($name.Count -gt 0 -and "$($name[0])".Trim() -eq $AvdName) { return $serial }
        }
    }
    return $null
}

$serial = Find-RunningEmulator
if (-not $serial) {
    Write-Host "Starting emulator $AvdName..."
    Start-Process -FilePath $EmulatorPath -ArgumentList '-avd', $AvdName
}

$deadline = (Get-Date).AddSeconds($BootTimeoutSeconds)
while ($true) {
    if (-not $serial) { $serial = Find-RunningEmulator }
    if ($serial -and "$(& $AdbPath -s $serial shell getprop sys.boot_completed)".Trim() -eq '1') { break }
    if ((Get-Date) -gt $deadline) { throw "Emulator $AvdName did not finish booting in $BootTimeoutSeconds seconds." }
    Start-Sleep -Seconds 3
}
Write-Host "Emulator ready: $AvdName ($serial)."
Write-Host 'The debug app must use http://10.0.2.2:5207/ in My Profile > Server connection.'

try {
    $health = Invoke-RestMethod -Uri 'http://127.0.0.1:5207/api/webhook/health' -TimeoutSec 3
    if ($health.status -eq 'healthy') {
        Write-Host 'Backend is already running. Keep its terminal open.'
        Write-Warning 'If backend code changed, this running process may still use the old build. Stop its terminal and rerun this script to load new chatbot code.'
        return
    }
} catch {
    # No backend is listening yet; start it below.
}

$backendDirectory = Join-Path $PSScriptRoot 'BE\InteriorAI'
if (-not (Test-Path -LiteralPath $backendDirectory)) { throw 'Backend project was not found.' }
Push-Location $backendDirectory
try {
    Write-Host 'Starting the backend. Keep this terminal open while using the app.'
    & dotnet run --launch-profile http
    if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE." }
} finally {
    Pop-Location
}
