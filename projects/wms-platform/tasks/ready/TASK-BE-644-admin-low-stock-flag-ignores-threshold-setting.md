# Task ID

TASK-BE-644

# Title

admin-service 재고 읽기 모델의 `lowStockFlag` 가 저재고 설정(`inventory.low_stock.default_threshold_qty`)을 무시하고 상수 10 을 쓴다 — 계약은 «현재 설정으로 재계산» 이라 적는다 · 곁: `domain-model.md` 의 «Mirrors Order status» 드리프트

# Status

ready

# Owner

backend

# Task Tags

- code
- test
- spec

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (프로젝션 · 설정 소비 · 재계산 범위 판정)
> 기안 2026-10-22 UTC — `TASK-PC-FE-364` 측정의 곁 발견 + `TASK-PC-FE-362` 의 스펙 드리프트 관찰(소유자 «진행», 2026-10-22 KST). **ready 만 — 착수는 소유자 지시 때.** 서버 변경 ⇒ 배포는 **굽기**.

# Dependency Markers

- 짝: `projects/platform-console/tasks/ready/TASK-PC-FE-365-*.md`(콘솔의 저재고 설명을 사실대로 — 이 티켓이 바꾸면 그 문구도 따라간다).
- 선례: `TASK-BE-459`(done — inventory-service 가 같은 설정을 `admin.settings.changed` 로 소비해 **알림** 임계값을 라이브로 바꾼다).

# Goal

- 계약 `specs/contracts/http/admin-service-api.md:281-282` 는 `lowStockFlag` 가 «현재 `inventory.low_stock.default_threshold_qty` 설정으로 재계산» 된다고 적는다.
- 실제 코드: `apps/admin-service/.../application/projection/InventoryProjectionService.java:54-55` `DEFAULT_LOW_STOCK_THRESHOLD = 10` (주석 «v1 default») · `:341` `boolean lowStock = availableQty <= DEFAULT_LOW_STOCK_THRESHOLD;`
- 결과: 운영자가 콘솔 WMS 운영 설정에서 임계값을 25 로 바꾸면 **inventory-service 의 저재고 알림은 25 기준**(BE-459)으로 오는데, **콘솔의 «저재고» 표시 · `lowStockOnly` 필터 · 통합 개요 «저재고 재고 보기»** 는 계속 10 기준이다 — 같은 개념이 두 기준으로 갈린다.
- 곁(문서만): `specs/services/admin-service/domain-model.md:379` 의 «Mirrors Order status» 는 코드와 다르다 — 출고 주문 요약은 상태를 `RECEIVED` · `SHIPPED` · `CANCELLED` 셋으로 접는다(`OrderSummaryEntity.java:123,128,133`; 콘솔 `overview-state.ts:195-197` 이 이미 적음). `TASK-PC-FE-362` 가 이 드리프트 때문에 계획을 바꿨다.

# Scope

## In Scope

- **AC-0 측정** (file:line):
  - ① 계약 · 도메인 모델 · 코드의 세 진술 대조(`admin-service-api.md:281-282` · `domain-model.md:221, 435` · 위 코드).
  - ② admin-service 가 설정 변경을 알 수 있는 경로 — 자기 설정 표(설정의 주인)를 프로젝션 시점에 읽을 수 있는가 · 이벤트를 자기 자신이 소비해야 하는가.
  - ③ **재계산 범위**: 설정이 바뀌면 기존 스냅샷 행의 `lowStockFlag` 를 일괄 재계산해야 하는가(계약 «recomputed» 의 뜻) — 행 수 · 비용 · 다음 재고 이벤트까지 기다리는 대안.
  - ④ 알림(inventory-service)과 표시(admin)의 기준이 같은 값을 보게 되는 순서 · 경합.
- **소유자 결정 (AC-0 뒤 선택창)**: (가) 설정을 따르게 고친다(추천 — 계약대로) / (나) 상수를 계약에 명시하고 계약 문장을 고친다(설정은 알림 전용).
- 스펙 먼저: 결정에 맞게 계약 · 도메인 모델을 고치고, `domain-model.md:379` 드리프트도 «세 상태로 접는다» 로 바로잡는다(이유 · 출처 보존).
- 구현 · 시험(설정 변경 → 플래그 재계산 · 경계값 · 설정 없음 = 기본 10 · 잘못된 값 무시).

## Out of Scope

- SKU 별 · 창고별 임계값(지금 설정은 GLOBAL 하나).
- 콘솔 문구(`TASK-PC-FE-365`).

# Acceptance Criteria

- [ ] **AC-0** — 위 ①~④.
- [ ] **AC-1** — 소유자 결정(가/나) 선택창 · 스펙 먼저.
- [ ] **AC-2** — 구현 + 시험 · CI 의 wms 잡 초록.
- [ ] **AC-3** — 굽기 뒤 라이브: 설정을 바꾸면 콘솔 저재고 필터 결과가 따라 바뀌는가(다음 데모 창).

# Related Specs

- `projects/wms-platform/specs/services/admin-service/domain-model.md` (§ settings · § 저재고 · `:379`)
- `projects/wms-platform/specs/services/admin-service/architecture.md`

# Related Contracts

- `projects/wms-platform/specs/contracts/http/admin-service-api.md` (`:281-282` — 갱신 대상)
- `projects/wms-platform/specs/contracts/events/admin-events.md` (`:343` — `admin.settings.changed` 소비자 기대)

# Edge Cases

- 설정이 없거나(초기 기동) 범위 밖이면 기본 10.
- 설정 변경과 재고 이벤트가 동시에 오면 — 순서 무관하게 최종 상태가 맞아야 한다.

# Failure Scenarios

1. 플래그만 새 값으로 계산하고 기존 행은 그대로 둬, 필터 결과가 «바뀐 행 + 옛 행» 으로 섞인다 → AC-0 ③.
2. 계약 문장만 고치고 콘솔 문구(365)를 안 맞춘다 → 짝 티켓.
