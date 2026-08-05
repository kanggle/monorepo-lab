# Task ID

TASK-FAN-BE-046

# Title

멤버십이 끝나도 피드 캐시가 유료 글의 제목·본문 미리보기를 최대 5분간 계속 내준다 — 상세 경로는 즉시 403 인데 피드만 열려 있다

# Status

ready

# Owner

fan-platform

# Task Tags

- backend
- community
- security

---

# 배경

`TASK-MONO-509`(팬 시드) AC-8 이 발굴했다.

`GetFeedUseCase` 는 Redis read-through 캐시를 쓰고, 캐시에 담기는 것은 **렌더된 페이지**다:

```java
boolean locked = isLocked(post, actor);
String title   = locked ? null : post.getTitle();
String preview = locked ? null : preview(post.getBody());   // 본문 앞 200자
```

즉 `locked` 판정과 그 판정에 따라 **드러낸 본문**이 함께 캐시된다. 무효화는
TTL(5분)뿐이다 — 클래스 javadoc 이 그 사실을 적어 두었지만, 거기서 예로 든 것은
"새 글 / 팔로우가 늦게 보인다" 는 **신선도** 문제다.

## 🔴 실제로는 신선도가 아니라 **자격**이다 (실측 2026-08-05)

```
# MEMBERS_ONLY 보유
detail  MEMBERS_ONLY  200
feed                  MEMBERS_ONLY=locked:false  title:"멤버십 전용 — 작업실 이야기"

# POST /api/v1/memberships/{id}/cancel  → 200 CANCELED

# 해지 직후 — 같은 계정, 같은 토큰
detail  MEMBERS_ONLY  403 MEMBERSHIP_REQUIRED   ← 상세는 즉시 닫힌다
feed                  MEMBERS_ONLY=locked:false  ← 피드는 열린 채, 제목도 그대로

# 인과 확정: redis 키 `feed:fan-platform:<accountId>:0:20` 를 비우면
feed                  MEMBERS_ONLY=locked:true   ← 즉시 닫힌다
```

캐시 키는 `(tenant, account, page, size)` 라 **타인에게 새는 것은 아니다.** 그러나
자격을 잃은 본인에게 유료 콘텐츠의 제목과 본문 200자를 계속 준다. 해지·만료·강등
어느 경로든 같다.

# Goal

자격이 바뀌면 피드의 잠금 상태가 **즉시** 따라간다 — 또는 캐시가 자격에 의존하는
값을 담지 않는다.

---

# Scope

## In Scope

- `GetFeedUseCase` / `FeedCache` 의 캐시 대상 재설계 또는 무효화 경로
- 자격 변경(구독·해지·만료·업그레이드) 시점의 반영

## Out of Scope

- 상세 경로 — 이미 올바르다(`PostAccessGuard` 는 매 요청 검사한다)
- 피드의 신선도(새 글/팔로우) 자체 — 이 티켓은 **자격**만 다룬다

---

# 🔴 두 방향, 그리고 왜 두 번째가 더 나을 수 있는가

| 안 | 모양 | 대가 |
|---|---|---|
| A. 무효화 | `membership.subscribed` / `.canceled` 를 구독해 그 계정의 피드 키를 지운다 | 이벤트가 늦거나 유실되면 다시 열린 채가 된다(fail-open) |
| B. 캐시에서 자격을 빼낸다 | 캐시에는 **잠기지 않은 원본**(id·타입·가시성·카운트)만 담고, `locked`/`title`/`preview` 는 **읽을 때** 계산한다 | 페이지당 `MembershipChecker` 호출이 남는다(캐시가 없애려던 바로 그 비용) |

B 는 "캐시가 권한 결정을 저장하지 않는다" 는 성질을 구조적으로 만든다 — A 는 그
성질을 이벤트 도달에 의존시킨다. 이 저장소에는 **이벤트가 조용히 도달하지 않은
전례**가 있다(`TASK-MONO-511`). 그 점을 근거로 저울질할 것.

---

# Acceptance Criteria

- [ ] **AC-0 (재측정)** — 위 세 줄(보유/해지 직후/캐시 비운 뒤)을 다시 받아 본다.
      그리고 캐시가 담는 필드를 전수로 다시 센다(`FeedItemView` 전 필드)
- [ ] **AC-1 (즉시성)** — 해지 직후 같은 토큰의 피드에서 그 글이 `locked:true` 이고
      `title`/`preview` 가 `null` 이다. **캐시를 비우지 않고** 그래야 한다
- [ ] **AC-2 (음성 대조 — 반대 방향도)** — 구독 **직후**에도 즉시 열려야 한다.
      잠기는 쪽만 고치면 "가입했는데 5분간 안 열린다" 가 남는다.
      🔴 한 방향만 단언하는 테스트는 이 결함의 절반을 재발시킨다
- [ ] **AC-3 (캐시가 여전히 산다)** — 같은 자격에서 연속 요청이 여전히 캐시 히트인지
      확인한다(고쳐서 캐시를 무력화하면 그건 삭제이지 수정이 아니다)
- [ ] **AC-4 (A 를 골랐을 때)** — 이벤트 유실 시 무엇이 남는지 적는다. fail-open 이면
      TTL 을 자격 창보다 짧게 두는 근거가 있어야 한다

---

# Related Specs

- `apps/community-service/.../application/GetFeedUseCase.java` (`isLocked` · TTL 주석)
- `apps/community-service/.../application/port/out/FeedCache.java`
- `apps/community-service/.../infrastructure/cache/FeedCacheRepository.java`
- `apps/community-service/.../application/PostAccessGuard.java` (올바른 쪽 — 비교 대상)

# Edge Cases

- 멤버십 **만료**(cancel 이 아니라 `validTo` 경과)는 이벤트가 없을 수 있다 — A 안의 사각지대
- 업그레이드(MEMBERS_ONLY → PREMIUM)는 **여는 쪽** 변경이다(AC-2)
- `actor.owns(author)` 인 글은 자격과 무관하게 열린다 — 캐시와 무관하게 정상

# Failure Scenarios

- **TTL 만 줄인다** — 창이 좁아질 뿐 성질은 그대로다. "5분이 30초가 됐다" 는 수정이 아니다
- **잠그는 방향만 테스트한다** — 구독 직후 5분 공백이 남고, 그건 데모에서 바로 보인다

# Definition of Done

- [ ] 결정 + 구현
- [ ] AC-1/AC-2 양방향 테스트
- [ ] 캐시 히트 유지 확인
- [ ] Ready for review
