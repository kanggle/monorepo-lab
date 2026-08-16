# Task ID

TASK-FAN-BE-049

# Title

community 에 **"내가 이 아티스트를 팔로우하는가"** 를 물을 방법이 없다 — 쓰기(`POST`/`DELETE`)만 있고 읽기가 계약에도 코드에도 없어서, 화면이 팔로우 상태를 그릴 수 없다

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

`TASK-FAN-FE-017`(아티스트 상세의 팔로우 버튼이 항상 "팔로우" 로 보인다) 의 **선행
티켓**이다. FE-017 을 착수하자마자 **HARDSTOP-08** 이 걸렸다 — 그 티켓은 프런트가 서버에서
팔로우 여부를 읽어 오게 하는 일인데, **읽을 곳이 없다.**

## 실측 (2026-08-16)

| 축 | 결과 |
|---|---|
| `specs/contracts/http/community-api.md` § Follows | `POST /api/community/follows` · `DELETE /api/community/follows/{artistAccountId}` — **끝** |
| community 의 다른 `GET` | `posts/{id}` · `posts/mine` · `feed` — 팔로우 상태를 답하는 것 없음 |
| `artist-api.md` 의 `GET /api/artists/{id}` | 팔로우 플래그 **없음** |
| `FollowController` (코드) | `@PostMapping` + `@DeleteMapping("/{artistAccountId}")` — **`@GetMapping` 없음** |

⇒ **계약과 코드가 일치한다.** 드리프트가 아니라 **진짜 공백**이다. 그래서 이 티켓은
"문서를 코드에 맞추는" 일이 아니라 **읽기 경로를 새로 여는** 일이고, 계약이 먼저다.

## 🔴 왜 우회가 안 되는가 — 있는 것으로 때우면 **빈 경우에 정확히 틀린다**

`GET /api/community/feed` 는 팔로우한 아티스트의 **글**을 준다. 이것으로 팔로우 여부를
추론하고 싶어지는데, 그 술어는 **글이 없는 아티스트**에서 무너진다:

| 실제 상태 | feed 에 나타나는가 |
|---|---|
| 팔로우 중 · 글 있음 | 예 |
| **팔로우 중 · 글 없음** | **아니오** |
| 팔로우 안 함 | 아니오 |

**아래 두 칸이 구별되지 않는다** ⇒ 대리지표로 부재를 판정하는 전형적 오류다. 게다가 이
화면(아티스트 상세)이 정확히 *"아직 글이 없는 아티스트를 발견해서 팔로우한다"* 는
경로에 놓여 있어, **틀리는 경우가 예외가 아니라 이 화면의 주 용도**다.

---

# Goal

community 가 **호출자 자신의** 팔로우 상태를 답하는 읽기 경로를 갖는다 — 계약 먼저, 그다음
구현.

---

# Scope

1. **계약** — `specs/contracts/http/community-api.md` § Follows 에 읽기 경로를 명세한다.
   응답 형태·인증·에러·페이지네이션(목록형인 경우)을 쓰기 두 경로와 같은 수준으로 적는다.
2. **community-service** — `FollowController` 에 그 경로를 구현. 조회는 **호출자 자신의
   팔로우만** 답한다(남의 팔로우 목록을 여는 것은 이 티켓이 하는 일이 아니다).
3. **테스트** — 컨트롤러 슬라이스 + 통합. 아래 AC-3 의 대조군이 판정이다.

## 🔵 모양 선택은 구현자가 하되, 근거를 계약에 적는다

두 후보가 있고 **둘 다 정당하다** — 이 티켓은 답을 강요하지 않는다:

| 후보 | 장점 | 대가 |
|---|---|---|
| **(A)** `GET /api/community/follows/{artistAccountId}` → 200/404 (또는 `{following: bool}`) | 상세 화면 1회 호출로 끝. 캐시 단순 | 디렉터리에서 N개를 그리려면 N회 |
| **(B)** `GET /api/community/follows?page=&size=` (내 팔로우 목록) | 디렉터리·마이페이지가 함께 쓴다 | 상세 한 화면 때문에 목록 전체를 끌어온다(팔로우가 늘면 비용) |

🔴 **(A) 를 404 로 설계할 때 주의**: 이 저장소의 기존 축은 *"없음 = 404"* 인데
(`DELETE` 가 `NOT_FOLLOWING` 404), **읽기에서 404 는 "그런 아티스트가 없다" 와 "팔로우하지
않았다" 를 섞는다.** 섞이면 프런트가 두 상황을 구별하지 못하고, 존재하지 않는 아티스트를
"팔로우 안 함" 으로 그리게 된다. 200 + `{following: false}` 가 그 모호함이 없다.

## Out of Scope

- 프런트 배선 — `TASK-FAN-FE-017` 소관이고, **이 티켓이 머지된 뒤**에 착수한다.
- 남의 팔로워/팔로잉 목록 공개, 팔로워 수 표시 — 새 제품 결정이다.
- 팔로우/언팔로우 **동작** 변경. 지금 정상 작동한다(2026-08-16 브라우저 실측).

---

# Acceptance Criteria

- [x] **AC-0 (전제 재확인)** — 착수 시 `community-api.md` § Follows 에 읽기 경로가 **여전히
      없고** `FollowController` 에 `@GetMapping` 이 **없는지** 확인한다. 생겼다면 **STOP** —
      이 티켓의 전제가 사라진 것이고, 그때 남는 일은 FE-017 뿐이다.
- [x] **AC-1 (계약 먼저)** — `community-api.md` § Follows 에 읽기 경로가 명세되고, **선택한
      모양(A/B)과 그 이유**가 적힌다. 🔴 (A) 를 골랐다면 *부재를 404 로 표현하지 않은/한
      이유*를 명시한다 — 위 표의 모호함이 이 계약에서 어떻게 처리되는지가 남아야 한다.
      **계약 변경이 구현 커밋보다 앞서거나 같은 PR 안에 있어야 한다**(CLAUDE.md § Layer Rules).
- [x] **AC-2** — `FollowController` 가 그 경로를 구현하고, 응답이 계약의 형태와 **글자 그대로**
      일치한다(필드명 포함).
- [x] **AC-3 (대조군 — 이것이 판정이다)** — 통합 테스트가 **같은 호출자**로 두 칸을 잰다:

      | 상태 | 기대 |
      |---|---|
      | 팔로우한 아티스트 | following = **true** |
      | 팔로우하지 않은 아티스트 | following = **false** |

      🔴 **한 칸만으로는 통과가 무의미하다** — 항상 `false` 를 내는 구현도, 항상 `true` 를
      내는 구현도 한 칸씩은 맞힌다. **두 칸이 갈라져야** 잰 것이다.
- [x] **AC-4 (격리)** — **다른 팬**의 팔로우가 내 답에 새지 않는다. 팬 A 가 아티스트를
      팔로우한 상태에서 **팬 B** 로 물으면 `false` 여야 한다. 🔴 이 축이 빠지면 "테넌트
      전체의 팔로우 존재 여부" 를 답하는 구현이 AC-3 을 통과한다.
- [x] **AC-5** — 인증 없는 호출은 401. (조회 대상이 **호출자 자신**이므로 익명 답변이 성립하지
      않는다.)
- [x] **AC-6** — 게이트웨이 라우트가 이 경로를 실제로 통과시킨다. 🔴 쓰기 두 경로가 이미
      라우팅된다는 것이 **읽기도 된다는 뜻이 아니다** — 메서드/경로 단위 라우트 규칙이면
      갈린다. 판정은 **게이트웨이를 통한 호출**로 한다.

---

# Related Specs

- `projects/fan-platform/specs/services/community-service/architecture.md`
- `projects/fan-platform/specs/contracts/http/community-api.md` § Follows (**변경 대상**)

# Related Contracts

- `projects/fan-platform/specs/contracts/http/community-api.md` — **이 티켓이 여는 것**
- `projects/fan-platform/specs/contracts/http/artist-api.md` — `artists.account_id` 가
  팔로우의 키라는 근거(`TASK-FAN-BE-045` · `ADR-004`)

---

# Edge Cases

- 🔴 **축을 틀려도 데모는 초록이다** — 팔로우가 검증하는 것은 `artists.account_id` 이고
  아티스트 상세 라우트의 키는 `artists.id` 인데, **데모 백필 행에서는 두 값이 우연히
  일치한다**(`TASK-FAN-BE-045` 가 코드 주석으로 남긴 함정). 조회도 **`account_id` 축**을
  써야 하고, 테스트 픽스처는 **두 값이 다른** 행을 최소 하나 포함해야 한다 — 그렇지 않으면
  이 결함을 만들 수 없는 픽스처 위에서 영원히 초록이다.
- **존재하지 않는 아티스트** — Scope 의 (A)/(B) 선택에 따라 답이 달라진다. 어느 쪽이든
  계약이 그 경우를 **명시**해야 한다(AC-1).
- **자기 자신** — 아티스트 계정이 자기를 조회하는 경우. 팔로우 자체가 `SELF_FOLLOW_FORBIDDEN`
  이므로 답은 항상 `false` 이지만, 그것이 **의도된 답인지 미정의인지**를 계약이 말해야 한다.
- **테넌트** — 팔로우 행은 `tenant_id` 를 갖는다. 조회도 호출자의 테넌트로 좁혀야 한다.

# Failure Scenarios

- **feed 로 때운다** → 위 표의 *"팔로우 중 · 글 없음"* 칸에서 틀리고, 그 칸이 이 화면의
  **주 용도**다. 게다가 틀린 방향이 현재 결함과 **똑같이 보여서**(항상 "팔로우") 고쳤다고
  믿게 된다.
- **한 칸만 테스트한다** → 상수를 내는 구현이 통과한다(AC-3).
- **격리 축을 빼먹는다** → 테넌트 전역 존재 여부를 답하는 구현이 통과하고, 남의 팔로우가
  내 화면에 반영된다(AC-4).
- **계약을 나중에 쓴다** → HARDSTOP-08 이 이 티켓을 만든 바로 그 이유다. 구현이 먼저 가면
  이 공백이 **문서화되지 않은 사실상의 API** 가 되어 다음 소비자가 추측으로 붙는다.
- **게이트웨이 확인을 생략한다** → 서비스 단위 테스트는 초록인데 브라우저에서 404 다.
  `TASK-FAN-FE-017` 이 그 404 를 "팔로우 안 함" 으로 그리면 원래 결함과 구별되지 않는다.

---

# ✅ 실행 결과 (2026-08-17)

## 채택한 모양 — (A) 단건 조회, 200 + 불리언, **404 없음**

`GET /api/community/follows/{artistAccountId}` → `200 {data:{artistAccountId, following}}`.

404 를 쓰지 않은 이유를 계약에 표로 적었다. 형제 `DELETE` 가 같은 조건에 404
`NOT_FOLLOWING` 을 쓰므로 404 가 **국소적으로 일관된 선택**인데, 읽기에서는 그것이
*"팔로우하지 않았다"* 와 *"그런 아티스트가 없다"* 를 겹치게 만든다. 첫 소비자
(`TASK-FAN-FE-017`)가 바로 그 응답으로 버튼을 그리므로 모호함이 **UI 에 착지**한다.

**존재 검증은 하지 않는다** — 쓰기와 의도적으로 비대칭이고, 그 이유 둘을 계약에 적었다:
①`POST` 의 fail-closed(`ADR-004`)를 읽기가 물려받으면 artist-service 장애가 **모든
아티스트 페이지의 팔로우 버튼을 지운다**(쓰기는 틀린 대상이 피드 조인을 망가뜨리지만
읽기는 자기 행을 볼 뿐이다) ②검증하지 않는 편이 이 엔드포인트가 **존재 오라클**이 되는
것을 막는다 — `ADR-004` 가 § Drivers 를 들여 피하는 바로 그것이다.

## AC 판정 — 전부 실측

| AC | 판정 | 실측 |
|---|---|---|
| AC-0 | ✅ | 계약 § Follows 에 `GET` 없음(`POST`/`DELETE` 뿐) · `FollowController` 에 `@GetMapping` **0건** |
| AC-1 | ✅ | 계약 먼저 갱신 — 모양·404 미사용 근거·존재 미검증 근거를 본문에 기재 |
| AC-2 | ✅ | `FollowController.isFollowing` + `IsFollowingArtistUseCase` + `FollowStatusResponse`. 응답 필드명이 계약과 일치 |
| AC-3 | ✅ | 통합 `followedVsNotFollowed_answersDiffer` — 같은 팬, 팔로우한 대상 `true` ↔ 안 한 대상 `false`, **두 칸이 다름을 단언** |
| AC-4 | ✅ | `anotherFansFollowDoesNotLeakIntoMine` — 같은 아티스트, 팔로우한 팬 `true` ↔ 제3의 팬 `false`. 추가로 `followRowInAnotherTenantIsNotVisible`(테넌트 축) |
| AC-5 | ✅ | 슬라이스 + 통합 양쪽에서 무인증 → **401** |
| AC-6 | ✅ | `GatewayRouteRewriteTest.communityRouteRewritesFollowStatusRead` — 게이트웨이를 통과한 호출이 **GET** 으로, `/api/community/follows/{id}` 로 도착 |

실행 수치(전부 XML 아티팩트에서 읽음, `BUILD SUCCESSFUL` 로 판정하지 않았다):

| 스위트 | tests | failures | errors | **skipped** |
|---|---|---|---|---|
| `FollowControllerSliceTest` | 7 | 0 | 0 | **0** |
| `FollowStatusReadIntegrationTest` | 6 | 0 | 0 | **0** |
| community `integrationTest` 전체 | **44** | 0 | 0 | **0** |
| `GatewayRouteRewriteTest` | **10** | 0 | 0 | **0** |
| `community:check` + `gateway:check` | — | rc=0 | — | — |

## 🔴 범위 밖에서 결함 둘을 밟았다 — AC-6 이 그것을 밟게 만들었다

AC-6 은 *"쓰기가 라우팅된다는 것이 읽기도 된다는 뜻이 아니다"* 라고만 적혀 있었는데,
그것을 실제로 재려다 **훨씬 큰 것 둘**이 나왔다.

### (1) 게이트웨이 통합 스위트 **전체가 죽어 있었다**

`GatewayRouteRewriteTest` 를 처음 돌리자 **10개 전부 `initializationError`** —
`NullPointerException: … "jwks" is null`. **내 변경 탓인지부터 갈랐다**: 내 케이스를
stash 로 걷어내고 **baseline 을 돌려 동일한 실패**를 확인했다 ⇒ 선재 결함.

원인은 순서다. `@DynamicPropertySource` 의 supplier 는 **Spring 컨텍스트를 만들 때**
평가되는데, `@TestInstance(PER_CLASS)` 에서는 테스트 인스턴스 생성(=컨텍스트 로드)이
`@BeforeAll` **보다 먼저** 일어난다. `jwks`/`downstream` 은 그 `@BeforeAll` 에서야
만들어지므로 supplier 가 볼 때는 언제나 null 이다.

고침 = 공유 인프라를 **static 초기화 블록**으로 옮겼다(클래스 로드 시점이라 순서 위험이
*조정*되는 게 아니라 **사라진다**). `@AfterAll` 의 managed stop 은 제거했다 — 첫 서브클래스
뒤 컨테이너를 내리면서 Spring 컨텍스트 캐시는 다음 서브클래스에 죽은 연결을 넘기는,
이 저장소가 batch-worker 에서 이미 겪은 패턴이다. 정리는 Ryuk/JVM 종료에 맡긴다.

⇒ **선재 테스트 9개가 함께 되살아났다.** 이 서비스의 라우트 계약 전체가 그 스위트다.

### (2) 그것이 보이지 않았던 이유 — **CI 레인이 없다**

`gateway-service:check` 는 CI 에 있지만 `check` 는 설계상 `integration` 태그를
**제외**하고(Docker-free 유지), 팬 통합 워크플로는 community·artist·membership·
notification **넷만** 나열한다 ⇒ `gateway-service:integrationTest` 는 **어디서도 돌지
않는다.** 안 도는 스위트가 썩은 것이고, 썩은 줄 아무도 몰랐다.

🔴 이 수정만으로는 **재발을 막지 못한다** — 지금 되살린 10개도 여전히 CI 밖이다.
레인 추가는 `.github/workflows/ci.yml`(공유 경로) 변경이라 루트 태스크
**`TASK-MONO-541`** 로 분리했다.

## 🔵 로컬 판정에 붙는 단서

- **`BUILD SUCCESSFUL` 을 판정으로 쓰지 않았다.** 실제로 한 번 그것에 속을 뻔했다 —
  게이트웨이 재실행이 `rc=0 · BUILD SUCCESSFUL` 인데 XML 은 **`skipped=10`** 이었다
  (npipe 가 그 회차에 Docker 를 못 찾아 `disabledWithoutDocker` 가 전부 스킵). 이후
  모든 수치는 XML 의 `tests/failures/errors/skipped` 네 값을 함께 읽었다.
- 로컬 Windows npipe 는 **flaky(~1 pass/3)** 이고 IT 검증 권위는 **CI Linux** 다.
  위 초록은 로컬 1회 관측이며, 권위 판정은 PR 의 `Integration (fan-platform,
  Testcontainers)` 레인이다. 🔴 단, **게이트웨이 스위트는 그 레인에 없으므로 이 PR 의
  CI 는 AC-6 을 재지 않는다** — AC-6 의 유일한 관측은 위 로컬 실행이고, 그 사실을
  숨기지 않는다.
