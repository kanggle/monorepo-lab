# Task ID

TASK-BE-017

# Title

community-service 부트스트랩 — 아티스트 포스트 발행, 팬 댓글/반응, 피드, 멤버십 접근 제어

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- event
- db

# depends_on

- (없음 — membership-service는 WireMock으로 모킹)

---

# Goal

community-service를 실행 가능한 Spring Boot 애플리케이션으로 초기화하고, 아티스트 포스트 발행(`POST /api/community/posts`), 팬 피드 조회(`GET /api/community/feed`), 포스트 단건 조회(`GET /api/community/posts/{postId}`), 댓글 작성(`POST /api/community/posts/{postId}/comments`), 반응 추가(`POST /api/community/posts/{postId}/reactions`), 아티스트 팔로잉(`POST/DELETE /api/community/subscriptions/artists/{id}`)의 골든패스를 구현한다. MEMBERS_ONLY 포스트 접근 제어는 membership-service 내부 HTTP 호출로 위임하며, 모든 포스트 이벤트는 outbox 패턴으로 Kafka에 발행한다.

---

# Scope

## In Scope

- `apps/community-service/` 모듈 생성 (`settings.gradle` include 추가)
- 패키지 구조: `presentation / application / domain / infrastructure` ([architecture.md](../../specs/services/community-service/architecture.md))
- Flyway 마이그레이션: `posts`, `post_status_history`, `comments`, `reactions`, `feed_subscriptions`, `outbox_events` 테이블
- 공개 API: 포스트 CRUD, 피드 조회, 댓글 작성, 반응 upsert, 팔로잉 관리
- `PostStatusMachine` 도메인 객체 (DRAFT→PUBLISHED→HIDDEN/DELETED)
- `post_status_history` append-only 기록 (DB 트리거 포함)
- `ContentAccessChecker` 포트 + `MembershipAccessClient` WireMock 기반 인프라 구현체
- `AccountProfileClient` — 작성자 표시명 조회 (WireMock 모킹, 5분 캐시)
- Resilience4j CircuitBreaker — MembershipAccessClient (fail-closed: 접근 거부)
- Outbox 이벤트: `community.post.published`, `community.comment.created`, `community.reaction.added`
- Prometheus Micrometer 메트릭 ([observability.md](../../specs/services/community-service/observability.md))
- 단위 + slice + 통합 테스트

## Out of Scope

- notification-service 연동 (이벤트 발행만, 소비자 없음)
- 미디어 업로드 (URL 참조만 저장)
- 댓글 대댓글 (단일 레벨만)
- 포스트 검색 (search-service 별도)
- admin 운영자 포스트 숨김 API (향후)

---

# Acceptance Criteria

- [ ] `./gradlew :apps:community-service:bootRun` 성공, `/actuator/health` → 200
- [ ] `POST /api/community/posts` (ARTIST_POST, PUBLIC) → 201 + `community.post.published` outbox 이벤트 기록
- [ ] `POST /api/community/posts` (ARTIST_POST, MEMBERS_ONLY) → 201 성공
- [ ] FAN_POST 계정이 ARTIST_POST 시도 → 403 `PERMISSION_DENIED`
- [ ] `GET /api/community/posts/{postId}` PUBLIC 포스트 → 200
- [ ] `GET /api/community/posts/{postId}` MEMBERS_ONLY 포스트 + FAN_CLUB 구독 → 200 (WireMock: allowed=true)
- [ ] `GET /api/community/posts/{postId}` MEMBERS_ONLY 포스트 + 구독 없음 → 403 `MEMBERSHIP_REQUIRED` (WireMock: allowed=false)
- [ ] membership-service 503 시 MEMBERS_ONLY 포스트 → 403 (fail-closed)
- [ ] `GET /api/community/feed` → 200 + 팔로잉 아티스트 포스트 최신순 페이지네이션
- [ ] `POST /api/community/posts/{postId}/comments` → 201 + `community.comment.created` 이벤트
- [ ] `POST /api/community/posts/{postId}/reactions` → 200 + upsert (중복 요청 시 변경)
- [ ] `PostStatusMachine` 불허 전이 (DELETED→PUBLISHED) → 400 예외
- [ ] `post_status_history` append-only 트리거 동작 검증
- [ ] Flyway 마이그레이션 정상 실행
- [ ] `./gradlew :apps:community-service:test` — 모든 테스트 통과

---

# Related Specs

- `specs/services/community-service/architecture.md`
- `specs/services/community-service/overview.md`
- `specs/services/community-service/data-model.md`
- `specs/services/community-service/dependencies.md`
- `specs/services/community-service/observability.md`

# Related Skills

- `.claude/skills/service-types/rest-api-setup/SKILL.md`
- `.claude/skills/backend/springboot-api/SKILL.md`
- `.claude/skills/backend/validation/SKILL.md`
- `.claude/skills/database/schema-change-workflow/SKILL.md`

---

# Related Contracts

- `specs/contracts/http/community-api.md`
- `specs/contracts/http/internal/community-to-membership.md`
- `specs/contracts/events/community-events.md`

---

# Target Service

- `apps/community-service`

---

# Architecture

`specs/services/community-service/architecture.md` — Layered + 명시적 포스트 상태 기계. `ContentAccessChecker` 도메인 포트를 통해 membership-service 의존성을 역전.

---

# Edge Cases

- MEMBERS_ONLY 피드 항목: 구독 없는 팬에게 `locked: true` + body/title null 반환 (피드 전체 차단 X)
- 반응 upsert: 동일 계정이 동일 포스트에 다른 이모지로 재요청 시 기존 반응 교체
- 동시 팔로잉 요청 race → DB unique constraint (fan_account_id, artist_account_id) → 409
- 낙관적 락 (posts.version) 동시 수정 충돌 → 409 CONFLICT
- HIDDEN 포스트 피드 미노출 (status = PUBLISHED만)

---

# Failure Scenarios

- membership-service 503 → CircuitBreaker OPEN → fallback: `allowed=false` → 403 반환 (fail-closed)
- account-service 장애 → 캐시 표시명 사용, 캐시 없으면 `displayName: null` (포스트 본문 유지)
- MySQL 미기동 → 앱 기동 실패
- Kafka 장애 → outbox 저장 완료, 발행 지연 허용

---

# Test Requirements

- Unit: `PostStatusMachine` 전이 규칙 전체, `Post` 도메인 객체 생성 규칙
- Slice: `@WebMvcTest` — PostController, FeedController, CommentController, ReactionController
- Repository: `@DataJpaTest` + Testcontainers — 피드 페이지네이션, reactions upsert
- Integration: `@SpringBootTest` + Testcontainers + WireMock — 포스트 발행→피드 조회, MEMBERS_ONLY 접근 제어, CircuitBreaker fail-closed
- Client: WireMock — MembershipAccessClient (success/denied/503)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] Contracts match (community-api.md, community-to-membership.md, community-events.md)
- [ ] Flyway migration applied
- [ ] `post_status_history` immutability verified
- [ ] Prometheus metrics 엔드포인트 노출 확인
- [ ] Ready for review
