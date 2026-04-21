# Task ID

TASK-INT-013

# Title

Docker 보안 강화 — Java 서비스 non-root 사용자, OTEL JAR 체크섬 검증

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

TASK-INT-012 크로스 리뷰에서 발견된 Critical 이슈 수정. 전체 Java 서비스 Dockerfile에서 root 사용자로 실행되는 문제와 OpenTelemetry JAR가 체크섬 검증 없이 다운로드되는 공급망 보안 문제를 수정한다.

---

# Scope

## In Scope

- 전체 Java 서비스 Dockerfile(8개): non-root 사용자 추가 (USER directive)
- OTEL JAR 다운로드 시 SHA256 체크섬 검증 추가
- 전체 Java 서비스 Dockerfile: HEALTHCHECK directive 추가

## Out of Scope

- Kubernetes SecurityContext 설정
- seccomp/AppArmor 프로필 설정

---

# Acceptance Criteria

- [x] 모든 Java 서비스 Dockerfile에 non-root 사용자가 설정된다
- [x] OTEL JAR 다운로드에 체크섬 검증이 포함된다
- [x] 모든 Java 서비스 Dockerfile에 HEALTHCHECK가 추가된다
- [x] docker-compose up으로 전체 서비스가 정상 기동된다

---

# Related Specs

- `specs/platform/security-rules.md`

# Related Contracts

_(없음)_

---

# Edge Cases

- non-root 사용자의 파일 접근 권한 문제
- OTEL 버전 업데이트 시 체크섬 갱신 절차

---

# Failure Scenarios

- non-root 사용자로 인한 로그 파일 쓰기 권한 오류
- 체크섬 불일치 시 빌드 실패 (의도된 동작)

---

# Test Requirements

- docker build 성공 확인
- 컨테이너 내부에서 whoami 결과가 non-root 사용자인지 확인
