# Task ID

TASK-INT-008

# Title

CI/CD 파이프라인 구성 — GitHub Actions 빌드, 테스트, 이미지 빌드 워크플로우

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

GitHub Actions 기반 CI/CD 파이프라인을 구성하여 PR 생성 시 자동 빌드/테스트, main 브랜치 머지 시 Docker 이미지 빌드를 수행한다.

---

# Scope

## In Scope

### 백엔드 CI 워크플로우 (`.github/workflows/backend-ci.yml`)
- PR 생성/업데이트 시 트리거
- Gradle 빌드 및 단위/통합 테스트 실행
- 변경된 서비스만 빌드 (path filter)
- Testcontainers 지원 (Docker-in-Docker)

### 프론트엔드 CI 워크플로우 (`.github/workflows/frontend-ci.yml`)
- PR 생성/업데이트 시 트리거
- pnpm install, lint, type-check, test 실행
- 변경된 앱/패키지만 빌드 (turbo filter)

### Docker 이미지 빌드 워크플로우 (`.github/workflows/docker-build.yml`)
- main 브랜치 머지 시 트리거
- 변경된 서비스의 Docker 이미지 빌드
- 이미지 태그: `{service-name}:{git-sha}`

## Out of Scope

- 컨테이너 레지스트리 푸시 (ECR, GHCR 등)
- 스테이징/프로덕션 배포 자동화
- Secrets Manager 연동

---

# Acceptance Criteria

- [ ] 백엔드 PR에서 Gradle 빌드 및 테스트가 자동 실행된다
- [ ] 프론트엔드 PR에서 lint, type-check, test가 자동 실행된다
- [ ] 변경된 서비스만 빌드되도록 path filter가 적용된다
- [ ] main 머지 시 Docker 이미지가 빌드된다
- [ ] 워크플로우 YAML 문법이 유효하다

---

# Related Specs

- `specs/platform/deployment-policy.md`
- `specs/platform/testing-strategy.md`

# Related Skills

_(없음)_

---

# Related Contracts

_(없음)_

---

# Target Service

- `.github/workflows/backend-ci.yml` (신규)
- `.github/workflows/frontend-ci.yml` (신규)
- `.github/workflows/docker-build.yml` (신규)

---

# Architecture

_(해당 없음)_

---

# Edge Cases

- 여러 서비스가 동시에 변경된 경우 병렬 빌드
- 공유 라이브러리(libs/) 변경 시 의존하는 모든 서비스 빌드 트리거
- packages/ 변경 시 모든 프론트엔드 앱 빌드 트리거

---

# Failure Scenarios

- 테스트 실패 시 PR에 실패 상태 표시 및 로그 접근 가능
- Docker 빌드 실패 시 어떤 서비스에서 실패했는지 명확히 표시

---

# Test Requirements

- GitHub Actions 워크플로우 YAML 문법 검증 (`actionlint` 또는 수동 검증)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
