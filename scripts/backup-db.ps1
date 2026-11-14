# 2026-11-13 (Shir: security - "a nightly backup"): a full dump of the BrainBlocks database every night.
# Keeps the last 14 days in D:\Projects\Backups\brainblocks-auto and a copy on C: (another drive), and
# checks every dump really has data (tables + rows) before counting it as a backup.
$ErrorActionPreference = "Stop"
$server = Split-Path -Parent $PSScriptRoot
$props = Get-Content (Join-Path $server "local.properties") -Encoding UTF8
$pw = ($props | Where-Object { $_ -match '^spring\.datasource\.password\s*=' }) -replace '^spring\.datasource\.password\s*=\s*', ''
$mysqldump = "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqldump.exe"
$dirs = @("D:\Projects\Backups\brainblocks-auto", "$env:USERPROFILE\BrainBlocksBackups")
foreach ($d in $dirs) { New-Item -ItemType Directory -Force -Path $d | Out-Null }
$name = "brainblocks-" + (Get-Date -Format "yyyyMMdd-HHmm") + ".sql"
$main = Join-Path $dirs[0] $name
$env:MYSQL_PWD = $pw.Trim()
& $mysqldump -ubrainblocks --no-tablespaces --single-transaction --result-file="$main" brainblocks
Remove-Item Env:MYSQL_PWD
$inserts = (Select-String -Path $main -Pattern "INSERT INTO" -SimpleMatch).Count
if ($inserts -lt 5) { throw "backup looks empty ($inserts INSERT lines): $main" }
Copy-Item $main (Join-Path $dirs[1] $name)
# keep 14 days
foreach ($d in $dirs) {
  Get-ChildItem $d -Filter "brainblocks-*.sql" | Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-14) } | Remove-Item
}
"$(Get-Date -Format s) ok $name ($inserts inserts, $([math]::Round((Get-Item $main).Length/1KB)) KB)" | Add-Content (Join-Path $dirs[0] "backup.log")

# 2026-11-14: after this PC's backup, the public site's database too (does nothing until cloud.properties exists).
& (Join-Path $PSScriptRoot "backup-cloud.ps1")
