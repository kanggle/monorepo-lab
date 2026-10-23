# Task ID

TASK-MONO-810

# Title

`ADR-MONO-084` P5 — SCM 실적 지표: 발주 건수 · 발주 금액 · 입고 완료 건수(제출 시각 귀속) · 발주 목록 날짜 필터 · 콘솔 개요 실적 줄

# Status

review

# Owner

monorepo

# Task Tags

- code
- api
- scm
- platform-console
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (발주 금액의 통화 · 상태 기계 전이 시각)
> 🔴 **cross-project 원자 PR**: procurement-service 계약 · 엔드포인트와 콘솔 화면을 한 PR 에.

# Dependency Markers

- **선행**: `TASK-MONO-806`(P1) `main` 머지.
- 형제: `TASK-MONO-807`~`809` · `811` · `812`.

# Goal

SCM 개요(`/scm`)는 발주 · 재고 스냅샷 건수와 발주 상태 분포만 보여 준다. 맨 위에 이번 주 · 이번 달 **발주 건수 · 발주 금액 · 입고 완료 건수**와 직전 대비 · 일별 추이를 붙인다. 발주 목록 API 에는 날짜 필터가 없어서(`procurement-api.md` § `GET /po`, 필터 = `status` · `supplierId` 뿐), 실적 카드에서 목록으로 넘어갈 때 기간을 걸 수 없다 — 그 필터도 더한다.

# Scope

## In Scope

- 계약 먼저: `procurement-api.md` 에 `…/metrics?period=week|month` (P1 공용 모양) · 지표 `poCount` · `poAmount` · `receivedCount` 의 포함 상태 · 귀속 시각 · 제외(ADR-084 D5: 제출 `SUBMITTED` 이후 · `DRAFT` · `CANCELED` 제외) · `GET /po` 에 `submittedFrom` / `submittedTo`(이름은 계약에서 확정).
- procurement-service: 집계 · 날짜 필터 · 인가(ADR-083 P5 세분 인가의 조회 역할).
- 콘솔: `features/scm-ops` 개요에 P1 «실적 줄» · 카드에서 기간 걸린 발주 목록으로.

## Out of Scope

- 공급사 납기 준수율 · 발주 → 입고 소요 시간.
- 수요계획 · 물류 · 재고 가시성 지표.

## AC-0 기록 (2026-10-24 UTC, origin/main `6f5431876`)

| 질문 | 실측 |
|---|---|
| 전이 시각 | `purchase_orders` 에 `submitted_at` · `acknowledged_at` · `confirmed_at` · `canceled_at`(V1:42-58). 🔴 **입고(`PARTIALLY_RECEIVED` · `RECEIVED`) 시각 칸은 없다** — `po_status_history.occurred_at`(전이마다 한 행, ASN 경로 포함 `PurchaseOrderApplicationService.java:399-403`)에만 있다. `SETTLED` · `CLOSED` 는 전이 메서드가 없다(v1 에서 도달 불가). |
| 재제출 | 없다 — `submit` 은 DRAFT 에서만 · DRAFT 로 돌아가는 전이 없음 ⇒ `submitted_at` 은 한 번만 쓰인다 |
| 제출 뒤 취소 | SUBMITTED · ACKNOWLEDGED · CONFIRMED 에서 가능(`PoStatusMachine.java:39-63`; 계약 `procurement-api.md:274-292` 의 «CONFIRMED+ 취소 불가» 는 코드와 다르다 — 낡은 문구) · `submitted_at` 유지 + `canceled_at` |
| 금액 | `total_amount NUMERIC(18,2)` · 🔴 **통화는 발주마다, 자유 3 자**(`@Size(3)` 만 — 비 ISO 가능) · 시드 · 샘플 = KRW, 시험 = USD 도 · 줄에서 재계산 · NULL 불가 · 줄 없는 발주는 제출 불가 · 제안에서 만든 발주는 단가 0 ⇒ 합계 0 으로 제출됨 |
| 입고 완료 | `RECEIVED`(전량) · 경로는 공급사 ASN 웹훅뿐(WMS 소비 아님) |
| 테넌트 | 모든 표 `tenant_id` · JWT `tenant_id` → `ActorContext` · `TenantClaimEnforcer` 403 · 남의 id 는 404 |
| 인가 | `SecurityConfig.java:74-82` URL 게이트: `GET /api/procurement/**` = `SCM_PROCUREMENT_READ ∨ WRITE`(ADR-083 P5) · 거부 403 `PERMISSION_DENIED` |
| 🔴 400 | 이 서비스는 `IllegalArgumentException` 을 **422** 로 돌린다(`GlobalExceptionHandler.java:68-71`) — 공용 계약은 `period` 오류에 400 을 요구 ⇒ 별도 경로 필요 |
| 목록 필터 | `GET /po` = `status` · `supplierId` 뿐(JPQL 선택 인자) · 콘솔 목록 화면은 URL 쿼리를 안 읽는다 ⇒ 카드 → 기간 걸린 목록에는 화면 · 상태 · 훅 · BFF · 클라이언트를 다 고쳐야 한다 |
| 데모 시드 | `seed-scm.sh` 발주 3(DRAFT · SUBMITTED · CONFIRMED, KRW 100,000 씩, 시드 시각) · 입고 · 취소 0 |

**정한 것**

- `poCount` · `poAmount` — `submitted_at` ∈ 창 ∧ **지금** `status ≠ CANCELED`(ADR-084 D5 · Edge 기본값). 🔴 제출 뒤 취소되면 그 주의 값이 **나중에 줄어든다** — 계약에 적는다. `DRAFT` 는 `submitted_at` 이 없어 자연히 빠진다.
- `poAmount` — **통화마다 한 키** `poAmount:<통화>`(ADR-084 D2 · OD-3, 환산 없음). 창 안 통화별 합(BigDecimal)을 **한 번** 그 통화의 최소 단위로(`Currency.getDefaultFractionDigits`, 비 ISO 는 2) HALF_UP 반올림 — 발주마다 반올림하면 합이 어긋난다. 현재 · 직전 창에 나타난 통화의 합집합, 없으면 `poAmount:KRW` 0 한 줄.
- `receivedCount` — 🟢 **소유자 결정 2026-10-24 «입고된 시각 기준»**: `po_status_history` 의 `→ RECEIVED` 전이 `occurred_at` ∈ 창인 **발주 수**(같은 발주 두 번이면 한 번). 제출 시각과 무관 — 지난 값이 바뀌지 않는다.
- 🔴 **곁에서 찾은 결함(별도 티켓)**: ASN 웹훅이 상태 검사를 건너뛴다 — `PurchaseOrder.applyAsnLine`(`PurchaseOrder.java:301-335`)이 전량이면 상태와 무관하게 `RECEIVED` 로 바꾼다 ⇒ SUBMITTED · ACKNOWLEDGED · **CANCELED** 발주도 입고 완료가 되고, `receivedCount` 가 그것을 센다. 계약은 422 `PO_STATUS_TRANSITION_INVALID`(`procurement-api.md:400-405`).
- 발주 목록 필터 이름 `submittedFrom` / `submittedTo` — ISO-8601 instant, **반열린 `[from, to)`**(실적 창과 같은 규칙 — 카드에서 넘어온 목록의 건수가 실적 숫자와 같아야 한다; Failure «`createdAt` 으로 건다» 의 답).
# Acceptance Criteria

- [x] **AC-0** — 재측정: 발주 상태 기계(`DRAFT` → … → `CLOSED` / `CANCELED`)의 전이 시각 저장 여부 · 발주 금액 필드와 통화(여러 통화면 ADR-084 D2 대로 통화마다 한 줄) · 입고 완료의 정의(`RECEIVED` 인가 `CLOSED` 인가) · 테넌트 범위. 결과 → 이 파일 + 계약 문구.
- [ ] **AC-1** — 🔴 대조군: `DRAFT` · `CANCELED` 발주는 `poCount` · `poAmount` 를 안 움직인다. 제출 하나 → 움직인다.
- [ ] **AC-2** — 날짜 필터 시험(경계 포함/제외 · 다른 필터와 조합) · 경계 시험(`Clock` 주입, `TZ=UTC` · `TZ=Asia/Seoul`) · 테넌트 격리 · 인가.
- [x] **AC-3** — 콘솔: 실적 줄 · 칸 실패 · 401 · 샘플 모드 응답 · 카드 → 기간 걸린 목록.
- [ ] **AC-4** — 배포 상태(`main` · AMI · 데모 창 라이브)를 따로 적는다.

# Related Specs

- `docs/adr/ADR-MONO-084-domain-overview-performance-metrics.md` D2 · D4 · D5
- `projects/scm-platform/specs/services/procurement-service/architecture.md`

# Related Contracts

- `projects/scm-platform/specs/contracts/http/procurement-api.md` § `GET /po` · 새 `/metrics`
- P1 공용 실적 응답 모양

# Edge Cases

- 제출 뒤 취소된 발주 → 제출 주에 잡혔다가 취소 뒤 빠지는가 → AC-0 에서 정한다(기본: 현재 `CANCELED` 면 뺀다 — 지난 값이 바뀔 수 있다고 계약에 적는다).
- 금액이 없는 발주(초안 줄 없음) → 0 원으로 세지 않고 AC-0 에서 수를 센다.

# Failure Scenarios

- 금액을 통화 무시하고 더한다 → 원 · 달러가 섞인 숫자.
- 날짜 필터를 `createdAt` 으로 건다 → 실적(제출 기준)과 목록(생성 기준)이 서로 다른 건수를 보인다.

## 검증 (2026-10-24 UTC, 로컬 · Windows 호스트 · Docker 없음)

| 무엇 | 결과 |
|---|---|
| procurement-service 단위 · 슬라이스 (내가 다시 돌림) | rc=0 · **298 / 298** (새 `PurchaseOrderMetricsServiceTest` 12 · `PurchaseOrderMetricsControllerSliceTest` 10 · 게이트 +3) |
| 🔴 bite (에이전트) | UTC 버킷 · CANCELED 제외 제거 · 반올림 DOWN · 발주마다 먼저 반올림 → 넷 다 빨강, 복원 rc=0 |
| 콘솔 `tsc --noEmit` (내가 다시 돌림) | rc=0 |
| 콘솔 scm- · Scm · sample- · f5- · performance- · 다른 도메인 개요 (내가 다시 돌림) | rc=0 · **59 파일 976** |
| 콘솔 전체 · `next build` (에이전트) | 5393 / 5393 · rc=0 |
| ⚪ `PurchaseOrderMetricsIntegrationTest` (Postgres Testcontainers, 9) | **CI 가 첫 판정** — 대조군 · 다통화 · 반열린 창 · `receivedCount`(실제 ASN) · `GET /po` 필터 · 카드 목록 = `poCount` · 테넌트 격리 |

**AC 판정 메모**

- AC-1 🔴 대조군 — DRAFT · 제출 뒤 취소 → 0, 제출 하나 → 1 / 금액. 단위(서비스가 제외를 한다 — bite 로 물림) + IT(실 DB) ⇒ IT 판정 전이라 [ ].
- AC-2 — 경계(고정 `Clock` + JVM 기본 시간대 UTC · Asia/Seoul) · 짧은 달 직전 창 · 인가(READ/WRITE 통과, 그 밖 403, 토큰 없음 401, 다른 테넌트 403) 는 초록. 날짜 필터 경계 · 다른 필터와 조합 · 테넌트 격리는 IT ⇒ [ ].
- AC-3 — 실적 줄 · 칸 실패 · 401 · 샘플 모드 · 🔵 **카드 → 기간 걸린 목록**: `poCount` · `poAmount:*` 카드 = `/scm/procurement?submittedFrom=…&submittedTo=…&excludeStatus=CANCELED` ⇒ 그 목록의 `totalElements` = 카드의 `poCount`(같은 귀속 · 같은 반열린 범위 · 같은 제외). 🔴 처음엔 목록에 «취소 제외» 가 없어 두 숫자가 갈렸다 — `GET /po` 에 `excludeStatus` 를 더해 맞췄고, 샘플 · IT 둘 다 «빼면 달라진다» 를 단언해 문다. `receivedCount` 카드는 `?status=RECEIVED`(입고 시각 ≠ 제출 시각이라 기간을 안 건다). 남은 틈: 응답의 `current.to` 가 초 단위라 조회 직전 1 초 안의 제출은 숫자엔 들고 목록엔 빠진다(계약에 적음).
- 곁에서 고친 것: 계약의 «CONFIRMED 이후 취소 불가» 문구를 코드(`PoStatusMachine`)에 맞춤.
- 곁에서 찾은 결함: ASN 이 상태 검사를 건너뜀 → `TASK-SCM-BE-063` 기안(이 PR 에 포함).

## 배포 상태

- `main` ⏳ 이 PR.
- AMI ⚪ 다음 굽기(procurement-service 새 이미지 + V7 인덱스). 굽기 전 데모의 실적 줄은 «불러올 수 없음». 🔵 옛 백엔드는 `submittedFrom` · `submittedTo` · `excludeStatus` 를 모르는 인자로 **조용히 무시**하므로, 손으로 만든 그 주소는 기간 없는 전체 목록을 보인다 — 다만 그때는 실적 줄이 degraded 라 카드(링크)가 없어 화면에서 그 경로로 갈 수 없다.
- 라이브 ⚪ 데모 창: 신선 시드면 이번 달 발주 2(SUBMITTED · CONFIRMED) · 금액 KRW 200,000 · 입고 완료 0.
