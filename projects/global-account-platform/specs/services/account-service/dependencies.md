# account-service — Dependencies

## Internal HTTP (incoming)

| 호출자 | 목적 | 엔드포인트 |
|---|---|---|
| `auth-service` | Credential lookup, 계정 상태 조회 | [../../contracts/http/internal/auth-to-account.md](../../contracts/http/internal/) |
| `security-service` | 자동 잠금 명령 (비정상 로그인 탐지 결과) | [../../contracts/http/internal/security-to-account.md](../../contracts/http/internal/) |
| `admin-service` | 운영자 lock/unlock/delete, 상태 조회 | [../../contracts/http/internal/admin-to-account.md](../../contracts/http/internal/) |

내부 엔드포인트 prefix는 `/internal/accounts/*`. 게이트웨이는 `/internal/*` 경로를 **퍼블릭 라우팅하지 않음** ([rules/domains/saas.md](../../../rules/domains/saas.md) S2).

## Internal HTTP (outgoing)

없음. account-service는 동기 경로에서 다른 서비스를 호출하지 않는다. 다운스트림 동기화는 모두 이벤트로 수행 (auth-service가 `account.status.changed`를 소비하여 세션 무효화 등).

## Persistence

### MySQL (`account_db`)

| 테이블 | 용도 | 특이사항 |
|---|---|---|
| `accounts` | `id`, `email` (unique index), `created_at`, `version` | Aggregate root, 낙관적 락 |
| `profiles` | `account_id`, `display_name`, `phone_number`, `birth_date`, `locale`, `timezone`, `preferences` (JSON) | PII 분류 등급 `confidential` ([rules/traits/regulated.md](../../../rules/traits/regulated.md) R1). `password_hash` 절대 없음 (S1) |
| `account_status_history` | `id`, `account_id`, `from_status`, `to_status`, `reason_code`, `actor_type`, `actor_id`, `occurred_at`, `details` (JSON) | **append-only**. DB 트리거로 UPDATE/DELETE 차단 ([rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A3) |
| `outbox_events` | 이벤트 발행 스테이징 | [libs/java-messaging](../../../libs/java-messaging) |
| `processed_events` | 해당 없음 | — |

상세 스키마는 [data-model.md](data-model.md).

### Redis

| 키 패턴 | 용도 | TTL |
|---|---|---|
| `signup:email-verify:{token}` | 이메일 검증 코드 | 24h |
| `signup:dedup:{email_hash}` | 중복 가입 요청 차단 | 5분 |

## Messaging

### Kafka Producer

| 토픽 | 이벤트 | 파티션 키 |
|---|---|---|
| `account.created` | 신규 가입 완료 | `account_id` |
| `account.status.changed` | 상태 전이 (active/locked/dormant/deleted 간) | `account_id` |
| `account.locked` | status.changed의 하위 종류 — 잠김 전용 | `account_id` |
| `account.unlocked` | status.changed의 하위 종류 — 잠금 해제 | `account_id` |
| `account.deleted` | 논리 삭제 완료 (유예 시작) / 익명화 완료 | `account_id` |

모든 이벤트는 outbox 경유. 계약: [../../contracts/events/account-events.md](../../contracts/events/).

### Kafka Consumer

초기 스코프에서 없음. 미래에 외부 소스의 계정 변경 이벤트를 구독할 가능성 있음 (예: IdP 페더레이션).

## Shared Libraries

| Lib | 용도 |
|---|---|
| [libs/java-common](../../../libs/java-common) | DTO 베이스, `Page<T>`, enum |
| [libs/java-web](../../../libs/java-web) | Spring Web, validation, 에러 응답 |
| [libs/java-messaging](../../../libs/java-messaging) | Outbox, Kafka producer wrapper |
| [libs/java-observability](../../../libs/java-observability) | 메트릭, 트레이스, MDC |
| [libs/java-test-support](../../../libs/java-test-support) | Testcontainers (MySQL + Kafka) |

`libs/java-security` **사용 안 함** — 인증은 gateway + auth-service가 처리하고, account-service는 이미 인증된 요청만 받는다 (내부 HTTP는 별도 인증 경계).

## External SaaS / 3rd-Party

| Provider | 용도 | 활성화 여부 |
|---|---|---|
| 이메일 provider (SendGrid / SES 등) | 가입 검증 이메일 | 초기 스코프 미정, mock/stub 사용 |
| SMS provider | 전화 번호 검증 (선택) | 미정 |

실제 발송은 [rules/traits/integration-heavy.md](../../../rules/traits/integration-heavy.md) 규칙에 따라 adapter layer로 격리. 초기 구현은 console log 또는 in-memory queue로 대체.

## Runtime Dependencies

| 구성 요소 | 버전 / 제약 |
|---|---|
| Java | 21 |
| Spring Boot | 3.x |
| Spring Data JPA | 3.x |
| QueryDSL | 5.x |
| Flyway | 9.x |
| MySQL driver | 8.x |
| Lettuce (Redis) | — |
| Spring Kafka | 3.x |
| Jackson | JSON + preferences 컬럼 직렬화 |
| Micrometer + Prometheus | — |

## Failure Modes

| 실패 | 영향 | 대응 |
|---|---|---|
| MySQL 장애 | 가입·프로필 변경·상태 전이 불가 | fail-closed. 읽기 경로(`GET /me`)는 read replica가 있다면 일부 계속 가능 |
| Redis 장애 | 이메일 검증 재전송 불가, dedup 우회 가능 | Redis 없이도 MySQL `accounts.email` unique 제약으로 중복 방지 (graceful degradation) |
| Kafka 장애 | 이벤트 발행 지연 → 다운스트림(auth/security) 동기화 지연 | Outbox에 유지, relay가 복구 시 발행 |
| 이메일 provider 장애 | 가입 검증 코드 발송 실패 | Circuit breaker + 재시도. 사용자에게 "이메일 발송에 실패했습니다, 재시도" 응답. 계정 자체는 생성하지 않음 |
| 동시 가입 요청 race | 두 번째 요청이 `email` unique 제약 위반 | 409 Conflict 반환 |
