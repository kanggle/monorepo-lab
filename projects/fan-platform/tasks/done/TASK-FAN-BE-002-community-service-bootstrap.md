# Task ID

TASK-FAN-BE-002

# Title

fan-platform community-service Spring Boot 부트스트랩 (post / comment / reaction / feed)

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- event
- deploy

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

fan-platform 의 핵심 도메인 service 인 `community-service` 를 부트스트랩한다. 이 service 는 **아티스트 1 : N 팬 비대칭 콘텐츠 관계** 의 중심 — 포스트 발행, 댓글, 반응, 팬 피드를 책임진다.

GAP 의 frozen [community-service](../../../global-account-platform/apps/community-service/) 가 동일 도메인의 mini-demo 로 이미 존재 — **본 task 는 그 4계층 + 상태 기계 패턴을 reference 로 복제하고, fan-platform 의 multi-tenant / content-heavy / read-heavy / membership-tier 요구사항을 +α 로 얹는다**.

이 태스크 완료 후:

- `projects/fan-platform/apps/community-service/` 에 Spring Boot 3.4 + JPA + Redis 기반 REST API 동작
- TASK-FAN-BE-001 gateway-service 의 placeholder route `community` 가 활성화 (`gateway → community-service:8080`)
- OAuth2 Resource Server (RS256, GAP JWKS, `tenant_id=fan-platform` 강제) — gateway 에서 이미 검증되지만 service 레벨 fail-closed 보장
- 핵심 엔티티: `Post` (DRAFT/PUBLISHED/HIDDEN/DELETED 상태 기계), `Comment`, `Reaction`, `Follow` (artist↔fan), `Feed` (read model)
- visibility tier: `PUBLIC` / `MEMBERS_ONLY` / `PREMIUM` — `PREMIUM` 은 membership-service 위임 (v2 도입 전까지는 항상 통과 + TODO 주석)
- outbox 패턴 — `community.post.published`, `community.comment.added`, `community.reaction.added` 이벤트 Kafka 발행 (notification/search 후속 연결 기반)
- audit append-only: `post_status_history` (상태 전이 기록)
- 단위 + 슬라이스 + Testcontainers integration test (Postgres + Kafka)
- `docker-compose.yml` 에 community-service + 의존 인프라 (postgres, kafka, redis 공유) 추가

---

# Scope

## In Scope

### 1. Project skeleton

- `projects/fan-platform/apps/community-service/` 디렉토리 + `build.gradle` (Spring Boot starter, Spring Web, Spring Data JPA, Flyway, Spring Kafka, Spring Data Redis, Spring Security OAuth2 Resource Server, Lombok, libs:java-common/web/security/observability/messaging)
- `Dockerfile` — multi-stage (Eclipse Temurin 21 JRE), bootJar
- `settings.gradle` 루트에 `'projects:fan-platform:apps:community-service'` include 추가

### 2. Application configuration

- `CommunityServiceApplication.java` (main)
- `application.yml` (default + `local` profile + `test` profile):
  - `spring.application.name: fan-platform-community-service`
  - `spring.datasource` (Postgres) — `jdbc:postgresql://${POSTGRES_HOST:postgres}:5432/${POSTGRES_DB:fanplatform_community}`
  - `spring.flyway.enabled: true`, locations `classpath:db/migration/community`
  - `spring.kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP:kafka:9092}`
  - `spring.data.redis` — feed cache 공유
  - OAuth2 RS server: issuer + JWKS URI ${OIDC_ISSUER_URL}
- `application-test.yml` — Testcontainers 가정

### 3. Domain layer (Layered + state machine, GAP frozen 패턴 따름 + 확장)

`apps/community-service/src/main/java/com/example/fanplatform/community/`:

```
├── CommunityServiceApplication.java
├── presentation/
│   ├── PostController.java                   ← /api/community/posts CRUD
│   ├── FeedController.java                   ← /api/community/feed
│   ├── CommentController.java                ← /api/community/posts/{id}/comments
│   ├── ReactionController.java               ← /api/community/posts/{id}/reactions
│   ├── FollowController.java                 ← /api/community/follow (artist↔fan)
│   ├── dto/                                  ← request/response DTO
│   ├── advice/
│   │   └── GlobalExceptionHandler.java       ← 401/403/404/409/422 envelope
│   └── filter/
│       └── TenantClaimEnforcer.java          ← service 레벨 tenant_id=fan-platform fail-closed
├── application/
│   ├── PublishPostUseCase.java
│   ├── UpdatePostUseCase.java
│   ├── ChangePostStatusUseCase.java          ← DRAFT→PUBLISHED, PUBLISHED→HIDDEN, etc.
│   ├── DeletePostUseCase.java
│   ├── GetPostUseCase.java                   ← visibility 검사 (PUBLIC/MEMBERS_ONLY/PREMIUM)
│   ├── GetFeedUseCase.java                   ← 팔로우 기반 + tenant scope
│   ├── AddCommentUseCase.java
│   ├── DeleteCommentUseCase.java
│   ├── AddReactionUseCase.java               ← 멱등 upsert
│   ├── RemoveReactionUseCase.java
│   ├── FollowArtistUseCase.java
│   ├── UnfollowArtistUseCase.java
│   ├── PostAccessGuard.java                  ← visibility + membership 검사
│   └── event/
│       └── CommunityEventPublisher.java       ← outbox 적재
├── domain/
│   ├── post/
│   │   ├── Post.java                         ← @Aggregate root, JPA @Entity
│   │   ├── PostId.java                       ← value object (UUID v7)
│   │   ├── PostType.java                     ← enum: ARTIST_POST / FAN_POST
│   │   ├── PostVisibility.java               ← enum: PUBLIC / MEMBERS_ONLY / PREMIUM
│   │   ├── status/
│   │   │   ├── PostStatus.java               ← enum: DRAFT / PUBLISHED / HIDDEN / DELETED
│   │   │   ├── PostStatusMachine.java        ← 전이 규칙 (DRAFT→PUBLISHED, PUBLISHED→HIDDEN, etc.)
│   │   │   └── PostStatusHistory.java        ← append-only audit
│   │   ├── PostMediaRef.java                 ← S3/MinIO key (실제 미디어는 v2)
│   │   └── repository/
│   │       └── PostRepository.java           ← port
│   ├── comment/
│   │   ├── Comment.java
│   │   ├── CommentId.java
│   │   └── repository/
│   │       └── CommentRepository.java
│   ├── reaction/
│   │   ├── Reaction.java
│   │   ├── ReactionType.java                 ← enum: LIKE / LOVE / FIRE / SAD
│   │   └── repository/
│   │       └── ReactionRepository.java
│   ├── follow/
│   │   ├── Follow.java                       ← (fan_id, artist_id, tenant_id) PK
│   │   └── repository/
│   │       └── FollowRepository.java
│   └── tenant/
│       └── TenantContext.java                ← ThreadLocal 또는 Spring SecurityContext 어댑터
└── infrastructure/
    ├── jpa/
    │   ├── PostJpaRepository.java
    │   ├── CommentJpaRepository.java
    │   ├── ReactionJpaRepository.java
    │   └── FollowJpaRepository.java
    ├── outbox/
    │   ├── OutboxEvent.java                  ← @Entity
    │   └── OutboxRelay.java                  ← @Scheduled, libs:java-messaging 활용
    ├── cache/
    │   └── FeedCacheRepository.java          ← Redis Sorted Set, key: feed:<tenant>:<account>
    └── security/
        └── ServiceLevelOAuth2Config.java     ← gateway 에서 이미 검증되지만 fail-closed 재검증
```

### 4. Database schema (Flyway)

- `db/migration/community/V1__init.sql`:
  - `posts` (id UUID, tenant_id, author_account_id, post_type, visibility, status, body, media_refs JSONB, created_at, updated_at, published_at, INDEX (tenant_id, status, published_at DESC), INDEX (tenant_id, author_account_id))
  - `post_status_history` (id, post_id, from_status, to_status, actor_account_id, actor_type, reason, occurred_at) — append-only
  - `comments` (id, post_id, tenant_id, author_account_id, body, created_at, INDEX (post_id, created_at))
  - `reactions` (post_id, reactor_account_id, reaction_type, tenant_id, created_at, PK (post_id, reactor_account_id))
  - `follows` (fan_account_id, artist_account_id, tenant_id, created_at, PK (fan_account_id, artist_account_id), INDEX (artist_account_id))
  - `outbox_events` (id, aggregate_type, aggregate_id, event_type, payload JSONB, tenant_id, created_at, processed_at, INDEX (processed_at NULLS FIRST, created_at))

모든 테이블에 `tenant_id` 컬럼 + multi-tenant 인덱스 prefix (rules/traits/multi-tenant.md M2 따름).

### 5. API contract (신규)

- `projects/fan-platform/specs/contracts/http/community-api.md` (신규):
  - `POST /api/community/posts` — 포스트 발행 (DRAFT 또는 PUBLISHED)
  - `GET /api/community/posts/{id}` — 단건 조회 (visibility + membership 검사)
  - `PATCH /api/community/posts/{id}` — 본문/미디어 수정 (PUBLISHED 후에는 제한)
  - `PATCH /api/community/posts/{id}/status` — 상태 전이 (HIDDEN/DELETED)
  - `GET /api/community/feed` — 팔로우 기반 피드 (paginated)
  - `POST /api/community/posts/{id}/comments` — 댓글 추가
  - `DELETE /api/community/posts/{id}/comments/{commentId}` — 댓글 삭제 (작성자 또는 운영자)
  - `PUT /api/community/posts/{id}/reactions` — 반응 추가/변경 (멱등 upsert)
  - `DELETE /api/community/posts/{id}/reactions` — 반응 제거
  - `POST /api/community/follows` — 아티스트 팔로우
  - `DELETE /api/community/follows/{artistId}` — 언팔로우
  - 모든 응답은 `{ data: ..., meta: ... }` envelope
  - 에러는 `{ code, message, details? }` (gateway 의 ApiErrorEnvelope 와 일관)
  - GAP 의 [community-api.md](../../../global-account-platform/specs/contracts/http/community-api.md) 형식 따름

### 6. Event contracts (신규 또는 GAP 패턴 복제)

- `projects/fan-platform/specs/contracts/events/community-events.md` (신규):
  - `community.post.published` — { post_id, author_account_id, tenant_id, post_type, visibility, published_at }
  - `community.post.status_changed` — { post_id, from, to, actor_account_id, occurred_at }
  - `community.comment.added` — { comment_id, post_id, author_account_id, tenant_id, occurred_at }
  - `community.reaction.added` — { post_id, reactor_account_id, reaction_type, tenant_id, occurred_at }
  - 이벤트 envelope: `{ event_id, event_type, occurred_at, payload, tenant_id }`
  - 멱등 키: `event_id` (UUID v7) — 컨슈머가 중복 처리 방지
- outbox 패턴 — `libs:java-messaging` 의 `OutboxEntity` / `OutboxRelay` 재사용

### 7. spec 작성

- `projects/fan-platform/specs/services/community-service/architecture.md` — Service Type=`rest-api`, Architecture Style=Layered + 명시적 상태 기계, GAP 의 동일 파일을 reference 로 복제 + fan-platform 변경 적용 (tenant 값, package 경로, +α 기능)
- `projects/fan-platform/specs/services/community-service/data-model.md` — 엔터티 / 인덱스 / multi-tenant 컬럼 (GAP 의 동일 파일 패턴)
- `projects/fan-platform/specs/services/community-service/dependencies.md` — postgres, redis, kafka, GAP IdP, membership-service (v2)
- `projects/fan-platform/specs/services/community-service/observability.md` — 메트릭, 로그, 트레이스
- `projects/fan-platform/specs/services/community-service/overview.md` — 서비스 책임 1-pager

### 8. Tests

- 단위:
  - `PostStatusMachineTest` — DRAFT→PUBLISHED, PUBLISHED→HIDDEN, 잘못된 전이 reject
  - `PostAccessGuardTest` — PUBLIC/MEMBERS_ONLY/PREMIUM 게이팅
  - `CommunityEventPublisherTest` — outbox 적재 검증
  - 각 use-case 클래스 단위 테스트
- 슬라이스:
  - `PostControllerTest` (`@WebMvcTest`)
  - `FeedControllerTest`, `CommentControllerTest`, `ReactionControllerTest`, `FollowControllerTest`
- 통합 (`@Tag("integration")`):
  - `CommunityServiceIntegrationTest` — Postgres + Kafka Testcontainer, 포스트 발행 → outbox → Kafka 메시지 발행 검증
  - `MultiTenantIsolationTest` — `tenant_id=fan-platform` 외 토큰 시 403
  - `OutboxRelayIntegrationTest` — outbox 적재 → relay → Kafka 발행 → outbox `processed_at` 갱신
  - `FeedQueryIntegrationTest` — 팔로우 기반 피드 조회 (페이지네이션 + Redis 캐시)
- 컨트랙트 테스트: `CommunityApiContractTest` (응답 스키마 검증, GAP CommunityApiContractTest 패턴 따름)

### 9. gateway-service 라우트 활성화

- TASK-FAN-BE-001 의 `application.yml` 의 `community` placeholder 라우트가 이미 `http://community-service:8080` 으로 향함. 본 태스크에서 community-service 가 실제로 응답하므로 별도 변경 불필요. 다만 라우트 metadata (rate-limit per-route 등) 는 community-service 운영 특성에 맞게 조정.

### 10. docker-compose 통합

- `projects/fan-platform/docker-compose.yml` 에 추가:
  - `community-service`: `expose: ["8080"]`, `depends_on: [postgres, kafka, redis]`, networks: `fan-platform-net`
  - 환경 변수: `OIDC_ISSUER_URL`, `JWT_JWKS_URI`, `POSTGRES_HOST`, `KAFKA_BOOTSTRAP`, `REDIS_HOST`, `SPRING_PROFILES_ACTIVE`
  - Traefik 라벨 불필요 (gateway 만 외부 노출)
  - postgres 컨테이너에 `community` 데이터베이스가 init script 또는 별도 DB 로 생성되도록 설정
- `kafka` 서비스가 -001 docker-compose 에 없으면 추가 (KRaft 모드, single broker, expose only)

## Out of Scope

- artist-service 부트스트랩 — TASK-FAN-BE-003
- 미디어 업로드 (S3/MinIO 통합) — v2 (별도 task)
- membership-service 통합 — v2 (현재는 PREMIUM 항상 통과 + TODO)
- notification-service 컨슈머 구현 — v2
- search-service 인덱싱 — v2
- 모더레이션 워크플로 (신고 → 처리) — v2 admin-service
- E2E 시나리오 (포스트 발행 → 피드 조회) — TASK-FAN-INT-001 (별도 task, -002 + -003 완료 후)
- frontend 통합 — TASK-FAN-FE-002

---

# Acceptance Criteria

- [ ] `./gradlew :projects:fan-platform:apps:community-service:build` 통과
- [ ] `./gradlew :projects:fan-platform:apps:community-service:check` 통과 (단위 + 슬라이스)
- [ ] `./gradlew :projects:fan-platform:apps:community-service:integrationTest` 통과 (Docker 필요, `@Tag("integration")`)
- [ ] `pnpm fan-platform:up` 후 `curl http://fan-platform.local/api/community/posts` 가 401 (인증 없음) 응답
- [ ] valid `tenant_id=fan-platform` JWT 로 `POST /api/community/posts` → 201 + outbox 행 생성
- [ ] outbox relay 가 Kafka 토픽 `community.post.published.v1` 에 메시지 발행
- [ ] `tenant_id=wms` JWT 로 시도 → gateway 가 403 차단 (service 까지 안 옴)
- [ ] `tenant_id=fan-platform` 이지만 service 레벨 fail-closed 재검증 동작 (gateway 우회 시뮬레이션 단위 테스트)
- [ ] PUBLISHED 포스트의 PUBLISHED → DRAFT 전이는 422 reject (상태 기계 위반)
- [ ] `MEMBERS_ONLY` 포스트를 비-멤버가 조회 시 403 `MEMBERSHIP_REQUIRED`
- [ ] Flyway 마이그레이션이 멱등 (재실행 시 변화 없음)
- [ ] 모든 테이블에 `tenant_id` 컬럼 존재 + 인덱스 prefix
- [ ] `specs/contracts/http/community-api.md` + `specs/contracts/events/community-events.md` 작성 + 구현과 일치
- [ ] `specs/services/community-service/architecture.md` 가 [platform/architecture-decision-rule.md](../../../../platform/architecture-decision-rule.md) 형식 따름

---

# Related Specs

- `projects/fan-platform/PROJECT.md` § Service Map (v1) — community-service 책임
- `projects/fan-platform/PROJECT.md` § GAP IdP Integration
- `projects/fan-platform/specs/integration/gap-integration.md` (TASK-FAN-BE-001 에서 작성)
- `rules/domains/fan-platform.md` § F1 (asymmetric content), F2 (fail-closed), F4 (audit trail), F7 (multi-tenant)
- `rules/traits/transactional.md` § T4 (state machine)
- `rules/traits/content-heavy.md`
- `rules/traits/multi-tenant.md` § M2 (tenant_id everywhere)
- `rules/traits/integration-heavy.md` § I3 / I8 (fail-open caches, outbox)
- `platform/event-driven-policy.md` (outbox 패턴)
- `projects/global-account-platform/apps/community-service/` (frozen demo — reference 패턴)
- `projects/global-account-platform/specs/services/community-service/architecture.md` (reference)
- `projects/global-account-platform/specs/services/community-service/data-model.md` (reference)
- `projects/wms-platform/apps/master-service/` (reference — Hexagonal master-data 패턴이지만 본 service 는 Layered)

# Related Skills

- `.claude/skills/backend/springboot-api/SKILL.md`
- `.claude/skills/backend/architecture/layered/SKILL.md`
- `.claude/skills/backend/exception-handling/SKILL.md`
- `.claude/skills/backend/validation/SKILL.md`
- `.claude/skills/backend/dto-mapping/SKILL.md`
- `.claude/skills/backend/transaction-handling/SKILL.md`
- `.claude/skills/backend/audit-logging/SKILL.md`
- `.claude/skills/backend/pagination/SKILL.md`
- `.claude/skills/backend/observability-metrics/SKILL.md`
- `.claude/skills/messaging/event-implementation/SKILL.md`
- `.claude/skills/messaging/outbox-pattern/SKILL.md`
- `.claude/skills/database/schema-change-workflow/SKILL.md`
- `.claude/skills/database/indexing/SKILL.md`
- `.claude/skills/database/transaction-boundary/SKILL.md`
- `.claude/skills/cross-cutting/caching/SKILL.md`
- `.claude/skills/testing/testcontainers/SKILL.md`
- `.claude/skills/testing/contract-test/SKILL.md`
- `.claude/skills/service-types/rest-api-setup/SKILL.md`

---

# Related Contracts

- `projects/fan-platform/specs/contracts/http/community-api.md` (신규)
- `projects/fan-platform/specs/contracts/events/community-events.md` (신규)
- `projects/global-account-platform/specs/contracts/http/community-api.md` (참조)

---

# Target Service / Component

- `projects/fan-platform/apps/community-service/` (신규)
- `projects/fan-platform/specs/services/community-service/{architecture,data-model,dependencies,observability,overview}.md` (신규)
- `projects/fan-platform/specs/contracts/http/community-api.md` (신규)
- `projects/fan-platform/specs/contracts/events/community-events.md` (신규)
- `projects/fan-platform/docker-compose.yml` (수정 — community-service + kafka 추가)
- `projects/fan-platform/.env.example` (수정 — 새 환경변수)
- `settings.gradle` (루트, community-service include)

---

# Architecture

`platform/architecture-decision-rule.md` 따름. Service Type = `rest-api`. Architecture Style = **Layered + 명시적 상태 기계** (GAP frozen community-service 와 동일).

---

# Implementation Notes

- **GAP 의 frozen [community-service](../../../global-account-platform/apps/community-service/) 를 첫 reference 로 복제** + 다음 변경:
  - 패키지: `com.example.community` → `com.example.fanplatform.community`
  - tenant 값: GAP 의 demo tenant → `fan-platform`
  - 추가 `MEMBERS_ONLY` / `PREMIUM` visibility 처리 (GAP demo 는 PUBLIC 만)
  - outbox + Kafka 발행 (GAP demo 는 in-process 이벤트만)
  - 모든 테이블 + 쿼리 `tenant_id` 컬럼 (multi-tenant.md M2)
- `tenant_id` claim 은 gateway 에서 이미 검증됨 — service 레벨에서는 **fail-closed 재검증** (gateway 우회 공격 방어). `TenantClaimEnforcer` 필터로 구현.
- `PostStatusMachine` 은 stateless utility — 전이 매트릭스를 enum 으로 명시. 잘못된 전이는 `InvalidStateTransitionException` (422).
- outbox 적재는 비즈니스 트랜잭션과 같은 트랜잭션. `OutboxRelay` 는 별도 스케줄러 (libs:java-messaging 활용) — at-least-once 발행, consumer 가 멱등.
- Feed 는 fan-out-on-write (피드 조회 시 follow 테이블 join) 가 아닌 **fan-out-on-read with cache**: 첫 조회 시 query → Redis Sorted Set 캐시 (TTL 5분). 캐시 invalidation 은 `community.post.published` 이벤트 컨슈머가 처리 (v2). v1 은 단순 read-through + TTL.
- 라우트 활성화: gateway-service 의 `community` 라우트는 -001 에서 이미 `http://community-service:8080` 으로 설정됨. 본 task 에서는 추가 변경 없이 community-service 가 응답하면 됨.

---

# Edge Cases

- **DRAFT 포스트의 PUBLISH 권한**: 작성자만 가능 (artist 계정 또는 fan 계정 — `post_type` 으로 판단). 운영자도 가능 (admin role).
- **PUBLISHED 포스트 본문 수정**: 발행 후 5분 이내만 허용 (간단 grace window). 그 이후 수정은 422 `EDIT_WINDOW_EXPIRED`.
- **DELETED 포스트의 댓글 / 반응**: 신규 댓글 / 반응 추가 reject (404 `POST_NOT_FOUND`). 기존 행은 유지하되 조회 시 hidden.
- **Cross-tenant 포스트 ID 추측**: 임의의 UUID 로 다른 tenant 포스트 조회 시도 → tenant_id 미스매치로 404 (403 아님 — 존재 자체를 누설하지 않음).
- **반응 멱등 upsert**: 같은 (post_id, reactor_account_id) 로 두 번째 호출 시 reaction_type 만 업데이트, 이벤트는 별도 발행 (`reaction.changed` v2 또는 동일 이벤트 두 번).
- **자기 자신 팔로우**: artist_id == fan_account_id → 422 `SELF_FOLLOW_FORBIDDEN`.
- **PREMIUM visibility (v1)**: membership-service 미존재 — v1 은 항상 통과 + 로그 + TODO 주석. v2 에서 hard fail-closed.
- **outbox 폭증**: relay 처리량보다 적재 속도가 빠르면 lag 증가. metric `community_outbox_lag_seconds` 노출 + alarm.

---

# Failure Scenarios

- **Postgres 다운**: 모든 쓰기 / 조회 5xx — gateway 의 503 envelope. circuit breaker (libs:java-web) 적용.
- **Kafka 다운**: outbox 적재는 성공 (DB 트랜잭션). relay 만 실패 — `processed_at` null 유지, 재시도 backoff. metric `community_outbox_publish_failures_total`.
- **Redis 캐시 다운**: feed 조회 직접 DB query (fail-open). metric `community_feed_cache_unavailable_total`.
- **JWKS 캐시 stale**: TASK-FAN-BE-001 과 동일 — 5분 TTL + manual refresh.
- **상태 기계 충돌**: 두 운영자가 동시에 같은 포스트를 HIDDEN ↔ PUBLISHED 전이 시 — `@Version` optimistic lock 으로 두 번째 쪽 409 `CONFLICT` 반환.
- **outbox dead letter**: `outbox_events.processed_at` 가 1시간 이상 null + retry > 10 → DLQ 적재 + 운영자 알림 (v2).

---

# Test Requirements

- 단위: 도메인 + use-case + status machine
- 슬라이스: 5 controller `@WebMvcTest`
- 통합 (`@Tag("integration")`):
  - `CommunityServiceIntegrationTest` — happy path (포스트 발행 → 댓글 → 반응 → 피드 조회)
  - `MultiTenantIsolationTest` — cross-tenant 포스트 조회 시 404 + write 차단
  - `OutboxRelayIntegrationTest` — outbox → Kafka → processed_at
  - `FeedQueryIntegrationTest` — 팔로우 기반 + 페이지네이션 + Redis 캐시
  - `MembershipGateIntegrationTest` — MEMBERS_ONLY visibility (membership-service mock + v1 PREMIUM 항상 통과)
- 컨트랙트: `CommunityApiContractTest` (요청/응답 스키마)

---

# Definition of Done

- [ ] community-service 코드 작성 완료 (presentation / application / domain / infrastructure)
- [ ] Flyway V1 마이그레이션 + 모든 테이블 `tenant_id` + multi-tenant 인덱스
- [ ] 단위 + 슬라이스 + 통합 + 컨트랙트 테스트 작성 + 통과
- [ ] `specs/services/community-service/{architecture,data-model,dependencies,observability,overview}.md` 작성
- [ ] `specs/contracts/http/community-api.md` + `specs/contracts/events/community-events.md` 작성
- [ ] `docker-compose.yml` 갱신 + community-service + kafka 추가
- [ ] `.env.example` 갱신
- [ ] `settings.gradle` 갱신
- [ ] gateway-service `community` 라우트 통한 end-to-end 동작 확인 (`pnpm fan-platform:up` + `curl http://fan-platform.local/api/community/posts` 401, valid JWT 로 201)
- [ ] outbox → Kafka 발행 통합 테스트 통과
- [ ] Ready for review
