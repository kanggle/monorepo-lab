# Task ID

TASK-BE-668

# Title

iam 스펙 필수 metric 중 대시보드가 묻는데 없는 5개(837 D1)를 구현한다 — `account_signup_total` · `admin_audit_write_total` · `admin_audit_query_total` · `auth_token_issued_total` · `auth_token_refresh_total` — 그리고 대기 목록에서 지운다

# Status

ready

# Owner

backend

# Task Tags

- observability
- account-service
- admin-service
- auth-service

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 (세 서비스 · 세는 지점을 결과별로 고르는 판단 — `TASK-BE-664` 와 같은 결)
> 기안 2026-11-25 UTC. 출처: `TASK-MONO-837` AC-5 «D1 = 구현» → 소유자 «권장안대로 진행».

# Dependency Markers

- 선행: `TASK-MONO-837`(분류) · `TASK-MONO-839`(대기 목록 `projects/iam-platform/infra/grafana/pending-metrics.txt` + 가드: 구현되면 그 항목이 남아 있는 한 **가드가 실패** 한다 — 이 티켓이 항목을 지워야 초록).
- 본보기: `TASK-BE-662`(admin `admin_command_total`) · `TASK-BE-664`(auth `AuthMetricsPort` + `MicrometerAuthMetrics`, 인프라 장애는 `failure` 로 세지 않는 판단).

# Goal

스펙(`specs/services/*/observability.md` «필수 메트릭»)이 요구하고 iam 대시보드 패널이 묻는데 코드가 내보내지 않아 패널이 빈 5개:

| 이름 | 서비스 | 라벨(스펙) | 묻는 패널 |
|---|---|---|---|
| `account_signup_total` | account | `result` (success/duplicate/validation_error) | `account-overview.json` «Signup Rate» |
| `admin_audit_write_total` | admin | `result` (success/failure) | `admin-overview.json` «Audit Write Rate» |
| `admin_audit_query_total` | admin | `query_type` | `admin-overview.json` «Audit Queries» |
| `auth_token_issued_total` | auth | `type` (access/refresh) | `auth-overview.json` «Tokens Issued» |
| `auth_token_refresh_total` | auth | `result` (success/expired/reuse_detected/revoked) | `auth-overview.json` «Refresh Token Rotation» |

# Scope

## In Scope

1. 다섯 metric 을 스펙 이름 · 라벨 그대로 등록하고 실제 사건 지점에서 센다. 서비스마다 기존 관례(port + Micrometer 어댑터 — auth 는 `AuthMetricsPort`, admin 은 `AdminActionAuditor` 주변)를 따른다.
2. **세는 지점 판단을 증거에 적는다**: 각 `result` 값이 코드의 어느 분기인지(파일:줄). 스펙 라벨 값 중 코드에 그 분기가 없는 것은 «0 으로 남는다» 고 적는다(664 의 `rate_limited` 처럼). 인프라 장애를 업무 실패로 세지 않는다(664 판단과 같은 이유 — 알림 오발).
3. `projects/iam-platform/infra/grafana/pending-metrics.txt` 에서 다섯 줄 삭제(남은 6줄은 837 D2 — 그대로).
4. 테스트: 서비스마다 metric 단위 테스트(결과별 increment) + 사건 지점 테스트(그 분기에서 port 가 불린다).

## Out of Scope

- 837 D2 의 다른 iam metric.
- 대시보드 수정(이미 스펙 이름을 묻는다).

# Acceptance Criteria

- [ ] AC-1: 다섯 metric 이 실제 `PrometheusMeterRegistry` scrape 에 스펙 이름 · 라벨로 나온다(테스트로 — 이름 변환을 추측하지 않는다).
- [ ] AC-2: 결과별 세는 지점이 증거에 파일:줄로 있고, 각 지점에 테스트가 있다.
- [ ] AC-3: `check-alert-rule-metrics.sh` rc=0 — 대기 목록 iam 6 · ecommerce 1(또는 669 가 먼저 머지됐으면 0) · `check-metric-name-registry.sh` rc=0.
- [ ] AC-4: account · admin · auth `test` 통과 · 가드 전부(스테이지 뒤) rc=0(환경 전제 비0 은 사유와 함께) · PR CI 실패 0.

# Related Specs

- `specs/services/account-service/observability.md` · `admin-service/observability.md` · `auth-service/observability.md`

# Related Contracts

- 없음.

# Edge Cases

- `auth_token_refresh_total{result="reuse_detected"}` 와 이미 있는 `auth_token_reuse_detected_total` 은 같은 사건 — 둘 다 센다(스펙이 둘 다 요구).
- 토큰 발급이 여러 경로(로그인 · refresh · OAuth · SAS)에서 일어난다 → 발급 지점 하나(토큰 생성 서비스)에서 세면 경로마다 빠짐이 없다. 경로별로 세면 이중 계수 위험.

# Failure Scenarios

- 가입 실패를 예외 처리기에서 세다가 인프라 오류까지 `validation_error` 로 셈 → 664 판단 위반.
- 대기 목록 항목을 안 지움 → 가드 빨강(의도된 동작).

# Test Requirements

- 위 단위 · 지점 테스트 · 실제 scrape 이름 확인 · 서비스 test · 가드 · CI.

# Definition of Done

- AC 전부 · 머지 · 4-dim close.
