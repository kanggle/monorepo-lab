# Task ID

TASK-FAN-FE-032

# Title

post 상세 화면에 댓글 **목록 + 작성 + 본인 삭제** UI 를 넣는다 — 소유자 결정(`TASK-FAN-FE-030`)의
전환 결정 구현

# Status

review

# Owner

fan-platform

# Task Tags

- frontend
- community

---

> **선행(전제) 티켓**: `TASK-FAN-BE-052` — 이 티켓이 필요로 하는
> `GET /api/community/posts/{postId}/comments` 읍기 경로를 연다. **그 티켓이 머지되기
> 전에는 이 티켓을 착수하지 않는다**(화면이 그릴 데이터가 없다).

---

# 배경

소유자 발화(verbatim, 2026-10-07 UTC): 「FAN-FE-030(팬 댓글 작성 화면을 넣을지) 넣어줘」

`TASK-FAN-FE-030` 이 "범위에 넣는다"로 결정되며 분기된 프런트 구현 티켓이다.
`specs/services/fan-platform-web/overview.md:57` 은 지금 다음과 같이 적혀 있다:

```
## Out of scope (v1)
- 댓글 composer UI — community-service 의 comment API 는 backend 존재, 본 frontend 는
  read-only display.
```

이 줄은 더 이상 소유자의 의도가 아니다 — 이 티켓의 Scope 가 그 줄을 고친다.

## 현재 코드 상태 (2026-10-06~07 UTC 실측)

- `features/post/ui/PostCard.tsx:97`, `features/post/ui/MyPostList.tsx:52` — `댓글
  {item.commentCount}` 숫자만 표시. 댓글을 **읍는** 호출이 어디에도 없다.
- `features/post/ui/memberPostDetail.tsx` — post 상세 화면. `ReactionBar` 는 이미 붙어
  있다(참고할 패턴) — 댓글 섹션은 없다.
- 백엔드: `TASK-FAN-BE-052` 가 머지되기 전까지 `GET /posts/{postId}/comments` 는 **존재하지
  않는다**(404). 이 티켓은 그 티켓이 먼저 머지된 뒤 착수한다.

---

# Goal

post 상세 화면(`/posts/[id]`)에서 팬이 그 글의 댓글을 **읍고**, 댓글을 **쓰고**, **자기가
쓴 댓글을 지울 수** 있다.

---

# Scope

## In Scope

1. **목록** — `GET /api/community/posts/{postId}/comments?page=&size=` 를 서버 측에서
   호출해 post 상세 화면에 댓글 목록을 그린다. 작성 순서(오름차순)로 렌더.
2. **작성(composer)** — 로그인한 팬이면 입력창 + 제출 버튼. 제출은 Server Action
   (`features/post/api/actions.ts` 의 `setReaction` 패턴을 참고 — 토큰은 서버에서만 다룸)으로
   `POST /api/community/posts/{postId}/comments` 호출. 성공 시 목록을 갱신해 새 댓글이 보여야
   한다(라우터 `revalidate` 또는 재조회 — 기존 `followArtist`/`unfollowArtist` 액션이 쓰는
   갱신 방식을 참고).
3. **본인 삭제** — 목록의 각 댓글 중 **호출자 자신이 작성한 것**에만 삭제 버튼을 보이고,
   `DELETE /api/community/posts/{postId}/comments/{commentId}` 호출. "자신이 작성한 것"의
   판정은 서버가 돌려주는 `authorAccountId` 와 세션의 accountId 비교 — 백엔드가 어차피
   403(`PERMISSION_DENIED`)으로 재차 거부하므로, 프런트 쪽 숨김은 UX 편의일 뿐 신뢰 경계가
   아니다.
4. **`overview.md:57` 수정** — "Out of scope (v1)" 의 그 줄을 지우고, 해당 기능이 v1 범위에
   들어왔음을 반영한다(`TASK-FAN-FE-032` 로 들어왔다는 각주 포함).

## Out of Scope

- 댓글 수정(edit) — 백엔드 계약에 없다(`DELETE`만).
- 댓글에 대한 반응(좋아요 등) — 제품 범위 밖, 요청 없음.
- 댓글 모더레이션(운영자 숨김/삭제) — v2 admin-service.
- `@-멘션` — `AddCommentUseCase` 주석이 이미 "멘션 문법 없음"을 명시.
- `TASK-FAN-BE-052` 자체의 백엔드 구현 — 그 티켓의 범위.

---

# 🔴 데모 백엔드가 아직 이 경로를 서빙하지 않는 동안에도 화면이 깨지면 안 된다

`TASK-FAN-BE-052` 가 머지돼도, 배포된 데모 백엔드(AMI)는 다음 재굽기 창까지 이 엔드포인트를
서빙하지 않는다(`myReaction`/`mediaRefs` 가 겪은 것과 같은 간극 —
`community-api.md` § `myReaction`/§ `mediaRefs` 참조). **그 기간 동안 댓글 목록 호출이
404 거나 응답이 없으면, 댓글 섹션만 숨기고 나머지 상세 화면(본문·반응·메타)은 정상
렌더되어야 한다** — 한 섹션의 부재가 전체 페이지 에러 경계(`app/error.tsx`)를 태우면 안
된다. `post.myReaction ?? null` 과 같은 관용구를 댓글 목록 fetch 에도 적용한다(빈 배열로
폴백).

---

# Acceptance Criteria

- [x] **AC-0 (전제 확인)** — `TASK-FAN-BE-052` 는 이 브랜치의 베이스 커밋(`b675955d2`,
      PR #4195)에 이미 머지되어 있다. 로컬/스테이징 라이브 200 확인은 데모 백엔드가
      AMI 재굽기를 거치기 전까지는 불가능하다(§ 🔴 절) — 코드 레벨 전제(계약 +
      `CommentController` 존재)는 `specs/contracts/http/community-api.md` § Comments —
      List 를 읽어 확인했다. 라이브 200 자체는 오케스트레이터가 재굽기 창에서 측정
      (AC-5 와 같은 분리).
- [x] **AC-1 (목록)** — `CommentPanel`(`features/post/ui/CommentPanel.tsx`) 이
      `initialComments`(서버가 오름차순으로 돌려준 배열)를 그대로 순서 보존해 렌더.
      빈 배열이면 `comment-empty` 테스트id로 "아직 댓글이 없습니다"를 보인다. 유닛:
      `comment-panel.test.tsx` § AC-1.
- [x] **AC-2 (작성)** — `addComment` 서버 액션(`features/post/api/comments.ts`)이 성공
      응답의 `Comment` 객체를 돌려주고, `CommentPanel` 이 그 객체를 로컬 state 에
      즉시 append — 브라우저 증거(렌더 결과에 새 댓글 텍스트가 보임)를 유닛
      테스트로 확인(`comment-panel.test.tsx` "🔴 bite: 서버 액션이 성공하면 같은
      화면에서 새 댓글이 바로 보인다").
- [x] **AC-3 (본인 삭제)** — `authorAccountId === viewerAccountId` 인 댓글에만
      `comment-delete` 버튼 렌더(다른 댓글엔 없음), 클릭 시 `window.confirm` →
      `deleteComment` 호출 → optimistic 제거, 서버가 거부(`ok:false`)하면 롤백.
      서버의 403 `PERMISSION_DENIED` 가 신뢰 경계이고 프런트 숨김은 UX 편의일 뿐이라는
      점을 `deleteComment` 자신의 주석에 명시. 유닛: `comment-panel.test.tsx` § AC-3
      (bite + 롤백 + confirm 취소).
- [x] **AC-4 (미인증/비로그인)** — `CommentPanel`은 `viewerAccountId=null` 이면
      composer 와 모든 댓글의 삭제 버튼을 숨긴다(유닛으로 확인). 🟡 **부분 이탈
      기록** — 이 AC 의 괄호 서술("middleware 가 이미 비로그인을 `/login` 으로
      보내는 gated 경로")은 재확인 결과 **이 경로에는 적용되지 않는다**:
      `shared/auth/public-paths.ts` 는 `/posts` 를 하위 경로까지 명시적으로
      공개(public)로 선언하고, 익명 방문자는 `PostDetailPage`의 두 번째 분기
      (`PublicPostDetail`, `@demo/public-data` 저장본만 읽는 **zero-gateway**
      경로 — `ADR-MONO-070`)로 간다. 댓글은 그 경로에 **없다**(반응 바도 같은
      이유로 없음 — `PublicPostDetail.tsx` 자체 주석 참조). 이 티켓은
      `memberPostDetail.tsx` 를 유일한 삽입 지점으로 지정했고, 댓글 목록을 공개
      zero-gateway 경로에 추가하는 것은 그 아키텍처 불변식을 깨는 새 결정이라
      HARDSTOP-09 범위로 보고 손대지 않았다. 결과: 로그인한 팬에게는 AC-4 가 요구한
      "작성/삭제 UI 안 보임"이 100% 성립(삭제 버튼이 아예 없는 익명 상태는 발생하지
      않음 — 익명은 댓글 섹션 자체를 보지 않음). "목록은 보되" 부분은 익명에게
      적용되지 않는다 — 익명은 이 페이지에서 원래도 게이트웨이를 호출하지 않는다.
- [ ] **AC-5 (404/부재 허용)** — 댓글 목록 API 가 404 또는 빈 응답이어도 post 상세 페이지
      전체가 깨지지 않고, 댓글 섹션만 숨겨진다(위 🔴 절 참조). 이 AC 는 **오케스트레이터가
      재굽기 창에서** 데모 환경 라이브로 측정한다 — 이 티켓의 로컬 구현 PR 은 코드로만
      대응한다(의도적으로 체크 보류). 코드 증거: `commentSection()`
      (`memberPostDetail.tsx`)이 `getComments` 가 던지는 모든 에러(404 포함)를
      catch 해 `null` 을 돌려주고, 그 경우 `comment-section` 자체가 렌더되지 않음을
      `comment-section-404.test.tsx` 로 bite 확인(404 ApiError + 네트워크 ApiError(0)
      두 칸 + 성공 대조군 + 빈 목록 대조군).
- [x] **AC-6 (overview.md 갱신)** — `specs/services/fan-platform-web/overview.md` 의
      "댓글 composer UI — … read-only display" 줄을 제거했고, Public surface 표의
      `/posts/[id]` 행에 댓글 기능을 추가 + 각주로 `TASK-FAN-FE-030`→`TASK-FAN-FE-032`
      전환 경위와 zero-gateway 경로엔 없다는 점을 기록, Responsibilities 절의
      Server Components/Actions 목록에도 `getComments`/`addComment`/`deleteComment`/
      `loadMoreComments` 를 추가했다.

---

# Implementation Note (in-progress, 2026-10-07 UTC)

- 구현 파일: `features/post/api/getComments.ts`(RSC 읍기, `server-only`),
  `features/post/api/comments.ts`(`'use server'` — `addComment`/`deleteComment`/
  `loadMoreComments`, 전부 `{ok, ...}` 결과 객체 — `publishFanPost` 와 같은 이유로
  throw 대신 결과 객체: Server Action 경계를 넘는 thrown 메시지는 프로덕션
  빌드에서 잘릴 수 있다), `features/post/ui/CommentPanel.tsx`(`'use client'`,
  로컬 state 로 목록 관리 — `FollowButton` 의 optimistic·rollback 관용구 재사용),
  `memberPostDetail.tsx` 에 `commentSection()` 헬퍼 추가(반환값 `ReactNode | null`
  관용구는 `memberPostDetail` 자신과 동일).
- 테스트: `comment-panel.test.tsx`(약 20 케이스 — 목록/빈 목록/composer 가시성/
  본인삭제/롤백/글자수 제한/더보기), `comment-section-404.test.tsx`(404·네트워크
  에러·성공·빈목록 4 칸). 기존 `reaction-restore.test.tsx` 는 `memberPostDetail`
  이 이제 `getComments` 도 참조하므로 그 모듈도 mock 하도록 1곳 수정(동작 불변,
  import 그래프만 따라감).
- 전체 vitest 48 files / 427 tests rc=0, `tsc --noEmit` rc=0, `next lint` rc=0,
  `next build` rc=0.
- bite: `comment-section-404.test.tsx` 의 `getComments.mockRejectedValue` 를
  지워 재현(섹션 생략 로직 삭제) → 해당 2 케이스 레드 확인 → 복원 → `cmp` 로
  원본과 바이트 동일 확인 → 재실행 그린.

---

# Related Specs

- `projects/fan-platform/specs/services/fan-platform-web/overview.md` § Out of scope (v1)
  (**변경 대상**, line 57)
- `projects/fan-platform/specs/services/fan-platform-web/architecture.md` § Package Layout
  (FSD feature slice 배치 참고)
- `projects/fan-platform/web/fan-platform-web/src/features/post/ui/memberPostDetail.tsx`
  (삽입 지점)
- `projects/fan-platform/web/fan-platform-web/src/features/post/ui/ReactionBar.tsx` +
  `features/post/api/actions.ts` (Server Action 갱신 패턴 참고)
- `projects/fan-platform/tasks/review/TASK-FAN-FE-030-no-comment-composer-ui-product-gap-decision.md`
  (이 티켓을 분기시킨 결정 티켓)

# Related Contracts

- `projects/fan-platform/specs/contracts/http/community-api.md` § Comments — `POST`/`DELETE`
  (기존) + `GET` 목록(**`TASK-FAN-BE-052` 가 추가, 이 티켓의 전제**)

---

# Edge Cases

- **빈 목록** — "아직 댓글이 없습니다" 류 상태, 에러로 보이면 안 된다.
- **긴 댓글 본문** — 백엔드 제약은 1..2000자(`AddCommentRequest`). 프런트도 같은 상한을
  입력 단계에서 안내.
- **MEMBERS_ONLY/PREMIUM post** — 목록 호출이 403 `MEMBERSHIP_REQUIRED` 를 받으면, 이미
  존재하는 `ApiError → ErrorState` 경로로 자연 처리되는지 확인(이 post 자체가 gated 이면
  상세 화면 전체가 이미 그 경로를 타므로 새 분기를 만들 필요가 없을 가능성이 높다 — 착수
  시 확인).
- **AMI 재굽기 간극** — 위 🔴 절. 로컬/스테이징에선 200, 데모 라이브에선 한동안 404/부재.

# Failure Scenarios

- **BE-052 머지 전에 착수한다** — 그릴 데이터가 없어 처음부터 깨진 화면을 만든다.
- **404 를 전체 페이지 에러로 전파한다** — 재굽기 창 전까지 데모 라이브의 모든 post 상세가
  깨진 것처럼 보인다(위 🔴 절이 막으려는 것).
- **본인 삭제 판정을 프런트에서만 믿는다** — 서버의 403 을 신뢰 경계로 유지하지 않으면
  (프런트 숨김이 유일한 방어가 되면) 요청을 조작한 사용자가 남의 댓글을 지울 수 있다는
  오해를 만든다 — 실제로는 서버가 막지만, 코드 리뷰에서 "프런트가 막는다"고 잘못 적으면
  다음 사람이 그 가정 위에 짓는다.
- **`overview.md` 갱신을 빼먹는다** — 스펙과 코드가 다시 드리프트해 다음 점검(`TASK-MONO-764`
  류)에서 "결함인지 공백인지" 질문이 재발한다.
