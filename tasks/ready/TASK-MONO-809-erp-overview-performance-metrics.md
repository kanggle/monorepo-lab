# Task ID

TASK-MONO-809

# Title

`ADR-MONO-084` P4 — ERP 실적 지표: 결재 상신 · 승인 · 반려 건수(전이 시각 귀속) · 콘솔 개요 실적 줄

# Status

ready

# Owner

monorepo

# Task Tags

- code
- api
- erp
- platform-console
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (집계 하나 · 화면 붙이기 — 전이 시각이 저장돼 있다면. AC-0 에서 없다고 나오면 Opus 로 올린다)
> 🔴 **cross-project 원자 PR**: approval-service 계약 · 엔드포인트와 콘솔 화면을 한 PR 에.

# Dependency Markers

- **선행**: `TASK-MONO-806`(P1) `main` 머지.
- 형제: `TASK-MONO-807` · `808` · `810`~`812`.

# Goal

ERP 개요(`/erp`)는 지금 마스터 건수 · 결재 상태 분포 · 내 결재 대기만 보여 준다(전부 현재 상태). 맨 위에 이번 주 · 이번 달 **결재 상신 · 승인 · 반려 건수**와 직전 대비 · 일별 추이를 붙인다.

# Scope

## In Scope

- 계약 먼저: `approval-api.md` 에 `…/metrics?period=week|month` (P1 공용 모양, 경로는 그 서비스 관례) · 지표 `submitted` · `approved` · `rejected` 각각 «어떤 전이 · 어느 시각으로 귀속 · 무엇을 빼나» 한 줄(ADR-084 D5).
- approval-service: 집계 · 인가(기존 조회 API 와 같은 역할 게이트 — ADR-083 P6 의 세분 인가를 그대로 따른다).
- 콘솔: `features/erp-ops` 개요에 P1 «실적 줄». `?asOf=` 가 있을 때 실적 줄의 의미(시점 보기와 기간 실적은 다른 축)를 AC-0 에서 정하고 화면에 적는다.

## Out of Scope

- 결재 처리 시간(상신 → 완결 소요) — 평균 · 분위 정의가 필요하다. 계약 변경으로 나중에.
- 인사 · 마스터 변동 지표.

# Acceptance Criteria

- [ ] **AC-0** — 재측정: 결재 요청 · 단계의 상태 전이 시각이 저장돼 있는가(상신 · 승인 · 반려 각각) · 회수 · 재상신의 셈법 · 위임 결재의 귀속 · 테넌트 범위 · 조회 인가 역할. 결과 → 이 파일 + 계약 문구.
- [ ] **AC-1** — 🔴 대조군: 초안(상신 전) · 회수된 요청은 `submitted` 를 안 움직인다(AC-0 정의대로). 승인 하나 → `approved` 만 움직인다.
- [ ] **AC-2** — 경계 시험(`Clock` 주입, `TZ=UTC` · `TZ=Asia/Seoul`) · 테넌트 격리 · 인가 없는 토큰 403.
- [ ] **AC-3** — 콘솔: 실적 줄 렌더 · 칸 실패 시 현재 상태 블록이 선다 · 401 · 샘플 모드 응답.
- [ ] **AC-4** — 배포 상태(`main` · AMI · 데모 창 라이브)를 따로 적는다.

# Related Specs

- `docs/adr/ADR-MONO-084-domain-overview-performance-metrics.md` D2 · D4 · D5
- `projects/erp-platform/specs/services/approval-service/architecture.md`

# Related Contracts

- `projects/erp-platform/specs/contracts/http/approval-api.md` · 새 `/metrics`
- P1 공용 실적 응답 모양

# Edge Cases

- 여러 단계 결재: 중간 단계 승인은 `approved` 인가, 최종 승인만인가 → AC-0 에서 정하고 계약에 적는다(기본: 최종 결과만).
- 상신은 지난주, 승인은 이번 주 → 각자 자기 시각으로 귀속.

# Failure Scenarios

- 상태 분포(현재)와 기간 실적을 같은 숫자로 착각하게 그린다 → 카드 제목에 기간을 적는다.
- 전이 시각이 없어 `updatedAt` 으로 대신한다 → 다른 수정에도 날짜가 움직인다. AC-0 에서 없으면 멈추고 보고한다.
