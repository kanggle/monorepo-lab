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
**이번에 안 썼다**. 반면 `OAuth2AuthorizationServerIntegrationTest` 의
`client_credentials` 케이스는 `AuthorizationServerConfig` 의 **인메모리 placeholder**
(`test-internal-client`)를 쓰므로 시드 행을 전혀 건드리지 않는다.

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

- [ ] **AC-0 (실측)** — 착수 시점에 위 표를 다시 확인한다: fan 양쪽 테스트가 여전히
      자체 서명/스텁 토큰을 쓰는지, iam 에 시드-클라이언트 토큰 발급 테스트가 여전히 0건인지.
      🔴 이미 누가 채웠다면 이 티켓은 **닫고 사유를 적는다**(중복 작성 금지)
- [ ] **AC-1 (양성)** — 시드된 `community-service-client` 로 `client_credentials` 토큰을
      실제로 발급받고, 그 토큰의 `scope` 에 `artist.read` 가 있음을 단언한다.
      🔴 인메모리 placeholder 클라이언트가 아니라 **Flyway 가 넣은 행**이어야 한다 —
      `PlatformConsoleOidcClientSeedIntegrationTest` 가 그 모양의 선례다
- [ ] **AC-2 (음성 대조)** — 이 클라이언트가 갖지 않은 스코프 요청 → `invalid_scope`.
      양성만으로는 "이 스코프를 준다" 와 "아무 스코프나 준다" 를 구별할 수 없다
- [ ] **AC-3 (계약 정합)** — `specs/contracts/http/auth-api.md` § OAuth2 Clients 의
      `community-service-client` 행이 실제 시드 결과와 일치함을 확인(불일치면 계약을 고친다)

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

- [ ] AC-0 실측 기록
- [ ] 시드 클라이언트 토큰 발급 테스트(양성 + 음성 대조 + 기존 스코프 보존)
- [ ] CI green (Testcontainers 레인에서 **실제 실행**됨을 로그로 확인 — SKIPPED 아님)
- [ ] Ready for review

---

분석=Opus 5 / 구현 권장=**Sonnet** — 선례 테스트가 있고 단언 목록이 명확한 검증 작업이다.
