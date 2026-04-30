# Task ID

TASK-BE-016

# Title

Fix observability metric names in dashboards + security-service scrape port

# Status

ready

# Owner

backend

# Task Tags

- deploy

# depends_on

- TASK-BE-015 (fix for TASK-BE-012)

---

# Goal

TASK-BE-015 (BE-012 fix) 리뷰에서 발견된 잔여 이슈 수정:
1. security-service Prometheus scrape 포트 불일치 (8083 → 8084)
2. admin-overview.json 대시보드 메트릭 이름이 observability.md 스펙과 불일치 (7개 위반)
3. auth-overview.json 대시보드 메트릭 이름 4개 위반
4. `SecurityConsumerLagHigh` alert severity `warning` → `critical` (스펙 요구)

---

# Scope

## In Scope

1. **prometheus.yml 포트 정정**:
   - `infra/prometheus/prometheus.yml` line 49: security-service target `host.docker.internal:8083` → `8084`

2. **admin-overview.json 메트릭 이름 정합화**:
   - `admin_actions_performed_total` → `admin_command_total`
   - `admin_lock_total` / `admin_unlock_total` → `admin_command_total{action_code="LOCK"}` / `admin_command_total{action_code="UNLOCK"}`
   - `admin_audit_queries_total` → `admin_audit_query_total`
   - `admin_audit_trail_total` → `admin_audit_write_total`
   - `admin_downstream_failures_total` → `admin_downstream_duration_seconds` (histogram — panel 타입도 변경 필요)
   - `admin_reason_required_rejected_total` → 스펙에 없음, 패널 제거 또는 `admin_command_total{outcome="FAILURE"}` 필터로 대체

3. **auth-overview.json 메트릭 이름 정합화**:
   - `auth_login_success_total` → `auth_login_total{result="success"}`
   - `auth_refresh_rotation_total` → `auth_token_refresh_total`
   - `auth_jwks_refresh_total` → gateway 스펙에서 관리되므로 제거 또는 gateway 대시보드로 이동
   - `auth_tokens_issued_total` → `auth_token_issued_total`

4. **alert severity 정정**:
   - `SecurityConsumerLagHigh` alert `severity: warning` → `critical`

## Out of Scope

- 실제 Micrometer 카운터/히스토그램 코드 구현 (별도 태스크)
- 신규 대시보드 생성
- 런북 실제 내용 작성

---

# Acceptance Criteria

- [ ] prometheus.yml security-service target 포트 8084
- [ ] admin-overview.json 모든 메트릭 이름이 `specs/services/admin-service/observability.md`와 일치
- [ ] auth-overview.json 모든 메트릭 이름이 `specs/services/auth-service/observability.md`와 일치
- [ ] `SecurityConsumerLagHigh` severity=critical
- [ ] `docker compose config --quiet` 통과

---

# Related Specs

- `specs/services/admin-service/observability.md`
- `specs/services/auth-service/observability.md`
- `specs/services/security-service/observability.md`

# Related Skills

- `.claude/skills/infra/monitoring-stack/SKILL.md`

---

# Target Service

- root (infra/)

---

# Edge Cases

- 일부 메트릭은 코드에 아직 노출되지 않을 수 있음 → "No data" 허용 (후속 태스크에서 Micrometer 추가)

---

# Failure Scenarios

- Grafana dashboard JSON 파싱 실패 → provisioning 시점 검증 필요

---

# Test Requirements

- `docker compose config --quiet` 통과
- 대시보드 JSON 파싱 에러 없음

---

# Definition of Done

- [ ] 메트릭 이름 정합화
- [ ] 포트 정정
- [ ] Severity 정정
- [ ] Ready for review
