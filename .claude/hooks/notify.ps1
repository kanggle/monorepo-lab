param(
    [string]$Title   = "Claude Code",
    [string]$Message = ([string][char[]]@(0xC791,0xC5C5,0x20,0xC644,0xB8CC))
)

# "작업 완료" = 0xC791 0xC5C5 0x20 0xC644 0xB8CC

# 소리
try {
    Add-Type -AssemblyName System.Windows.Forms
    [System.Media.SystemSounds]::Beep.Play()
    Start-Sleep -Milliseconds 400
}
catch {}

# BurntToast 사용
$useToast = $false
try {
    if (Get-Module -ListAvailable -Name BurntToast) {
        Import-Module BurntToast -ErrorAction Stop
        New-BurntToastNotification -Text $Title, $Message | Out-Null
        $useToast = $true
    }
}
catch {}

# fallback
if (-not $useToast) {
    try {
        Add-Type -AssemblyName System.Windows.Forms
        $notify                  = New-Object System.Windows.Forms.NotifyIcon
        $notify.Icon             = [System.Drawing.SystemIcons]::Information
        $notify.BalloonTipTitle  = $Title
        $notify.BalloonTipText   = $Message
        $notify.Visible          = $true
        $notify.ShowBalloonTip(4000)
        Start-Sleep -Seconds 5
        $notify.Dispose()
    }
    catch {
        Write-Host "[Claude] $Message"
    }
}
