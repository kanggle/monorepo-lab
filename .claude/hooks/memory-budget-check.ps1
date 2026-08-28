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
# ── 판정축과 임계의 출처 (TASK-MONO-596) ─────────────────────────────────
#
# 🔴 옛 판정: `포인터 개수 <= 180`, 유도는 `24576 ÷ 포인터당바이트(실측 130) = 189`.
#    그 나눗셈은 **단위가 섞여 있었고**(분자는 문자 한도로 들여온 값, 분모는 바이트),
#    더 나쁘게는 **그 분모가 낡았다**: 2026-08-28 실측 포인터당 **157.4B** — 130 대비
#    **+21%**. 같은 산식을 지금 입력으로 다시 돌리면 `24576 ÷ 157.4 = 156` 이라
#    **현재 179 는 초과**다. 같은 공식·같은 파일·반대 판정. 임계가 «지켜지고 있다» 는
#    상태는 파일이 작아서가 아니라 **공식이 틀려서** 성립하고 있었다.
#
#    ⇒ 그래서 재도출하지 않고 **축을 바꿨다.** 포인터 임계의 유일한 유도 경로가
#    「문자한도 ÷ 포인터당바이트」인데 그 입력이 며칠 만에 21% 흐른다. 흐르는 입력의
#    몫을 상수로 얼리면 고치려는 결함을 그대로 재생산한다.
#
# ── 지금 판정: **줄 수 < 140** ────────────────────────────────────────────
#
# 🔵 **이 숫자는 우리가 만든 것이 아니다 — 하네스가 요구한 것을 인용한다.**
#    하네스 PostToolUse 게이트: 200줄 읽기 한도, **140줄 미만**을 요구.
#    출처 = 발화 문구(훅 소스 접근 불가), **2026-08-27 관측**.
#    ⇒ 유도가 없는 것이 아니라 **유도할 필요가 없다.** 외부에서 주어진 임계다.
#
# 🔵 왜 이 축인가 (세 후보 중):
#    (A) 포인터 유지 + 임계 재도출 — 위 이유로 기각. **실측이 배제 근거다.**
#    (B) 문자 수로 전환 — 하네스 ① 게이트와 **같은 것**을 재게 되고, 게다가 문자축은
#        오늘 구속 조건이 아니다(20,078 < 24,986). 중복 게이트.
#    (C) 다 재되 판정은 하나 ← **채택.** 줄 수는 ⒜ 오늘 실제로 빨간 축이고(168 > 140),
#        ⒝ **리포 안에서 아무도 안 보는** 축이며(`/audit-memory` 는 ①②만 본다 —
#        TASK-MONO-595), ⒞ 항목 수의 대리지표라 이 훅의 처방(항목 감축)이 실제로
#        움직이는 축이다. 포인터·바이트·문자는 **보고만** 한다(임계 아님).
#
# 🔴 **무엇이 바뀌면 다시 재야 하나**: 하네스의 발화 문구가 바뀔 때. 그때만이다.
#    포인터당 바이트가 흘러도 이 임계는 안 움직인다 — 그게 축을 바꾼 이유다.
#
# 🔴 **재는 방법** (다음 사람이 «실측 130» 같은 검증 불가 숫자를 물려받지 않도록):
#    node -e "const s=require('fs').readFileSync('MEMORY.md','utf8');console.log(
#      s.split('\n').length+'줄', s.length+'자', Buffer.byteLength(s,'utf8')+'B',
#      (s.match(/\]\([a-z0-9_]+\.md\)/g)||[]).length+'ptr')"
#
# 🔴 루프 안전: Stop 훅이 매번 block 하면 에이전트가 못 끝내고 무한 반복한다.
#    그래서 **하루 1회**만 발화한다(마커 파일). 이미 발화한 날은 조용히 통과.

$ErrorActionPreference = 'Stop'

# ── 튜너블 ────────────────────────────────────────────────────────────────
$LineLimit = 140          # 판정. 하네스 요구치의 **인용**이다 — 위 § 출처 참조.
                          #   재측정 트리거 = 하네스 발화 문구가 바뀔 때.
$NotifyOnly = $false      # $true 로 두면 block 하지 않고 데스크톱 알림만
# 🔵 아래 둘은 **보고 전용이다. 어떤 판정에도 쓰이지 않는다.** 상수를 두지 않는 이유:
#    유도 없는 숫자를 파일에 남겨두면 다음 사람이 그것을 임계로 오인한다 — 이 파일이
#    이미 두 번 그렇게 했다(180, 그리고 24576).
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
$chars = $text.Length
$ptr   = ([regex]::Matches($text, '\]\([a-z0-9_]+\.md\)')).Count
$lineCount = ($text -split "`r?`n").Count

# 🔴 0 은 «위반 없음» 이 아니라 «못 읽었음» 이다. 파일이 비었거나 인코딩이 깨졌을 때
#    조용히 통과하면 이 훅은 영원히 초록이다.
if ($lineCount -le 1 -and $ptr -eq 0) { exit 0 }
$perPtr = if ($ptr -gt 0) { [math]::Round($bytes / $ptr) } else { 0 }

if ($lineCount -lt $LineLimit) { exit 0 }   # WITHIN — 조용히 통과

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
# 🔴 TASK-MONO-596 AC-3: 재고가 0 이면 «없다» 고 말한다. 2026-08-28 실측으로 §C·§F 는
#    포인터 0(이관 완료)이라 이 폴백이 실제로 뜨는 경로다 — 훅이 실행 불가능한 선택지를
#    먼저 제시하지 않도록.
if (-not $candText) { $candText = '없음 — §C·§F 는 이미 포인터 0(이관 완료). 2번 레버는 재고가 없다.' }

Set-Content -LiteralPath $marker -Value $today -Encoding UTF8

$reason = @"
[VIOLATION] MEMORY-BUDGET-01: MEMORY.md 인덱스가 **줄 수** 임계를 넘었습니다 — $lineCount 줄 / 한도 $LineLimit 줄 미만 at $index
  (보고 전용 — 판정 아님: $ptr 포인터 · $chars 자 · $bytes B · 포인터당 ${perPtr}B)
[WHY] 이 인덱스는 매 세션 자동 로드되므로 크기가 곧 매 세션 토큰 비용입니다. 초과가 3회 재발(2026-06-06 / 08-08 / 08-14)했고 매번 사람이 우연히 발견했습니다. 리포 CI 는 이 파일이 리포 밖이라 못 보고, 주간 루틴은 최대 7일 늦습니다. 🔴 판정축은 **줄 수** 하나이며 그 임계 140 은 하네스 요구치의 인용입니다(2026-08-27 관측) — 포인터/문자/바이트는 위에 보고만 되고 아무것도 판정하지 않습니다.
[REMEDIATION] Choose one:
  🔴 먼저: 산문 압축은 처방이 아닙니다 — 줄 수는 **항목 수**로만 내려갑니다.
  1. 완료분을 ARCHIVE.md 로 이동한다. 🔴 이 훅은 ARCHIVE 후보를 **계산하지 않는다** — 직접 열어 «끝났다»가 참인지 확인해야 하고, 2026-08-28 실측 재고는 **0** 이었다(ktg FAQ 는 파일 자신이 「끝났다」를 쓰지 말라고 하고, ADR-067 은 단계 3·4 미완).
  2. 절차·플레이북·외부참조를 PLAYBOOKS.md 로 내린다. 후보: $candText
  3. 형제 항목 통합(같은 결함 클래스를 한 줄로). 2026-08-27 가격표 실측 −12 포인터. 🔴 **소유자 결정이다** — 함정(A2·B) 의 포인터를 줄이는 일이고, 과거 통합이 인바운드 wikilink 20종 재배선 사고를 냈다. 에이전트가 단독으로 하지 말 것.
  4. 1~3 으로 안 되면 임계 자체를 다시 논한다. 🔴 단 «현재값에 임계를 맞추는» 것은 금지 — 그러면 게이트가 아무것도 안 잰다. 임계 140 은 하네스가 준 값이므로, 바꾸려면 **하네스 발화 문구가 바뀌었다는 관측**이 있어야 한다.
  🔴 금지: 함정(A2·B) 을 lazy-load 로 내리지 말 것(안 로드된 함정은 다시 밟는다) · 항목 삭제 금지.
  ✅ 이동 후 포인터 집합 동등성을 확인할 것: 이동 전후 `](x.md)` 집합이 같아야 한다(comm -23).
[REFERENCE] .claude/commands/audit-memory.md § Phase 2-6 (인덱스 예산) · memory/feedback_memory_index_one_line_discipline · TASK-MONO-596 (판정축과 임계의 출처)
"@

if ($NotifyOnly) {
    & "$PSScriptRoot\notify.ps1" -Title "MEMORY.md budget" -Message "$lineCount 줄 / 한도 $LineLimit 줄 미만 초과"
    exit 0
}

@{ decision = 'block'; reason = $reason } | ConvertTo-Json -Compress -Depth 3
exit 0
