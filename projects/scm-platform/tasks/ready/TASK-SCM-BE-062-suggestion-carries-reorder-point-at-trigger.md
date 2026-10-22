# Task ID

TASK-SCM-BE-062

# Title

보충 제안이 «발동 당시 재주문점»을 싣는다 — 콘솔이 «재주문점 · 발동 재고 · 제안 수량» 을 한 줄 막대로 그려 «이 제안이 얼마나 급한가» 를 보이게 하는 데이터 (SKU 마다 정책을 따로 조회하지 않고)

# Status

ready

# Owner

backend

# Task Tags

- code
- test
- contract

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (계약 변경 · ADR-MONO-017 범위 판정 · 스냅샷 의미)
> 기안 2026-10-22 UTC — 콘솔 그래프 조사(`TASK-PC-FE-357`~`362`)의 «데이터가 더 필요한 후보» 중 명분이 커지는 유일한 것(소유자 «어», 2026-10-22 KST). **ready 만 — 착수는 소유자 지시 때.** 서버 변경 ⇒ 배포는 **굽기**.

# Dependency Markers

- 출처: 콘솔 그래프 조사 — 보충 제안 행은 `suggestedQty` · `triggerAvailableQty` 만 있어 «막대의 절반» 만 그릴 수 있었다(재주문점 없음).
- 후속(이 티켓 머지 · 굽기 뒤): 콘솔 티켓 — `/scm/replenishment` 표에 «재주문점 · 발동 재고 · 제안 수량» 한 줄 막대. 이 티켓이 닫힐 때 기안한다.
- 선행 없음.

# Goal

지금 `GET /api/v1/demand-planning/suggestions` 의 한 행(`specs/contracts/http/demand-planning-api.md:60-85`)은 `suggestedQty` · `triggerAvailableQty` 를 갖지만 **그 제안을 발동시킨 재주문점**은 없다. 재주문점은 `GET /policies/{skuCode}`(`:137-145`)에 따로 있어, 콘솔이 막대를 그리려면 **SKU 마다 한 번씩** 정책을 불러야 한다(20행이면 20호출). 제안이 발동될 때의 정책 값(`reorderPoint` · 필요하면 `safetyStock`)을 제안 행에 **함께 저장하고 응답에 싣는다**.

# Scope

## In Scope

- **AC-0 측정** (file:line):
  - ① 제안이 만들어지는 경로(`source=ALERT` 등) — 그 시점에 정책 값을 이미 읽는가(읽는다면 저장만 더하면 된다).
  - ② `ADR-MONO-017` 의 기각 갈래 B(«도메인마다 BFF 용 `/summary` · `/dashboard-card` 집계 엔드포인트», `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md:65`)에 이 변경이 해당하는가 — 이 티켓은 **새 엔드포인트도 집계도 아니고**, 제안 한 건이 자기 발동 조건을 기록하는 것이다. 해당하면 STOP(ADR 개정 먼저).
  - ③ 같은 제안 응답을 쓰는 소비자 전수(콘솔 · 다른 서비스 · 시험) — 필드 **추가**가 깨는 곳이 있는가(zod `strict` · 계약 시험).
  - ④ 기존 제안 행(마이그레이션 전 데이터)에 값이 없을 때의 표현(`null`) · 데모 시드.
- **소유자 결정 (AC-0 뒤 선택창)**: 실을 값의 의미 — **(가) 발동 당시 스냅샷**(추천: 정책이 나중에 바뀌어도 «재고 5 ≤ 재주문점 10 이라 발동» 이 정직하다) / (나) 응답 시점의 현재 정책(조인 — 바뀐 정책이 옛 제안을 거짓으로 설명할 수 있다).
- 계약 먼저: `demand-planning-api.md` 의 제안 모양에 필드 추가(`reorderPointAtTrigger` 같은 이름 — 의미가 이름에 드러나게) · 이벤트 계약에 제안 이벤트가 있으면 함께 판정.
- 구현: 스키마 마이그레이션(nullable 열) · 발동 시 저장 · 응답 매핑 · 시험(발동 → 값 저장 · 정책 변경 뒤에도 제안 값 불변(가 일 때) · 옛 행 `null` · 계약 시험).

## Out of Scope

- 콘솔 화면(후속 티켓).
- 정책 API · 제안 상태 기계 변경.
- 현재 재고(inventory-visibility) 를 제안에 싣는 것 — «발동 재고» 와 다른 값이고 실시간성 문제가 있다.

# Acceptance Criteria

- [ ] **AC-0** — 위 ①~④. ② 가 «해당» 이면 STOP.
- [ ] **AC-1** — 소유자 결정(가/나) 선택창 · 계약 먼저.
- [ ] **AC-2** — 구현 + 시험(위 목록) · CI 의 scm 잡 초록.
- [ ] **AC-3** — 굽기 뒤 라이브: 데모에서 새로 발동된 제안 응답에 값이 실리고, 옛 제안은 `null`(다음 데모 창). 닫을 때 콘솔 후속 티켓 기안.

# Related Specs

- `projects/scm-platform/specs/services/demand-planning-service/` (architecture · data-model — AC-0 에서 이름 확인)
- `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md` (§ 갈래 B — 범위 판정)

# Related Contracts

- `projects/scm-platform/specs/contracts/http/demand-planning-api.md` (갱신 대상)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § SCM 보충(읽기 — 소비자 영향)

# Edge Cases

- 정책이 없는 SKU 로 발동된 제안(가능한가 — AC-0 ①) → `null`.
- 같은 SKU 의 정책이 제안 사이에 바뀜 → (가) 면 제안마다 다른 값이 정상.
- 콘솔 zod 가 응답 필드에 `strict` 면 필드 추가가 콘솔을 깬다 → AC-0 ③.

# Failure Scenarios

1. 현재 정책을 조인해 «발동 당시» 처럼 보인다 → 이름 · 시험으로 의미를 고정.
2. 필드 추가가 콘솔 파서를 깨 보충 화면이 저하된다 → AC-0 ③ + 콘솔 계약 시험.
3. 굽기 전에 콘솔 후속을 머지 → 운영 콘솔이 없는 필드를 기다린다(빈 막대) — 후속은 이 티켓의 AC-3 뒤.
