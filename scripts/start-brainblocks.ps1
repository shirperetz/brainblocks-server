# Starts the BrainBlocks server for the public link (2026-11-11, Tailscale Funnel -> 127.0.0.1:8080).
# Runs a COPY of the jar (run\brainblocks.jar) so a rebuild never fights a locked file.
# Working dir = server\ : local.properties (DB password) and ..\learn-client\dist (the site) are relative.
$ErrorActionPreference = "Stop"
$server = Split-Path $PSScriptRoot -Parent
Set-Location $server
$busy = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($busy) { Write-Output "8080 already in use (pid $($busy[0].OwningProcess)) - not starting a second server."; exit 0 }
# MySQL must be up first (service MySQL80 or similar).
for ($i = 0; $i -lt 30 -and -not (Get-NetTCPConnection -LocalPort 3306 -State Listen -ErrorAction SilentlyContinue); $i++) { Start-Sleep 2 }
$java = Join-Path $env:JAVA_HOME "bin\java.exe"
if (-not (Test-Path $java)) { $java = "java" }
if (Test-Path run\server.log) { Copy-Item run\server.log run\server.prev.log -Force }
Start-Process -FilePath $java -ArgumentList "-jar", "run\brainblocks.jar", "--brainblocks.rooms-file=run/rooms-snapshot.json" -WorkingDirectory $server `
    -RedirectStandardOutput run\server.log -RedirectStandardError run\server-err.log -WindowStyle Hidden
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep 2
    try { Invoke-WebRequest http://127.0.0.1:8080/ -UseBasicParsing -TimeoutSec 3 | Out-Null; Write-Output "UP after $(($i + 1) * 2)s"; exit 0 } catch {}
}
Write-Output "did not come up - see run\server.log"; exit 1
