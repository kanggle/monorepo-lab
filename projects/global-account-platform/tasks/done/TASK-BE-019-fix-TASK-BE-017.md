# Task ID

TASK-BE-019

# Title

community-service — fix TASK-BE-017 review findings (레이어 위반, 감사 이력 누락, N+1, CB 테스트)

# Status

ready

# Owner

backend

# Task Tags

- code
- db

# depends_on

- (없음)

---

# Goal

TASK-BE-017 코드리뷰에서 발견된 4개 critical 이슈와 4개 warning을 수정한다.

---

# Scope

## In Scope

### Critical

1. **레이어 위반 — `ChangePostStatusUseCase`가 인프라 JPA 타입 직접 임포트**
   - `ChangePostStatusUseCase.java`가 `PostStatusHistoryJpaEntity`, `PostStatusHistoryJpaRepository` (infrastructure)를 직접 임포트
   - 수정: `domain/post/status/PostStatusHistoryRepository` 인터페이스 추가, infrastructure adapter 구현, use-case는 도메인 포트만 사용

2. **감사 이력 누락 — `PublishPostUseCase`가 `post_status_history` row 미기록**
   - `PublishPostUseCase`에서 DRAFT→PUBLISHED 전이 시 `post_status_history` 기록 없음 (audit-heavy A1, A3 위반)
   - 수정: `PublishPostUseCase`에서 `PostStatusHistoryRepository.save()` 호출 추가

3. **N+1 쿼리 — `GetFeedUseCase`에서 포스트당 댓글 수 + 반응 수 개별 조회**
   - 페이지당 최대 50개 포스트 × 2 쿼리 = 100 DB 쿼리
   - 수정: `commentRepository.countsByPostIds(List<String> postIds)`, `reactionRepository.countsByPostIds(List<String> postIds)` 추가 → 단일 집계 쿼리로 교체 (GROUP BY post_id)

4. **CircuitBreaker fail-closed 테스트 미검증 — Spring 컨텍스트 없는 raw 예외만 테스트**
   - `MembershipAccessClientTest`가 Spring-managed CB fallback(`denyFallback`)이 `false`를 반환하는지 검증하지 않음
   - 수정: `@SpringBootTest(webEnvironment=NONE)` + WireMock으로 503 응답 시 `contentAccessChecker.check()` → `false` 반환 확인

### Warning

5. **`ARTIST_NOT_FOUND` 404 미구현 — 팔로잉 시 아티스트 존재 확인 없음**
   - `FollowArtistUseCase`에서 아티스트 계정 존재 여부 미검증
   - 수정: community-service는 아티스트 존재 검증을 account-service 없이 feed_subscriptions 중복 삽입 시 DB 제약으로 처리. community-api.md의 404는 향후 account-service 통합 시 추가로 표시 (scope out of current task — 주석으로 TODO 추가)

6. **`PostStatusMachineTest`에서 AUTHOR `DRAFT→DELETED` 금지 전이 미검증**
   - 수정: 테스트 케이스 추가

7. **`CommunityEventPublisher`에서 `JsonProcessingException` 삼킴**
   - 직렬화 실패 시 로그만 남기고 계속 진행 → outbox 미기록인데 상태는 변경된 inconsistency
   - 수정: rethrow로 변경 (트랜잭션 롤백 유도)

8. **`CommentController`, `ReactionController`, `FeedSubscriptionController` `@WebMvcTest` 슬라이스 미작성**
   - 수정: MEMBERS_ONLY 포스트 댓글 시도 → 403 케이스 포함

## Out of Scope

- 인덱스 `DESC` 방향 추가 (MySQL 성능 최적화, 기능 영향 없음 — 별도 스프린트)

---

# Acceptance Criteria

- [ ] `ChangePostStatusUseCase`가 `PostStatusHistoryRepository` 도메인 포트만 임포트 (infrastructure import 없음)
- [ ] `PublishPostUseCase` 실행 후 `post_status_history`에 DRAFT→PUBLISHED row 기록
- [ ] `GetFeedUseCase` 댓글/반응 수 조회가 페이지당 2쿼리로 고정 (포스트 수와 무관)
- [ ] `MembershipAccessClientTest`: membership-service 503 → `contentAccessChecker.check()` → `false` 반환 (Spring CB fallback 검증)
- [ ] `PostStatusMachineTest`: AUTHOR DRAFT→DELETED 금지 전이 테스트 추가
- [ ] `CommunityEventPublisher`: 직렬화 실패 시 예외 rethrow
- [ ] `CommentControllerTest`, `ReactionControllerTest` `@WebMvcTest` 추가
- [ ] `./gradlew :apps:community-service:test` 모든 테스트 통과

---

# Related Specs

- `specs/services/community-service/architecture.md`
- `specs/contracts/http/community-api.md`
- `specs/contracts/events/community-events.md`

# Related Contracts

- `specs/contracts/events/community-events.md`

---

# Target Service

- `apps/community-service`

---

# Architecture

Layered 4-tier. `domain/post/status/PostStatusHistoryRepository` 도메인 포트 추가 필요.

---

# Edge Cases

- `countsByPostIds`에 빈 리스트 전달 시 빈 Map 반환 (IN 절 오류 방지)
- CircuitBreaker 테스트에서 WireMock stub 503 설정 후 충분한 실패 횟수로 OPEN 전이 유도

---

# Failure Scenarios

- `PostStatusHistoryRepository` 저장 실패 → 트랜잭션 롤백 (상태 변경도 취소됨)

---

# Test Requirements

- Unit: `PostStatusMachineTest` 추가 케이스
- Slice: `CommentControllerTest`, `ReactionControllerTest` 신규
- Integration: `MembershipAccessClientCbTest` (`@SpringBootTest` + WireMock, CB fail-closed 검증)
- Repository: `@DataJpaTest` — `countsByPostIds` 집계 쿼리 (선택사항)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] Architecture layer violation 해소 확인
- [ ] Ready for review
