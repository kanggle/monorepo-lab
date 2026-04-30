# Task ID

TASK-BE-138

# Title

community-service — UpdatePostUseCaseTest 추가 + PublishPostUseCaseTest @DisplayName/3-part 네이밍 보완 (fix TASK-BE-135)

# Status

ready

# Owner

backend

# Task Tags

- test

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

TASK-BE-135 리뷰에서 두 가지 문제가 발견되었다:

1. `UpdatePostUseCase`가 수정되었으나 단위 테스트 `UpdatePostUseCaseTest`가 존재하지 않는다.  
   task AC "기존 `UpdatePostUseCaseTest`가 모두 통과한다"는 파일이 원래부터 없어서 충족되지 않은 상태였다.

2. `PublishPostUseCaseTest`의 두 테스트 메서드에 `@DisplayName(Korean)` 어노테이션이 없으며,  
   메서드 이름이 `{method}_{condition}_{expectedResult}` 3-part 규칙을 따르지 않는다  
   (`platform/testing-strategy.md` 위반).

두 문제를 이 태스크에서 보완한다.

---

# Scope

## In Scope

- 신규 파일: `apps/community-service/src/test/java/com/example/community/application/UpdatePostUseCaseTest.java`
  - 단위 테스트 (Mockito, Spring 컨텍스트 없음)
  - Korean `@DisplayName` 필수
  - 3-part 메서드 이름: `{method}_{condition}_{expectedResult}`
  - 커버 시나리오:
    - 정상 업데이트 (author 본인)
    - 비저자(non-author)가 업데이트 시도 → `PermissionDeniedException`
    - 존재하지 않는 포스트 업데이트 시도 → `PostNotFoundException`
    - `mediaUrls`가 null 이거나 빈 리스트 → `null` 직렬화 (`PostMediaUrlsSerializer` real 사용)

- 수정 파일: `apps/community-service/src/test/java/com/example/community/application/PublishPostUseCaseTest.java`
  - 두 기존 테스트 메서드에 Korean `@DisplayName` 추가
  - 메서드 이름을 3-part 규칙으로 수정:
    - `artist_publishes_public_post_ok` → `execute_artistPublishesPublicPost_returnsPostView`
    - `fan_cannot_publish_artist_post` → `execute_fanPublishesArtistPost_throwsPermissionDenied`

## Out of Scope

- 프로덕션 코드 변경 없음
- 다른 UseCase / 파일 변경 없음
- `PostMediaUrlsSerializer` 변경 없음

---

# Acceptance Criteria

- [ ] `UpdatePostUseCaseTest`가 신규 생성된다
  - 3건 이상의 테스트 (정상 업데이트, 비저자 → PermissionDeniedException, 존재하지 않는 포스트 → PostNotFoundException)
  - 각 테스트에 Korean `@DisplayName` 존재
  - 3-part 메서드 이름 (`execute_{condition}_{expectedResult}`)
- [ ] `PublishPostUseCaseTest` 두 메서드에 Korean `@DisplayName` 추가됨
- [ ] `PublishPostUseCaseTest` 메서드 이름이 3-part 규칙 준수
- [ ] `./gradlew :apps:community-service:test` 통과

---

# Related Specs

- `specs/services/community-service/architecture.md`
- `platform/testing-strategy.md` (Korean `@DisplayName`, 3-part naming)

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

없음 — 테스트 보완만, 프로덕션 행위 변경 없음.

---

# Target Service

- `community-service`

---

# Edge Cases

- `mediaUrls = null` → `serialize(null)` → `null` (DB에 null 저장)
- `mediaUrls = List.of()` → `serialize(List.of())` → `null`
- 비저자가 업데이트 시도: `PermissionDeniedException`
- 존재하지 않는 postId: `PostNotFoundException`

---

# Failure Scenarios

- `PostRepository.findById` 가 `Optional.empty()` 반환 → `PostNotFoundException` 전파
- 비저자 `actor.accountId()` 불일치 → `PermissionDeniedException` 전파

---

# Test Requirements

- `UpdatePostUseCaseTest`:
  - `execute_authorUpdatesPost_contentIsUpdated`
  - `execute_nonAuthorUpdatesPost_throwsPermissionDenied`
  - `execute_nonExistentPost_throwsPostNotFound`
  - (선택) `execute_nullMediaUrls_mediaUrlsJsonIsNull`
- `PublishPostUseCaseTest`:
  - `@DisplayName` + 3-part 이름 적용

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing
- [ ] Contracts updated if needed (해당 없음)
- [ ] Specs updated first if required (해당 없음)
- [ ] Ready for review
