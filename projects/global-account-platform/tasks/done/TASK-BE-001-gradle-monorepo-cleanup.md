# Task ID

TASK-BE-001

# Title

Gradle monorepo cleanup — 프로젝트명 교체, 빌드 구조 검증, apps/ 디렉터리 준비

# Status

ready

# Owner

backend

# Task Tags

- code

---

# Goal

`settings.gradle`의 `rootProject.name`이 `global-account-platform`으로 설정되고, `libs/` 6개 모듈이 정상 빌드되며, 향후 서비스 모듈(`apps/`)이 추가될 수 있도록 빌드 구조가 준비된 상태를 만든다.

---

# Scope

## In Scope

- `settings.gradle` include 구조 확인 (현재 libs-only, apps는 아직 없음)
- `build.gradle` (root) 공통 플러그인·의존성 검증 (Java 21, Spring Boot 3, QueryDSL, Flyway 등)
- `gradle.properties` 검증 (버전 변수, 인코딩)
- 빈 `apps/` 디렉터리 생성
- `./gradlew build` 전체 성공 확인

## Out of Scope

- 개별 서비스 모듈 생성 (TASK-BE-004~008에서 수행)
- Docker Compose 설정 (TASK-BE-003)
- libs 내부 코드 변경 (TASK-BE-002)

---

# Acceptance Criteria

- [ ] `./gradlew build` 성공 (exit 0)
- [ ] `settings.gradle`의 `rootProject.name`이 `global-account-platform`
- [ ] `libs/` 6개 모듈(java-common, java-web, java-messaging, java-security, java-observability, java-test-support) 모두 `build` 태스크 통과
- [ ] `apps/` 디렉터리 존재 (빈 상태)
- [ ] root `build.gradle`에 Java 21 toolchain 설정
- [ ] Deprecated Gradle 경고 중 blocking issue 없음

---

# Related Specs

- `platform/entrypoint.md`
- `platform/repository-structure.md`
- `platform/shared-library-policy.md`

# Related Skills

- `.claude/skills/backend/implementation-workflow/SKILL.md`

---

# Related Contracts

없음 (인프라 태스크)

---

# Target Service

- root (multi-module)

---

# Architecture

root 빌드 스크립트. 서비스별 아키텍처 해당 없음.

---

# Implementation Notes

- `settings.gradle`는 이미 libs-only로 정리된 상태. 주요 작업은 root `build.gradle`의 공통 설정 검증
- Java 21, Spring Boot 3.x, QueryDSL 5.x, Flyway 9.x 버전이 `gradle.properties` 또는 root `build.gradle`에 명시되어야 함
- `apps/` 디렉터리에 `.gitkeep` 추가

---

# Edge Cases

- Gradle wrapper 버전이 낮아 Java 21을 지원하지 않는 경우 → `./gradlew wrapper --gradle-version 8.14` 실행
- libs 간 의존성 순환 → `./gradlew dependencies`로 확인

---

# Failure Scenarios

- root build.gradle에 Spring Boot BOM 버전이 하드코딩되어 libs와 불일치 → gradle.properties 중앙 관리로 해결
- java-security 모듈이 src/main 없이 빌드 실패 → TASK-BE-002에서 scaffold 추가

---

# Test Requirements

- `./gradlew build` 전체 통과 (기존 libs 테스트 포함)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing (`./gradlew build`)
- [ ] Ready for review
