# Task ID

TASK-MONO-810

# Title

`ADR-MONO-084` P5 — SCM 실적 지표: 발주 건수 · 발주 금액 · 입고 완료 건수(제출 시각 귀속) · 발주 목록 날짜 필터 · 콘솔 개요 실적 줄

# Status

ready

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

# Acceptance Criteria

- [ ] **AC-0** — 재측정: 발주 상태 기계(`DRAFT` → … → `CLOSED` / `CANCELED`)의 전이 시각 저장 여부 · 발주 금액 필드와 통화(여러 통화면 ADR-084 D2 대로 통화마다 한 줄) · 입고 완료의 정의(`RECEIVED` 인가 `CLOSED` 인가) · 테넌트 범위. 결과 → 이 파일 + 계약 문구.
- [ ] **AC-1** — 🔴 대조군: `DRAFT` · `CANCELED` 발주는 `poCount` · `poAmount` 를 안 움직인다. 제출 하나 → 움직인다.
- [ ] **AC-2** — 날짜 필터 시험(경계 포함/제외 · 다른 필터와 조합) · 경계 시험(`Clock` 주입, `TZ=UTC` · `TZ=Asia/Seoul`) · 테넌트 격리 · 인가.
- [ ] **AC-3** — 콘솔: 실적 줄 · 칸 실패 · 401 · 샘플 모드 응답 · 카드 → 기간 걸린 목록.
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
