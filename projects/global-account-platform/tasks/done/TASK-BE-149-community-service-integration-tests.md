# Task ID

TASK-BE-149

# Title

community-service — 통합 테스트 추가 (Repository slice · Application integration · Outbox relay)

# Status

ready

# Owner

backend

# Task Tags

- test
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

현재 community-service 에는 Unit + Controller slice 테스트만 존재하며 `platform/testing-strategy.md` 에서 요구하는 **Repository slice · Application integration · Outbox relay** 레이어 테스트가 없음.

아래 누락 커버리지를 추가한다:

1. **Repository slice** (`@SpringBootTest` + Testcontainers MySQL)
   - `PostRepositoryIntegrationTest` — `findFeedForFan` JPQL: PUBLISHED 필터, 팔로잉 아티스트만, soft-delete 제외, 페이지네이션
   - `FeedSubscriptionRepositoryIntegrationTest` — find/save/delete/exists

2. **Application integration** (`@SpringBootTest` + MockMvc + Testcontainers MySQL/Kafka + WireMock)
   - `PublishPostIntegrationTest` — 아티스트 포스트 발행 성공 (DB 저장 + status_history + outbox 엔트리); 팬 → 403
   - `AddCommentIntegrationTest` — PUBLIC 포스트 댓글 성공 (DB + outbox); MEMBERS_ONLY + FREE 팬 → 403; membership-service 503 → fail-closed (403)

3. **Outbox relay** (`@SpringBootTest` + Testcontainers MySQL/Kafka)
   - `CommunityOutboxRelayIntegrationTest` — `community.post.published` / `community.comment.created` / `community.reaction.added` 가 Kafka 에 올바른 envelope 로 릴레이됨

---

# Scope

## In Scope

- `src/test/resources/application-test.yml` 신설 (Hikari/Kafka tuning, server.port=0, WireMock URLs, outbox.polling.interval-ms=500)
- `src/test/resources/keys/public.pem` + `private.pem` (auth-service 와 동일한 테스트 키 쌍)
- `CommunityIntegrationTestBase` — `AbstractIntegrationTest` 확장, WireMock 서버, JWT 헬퍼
- 위 5개 통합 테스트 클래스 신설

## Out of Scope

- 기존 Unit / Controller slice 테스트 수정 없음
- E2E / Contract 테스트 (별도 태스크)
- production 코드 변경 없음

---

# Acceptance Criteria

- [ ] `PostRepositoryIntegrationTest` — `findFeedForFan` 시나리오 4건 (PUBLISHED 필터, 팔로우 범위, soft-delete 제외, 페이지네이션)
- [ ] `FeedSubscriptionRepositoryIntegrationTest` — find/save/delete/exists 검증
- [ ] `PublishPostIntegrationTest` — 아티스트 발행 201 + DB/status_history/outbox 검증; 팬 발행 403
- [ ] `AddCommentIntegrationTest` — PUBLIC 댓글 201 + outbox; MEMBERS_ONLY FREE 팬 403; membership-service 503 fail-closed 403
- [ ] `CommunityOutboxRelayIntegrationTest` — 3개 이벤트 토픽 Kafka relay 검증 (Awaitility)
- [ ] `application-test.yml` 존재 (Hikari/Kafka 튜닝, WireMock URL 구성)
- [ ] `:apps:community-service:test` BUILD SUCCESSFUL

---

# Related Specs

- `platform/testing-strategy.md`
- `specs/services/community-service/architecture.md` (Testing Expectations 표)

---

# Related Contracts

- `specs/contracts/events/community-events.md`
- `specs/contracts/http/internal/community-to-membership.md`

---

# Target Service

- `community-service`

---

# Edge Cases

- `findFeedForFan` 결과에 `deleted_at != null` 포스트 포함되지 않아야 함 — `Post.status = PUBLISHED` 필터만으로는 부족; 현재 JPQL이 soft-delete를 별도 필터링하지 않으면 테스트로 발견됨
- membership-service 503 시 Resilience4j CB fallback `denyFallback` 이 `false` 반환 → `PostAccessGuard` 가 `MembershipRequiredException` throw → 403

# Failure Scenarios

- WireMock URL 미설정 시 실제 외부 서비스 호출 시도 → `application-test.yml` 에 base-url 지정 필수
- Kafka broker 미기동 시 outbox relay 테스트 타임아웃 → `AbstractIntegrationTest` 공유 Kafka 컨테이너 사용 필수
