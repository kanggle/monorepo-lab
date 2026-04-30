# gateway-service — Dependencies

## Internal HTTP (outgoing)

| 대상 | 목적 | 경로 | 계약 |
|---|---|---|---|
| `auth-service` | JWKS 페치 (JWT 서명 키) | `GET /internal/auth/jwks` | [../../contracts/http/internal/gateway-to-auth.md](../../contracts/http/internal/) |
| `auth-service` | `/api/auth/*` 요청 포워딩 | Spring Cloud Gateway route | [../../contracts/http/gateway-api.md](../../contracts/http/) |
| `account-service` | `/api/accounts/*` 요청 포워딩 | Spring Cloud Gateway route | [../../contracts/http/gateway-api.md](../../contracts/http/) |
| `admin-service` | `/api/admin/*` 요청 포워딩 (별도 필터 체인) | Spring Cloud Gateway route | [../../contracts/http/gateway-api.md](../../contracts/http/) |

**타임아웃·재시도·circuit breaker**: [rules/traits/integration-heavy.md](../../../rules/traits/integration-heavy.md) I1-I3 적용. JWKS 페치는 10분 주기 + 실패 시 이전 캐시 유지 + kid miss 시 즉시 refetch.

## Persistence

- **MySQL**: 없음 (gateway는 영속 상태를 가지지 않음)
- **Redis**: 있음 — 상세는 [redis-keys.md](redis-keys.md)
  - JWKS 캐시
  - Rate limit 토큰 버킷

## Messaging

- **Kafka**: 없음 (producer/consumer 모두 없음)
- 게이트웨이는 동기 HTTP 경로에만 존재 — 이벤트 경로는 각 서비스가 자체적으로 관리

## Shared Libraries

| Lib | 용도 |
|---|---|
| [libs/java-web](../../../libs/java-web) | 공통 에러 포맷, DTO 베이스, Spring Web 설정 헬퍼 |
| [libs/java-security](../../../libs/java-security) | JWT 검증 유틸리티, kid 매칭 로직 |
| [libs/java-observability](../../../libs/java-observability) | Request ID 필터, MDC, Prometheus 메트릭, OTel 트레이싱 |
| [libs/java-test-support](../../../libs/java-test-support) | Testcontainers 베이스, WireMock 헬퍼 |

`libs/java-common`, `libs/java-messaging`은 **사용하지 않음** (도메인 모델·DB·이벤트 없음).

## External SaaS / 3rd-Party

없음. 게이트웨이는 외부 SaaS와 직접 통합하지 않는다. OAuth 콜백 라우트 같은 것은 auth-service로 전달만 한다.

## Runtime Dependencies

| 구성 요소 | 버전 / 제약 |
|---|---|
| Java | 21 (LTS) |
| Spring Boot | 3.x |
| Spring Cloud Gateway | Spring Cloud BOM 호환 버전 |
| Resilience4j | circuit breaker + rate limiter fallback (Redis 장애 시) |
| Micrometer | Prometheus registry |
| Jackson | JSON 처리 |

## Failure Modes

| 실패 종류 | 영향 | 대응 |
|---|---|---|
| Redis 장애 | Rate limit 동작 불가, JWKS 캐시 miss | Resilience4j fallback으로 rate limit fail-open 또는 fail-closed 정책 선택 (`rules/traits/integration-heavy.md` I2). JWKS는 마지막 성공 키를 in-memory로 보관 (grace period 5분) |
| auth-service JWKS 엔드포인트 장애 | 새 키로 서명된 토큰 검증 실패 | 이전 캐시된 JWKS로 일정 시간 계속 검증 + 경고 메트릭 |
| 다운스트림 타임아웃 | 해당 요청 504 또는 503 | Circuit breaker 열림 후 fast-fail |
| Kafka 장애 | 영향 없음 (gateway는 Kafka 사용 안 함) | — |
