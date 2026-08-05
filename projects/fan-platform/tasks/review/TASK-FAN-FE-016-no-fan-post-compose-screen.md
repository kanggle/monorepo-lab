# Task ID

TASK-FAN-FE-016

# Title

팬이 글을 쓸 화면이 없다 — `FAN_POST` 는 도메인의 1급 시민이고 API 도 201 을 내는데 프런트에 작성 진입점이 0개다

# Status

review

# Owner

fan-platform

# Task Tags

- frontend
- community

---

# 배경

`TASK-MONO-509`(팬 시드) AC-8 이 발굴했다. 티켓의 AC-4 "팬 게시물 작성이 브라우저에서
성공한다" 가 이 결함으로 **달성 불가**였다.

## 실측 (2026-08-05)

- 브라우저: 작성 진입점(`글쓰기` 버튼 / `/write` · `/compose` 링크) **0개**
- 코드: `FAN_POST` 문자열이 프런트 전체에서 `entities/post/types.ts` 와
  `__tests__/post-card.test.tsx` **두 곳뿐**이다. 쓰는 코드는 없고 타입만 있다
- API: `POST /api/v1/community/posts` `{"postType":"FAN_POST",...}` → **201**
  (시드가 이 경로로 글을 하나 넣는다)

즉 백엔드·도메인·타입은 전부 준비돼 있고 **화면만 없다.** `PostType` 이 두 값
(`ARTIST_POST` / `FAN_POST`)인 도메인에서 팬은 읽고 반응만 할 수 있다.

## 왜 눈에 띄지 않았나

피드는 **팔로우 기반**이라 자기 글이 자기 피드에 뜨지 않는다(자신을 팔로우하지 않는 한).
그래서 글을 쓸 수 없다는 사실도, 쓴 글이 어디에도 안 보인다는 사실도 화면에서
드러나지 않는다. 두 공백이 서로를 가린다.

---

# Goal

팬이 브라우저에서 글을 쓰고, 쓴 글을 다시 볼 수 있다.

---

# Scope

## In Scope

- 작성 화면/모달 + 진입점
- 쓴 글을 다시 보는 경로(자기 글 목록 또는 작성 후 상세 이동)

## Out of Scope

- `ARTIST_POST` 작성 — 저자 모델이 먼저다(`TASK-FAN-BE-045`)
- 미디어 업로드 — `mediaRefs` 는 있지만 저장소 배선은 별건

---

# 🔴 "쓴 글을 다시 볼 수 있다" 가 왜 Goal 에 들어가는가

작성만 붙이면 **글이 사라진 것처럼 보인다.** 피드 쿼리가 팔로우 기반이라 자기 글은
안 뜨고, `/posts/[id]` 는 id 를 알아야 간다. 작성 성공 후 상세로 보내든, "내 글"
목록을 만들든, **하나는 있어야 기능이 성립한다.** 이 항목이 없으면 다음 사람이
"작성은 되는데 어디로 갔나" 를 다시 조사하게 된다.

---

# Acceptance Criteria

- [ ] **AC-0 (재측정)** — 진입점 0개와 `FAN_POST` 참조 2곳을 다시 센다.
      그리고 **자기 글을 조회할 수 있는 기존 엔드포인트가 있는지** 전수로 확인한다
      (2026-08-05 기준 `PostController` 는 `GET /{postId}` 뿐 — 목록이 없다.
      없다면 백엔드 추가가 이 티켓의 전제이므로 **스코프를 다시 잡을 것**)
- [ ] **AC-1 (브라우저 작성)** — 작성 → `posts` 행이 늘고 → **그 글을 화면에서 다시 본다**.
      201 응답만으로는 부족하다
- [ ] **AC-2 (가시성 선택)** — 팬도 `visibility` 를 고를 수 있는지 결정한다.
      🔴 `PublishPostUseCase` 는 `postType` 만 검사하고 `visibility` 는 검사하지 않는다 —
      지금 API 는 **팬이 `PREMIUM` 글을 쓰는 것을 허용한다**(실측 가능). 화면이 그것을
      노출할지, 백엔드가 막을지 정한다. 어느 쪽이든 테스트로 고정한다
- [ ] **AC-3 (음성 대조)** — 로그인하지 않은 사용자에게 작성 진입점이 없어야 한다
- [ ] **AC-4 (시드 정합)** — `seed-fan.sh` 가 넣는 `FAN_POST`("첫 콘서트 후기")가
      새 화면에서 보인다. 안 보이면 시드가 아니라 화면의 문제다

---

# Related Specs

- `apps/community-service/.../presentation/controller/PostController.java`
- `apps/community-service/.../application/PublishPostUseCase.java` (`visibility` 미검사)
- `web/fan-platform-web/src/entities/post/types.ts`
- `infra/demo/seed/seed-fan.sh` (`FAN_POST` 를 API 로 넣는 부분)

# Edge Cases

- `title` 은 선택(`@Size(max=200)`), `body` 는 필수(1~10000자) — DTO 가 권위다
- 팬 글에도 댓글·리액션이 달린다(같은 엔드포인트) — 상세 화면이 그것을 보여줄지

# Failure Scenarios

- **작성만 붙이고 조회 경로를 빼먹는다** — 글이 사라진 것처럼 보인다(위 § 참조)
- **`visibility` 를 UI 에 그냥 노출한다** — 팬이 유료 글을 쓰고 아무도 못 읽는다

# Definition of Done

- [ ] 구현
- [ ] AC-1 브라우저 증거(행 증가 + 화면 재확인)
- [ ] AC-2 결정 + 테스트
- [ ] Ready for review
