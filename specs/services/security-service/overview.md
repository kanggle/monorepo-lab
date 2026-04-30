# security-service — Overview

## Purpose

비동기 **보안 분석 서비스**. auth-service가 발행한 로그인·토큰 이벤트를 Kafka로 소비하여 (1) 불변 로그인 이력을 적재하고 (2) 비정상 로그인을 탐지하며 (3) 심각한 탐지 결과에 대해 account-service에 자동 잠금 명령을 발행한다. 부수적으로 admin-service의 감사 조회를 위한 **좁은 read-only HTTP 표면**을 제공한다.

[rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A1·A3의 **불변 감사 로그** 소유자 — `login_history`는 이 서비스 외에는 아무도 쓰지 못한다.

핵심 설계 원칙: **동기 로그인 플로우(auth-service)에서 분리**. security-service의 장애·지연은 로그인 응답 시간에 영향을 주지 않는다.

## Callers

### Primary (Kafka)
- 토픽 구독:
  - `auth.login.attempted`
  - `auth.login.failed`
  - `auth.login.succeeded`
  - `auth.token.refreshed`
  - `auth.token.reuse.detected`

### Secondary (read-only HTTP — 내부 전용)
- **admin-service** — 감사 조회:
  - `GET /internal/security/login-history?accountId=&from=&to=`
  - `GET /internal/security/suspicious-events?accountId=&from=&to=`

이 HTTP 표면은 의도적으로 **매우 좁게** 유지된다. 상태 변경 엔드포인트 추가 금지 ([architecture.md](architecture.md) Forbidden 섹션). 확대되면 `security-query-service`로 분할하는 것이 올바른 방향.

## Callees

| 대상 | 목적 | 경로 |
|---|---|---|
| MySQL (`security_db`) | login_history (append-only), suspicious_events, processed_events, outbox_events | 직접 JPA |
| Kafka | consumer (위 5개 토픽), producer (outbox → `security.*`, `auto.lock.*`) | Kafka client |
| Redis | eventId 기반 dedupe (`security:event-dedup:{eventId}`), 탐지 규칙 임시 상태 | 직접 접속 |
| `account-service` | 자동 잠금 명령 (내부 HTTP) | [contracts/http/internal/security-to-account.md](../../contracts/http/internal/) |

## Owned State

### MySQL
- `login_history` — **append-only**. `account_id`, `event_id`, `outcome` (SUCCESS/FAILURE/RATE_LIMITED/TOKEN_REUSE), `ip_masked`, `user_agent_family`, `device_fingerprint`, `geo_country`, `occurred_at`. DB 트리거 또는 권한으로 UPDATE/DELETE 차단
- `suspicious_events` — `id`, `account_id`, `rule_code` (VELOCITY/GEO_ANOMALY/DEVICE_CHANGE 등), `risk_score`, `evidence` (JSON), `action_taken` (AUTO_LOCK/ALERT/NONE), `detected_at`
- `processed_events` — 이벤트 멱등 소비 기록 (`event_id`, `topic`, `processed_at`). TTL이 필요하다면 batch 정리
- `outbox_events` — `suspicious.detected`, `auto.lock.triggered` 발행 큐

### Redis
- `security:event-dedup:{eventId}` — TTL 24시간. `rules/traits/transactional.md` T8 보조
- 탐지 규칙 윈도우 카운터 (시간당 실패 횟수 등) — TTL은 규칙 윈도우에 맞춤

## Change Drivers

1. **새 탐지 규칙 추가** — 새로운 `SuspiciousActivityRule` 전략 구현 (예: 알려진 VPN/Tor exit node 탐지)
2. **규칙 임계치 튜닝** — velocity 한도, geo 거리 임계, 디바이스 변경 민감도
3. **새 보안 이벤트 토픽 구독** — auth-service가 추가로 발행하는 이벤트 소비
4. **감사 보존 기간 변경** — `login_history` 보존·아카이브 전략 조정 ([rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A4)
5. **자동 대응 정책 변화** — 자동 잠금 vs 알림 vs 수동 검토 경계 조정
6. **새 감사 조회 요구** — admin-service로부터의 새 조회 파라미터 (단, 여전히 read-only 원칙 유지)

## Not This Service

- ❌ **동기 로그인 차단** — auth-service가 동기 경로에서 직접 결정 (Redis 실패 카운터). security-service는 **비동기** 분석만
- ❌ **계정 상태 변경 실행** — account-service가 상태 기계 소유. security-service는 **명령 발행**만 (`POST /internal/accounts/{id}/lock`)
- ❌ **credentials / 세션 관리** — auth-service의 책임
- ❌ **운영자 UI** — admin-service가 조회 API를 호출하고, UI는 admin-service 또는 별도 프론트엔드
- ❌ **실시간 알림** — 알림 발송 자체는 notification-service(해당되면) 또는 외부 메일·SMS provider. security-service는 이벤트 발행만

## Related Specs

- Architecture: [architecture.md](architecture.md)
- Dependencies: [dependencies.md](dependencies.md)
- Observability: [observability.md](observability.md)
- Redis keys: [redis-keys.md](redis-keys.md)
- HTTP contract (좁은 query 표면): [../../contracts/http/security-query-api.md](../../contracts/http/)
- HTTP contract (out-going): [../../contracts/http/internal/security-to-account.md](../../contracts/http/internal/)
- Event contracts: [../../contracts/events/auth-events.md](../../contracts/events/) (구독), [../../contracts/events/security-events.md](../../contracts/events/) (발행)
- Feature specs: [../../features/login-history.md](../../features/), [../../features/abnormal-login-detection.md](../../features/), [../../features/audit-trail.md](../../features/)
