# Task ID

TASK-BE-609

# Status

done

# Title

비밀번호 변경 · 재설정이 **게이트웨이를 거치면 쓸 수 없다** — 재설정은 `public-paths` 누락(401), 변경은 사용자 Bearer 를 auth-service 가 내부 자격으로 거부(401)

# Owner

iam-platform

# Task Tags

- gateway-service
- auth-service
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 두 결함 모두 설정/체인 순서 한 곳이다. 단 ② 의 원인은 AC-0 에서 코드로 확정한다(아래는 라이브 재현까지).

---

# Goal

`TASK-BE-607` AC-3 창(2026-09-26 UTC, 16차 AMI `58d4920c4`)에서 발견. 서비스 자체는 정상이다(직접 호출: 변경 204 · 재설정 204 · 결과 상태 확인).
그러나 **사용자가 닿는 유일한 문**인 iam 게이트웨이(`iam-gateway-service`)를 거치면:

| 호출 | 게이트웨이 경유 | 층 |
|---|---|---|
| `POST /api/auth/password-reset/request` (비로그인) | **401 `TOKEN_INVALID`** | 게이트웨이 — `public-paths` 에 없다(`gateway-service/src/main/resources/application.yml` § public-paths: signup · refresh · `/oauth2/**` 뿐) |
| `POST /api/auth/password-reset/confirm` | 같음 | 같음 |
| `PATCH /api/auth/password` + 유효한 사용자 Bearer | **401 `Missing or invalid internal credentials`** | auth-service — 직접 재현: `X-Account-Id` 만 → 400(정상 판정) · 같은 요청 + `Authorization: Bearer <사용자 토큰>` → **401** |

auth-service `SecurityConfig` 는 두 경로를 `permitAll` 로 열어 두었다(`:160-161`). ② 는 사용자 Bearer 가 붙으면 내부 자격(GAP `client_credentials`) 검사가 먼저 도는 것으로 보인다 — ⚪ 체인 판독은 AC-0.
🔵 이 두 API 를 부르는 프런트는 **0**(저장소 grep) — 그래서 드러나지 않았다. `AccountSessionController`(`/api/accounts/me/sessions`)가 같은 «게이트웨이가 `X-Account-Id` 주입» 패턴이므로 **같은 모양인지** 함께 본다.

# Scope

## In Scope

- AC-0: ② 의 원인을 코드로 확정(어느 체인이 `Authorization` 헤더를 보고 거부하나) + 형제(`/api/accounts/me/sessions/**`) 판정.
- 게이트웨이 `public-paths` 에 재설정 두 경로 추가(POST 만) + 레이트 리밋 검토(재설정 요청은 이미 서비스 쪽 제한이 있다 — `RequestPasswordResetUseCase` rate limit).
- ② 수정.

## Out of Scope

- 프런트 화면(비밀번호 변경 UI)을 새로 만드는 일 — 필요하면 별도 티켓.

# Acceptance Criteria

- [x] **AC-0** — 원인 확정 + 형제 경로 판정. → § AC-0 결과
- [x] **AC-1** — 게이트웨이 경유 IT(또는 슬라이스): 재설정 요청 204 · 확인 204 · 변경(사용자 Bearer) 204 · 대조군: 토큰 없는 변경은 여전히 401. → § AC-1 결과 (🔴 게이트웨이 IT 는 Docker 필요 — CI 판정)
- [ ] **AC-2** ⏳ 재굽기 뒤 창 — `TASK-MONO-737` AC-2 와 같은 창에서 판정 — 🔴 **결과로 판정**(창): 일회용 계정으로 게이트웨이 주소(`https://auth.hubwang.com` 또는 iam 게이트웨이)에서 변경 → 새 비밀번호 로그인 성공 · 재설정 → 새 비밀번호 로그인 성공.

# Related Specs

- `specs/contracts/http/auth-api.md` § PATCH /api/auth/password · password-reset
- `specs/features/password-management.md`
- `TASK-BE-607` § CORRECTION (2026-09-26)

# Related Contracts

- `auth-api.md` — 인증 방식 서술(«Bearer Access Token 필수»)이 실제 경로와 맞는지.

# Edge Cases

| 상황 | 기대 |
|---|---|
| 재설정 요청을 공개로 열었다 | 계정 존재와 무관하게 204(열거 오라클 없음 — 기존 계약) |

# Failure Scenarios

1. **auth-service 직접 호출로 IT 를 짠다** → 이번에 드러난 두 층을 다시 못 본다. 게이트웨이 경유여야 한다.
2. **`/api/auth/**` 전체를 public 으로** → 변경 API 가 인증 없이 열린다.

---

# AC-0 결과 (2026-09-29 UTC · 분석=Opus 5.5 · 코드 판독)

**② 의 원인 = auth-service `@Order(2)` 체인의 Bearer 필터가 `permitAll` 보다 먼저 돈다.** `SecurityConfig.filterChain` 에
`oauth2ResourceServer(jwt → internalJwtDecoder)` 가 붙어 있고, `BearerTokenAuthenticationFilter` 는 이 체인이 받는 **모든** 요청에서
`Authorization: Bearer` 를 발견하면 인가(authorizeHttpRequests) **전에** 인증을 시도한다. `internalJwtDecoder` 는 `internal.invoke`
워크로드 scope 를 요구하므로(TASK-MONO-422) 사용자 토큰은 반드시 실패하고 → 엔트리 포인트가 `401 Missing or invalid internal credentials`.
`permitAll` 은 인가 단계의 결정이라 이 실패를 막지 못한다. 게이트웨이는 `X-Account-ID` 를 주입하면서 사용자 `Authorization` 도
그대로 전달한다 — 직접 재현의 「`X-Account-Id` 만 → 통과 · + Bearer → 401」 과 정확히 일치.

| 경로 (같은 `@Order(2)` 체인 · `permitAll`) | 게이트웨이 경유 시 | 판정 |
|---|---|---|
| `PATCH /api/auth/password` | 사용자 Bearer 동반 → **401** | 🔴 라이브 재현(2026-09-26) |
| `/api/accounts/me/sessions/**` (4경로) | 게이트웨이가 JWT 필수 → Bearer 동반 → **401** | 🔴 **같은 모양**(코드 판독 — 아무 프런트도 부르지 않아 라이브 관측 없음) |
| `POST /api/auth/logout` | public-paths 아님 → Bearer 동반 → **401** | 🔴 **같은 모양**(코드 판독 — 셋째 형제, 티켓이 적지 않았던 것) |
| `POST /api/auth/password-reset/**` | ① 게이트웨이 `public-paths` 누락 → 엣지 401 (Bearer 없는 사용자라 ② 는 안 밟음) | 🔴 라이브 재현 |
| `/internal/**` | 워크로드 토큰 — 이 필터가 **존재 이유** | 🟢 변경 없음(no token → 401 유지) |

**수정** — ① 게이트웨이 `public-paths` 에 `POST:/api/auth/password-reset/request` · `/confirm` (정확 경로, POST 만 — `/api/auth/**` 아님).
② auth-service `SecurityConfig.internalOnlyBearerTokenResolver()` — Bearer 를 `/internal/` 접두 경로에서만 읽는다. 사용자 경로는 게이트웨이가
인증하고 `X-Account-Id` 로 식별하므로 그 토큰을 여기서 다시 검증할 이유가 없다. 한 줄로 형제 셋(변경 · 세션 4 · logout)이 함께 풀린다.
**레이트 리밋**: 재설정 요청은 auth-service 가 이메일별로 제한하고(BE-144) 계정 존재와 무관하게 204 — 엣지 global 버킷도 돈다. 전용 엣지 버킷은
두지 않았다(필요 근거 미측정).

# AC-1 결과 (2026-09-29 UTC)

두 층을 **각각** 잰다 — 이번 결함은 층이 둘이었다(Failure Scenario 1):

| 층 | 셀 | 판정 |
|---|---|---|
| 게이트웨이 (IT, WireMock auth-service · Redis Testcontainer) | `GatewayIntegrationTest.passwordReset_requestAndConfirm_passWithoutToken`(토큰 없이 204 둘) · `passwordChange_withUserJwt_forwardsAccountIdAndBearer`(204 + 하류에 `X-Account-ID` **와 사용자 Bearer 가 함께** 도착 — auth-service 층이 이것을 견뎌야 하는 이유를 핀) · 대조군 `passwordChange_withoutToken_returns401` | 🔴 **로컬 Docker 꺼짐 → CI(`ci.yml` iam gateway `integrationTest`) 판정** |
| 게이트웨이 (단위 — 실제 `application.yml` 을 읽는 `RouteConfigTest`) | 재설정 두 경로 public · 대조군: `PATCH /api/auth/password` · `GET` 재설정 · `/password-reset/other` · 세션 경로는 NOT public | 🟢 |
| auth-service (실제 `SecurityConfig` 슬라이스) | `PasswordControllerSliceTest.changePassword_withForwardedUserBearer_returns204` · 대조군 `internalPath_withSameBearer_stillRejected`(401 유지) · `SecurityConfigBearerResolverTest`(8셀 — `/internal/**` 만 읽고 `/internalx/…` 같은 접두 유사 경로는 안 읽음) | 🟢 |

**검증 (로컬)**: `gateway-service:test` 122 / 0 fail · `auth-service:test` 877 / 0 fail / 31 skip(Docker IT) — **rc=0**.
🔴 첫 실행은 rc=1 이었다 — 대조군 셀의 토큰이 **형식상 유효한 RS256 JWT** 라 디코더가 원격 JWKS 를 받으러 가 연결 오류(401 이 아닌
`AuthenticationServiceException`)가 났다. 수정 결함이 아니라 셀 전제 결함(그리고 `/internal/**` 에선 토큰을 **읽는다**는 반증). 파싱 단계에서
거부되는 토큰으로 바꿨다.
**bite** (백업 → 변이 → 파일 복사 원복 · md5 일치): 리졸버 연결 한 줄 + public-paths 두 줄 제거 → **새 셀 3개만 빨강**(재설정 public ×2 ·
Bearer 동반 변경 204), 대조군·기존 셀 초록 → 원복 후 두 모듈 전체 rc=0.

# AC-2 창 런북

0단계 = 재굽기 확인(`RepoCommit` 이 이 PR 스쿼시를 조상으로 포함). 🔴 공유 데모 계정 금지 — **일회용 계정**.
1. 게이트웨이 주소로 `POST /api/auth/password-reset/request` → **204**(없는 이메일도 204 — 대조군) · 메일/토큰 행 생성 확인 → `…/confirm` **204** → 새 비밀번호로 폼 로그인 성공.
2. 로그인해 받은 사용자 토큰으로 `PATCH /api/auth/password` → **204** → 새 비밀번호 로그인 성공 · 옛 비밀번호 실패.
3. 형제: 같은 토큰으로 `GET /api/accounts/me/sessions` → **200**(이전 401 예상 모양).
4. 대조군: 토큰 없이 `PATCH /api/auth/password` → 엣지 **401 `TOKEN_INVALID`**.

---

## CORRECTION (2026-10-02 UTC) — AC-2 라이브: 재설정 🟢 · 변경 ⚪ (review 유지)

창: 18차 AMI `ami-03fa427e858219e47`(RepoCommit `1feb9fc6d` — AMI 태그·Lambda `AMI_REPO_COMMIT`·`check-ami-generation.sh --with-aws` rc=0 세 곳 일치), 인스턴스 `i-05395a5a7baa23bb8`, 2026-10-02 09:16–10:19 UTC. 측정 대상 변경은 전부 `1feb9fc6d` 의 조상(이미지 시각 ≥ 머지 시각). 브라우저 측정 증거 = 세션 스크래치 `live18/`(스크린샷·로그), 인스턴스 측정 = SSM 읽기 + 일회용 계정 쓰기.

- **재설정 🟢**: `POST https://auth.hubwang.com/api/auth/password-reset/request` → **204**(실제 이메일 · 없는 이메일 둘 다 — 예전 401 TOKEN_INVALID 아님). 일회용 풀 계정 `be612-…` 의 재설정 확인 204 뒤 **새 비밀번호로 팬 로그인 성공**(세션 accountId `8cb67fce-…`) · 틀린 비밀번호 대조군은 IAM 폼 오류. `m9-*`.
- **변경 ⚪**: 팬·스토어에 비밀번호 변경 화면이 없고(이 티켓은 UI 를 범위 밖으로 뒀다), 사용자 액세스 토큰은 브라우저에 오지 않는다(사이트 client 는 전부 confidential, 세션은 id·tenant·roles 만). 토큰 없이 `PATCH /api/auth/password` → **401 TOKEN_INVALID**(경로는 열려 있음). 🔴 곁발견: 스토어 BFF 의 `PATCH /api/bff/api/auth/password` 는 **404**(ecommerce 게이트웨이가 `/api/auth/**` 를 라우팅하지 않음) — 스토어에서 비밀번호를 바꿀 사용자 경로가 지금 없다.
- ⇒ AC-2 의 «변경 → 새 비밀번호 로그인» 을 잴 길이 없다. 닫으려면 (a) 사용자 토큰을 얻는 측정 하네스(인스턴스 안 authorization-code 흐름) 또는 (b) 사이트의 비밀번호 변경 화면이 필요하다 — 소유자 판단. `review/` 유지.

---

## CORRECTION (2026-10-03 UTC) — 4차원 종결 (소유자 결정)

- (a) #4056 `MERGED` · (b) 스쿼시 `3bd595b9c` 가 `origin/main` 에 포함 · (c) 머지 시점 rollup 실패 0/67.
- (d) AC-0·1 본문 근거로 닫힘. AC-2 의 **재설정** 칸은 18차 창(2026-10-02)에서 🟢(위 CORRECTION). AC-2 의 **변경** 칸 — **소유자 결정 (2026-10-03 UTC): «(a) 범위에서 뺀다»**. 팬·스토어에 비밀번호 변경 화면이 없고 사용자 액세스 토큰이 브라우저에 오지 않아 잴 길이 없었다(위 CORRECTION). 게이트웨이·auth-service 의 변경 경로 자체는 AC-1 의 IT/슬라이스(`passwordChange_withUserJwt_forwardsAccountIdAndBearer` · `PasswordControllerSliceTest`)가 지킨다. 변경 화면이 필요해지면 새 티켓으로 연다.
