# Task ID

TASK-MONO-708

# Title

🔴 web-store `DemoBackendNotice` 의 **bite 칸이 코드 변경 없이 빨개진다** — main 이 한 번 빨갛게 됐고 재실행만으로 초록이 됐다

# Status

ready

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
- [ ] **AC-2 — 고치고 bite 가 살아 있는지 확인.** 고친 뒤 ① 제품 코드를 옛 판(껍데기가 판정을 굽는 판)으로 되돌리면 그 칸이 **여전히 빨개진다** ② 고친 하네스로 N회 돌려 실패 0.
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
