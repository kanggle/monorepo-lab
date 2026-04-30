# Task ID

TASK-BE-133

# Title

community-service — AddCommentUseCase / AddReactionUseCase 게시글 접근 검증 중복 제거 — PostAccessGuard 추출

# Status

ready

# Owner

backend

# Task Tags

- refactor

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

`AddCommentUseCase`와 `AddReactionUseCase` 두 곳에서 아래 9줄 블록이 동일하게 반복된다:

```java
Post post = postRepository.findById(postId)
        .orElseThrow(() -> new PostNotFoundException(postId));
if (post.getStatus() != PostStatus.PUBLISHED) {
    throw new PostNotFoundException(postId);
}

if (post.getVisibility() == PostVisibility.MEMBERS_ONLY
        && !post.getAuthorAccountId().equals(actor.accountId())) {
    boolean allowed = contentAccessChecker.check(actor.accountId(), GetPostUseCase.REQUIRED_PLAN_LEVEL);
    if (!allowed) {
        throw new MembershipRequiredException();
    }
}
```

`application` 패키지에 Spring `@Component` `PostAccessGuard`를 추가하고, `requirePublishedAccess(String postId, ActorContext actor): Post` 메서드를 제공한다. 두 use-case는 `postRepository` + `contentAccessChecker` 직접 주입 대신 `PostAccessGuard`를 주입하여 단일 메서드 호출로 대체한다.

---

# Scope

## In Scope

- `PostAccessGuard.java` 신규 생성 (`com.example.community.application` 패키지)
- `AddCommentUseCase.java` — `PostAccessGuard` 주입으로 변경, 9줄 블록 → 1줄 호출
- `AddReactionUseCase.java` — 동일하게 변경
- `PostAccessGuard`에 대한 단위 테스트 추가

## Out of Scope

- `GetPostUseCase` 변경 없음 (다른 status 분기 로직이 있음)
- `ChangePostStatusUseCase` 변경 없음
- API 계약 변경 없음
- 행위(behavior) 변경 없음

---

# Acceptance Criteria

- [ ] `PostAccessGuard` 클래스가 `com.example.community.application` 패키지에 생성된다
- [ ] `PostAccessGuard.requirePublishedAccess(String postId, ActorContext actor)` 가 `Post`를 반환한다
- [ ] `AddCommentUseCase`의 9줄 중복 블록이 `postAccessGuard.requirePublishedAccess(postId, actor)` 1줄로 대체된다
- [ ] `AddReactionUseCase`도 동일하게 대체된다
- [ ] `AddCommentUseCase`, `AddReactionUseCase` 두 클래스에서 `PostRepository`와 `ContentAccessChecker` 직접 의존이 제거된다
- [ ] `PostAccessGuard` 단위 테스트가 추가된다 (PUBLISHED 정상 케이스, 비게시 404, MEMBERS_ONLY 미구독 403)
- [ ] 기존 `AddCommentUseCaseTest`, `AddReactionUseCaseTest`가 있다면 모두 통과한다
- [ ] 빌드 통과

---

# Related Specs

- `specs/services/community-service/architecture.md`
- `specs/services/community-service/overview.md`

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`
- `.claude/skills/backend/architecture/layered/SKILL.md`

---

# Related Contracts

없음 — API 계약 변경 없음

---

# Target Service

- `community-service`

---

# Architecture

Follow:

- `specs/services/community-service/architecture.md`
- application 레이어 내부 리팩토링: 공유 헬퍼 컴포넌트 추출

---

# Implementation Notes

`PostAccessGuard` 구현:

```java
@Component
@RequiredArgsConstructor
class PostAccessGuard {

    private final PostRepository postRepository;
    private final ContentAccessChecker contentAccessChecker;

    Post requirePublishedAccess(String postId, ActorContext actor) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new PostNotFoundException(postId);
        }
        if (post.getVisibility() == PostVisibility.MEMBERS_ONLY
                && !post.getAuthorAccountId().equals(actor.accountId())) {
            boolean allowed = contentAccessChecker.check(
                    actor.accountId(), GetPostUseCase.REQUIRED_PLAN_LEVEL);
            if (!allowed) {
                throw new MembershipRequiredException();
            }
        }
        return post;
    }
}
```

- 클래스는 package-private(`class` without `public`)으로 선언 가능 — 같은 패키지의 use-case만 사용한다.
- `GetPostUseCase.REQUIRED_PLAN_LEVEL` 상수는 package-private이므로 동일 패키지에서 접근 가능.
- `AddCommentUseCase`는 `postRepository`, `contentAccessChecker` 필드를 제거하고 `postAccessGuard`를 추가.
- `AddReactionUseCase`도 동일.

---

# Edge Cases

- `postId`가 null인 경우: `postRepository.findById(null)`가 `PostNotFoundException`을 던지거나 JPA 레벨에서 처리 — 기존 동작과 동일.
- PUBLISHED 상태이지만 MEMBERS_ONLY이고 author 본인인 경우: 멤버십 체크 건너뜀 — 기존 로직 유지.
- `contentAccessChecker.check()` 가 503 응답(membership-service 다운) 등으로 false를 반환하는 경우: fail-closed 동작으로 `MembershipRequiredException` 던짐 — 기존 동작 유지.

---

# Failure Scenarios

- `PostAccessGuard` Bean이 Spring 컨텍스트에 등록되지 않으면 `AddCommentUseCase`, `AddReactionUseCase` 주입 실패 → `NoSuchBeanDefinitionException` — `@Component` 어노테이션으로 방지.
- `PostAccessGuard` 메서드가 `Post`를 반환하지 않고 void이면 caller에서 post를 재조회해야 하는 불필요한 추가 쿼리 발생 — 반드시 `Post` 반환.

---

# Test Requirements

`PostAccessGuardTest` 작성 (단위 테스트, Mockito):

- `requirePublishedAccess` — post가 PUBLISHED이고 PUBLIC이면 Post 반환
- `requirePublishedAccess` — post 미발견 시 `PostNotFoundException` 발생
- `requirePublishedAccess` — post가 PUBLISHED가 아닌 상태(DRAFT, HIDDEN)면 `PostNotFoundException` 발생
- `requirePublishedAccess` — MEMBERS_ONLY이고 non-author이고 구독 없으면 `MembershipRequiredException` 발생
- `requirePublishedAccess` — MEMBERS_ONLY이지만 author 본인이면 정상 반환
- `requirePublishedAccess` — MEMBERS_ONLY이고 non-author이고 구독 있으면 정상 반환

테스트 @DisplayName은 한국어 비즈니스 설명으로 작성.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] Contracts updated if needed (해당 없음)
- [ ] Specs updated first if required (해당 없음)
- [ ] Ready for review
