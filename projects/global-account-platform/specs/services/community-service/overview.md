# community-service — Overview

> **Status: FROZEN** — global-account-platform의 product-layer demo consumer. 스코프 재개방 없이 신규 기능 태스크를 발행하지 않는다. 리뷰에서 생성된 fix 태스크(TASK-BE-021 등)만 수용한다.

## Purpose

아티스트 포스트·팬 커뮤니티 전담 서비스. 아티스트가 콘텐츠를 발행하고, 팬이 댓글·반응으로 상호작용하며, 팔로잉 아티스트 기반의 피드를 제공한다. Weverse 핵심 도메인인 "아티스트↔팬 상호작용"의 소유자.

멤버십 기반 접근 제어(프리미엄 포스트)는 membership-service를 동기 HTTP로 호출하여 위임한다. 콘텐츠 데이터는 이 서비스가 유일한 진실 소스이며, 결제·구독 정보는 여기서 보유하지 않는다.

## Callers

### 공개 경로 (gateway 경유)
- **팬(사용자)** — `GET /api/community/feed`, `GET /api/community/posts/{postId}`, `POST /api/community/posts/{postId}/comments`, `POST /api/community/posts/{postId}/reactions`
- **아티스트(사용자)** — `POST /api/community/posts` (포스트 발행), `PATCH /api/community/posts/{postId}`, `DELETE /api/community/posts/{postId}`

## Callees

| 대상 | 목적 | 경로 |
|---|---|---|
| MySQL (`community_db`) | posts, comments, reactions, feed_subscriptions, outbox_events | 직접 JPA |
| Kafka | outbox relay → `community.*` 이벤트 발행 | producer |
| membership-service | 프리미엄 포스트 접근 권한 체크 | 내부 HTTP (`/internal/membership/access`) |
| account-service | 작성자 프로필(표시명) 조회 (캐시 후 사용) | 내부 HTTP (`/internal/accounts/{id}/profile`) |

## Owned State

### MySQL
- `posts` — 포스트 본문, 타입(ARTIST_POST / FAN_POST), 공개 범위(PUBLIC / MEMBERS_ONLY), 상태(DRAFT / PUBLISHED / HIDDEN / DELETED)
- `post_status_history` — **append-only**. 포스트 상태 변경 이력 (audit-heavy A3)
- `comments` — 포스트에 속한 팬 댓글. 소프트 삭제(`deleted_at`)
- `reactions` — 포스트별 계정별 반응(이모지). 계정당 포스트당 1개 제한 (upsert)
- `feed_subscriptions` — 팬이 팔로잉하는 아티스트 목록. 피드 쿼리의 기반
- `outbox_events` — `community.*` 이벤트 스테이징

## Change Drivers

1. **새 포스트 유형 추가** — 이미지·영상·라이브 포스트 등 타입 확장
2. **피드 알고리즘 변화** — 최신순에서 개인화 추천으로 전환 시 별도 서비스 분리
3. **공개 범위 추가** — SUPER_FANS 전용 등 멤버십 플랜 단계 세분화
4. **반응 유형 확장** — 이모지 종류 추가, 복수 반응 허용 정책
5. **댓글 계층 구조** — 대댓글 지원 추가 (현재 단일 레벨)

## Not This Service

- ❌ **구독/결제 관리** — membership-service의 책임
- ❌ **알림 발송** — notification-service가 이벤트 구독 후 처리 (현재 미구현)
- ❌ **계정 상태 관리** — account-service의 책임
- ❌ **미디어 저장** — media-service 별도 (현재 URL 참조만 저장)
- ❌ **검색** — search-service 별도 (현재 미구현)

## Related Specs

- Architecture: [architecture.md](architecture.md)
- Data model: [data-model.md](data-model.md)
- Dependencies: [dependencies.md](dependencies.md)
- Observability: [observability.md](observability.md)
- HTTP contracts: [../../contracts/http/community-api.md](../../contracts/http/community-api.md) (외부) + [../../contracts/http/internal/community-to-membership.md](../../contracts/http/internal/)
- Event contract: [../../contracts/events/community-events.md](../../contracts/events/community-events.md)
