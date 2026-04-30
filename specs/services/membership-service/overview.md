# membership-service — Overview

> **Status: FROZEN** — global-account-platform의 product-layer demo consumer. 스코프 재개방 없이 신규 기능 태스크를 발행하지 않는다. 리뷰에서 생성된 fix 태스크만 수용한다.

## Purpose

구독 플랜 관리 및 프리미엄 콘텐츠 접근 제어 전담 서비스. 팬이 아티스트 팬 클럽을 구독(활성화)하고 만료·해지하는 생명주기를 소유한다. community-service는 프리미엄 포스트 접근 여부를 이 서비스에 위임한다.

결제 처리는 외부 결제 게이트웨이 책임이며 현 단계에서 stub 처리된다. 이 서비스는 결제 완료 이벤트를 수신해 구독을 활성화하는 역할만 담당한다.

## Callers

### 공개 경로 (gateway 경유)
- **팬(사용자)** — `POST /api/membership/subscriptions` (구독 활성화), `DELETE /api/membership/subscriptions/{id}` (해지), `GET /api/membership/subscriptions/me` (내 구독 상태 조회)

### 내부 경로 (gateway 우회)
- **community-service** — `GET /internal/membership/access` (프리미엄 콘텐츠 접근 권한 체크)

## Callees

| 대상 | 목적 | 경로 |
|---|---|---|
| MySQL (`membership_db`) | membership_plans, subscriptions, content_access_policies, outbox_events | 직접 JPA |
| Kafka | outbox relay → `membership.*` 이벤트 발행 | producer |
| account-service | 계정 상태 유효성 확인 (ACTIVE만 구독 허용) | 내부 HTTP (`/internal/accounts/{id}/status`) |
| 결제 게이트웨이 | 결제 처리 (현재 Stub — always succeed) | 외부 HTTP (향후 실제 연동) |

## Owned State

### MySQL
- `membership_plans` — FREE / FAN_CLUB 플랜 정의 (가격, 혜택, 접근 레벨)
- `subscriptions` — 계정별 플랜별 구독 이력. 상태(ACTIVE / EXPIRED / CANCELLED). 만료일(`expires_at`)
- `content_access_policies` — 컨텐츠 공개 범위와 필요 플랜 레벨 매핑 (community-service가 요청 시 조회)
- `outbox_events` — `membership.*` 이벤트 스테이징

## Change Drivers

1. **새 멤버십 플랜 추가** — SUPER_FAN, PREMIUM 등 단계 세분화
2. **결제 게이트웨이 연동** — stub → 실제 PG 연동, 결제 실패 처리 추가
3. **자동 갱신** — 구독 만료 전 자동 결제 재시도 로직
4. **멀티 아티스트** — 아티스트별 독립 구독 (현재는 플랫폼 레벨 단일 구독)
5. **환불 정책** — 해지 시 잔여 기간 환불 처리

## Not This Service

- ❌ **콘텐츠 데이터 관리** — community-service의 책임
- ❌ **계정 상태 관리** — account-service의 책임
- ❌ **결제 처리 실행** — 외부 결제 게이트웨이 (현재 stub)
- ❌ **알림 발송** — notification-service가 이벤트 구독 후 처리 (미구현)

## Related Specs

- Architecture: [architecture.md](architecture.md)
- Data model: [data-model.md](data-model.md)
- Dependencies: [dependencies.md](dependencies.md)
- Observability: [observability.md](observability.md)
- HTTP contracts: [../../contracts/http/membership-api.md](../../contracts/http/membership-api.md) (외부) + [../../contracts/http/internal/community-to-membership.md](../../contracts/http/internal/) (내부 수신)
- Event contract: [../../contracts/events/membership-events.md](../../contracts/events/membership-events.md)
