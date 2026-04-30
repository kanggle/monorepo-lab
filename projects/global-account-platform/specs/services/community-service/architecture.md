# Service Architecture — community-service

## Service

`community-service`

## Service Type

`rest-api` — 아티스트 포스트·팬 커뮤니티 서비스. 포스트 발행, 댓글, 반응, 팬 피드 제공.

적용되는 규칙: [platform/service-types/rest-api.md](../../../platform/service-types/rest-api.md)

## Architecture Style

**Layered Architecture + 명시적 상태 기계** — `presentation / application / domain / infrastructure` 4계층. `domain/post/status/`가 포스트 상태 기계를 소유.

## Why This Architecture

- **포스트 상태 전이가 비즈니스 규칙**: 아티스트 포스트는 DRAFT → PUBLISHED → HIDDEN/DELETED 순으로 전이하며, 임의 UPDATE 금지. 팬 포스트는 PUBLISHED 즉시 공개이나 신고·운영자 처리로 HIDDEN 전이 가능. 상태 기계가 이를 명시적으로 관리 ([rules/traits/transactional.md](../../../rules/traits/transactional.md) T4).
- **멤버십 접근 제어 위임**: 프리미엄 콘텐츠 접근 여부는 membership-service에 동기 HTTP로 위임. community-service는 구독 데이터를 직접 보유하지 않음 — 단일 책임 원칙.
- **감사 추적**: 포스트 상태 변경은 `post_status_history`에 append-only 기록 ([rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A1·A3).
- **이벤트 발행**: 포스트 발행·댓글 생성·반응 추가는 outbox 패턴으로 Kafka 이벤트 발행 — 향후 notification-service·search-service 연결 기반.

## Internal Structure Rule

```
apps/community-service/src/main/java/com/example/community/
├── CommunityApplication.java
├── presentation/
│   ├── PostController.java              ← 포스트 CRUD
│   ├── FeedController.java              ← 피드 조회
│   ├── CommentController.java           ← 댓글 CRUD
│   ├── ReactionController.java          ← 반응 upsert/delete
│   ├── FeedSubscriptionController.java  ← 아티스트 팔로잉
│   ├── dto/
│   └── exception/
├── application/
│   ├── PublishPostUseCase.java
│   ├── UpdatePostUseCase.java
│   ├── ChangePostStatusUseCase.java
│   ├── GetFeedUseCase.java
│   ├── AddCommentUseCase.java
│   ├── AddReactionUseCase.java
│   ├── FollowArtistUseCase.java
│   └── event/
│       └── CommunityEventPublisher.java
├── domain/
│   ├── post/
│   │   ├── Post.java                    ← 엔터티 (aggregate root)
│   │   ├── PostId.java
│   │   ├── PostType.java                ← enum: ARTIST_POST / FAN_POST
│   │   ├── PostVisibility.java          ← enum: PUBLIC / MEMBERS_ONLY
│   │   ├── status/
│   │   │   ├── PostStatus.java          ← enum: DRAFT / PUBLISHED / HIDDEN / DELETED
│   │   │   └── PostStatusMachine.java   ← 전이 규칙
│   │   └── repository/
│   │       └── PostRepository.java
│   ├── comment/
│   │   ├── Comment.java
│   │   └── repository/
│   │       └── CommentRepository.java
│   ├── reaction/
│   │   ├── Reaction.java
│   │   └── repository/
│   │       └── ReactionRepository.java
│   ├── feed/
│   │   ├── FeedSubscription.java
│   │   └── repository/
│   │       └── FeedSubscriptionRepository.java
│   └── access/
│       ├── ContentAccessChecker.java    ← membership-service 호출 포트(인터페이스)
│       ├── ArtistAccountChecker.java    ← account-service artist 존재 확인 포트(인터페이스)
│       └── ArtistNotFoundException.java
└── infrastructure/
    ├── persistence/
    │   ├── PostJpaEntity.java
    │   ├── PostStatusHistoryJpaEntity.java
    │   ├── CommentJpaEntity.java
    │   ├── ReactionJpaEntity.java
    │   ├── FeedSubscriptionJpaEntity.java
    │   └── *JpaRepository.java
    ├── kafka/
    │   └── CommunityKafkaProducer.java
    ├── client/
    │   ├── MembershipAccessClient.java  ← ContentAccessChecker 구현체
    │   ├── AccountProfileClient.java    ← 작성자 표시명 조회
    │   └── AccountExistenceClient.java  ← ArtistAccountChecker 구현체
    └── config/
```

## Allowed Dependencies

```
presentation → application → domain
                   ↓
            infrastructure → domain
```

- `domain/access/ContentAccessChecker` — 인터페이스 (port). `infrastructure/client/MembershipAccessClient`가 구현
- `application` → [libs/java-messaging](../../../libs/java-messaging) (outbox)

## Forbidden Dependencies

- ❌ `presentation/`이 infrastructure를 직접 참조
- ❌ 포스트 상태를 `UPDATE posts SET status = ?`로 직접 변경 — 반드시 `PostStatusMachine.transition()` 경유
- ❌ `infrastructure/client/`에서 구독 데이터 캐시를 로컬 DB에 저장 — membership-service가 진실 소스
- ❌ 외부 요청이 `/internal/*` 경로에 도달 — gateway는 내부 경로를 공개 라우트로 열지 않음

## Boundary Rules

### presentation/
- 공개: `/api/community/posts/**`, `/api/community/feed`, `/api/community/subscriptions`
- 모든 요청은 유효한 JWT (accountId 추출) 필요
- 응답에서 soft-delete된 콘텐츠(`deleted_at != null`) 제외

### application/
- `PublishPostUseCase`: 작성자 role 확인(ARTIST) → PostStatusMachine.transition(DRAFT→PUBLISHED) → post 저장 → `community.post.published` 이벤트 (outbox)
- `GetFeedUseCase`: feed_subscriptions 조회 → 팔로잉 아티스트 포스트 최신순 페이지네이션
- `AddCommentUseCase`: 포스트 존재 확인 → MEMBERS_ONLY이면 ContentAccessChecker 호출 → 댓글 저장 → `community.comment.created` 이벤트
- `AddReactionUseCase`: 중복 반응 upsert → `community.reaction.added` 이벤트

### domain/post/status/
- `PostStatusMachine.transition(current, target, actorType)`:
  - 아티스트: `DRAFT → PUBLISHED`, `PUBLISHED → HIDDEN`, `PUBLISHED → DELETED`
  - 운영자: `PUBLISHED → HIDDEN`, `HIDDEN → PUBLISHED`, `* → DELETED`
  - 금지: `DELETED → *` (삭제 후 복구 없음)

## Integration Rules

- **HTTP 컨트랙트 (외부)**: [specs/contracts/http/community-api.md](../../contracts/http/community-api.md)
- **HTTP 컨트랙트 (내부 발신)**: [specs/contracts/http/internal/community-to-membership.md](../../contracts/http/internal/community-to-membership.md)
- **HTTP 컨트랙트 (내부 발신, account-service)**: [specs/contracts/http/internal/community-to-account.md](../../contracts/http/internal/community-to-account.md)
- **이벤트 발행**: [specs/contracts/events/community-events.md](../../contracts/events/community-events.md) — `community.post.published`, `community.comment.created`, `community.reaction.added`
- **퍼시스턴스**: MySQL (`community_db`) — `posts`, `post_status_history`, `comments`, `reactions`, `feed_subscriptions`, `outbox_events`

## Testing Expectations

| 레이어 | 목적 | 도구 |
|---|---|---|
| Unit | `PostStatusMachine` 전이 규칙 (허용/불허) | JUnit 5 |
| Repository slice | JPA 쿼리, 피드 페이지네이션 | `@DataJpaTest` + Testcontainers (MySQL) |
| Application integration | use-case 트랜잭션 · outbox · 이벤트 | Testcontainers + Kafka |
| Controller slice | DTO validation · 접근 제어 응답 | `@WebMvcTest` |
| Client mock | MembershipAccessClient 503 시 fail-closed | WireMock |

**필수 시나리오**: MEMBERS_ONLY 포스트를 FREE 팬이 조회 시 403 / membership-service 503 시 fail-closed (403) / 아티스트만 포스트 발행 가능 / 계정당 포스트당 반응 1개 제한 / 삭제 포스트 피드 미노출.

## Change Rule

1. 포스트 상태 전이 규칙 변경 → [specs/features/](../../features/)에 feature spec 추가 선행
2. 공개 범위 세분화 → membership-service 플랜 레벨 변경과 동시 PR
3. 내부 API 경계 변경 → community-to-membership 컨트랙트 파일 먼저 갱신
