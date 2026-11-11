# Registers "BrainBlocks Server" - starts the server at logon (the public link needs it running).
$script = Join-Path $PSScriptRoot "start-brainblocks.ps1"
$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$script`""
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit ([TimeSpan]::Zero)
Register-ScheduledTask -TaskName "BrainBlocks Server" -Action $action -Trigger $trigger -Settings $settings -Description "BrainBlocks math game for the public link (Tailscale Funnel)" -Force | Out-Null
Write-Output "registered"
