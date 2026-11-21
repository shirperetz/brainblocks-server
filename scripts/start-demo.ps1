# 2026-11-21: a LOCAL demo of the site, to try new things before they go to the real site.
# Its own empty database (H2 file in %TEMP%\brainblocks-demo) - no real children, nothing from the cloud.
# Only on this computer: http://127.0.0.1:8094  (Ctrl+C stops it)
# Demo accounts are created by the first sign-up with join code 4711; "boss" = admin.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$data = Join-Path $env:TEMP "brainblocks-demo"
New-Item -ItemType Directory -Force $data | Out-Null
Push-Location (Join-Path $root "..\learn-client"); npm run build; Pop-Location
$env:SPRING_PROFILES_ACTIVE = "cloud"
$env:DATABASE_URL = "jdbc:h2:file:$($data.Replace('\','/'))/demo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
$env:DATABASE_USER = "sa"; $env:DATABASE_PASSWORD = ""
$env:JOIN_CODE = "4711"; $env:ADMIN_USERNAMES = "boss"; $env:ADMIN_2FA = "off"
$env:PUBLIC_ORIGIN = "http://127.0.0.1:8094"; $env:PORT = "8094"; $env:KEEP_AWAKE_URL = ""
$env:CLIENT_DIST = (Resolve-Path (Join-Path $root "..\learn-client\dist")).Path + "\"
$env:ROOMS_FILE = Join-Path $data "rooms.json"
Push-Location $root
.\mvnw.cmd -q spring-boot:run "-Dspring-boot.run.useTestClasspath=true" "-Dspring-boot.run.arguments=--server.address=127.0.0.1"
Pop-Location
