# Rancher Desktop 데이터 VHDX 압축 진단/실행 (관리자 권한으로 실행)
# zero-fill 은 이미 완료됨. diskpart 실제 출력을 로그로 남긴다.
$ErrorActionPreference = 'Continue'
$f   = "$env:LOCALAPPDATA\rancher-desktop\distro-data\ext4.vhdx"
$log = "C:\Users\kangdow\dev\project\ai-project\monorepo-lab\compact-diskpart.log"

function Get-AllocGB($path) {
  $sig = @'
[DllImport("kernel32.dll", CharSet=CharSet.Unicode)]
public static extern uint GetCompressedFileSize(string lpFileName, out uint lpFileSizeHigh);
'@
  $t = Add-Type -MemberDefinition $sig -Name KZ -Namespace WZ -PassThru
  $high = 0; $low = $t::GetCompressedFileSize($path, [ref]$high)
  [math]::Round((([uint64]$high -shl 32) + [uint64]$low) / 1GB, 2)
}

Write-Host "before: $(Get-AllocGB $f) GB on disk / C: free $([math]::Round((Get-PSDrive C).Free/1GB,2)) GB"

# 1) WSL 완전 종료 + vhdx 락 해제 대기
$rdctl = "C:\Program Files\Rancher Desktop\resources\resources\win32\bin\rdctl.exe"
if (Test-Path $rdctl) { & $rdctl shutdown 2>$null }
Start-Sleep -Seconds 5
wsl --shutdown
# rancher-desktop (소비자 distro) 가 Stopped 되고 WSL VM 이 내려갈 때까지 대기 — 이게 vhdx 락 해제 신호
for ($i=0; $i -lt 20; $i++) {
  Start-Sleep -Seconds 2
  $rd = (wsl -l -v | Select-String 'rancher-desktop ' ) -replace '\x00',''
  if (-not ($rd -match 'Running')) { break }
}
wsl --shutdown   # 확실히 한 번 더
Start-Sleep -Seconds 8

# diskpart 는 sparse vhdx 를 거부한다("must not be sparse"). 먼저 sparse 플래그 해제.
Write-Host "sparse 플래그 해제 중..."
wsl --manage rancher-desktop-data --set-sparse false 2>&1 | Out-Null
Start-Sleep -Seconds 2
Write-Host "sparse 상태: $((Get-Item $f).Attributes)"

Write-Host "WSL 종료 완료. compact 시도 (출력은 $log 에 기록)..."

# 2) diskpart compact — 출력 전체를 로그로
$script = @"
select vdisk file="$f"
attach vdisk readonly
compact vdisk
detach vdisk
exit
"@
$tmp = "$env:TEMP\rd_compact.txt"
Set-Content -Path $tmp -Value $script -Encoding ascii
& diskpart.exe /s $tmp *> $log
Remove-Item $tmp -ErrorAction SilentlyContinue

Write-Host "=== diskpart 출력 ==="
Get-Content $log
Write-Host "===================="
Write-Host "after:  $(Get-AllocGB $f) GB on disk / C: free $([math]::Round((Get-PSDrive C).Free/1GB,2)) GB"

# 3) Rancher + e2e 재기동
Write-Host "Rancher Desktop 재기동 중..."
if (Test-Path $rdctl) { Start-Process -FilePath $rdctl -ArgumentList 'start' -WindowStyle Hidden }
$up = $false
for ($i = 0; $i -lt 40; $i++) {
  Start-Sleep -Seconds 8
  & docker version --format '{{.Server.Version}}' > $null 2>&1
  if ($LASTEXITCODE -eq 0) { $up = $true; break }
}
if ($up) {
  Write-Host "docker 복구됨 — e2e 스택 재기동..."
  $dir = "C:\Users\kangdow\dev\project\ai-project\monorepo-lab\tests\federation-hardening-e2e\docker"
  Push-Location $dir
  $env:MSYS_NO_PATHCONV = '1'
  & docker compose -p federation-hardening-e2e -f docker-compose.federation-e2e.yml -f docker-compose.federation-e2e.demo.yml start
  Pop-Location
  Write-Host "완료."
} else {
  Write-Host "docker 가 아직 안 올라옴 — Rancher Desktop 수동 확인 필요."
}
Write-Host ""
Write-Host ">>> 위 'diskpart 출력' 과 before/after 수치를 Claude 에게 알려주세요. (로그: $log)"
