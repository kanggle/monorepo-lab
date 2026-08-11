# Task ID

TASK-BE-579

# Title

`V0032` 가 `community-service-client` 에 준 `artist.read` 를 **SAS 가 실제로 발급하는지 아무도 확인하지 않는다** — 소비자 쪽만 초록인 이음매

# Status

ready

# Owner

iam-platform

# Task Tags

- backend
- security
- oauth2

---

# 배경

`TASK-FAN-BE-045`(fan-platform, PR #3270 머지)가 community-service → artist-service
동기 검증을 넣으면서, 그 호출에 필요한 machine 스코프를 이 프로젝트의
[`V0032__grant_community_client_artist_read_scope.md`](../../apps/auth-service/src/main/resources/db/migration/V0032__grant_community_client_artist_read_scope.sql)
로 부여했다. **iam 쪽 티켓 없이** 크로스프로젝트 원자 PR 로 들어갔다 — 스코프가 없으면
SAS 가 `invalid_scope` 로 토큰을 거절하고 fan 의 checker 가 fail-closed 로 **모든 팔로우를
거절**하므로 나눠 낼 수 없었다(CLAUDE.md § Cross-Project Changes). 이 티켓이 그 사후 기록이자,
그때 **증명되지 않은 채로 남은 것**을 닫는다.

## 🔴 무엇이 증명되지 않았나 — 잰 것과 재지 않은 것

`FAN-BE-045` 의 CI 초록이 증명한 것은 **소비자 쪽**뿐이다(실측):

| 테스트 | 토큰을 어디서 얻나 | 그래서 증명하는 것 |
|---|---|---|
| `InternalArtistAuthIntegrationTest` (fan) | `JwtTestHelper` 가 **직접 서명** | 스코프가 있으면 통과·없으면 403 — **artist-service 의 판정** |
| `FollowArtistGateIntegrationTest` (fan) | MockWebServer 가 토큰 엔드포인트를 **스텁** | 어댑터의 fail-closed 동작 |

⇒ **"SAS 가 `community-service-client` 에게 `artist.read` 를 실제로 발급한다"** 를 확인하는
테스트는 **양쪽 프로젝트 어디에도 0건**이다. 두 테스트 모두 그 사실을 *가정*하고 자기가 만든
토큰으로 뒷단만 잰다.

🔵 계측기 검증: 같은 저장소에 **시드된 클라이언트를 실제 토큰 엔드포인트로 검증하는 선례가
있다** — `PlatformConsoleOidcClientSeedIntegrationTest`(TASK-BE-296, Flyway `V0015` 시드를
`RegisteredClientRepository` + 실제 엔드포인트로 확인). 즉 "그런 테스트를 못 쓴다" 가 아니라
**이번에 안 썼다**. ~~반면 `OAuth2AuthorizationServerIntegrationTest` 의
`client_credentials` 케이스는 `AuthorizationServerConfig` 의 **인메모리 placeholder**
(`test-internal-client`)를 쓰므로 시드 행을 전혀 건드리지 않는다.~~

🔴 **취소선 문장은 틀렸다 (AC-0 재측정, 2026-08-12).** 그 클래스의 **케이스 7·8**
(`TASK-BE-317`/`TASK-BE-515`)은 **`account-service-client`** 를 쓰고, 그것은 `V0019` 가
**Flyway 로 시드**한 행이다. 실제 토큰 엔드포인트 + JWKS 검증 + **scope 양성/음성**까지
이미 있고 `scopesOf()` 헬퍼까지 있다.

**내가 어디서 틀렸나**: `test-internal-client` 는 그 클래스의 **클래스 javadoc** 에 적힌
말이고, 그 javadoc 은 여전히 **Phase 1(`TASK-BE-251`) 시절**을 서술한다. 나는 **케이스를
읽지 않고 javadoc 을 읽었다** — 그리고 그 한 문장에서 *"이 클래스는 시드 행을 안 건드린다"* 를
넘어 *"발급자 쪽 선례가 어디에도 없다"* 까지 일반화했다.
[[feedback_my_own_ticket_cited_a_spec_that_says_otherwise]]

🔵 **그래도 이 티켓은 살아 있다 — 다만 모양이 바뀐다.** 어떤 테스트도
`community-service-client` 나 `artist.read` 를 건드리지 않는다(전수: `artist.read` 는 21개
파일에 나오는데 **iam 테스트 코드는 0건**). ⇒ 할 일은 **새 하네스 구축이 아니라 그 클래스에
케이스 4개 추가**다(기존 헬퍼 재사용 · 컨텍스트/Redis 컨테이너 재사용 — iam 통합 레인은
벽시계 때문에 샤딩돼 있다, `TASK-MONO-438`).

## 왜 이것이 실제 위험인가

스코프 부여가 실패하는 방식은 조용하다. `JSON_ARRAY_APPEND` 가 안 먹거나(컬럼 모양 변화),
`oauth_scopes` 카탈로그 행이 없어 SAS 가 거절하거나, `is_system`/`tenant_id` 조합이 조회에
안 걸리거나 — 어느 경우든 증상은 **런타임에 모든 팔로우가 422** 이고, 그 422 는 fail-closed
설계상 *정상적인 도메인 거절과 구별되지 않는다*. 즉 **동작하는 보안 통제와 똑같이 보인다**.

---

# Goal

`community-service-client` 가 `client_credentials` 로 `artist.read` 를 요청하면 SAS 가
**실제로** 그 스코프를 담은 토큰을 발급한다는 것을, 시드 마이그레이션을 실행한 상태에서
테스트로 고정한다.

---

# Scope

## In Scope

- `V0032` 시드 결과 검증: `oauth_scopes` 카탈로그 행 + `oauth_clients.scopes` 에 값이 실렸는지
- 실제 `POST /oauth2/token` (`grant_type=client_credentials`, `scope=artist.read`) → 200 +
  발급 토큰의 `scope` 클레임에 `artist.read` 포함
- 🔴 **음성 대조**: 이 클라이언트가 보유하지 않은 스코프를 요청하면 `invalid_scope` 로 거절되는 것
  (양성만 있으면 "무엇이든 발급한다" 와 구별되지 않는다)

## Out of Scope

- artist-service 의 `/internal/**` 판정 — 이미 `FAN-BE-045` 가 덮는다
- `fan-platform.artist.read`(엔드유저 스코프)의 발급 경로 — `V0030`/`TASK-BE-570` 소관
- 다른 서비스 클라이언트의 스코프 감사 — 하고 싶다면 별도 티켓(모집단을 새로 세야 한다)

---

# Acceptance Criteria

- [x] **AC-0 (실측) — 완료 2026-08-12.** fan 양쪽은 그대로다: `InternalArtistAuthIntegrationTest`
      = `JwtTestHelper` 자체 서명 · `FollowArtistGateIntegrationTest` = MockWebServer 가
      `/oauth2/token` 을 스텁해 `"stub-workload-token"` 문자열을 돌려준다.
      iam 쪽 `artist.read` 테스트도 **여전히 0건**(전수: 21개 파일에 등장하나 iam 테스트 코드 0).
      ⇒ **중복 아님, 착수 유효.** 🔴 단, **이 티켓 § 배경의 한 문장이 틀렸다** — 상세는 위 § 정정
- [x] **AC-1 (양성) — 완료.** `OAuth2AuthorizationServerIntegrationTest` @Order(9):
      시드된 `community-service-client:secret` → 실제 `POST /oauth2/token` → **JWKS 로 검증한**
      토큰의 `scope` 에 `artist.read` 적재 확인. `sub == client_id`,
      `tenant_id == fan-platform` 도 함께 단언한다 — 다른 클라이언트가 우연히 그 스코프를
      들고 있어도 통과해 버리는 것을 막는다.
      🔵 인메모리 placeholder 가 아니다. 선례는 `PlatformConsoleOidcClientSeedIntegrationTest`
      이지만, **같은 클래스의 케이스 7·8이 더 가까운 선례였다**(§ 정정)
      + @Order(12) 가 **시드 행 자체**를 본다: `oauth_scopes` 카탈로그 행을
      `(name, tenant_id IS NULL, is_system)` **조합으로** 단언(Edge Case 가 지목한 축) ·
      `oauth_clients.scopes` 내용 · `V0032` 의 `JSON_SEARCH` 멱등 가드 재실행이 no-op 인지
      (Flyway 는 재실행하지 않으므로 그 가드는 **다른 무엇도 검증하지 않는다**)
- [x] **AC-2 (음성 대조) — 완료.** @Order(10): 이 클라이언트가 갖지 않은 `internal.invoke`
      요청 → **400 `invalid_scope`**. 🔵 없는 문자열이 아니라 **실재하는**(단 `account-service-client`
      소유) 스코프를 골랐다 — 무의미한 문자열이면 카탈로그가 거절하므로 *클라이언트별 부여*를
      재는 게 아니게 된다
- [x] **AC-3 (계약 정합) — 완료, 수정 없음.** `auth-api.md` § OAuth2 Clients 의
      `community-service-client` 행은 이미
      `fan-platform` / `client_credentials` / `account.read, membership.read, artist.read` /
      `V0009 (+V0032 artist.read)` 로 **실제 시드 결과와 일치**한다. ⇒ 계약을 고치지 않고
      **테스트가 그 행을 지키게** 했다(@Order(9) 의 `tenant_id` 단언 + @Order(11) 의 세 스코프)
- [x] **Failure Scenario 3 (스코프 보존) — 완료.** @Order(11): `account.read` ·
      `membership.read` · `artist.read` 를 함께 요청해 셋 다 실린 것을 본다.
      🔴 `JSON_ARRAY_APPEND` 가 배열을 덮어쓰는 형태였다면 **멤버십 게이트가 fail-closed 로
      닫혀 프리미엄 피드가 전면 차단**되는데, `artist.read` 만 보면 그게 안 보인다

---

# Related Specs

- `projects/iam-platform/specs/contracts/http/auth-api.md` § OAuth2 Clients
- `projects/iam-platform/apps/auth-service/src/main/resources/db/migration/V0032__grant_community_client_artist_read_scope.sql`
- `projects/iam-platform/apps/auth-service/src/test/java/com/example/auth/integration/PlatformConsoleOidcClientSeedIntegrationTest.java` (선례)
- `projects/fan-platform/apps/community-service/.../infrastructure/artist/ArtistAccountCheckerConfig.java` (소비자)

# Related Contracts

- `auth-api.md` — OAuth2 Clients 표. 이 티켓은 표를 **바꾸지 않고 검증**한다(불일치 발견 시에만 수정)

# Edge Cases

- `artist.read` 는 `tenant_id = NULL` · `is_system = TRUE` 로 넣었다(machine 계열
  `account.read`/`membership.read` 와 같은 모양). 조회가 tenant 로 필터링된다면 이 조합이
  안 걸릴 수 있다 — 그것이 정확히 이 티켓이 잡아야 할 실패다
- `V0032` 는 `JSON_ARRAY_APPEND` + `JSON_SEARCH` 멱등 가드 — 재실행이 no-op 이어야 한다
- H2 백엔드 슬라이스 테스트는 Flyway 를 끄므로(`spring.flyway.enabled=false`) 이 검증은
  **Testcontainers MySQL 레인**에 있어야 한다

# Failure Scenarios

- 🔴 **양성만 단언한다** — "토큰이 나왔다" 는 스코프 없이도 나올 수 있다. `scope` 클레임을
  직접 읽고, 없는 스코프 요청이 거절되는 것까지 봐야 한다
- 🔴 **인메모리 placeholder 로 테스트한다** — `test-internal-client` 로 초록을 만들면
  시드 마이그레이션은 여전히 미검증이고, 이 티켓은 아무것도 하지 않은 것이 된다
- 🔴 **`artist.read` 만 보고 `account.read`/`membership.read` 가 사라졌는지 안 본다** —
  `JSON_ARRAY_APPEND` 가 배열을 덮어쓰는 형태로 잘못 나가면 기존 두 스코프가 날아가고,
  그러면 **멤버십 게이트가 fail-closed 로 닫힌다**(프리미엄 피드 전면 차단). 기존 값 보존도 단언할 것

# Definition of Done

- [x] AC-0 실측 기록 (+ 이 티켓 전제 1건 정정)
- [x] 시드 클라이언트 토큰 발급 테스트(양성 + 음성 대조 + 기존 스코프 보존 + 시드 행/멱등)
- [x] CI green (Testcontainers 레인에서 **실제 실행**됨을 확인 — SKIPPED 아님)
- [ ] Ready for review

---

# 🧪 "실제 실행됐다" 를 어떻게 증명했나 — 초록 런으로는 못 한다

DoD 의 이 줄이 **초록만으로는 만족될 수 없다**는 것을 어제 `TASK-MONO-512` 가 실측했다:
통합 리포트 아티팩트는 **실패 시에만** 업로드된다(초록 런 `31501902466` 의 유일한 아티팩트는
`fan-platform-boot-jars`). ⇒ 초록 런에서는 *"돌았다"* 와 *"조용히 SKIPPED 됐다"* 가 구별되지 않고,
Gradle 은 초록 테스트의 이름을 로그에 찍지도 않는다.

그래서 **단언 하나를 뒤집어 레인을 한 번만 RED 로 만들고**(커밋 `d9b7f142a`, 다음 커밋에서 회수)
업로드된 리포트를 받았다. `Integration (iam B, Testcontainers)` / run `31532438336`:

```
TEST-…OAuth2AuthorizationServerIntegrationTest.xml
tests="12" skipped="0" failures="1" errors="0"

0.100s  TASK-BE-579 AC-1: SAS actually issues `artist.read` to the SEEDED community-service-client   ← 주입한 실패
0.083s  TASK-BE-579 AC-2 (negative control): a scope this client does NOT hold → invalid_scope        PASS
0.117s  TASK-BE-579: V0032 APPENDED — account.read / membership.read survived the JSON_ARRAY_APPEND   PASS
0.008s  TASK-BE-579 AC-1: the V0032 seed rows themselves — catalog entry + client grant, 멱등          PASS
```

🔵 **한 번의 RED 가 두 가지를 동시에 증명한다**: ① `skipped="0"` + 네 케이스의 **실행 시간** ⇒
Testcontainers MySQL 을 상대로 실제로 돌았다(SKIPPED 아님) ② 주입한 곳만 실패하고 **나머지 셋은
통과** ⇒ `V0032` 의 부여가 실제로 동작한다(음성 대조·스코프 보존·시드 행 전부 참).
🔴 이것이 이 티켓이 고치려던 결함의 거울상이다 — *"초록이니까 됐다"* 를 한 겹 더 캐물은 것.

---

분석=Opus 5 / 구현 권장=**Sonnet** — 선례 테스트가 있고 단언 목록이 명확한 검증 작업이다.
