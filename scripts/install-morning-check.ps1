# 2026-11-22: registers "BrainBlocks Morning Check" - every day at 07:30 (and at the next logon if the PC
# was off then) it runs morning-check.ps1 against the live site. Read-only; alerts only when something broke.
$script = Join-Path $PSScriptRoot "morning-check.ps1"
$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$script`""
$trigger = New-ScheduledTaskTrigger -Daily -At 07:30
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Minutes 10)
Register-ScheduledTask -TaskName "BrainBlocks Morning Check" -Action $action -Trigger $trigger -Settings $settings -Description "Checks brainblocks.onrender.com every morning; phone alert via Katrine if something broke" -Force | Out-Null
Write-Output "registered"
