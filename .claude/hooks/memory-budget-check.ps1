# memory-budget-check.ps1 — Stop hook: MEMORY.md 인덱스 예산 감시 (TASK-MONO-529 후속)
#
# 왜 훅인가: MEMORY.md 는 매 세션 자동 로드돼 크기가 곧 토큰 비용인데, 리포 CI 는
# 그 파일이 리포 밖(`~/.claude/projects/<slug>/memory/`)이라 원리적으로 못 본다.
# 주간 루틴(`monorepo-lab-audit-memory-weekly`, Mon 09:30 KST)이 `/audit-memory`
# Phase 2-6 으로 같은 것을 재지만 **최대 7일 늦다.** 이 훅은 그 간격을 메운다.
#
# 🔴 루프 안전: Stop 훅이 매번 block 하면 에이전트가 못 끝내고 무한 반복한다.
#    그래서 **하루 1회**만 발화한다(마커 파일). 이미 발화한 날은 조용히 통과.
#
# 판정 술어 — 바이트가 아니라 **포인터 개수**다. 실측(2026-08-14): 포인터당 130B,
# 그중 40%가 파일명(=주소라 압축 불가) ⇒ 24KB 는 "포인터 약 189개"의 다른 이름이고,
# 줄 병합은 6줄 합쳐 188B(0.8%)로 사실상 무효였다. 그래서 정원을 포인터로 센다.

$ErrorActionPreference = 'Stop'

# ── 튜너블 ────────────────────────────────────────────────────────────────
$QuotaPointers = 180      # 정원. 24576 ÷ 포인터당바이트(실측 130) = 189, 여유 두고 180
$HardLimitBytes = 24576   # 24KB — 참고용(보고에만 사용, 판정은 포인터로 한다)
$NotifyOnly = $false      # $true 로 두면 block 하지 않고 데스크톱 알림만
# ─────────────────────────────────────────────────────────────────────────

# stdin 은 반드시 읽어 버린다 — 안 읽으면 호출자가 파이프에서 막힐 수 있다.
$reader = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), [System.Text.Encoding]::UTF8)
$null = $reader.ReadToEnd()

function Get-MemoryDir {
    # `~/.claude/projects/<slug>/memory` — slug 은 cwd 의 ':' 와 구분자를 '-' 로 바꾼 것.
    # 🔴 계산한 슬러그를 믿지 말고 **존재 확인**한다. 없으면 조용히 통과(다른 머신/경로).
    $slug = (Get-Location).Path -replace ':', '-' -replace '[\\/]', '-'
    $dir  = Join-Path $env:USERPROFILE ".claude\projects\$slug\memory"
    if (Test-Path -LiteralPath $dir) { return $dir }

    # 폴백: 대소문자/드라이브 표기 차이를 흡수 — projects/* 중 접미사가 맞는 것을 찾는다.
    $root = Join-Path $env:USERPROFILE ".claude\projects"
    if (-not (Test-Path -LiteralPath $root)) { return $null }
    $leaf = Split-Path (Get-Location).Path -Leaf
    $cand = Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like "*$leaf" } |
            ForEach-Object { Join-Path $_.FullName 'memory' } |
            Where-Object { Test-Path -LiteralPath $_ }
    if ($cand) { return @($cand)[0] }
    return $null
}

$memDir = Get-MemoryDir
if (-not $memDir) { exit 0 }

$index = Join-Path $memDir 'MEMORY.md'
if (-not (Test-Path -LiteralPath $index)) { exit 0 }

# 하루 1회 디바운스
$marker = Join-Path $memDir '.budget-warned'
$today  = (Get-Date).ToString('yyyy-MM-dd')
if (Test-Path -LiteralPath $marker) {
    try { if ((Get-Content -LiteralPath $marker -Raw -ErrorAction Stop).Trim() -eq $today) { exit 0 } } catch {}
}

# ── 계측 ──────────────────────────────────────────────────────────────────
$text  = Get-Content -LiteralPath $index -Raw -Encoding UTF8
$bytes = [System.Text.Encoding]::UTF8.GetByteCount($text)
$ptr   = ([regex]::Matches($text, '\]\([a-z0-9_]+\.md\)')).Count
if ($ptr -eq 0) { exit 0 }
$perPtr = [math]::Round($bytes / $ptr)

if ($ptr -le $QuotaPointers) { exit 0 }   # WITHIN — 조용히 통과

# ── 초과: 구조 변경 후보를 계산해서 함께 넘긴다 ──────────────────────────
# 🔴 **허용리스트**로 고른다(블록리스트 아님). 처음엔 "함정 섹션을 제외" 로 짰다가
#    `^(A2|B)[\.\s]` 가 `B5.` 를 못 잡아 **함정 5개 섹션이 전부 후보로 새어 나왔다**.
#    블록리스트는 새 섹션이 생길 때마다 *열리는 쪽*으로 틀린다. 허용리스트는 닫히는
#    쪽으로 틀리므로, 모르는 섹션은 후보가 되지 않는다(= 함정이 강등될 일이 없다).
#    내려도 되는 것은 절차(C) · 플레이북(D) · 전략(F) · 외부참조(G) 뿐이다.
$MovableSections = '^[CDFG][\.\d]'
$lines = $text -split "`r?`n"
$section = ''; $cand = @{}
foreach ($l in $lines) {
    if ($l -match '^##+\s*(.+)$') { $section = $Matches[1].Trim() }
    if ($l -match '^\-\s' -and $section -and $section -match $MovableSections) {
        $n = ([regex]::Matches($l, '\]\([a-z0-9_]+\.md\)')).Count
        if ($n -gt 0) { $cand[$section] = [int]$cand[$section] + $n }
    }
}
$candText = ($cand.GetEnumerator() | Sort-Object Value -Descending |
             ForEach-Object { "$($_.Key) = $($_.Value) 포인터" }) -join ' · '
if (-not $candText) { $candText = '(절차성 섹션 없음 — 정원 증액 여부를 사용자에게 물어야 한다)' }

Set-Content -LiteralPath $marker -Value $today -Encoding UTF8

$reason = @"
[VIOLATION] MEMORY-BUDGET-01: MEMORY.md 인덱스가 정원을 초과했습니다 — 포인터 $ptr / 정원 $QuotaPointers ($bytes B, 포인터당 ${perPtr}B, 참고 한도 ${HardLimitBytes}B) at $index
[WHY] 이 인덱스는 매 세션 자동 로드되므로 크기가 곧 매 세션 토큰 비용입니다. 한도 초과가 3회 재발(2026-06-06 / 08-08 / 08-14)했고 매번 사람이 우연히 발견했습니다. 리포 CI 는 이 파일이 리포 밖이라 못 보고, 주간 루틴은 최대 7일 늦습니다.
[REMEDIATION] Choose one:
  1. 완료분을 ARCHIVE.md 로 이동한다.
  2. 절차·플레이북·외부참조를 PLAYBOOKS.md 로 내린다. 후보: $candText
  3. 1·2 로 안 되면 정원 증액(= 매 세션 토큰을 더 쓸지)을 사용자에게 묻는다. 조용히 더 깎지 않는다.
  🔴 금지: 함정(A2·B) 을 lazy-load 로 내리지 말 것(안 로드된 함정은 다시 밟는다) · 줄 병합으로 해결하려 들지 말 것(실측 6줄 합쳐 188B, 0.8%) · 항목 삭제 금지.
  ✅ 이동 후 포인터 집합 동등성을 확인할 것: 이동 전후 `](x.md)` 집합이 같아야 한다(comm -23).
[REFERENCE] .claude/commands/audit-memory.md § Phase 2-6 (인덱스 예산) · memory/feedback_memory_index_one_line_discipline
"@

if ($NotifyOnly) {
    & "$PSScriptRoot\notify.ps1" -Title "MEMORY.md budget" -Message "포인터 $ptr / 정원 $QuotaPointers 초과"
    exit 0
}

@{ decision = 'block'; reason = $reason } | ConvertTo-Json -Compress -Depth 3
exit 0
