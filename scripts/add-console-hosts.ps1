$ErrorActionPreference = 'Stop'
$HostsFile = "$env:WINDIR\System32\drivers\etc\hosts"
$Marker = "# monorepo-lab console dev hosts"
$Entries = @(
    '127.0.0.1  console.local',
    '127.0.0.1  console-bff.local',
    '127.0.0.1  gap.local',
    '127.0.0.1  wms.local',
    '127.0.0.1  scm.local',
    '127.0.0.1  finance.local',
    '127.0.0.1  erp.local'
)

$content = Get-Content $HostsFile -Raw -ErrorAction SilentlyContinue
if (-not $content) { $content = '' }

$toAppend = @()
foreach ($entry in $Entries) {
    $hostname = ($entry -split '\s+')[1]
    if ($content -notmatch "(?m)^\s*127\.0\.0\.1\s+$([regex]::Escape($hostname))\b") {
        $toAppend += $entry
    } else {
        Write-Host "[skip] $hostname already mapped"
    }
}

if ($toAppend.Count -eq 0) {
    Write-Host "[ok] all entries already present"
    exit 0
}

$block = "`r`n$Marker`r`n" + ($toAppend -join "`r`n") + "`r`n"
Add-Content -Path $HostsFile -Value $block -Encoding ASCII
Write-Host "[ok] appended $($toAppend.Count) entries:"
$toAppend | ForEach-Object { Write-Host "  $_" }
