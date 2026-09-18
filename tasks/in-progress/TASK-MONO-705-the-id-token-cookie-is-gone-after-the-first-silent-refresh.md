# Task ID

TASK-MONO-705

# Title

🔴 콘솔이 30분 유휴 뒤 조용히 갱신하면 **`console_id_token` 이 다시 서지 않는다** — 그 뒤의 로그아웃은 IdP 세션을 못 끝낸다

# Status

in-progress (2026-09-18 UTC — AC-0 · AC-3 닫힘 · AC-1 은 🔴 소유자 결정 대기)

# Owner

monorepo

# Task Tags

- console
- iam
- oidc
- logout

---

# Goal

`TASK-MONO-674` 가 30분 유휴 뒤 조용한 갱신(`/api/auth/refresh`)을 넣었고, 2026-09-17 두 창에서 그 경로가 화면을 살린다는 것은 확인됐다. 그 티켓의 마지막 측정 칸(«id_token 쿠키가 갱신 뒤 다시 서는가»)의 답은 **«안 선다»** 였다(2026-09-17 UTC 둘째 창, AMI `af0018aa6`):

| UTC | 콘솔 쿠키(이름 · 남은 초) |
|---|---|
| 16:44:13 로그인 | access 1787 · **id_token 1787** · operator 3588 · refresh 30일 |
| 17:15:13 유휴 31분 뒤 | operator · refresh 만 남음 |
| 17:18:42 갱신 뒤(`307 /api/auth/refresh` → 원래 화면) | access 1779 · operator 3580 · refresh 30일 · **id_token 없음** |

코드상 콘솔은 **갱신 응답에 `id_token` 이 있을 때만** 쿠키를 세운다(`console-web/src/shared/lib/session-refresh.ts:148-150`, 콜백 `app/api/auth/callback/route.ts:149` 과 같은 조건) ⇒ **IAM 의 `refresh_token` 그랜트 응답에 `id_token` 이 없다**고 읽힌다(🔴 응답 본문 자체는 아직 안 봤다 — AC-0).

결과: 로그인 30분 뒤부터 로그아웃이 `id_token_hint` 없이 **로컬 로그아웃으로 폴백**한다(`app/api/auth/logout/route.ts:31-32,86`) — 브라우저 쿠키는 지워지지만 **IdP(IAM) 세션은 남는다.** 같은 브라우저에서 다시 «로그인» 을 누르면 비밀번호 없이 들어갈 수 있는지가 이 결함의 사용자 쪽 모양이다(AC-0 에서 확인). 표시 이름은 액세스 토큰으로 폴백해 **눈에 보이는 변화는 없다** — 그래서 조용하다.

---

# Scope

## 포함

- IAM 갱신 응답에 `id_token` 이 정말 없는지, 없다면 왜인지(Spring Authorization Server 설정 · 클라이언트 등록 · scope) 관측으로 지목.
- 갈래 비교 후 **소유자 결정**: ⓐ IAM 이 `openid` scope 의 refresh 응답에 `id_token` 을 싣게 한다 ⓑ 콘솔이 `id_token` 쿠키를 로그인 시점 값으로 **갱신하지 않고 유지**한다(만료를 refresh 쿠키 수명에 맞춘다 — `id_token_hint` 는 만료된 id_token 도 받는 IdP 가 많다, 🔴 IAM 이 받는지 먼저 잰다) ⓒ 수용(로컬 로그아웃 폴백을 문서화).
- 로그아웃이 IdP 세션까지 끝내는지의 판정 술어.

## 제외

- 조용한 갱신 경로 자체(`TASK-MONO-674` 에서 끝났다).
- 다른 콘솔 클라이언트(fan · store)의 같은 축 — AC-0 에서 같은 모양이면 기록만.

---

# Acceptance Criteria

- [x] **AC-0 — 재측정.** (1) IAM `refresh_token` 그랜트 응답에 `id_token` 필드가 있는가를 **응답 본문의 키 목록**으로 본다(값 출력 금지) — 로컬 IAM(Testcontainers/슬라이스)으로 재현되면 그것이 첫 판정, 안 되면 창. (2) 로그인 30분 뒤 로그아웃 → 같은 브라우저에서 «로그인» 을 누르면 IAM 폼이 다시 뜨는가(뜨면 IdP 세션이 끝난 것). 🔴 두 번째는 창이 필요하다 — 없으면 ⚪ + `TASK-MONO-672`.
- [ ] **AC-1 — 갈래를 고른다 (🔴 소유자 결정).** 위 ⓐ/ⓑ/ⓒ 를 AC-0 결과와 함께 추천을 붙여 묻는다. 🔴 추천을 결정으로 적지 마라. ⓐ 는 IAM(다른 프로젝트) 설정 변경이라 계약·보안 축을 적는다.
- [ ] **AC-2 — 고친다 + bite.** 테스트: «갱신 뒤에도 로그아웃이 `id_token_hint` 를 싣는다»(ⓐ/ⓑ) 또는 «로컬 폴백이 사유를 말한다»(ⓒ). 고친 것을 되돌리면 빨개진다.
- [x] **AC-3 — 창 판정.** 로그인 → 31분 유휴(손대지 않은 세션) → 이동 → 로그아웃 → «로그인» 이 IAM 폼을 다시 띄우는가. 창이 없으면 ⚪ + `TASK-MONO-672`.

---

# Related Specs

- `tasks/done/TASK-MONO-674-thirty-minutes-of-idle-logs-you-out-and-says-nothing.md` § 창 실측(항목 8) · `:182`
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.6 (세션 · 로그아웃)
- `TASK-PC-FE-033` — 로그아웃 `id_token_hint`

# Related Contracts

- IAM OIDC 토큰 엔드포인트의 refresh 응답 · 콘솔 로그아웃(RP-initiated logout). 🔴 바꾸면 계약 먼저.

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| 30분 안에 로그아웃 | 지금도 `id_token_hint` 가 실린다 — 대조군 |
| 60분 유휴(operator 까지 만료) 뒤 로그아웃 | 30분 칸과 같은 판정 |
| IAM 이 만료된 `id_token_hint` 를 거부 | ⓑ 는 불가 — AC-0 에서 먼저 잰다 |

# Failure Scenarios

1. **콘솔만 보고 «IAM 이 안 준다» 로 확정한다** → 응답 본문 키를 안 봤다(AC-0 (1)).
2. **쿠키가 다시 선 것으로 닫는다** → 판정은 «로그아웃이 IdP 세션을 끝내는가» 다(AC-3).

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Opus 5** (IAM 설정·보안 축 판단. 갈래가 ⓒ 면 Sonnet 5)

---

# 🔵 AC-0 (1) — 저장소 재측정 2026-09-17 UTC: **IAM 이 refresh 그랜트에서 id_token 을 만들지 않는다**

창이 아니라 **코드로** 답이 나왔다(조사 에이전트 + 조정자 직접 확인):

| 자리 | 읽은 것 |
|---|---|
| `projects/iam-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/oauth2/SasRefreshTokenAuthenticationProvider.java:341-343` | `return new OAuth2AccessTokenAuthenticationToken(registeredClient, clientPrincipal, sasAccessToken, newRefreshToken, Map.of());` — 마지막 인자가 additionalParameters 다. **빈 맵**이고, 파일 전체에 `id_token`/`IdToken` 언급이 **0건**(조정자가 `grep -c` 로 확인) |
| 같은 파일 `:207`, `:228`, `:248` | 원 authorization 의 `authorizedScopes` 를 **그대로** 쓴다 — 어디서도 `openid` 를 깎지 않는다 |
| `AuthorizationServerConfig.java` (tokenEndpoint 프로바이더 등록) | 이 커스텀 프로바이더가 **먼저** 등록돼 SAS 내장 refresh 프로바이더보다 우선 ⇒ 내장 경로의 id_token 발급은 **도달 불가** |
| `V0015__seed_platform_console_oidc_client.sql` | 콘솔 클라이언트 scope 에 `openid` **있음**, grant 에 `refresh_token` 있음 |
| `console-web/src/shared/lib/session-refresh.ts:104-107` | 콘솔 refresh 요청은 `grant_type`·`refresh_token`·`client_id` 만 보낸다 — **`scope` 파라미터를 안 보낸다** ⇒ 갈래 ⓑ(«콘솔이 openid 를 뺀다»)는 **기각** |
| 같은 파일 `:137`, `:149-154` | 응답에 `id_token` 이 있으면 **반드시** 쿠키를 세운다 ⇒ 갈래 ⓒ(«받고도 저장 안 한다»)도 **기각** |

⇒ **(a) IAM 이 발급하지 않는다** — 커스텀 프로바이더가 additionalParameters 를 비우기 때문이다. 🔵 고칠 자리는 한 곳이고 재료는 이미 있다: `TenantClaimTokenCustomizer` 에 `isIdToken` + REFRESH_TOKEN 분기가 이미 있고 `JwtGenerator` 도 등록돼 있다.

🔴 **아직 안 잰 것**: ① 실제 토큰 엔드포인트 응답 본문(위는 «우리 코드가 안 만든다» 이지 «응답에 없다» 를 엔드포인트에서 읽은 것은 아니다 — 다만 창 실측에서 쿠키가 안 선 것이 그 증거다) ② 갈래 ⓑ 의 전제인 «IAM 이 **만료된** id_token_hint 를 받는가»(`TASK-PC-FE-033:87` 의 주장인데 출처가 없다) ③ AC-0 (2) «로그아웃 뒤 다시 로그인하면 IAM 폼이 뜨는가»(창 필요).

🔵 **첫 판정을 만들 자리(AC-2 의 bite 후보)**: `PlatformConsoleOidcClientSeedIntegrationTest` 가 이미 콘솔 클라이언트로 같은 refresh 호출을 하면서 `access_token`/`refresh_token` 만 단언한다 — 거기에 `id_token` 키 존재 단언 한 줄이면 된다(🔴 Docker 필요 — 없으면 조용히 skip 되는 구조).
🔴 **픽스처가 결함을 가리고 있다**: 콘솔 `tests/unit/auth-idle-refresh.test.ts:119` 의 IAM 성공 픽스처는 `id_token: 'new.id'` 를 **넣는다** — 현실에 없는 입력이라 그 스위트의 초록은 이 축에서 공허하다.

---

# 🔵 창 실측 — 2026-09-18 UTC (창 03:58:15Z–04:48:28Z · 45분) · AMI `ami-02613b0378621b124`(`af0018aa6`) · 인스턴스 `i-07ddb6b41233f2673` · 소유자 승인 «창 75분, 667 finance 까지» (분석·측정=Opus 5)

## AC-0 (2) · AC-3 — 🔴 **결함은 실재한다. 그런데 «31분» 한 칸만 쟀으면 정반대로 적을 뻔했다**

### ① 먼저 티켓이 적은 그대로 쟀다 (로그인 04:07:37Z → 유휴 31분 → 04:39:05Z)

| 칸 | 관측 |
|---|---|
| 유효성 술어 | 🟢 IdP 세션 쿠키(`auth.hubwang.com JSESSIONID`)가 **복원된 상태**에서 쟀다 — 없으면 「폼이 떴다」는 로그아웃의 증거가 아니라 그냥 세션이 없는 것이다 |
| 유휴 뒤 첫 항해 | 200 · `/dashboards/overview` · «테넌트를 선택» 안내 없음 ⇒ `TASK-MONO-674` 의 조용한 갱신이 화면을 살린다 |
| `console_id_token` 재발급 | 🔴 **없다** (갱신 뒤 쿠키: access 1793 · assumed 1793 · operator 3594 · refresh 2591994 — **id_token 없음**) |
| 로그아웃 갈래 | 🔴 **로컬 폴백** — `POST /api/auth/logout` 이 `logoutUrl = /login` 을 냈다(`/connect/logout` 이 아니다) |
| 다시 «로그인» | 🟢 **IAM 폼이 떴다**(비밀번호 칸 1개) |

⇒ 여기까지만 보면 «기전은 결함인데 사용자 피해는 없다» 이고, 그러면 ⓒ(수용)로 기울었을 것이다.

### ② 🔴🔴 그 결론은 **교란돼 있었다** — 대조군이 갈랐다

①의 ④는 두 해석과 똑같이 맞는다: **ⓐ 로컬 폴백도 IdP 세션을 끝낸다** vs **ⓑ IAM 세션이
31분 유휴로 스스로 죽어 있었다**. 로그아웃이 한 일과 시간이 한 일이 **같은 관측**을 만든다.
그래서 시간을 빼고 다시 쟀다 — **갓 로그인한 세션에서 `console_id_token` 쿠키만 지운다.**

| 칸 | `id_token` | IdP 세션 | 로그아웃 갈래 | 다시 로그인 | 판정 |
|---|---|---|---|---|---|
| **양성 대조** (04:44) | 있음 | 살아 있음 | 🟢 RP-initiated | `auth.hubwang.com/login` · 비밀번호 칸 **1** · 세션 재발급 **없음** | 정상 경로는 작동한다 |
| **결함** (04:42) | **제거**(주입 단언: 나머지 4쿠키 유지 · IdP 세션 유지) | 살아 있음 | 🔴 로컬 폴백 | `/dashboards/overview` · 비밀번호 칸 **0** · `console_access_token` **다시 섰다** | 🔴🔴 **비밀번호 없이 재입장한다** |
| **실제 31분** (04:39) | 만료 | **죽어 있었다** | 🔴 로컬 폴백 | IAM 폼 1개 | 무해해 **보였을 뿐** |

🔴 **결론: 결함은 실재한다.** 로컬 폴백 로그아웃은 IdP 세션을 끝내지 못하고, 그 상태에서
«로그인» 을 누르면 **비밀번호 없이** 세션이 다시 선다. 31분 창에서 폼이 떴던 것은 로그아웃이
한 일이 아니라 **IAM 브라우저 세션이 그 사이 스스로 만료**했기 때문이다.

🔴 **노출 창의 크기는 아직 모른다.** IAM 세션의 유휴 만료가 id_token 수명(1789초)과 비슷해
보이지만, 그것은 **한 표본에서 «대략 비슷하다»** 일 뿐 설정값을 읽은 것이 아니다. 두 값의
관계가 **성질**인지 **우연**인지는 `auth-service` 의 세션 타임아웃 설정을 읽어야 한다
(저장소에서 잴 수 있다 — 창이 필요 없다). AC-1 의 입력이므로 거기서 잰다.

## 🔴 내가 틀린 술어 — 기록

대조군의 첫 판은 «비밀번호 칸이 0개인가»로 물었고 «콘솔 `/login` 에 그대로 머물렀다»와
«비밀번호 없이 재입장했다»가 **같은 0** 으로 나왔다. 실제로 첫 실행은 `console.hubwang.com/login`
에서 0개를 보고 **정반대 판정**을 찍었다. 고친 술어는 «**세션 쿠키가 다시 섰는가**»이고,
항해가 삼켜지는 경우를 없애려고 입구를 «누르는» 대신 그 링크로 **직접 갔다**. 판정이 뒤집혔다.

## AC-1 — 🔴 소유자 결정 (이 창의 결과를 붙여 다시 묻는다)

| 갈래 | 이 창이 바꾼 것 |
|---|---|
| ⓐ IAM 이 refresh 응답에 `id_token` 을 싣는다 | 🔵 **피해가 실측됐으므로 ⓒ 보다 강해졌다.** IAM(다른 프로젝트) 설정·계약 변경 |
| ⓑ 콘솔이 로그인 시점 `id_token` 을 **유지**한다 | 🔴 **선행 측정이 아직 없다** — IAM 이 «만료된 `id_token_hint`» 를 받는지 안 쟀다(이 창에서는 만료된 값을 손에 넣을 수 없었다: 만료되면 쿠키가 복원되지 않는다). 그것 없이는 고를 수 없다 |
| ⓒ 수용(문서화) | 🔴 **약해졌다** — «비밀번호 없이 재입장» 이 실측됐다 |

🔵 **분석의 추천(결정 아님)**: ⓐ. 이유는 ⓑ 가 «IdP 가 만료 토큰을 받는가» 라는 **남의 구현에
기댄 가정** 위에 서 있고, ⓒ 는 이제 «알려진 인증 결함을 문서로 덮는» 모양이기 때문이다.
🔴 추천을 결정으로 적지 않는다.

## 노출 창의 크기 — 저장소 쪽 측정 (2026-09-18, 창 없이)

`iam-platform` 전체에서 `server.servlet.session.timeout` · `spring-session` · `maxInactiveInterval`
설정을 찾았고 **하나도 없다**(`DeviceSession*` 은 도메인 리포지터리이지 HTTP 세션 저장소가 아니다)
⇒ 서블릿 세션은 **Spring Boot 기본값 30분**으로 돈다. 🔴 이것은 **문서에서 온 값**이지 돌고 있는
인스턴스를 잰 값이 아니다 — 그 구별을 지키려고 여기 적는다. 창에서 잰 것은 «31분이면 죽어 있다»
한 점뿐이고, 그 점은 이 기본값과 **모순되지 않는다**.

🔴🔴 **그래도 노출 창을 «30분» 으로 적으면 안 된다.** 두 시계는 **다른 사건으로 리셋된다**:

| 시계 | 리셋하는 사건 |
|---|---|
| `console_id_token`(1789초) | **아무것도 없다** — 갱신이 다시 세우지 못하는 것이 이 티켓의 결함이다 |
| IAM 브라우저 세션(기본 30분 유휴) | `auth.hubwang.com` 으로의 **모든 방문** |

이 데모는 IdP 하나를 **콘솔·스토어·팬 셋이 공유**한다. 그래서 사용자가 형제 앱에 SSO 로
들어가기만 해도 IdP 세션 시계는 새로 시작하고, 콘솔의 `id_token` 은 **죽은 채로 남는다**
⇒ 「`id_token` 은 없는데 IdP 세션은 살아 있는」 구간이 **30분에 묶이지 않는다.**
🔵 이것은 위 표의 두 리셋 규칙에서 나온 **추론**이다 — 실측하려면 「콘솔 로그인 → 40분 뒤
스토어 로그인 → 콘솔 로그아웃 → 콘솔 재로그인」 한 칸을 창에서 돌리면 된다(다음 창 후보).
