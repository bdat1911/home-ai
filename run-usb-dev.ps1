param(
    [string]$DeviceSerial,
    [string]$AdbPath
)

$ErrorActionPreference = 'Stop'

if (-not $AdbPath) {
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    $sdkAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    if ($adbCommand) {
        $AdbPath = $adbCommand.Source
    } elseif (Test-Path -LiteralPath $sdkAdb) {
        $AdbPath = $sdkAdb
    } else {
        throw 'ADB was not found. Set -AdbPath to your Android SDK platform-tools\adb.exe.'
    }
}

$deviceOutput = & $AdbPath devices
if ($LASTEXITCODE -ne 0) { throw 'Could not list Android devices with ADB.' }
$connected = @($deviceOutput | ForEach-Object {
    if ($_ -match '^(\S+)\s+device$') { $Matches[1] }
})
if ($DeviceSerial) {
    if ($connected -notcontains $DeviceSerial) { throw "Device $DeviceSerial is not connected and authorized." }
} elseif ($connected.Count -eq 1) {
    $DeviceSerial = $connected[0]
} elseif ($connected.Count -eq 0) {
    throw 'No authorized Android device found. Unlock the phone and allow USB debugging.'
} else {
    throw 'More than one Android device found. Pass -DeviceSerial with the desired serial.'
}

& $AdbPath -s $DeviceSerial reverse tcp:5207 tcp:5207
if ($LASTEXITCODE -ne 0) { throw 'ADB reverse failed.' }
Write-Host "USB forwarding ready for $DeviceSerial (phone 127.0.0.1:5207 -> PC 5207)."
Write-Host 'The debug app must use http://127.0.0.1:5207/ in My Profile > Server connection.'

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
