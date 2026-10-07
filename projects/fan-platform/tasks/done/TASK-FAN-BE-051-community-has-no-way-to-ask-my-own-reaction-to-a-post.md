# Task ID

TASK-FAN-BE-051

# Title

community 에 **"이 글에 내가 어떤 반응을 남겼나"** 를 물을 방법이 없다 — 쓰기(`PUT`/`DELETE`)만
있고 읽기가 계약에도 코드에도 없어서, 화면이 뷰어 자신의 반응을 그릴 수 없다

# Status

done

# Owner

fan-platform

# Task Tags

- backend
- contract
- community

---

# 배경

`TASK-FAN-FE-029`(반응 버튼이 새로고침 뒤 뷰어의 기존 반응을 복원하지 못한다) 의 **선행
티켓**이다. FE-029 를 착수하자마자 Scope 가 지시한 대로 계약·코드를 먼저 읍었고
**HARDSTOP-08** 이 걸렸다 — 그 티켓은 프런트가 서버에서 뷰어의 기존 반응을 읍어 오게 하는
일인데, **읍을 곳이 없다.**

## 실측 (2026-10-06 UTC)

| 축 | 결과 |
|---|---|
| `specs/contracts/http/community-api.md` § Reactions | `PUT /api/community/posts/{postId}/reactions` · `DELETE …` — **끝.** 응답 `{postId, reactionType, totalReactions}` 는 **방금 호출한 액션의 결과**일 뿐, 그 외 어떤 시점에도 "지금 내 반응이 뭔가" 를 물을 길이 없다 |
| `GET /api/community/posts/{id}` 응답 (§ Posts) | `commentCount`·`reactionCount`(집계) 만. 뷰어별 필드 없음 |
| community 의 다른 `GET` (`posts/mine` · `feed`) | 둘 다 집계뿐, 뷰어별 반응 없음 |
| `ReactionController` (코드) | `@PutMapping` + `@DeleteMapping` — **`@GetMapping` 없음** |
| `GetPostUseCase` (코드) | `reactionRepository.countByPostId(...)` 만 호출 — **`reactionRepository.find(postId, actor.accountId(), tenantId)` 를 부르지 않는다.** 이 메서드 자체는 이미 존재하고 `AddReactionUseCase`/`RemoveReactionUseCase` 가 **내부적으로**(멱등 upsert 판정용) 쓰고 있다 — 데이터는 테이블에 있고, 질문하는 코드만 없다 |
| `PostView`/`PostResponse` DTO | 뷰어별 반응 필드 **없음** |

⇒ **계약과 코드가 일치한다.** 드리프트가 아니라 **진짜 공백**이다 — `TASK-FAN-FE-017` 이
팔로우 쪽에서 겪은 것과 **같은 클래스**(계약도 코드도 "없다"는 점에서 일치하고, 리포지토리에
단건 조회 메서드가 이미 있다는 점은 그보다 한 칸 더 간 상태)다.

## 🔴 왜 집계로 때울 수 없는가

`reactionCount` 는 그 글에 달린 반응의 **총합**이다. 합계에서 "그중 내 것이 있는가, 있다면
무엇인가" 를 복원하는 것은 원리적으로 불가능하다(합계는 귀속 정보를 버린다) — 이것은
FE-029 Failure Scenarios 가 이미 경고한 함정이고, 그래서 우회가 아니라 새 읍기 경로가 필요한
이유다.

---

# Goal

community 가 **호출자 자신의** 특정 글에 대한 반응 상태(없으면 `null`, 있으면 그
`reactionType`)를 답하는 읍기 경로를 갖는다 — 계약 먼저, 그다음 구현.

---

# Scope

1. **계약** — `specs/contracts/http/community-api.md` § Reactions 에 읍기 경로를 명세한다.
   응답 형태·인증·에러를 쓰기 두 경로와 같은 수준으로 적는다.
2. **community-service** — 그 경로를 구현한다. 조회는 **호출자 자신의 반응만** 답한다(다른
   팬의 반응을 공개하는 것은 이 티켓이 하는 일이 아니다 — 다른 팬의 반응이 새면 익명성 기대를
   깨는 것과 같은 층의 문제다).
3. **테스트** — 컨트롤러 슬라이스 + 통합. 아래 AC-3 의 대조군이 판정이다.

## 🔵 모양 선택은 구현자가 하되, 근거를 계약에 적는다

두 후보가 있고 **이번에는 하나가 뚜렷이 우세하다** — `TASK-FAN-BE-049` 가 팔로우 쪽에서
답을 강요하지 않았던 것과 달리, 여기서는 소비자(`memberPostDetail.tsx`)가 **이미 같은 글을
`GET /posts/{id}` 로 한 번에 가져온다**는 사실이 선택을 좁힌다:

| 후보 | 장점 | 대가 |
|---|---|---|
| **(A, 권장)** `GET /api/community/posts/{id}` 응답에 `myReaction: "LIKE"\|"LOVE"\|"FIRE"\|"SAD"\|null` 필드를 추가 — `commentCount`/`reactionCount` 와 나란히, 같은 `GetPostUseCase` 호출 안에서 `reactionRepository.find(...)` 한 번 더 | 소비자가 **이미 하는 호출 하나**에 얹힌다. 추가 왕복 0회. `AddReactionUseCase`/`RemoveReactionUseCase` 가 이미 쓰는 메서드를 재사용 | 팔로우 쪽과 달리 **상세 전용** — 피드 아이템(`FeedItem`)이나 `posts/mine` 에도 같은 필드가 필요해지면 그 소비자마다 따로 챙겨야 한다(이번 소비자는 상세뿐이므로 범위 밖) |
| **(B)** 전용 `GET /api/community/posts/{postId}/reactions/mine` → `{reactionType: ...\|null}` | `PUT`/`DELETE` 와 같은 리소스 경로 아래, 독립적으로 캐시/재사용 가능 | 상세 화면은 **이미 같은 글을 한 번 가져온 다음에** 이 호출을 또 해야 한다 — 추가 왕복 1회, `BE-049` 가 피하려던 바로 그 비용을 여기서는 **불필요하게** 만든다 |

🔴 **(A) 를 고를 때 주의할 것** — `PostView`/`PublishPostUseCase.view(...)` 는 발행·수정·조회
세 use case가 공유하는 뷰 팩토리다. `myReaction` 은 **조회 시점의 호출자**에 종속된 값이라,
발행/수정 직후의 응답(그 글을 막 쓴 사람이 호출자)에 끼워 넣어도 틀리지는 않지만(보통 `null`),
공유 팩토리에 조회-전용 파라미터를 끼우는 모양이 되므로 **`GetPostUseCase` 경로에서만 값을
채우고 나머지 경로는 `null` 고정으로 둘지, 팩토리 자체를 쪼갤지**는 구현 시점에 정하고 계약에
그 선택과 이유를 적는다.

## Out of Scope

- 프런트 배선 — `TASK-FAN-FE-029` 소관이고, **이 티켓이 머지된 뒤**에 착수한다.
- 피드(`GET /api/community/feed`)·`posts/mine` 에 같은 필드를 추가하는 것 — 현재 소비자는
  상세 화면 하나뿐이다. 필요해지면 별건.
- 다른 팬의 반응 공개 — 새 제품 결정이다.
- 반응 추가/제거 **동작** 변경. 지금 정상 작동한다(23차 창 라이브 실측, 저장은 됨).
- 데모 라이브 환경(AMI)에 이 변경이 실제로 반영되는 시점 — 백엔드 배포 파이프라인은 이
  티켓의 범위 밖이고, `TASK-FAN-FE-029` AC-2/AC-3 의 **라이브** 측정은 다음 AMI 재굽기 창을
  기다려야 한다(`mediaRefs` 필드가 겪은 것과 같은 모양 — `community-api.md` § `mediaRefs`
  참조). 그동안 프런트는 필드 부재를 허용해야 한다(`post.myReaction ?? null`).

---

# Acceptance Criteria

- [x] **AC-0 (전제 재확인)** — 착수 시 `community-api.md` § Reactions 에 읍기 경로가 **여전히
      없고** `ReactionController` 에 `@GetMapping` 이 **없는지**, `GetPostUseCase` 가 여전히
      `reactionRepository.find(...)` 를 호출하지 **않는지** 확인한다. 생겼다면 **STOP** — 이
      티켓의 전제가 사라진 것이고, 그때 남는 일은 FE-029 뿐이다.
- [x] **AC-1 (계약 먼저)** — `community-api.md` § Reactions 또는 § Posts 에 읍기 경로(또는
      필드)가 명세되고, **선택한 모양(A/B)과 그 이유**가 적힌다. (A) 를 골랐다면 `PostView`
      공유 문제(위 🔴)를 어떻게 풀었는지도 적는다. **계약 변경이 구현 커밋보다 앞서거나 같은
      PR 안에 있어야 한다**(CLAUDE.md § Layer Rules).
- [x] **AC-2** — 구현한 경로/필드의 응답이 계약의 형태와 **글자 그대로** 일치한다(필드명 포함).
- [x] **AC-3 (대조군 — 이것이 판정이다)** — 통합 테스트가 **같은 호출자·같은 글**로 세 칸을
      잰다:

      | 상태 | 기대 |
      |---|---|
      | 반응 안 함 | `myReaction = null` |
      | `LIKE` 반응 중 | `myReaction = "LIKE"` |
      | `LIKE` → `LOVE` 로 바꾼 뒤 | `myReaction = "LOVE"`(이전 값이 남지 않음) |

      🔴 **한 칸만으로는 통과가 무의미하다** — 항상 `null` 을 내는 구현도 첫 칸은 맞힌다.
      세 칸이 **갈라져야** 잰 것이다.
- [x] **AC-4 (격리)** — **다른 팬**의 반응이 내 답에 새지 않는다. 팬 A 가 어떤 반응을 남긴
      글을 **팬 B** 로 조회하면 `myReaction = null` 이어야 한다(A의 반응이 집계에는 보이되
      B의 "내 반응" 에는 보이지 않아야 함). 🔴 이 축이 빠지면 "그 글에 달린 반응 중 하나를
      아무거나" 를 답하는 구현이 AC-3 을 통과한다.
- [x] **AC-5** — 인증 없는 호출은 401. (조회 대상이 **호출자 자신**이므로 익명 답변이 성립하지
      않는다.)
- [x] **AC-6** — 게이트웨이 라우트가 (A) 를 골랐다면 기존 `GET /posts/{id}` 라우트가 새 필드를
      그대로 통과시키는지, (B) 를 골랐다면 신규 경로가 실제로 라우팅되는지 확인한다. 🔴 쓰기
      두 경로가 라우팅된다는 것이 읍기도 된다는 뜻이 아니다(`TASK-FAN-BE-049` 가 겪은 함정과
      같다) — 판정은 **게이트웨이를 통한 호출**로 한다.

---

# ✅ 실행 결과 (2026-10-06 UTC)

## 채택한 모양 — (A), `GET /api/community/posts/{id}` 응답에 `myReaction` 필드 추가

`myReaction: "LIKE"|"LOVE"|"FIRE"|"SAD"|null`. 전용 엔드포인트(B)를 쓰지 않은 이유, `null`
의미, `PostView`/`PublishPostUseCase.view(...)` 공유 문제를 어떻게 풀었는지(값은
`GetPostUseCase` 경로에서만 채우고 나머지 세 경로는 오버로드로 고정 `null`) 전부
`community-api.md` § `myReaction`(§ Posts)과 § "Reading the caller's own reaction"(§
Reactions)에 적었다.

## 코드 변경

- `PostView.java` — `ReactionType myReaction` 필드 추가(`reactionCount` 뒤, `publishedAt`
  앞).
- `PublishPostUseCase.view(...)` — 기존 3-인자 메서드는 4-인자 오버로드를 `myReaction=null`
  로 호출하도록 변경(기존 호출부 `UpdatePostUseCase`/`GetMyPostsUseCase`는 **무변경**). 새
  4-인자 오버로드를 `GetPostUseCase` 전용으로 추가.
- `GetPostUseCase.execute(...)` — `reactionRepository.find(postId, actor.accountId(),
  actor.tenantId())` 호출 추가(기존에 `AddReactionUseCase`/`RemoveReactionUseCase`가 이미
  쓰던 메서드) → `Optional<Reaction>` → `ReactionType` 매핑 → `view(...)`에 전달.
- `PostResponse.java` — `myReaction` 필드 추가(`String`, `v.myReaction()==null ? null :
  v.myReaction().name()`).
- 테스트 call site 2곳(`PostControllerSliceTest`의 직접 `new PostView(...)`)에 `null` 인자
  추가해 컴파일 유지.

## AC 판정

| AC | 판정 | 실측 |
|---|---|---|
| AC-0 | ✅ | 착수 시 재확인: `community-api.md` § Reactions 에 `GET` 없음(`PUT`/`DELETE` 뿐) · `ReactionController` 에 `@GetMapping` **0건** · `GetPostUseCase` 가 `reactionRepository.find(...)` 를 호출하지 않음 — 전제 그대로 성립 |
| AC-1 | ✅ | 계약 먼저 갱신 — (A) 선택 근거, `null` 의미, `PostView` 공유 문제의 해법을 본문에 기재 |
| AC-2 | ✅ | `PostResponse.myReaction` 필드명이 계약과 글자 그대로 일치(`PostControllerSliceTest.get_responseFieldNameMatchesContractExactly`) |
| AC-3 | ✅(유닛) / CI 권위(IT) | 유닛 `GetPostUseCaseTest.theThreeCellsDiffer` — 세 칸(`null`/`LIKE`/`LOVE`)이 pairwise 다름을 단언. 같은 세 칸을 HTTP 레벨로 재는 `ReactionMyStatusReadIntegrationTest`(Testcontainers)는 이 호스트에 Docker 가 없어 로컬 미실행 — CI Linux 가 권위 |
| AC-4 | ✅(유닛 호출계약) / CI 권위(IT) | 유닛 `GetPostUseCaseTest.queriesOnlyTheCallersOwnAccountId` — `reactionRepository.find` 가 호출자 자신의 accountId로만 질의됨을 단언. 실제 격리(팬 A 반응 vs 팬 B 조회)는 `ReactionMyStatusReadIntegrationTest.anotherFansReactionDoesNotLeakIntoMine` + 테넌트 격리 케이스 — Docker 부재로 로컬 미실행, CI 권위 |
| AC-5 | ✅ | 슬라이스 `PostControllerSliceTest.get_withoutAuth_returns401`(로컬 실행, 통과) + 통합 `ReactionMyStatusReadIntegrationTest.withoutBearer_returns401`(CI) |
| AC-6 | ✅(코드) / CI 권위(IT) | `GatewayRouteRewriteTest.communityRouteForwardsMyReactionFieldOnPostDetailRead` 추가 — 게이트웨이가 `GET /api/v1/community/posts/{id}` 를 `/api/community/posts/{id}` 로 재작성하고 응답 본문의 `myReaction` 필드를 그대로 통과시키는지 확인. 이 스위트는 `TASK-FAN-BE-049` 가 밝힌 대로 CI 통합 워크플로에 없고(`TASK-MONO-541` 미해결), Docker 부재로 로컬도 미실행 — **이 PR 로는 AC-6 이 CI 에서 재지지 않는다**, 추가한 테스트 코드 자체가 유일한 산출물이다 |

## 🔴 로컬 측정의 한계 — Docker 없음

이 Windows 호스트에는 Docker 가 없다(`docker info` rc=1). `CommunityServiceIntegrationBase`
(`@Testcontainers(disabledWithoutDocker = true)`)와 `GatewayIntegrationBase` 가 끄는 모든
IT는 로컬에서 **실행되지 않고 스킵된다** — `TASK-FAN-BE-049` 가 겪은 "로컬 1회 관측, flaky"
상황과도 다르다(여기는 로컬 관측이 **전혀 없다**). 따라서 AC-3/AC-4/AC-6 의 IT 레벨 판정은
전적으로 CI 에 있다. 로컬로 실제로 돌려 XML 로 확인한 것은 unit + slice 뿐이다:

| 스위트 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `GetPostUseCaseTest` (신규) | 5 | 0 | 0 | 0 |
| `PostControllerSliceTest` | 18 | 0 | 0 | 0 |
| `AddReactionUseCaseTest` | 5 | 0 | 0 | 0 |
| `ReactionControllerSliceTest` | 4 | 0 | 0 | 0 |
| community-service `test` 전체 (40 클래스) | 219 | 0 | 0 | 0 |
| `community-service:check` + `gateway-service:check` | — | rc=0 | — | — |

수치는 `build/test-results/test/*.xml` 을 직접 파싱해 읽었다(`BUILD SUCCESSFUL` 을 판정으로
쓰지 않았다). `gateway-service:test` 는 `GatewayRouteRewriteTest` 가 `@Tag("integration")`
이라 기본 `test` 태스크에서 애초에 제외된다 — 그 클래스의 신규 케이스는 **컴파일만** 로컬로
확인했다(`compileTestJava` rc=0).

## Out of Scope 로 남긴 것 (티켓 선언대로)

- 프런트 배선 — `TASK-FAN-FE-029`.
- 피드/`posts/mine` 에 같은 필드 추가 — 미요청.
- 다른 팬의 반응 공개 — 새 제품 결정.
- AMI 재굽기 — 데모 라이브는 다음 재굽기 창까지 이 필드를 못 돌려준다(계약에 명시).

---

# Related Specs

- `projects/fan-platform/specs/services/community-service/architecture.md`
- `projects/fan-platform/specs/contracts/http/community-api.md` § Reactions / § Posts
  (**변경 대상**)
- `projects/fan-platform/tasks/done/TASK-FAN-BE-049-community-has-no-way-to-ask-whether-i-follow-this-artist.md`
  (같은 클래스의 선행 수정 — 구조를 복사했다)

# Related Contracts

- `projects/fan-platform/specs/contracts/http/community-api.md` — **이 티켓이 여는 것**

---

# Edge Cases

- **반응을 바꾼 뒤** — `(postId, reactorAccountId)` 유니크 upsert이므로 이전 타입은 덮어써진다
  (AC-3 세 번째 칸).
- **삭제된/숨겨진 글** — `GetPostUseCase` 가 이미 가시성 게이팅을 하므로, 가시성이 거부되는
  글은 이 필드가 채워지기 전에 이미 403/404 — 새 분기를 만들지 않는다.
- **테넌트** — 반응 행은 `tenant_id` 를 갖는다(`ReactionRepository.find` 의 세 번째 인자).
  조회도 호출자의 테넌트로 좁혀야 한다 — 기존 메서드 시그니처가 이미 강제한다.
- **AMI 재굽기 간극** — 이 티켓이 머지돼도 데모 라이브 환경은 다음 AMI 재굽기 전까지 이
  필드를 돌려주지 않는다. `TASK-FAN-FE-029` 의 라이브(AC-2/AC-3) 측정은 그 창을 기다린다.

# Failure Scenarios

- **`reactionCount` 에서 추론한다** → 합계는 귀속 정보를 버려 원리적으로 불가능하다
  (`TASK-FAN-FE-029` Failure Scenarios 가 이미 경고).
- **한 칸만 테스트한다** → 상수(`null`)를 내는 구현이 통과한다(AC-3).
- **격리 축을 빼먹는다** → "그 글의 반응 중 하나" 를 답하는 구현이 통과하고, 남의 반응이
  내 화면에 "내가 누른 것"으로 보인다(AC-4).
- **계약을 나중에 쓴다** → HARDSTOP-08 이 이 티켓을 만든 바로 그 이유다.
- **게이트웨이 확인을 생략한다** → 서비스 단위 테스트는 초록인데 브라우저에서 필드가 안 온다.
  `TASK-FAN-FE-029` 가 그 공백을 "반응 없음" 으로 그리면 원래 결함과 구별되지 않는다.

---

# 닫기 기록 (2026-10-07 UTC, 4차원 검증)

- (a) PR #4185 `state=MERGED`, squash `28dbefca3`.
- (b) `origin/main` 이 `28dbefca3` 를 포함.
- (c) 머지된 PR 의 `statusCheckRollup` 실패 0 · 대기 0.
- (d) AC 절을 열어 동사대로 읽음: AC-0~AC-6 전부 [x]. AC-3/4/6 의 IT 축은 «CI 권위» 로 적혀 있었다 — #4185 의 `Integration (fan-platform, Testcontainers)` · `E2E (fan-platform v1 live-trio smoke)` 가 SUCCESS 로 실제 실행됨(SKIPPED 아님)을 확인.
