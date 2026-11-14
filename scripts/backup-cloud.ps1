# 2026-11-14 (Shir: "a nightly backup" of the public site): every night the whole cloud database (Neon)
# comes down to this PC as one file, checked table by table against the database (DataCopy dump -> read
# back -> same fingerprints). Keeps 30 days in D:\Projects\Backups\brainblocks-cloud and a copy on C:.
# Runs from the nightly task (backup-db.ps1). The address/password are in server\cloud.properties (not in git):
#   cloud.url=jdbc:postgresql://<neon host>/<db>?sslmode=require
#   cloud.user=...
#   cloud.password=...
# Restore (only if ever needed, after Shir says so):
#   java -cp run\brainblocks.jar -Dloader.main=com.example.brainblocks.tools.DataCopy `
#        org.springframework.boot.loader.launch.PropertiesLauncher load --from <file.zip> --to <jdbc url> --replace
$ErrorActionPreference = "Stop"
$server = Split-Path -Parent $PSScriptRoot
$propsFile = Join-Path $server "cloud.properties"
if (-not (Test-Path $propsFile)) { "no cloud.properties - no cloud backup"; exit 0 }
$props = @{}
foreach ($line in Get-Content $propsFile -Encoding UTF8) {
  if ($line -match '^\s*([\w.]+)\s*=\s*(.*)$') { $props[$Matches[1]] = $Matches[2].Trim() }
}
# Its own copy of the tool (the PC server jar may be old, or retired after the move to Render).
# After a server change: copy target\brainblocks-0.0.1-SNAPSHOT.jar here.
$jar = Join-Path $server "run\brainblocks-tools.jar"
$dirs = @("D:\Projects\Backups\brainblocks-cloud", "$env:USERPROFILE\BrainBlocksBackups\cloud")
foreach ($d in $dirs) { New-Item -ItemType Directory -Force -Path $d | Out-Null }
$name = "brainblocks-cloud-" + (Get-Date -Format "yyyyMMdd-HHmm") + ".zip"
$main = Join-Path $dirs[0] $name
$log = Join-Path $dirs[0] "backup.log"

$env:FROM_USER = $props["cloud.user"]
$env:FROM_PASSWORD = $props["cloud.password"]
try {
  $out = & java -cp $jar "-Dloader.main=com.example.brainblocks.tools.DataCopy" org.springframework.boot.loader.launch.PropertiesLauncher `
      dump --from $props["cloud.url"] --to $main 2>&1
  $code = $LASTEXITCODE
} finally {
  Remove-Item Env:FROM_USER, Env:FROM_PASSWORD -ErrorAction SilentlyContinue
}
if ($code -ne 0 -or -not (Test-Path $main)) {
  "$(Get-Date -Format s) FAILED $name (exit $code): $($out | Select-Object -Last 3)" | Add-Content $log
  throw "cloud backup failed (exit $code)"
}
Copy-Item $main (Join-Path $dirs[1] $name)
foreach ($d in $dirs) {
  Get-ChildItem $d -Filter "brainblocks-cloud-*.zip" | Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-30) } | Remove-Item
}
$rows = ($out | Select-String "  same  ").Count
"$(Get-Date -Format s) ok $name ($rows tables verified, $([math]::Round((Get-Item $main).Length/1KB)) KB)" | Add-Content $log
