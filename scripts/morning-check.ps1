# 2026-11-22 (Shir: "a bot that checks the live site every morning and alerts me if something broke").
# Read-only: opens the main pages and their files, checks the server answers and that private data needs
# a login. Creates nothing, logs in as nobody (one made-up name, to prove the database answers).
# All fine -> one line in the log. Something wrong -> the same line + an alert to her phone through
# Katrine (loopback only), so it reaches her even outside the house.
#   Log: %LOCALAPPDATA%\BrainBlocks\morning-check.log
#   Run by hand: powershell -File morning-check.ps1   (-Test sends the phone alert even when all is fine,
#   -NoAlert never sends one)
param([string]$Site = "https://brainblocks.onrender.com", [switch]$Test, [switch]$NoAlert)
$ErrorActionPreference = "Continue"
$problems = New-Object System.Collections.Generic.List[string]
$logDir = Join-Path $env:LOCALAPPDATA "BrainBlocks"
New-Item -ItemType Directory -Force $logDir | Out-Null
$log = Join-Path $logDir "morning-check.log"

function Get-Url([string]$url, [int]$timeout = 30) {
    try {
        $sw = [Diagnostics.Stopwatch]::StartNew()
        $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec $timeout -MaximumRedirection 0 -ErrorAction Stop
        return @{ Code = [int]$r.StatusCode; Body = $r.Content; Ms = $sw.ElapsedMilliseconds }
    } catch {
        $code = 0
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        return @{ Code = $code; Body = ""; Ms = -1; Error = $_.Exception.Message }
    }
}

# 1. the server is up (the first call may wake it - up to 60 s)
$ping = Get-Url "$Site/api/ping" 60
if ($ping.Code -ne 200) { $problems.Add("השרת לא עונה (ping: $($ping.Code))") }

# 2. the main pages load and are the real site
$front = $null
foreach ($p in @("/", "/login", "/privacy", "/accessibility", "/room", "/contact")) {
    $r = Get-Url "$Site$p"
    if ($r.Code -ne 200 -or $r.Body -notmatch 'id="root"') { $problems.Add("הדף $p לא נטען ($($r.Code))") }
    elseif ($r.Ms -gt 8000) { $problems.Add("הדף $p איטי ($([math]::Round($r.Ms / 1000, 1)) שניות)") }
    if ($p -eq "/") { $front = $r }
}

# 3. the site's own files (scripts, styles) that the home page asks for
if ($front -and $front.Body) {
    $assets = [regex]::Matches($front.Body, '(?:src|href)="(/assets/[^"]+\.(?:js|css))"') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique
    if (-not $assets) { $problems.Add("דף הבית בלי קבצי האתר") }
    foreach ($a in $assets) {
        $r = Get-Url "$Site$a"
        if ($r.Code -ne 200) { $problems.Add("קובץ $a חסר ($($r.Code))") }
    }
}

# 4. private data needs a login
foreach ($p in @("/api/learn/profiles/1/island", "/api/learn/admin/children", "/api/learn/rooms/templates")) {
    $r = Get-Url "$Site$p"
    if ($r.Code -ne 401 -and $r.Code -ne 403) { $problems.Add("$p ענה $($r.Code) בלי כניסה (צריך 401)") }
}

# 5. the database answers: a made-up user is refused (401/403), not an error (5xx)
try {
    $body = @{ username = "morningcheck_nobody"; password = "not-a-password-1" } | ConvertTo-Json
    Invoke-WebRequest -Uri "$Site/api/learn/auth/login" -Method Post -Body $body -ContentType "application/json; charset=utf-8" -UseBasicParsing -TimeoutSec 30 -ErrorAction Stop | Out-Null
    $problems.Add("כניסה עם משתמש שלא קיים הצליחה")
} catch {
    $code = 0
    if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    if ($code -ge 500 -or $code -eq 0) { $problems.Add("מסד הנתונים לא עונה (כניסה: $code)") }
}

$stamp = Get-Date -Format "yyyy-MM-dd HH:mm"
if ($problems.Count -eq 0) {
    Add-Content -Path $log -Value "$stamp OK (ping $($ping.Ms) ms)" -Encoding UTF8
} else {
    Add-Content -Path $log -Value "$stamp PROBLEM: $($problems -join ' | ')" -Encoding UTF8
}

if (($problems.Count -gt 0 -or $Test) -and -not $NoAlert) {
    $text = if ($problems.Count -gt 0) { "בבדיקת הבוקר של עולם החשבון: " + ($problems -join "; ") } else { "בדיקת הבוקר של עולם החשבון: הכל תקין (בדיקה ידנית)." }
    $alert = @{ title = "עולם החשבון"; text = $text } | ConvertTo-Json -Compress
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($alert)
        Invoke-WebRequest -Uri "http://127.0.0.1:8081/local/phone-alert" -Method Post -Body $bytes -ContentType "application/json; charset=utf-8" -UseBasicParsing -TimeoutSec 10 | Out-Null
    } catch {
        Add-Content -Path $log -Value "$stamp (the phone alert did not go out: Katrine not reachable)" -Encoding UTF8
    }
}
if ($problems.Count -gt 0) { Write-Output "PROBLEM: $($problems -join ' | ')"; exit 1 }
Write-Output "OK"
