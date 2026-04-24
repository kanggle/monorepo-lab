# Task ID

TASK-BE-061

# Title

전체 서비스 하드코딩 시크릿 제거 — DB 크레덴셜, JWT 시크릿 설정 외부화

# Status

ready

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

---

# Goal

TASK-INT-012 크로스 리뷰에서 발견된 Critical 이슈 수정. 전체 백엔드 서비스의 application.yml에 하드코딩된 DB 크레덴셜 기본값과 JWT 시크릿을 제거하고, docker-compose.yml의 하드코딩된 PostgreSQL/Grafana 비밀번호를 .env 변수 참조로 변경한다.

---

# Scope

## In Scope

- auth-service application.yml: `DB_PASSWORD:auth_pass` 기본값 제거
- auth-service application-local.yml: 하드코딩된 JWT 시크릿 제거
- gateway-service application.yml: 하드코딩된 JWT 시크릿 기본값 제거
- docker-compose.yml: 모든 PostgreSQL 서비스 `POSTGRES_PASSWORD` 환경변수화
- docker-compose.yml: Grafana `GF_SECURITY_ADMIN_PASSWORD` 환경변수화
- .env.example 파일 생성 (필수 환경변수 목록 문서화)

## Out of Scope

- Kubernetes secrets 관리
- 외부 시크릿 매니저 연동

---

# Acceptance Criteria

- [ ] 모든 application.yml에서 크레덴셜 기본값이 제거된다
- [ ] docker-compose.yml의 모든 비밀번호가 `${VARIABLE}` 형태로 변경된다
- [ ] .env.example 파일이 필수 환경변수를 문서화한다
- [ ] .env 파일이 .gitignore에 포함되어 있다
- [ ] docker-compose up이 .env 파일로 정상 동작한다

---

# Related Specs

- `specs/platform/security-rules.md`
- `specs/platform/coding-rules.md`

# Related Contracts

_(없음)_

---

# Edge Cases

- .env 파일이 없을 때 docker-compose가 명확한 에러를 출력해야 함
- CI 환경에서 환경변수 주입 방식 호환성

---

# Failure Scenarios

- 환경변수 미설정 시 서비스가 시작 실패하되, 에러 메시지가 명확해야 함

---

# Test Requirements

- docker-compose up 정상 동작 확인
- 서비스 시작 시 환경변수 누락 에러 메시지 확인
