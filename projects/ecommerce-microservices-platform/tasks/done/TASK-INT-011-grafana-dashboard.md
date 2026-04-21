# Task ID

TASK-INT-011

# Title

모니터링 대시보드 구성 — Grafana 대시보드 프로비저닝 및 Prometheus 메트릭 시각화

# Status

done

# Owner

backend

# Task Tags

- deploy
- code

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

Grafana를 docker-compose에 추가하고, Prometheus 메트릭을 시각화하는 대시보드를 프로비저닝한다. 서비스 상태, JVM 메트릭, 비즈니스 메트릭을 한눈에 확인할 수 있는 대시보드를 제공한다.

---

# Scope

## In Scope

### Grafana docker-compose 추가
- Grafana 서비스 정의 (포트: 3100)
- Prometheus 데이터소스 자동 프로비저닝
- 대시보드 JSON 자동 프로비저닝

### 대시보드 구성
- **서비스 개요 대시보드**: 전체 서비스 상태, 응답 시간, 에러율
- **JVM 대시보드**: 힙 메모리, GC, 스레드 (Spring Boot Actuator/Micrometer 메트릭)
- **비즈니스 메트릭 대시보드**: 서비스별 커스텀 Counter/Histogram 시각화

### 프로비저닝 파일
- `infra/grafana/provisioning/datasources/prometheus.yml`
- `infra/grafana/provisioning/dashboards/dashboard.yml`
- `infra/grafana/dashboards/*.json`

## Out of Scope

- 알림(Alerting) 규칙 설정
- Loki 로그 수집 연동
- Tempo 트레이스 연동 (Jaeger 사용 중)

---

# Acceptance Criteria

- [ ] Grafana가 docker-compose에서 정상 기동된다
- [ ] Prometheus 데이터소스가 자동 연결된다
- [ ] 서비스 개요 대시보드에서 전체 서비스 상태를 확인할 수 있다
- [ ] JVM 대시보드에서 힙 메모리, GC 현황을 확인할 수 있다
- [ ] 비즈니스 메트릭 대시보드에서 커스텀 메트릭을 확인할 수 있다
- [ ] `docker compose config` 문법 검증을 통과한다

---

# Related Specs

- `specs/platform/observability.md`
- `specs/platform/deployment-policy.md`

# Related Skills

_(없음)_

---

# Related Contracts

_(없음)_

---

# Target Service

- `docker-compose.yml` (Grafana 서비스 추가)
- `infra/grafana/` (신규 디렉토리)

---

# Architecture

_(해당 없음)_

---

# Edge Cases

- Prometheus가 아직 기동되지 않았을 때 Grafana 데이터소스 연결 실패 → depends_on 설정
- 대시보드 JSON이 Grafana 버전과 호환되지 않는 경우

---

# Failure Scenarios

- Grafana OOM 시 리소스 제한 조정 필요
- 프로비저닝 파일 경로 오류 시 대시보드 미로드 → 로그 확인 가이드 필요

---

# Test Requirements

- `docker compose config` 로 문법 검증
- Grafana 기동 후 `/api/health` 헬스체크 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
