# Task ID

TASK-BE-150

# Title

auth-service — infrastructure 레이어 `catch (Exception)` 정책 준수 감사 후속 수정

# Status

ready

# Owner

backend

# Task Tags

- refactor
- architecture

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

TASK-BE-149 작업 중 수행한 infra 레이어 감사에서 발견된 `catch (Exception)` 위반 15건 수정.

| 파일 | 건수 | 수정 방향 |
|---|---|---|
| `Redis*` 어댑터 4개 | 9 | `catch (DataAccessException e)` |
| `OutboxLagMetric` | 1 | `catch (DataAccessException e)` |
| `AccountServiceClient` | 4 | `catch (RuntimeException e)` |
| `Slf4jEmailSender` (email masking) | 1 | `catch (RuntimeException e)` |

**현행 유지(5건):** `GoogleOAuthClient`, `KakaoOAuthClient`, `MicrosoftOAuthClient`, `OidcJwksVerifier` — `OAuthProviderException` 경계 래핑 패턴으로 `KeyFactory`, `ObjectMapper` 등에서 checked exception 이 발생하므로 `catch (Exception)` 이 유일한 현실적 선택.

---

# Scope

## In Scope

- `RedisBulkInvalidationStore` × 3 → `DataAccessException`
- `RedisLoginAttemptCounter` × 3 → `DataAccessException`
- `RedisTokenBlacklist` × 2 → `DataAccessException`
- `RedisPasswordResetAttemptCounter` × 1 → `DataAccessException`
- `OutboxLagMetric` × 1 → `DataAccessException`
- `AccountServiceClient` × 4 → `RuntimeException`
- `Slf4jEmailSender` email 마스킹 유틸 × 1 → `RuntimeException`
- 각 파일에 `org.springframework.dao.DataAccessException` import 추가

## Out of Scope

- OAuth 클라이언트 / `OidcJwksVerifier` (현행 유지)
- 동작 변경 없음 — catch 범위 교체만

---

# Acceptance Criteria

- [ ] 위 15개 위치에서 `catch (Exception)` 완전 제거
- [ ] `DataAccessException` / `RuntimeException` 로 교체
- [ ] `:apps:auth-service:test` BUILD SUCCESSFUL

---

# Related Specs

- `platform/coding-rules.md` (catch (Exception) 제한)

---

# Related Contracts

- 없음 (내부 예외 범위 교체, 동작 불변)

---

# Target Service

- `auth-service`

---

# Architecture

Redis 어댑터 fail-open/closed 패턴: Spring `DataAccessException` 이 모든 Redis 연결 오류(`RedisConnectionFailureException` 등)의 상위 타입 — 기존 fail-open/closed 의미 보존.

---

# Edge Cases

- `DataAccessException`은 `RuntimeException` 하위 타입이므로 `RedisConnectionFailureException`, `QueryTimeoutException` 등 모두 포착됨 — catch 범위 축소 후 누락 없음.
- `AccountServiceClient` 내부 `doGet*` 메서드의 `catch (Exception e) → throw new RuntimeException(...)` 패턴: RestClient 모든 예외가 `RuntimeException` 하위 타입이므로 `catch (RuntimeException e)` 로 충분.

# Failure Scenarios

- `DataAccessException` 으로 교체 후 Redis 라이브러리가 비-DataAccessException을 던지면 catch 되지 않아 상위로 전파됨 → Spring Data Redis 는 모든 연결 오류를 `DataAccessException` 으로 래핑하므로 실제 발생하지 않음.
