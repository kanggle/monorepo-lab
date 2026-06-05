# Task ID

TASK-BE-147

# Title

auth-service — application 레이어 Redis 직접 사용 제거 (BE-146 후속 architecture cleanup)

# Status

done

# Owner

backend

# Task Tags

- refactor
- architecture
- security

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

TASK-BE-146 리뷰 도중 발견된 후속 권고: `ForceLogoutUseCase` 와 `OAuthLoginUseCase` 가 application 레이어에서 `StringRedisTemplate` 을 직접 주입받아 사용 중. 이는 `specs/services/auth-service/architecture.md:105` 의 "application 에서 JPA 엔터티·Redis 키 직접 사용 — 반드시 domain 의 포트 인터페이스 경유" 규칙 위반.

- `apps/auth-service/.../application/ForceLogoutUseCase.java:25,42` — `StringRedisTemplate` + private `setAccessInvalidation` 헬퍼 + `catch (Exception)` (`platform/coding-rules.md:32` 위반)
- `apps/auth-service/.../application/OAuthLoginUseCase.java:38,59,106` — OAuth state CSRF 토큰을 `redisTemplate.opsForValue().set/getAndDelete` 직접 호출

본 태스크는 두 use case 를 도메인 포트 경유로 교체:

1. `ForceLogoutUseCase` → 기존 `AccessTokenInvalidationStore` (TASK-BE-146 도입) 재사용. inline 헬퍼 + `StringRedisTemplate` 의존성 제거.
2. `OAuthLoginUseCase` → 신규 `OAuthStateStore` 포트 + `RedisOAuthStateStore` 어댑터 도입. `store(state, provider, ttl)` / `Optional<OAuthProvider> consumeAtomic(state)` 두 메서드.

---

# Scope

## In Scope

- `ForceLogoutUseCase` 의 `StringRedisTemplate` 의존성/헬퍼 제거 → `AccessTokenInvalidationStore.invalidateAccessBefore` 호출
- `OAuthStateStore` 도메인 포트 신설 (`apps/auth-service/.../domain/repository/`)
- `RedisOAuthStateStore` 어댑터 (`apps/auth-service/.../infrastructure/redis/`)
- `OAuthLoginUseCase` 가 신규 포트 호출, `StringRedisTemplate` 의존성 제거
- 어댑터 단위 테스트 (`RedisOAuthStateStoreTest`)
- 기존 `ForceLogoutUseCaseTest`, `OAuthLoginUseCaseTest` Mock 갱신
- 어댑터의 catch 범위는 `DataAccessException` (Spring Data Redis 의 모든 런타임 실패 super)

## Out of Scope

- OAuth state TTL 변경 (현행 10분 유지)
- state 형식/엔트로피 변경 (UUIDv7 그대로)
- 동작 변경 — 순수 architecture refactor

---

# Acceptance Criteria

- [x] `ForceLogoutUseCase` 가 `AccessTokenInvalidationStore` 호출, `StringRedisTemplate` 의존성 제거
- [x] `OAuthStateStore` 포트 신설, `RedisOAuthStateStore` 어댑터 신설
- [x] `OAuthLoginUseCase` 가 신규 포트만 사용, `StringRedisTemplate` 의존성 제거
- [x] 어댑터에서 적절한 catch 정책 — `RedisAccessTokenInvalidationStore` 는 `DataAccessException` 흡수(fail-soft), `RedisOAuthStateStore` 는 비흡수(fail-closed). `consumeAtomic` 은 GETDEL 단일 명령으로 원자성 보장, malformed value 만 `Optional.empty()` 매핑.
- [x] `:apps:auth-service:test` 통과 — 기존 테스트 회귀 없음
- [x] 어댑터 단위 테스트: `RedisOAuthStateStoreTest` (5건: store TTL/key, consume hit/miss/malformed/DataAccessException 전파)

---

# Related Specs

- `specs/services/auth-service/architecture.md` (특히 §Forbidden Dependencies)
- `platform/coding-rules.md` (catch (Exception) 제한)
- `specs/features/oauth-social-login.md` (state TTL)

---

# Related Contracts

- 없음 (내부 Redis 키 정책, 동작 불변)

---

# Target Service

- `auth-service`

---

# Architecture

`specs/services/auth-service/architecture.md` 의 hexagonal layering 준수: application → domain port → infrastructure adapter.

---

# Implementation Notes

- `OAuthStateStore.consumeAtomic` 은 GETDEL 단일 명령으로 원자성 보장 (현행 동작 유지). Redis 장애 시 `Optional.empty()` 반환 또는 예외 — fail-closed: state 미검증으로 OAuth 통과 막아야 하므로 `InvalidOAuthStateException` 으로 매핑되도록 상위에서 처리.
- `AccessTokenInvalidationStore` 는 fail-soft 가 합리적 (이미 ForceLogoutUseCase 에 commit 된 사이드이펙트 — refresh revoke + bulk 마커가 있으므로 access 마커 미작성 시에도 보안 가드는 부분 작동).
- `ForceLogoutUseCase.ACCESS_INVALIDATE_KEY_PREFIX` 상수는 어댑터로 이동 (이미 `RedisAccessTokenInvalidationStore` 에 동일 상수 존재 — 중복 제거).
- `OAuthLoginUseCase` 의 `STATE_KEY_PREFIX` / `STATE_TTL` 도 어댑터로 이동.

---

# Edge Cases

- OAuth state Redis 장애 시: 현행 동작은 GETDEL 결과가 null 이면 `InvalidOAuthStateException` 던지므로 자연스럽게 fail-closed. 어댑터가 `DataAccessException` 흡수하지 않고 전파 → `OAuthLoginUseCase` 에서 `InvalidOAuthStateException` 으로 매핑하거나 그대로 전파(상위 핸들러가 401/500 처리). spec 의 의도 — fail-closed.
- access invalidation 마커 Redis 장애: 어댑터에서 흡수 (TASK-BE-146 와 동일).

---

# Failure Scenarios

- 포트 분리 후 `OAuthLoginUseCase` 의 stub 으로 인해 OAuth 콜백 흐름 회귀 가능 — 통합 테스트(`OAuthLoginIntegrationTest`) 가 있다면 그 통과로 검증.
- `ForceLogoutUseCase` 의 access invalidation 마커가 누락되면 force-logout 후 access token 으로 통과 가능 — 어댑터 단위 테스트 + use case 단위 테스트로 검증.

---

# Test Requirements

- `RedisOAuthStateStoreTest`:
  - store: `oauth:state:{state}` 키 + provider 값 + 10분 TTL 로 SET
  - consumeAtomic: GETDEL 호출 + 결과 반환 (`Optional<OAuthProvider>`)
  - 미존재 state → `Optional.empty()`
  - DataAccessException 전파 (fail-closed) 또는 `Optional.empty()` 반환 — 어느 쪽이든 `null` 흡수만 하지 않으면 OK
- 기존 `ForceLogoutUseCaseTest`: Mock 을 `AccessTokenInvalidationStore` 로 교체, key/TTL ArgumentCaptor 검증
- 기존 `OAuthLoginUseCaseTest`: state store Mock 으로 교체, store/consume 호출 검증

---

# Definition of Done

- [x] ForceLogoutUseCase 리팩터링 + 신규 단위 테스트 추가 (회귀 가드)
- [x] OAuthStateStore 포트 + 어댑터 + 어댑터 단위 테스트 신설
- [x] OAuthLoginUseCase 리팩터링 + 테스트 갱신 (Mock 을 OAuthStateStore 로 교체)
- [x] `:apps:auth-service:test` 통과
- [x] Ready for review
