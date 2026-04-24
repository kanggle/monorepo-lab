# Task ID

TASK-INT-019-fix

# Title

TASK-INT-019 리뷰 이슈 수정 — AlertManager 포트 충돌, deprecated 문법, datasource 타입

# Status

done

# Owner

integration

# Task Tags

- deploy

---

# Goal

TASK-INT-019 리뷰에서 발견된 3건의 이슈를 수정한다.

---

# Scope

## In Scope

1. docker-compose.yml AlertManager 호스트 포트 충돌 해소 (9093 → 9094)
2. AlertManager v0.27.0 `matchers` 문법으로 마이그레이션
3. Grafana AlertManager datasource를 빌트인 `alertmanager` 타입으로 변경

## Out of Scope

- 알림 규칙 변경
- 새로운 기능 추가

---

# Acceptance Criteria

- [x] AlertManager와 Kafka 간 호스트 포트 충돌 없음
- [x] AlertManager 설정이 v0.27.0 문법 사용
- [x] Grafana AlertManager datasource가 빌트인 타입 사용

---

# Related Specs

- `specs/platform/observability.md`
- `specs/platform/deployment-policy.md`

# Related Skills

- N/A

---

# Related Contracts

- N/A

---

# Fix Details

## 이슈 1: 포트 충돌

- 파일: `docker-compose.yml`
- 현재: AlertManager `ports: "9093:9093"`, Kafka `ports: "9093:9094"`
- 수정: AlertManager 호스트 포트를 `9094:9093`으로 변경
- 문서(`docs/monitoring-alert-rules.md`)의 AlertManager URL도 `localhost:9094`로 갱신

## 이슈 2: deprecated 문법

- 파일: `infra/alertmanager/alertmanager.yml`
- `match:` → `matchers:` (리스트 형식)
- `source_match:` → `source_matchers:`
- `target_match:` → `target_matchers:`

## 이슈 3: Grafana datasource 타입

- 파일: `infra/grafana/provisioning/datasources/alertmanager.yml`
- `type: camptocamp-prometheus-alertmanager-datasource` → `type: alertmanager`

---

# Edge Cases

- N/A

# Failure Scenarios

- 포트 변경 후 Prometheus → AlertManager 연동에 영향 없음 확인 (내부 포트 9093은 그대로)

# Test Requirements

- docker-compose config 유효성 확인
