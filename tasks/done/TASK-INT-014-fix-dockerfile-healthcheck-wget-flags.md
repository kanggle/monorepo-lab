# Task ID

TASK-INT-014

# Title

Dockerfile HEALTHCHECK wget 플래그 BusyBox 호환성 수정

# Status

done

# Owner

backend

# Task Tags

- deploy, code

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

TASK-INT-013 리뷰에서 발견된 이슈 수정. 전체 Java 서비스 Dockerfile의 HEALTHCHECK에서 GNU wget 전용 플래그(`--no-verbose`, `--tries=1`)를 사용하고 있으나, `eclipse-temurin:21-jre-alpine` 이미지는 BusyBox wget만 포함하므로 HEALTHCHECK가 항상 실패한다. BusyBox 호환 플래그로 변경한다.

---

# Scope

## In Scope

- 전체 Java 서비스 Dockerfile(8개): HEALTHCHECK의 wget 플래그를 BusyBox 호환 형태로 변경

## Out of Scope

- docker-compose.yml의 healthcheck (이미 BusyBox 호환)
- HEALTHCHECK 주기/타임아웃 값 변경

---

# Acceptance Criteria

- [x] 모든 Java 서비스 Dockerfile의 HEALTHCHECK가 BusyBox wget 호환 플래그를 사용한다
- [x] docker-compose.yml의 healthcheck 형태(`wget -qO-`)와 일관된 패턴을 사용한다

---

# Related Specs

- `specs/platform/deployment-policy.md`

# Related Contracts

_(없음)_

---

# Edge Cases

- BusyBox wget 버전별 플래그 차이

---

# Failure Scenarios

- 잘못된 플래그 사용 시 HEALTHCHECK 항상 실패 (현재 상태)

---

# Test Requirements

- docker build 성공 확인
- HEALTHCHECK 명령어가 컨테이너 내부에서 정상 실행되는지 확인
