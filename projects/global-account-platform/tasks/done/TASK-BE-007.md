# Task ID

TASK-BE-007

# Title

ResilienceClientFactory 추출 — libs/java-common 공통 HTTP 클라이언트 팩토리

# Status

review

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

`AuthServiceClient`, `AccountServiceClient`(admin-service) 등 3개+ 서비스 간 HTTP 클라이언트가 Resilience4j 설정을 동일하게 복제하고 있다:
- HttpClient: connect 3s, read N초
- CircuitBreaker: 50% 실패율, 10s 슬라이딩 윈도우
- Retry: 지수 백오프

`libs/java-common`에 `ResilienceClientFactory`를 추가하여 표준 설정을 중앙화하고, 서비스별 클라이언트는 팩토리를 통해 표준화된 설정의 `RestClient`/`HttpClient`를 받는다.

---

# Scope

## In Scope

- `libs/java-common`에 `ResilienceClientFactory` 추가
  - 표준 HttpClient 빌더
  - 표준 CircuitBreaker 설정 (오버라이드 가능)
  - 표준 Retry 설정 (오버라이드 가능)
- `AuthServiceClient`, `AccountServiceClient`(admin-service), 기타 해당 클라이언트 수정 → 팩토리 사용
- `platform/shared-library-policy.md` 준수

## Out of Scope

- 실제 타임아웃 값 변경 없음 (기존 값 유지)
- 새로운 서비스 간 HTTP 클라이언트 추가 없음
- 서비스 디스커버리/로드밸런서 변경 없음

---

# Acceptance Criteria

- [x] `libs/java-common`에 `ResilienceClientFactory`가 추가된다 (`com.example.common.resilience.ResilienceClientFactory`)
- [x] 팩토리가 CircuitBreaker, Retry, HttpClient(RestClient) 표준 설정을 제공한다
- [x] 3개 서비스 클라이언트가 팩토리를 통해 설정을 받는다 (account-service `AuthServiceClient`, auth-service `AccountServiceClient`, membership-service `AccountStatusClient`)
- [x] 기존 타임아웃/재시도 동작이 변경되지 않는다 (default 값 verbatim 이전)
- [x] 서비스별 오버라이드가 가능하다 (`buildCircuitBreaker(name, customizer)`, `buildRetry(name, customizer)`, `buildRestClient(baseUrl, connect, read)` 모두 파라미터/customizer로 오버라이드)
- [x] `libs/java-common`이 서비스 도메인 클래스에 의존하지 않는다 (`com.example.common.resilience` 패키지, spring-web + resilience4j만 의존)
- [x] `ResilienceClientFactory` 단위 테스트 추가 (8개 테스트)
- [x] 빌드 및 테스트 통과 (`:libs:java-common:test`, `:apps:account-service:test`, `:apps:auth-service:test`, `:apps:membership-service:test` 모두 BUILD SUCCESSFUL)

---

# Related Specs

- `platform/shared-library-policy.md`
- `specs/services/admin-service/dependencies.md`
- `specs/services/account-service/dependencies.md`
- `specs/services/auth-service/dependencies.md`

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

없음 — HTTP 클라이언트 구현 변경, API 계약 변경 없음

---

# Target Service

- `libs/java-common`
- `admin-service`, `account-service`, `auth-service` (클라이언트 보유 서비스)

---

# Architecture

Follow:

- `platform/shared-library-policy.md`
- 각 서비스의 infrastructure 계층 규칙

---

# Implementation Notes

- `ResilienceClientFactory`는 Spring Bean으로 등록하거나 static factory 메서드 제공.
- `CircuitBreakerConfig`, `RetryConfig`를 빌더 패턴으로 오버라이드 가능하게 설계.
- Resilience4j 버전이 서비스마다 다를 경우 libs/java-common의 버전을 기준으로 통일.
- `libs/java-common`의 기존 클래스와 네이밍 충돌 없는지 확인.

---

# Edge Cases

- 특정 서비스 클라이언트가 표준과 다른 CircuitBreaker 임계값을 필요로 하는 경우 → 팩토리 오버라이드 파라미터로 처리
- CircuitBreaker OPEN 상태에서 호출 시 `CallNotPermittedException` → 기존 예외 처리 유지

---

# Failure Scenarios

- Resilience4j 버전 불일치로 빌드 실패 → `libs/java-common`의 BOM/의존성 관리로 해결
- CircuitBreaker 설정 오타로 의도치 않은 동작 → 단위 테스트로 설정값 검증

---

# Test Requirements

- `ResilienceClientFactory` 단위 테스트: 표준 설정 생성, 오버라이드 설정 적용 검증
- 각 서비스 클라이언트 통합 테스트: WireMock으로 CircuitBreaker/Retry 동작 검증

---

# Definition of Done

- [x] Implementation completed
- [x] Tests added
- [x] Tests passing
- [x] Contracts updated if needed (N/A — HTTP 클라이언트 구현 변경, API 계약 변경 없음)
- [x] Specs updated first if required (N/A — 순수 리팩토링)
- [x] Ready for review

## Implementation Notes

- Scope: 프로그래매틱(constructor에서 `CircuitBreaker.of/Retry.of`/RestClient builder를 직접 구성)하던 3개 클라이언트만 리팩토링 대상.
- admin-service / community-service는 `@CircuitBreaker(name=)` / `@Retry(name=)` 어노테이션 + YAML 기반 (`resilience4j.circuitbreaker.instances.*`, `resilience4j.retry.instances.*`)이므로 동작 변경 위험을 피하기 위해 본 태스크 범위에서 제외.
- security-service `AccountServiceClient`는 Resilience4j를 사용하지 않고 자체 retry-loop를 구현하므로 별도 태스크 영역.
