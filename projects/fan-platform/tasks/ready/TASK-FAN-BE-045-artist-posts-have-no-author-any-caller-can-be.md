# Task ID

TASK-FAN-BE-045

# Title

아티스트 엔티티에 계정이 없어 `ARTIST_POST` 를 **어떤 실제 호출자도 만들 수 없다** — 피드 조인이 요구하는 저자 id 를 발행 경로가 낼 수 없다

# Status

ready

# Owner

fan-platform

# Task Tags

- backend
- domain
- community

---

# 배경

`TASK-MONO-509`(팬 시드) AC-8 이 발굴했다. 시드가 `ARTIST_POST` 를 직접-DB 로 넣는 이유다.

## 세 사실이 서로 맞지 않는다 (전부 실측/코드 확인)

1. **피드는 저자 id 로 잇는다.** `PostJpaRepository.findFeedForFan`:

   ```sql
   WHERE p.authorAccountId IN (SELECT f.artistAccountId FROM Follow f
                               WHERE f.fanAccountId = :fanAccountId ...)
   ```

2. **팔로우 대상은 아티스트 엔티티 id 다.** `artists/[id]/page.tsx`:

   ```tsx
   <FollowButton artistAccountId={artist.id} initialFollowing={false} />
   ```

   그리고 `artists` 테이블에는 **`account_id` 컬럼이 없다**(실측 — 컬럼 15개 전수).
   `RegisterArtistRequest` 에도 id/계정 필드가 없어 외부에서 묶을 수 없다.

3. **발행은 저자를 호출자로 고정한다.** `PublishPostUseCase`:

   ```java
   Post draft = Post.createDraft(postId, actor.tenantId(), actor.accountId(), ...);
   ```

⇒ `ARTIST_POST` 가 피드에 뜨려면 `author_account_id == artists.id` 여야 하는데,
**그 값을 낼 수 있는 호출자가 존재하지 않는다.** ARTIST 역할 계정이 써도, 운영자가
써도(`TASK-MONO-512` 가 열린다 해도) 저자는 그 사람의 `sub` 이 되고, 그 글은
**그 아티스트를 팔로우한 팬의 피드에 뜨지 않는다.**

## 왜 지금까지 초록이었나

`PublishPostUseCase` 의 ARTIST 역할 게이트는 **단위 테스트로 잘 덮여 있다** — 역할이
없으면 `PermissionDeniedException` 이 난다는 것을. 그러나 그 테스트들은 저자 id 가
**팔로우 대상과 이어지는지**를 묻지 않는다. 발행과 피드가 각각 초록인데 둘을 잇는
불변식은 아무도 단언하지 않는다.

---

# Goal

`ARTIST_POST` 를 **실제 호출자가** 만들 수 있고, 그 글이 그 아티스트를 팔로우한 팬의
피드에 뜬다. 그리고 그 연결이 테스트로 고정된다.

---

# Scope

## In Scope

- 아티스트 엔티티 ↔ 계정의 연결 모델 결정
- `PublishPostUseCase` 의 저자 결정 규칙
- 발행 ↔ 피드 조인을 **함께** 단언하는 테스트

## Out of Scope

- 운영자 역할 발급 가능성 — `TASK-MONO-512`. **둘은 독립이다**: 그 티켓이 열려도
  이 문제는 남고, 이 티켓이 풀려도 그 문제는 남는다
- 팬 게시물 작성 UI — `TASK-FAN-FE-016`

---

# 🔴 고르기 전에 물어야 할 것

세 가지 형태가 가능하고, 셋이 서로 다른 제품을 만든다:

| 안 | 모양 | 대가 |
|---|---|---|
| A. `artists.account_id` 추가 | 아티스트가 로그인하는 실제 계정을 가진다 | 아티스트 온보딩(계정 발급) 경로가 필요하다 |
| B. 발행 시 `authorAccountId` 를 파라미터로 | 운영자가 "이 아티스트 이름으로" 쓴다 | **저자 위조 표면**이 열린다 — 누가 어느 id 로 쓸 수 있는지 규칙이 필요 |
| C. 팔로우/피드를 아티스트 id 가 아닌 **계정 id** 로 재정의 | 조인의 의미를 바꾼다 | 프런트·follows 테이블·기존 행 마이그레이션 |

**A 가 기본값이다** — 이 도메인은 "아티스트가 글을 쓴다" 를 모델링하고 있고, 계정이
없다는 것이 그 모델의 구멍이기 때문이다. B 를 고른다면 위조 방지가 AC 가 되어야 한다.

---

# Acceptance Criteria

- [ ] **AC-0 (재측정)** — 위 세 사실을 코드에서 다시 확인한다. 특히 `artists` 컬럼 전수와
      `PublishPostUseCase` 의 저자 대입. 하나라도 바뀌었으면 실제가 이긴다
- [ ] **AC-1 (결정)** — A/B/C 중 하나를 고르고 근거를 남긴다. 도메인 모델 변경이므로
      `projects/fan-platform/docs/adr/` 에 ADR 을 올린다
- [ ] **AC-2 (도달 가능성)** — 실제 호출자가 `ARTIST_POST` 를 만들고, **같은 테스트 안에서**
      그 아티스트를 팔로우한 팬의 피드에 그 글이 뜨는 것을 단언한다.
      🔴 발행 201 만 단언하는 테스트는 이 결함을 재발시킨다 — **조인을 건너뛰기 때문이다**
- [ ] **AC-3 (음성 대조)** — 팔로우하지 않은 팬의 피드에는 뜨지 않아야 한다.
      양성만으로는 "이어졌다" 와 "모두에게 보인다" 를 구별할 수 없다
- [ ] **AC-4 (B 를 골랐을 때)** — 저자 id 를 지정할 수 있는 주체가 제한되고, 그 제한이
      테스트로 고정된다(임의 id 로 쓰기 → 403)
- [ ] **AC-5 (시드 회수)** — `infra/demo/seed/seed-fan.sh` 의 두 번째 `dbexec --why` 가
      이 결함을 사유로 든다. 풀렸다면 그 블록을 API 로 옮긴다.
      🔴 옮길 때 **저자는 여전히 데모 계정이 아니어야 한다** — 데모 계정이 저자면
      `actor.owns()` 로 가시성 게이팅이 통째로 우회돼 시연이 공허해진다

---

# Related Specs

- `projects/fan-platform/specs/services/community-service/`
- `apps/community-service/.../infrastructure/jpa/PostJpaRepository.java` (피드 JPQL)
- `apps/community-service/.../application/PublishPostUseCase.java`
- `apps/artist-service/.../adapter/in/web/dto/request/RegisterArtistRequest.java`
- `web/fan-platform-web/src/app/(main)/artists/[id]/page.tsx` (`artistAccountId={artist.id}`)
- `infra/demo/seed/seed-fan.sh`

# Edge Cases

- `follows` 테이블에 이미 아티스트 엔티티 id 로 된 행이 있다(데모 시드 포함) — C 안은
  마이그레이션이 필요하다
- `PostAccessGuard` / `GetFeedUseCase` 의 `actor.owns(author)` 우회는 저자 모델이 바뀌면
  **의미가 달라진다**: 아티스트 계정이 생기면 그 계정은 자기 유료 글을 무료로 본다(정상)

# Failure Scenarios

- **발행만 테스트하고 끝낸다** — 지금과 같은 상태로 되돌아간다. 두 초록이 사이의
  불변식을 증명하지 않는다
- **B 를 고르고 위조 방지를 빠뜨린다** — 아무 팬이나 아티스트 이름으로 글을 쓴다

# Definition of Done

- [ ] ADR + 구현
- [ ] AC-2/AC-3 테스트(발행 → 피드 도달, 음성 대조 포함)
- [ ] `seed-fan.sh` 회수 여부 명시
- [ ] Ready for review
