# Task ID

TASK-PC-FE-366

# Title

콘솔 주문 상태 변경에서 «확정» 을 뺀다 — 운영자가 할 수 있는 변경은 «취소» 하나 (서버 `TASK-BE-648` 의 짝)

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (전이표 · 대화상자 · 가이드 문구)
> 기안 2026-10-22 UTC — 소유자 결정 2026-10-22 KST(«운영자 확정 없애기 · 티켓»). 서버 짝 = `TASK-BE-648`.

# Dependency Markers

- 짝: `projects/ecommerce-microservices-platform/tasks/ready/TASK-BE-648-*.md`. 🔵 이 콘솔 변경은 **서버보다 먼저 머지돼도 안전**하다(선택지를 줄일 뿐 — 서버는 굽기 전까지 확정을 받아도 콘솔이 안 낸다). 반대 순서(서버 먼저)는 운영자에게 400 이 보이므로 피한다.

# Goal

- 콘솔 전이표 `features/ecommerce-ops/api/order-types.ts:79` — `PENDING: ['CONFIRMED', 'CANCELLED']` · 머리말 `:23-25`.
- «확정» 을 빼서 대기 주문에서도 «취소» 만 보인다. 확정은 결제 · 재고 예약이 끝나면 자동으로 된다(가이드 문구와 맞춘다).

# Scope

## In Scope

- **AC-0 측정**: ① `allowedTransitions` · 상태 변경 대화상자 · 가이드(`features/ecommerce-guide/data.ts` 의 «운영자는 확정하거나 취소할 수 있습니다» 류) · 시험 · e2e 의 «확정» 전수 ② 대기 주문에서 «취소» 만 남았을 때 대화상자 모양(선택지 하나 — 버튼으로 바꿀지 그대로 둘지).
- 구현: 전이표에서 `PENDING → CONFIRMED` 제거 · 가이드 문구 사실대로 · 시험.

## Out of Scope

- 서버(`TASK-BE-648`).

# Acceptance Criteria

- [ ] **AC-0** — 위 ①②.
- [ ] **AC-1** — 구현 + **사용자에게 보이는 바뀐 문구(가이드 · 대화상자) 목록을 소유자에게 보이고 승인** 받은 뒤 머지.
- [ ] **AC-2** — 시험 + 기존 스위트(`--maxWorkers=4 --minWorkers=2`) · `tsc --noEmit` · `next lint` · Client graph 가드 둘 · 큐 가드 셋 rc=0. 대화상자가 클라이언트 전용이면 nightly 는 판단(SSR 변화 없음을 기록).
- [ ] **AC-3** — 배포(Vercel Production 기록) · 익명 샘플에서 대기 주문 상세의 상태 변경 선택지 확인.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/order-api.md` (운영자 상태 변경 — 읽기)

# Edge Cases

- 대기 주문에 선택지가 «취소» 하나 — 대화상자가 빈 선택 상태를 만들지 않게.

# Failure Scenarios

1. 가이드가 여전히 «운영자는 확정할 수 있다» 고 말한다 → AC-0 ① 전수.
