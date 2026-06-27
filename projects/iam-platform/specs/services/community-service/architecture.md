# community-service — Architecture

This document declares the internal architecture of `community-service`.
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `community-service` |
| Project | `iam-platform` |
| Service Type | `rest-api` (single — see Service Type Composition below) |
| Architecture Style | **Layered Architecture + 명시적 상태 기계** |
| Domain | saas |
| Primary language / stack | Java 21, Spring Boot |
| Bounded Context | Community (아티스트 포스트 + 댓글 + 반응 + 팬 피드) |
| Deployable unit | `apps/community-service/` |
| Data store | MySQL (owned) |
| Event publication | Kafka via outbox (community.post.* lifecycle events) |
| Event consumption | none (single-type rest-api) |

### Service Type Composition

`community-service` is a single-type `rest-api` service per
`platform/service-types/INDEX.md`. 아티스트 포스트·팬 커뮤니티 서비스 —
포스트 발행, 댓글, 반응, 팬 피드 제공. 적용되는 규칙:
[platform/service-types/rest-api.md](../../../../../platform/service-types/rest-api.md).

---

## Architecture Style

**Layered Architecture + 명시적 상태 기계** — `presentation / application / domain / infrastructure` 4계층. `domain/post/status/`가 포스트 상태 기계를 소유.

## Why This Architecture

- **포스트 상태 전이가 비즈니스 규칙**: 아티스트 포스트는 DRAFT → PUBLISHED → HIDDEN/DELETED 순으로 전이하며, 임의 UPDATE 금지. 팬 포스트는 PUBLISHED 즉시 공개이나 신고·운영자 처리로 HIDDEN 전이 가능. 상태 기계가 이를 명시적으로 관리 ([rules/traits/transactional.md](../../../../../rules/traits/transactional.md) T4).
- **멤버십 접근 제어 위임**: 프리미엄 콘텐츠 접근 여부는 membership-service에 동기 HTTP로 위임. community-service는 구독 데이터를 직접 보유하지 않음 — 단일 책임 원칙.
- **감사 추적**: 포스트 상태 변경은 `post_status_history`에 append-only 기록 ([rules/traits/audit-heavy.md](../../../../../rules/traits/audit-heavy.md) A1·A3).
- **이벤트 발행**: 포스트 발행·댓글 생성·반응 추가는 outbox 패턴으로 Kafka 이벤트 발행 — 향후 notification-service·search-service 연결 기반.

## Internal Structure Rule

`domain/`·`application/`은 **package-by-feature** 구성(layered skill이 허용하는 대안 —
계층 의존 규칙은 그대로 유지). 집계 엔터티는 `domain/<feature>/`에 JPA 매핑된 도메인
엔터티로 두고(별도 `*JpaEntity` 없음 — 아래 *퍼시스턴스 매핑 규약* 참조), `infrastructure/persistence/`는
Spring Data `*JpaRepository` + 도메인 포트 구현 `*RepositoryImpl` 어댑터만 보유.

```
apps/community-service/src/main/java/com/example/community/
├── CommunityApplication.java
├── presentation/
│   ├── PostController.java              ← 포스트 CRUD
│   ├── FeedController.java              ← 피드 조회
│   ├── CommentController.java           ← 댓글 CRUD
│   ├── ReactionController.java          ← 반응 upsert/delete
│   ├── FeedSubscriptionController.java  ← 아티스트 팔로잉
│   ├── dto/                             ← 요청·응답 DTO
│   └── exception/
│       └── GlobalExceptionHandler.java
├── application/                         ← flat use-case 패키지 (package-by-feature)
│   ├── PublishPostUseCase.java
│   ├── UpdatePostUseCase.java
│   ├── ChangePostStatusUseCase.java
│   ├── GetFeedUseCase.java
│   ├── GetPostUseCase.java
│   ├── AddCommentUseCase.java
│   ├── AddReactionUseCase.java
│   ├── FollowArtistUseCase.java
│   ├── ActorContext.java / PostAccessGuard.java / PostMediaUrlsSerializer.java  ← use-case 지원
│   ├── PublishPostCommand.java / PostView.java / FeedItemView.java / FeedPage.java / UpdatePostResponse.java  ← command·view 레코드
│   ├── event/
│   │   └── CommunityEventPublisher.java
│   └── exception/                       ← 응용 예외(PostNotFound, MembershipRequired, Permission, Following 등)
├── domain/                             ← 집계별 패키지 (package-by-feature)
│   ├── post/
│   │   ├── Post.java                    ← 집계 루트 (JPA 매핑 도메인 엔터티 — Layered 허용)
│   │   ├── PostType.java                ← enum: ARTIST_POST / FAN_POST
│   │   ├── PostVisibility.java          ← enum: PUBLIC / MEMBERS_ONLY
│   │   ├── PostRepository.java          ← 리포지토리 포트(인터페이스)
│   │   ├── PageResult.java              ← framework-free 페이지 VO (피드 페이지네이션)
│   │   └── status/
│   │       ├── PostStatus.java          ← enum: DRAFT / PUBLISHED / HIDDEN / DELETED
│   │       ├── PostStatusMachine.java   ← 전이 규칙
│   │       ├── ActorType.java           ← enum: ARTIST / OPERATOR
│   │       ├── PostStatusHistoryEntry.java       ← append-only 감사 레코드 (POJO record)
│   │       └── PostStatusHistoryRepository.java  ← 포트(인터페이스)
│   ├── comment/
│   │   ├── Comment.java                 ← JPA 매핑 도메인 엔터티
│   │   └── CommentRepository.java       ← 포트
│   ├── reaction/
│   │   ├── Reaction.java                ← JPA 매핑 도메인 엔터티 (@IdClass 복합키)
│   │   └── ReactionRepository.java      ← 포트
│   ├── feed/
│   │   ├── FeedSubscription.java        ← JPA 매핑 도메인 엔터티 (@IdClass 복합키)
│   │   └── FeedSubscriptionRepository.java ← 포트
│   └── access/
│       ├── ContentAccessChecker.java    ← membership-service 호출 포트(인터페이스)
│       ├── ArtistAccountChecker.java    ← account-service artist 존재 확인 포트(인터페이스)
│       ├── AccountProfileLookup.java    ← account-service 작성자 표시명 조회 포트(인터페이스)
│       └── ArtistNotFoundException.java
└── infrastructure/
    ├── persistence/
    │   ├── PostJpaRepository.java / PostRepositoryImpl.java
    │   ├── CommentJpaRepository.java / CommentRepositoryImpl.java
    │   ├── ReactionJpaRepository.java / ReactionRepositoryImpl.java
    │   ├── FeedSubscriptionJpaRepository.java / FeedSubscriptionRepositoryImpl.java
    │   └── PostStatusHistoryJpaEntity.java / PostStatusHistoryJpaRepository.java / PostStatusHistoryRepositoryImpl.java
    ├── event/
    │   └── CommunityOutboxPollingScheduler.java  ← outbox → Kafka 발행
    ├── client/
    │   ├── MembershipAccessClient.java  ← ContentAccessChecker 구현체
    │   ├── AccountProfileClient.java    ← AccountProfileLookup 구현체 (작성자 표시명)
    │   └── AccountExistenceClient.java  ← ArtistAccountChecker 구현체
    ├── security/                        ← JWT actor-context 변환·issuer/tenant 클레임 검증
    └── config/                          ← Clock, JPA, OAuth2 ResourceServer/WebClient, Security
```

### 퍼시스턴스 매핑 규약

- **집계(Post · Comment · Reaction · FeedSubscription)**: layered skill이 도메인 엔터티에
  JPA 애너테이션을 명시적으로 허용하므로(*"JPA annotations are acceptable"*), 도메인 클래스가
  곧 JPA 엔터티이다. 별도 `*JpaEntity` POJO 분리를 두지 않는다 — `infrastructure/persistence/`는
  Spring Data `*JpaRepository`와 도메인 포트 구현 `*RepositoryImpl` 어댑터만 가진다.
- **감사 로그(PostStatusHistory)**: 유일한 POJO/JpaEntity 분리. append-only 쓰기 전용 투영이므로
  도메인은 불변 record `domain/post/status/PostStatusHistoryEntry`로 표현하고,
  `infrastructure/persistence/PostStatusHistoryJpaEntity`가 영속 매핑을 담당하며
  `PostStatusHistoryRepositoryImpl`이 경계에서 변환한다. (집계처럼 로드/수정되는 엔터티가 아님.)

## Allowed Dependencies

```
presentation → application → domain
                   ↓
            infrastructure → domain
```

- `domain/access/ContentAccessChecker` — 인터페이스 (port). `infrastructure/client/MembershipAccessClient`가 구현
- `application` → [libs/java-messaging](../../../../../libs/java-messaging) (outbox)

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
- **퍼시스턴스**: MySQL (`community_db`) — `posts`, `post_status_history`, `comments`, `reactions`, `feed_subscriptions`, `outbox` (v1, KEEP-auto-config), `community_outbox` (v2, TASK-BE-455)

### Outbox (v2)

> TASK-BE-455 — outbox v1 → v2 migration (in-worktree auth-service / finance account-service MySQL precedent, ADR-MONO-004 § 5).
>
> - **Write path**: `application.event.CommunityEventPublisher` is now a **port**; the impl `infrastructure.outbox.OutboxCommunityEventPublisher` builds the canonical 7-field envelope (`{eventId, eventType, source="community-service", occurredAt, schemaVersion=1, partitionKey, payload}` — **byte-identical** to the v1 `BaseEventPublisher.writeEvent` wire) and persists a `community_outbox` row (`infrastructure.persistence.CommunityOutboxJpaEntity implements OutboxRow`, MySQL `CHAR(36)` UUIDv7 PK = envelope `eventId`) inside the caller's `@Transactional`. Each publish method's payload-Map is copied VERBATIM from the v1 publisher; key = `postId`.
> - **Relay**: `infrastructure.outbox.CommunityOutboxPublisher extends AbstractOutboxPublisher<CommunityOutboxJpaEntity>` — `@Component`, no `@ConditionalOnProperty` gate (the v1 `CommunityOutboxPollingScheduler` had none; `@EnableScheduling` already on `CommunityApplication`). Plain `MicrometerOutboxMetrics(registry,"community")` (the v1 scheduler had no custom failure counter) + `community.outbox.pending.count` gauge. `topicFor` ported VERBATIM from the v1 `resolveTopic` — iam topics are **bare** (no `.v1` suffix): each `community.*` event → identically-named topic; reject-unmapped.
> - **Clock bean**: community-service ALREADY declares a `Clock systemUTC()` bean (`infrastructure.config.ClockConfig`); the new `OutboxConfig` adds ONLY a `TransactionTemplate` (a second `Clock` would be a duplicate-bean conflict).
> - **KEEP-auto-config**: the lib `OutboxAutoConfiguration` is NOT excluded — the v1 `outbox` (BIGINT/status) + `processed_events` tables are retained (still EntityScanned, required under `ddl-auto=validate`). In-flight v1 `outbox` rows at cutover are abandoned (low-volume, re-derivable).
> - **Migration**: `db/migration/V0006__community_outbox_v2.sql`.

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
