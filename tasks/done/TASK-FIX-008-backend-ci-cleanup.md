# Task ID

TASK-FIX-008

# Title

Backend CI 워크플로우 클린업 — dead code 제거 및 불필요한 docker:dind 제거

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

TASK-INT-008 리뷰에서 발견된 backend-ci.yml의 2가지 이슈를 수정한다.

---

# Scope

## In Scope

### 1. detect-changes 스텝 dead code 제거
- `backend-ci.yml` 64~66행: `for` 루프 내 `key` 변수 할당과 첫 번째 `svc_changed` 할당이 바로 아래 `case` 문에 의해 덮어씌워지므로 제거
- `docker-build.yml`에도 동일 패턴이 있으면 함께 정리

### 2. 불필요한 docker:dind 서비스 컨테이너 제거
- `backend-ci.yml` 100~103행: `services.docker` 블록 제거
- GitHub Actions `ubuntu-latest`에는 Docker가 기본 설치되어 있어 Testcontainers가 DinD 없이 동작함

## Out of Scope

- 워크플로우 로직 변경
- 새 기능 추가

---

# Acceptance Criteria

- [ ] backend-ci.yml에서 dead code(key 변수, 첫 번째 svc_changed 할당)가 제거된다
- [ ] backend-ci.yml에서 docker:dind 서비스 컨테이너가 제거된다
- [ ] YAML 문법이 유효하다

---

# Related Specs

- `specs/platform/deployment-policy.md`

# Related Skills

_(없음)_

---

# Related Contracts

_(없음)_

---

# Target Service

- `.github/workflows/backend-ci.yml` (수정)

---

# Architecture

_(해당 없음)_

---

# Edge Cases

_(없음)_

---

# Failure Scenarios

_(없음)_

---

# Test Requirements

- YAML 문법 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
