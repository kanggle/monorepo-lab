# Task ID

TASK-MONO-102

# Title

`hardstop-detect.ps1` Edit CRLF/LF simulation mismatch fix (4-instance trigger 충족)

# Status

review

# Owner

monorepo

# Task Tags

- monorepo
- hardstop-rules
- hook-source-fix
- crlf-lf-mismatch
- audit-trigger-closure

---

# Goal

`.claude/hooks/hardstop-detect.ps1` 의 Edit simulation 분기 (L271 `$existing.Contains($oldString)`) 가 file CRLF ↔ Edit tool oldString LF mismatch 시 false 반환 → `simContent = $existing` (변경 미반영) → 새로 추가된 H3 / Service Type header / 기타 신규 catalog value detect 못 함 → false-positive HARDSTOP-10 fire.

**Trigger 정확 충족**: 메모리 `project_adr_mono_012_d3_cycle_complete.md` § "Edit hook CRLF/LF simulation mismatch 3 instance → 4 instance 시 hook fix 별 task". MONO-101 closure 로 4 instance 누적 도래 (MONO-083 + MONO-093 + MONO-095 + MONO-101).

Hook simulation 의 contains 단계를 CRLF/LF normalize fallback 으로 보강 — 정상 case (line ending 일치) 는 behavior 0 변경, mismatch case 만 정상 simContent 생성 path 회복.

# Scope

## In Scope

### 1. `.claude/hooks/hardstop-detect.ps1` L268-280 fix

현재 (L266-280):
```powershell
$simContent = ""
$absFile = Join-Path $repoRoot $relFromRoot
if ($data.tool_name -eq 'Write' -or ($data.tool_input -and $data.tool_input.content)) {
    $simContent = $newString
} elseif (Test-Path $absFile -PathType Leaf) {
    $existing = Get-Content -Raw -Path $absFile -ErrorAction SilentlyContinue
    if ($existing -and $oldString -and $newString -and $existing.Contains($oldString)) {
        $simContent = $existing.Replace($oldString, $newString)
    } elseif ($existing) {
        $simContent = $existing
    } else {
        $simContent = $newString
    }
} else {
    $simContent = $newString
}
```

Fix 후 (L271-272 sub-block 만 확장):
```powershell
} elseif (Test-Path $absFile -PathType Leaf) {
    $existing = Get-Content -Raw -Path $absFile -ErrorAction SilentlyContinue
    if ($existing -and $oldString -and $newString) {
        if ($existing.Contains($oldString)) {
            $simContent = $existing.Replace($oldString, $newString)
        } else {
            # CRLF/LF normalize fallback — Edit tool 의 oldString 이 LF normalize 되어 들어왔는데
            # file 이 CRLF 일 때 raw Contains false-negative 보정 (4-instance MONO-083/093/095/101 closure).
            $existingNorm = $existing -replace "`r`n", "`n"
            $oldNorm = $oldString -replace "`r`n", "`n"
            $newNorm = $newString -replace "`r`n", "`n"
            if ($existingNorm.Contains($oldNorm)) {
                $simContent = $existingNorm.Replace($oldNorm, $newNorm)
            } else {
                $simContent = $existing
            }
        }
    } elseif ($existing) {
        $simContent = $existing
    } else {
        $simContent = $newString
    }
}
```

핵심 변경:
- 기존 `Contains($oldString)` 단일 분기 → 2-단 nested `if`
- 1단: raw contains (기존 path, line ending 일치 case)
- 2단 fallback: CRLF/LF normalize 후 retry (mismatch case)
- 둘 다 fail = `$simContent = $existing` (기존 fallback path 동일)

### 2. 새 fixture `.claude/hooks/__tests__/hardstop-10-crlf-lf-simulation.ps1`

회귀 가드. 시나리오:
- synth temporary tree → `projects/<synth>/specs/services/<svc>/architecture.md` (CRLF line endings, **Service Type header 부재**) 생성
- Edit invocation (LF oldString → LF newString 으로 Service Type Composition H3 + catalog value 추가)
- Pre-fix: simContent = $existing (Service Type 없음) → hook 가 HARDSTOP-10 fire (false-positive)
- Post-fix: simContent = $existing.normalize().Replace().normalize() (Service Type Composition H3 추가됨) → hook 가 catalog value detect → allow

PASS line 1개 (positive — fix 후 hook 가 정상 allow).

### 3. `.claude/hooks/__tests__/run-all.ps1` 등록

기존 7 fixture → 8 fixture (hardstop-10-crlf-lf-simulation 추가).

## Out of Scope

- **Other simulation branches**: hook 는 Edit 만 simulation 함 (Write 는 $newString 전체로 simContent 직접 할당). Edit simulation 만 본 fix scope.
- **CR-only line ending support** (legacy Mac): `\r` 단일 line ending 은 modern Git 에서 매우 드물고, hook normalize 대상 = CRLF ↔ LF 만. 별 task.
- **Other hook source 강화** (e.g. `Find-ProjectMdAncestor` performance, allowlist 확장): 본 task 는 single root cause (Edit CRLF/LF simulation) 만 fix.
- **Hook unit test infrastructure**: fixture 패턴 답습 (Invoke-Hook + Assert-Stanza), 별 framework 도입 X.

# Acceptance Criteria

- [ ] `.claude/hooks/hardstop-detect.ps1` L271 단일 `if` → 2-단 nested `if` (raw → normalize fallback) — `$simContent` 할당 path 3개 (raw Replace / normalize Replace / fallback existing) 모두 유효.
- [ ] 새 fixture `hardstop-10-crlf-lf-simulation.ps1` 작성 — synth CRLF architecture.md + LF Edit → hook allow 검증 (1 PASS line).
- [ ] `run-all.ps1` 에 새 fixture 등록 — 기존 7 → 8 fixture.
- [ ] `.claude/hooks/__tests__/run-all.ps1` → **23 PASS** (기존 22 + 신규 1).
- [ ] Pre-fix 회귀 검증 (수동): `git stash` hook fix only → `hardstop-10-crlf-lf-simulation.ps1` 실행 시 fixture FAIL (Assert-Allowed 가 block 받음). `git stash pop` 후 재실행 = PASS. 본 verification round-trip 은 commit log 에만 기록 (file 미터치).
- [ ] Hook stanza body 미터치 (MONO-099 catalog ↔ MONO-100 fixture body sync 가드 보존).
- [ ] Production code / fixture 기존 assertion shape / platform/hardstop-rules.md = 0 변경.

# Related Specs

- [`.claude/hooks/hardstop-detect.ps1`](../../../.claude/hooks/hardstop-detect.ps1) — fix target (L266-280 simulation 분기)
- [`.claude/hooks/__tests__/_helpers.ps1`](../../../.claude/hooks/__tests__/_helpers.ps1) — existing `Invoke-Hook` / `Assert-Allowed` / `Assert-Stanza` 사용
- [`.claude/hooks/__tests__/run-all.ps1`](../../../.claude/hooks/__tests__/run-all.ps1) — fixture 등록 update
- [`platform/hardstop-rules.md`](../../../platform/hardstop-rules.md) — HARDSTOP-10 canonical (미터치, sync 영향 0)
- 메모리 reference: `project_adr_mono_012_d3_cycle_complete.md` § "Edit hook CRLF/LF simulation mismatch 3 instance 누적 → 4 instance 시 hook fix 별 task" (본 task 의 trigger source)
- 4 instance 사례:
  1. MONO-083 (in-progress second-Edit AC checkbox)
  2. MONO-093 (`.claude/agents/common/coordinator.md` Edit RULE-CONSISTENCY-02 false-positive)
  3. MONO-095 (SCM procurement-service `## Service Identity` rename, partial-state)
  4. MONO-101 (fan-platform artist-service architecture.md Composition H3 add)

# Related Contracts

없음.

# Edge Cases

1. **Mixed line endings in 같은 file**: file 안 CR LF / LF 혼재 시 `Replace("` `r` `n", "` `n")` 만으로 CRLF normalize 가 부분 진행. 그러나 modern Git 환경에서 매우 드문 case + 본 fix 의 가치 = "raw fail → normalize 한 번 retry, 이후 fallback". 두 차례 fail 시 기존 fallback path 와 동일. **회귀 0**.

2. **oldString 이 file 안 여러 위치 매치**: Edit tool 는 이미 단일 매치만 허용 (위반 시 fail). Hook simulation 도 첫 occurrence 만 replace — 기존 behavior 와 동일.

3. **normalize 후 newString 의 line ending**: normalize fallback path 가 newString 도 normalize. 결과 simContent 가 LF only — hook 후속 regex (`(?im)^#+\s*Service\s+Type`) 는 line ending agnostic 이므로 영향 0.

4. **fail-open 보장**: 두 분기 모두 fail 시 `simContent = $existing` (변경 미반영) — 기존 fallback path 와 동일. 새 path 도입으로 인한 hook crash 가능성 0 (PowerShell `Replace` / `Contains` 는 null-safe — 단 `$existingNorm`, `$oldNorm` 둘 다 string 보장).

5. **Performance**: hook 호출 당 추가 `Replace("` `r` `n", "` `n")` 2-3 호출 (fallback path 만, 정상 case 는 미실행). hook 가 호출되는 frequency (Edit/Write per turn) × file 크기 — micro-overhead, no impact.

6. **PowerShell -replace vs .Replace()**: PowerShell `-replace` 는 regex, `.Replace()` 는 literal. Line ending normalize 에는 literal `.Replace("` `r` `n", "` `n")` 또는 regex `-replace "` `r` `n", "` `n"` 둘 다 안전 (regex meta 미포함). 본 fix 는 `-replace` (string operator) 사용 — 단일 line 으로 깔끔 + 기존 hook code 의 style 답습.

# Failure Scenarios

A. **Fix 후 정상 case 회귀**: Edit oldString 이 file 과 정확 일치 (line ending 일치) 시 1단 path 가 처리, normalize fallback 실행 안 함. 기존 fixture (HARDSTOP-05 lifecycle move negative 등) PASS 유지 검증 = run-all.ps1 22 PASS 보존.

B. **Hook crash on null/empty inputs**: `$existing` null / empty 시 outer `if` 가 가드 (`$existing -and $oldString -and $newString`). 변경 없음.

C. **새 fixture 가 detect 못 함**: synth CRLF architecture.md 작성 시 PowerShell `Set-Content` 의 default encoding / line ending 가 OS-dependent. 명시적 `[System.IO.File]::WriteAllText` + CRLF 직접 작성 으로 안정 보장.

D. **CI 회귀**: Hook runtime 변경 → `.ps1` 변경이 `code-changed` filter true → 전 pipeline 활성. Hook 동작은 PreToolUse (Edit/Write) 만 영향 — CI runtime 영향 0. Test fixture 도 local-only (`run-all.ps1` is "developer-run only, not CI-gated"). 회귀 0 기대.

---

# Implementation Notes (작성 시 참고)

- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (focused hook code edit + fixture 신설, low judgment)
- D4 OVERRIDE 적용 — refactor-spec / validate-rules cycle 의 자연 연장, MONO-091/093~101 sibling (ADR-MONO-003a § D1.1)
- Lifecycle = ready → review 직접 (single-PR closure 18번째 적용)
- 묶음 근거 = single PR (hook fix + fixture + run-all 등록 모두 한 PR — 작은 scope, related closure)
- **4-instance closure 가치**: 본 fix 후 향후 architecture.md / spec file Edit 시 hook PowerShell 우회 불필요 — manual sync 부담 자동 해소. 5번째 instance 발생 자체 차단.
