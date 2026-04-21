# Task ID

TASK-BE-074

# Title

gateway-service K8s ConfigMap에 CORS_ALLOWED_ORIGINS 환경변수 추가

# Status

done

# Owner

backend

# Task Tags

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

TASK-BE-073 리뷰에서 발견된 이슈 수정. gateway-service의 application.yml에서 `${CORS_ALLOWED_ORIGINS}` 환경변수를 사용하도록 변경되었으나, K8s ConfigMap에 해당 변수가 누락되어 프로덕션 배포 시 기본값(localhost)으로 폴백되는 문제를 수정한다.

---

# Scope

## In Scope

- `k8s/services/gateway-service/configmap.yaml`에 `CORS_ALLOWED_ORIGINS` 추가

## Out of Scope

- application.yml 변경
- 다른 서비스의 ConfigMap 변경

---

# Acceptance Criteria

- [ ] `k8s/services/gateway-service/configmap.yaml`에 `CORS_ALLOWED_ORIGINS` 환경변수가 정의된다
- [ ] 값은 프로덕션 도메인 플레이스홀더로 설정된다 (기존 ConfigMap의 다른 서비스 URL과 동일 패턴)

---

# Related Specs

- `specs/platform/coding-rules.md` (하드코딩 금지)

---

# Related Contracts

_(없음)_

---

# Target Service

- gateway-service

---

# Edge Cases

- 여러 origin을 쉼표로 구분하는 형식이 Spring Cloud Gateway globalcors에서 올바르게 파싱되는지 확인

---

# Failure Scenarios

- CORS_ALLOWED_ORIGINS 누락 시 localhost 기본값으로 폴백되어 프로덕션 CORS 차단

---

# Test Requirements

- ConfigMap YAML 문법 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
