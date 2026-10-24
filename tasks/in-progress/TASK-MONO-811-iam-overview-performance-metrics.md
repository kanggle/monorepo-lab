# Task ID

TASK-MONO-811

# Title

`ADR-MONO-084` P6 — IAM 실적 지표: 로그인 성공 · 실패 · 계정 잠금 · 신규 계정(발생 시각 귀속) · 콘솔 개요 실적 줄

# Status

in-progress

# Owner

monorepo

# Task Tags

- code
- api
- iam
- security
- platform-console
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (지표 원천이 서비스 둘 이상에 흩어져 있다 — 로그인 이력은 security-service, 계정 · 잠금은 account/admin)
> 🔴 **cross-project 원자 PR**: iam 계약 · 엔드포인트와 콘솔 화면을 한 PR 에.

# Dependency Markers

- **선행**: `TASK-MONO-806`(P1) `main` 머지.
- 형제: `TASK-MONO-807`~`810` · `812`.

# Goal

IAM 개요(`/iam`)는 운영자 · 계정 · 감사 건수만 보여 준다. 맨 위에 이번 주 · 이번 달 **로그인 성공 · 실패 · 계정 잠금 · 신규 계정**과 직전 대비 · 일별 추이를 붙인다.

# Scope

## In Scope

- 계약 먼저: 지표가 사는 서비스의 계약에 `…/metrics?period=week|month` (P1 공용 모양). 원천: 로그인 이력 = `security-service`(`V0001__create_login_history.sql`) · 계정 생성 · 잠금 = account/admin. **콘솔이 부를 입구 하나**(admin-service 가 모으나, 서비스마다 하나씩 부르나)를 AC-0 에서 정한다 — 둘째면 콘솔 쪽 fan-out 이 늘어난다.
- 집계 · 인가: 운영자 조회 역할(기존 감사 조회와 같은 등급). 🔴 로그인 이력은 개인 정보다 — 집계 숫자만 내려주고 행은 내려주지 않는다.
- 콘솔: `features/iam-overview` 에 P1 «실적 줄».

## Out of Scope

- 의심 이벤트 · 위험 점수 지표.
- 운영자(콘솔 사용자) 활동 지표.

# Acceptance Criteria

- [ ] **AC-0** — 재측정: `login_history` 의 컬럼(결과 · 시각 · 테넌트) · 계정 생성 · 잠금 시각이 어디 저장되나(잠금 해제 후 재잠금 셈법) · 서비스 간 입구 결정 · 테넌트 범위(운영자의 테넌트에 속한 사용자만) · 개인정보 규칙(`specs/features/data-rights.md`). 결과 → 이 파일 + 계약 문구.
- [ ] **AC-1** — 🔴 대조군: 다른 테넌트의 로그인 · 계정은 안 잡힌다. 실패 로그인 하나 → `loginFailure` 만 움직인다.
- [ ] **AC-2** — 응답에 사용자 식별자 · 이메일 · IP 가 없다(필드 전수 단언).
- [ ] **AC-3** — 경계 시험(`Clock` 주입, `TZ=UTC` · `TZ=Asia/Seoul`) · 인가 없는 토큰 403.
- [ ] **AC-4** — 콘솔: 실적 줄 · 칸 실패 · 401 · 샘플 모드 응답.
- [ ] **AC-5** — 배포 상태(`main` · AMI · 데모 창 라이브)를 따로 적는다.

# Related Specs

- `docs/adr/ADR-MONO-084-domain-overview-performance-metrics.md` D2 · D4 · D5
- `projects/iam-platform/specs/services/security-service/architecture.md` · `admin-service/architecture.md`
- `projects/iam-platform/specs/features/data-rights.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/http/security-query-api.md` · `admin-api.md` · 새 `/metrics`
- P1 공용 실적 응답 모양

# Edge Cases

- 같은 사람이 같은 날 열 번 실패 → `loginFailure` 는 시도 수인가 사람 수인가 → AC-0 에서 정하고 계약에 적는다(기본: 시도 수).
- 소비자 풀 계정(ADR-MONO-078)은 어느 테넌트로 세나 → AC-0.

# Failure Scenarios

- 집계 API 가 행을 내려주고 콘솔이 센다 → 개인 정보가 콘솔로 나간다(AC-2 가 막는다).
- 서비스마다 경계 계산을 따로 한다 → P1 `KstPeriodBounds` 가 아닌 곳이 생긴다.
