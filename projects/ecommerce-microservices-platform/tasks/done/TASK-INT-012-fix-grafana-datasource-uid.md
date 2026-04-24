# Task ID

TASK-INT-012

# Title

Grafana 데이터소스 UID 불일치 수정 — 프로비저닝 파일에 UID 명시

# Status

done

# Owner

backend

# Task Tags

- deploy

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

대시보드 JSON에서 참조하는 datasource UID(`PBFA97CFB590B2093`)와 Grafana 프로비저닝 datasource의 UID를 일치시켜, 대시보드가 Prometheus 데이터소스를 정상적으로 연결할 수 있도록 한다.

---

# Scope

## In Scope

- `infra/grafana/provisioning/datasources/prometheus.yml`에 `uid: PBFA97CFB590B2093` 추가

## Out of Scope

- 대시보드 JSON 구조 변경
- 새로운 대시보드 추가

---

# Acceptance Criteria

- [ ] 프로비저닝 datasource 파일에 uid 필드가 명시되어 있다
- [ ] 대시보드 JSON의 datasource uid와 프로비저닝 파일의 uid가 일치한다

---

# Related Specs

- `specs/platform/observability.md`

# Related Skills

_(없음)_

---

# Related Contracts

_(없음)_

---

# Target Service

- `infra/grafana/provisioning/datasources/prometheus.yml`

---

# Architecture

_(해당 없음)_

---

# Edge Cases

- uid 변경 시 기존 Grafana 볼륨 데이터와 충돌 가능 → 로컬 개발이므로 볼륨 재생성으로 해결

---

# Failure Scenarios

- uid가 여전히 불일치하면 대시보드에서 "No data" 표시

---

# Test Requirements

- Grafana 기동 후 대시보드에서 Prometheus 데이터소스가 연결되는지 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
