# Task ID

TASK-BE-015

# Title

Fix TASK-BE-012 — observability scrape port, metric tags, alert metric names, datasource duplication

# Status

ready

# Owner

backend

# Task Tags

- deploy

# depends_on

- TASK-BE-012

---

# Goal

TASK-BE-012 리뷰에서 발견된 Critical/Warning 이슈들을 수정한다:
1. **Critical**: admin-service 포트 불일치 — `application.yml`은 8085, `prometheus.yml`은 8084 scrape
2. **Critical**: 4개 서비스(auth, account, security, admin)에 `management.metrics.tags.service` 누락 — 모든 대시보드 `{service="..."}` 쿼리 작동 불능
3. **Critical**: `alert-rules.yml`의 메트릭 이름이 observability.md 스펙과 불일치 → 모든 알림 발동 불가
4. **Critical**: Loki `derivedFields`가 존재하지 않는 `jaeger` datasource 참조
5. **Warning**: Promtail이 `trace_id` 추출 — logback은 `traceId` 방출 (camelCase 불일치)
6. **Warning**: Grafana datasource 3개 파일(`prometheus.yml`, `loki.yml`, `datasources.yml`) 중복 프로비저닝
7. **Warning**: `HighErrorRate5xx` severity가 `warning` — 스펙은 `critical` 요구
8. **Warning**: alert-rules.yml 대부분 `runbook` annotation 누락

---

# Scope

## In Scope

1. **포트 정정**:
   - `infra/prometheus/prometheus.yml`의 admin-service scrape target을 `host.docker.internal:8084` → `8085`

2. **metrics.tags.service 추가**:
   - `apps/auth-service/src/main/resources/application.yml`
   - `apps/account-service/src/main/resources/application.yml`
   - `apps/security-service/src/main/resources/application.yml`
   - `apps/admin-service/src/main/resources/application.yml`
   - 각 파일에 `management.metrics.tags.service: ${spring.application.name}` 추가

3. **alert-rules.yml 메트릭 이름 정합화** (관련 observability.md의 canonical 이름 사용):
   - `gateway_jwt_validation_failures_total` → `gateway_jwt_validation_total{result="failure"}`
   - `gateway_rate_limit_rejections_total` → `gateway_ratelimit_rejected_total`
   - `auth_login_failures_total` → `auth_login_total{result="failure"}`
   - `auth_refresh_reuse_detected_total` → `auth_token_reuse_detected_total`
   - `auth_outbox_pending_count` → `auth_outbox_lag_seconds`
   - `account_outbox_pending_count` → `account_outbox_lag_seconds`
   - `security_auto_lock_triggered_total` → `security_auto_lock_issued_total`
   - 대시보드 JSON 쿼리도 동일하게 정렬

4. **Loki derivedFields 수정**:
   - `infra/grafana/provisioning/datasources/loki.yml`에서 `datasourceUid: jaeger` 제거 (Jaeger 미프로비저닝 상태)
   - 또는 Jaeger 프로비저닝을 Out of Scope로 유지하고 derivedFields 자체 제거

5. **Promtail 필드명 정렬**:
   - `infra/promtail/promtail-config.yml`의 `trace_id` 추출 → `traceId`로 변경
   - `spanId`, `requestId`도 camelCase로 일관화

6. **datasource 파일 중복 제거**:
   - `infra/grafana/provisioning/datasources/prometheus.yml`과 `loki.yml` 삭제
   - `datasources.yml` 하나만 유지

7. **severity 정정**:
   - `HighErrorRate5xx` 알림의 `severity: warning` → `critical`

8. **runbook annotation 추가**:
   - 모든 알림 규칙에 `runbook: "docs/runbooks/<alert-name>.md"` 또는 placeholder URL annotation 추가
   - `docs/runbooks/` 디렉터리는 추후 채움 (이 태스크 Out of Scope)

## Out of Scope

- Jaeger/Tempo 통합 (미래 태스크)
- 런북 실제 내용 작성
- 새 메트릭 구현 (별도 태스크)

---

# Acceptance Criteria

- [ ] admin-service scrape target 포트 8085
- [ ] 4개 서비스에 `metrics.tags.service` 구성
- [ ] 모든 alert-rules metric 이름이 observability.md 스펙과 일치
- [ ] Loki datasource provisioning 에러 없음 (Jaeger 참조 제거)
- [ ] Promtail `traceId` 추출 + Loki 쿼리 `{traceId="..."}` 성공
- [ ] Grafana datasource 파일 1개만 존재 (`datasources.yml`)
- [ ] `HighErrorRate5xx` severity=critical
- [ ] 모든 알림 규칙에 runbook annotation 존재
- [ ] `docker compose config --quiet` 통과
- [ ] `./gradlew build` 통과

---

# Related Specs

- `specs/services/gateway-service/observability.md`
- `specs/services/auth-service/observability.md`
- `specs/services/account-service/observability.md`
- `specs/services/security-service/observability.md`
- `specs/services/admin-service/observability.md`
- `platform/observability.md`

# Related Skills

- `.claude/skills/infra/monitoring-stack/SKILL.md`
- `.claude/skills/cross-cutting/observability-setup/SKILL.md`

---

# Target Service

- root (infra/) + 4 services의 application.yml

---

# Edge Cases

- 일부 메트릭은 코드에 아직 노출되지 않을 수 있음 → 대시보드 패널이 "No data" 표시 (허용, 후속 태스크에서 Micrometer 카운터 추가)

---

# Failure Scenarios

- `docker compose up` 후 여전히 scrape 실패 → 네트워크 설정 재검토
- Grafana dashboard 쿼리 여전히 empty → 서비스 실제 메트릭 노출 여부 확인

---

# Test Requirements

- `docker compose config --quiet` 성공
- Promtail + Loki 통해 `traceId` 필드 필터링 가능 (수동 검증)
- `curl localhost:9090/api/v1/targets` → admin-service UP

---

# Definition of Done

- [ ] 포트·태그·메트릭 이름 정합성
- [ ] datasource 중복 제거
- [ ] runbook annotation
- [ ] Ready for review
