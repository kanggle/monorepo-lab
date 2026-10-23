# Task ID

TASK-MONO-812

# Title

`ADR-MONO-084` P7 — Finance 실적 지표: 유형별 거래 건수 · 금액(확정 거래만 · 캡처된 보류만 · 통화마다 따로) · 콘솔 개요 실적 줄

# Status

ready

# Owner

monorepo

# Task Tags

- code
- api
- finance
- platform-console
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (돈 · 보류/캡처 의미 · 다통화 · 계정 목록이 의도적으로 없는 도메인)
> 🔴 **cross-project 원자 PR**: finance 계약 · 엔드포인트와 콘솔 화면을 한 PR 에.

# Dependency Markers

- **선행**: `TASK-MONO-806`(P1) `main` 머지.
- 형제: `TASK-MONO-807`~`811`.

# Goal

Finance 개요(`/finance`)는 원장 집계(시산표 · 열린 회계기간 · 대사 차이 · 환율)와 운영자 기본 계정 하나만 보여 준다. 맨 위에 이번 주 · 이번 달 **유형별 거래 건수 · 금액**과 직전 대비 · 일별 추이를 붙인다.

# Scope

## In Scope

- 계약 먼저: 거래가 사는 서비스(account-service 또는 ledger-service — AC-0)의 계약에 `…/metrics?period=week|month` (P1 공용 모양). 지표 = 거래 유형(충전 · 이체 · 캡처 …)별 건수 · 금액. ADR-084 D5: **확정된 돈만** — 보류(hold)는 캡처된 금액만, 해제(release)된 보류는 0. OD-3 기본값: 통화마다 한 줄, 환산 없음.
- 🔴 `account-api.md` 는 `GET /accounts` 를 **의도적으로** 두지 않는다(계정 목록 노출 금지). 집계는 테넌트 전체 합계만 내려주고 계정별 행은 내려주지 않는다.
- 콘솔: `features/finance-overview` 에 P1 «실적 줄» · 통화마다 KPI 를 따로 그린다.

## Out of Scope

- 분개 건수 · 대사 차이 발생/해소 추이 — 계약 변경으로 나중에.
- 환율 환산 합계.

# Acceptance Criteria

- [ ] **AC-0** — 재측정: 거래 유형 · 상태 목록 · 확정 시각 필드 · 보류/캡처/해제가 거래 행으로 어떻게 남나(캡처가 별도 거래 행인가) · 원천을 account-service 와 ledger-service 중 어디로 할지(복식부기 원장에서 세면 한 거래가 두 번 잡힌다) · 통화 · 테넌트 범위 · 인가 역할. 결과 → 이 파일 + 계약 문구.
- [ ] **AC-1** — 🔴 대조군: 활성 보류 · 해제된 보류 · 실패 거래는 금액을 안 움직인다. 캡처 하나 → 캡처 금액만큼 움직인다. 부분 캡처 → 캡처된 부분만.
- [ ] **AC-2** — 다통화: KRW 거래와 USD 거래가 각자 줄로 나오고 서로 더해지지 않는다.
- [ ] **AC-3** — 응답에 계정 id · 보유자 정보가 없다(필드 전수 단언).
- [ ] **AC-4** — 경계 시험(`Clock` 주입, `TZ=UTC` · `TZ=Asia/Seoul`) · 테넌트 격리 · 인가.
- [ ] **AC-5** — 콘솔: 실적 줄 · 통화별 KPI · 칸 실패 · 401 · 샘플 모드 응답.
- [ ] **AC-6** — 배포 상태(`main` · AMI · 데모 창 라이브)를 따로 적는다.

# Related Specs

- `docs/adr/ADR-MONO-084-domain-overview-performance-metrics.md` D2 · D4 · D5 · OD-3
- `projects/finance-platform/specs/services/account-service/architecture.md` · `ledger-service/architecture.md`

# Related Contracts

- `projects/finance-platform/specs/contracts/http/account-api.md` (`GET /accounts` 부재의 이유) · `ledger-api.md` · 새 `/metrics`
- P1 공용 실적 응답 모양

# Edge Cases

- 이체 = 보내는 쪽 · 받는 쪽 두 행 → 한 번만 센다(AC-0 에서 정의).
- 보류가 지난주, 캡처가 이번 주 → 캡처 시각으로 귀속.

# Failure Scenarios

- 원장(분개)에서 차변 · 대변을 둘 다 센다 → 금액이 두 배.
- 보류 금액을 거래액에 넣는다 → 해제될 돈이 실적으로 보인다.
