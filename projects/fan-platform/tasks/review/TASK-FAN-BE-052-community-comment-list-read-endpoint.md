# Task ID

TASK-FAN-BE-052

# Title

community 에 댓글을 **목록으로 읍는** 경로가 없다 — 쓰기(`POST`/`DELETE`)만 있고, post 상세는
`commentCount` 숫자만 들고 있어서 composer 화면이 댓글을 그릴 데이터가 없다

# Status

review

# Owner

fan-platform

# Task Tags

- backend
- contract
- community

---

# 배경

`TASK-FAN-FE-030`(댓글 composer UI 소유자 결정 티켓)의 **선행 티켓**이다. 소유자가
2026-10-07 UTC 「FAN-FE-030(팬 댓글 작성 화면을 넣을지) 넣어줘」로 범위에 넣기로 결정했고,
그 착수 시 측정에서 전제가 절반만 사실이었음이 드러났다.

## 실측 (2026-10-07 UTC)

| 축 | 결과 |
|---|---|
| `specs/contracts/http/community-api.md` § Comments | `POST /api/community/posts/{postId}/comments` · `DELETE …/comments/{commentId}` — **끝.** 목록 읍기 경로가 없다 |
| `GET /api/community/posts/{id}` 응답 (§ Posts) | `commentCount`(집계) 만. 개별 댓글 본문은 어디에도 없다 |
| `CommentController` (코드) | `@PostMapping` + `@DeleteMapping` — **`@GetMapping` 없음** |
| `CommentRepository` (코드) | `countByPostId`/`countsByPostIds` 만. 페이지 조회 메서드 없음 |

⇒ **계약과 코드가 일치한다.** 드리프트가 아니라 진짜 공백이다 — `TASK-FAN-BE-049`(팔로우
읍기)·`TASK-FAN-BE-051`(반응 읍기)과 같은 클래스. composer 화면(`TASK-FAN-FE-032`)은 목록
없이는 쓸 수 없는 화면이므로, 이 티켓이 그 선행이다.

---

# Goal

community 가 한 post 의 댓글을 **페이지 단위로** 읍는 경로를 갖는다 — 계약 먼저, 그다음
구현. 가시성 규칙은 쓰기 경로(`POST`)와 동일해야 한다: 그 post 를 읍을 수 없는 호출자는
댓글도 읍을 수 없다.

---

# Scope

## In Scope

1. **계약** — `specs/contracts/http/community-api.md` § Comments 에
   `GET /api/community/posts/{postId}/comments?page=&size=` 를 명세한다. 응답 형태는 `POST`
   응답의 `data` 객체를 재사용하고, 페이지 필드는 `GET /posts/mine` 의 관례
   (`content`/`page`/`size`/`totalElements`/`totalPages`/`hasNext`)를 따른다. 정렬 순서와 그
   이유(대화는 위→아래로 읽는다 — `createdAt ASC`, `mine`의 `createdAt DESC`와 다름)를
   적는다.
2. **community-service** — 그 경로를 구현한다.
   - **가시성**: 새 질문을 다시 derive 하지 않는다 — `AddCommentUseCase` 가 쓰기 전에 이미
     돌리는 `PostAccessGuard.requirePublishedAccess(postId, actor)` 를 **그대로** 재사용해
     post 를 읍고, 그 결과로 나온 `Post` 의 id 로 댓글을 조회한다. 이렇게 하면 쓰기/읍기가
     가시성 판단에서 구조적으로 갈라질 수 없다.
   - 삭제된 댓글(`deletedAt IS NOT NULL`)은 제외한다 — `commentCount` 의 집계 조건과
     일치시킨다.
3. **테스트** — 유닛(`GetCommentsUseCase`) + 컨트롤러 슬라이스 + Testcontainers 통합. 이
   호스트는 Docker 가 없으므로 통합은 컴파일만 로컬로 확인하고 CI 가 권위다.
4. **게이트웨이** — `TASK-FAN-BE-049`/`TASK-FAN-BE-051` 이 반복해 지적한 대로, 라우트
   프레디킷(`Path=/api/v1/community/**`)이 메서드·깊이에 무관하다는 추론만으로 새 GET 이
   실제로 라우팅된다고 결론 내리지 않는다 — `GatewayRouteRewriteTest` 에 전용 케이스를
   추가해 게이트웨이를 통한 호출로 판정한다.

## Out of Scope

- 프런트 배선 — `TASK-FAN-FE-032` 소관이고, 이 티켓이 머지된 뒤 착수한다.
- 댓글 작성/삭제 **동작** 변경 — 지금 정상 작동한다.
- 댓글 수정(edit) — 계약에 없다. 신설 범위 밖.
- 데모 라이브 환경(AMI)에 이 변경이 실제로 반영되는 시점 — 다음 AMI 재굽기 창을 기다린다
  (`myReaction`/`mediaRefs` 가 겪은 것과 같은 모양). 그동안 프런트는 404/필드 부재를 허용해야
  한다(`TASK-FAN-FE-032` Edge Cases).

---

# Acceptance Criteria

- [x] **AC-0 (전제 재확인)** — 착수 시 `community-api.md` § Comments 에 목록 읍기 경로가
      **여전히 없고** `CommentController` 에 `@GetMapping` 이 **없는지** 확인한다. 생겼다면
      STOP. → 확인: 둘 다 없었음(전제 성립).
- [x] **AC-1 (계약 먼저)** — `community-api.md` § Comments 에 `GET` 경로가 명세되고, 응답
      형태·정렬·페이지 파라미터·에러가 적힌다. 계약 변경이 구현 커밋과 같은 PR 안에 있어야
      한다. → 같은 PR, 구현 전에 계약 커밋.
- [x] **AC-2** — 구현한 경로의 응답이 계약의 형태와 글자 그대로 일치한다. → `CommentListResponse`/`CommentResponse` 필드명이 계약과 일치(슬라이스 테스트로 고정).
- [x] **AC-3 (가시성 — 핵심)** — `POST`로 댓글을 쓸 수 없는 post 에서는 `GET` 목록도 거부된다:
      - MEMBERS_ONLY/PREMIUM post + 비멤버 팬 → 403 `MEMBERSHIP_REQUIRED` (POST 와 동일 코드)
      - 존재하지 않거나 cross-tenant post → 404 `POST_NOT_FOUND`
      - 작성자 본인은 멤버십 검사를 우회한다(POST/GET 상세와 동일)
      → `GetCommentsUseCase` 가 `AddCommentUseCase` 와 동일한
      `PostAccessGuard.requirePublishedAccess` 를 호출(재derive 하지 않음). 유닛
      (`GetCommentsUseCaseTest`) + 슬라이스(`CommentControllerSliceTest`)로 코드 레벨 고정,
      실제 DB 를 쓰는 격리 단언은 `CommentsListReadIntegrationTest`(Testcontainers, CI 권위).
- [x] **AC-4 (정렬 + 삭제 제외)** — 댓글 3개를 작성 순서대로 세팅하고(그중 1개는 이후 삭제)
      목록이 **작성 순서(오름차순)** 로, 삭제된 것을 뺀 2개만 반환됨을 단언한다. →
      `CommentsListReadIntegrationTest.returnsCommentsOldestFirstExcludingDeleted`(Testcontainers,
      CI 권위 — 이 호스트는 Docker 없음).
- [x] **AC-5** — 인증 없는 호출은 401. → 슬라이스(`listComments_withoutAuth_returns401`, 로컬
      실행·통과) + IT(`withoutBearer_returns401`, CI 권위).
- [x] **AC-6 (게이트웨이)** — `GatewayRouteRewriteTest` 에 신규 GET 경로가 실제로 게이트웨이를
      통해 라우팅되는지(메서드 GET 유지 + 경로 재작성) 확인하는 케이스를 추가한다. →
      `communityRouteRewritesCommentsListRead` 추가. `@Tag("integration")` 이라 로컬
      `test` 태스크에서 제외되고, Docker 가 없어 `integrationTest` 로도 로컬 실행은 안 됨 —
      컴파일만 로컬로 확인(`compileTestJava` rc=0), CI 가 권위.

---

# ✅ 실행 결과 (2026-10-07 UTC)

## 코드 변경

- `community-api.md` § Comments — `GET /api/community/posts/{postId}/comments` 명세 추가
  (정렬 결정 이유, 응답 형태, 에러 포함).
- `CommentRepository`/`CommentJpaRepository`/`CommentRepositoryImpl` — `findByPostId(postId,
  tenantId, page, size)` 추가. `createdAt ASC`, `deletedAt IS NULL`.
- `GetCommentsUseCase`(신규) — `PostAccessGuard.requirePublishedAccess` 를 그대로 호출해
  가시성을 재derive 하지 않음.
- `CommentResponse.from(Comment)` 오버로드 + `CommentListResponse`(신규, `MyPostsResponse`
  의 페이지 필드 관례 재사용).
- `CommentController` — `@GetMapping` 추가(`page`/`size` 쿼리 파라미터, `mine`/`feed` 와 같은
  clamp 는 `GetCommentsUseCase` 안에서).
- `GatewayRouteRewriteTest` — `communityRouteRewritesCommentsListRead` 추가.

## 테스트

- 신규: `GetCommentsUseCaseTest`(유닛 3케이스), `CommentsListReadIntegrationTest`(Testcontainers,
  7케이스), `CommentControllerSliceTest` 에 4케이스 추가.
- `community-service:test`(unit+slice, 전체) rc=0, `gateway-service:test` rc=0,
  `community-service:compileTestJava` rc=0(IT 포함 컴파일 확인).

## 🔴 로컬 측정의 한계 — Docker 없음 (BE-051 과 동일 사유)

이 Windows 호스트에는 Docker 가 없다. `CommentsListReadIntegrationTest` 와
`GatewayRouteRewriteTest.communityRouteRewritesCommentsListRead` 는
`integrationTest`/`disabledWithoutDocker=true` 경로로 **스킵**되었다(실행 아님).
AC-3(DB 격리 축)·AC-4·AC-6 의 IT 레벨 판정은 전적으로 CI 에 있다. 로컬로 실제 실행해 확인한
것은 unit + slice 뿐이다(`community-service:test`, `gateway-service:test` 모두
`BUILD SUCCESSFUL`, rc=0 — pipeline 이 아니라 각 명령을 단독 실행해 `$?` 로 직접 확인).

## Out of Scope 로 남긴 것 (티켓 선언대로)

- 프런트 배선 — `TASK-FAN-FE-032`.
- 댓글 수정(edit) — 계약에 없음.
- AMI 재굽기 — 데모 라이브는 다음 재굽기 창까지 이 경로를 못 돌려준다.

---

# Related Specs

- `projects/fan-platform/specs/services/community-service/architecture.md`
- `projects/fan-platform/specs/contracts/http/community-api.md` § Comments (**변경 대상**)
- `projects/fan-platform/tasks/review/TASK-FAN-FE-030-no-comment-composer-ui-product-gap-decision.md`
  (이 티켓을 분기시킨 결정 티켓)
- `projects/fan-platform/tasks/ready/TASK-FAN-FE-032-post-detail-comment-list-compose-delete-ui.md`
  (이 티켓의 후속 소비자)
- `projects/fan-platform/tasks/done/TASK-FAN-BE-049-community-has-no-way-to-ask-whether-i-follow-this-artist.md` ·
  `projects/fan-platform/tasks/review/TASK-FAN-BE-051-community-has-no-way-to-ask-my-own-reaction-to-a-post.md`
  (같은 클래스의 선행 수정 — 구조를 복사했다)

# Related Contracts

- `projects/fan-platform/specs/contracts/http/community-api.md` — **이 티켓이 여는 것**

---

# Edge Cases

- **삭제된 댓글** — `deletedAt IS NOT NULL` 은 목록·카운트 양쪽에서 제외. 삭제 후 재조회해도
  총원이 줄어든 채로 보여야 한다.
- **가시성이 거부되는 글** — `PostAccessGuard.requirePublishedAccess` 가 이미 던지므로, 댓글
  리포지토리는 호출되지 않는다(새 가시성 분기를 만들지 않는다).
- **테넌트** — 댓글 행은 `tenant_id` 를 갖는다. 다른 테넌트의 같은 `postId` 문자열에 달린
  댓글이 새면 안 된다.
- **page/size 경계** — `mine`/`feed` 와 같은 clamp(`page≥0`, `1≤size≤50`).

# Failure Scenarios

- **가시성 판단을 새로 derive 한다** → `POST`와 `GET`이 서로 다른 조건으로 갈라질 수 있다
  (예: 한쪽만 작성자 우회를 까먹음). `PostAccessGuard` 재사용이 이 티켓이 막으려는 것이다.
- **삭제 제외를 깨먹는다** → 삭제된 댓글이 다시 나타나거나, `totalElements` 가 `commentCount`
  와 어긋난다.
- **계약을 나중에 쓴다** → HARDSTOP-08.
- **게이트웨이 확인을 생략한다** → 서비스 단위 테스트는 초록인데 브라우저에서 404.
  `TASK-FAN-FE-032` 가 그 공백을 "댓글 없음"으로 그리면 원래 결함과 구별되지 않는다.
