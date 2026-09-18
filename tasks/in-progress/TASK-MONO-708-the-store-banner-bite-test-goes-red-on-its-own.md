# Task ID

TASK-MONO-708

# Title

🔴 web-store `DemoBackendNotice` 의 **bite 칸이 코드 변경 없이 빨개진다** — main 이 한 번 빨갛게 됐고 재실행만으로 초록이 됐다

# Status

in-progress (2026-09-17 UTC — AC-0 ⚪ 한계와 함께 · AC-1 ⚪ 원인 미지목 · AC-2 닫힘, AC-3 CI 대기)

# Owner

monorepo

# Task Tags

- ci
- flake
- web-store
- test-harness

---

# Goal

2026-09-17 UTC, `TASK-MONO-701` 의 impl PR([#3902](https://github.com/kanggle/monorepo-lab/pull/3902), squash `e4d032eec`)이 머지된 뒤 **main CI 가 실패**했다(런 35238255303):

```
FAIL src/widgets/demo-notice/__tests__/DemoBackendNotice.test.tsx
  > 🔴🔴 bite — 백엔드가 켜져 있든 꺼져 있든 껍데기의 출력이 **같다**
  AssertionError: expected '<div data-testid="host"><span data-te…' to be '<div data-testid="host"></div>'
  Test Files 1 failed | 128 passed (129) · Tests 1 failed | 975 passed (976)
```

🔴 그 PR 은 web-store 를 **한 줄도 안 건드렸다**(diff: 람다 · 람다 테스트 · ADR · 티켓 · INDEX). **실패한 잡만 재실행하니 success** ⇒ flake 로 판정했다(`TASK-MONO-701` § 닫음 (c)).

🔴 **이것이 왜 그냥 flake 가 아닌가**: 그 칸은 **bite** 다 — 「껍데기가 판정을 굽지 않는다」를 지키는 유일한 실행 비교이고(`TASK-MONO-654` 축), bite 가 무작위로 빨개지면 다음 사람은 그것을 **믿지 않게 된다**. 실패 모양(첫 호출 `up` 이 빈 `<div data-testid="host"></div>`, 둘째 `down` 이 마커를 그림)은 하네스가 `vi.resetModules()` + `vi.doMock()` 을 같은 테스트 안에서 두 번 쓰는 자리와 맞물려 있다(`shellHtml()` — 매 호출이 resetModules → doMock → dynamic import).

---

# Scope

## 포함

- 재현: 같은 파일을 반복 실행(예: `--repeat` 또는 루프 50회)해 실패율을 **수치로** 잰다. 🔴 «한 번 봤다» 는 빈도가 아니다.
- 원인 지목: 모킹 순서 · 병렬 실행(같은 파일 안의 두 테스트가 모듈 레지스트리를 공유) · 환경(Node 20 CI vs 24 로컬) 중 관측으로 하나.
- 하네스를 결정적으로 만든다(예: 자식 모킹을 `beforeEach` 로 올리거나, 두 호출을 별도 `describe`/`isolate` 로 가르거나, `vi.resetModules()` 위치를 바꾼다). 🔴 **단언을 느슨하게 만들지 마라** — 그러면 bite 가 죽는다.

## 제외

- 701 · 654 의 동작(둘 다 닫혔다).
- 프런트 스위트 전반의 러너 버전 정리(다른 축).

---

# Acceptance Criteria

- [ ] **AC-0 — 빈도를 잰다.** 로컬에서 그 파일을 N회(≥50) 돌려 실패율을 적는다. 🔴 로컬 러너가 CI 와 다르면(Node 버전 · vitest 버전) 그 사실을 함께 적고, CI 의 같은 잡 이력에서도 같은 칸의 실패를 찾는다(있으면 링크).
- [ ] **AC-1 — 원인을 관측으로 지목.** 가설별 대조군을 만든다(단독 실행 / 파일 전체 / 병렬 끔). 🔴 «모킹 순서 때문일 것» 은 판정이 아니다.
- [x] **AC-2 — 고치고 bite 가 살아 있는지 확인.** 고친 뒤 ① 제품 코드를 옛 판(껍데기가 판정을 굽는 판)으로 되돌리면 그 칸이 **여전히 빨개진다** ② 고친 하네스로 N회 돌려 실패 0.
- [ ] **AC-3 — CI 에서 확인.** 머지 뒤 그 잡이 초록이고, 이후 main 런에서 같은 칸의 실패가 없다(적어도 다음 5런 — 🔴 «없다» 는 창 밖에서도 확인 가능하므로 날짜가 아니라 런 수로 센다).

---

# Related Specs

- `projects/ecommerce-microservices-platform/apps/web-store/src/widgets/demo-notice/__tests__/DemoBackendNotice.test.tsx`
- `tasks/done/TASK-MONO-701-*` § 닫음 (c) — 이 flake 의 관측 기록
- `TASK-MONO-654` — 이 bite 가 지키는 결함

# Related Contracts

- 없음 — 테스트 하네스.

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| 로컬에서 재현 0회 | CI 이력으로 빈도를 잰다 — «로컬에서 안 난다» 는 닫는 근거가 아니다 |
| 원인이 러너 버전 | 그 사실을 적고 범위를 나눈다(이 티켓은 이 파일까지) |
| 고치면 bite 가 약해진다 | 🔴 그건 고친 게 아니다 — AC-2 ① 이 막는다 |

# Failure Scenarios

1. **재시도로 넘긴다** → 다음에 같은 칸이 빨개지면 또 «flake 겠지» 가 되고, 진짜 회귀를 그때 놓친다.
2. **단언을 느슨하게 해서 초록으로 만든다** → 지키던 결함이 다시 조용해진다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Sonnet 5** (테스트 하네스 한 파일. 판단은 AC-1 의 대조군 설계)

---

# 구현 기록 — 2026-09-17 UTC (분석·구현=Opus 5)

## AC-0 — 빈도: ⚪ **못 쟀다(두 경로가 각각 막혔다)** — 실측된 실패는 여전히 **1건**

| 경로 | 결과 |
|---|---|
| 로컬 반복(저장소 러너 그대로) | 🔴 **불가** — 이 호스트에 Node 24 뿐인데 web-store 는 vitest 4 이고 `ERR_PACKAGE_IMPORT_NOT_DEFINED`(`#module-evaluator`)로 **기동조차 못 한다**(실행해서 확인, 메모리의 기존 기록과 일치). 이 호스트엔 nvm·fnm·volta 도 없다 |
| 로컬 반복(러너를 바꿔서) | fan 워크스페이스의 **vitest 3** + 임시 설정(`oxc` → `esbuild: { jsx: 'automatic' }`)으로 § A 만 돌렸다 — **30회 실패 0**. 🔴 § B(클라이언트 7칸)는 이 러너에서 전부 죽는다(도구 불일치) ⇒ **러너가 다르므로 CI 의 빈도가 아니다** |
| CI 이력 | main CI 최근 40런에서 이 잡: 실행 **18** · 건너뜀 **21** · 목록상 실패 **0**. 🔴🔴 **그 0 은 거짓이다** — 우리가 본 실패(런 35238255303)는 **실패 잡만 재실행**해서 결론이 `success` 로 덮였고, `gh run list`/`run view` 는 **마지막 시도만** 본다. ⇒ 이 질의는 재실행된 flake 를 **구조적으로 과소계수**한다 |

⇒ 남는 사실: **관측된 실패 1건**(2026-09-17, #3902 머지 커밋, 976 중 1). 빈도는 모른다.

## AC-1 — 원인: ⚪ **관측으로 지목 못 했다** (그래서 «원인» 이 아니라 «그 모양이 가능한 구조» 를 고쳤다)

실패 메시지가 가리키는 것: `up`(첫 호출)이 **빈 출력**이고 `down`(둘째)에 마커가 있었다 ⇒ **첫 호출에서 모킹이 안 먹어 진짜 클라이언트가 렌더**됐다(탐침 전이라 아무것도 안 그린다). 껍데기는 조건 없이 자식을 렌더하므로(`DemoBackendNotice.tsx:36-38`) 다른 설명이 잘 붙지 않는다. 🔴 그러나 이것은 **메시지 해석**이지 재현이 아니다 — 위 AC-0 대로 재현을 못 했다.

🔵 구조는 이렇게 생겼었다: **한 파일**이 같은 모듈(`../DemoBackendNoticeClient`)에 대해 § A 에서는 테스트 실행 중 `vi.resetModules()` → `vi.doMock()` → 동적 `import()` 를 **호출마다** 하고, § B 에서는 `vi.doUnmock()` 후 진짜를 import 한다. 등록/해제가 실행 중에 오가는 구조다.

## AC-2 — 고침 + bite

- **§ A 를 별도 파일로 분리**: `__tests__/DemoBackendNoticeShell.test.tsx` — 모킹을 **파일 최상단 `vi.mock`**(호이스팅)으로 올리고 껍데기를 **정적 import** 한다. 테스트 실행 중 등록/해제가 **없다**.
- 원래 파일에서 § A 와 그 `doMock` 하네스를 **지웠다** ⇒ 그 파일은 이제 클라이언트를 모킹하지 않는다(§ B 만 남는다).
- 확인(러너: vitest 3, 위 단서 그대로):
  - 새 껍데기 파일 **30회 실패 0**.
  - 🔴 **bite 는 살아 있다**: 껍데기를 «판정을 굽는» 옛 모양으로 되돌리자(상태를 fetch 해 조건부로 배너) **2칸 전부 빨강**, 복원 후 초록(주입 마커 0건 확인).
- ⚪ 저장소 러너(vitest 4/Node 20)로의 실행은 **CI 가 권위**다 — 이 호스트에서 불가능하다(AC-0).

## AC-3 — ⏳ CI

머지 뒤 `Frontend unit tests` 잡이 초록이고, **다음 5런**에서 같은 칸의 실패가 없는지 본다. 🔴 날짜가 아니라 **런 수**로 센다. 🔵 그리고 이 티켓이 배운 것을 세어라 — 실패를 세려면 `gh run view`(마지막 시도)가 아니라 **시도(attempt)** 를 봐야 한다.

---

## 🔴 AC-3 정정 — «5런» 이 아니라 **«실행 5회»** 다 (2026-09-18 UTC)

AC-3 은 이렇게 적혀 있다: *"머지 뒤 그 잡이 초록이고, 이후 main 런에서 같은 칸의 실패가
없다(적어도 **다음 5런** — 🔴 «없다» 는 창 밖에서도 확인 가능하므로 날짜가 아니라 런 수로 센다)."*

🔴 **날짜 대신 런을 센 것은 옳았는데, 런과 실행이 다르다.** `Frontend unit tests` 는 **경로
게이팅** 잡이라 태스크 파일만 건드린 PR 에서는 `SKIPPED` 된다. 이 저장소의 머지는 대부분
태스크를 건드리므로, «5런» 으로 세면 flake 에게 **기회를 2번만 주고** 닫게 된다.

**실측 (708 머지 = `319040f00`, 2026-09-18T03:10Z 이후 main CI 런 전부):**

| main 커밋 | `Frontend unit tests` |
|---|---|
| `42ec0d262` | 🟢 success |
| `dbbe85f19` | ⚪ **skipped** (태스크 전용 PR) |
| `28bb47056` | 🟢 success |
| `a47aa32cd` | ⚪ **skipped** (태스크 전용 PR) |
| `30e876bcb` | ⏳ (이 기록 시점에 도는 중) |

⇒ **5런이 지났지만 실제 실행은 2회**다(둘 다 초록, 그 칸의 실패 0).

**그러므로 AC-3 은 «실행 5회» 로 읽는다** — 건너뛴 런은 기회가 아니다. 현재 **2/5**.
🔵 이 구별은 이 저장소가 이미 문서화한 축과 같다: *"Four are required, but on most PRs fewer
than four actually run"* (`CLAUDE.md` § Task Rules · `TASK-MONO-601`). 같은 함정을 내가 쓴
AC 에서 한 번 더 밟았다.

🔵 세는 법(다음 사람용) — 🔴 `gh run list --commit <sha>` 는 **조용히 0행**을 내므로 쓰지 마라:

```bash
wf=$(gh api repos/kanggle/monorepo-lab/actions/workflows --jq '.workflows[]|select(.name=="CI")|.id')
gh api "repos/kanggle/monorepo-lab/actions/workflows/$wf/runs?branch=main&per_page=10" \
  --jq '.workflow_runs[] | "\(.created_at)\t\(.head_sha[0:9])\t\(.id)"'
# 각 run id 로:
gh api "repos/kanggle/monorepo-lab/actions/runs/<id>/jobs?per_page=100" \
  --jq '.jobs[]|select(.name|startswith("Frontend unit tests"))|.conclusion'
```
