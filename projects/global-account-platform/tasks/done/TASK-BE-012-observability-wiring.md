# Task ID

TASK-BE-012

# Title

Observability 연동 — Prometheus scrape, Grafana 대시보드, 구조화 로그, Loki, 비즈니스 메트릭

# Status

backlog

# Owner

backend

# Task Tags

- deploy
- code

# depends_on

- TASK-BE-004
- TASK-BE-005
- TASK-BE-007
- TASK-BE-008

---

# Goal

모든 서비스의 Prometheus 메트릭·구조화 JSON 로그·OTel 트레이스를 수집하고, Grafana 대시보드를 통해 시스템 상태를 한눈에 파악할 수 있는 관측성 스택을 완성한다.

---

# Scope

## In Scope

- Docker Compose에 Prometheus, Grafana, Loki, Promtail 추가 (infra/ 기존 설정 활용)
- `infra/prometheus/prometheus.yml` scrape target 추가 (5개 서비스)
- `infra/prometheus/alert-rules.yml` 재작성 (각 서비스 observability.md의 알림 규칙)
- `infra/grafana/dashboards/` — 서비스별 대시보드 JSON 5개 + 시스템 오버뷰 1개
- `infra/grafana/provisioning/` datasource + dashboard provider 설정
- 각 서비스에 Micrometer Prometheus registry + `/actuator/prometheus` 엔드포인트 활성화
- 구조화 JSON 로그 설정 (Logback + JSON encoder)
- 각 서비스 observability.md에 정의된 비즈니스 메트릭 구현
- load-tests/scenarios/ 재작성 (auth-load-test.js, signup-load-test.js 최소)

## Out of Scope

- 분산 트레이싱 수집 (Jaeger/Tempo — 미래)
- 외부 SIEM 연동
- PagerDuty/Slack 알림 연동 (AlertManager webhook 설정만)
- K8s ServiceMonitor

---

# Acceptance Criteria

- [ ] `docker compose up -d` 후 Prometheus targets에 5개 서비스 모두 UP
- [ ] Grafana 대시보드: gateway(8패널), auth(8패널), account(7패널), security(10패널), admin(9패널) 확인
- [ ] 시스템 오버뷰 대시보드: 전체 서비스 상태·에러율·지연 한눈에 표시
- [ ] Loki에서 각 서비스 로그 조회 가능 (JSON 구조화, traceId/requestId 필드)
- [ ] AlertManager에 alert-rules.yml 로드됨
- [ ] 각 서비스 `/actuator/prometheus` 접속 시 메트릭 노출
- [ ] load-tests: auth-load-test.js 실행 → Grafana에서 메트릭 변화 확인

---

# Related Specs

- `specs/services/gateway-service/observability.md`
- `specs/services/auth-service/observability.md`
- `specs/services/account-service/observability.md`
- `specs/services/security-service/observability.md`
- `specs/services/admin-service/observability.md`
- `platform/observability.md`

# Related Skills

- `.claude/skills/cross-cutting/observability-setup/SKILL.md`
- `.claude/skills/infra/monitoring-stack/SKILL.md`

---

# Related Contracts

없음 (관측성 인프라 태스크)

---

# Target Service

- 모든 서비스 + infra/

---

# Edge Cases

- 서비스가 아직 미기동 상태에서 Prometheus scrape → target DOWN, 알림 발생하지 않도록 initial delay 설정
- Grafana 대시보드 JSON이 Prometheus datasource UID와 불일치 → provisioning에서 일관된 UID 사용

---

# Failure Scenarios

- Prometheus 디스크 부족 → retention 2일 설정 (로컬 개발용)
- Loki 수집 지연 → Promtail 버퍼 설정 확인
- Grafana 프로비저닝 실패 → provisioning/ YAML 구문 확인

---

# Test Requirements

- 인프라 기동: `docker compose up -d` → 모든 관측성 서비스 healthy
- 메트릭: `curl localhost:9090/api/v1/targets` → 5개 서비스 UP
- 로그: Grafana Explore → Loki → 서비스 이름으로 필터 → JSON 로그 확인
- 부하: load-tests 실행 → 대시보드에서 요청 증가 패턴 확인

---

# Definition of Done

- [ ] Docker Compose 관측성 스택 기동
- [ ] Prometheus 5개 서비스 scrape 확인
- [ ] Grafana 6개 대시보드 프로비저닝
- [ ] 구조화 로그 + Loki 연동
- [ ] AlertManager 규칙 로드
- [ ] Load test 실행 + 대시보드 확인
- [ ] Ready for review
