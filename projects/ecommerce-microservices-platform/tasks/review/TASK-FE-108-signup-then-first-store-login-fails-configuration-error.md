# Task ID

TASK-FE-108

# Status

review

# Title

가입 직후 이어지는 첫 스토어 로그인이 «인증 서버 설정에 문제가 있습니다» (NextAuth `Configuration`) 로 실패한다 — 재시도는 성공

# Owner

ecommerce-microservices-platform

# Task Tags

- web-store
- auth
- bug
- demo

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus — 가입 직후 재방문 로그인 경로의 쿠키/상태 흐름을 가르는 인증 설계 판단.

---

# Dependency Markers

- 출처: 25차 데모 창(2026-10-09 UTC, AMI `ami-03cc7efda4b0a7809`) — 오케스트레이터 측정(auth_db · auth-service 로그).
- 관련: `TASK-FE-067`(gap oauth cutover) · `TASK-FE-070`(rp-initiated logout) · IAM 가입(signup) → 인증 메일 → 콘솔/스토어 재방문 경로 전반.

---

# Goal

25차 창 실측(auth_db · auth-service 로그, UTC):

| 사실 | 값 |
|---|---|
| 신규 소비자 계정 `shopper1@demo.com` 가입 | 18:25:08Z |
| 그 가입 직후, 같은 브라우저로 스토어 로그인 시도 | `oauth2_authorization`(client `ecommerce-web-store-client-id`) — authorization code 발급 18:25:19Z, **`access_token` NULL**(코드가 콜백에서 교환되지 않았거나 교환이 실패) |
| 그 시각 auth-service 쪽 ERROR 로그 | 없음 |
| 스토어 화면 | NextAuth `Configuration` 오류 — «인증 서버 설정에 문제가 있습니다» |
| 같은 계정으로 재시도 | 18:39:46Z code 발급 → 18:39:47Z **access_token 발급 성공**(즉시 교환) |

⇒ 가입 직후 이어지는 **첫** 로그인 시도만 실패하고, 재시도는 1초 안에 성공한다. 이메일 미인증은 원인이 **아니다** — 재시도 성공 시점에도 이메일은 여전히 미인증 상태였다(인증 여부가 바뀌지 않았는데 결과가 바뀌었다).

가설(미검증, AC-0 가 재측정): 가입 → 로그인 재개(resume) 경로가 스토어 콜백으로 돌아올 때 스토어 자신의 state/PKCE 쿠키가 없거나(가입 흐름이 다른 오리진/리다이렉트를 거치며 쿠키가 비었거나) 기존 쿠키와 값이 어긋나, NextAuth 가 이를 `Configuration` 오류로 보고한다. 이 가설이 맞는지는 실제 NextAuth 에러 타입(`OAuthCallbackError`/`PKCE`/`state` 불일치 등)을 서버 로그에서 읽어야 판정된다 — 이 티켓의 AC-0 가 그 재측정이다.

# Scope

## In Scope

- **AC-0 = 재측정(가정 금지)** — 로컬 또는 e2e 로 "가입 → 즉시 첫 로그인" 경로를 재현하고, 스토어 서버(NextAuth) 로그에서 실제 에러 타입·메시지를 읽는다(현재 `config_error` 매핑이 가리키는 원문 로그 포함). 가입 흐름이 거치는 리다이렉트/쿠키 쓰기 지점을 file:line 으로 추적해 "어떤 쿠키가 없거나 어긋나는가" 를 표로 남긴다.
- AC-0 가 가리키는 지점의 구현(쿠키 수명/경로/도메인 정합, 또는 가입→로그인 재개 리다이렉트 순서 보정 등 — 판정 뒤 결정).
- 단위/통합 시험: 가입 직후 첫 로그인 재현 시나리오(대조군: 재시도·기존 계정 로그인 무변화).

## Out of Scope

- 이메일 인증 자체의 흐름 변경(이 결함과 무관함이 이미 실측으로 배제됨).
- IAM 쪽 가입/인증 코드 변경 — AC-0 가 원인을 IAM 가입 리다이렉트 모양으로 지목하면 범위를 다시 열고 크로스 프로젝트 티켓으로 분리한다(Failure Scenario 참조).

# Acceptance Criteria

- [x] **AC-0** — 착수 시 재측정: 로컬/e2e 로 "가입 → 즉시 첫 로그인" 을 재현하고 스토어 서버 로그의 실제 NextAuth 에러 타입을 읽는다. 가정하지 않고 file:line 으로 쿠키/리다이렉트 경로 표를 남긴다.
- [x] **AC-1** — 🔴 대조군: "가입 직후 첫 로그인"은 실패(현재) → 구현 뒤 성공. "이미 가입된 계정의 일반 로그인"(대조군)은 이전·이후 모두 변화 없이 성공.
- [x] **AC-2** — 재시도 경로(현재 성공) 가 구현 뒤에도 그대로 성공 — 회귀 없음.
- [ ] **AC-3** — 라이브 데모 창에서 신규 가입 → 즉시 첫 로그인이 재시도 없이 성공. ⚪ 다음 데모 창.

# Related Specs

- `projects/ecommerce-microservices-platform/apps/web-store/src/shared/auth/auth.ts`
- `projects/ecommerce-microservices-platform/apps/web-store/src/features/auth/ui/LoginForm.tsx`(`config_error` 매핑)
- IAM 가입(signup) → 로그인 재개(resume) 경로 — `projects/iam-platform/specs/contracts/http/auth-api.md`

# Related Contracts

- OAuth2/OIDC 인가 코드 흐름(RFC 6749 § 4.1), PKCE(RFC 7636) — 스토어 NextAuth 클라이언트 측.

# Edge Cases

- 가입 직후 **다른 브라우저/탭**에서의 첫 로그인은 같은 결함을 재현하지 않을 수 있다(세션 쿠키가 처음부터 없는 경우와 "있지만 어긋난" 경우가 다른 경로일 수 있음) — AC-0 에서 구분해서 잴 것.
- 이메일 인증 여부와 독립적임을 — 재측정 때도 — 대조군으로 다시 확인할 것(이번 실측이 n=1 이다).

# Failure Scenarios

1. "이메일 미인증이 원인" 으로 다시 가정하고 그 경로만 고친다 — 재시도 성공 사실이 이미 이 가정을 반증한다.
2. 원인이 IAM 가입 리다이렉트 모양(크로스 프로젝트)인데 스토어 쪽 쿠키 설정만 넓혀 덮는다 — 증상은 가려지고 다른 클라이언트(콘솔 등)의 같은 경로는 안 고쳐진다.
3. AC-0 없이 "쿠키 수명을 늘린다" 류의 추측 수정부터 한다 — 재현 없이는 고쳤는지 알 수 없다.

---

# AC-0 착수 시도 기록 (2026-10-10 UTC) — 🔴 재현 못 함 → `ready/` 유지

재현 수단 둘 다 없다: 로컬 Docker 데몬 꺼짐(`npipe:////./pipe/docker_engine` 없음) · 데모 창 닫힘(인스턴스 정지). 스토어는 Vercel 에서 돌아 **실제 NextAuth 오류 원문은 Vercel Runtime Logs 에만** 있고, 2026-10-09 18:25Z 의 줄은 보존 기간이 지났을 가능성이 크며 이 호스트엔 `vercel` CLI 가 없다. 그래서 아래는 **정적 추적과 25차 창 기록의 시각 대조**이고, 원인 판정이 아니다.

## 확정된 것 (코드 · 기록)

| 사실 | 근거 |
|---|---|
| 스토어 화면의 `Configuration` 은 원인 하나를 가리키지 않는다 — 콜백에서 난 오류 중 클라이언트 안전 목록 밖의 것은 **전부** `Configuration` 으로 바뀐다. state/PKCE 쿠키 문제(`InvalidCheck`)와 토큰 엔드포인트 거절(`invalid_grant`) 둘 다 `CallbackRouteError` 로 감싸여 같은 화면이 된다 | `@auth/core`(next-auth `5.0.0-beta.25`) `lib/actions/callback/index.js:385-390` · `lib/index.js:130-131` · `errors.js:393` |
| state · PKCE 쿠키 수명 = **15분** | `@auth/core` `lib/actions/callback/oauth/checks.js:5, 89` · 스토어 `checks: ['pkce', 'state']`(`auth.ts:88`) |
| 🔴 **15분 만료 가설은 기각** — 가입 안내(`shopper1@demo.com`, «새 시크릿 창») 메시지 18:23:02Z → 계정 생성 18:25:08Z → 코드 발급 18:25:19Z. 스토어에서 가입을 누른 시각부터 코드까지 **3분 미만** | 25차 창 대화 기록 시각 · auth_db · account_db |
| 🔴 **사이트 소속 거절(`invalid_grant`, `refusePoolPrincipal`)은 기각** — 그 거절은 INFO 로그를 남기는데(`TenantClaimTokenCustomizer.java:682-685`), 18:24Z 이후 auth-service 로그(INFO 포함, 같은 창에서 INFO 줄이 찍히는 것 확인)에 `Credential created` 한 줄뿐이었다 ⇒ 토큰 발급 단계(커스터마이저)까지 **오지 않았다** | 25차 창 `docker logs --since 2026-10-09T18:24:00Z iam-auth-service-1` |
| 가입 뒤 IAM 흐름 = `/login?registered` → 로그인 → 저장된 authorize 요청 재개 → 코드 | `SignupPageController.java:191` · `AuthorizationServerConfig.java:391-398`(`prompt=create` → `/signup` 진입점) |

## 남은 후보 — 둘 다 «스토어가 가진 state/verifier 가 코드를 만든 authorize 요청과 짝이 아니다» 로 모인다

| 후보 | 기전 | 지금 판별 못 하는 이유 |
|---|---|---|
| **H-A 스토어 콜백에서 끊김** (`InvalidCheck`) | state 또는 PKCE 쿠키가 없거나 다른 값 — 토큰 요청을 아예 안 보낸다(auth-service 로그 0 과 맞다) | Vercel 로그 원문 필요 |
| **H-B 토큰 엔드포인트에서 끊김** (SAS PKCE/redirect_uri 검증 `invalid_grant`) | verifier 가 다른 authorize 요청의 것 — SAS 는 이 거절을 DEBUG 로만 남긴다(로그 0 과 **역시** 맞다) | 같음 |

짝이 어긋나는 경로로 코드에서 보이는 것 (🔵 가능성이지 원인 판정 아님):
1. **스토어 `/signup` 의 이중 `signIn`** — 마운트 시 자동 `signup()`(`app/(auth)/signup/page.tsx:46-50`, ref 로 한 번만) **그리고** 리다이렉트가 도는 동안 보이는 «IAM 으로 이동» 버튼(`:59-66`)이 같은 `signIn` 을 다시 부른다. 두 번 부르면 쿠키 쓰기 순서와 IAM 이 마지막으로 저장한 authorize 요청 순서가 어긋날 수 있다. 재시도(18:39Z)는 `/login` 의 단일 클릭이라 어긋날 여지가 없다.
2. **시크릿 창의 공유 쿠키 통** — Chrome 시크릿 창은 열린 시크릿 창끼리 쿠키를 같이 쓴다. 그 통에는 직전(18:19Z) 콘솔 로그인의 IAM 세션이 있었다. 이 경우 스토어 authorize 는 `AuthorizeSessionTenantGate` 를 지나며(`AuthorizeSessionTenantGate.java:160-172`) 재인증으로 바뀐다 — 저장 요청과 쿠키의 순서에 끼어들 수 있는 또 하나의 경로다.

## 다음 데모 창 절차 (AC-0 를 닫는 재현)

1. 🔴 **새 브라우저 프로필 또는 시크릿 창을 전부 닫은 뒤** 새 시크릿 창 하나 — 대조군: 공유 쿠키 통 없음.
2. 스토어 `/signup` → 자동 이동만 기다린다(버튼 누르지 않음) → 새 이메일로 가입 → 로그인. 성공/실패 기록.
3. 같은 조건에서 `/signup` 에서 **«IAM 으로 이동» 버튼을 바로 한 번 더** 누른다 → 가입 → 로그인. 성공/실패 기록(후보 1 판별).
4. 25차 창 순서 재연: 시크릿 창에서 콘솔 로그인(콘솔 자격 계정) → 그 상태로 스토어 가입 → 로그인(후보 2 판별).
5. 🔴 실패가 나면 **바로** Vercel 대시보드 → 스토어 프로젝트 → Runtime Logs 에서 그 시각의 `[auth][error]` 줄(오류 타입 + cause)을 복사한다. 보존 기간이 짧아 나중엔 없다. 동시에 auth-service 로그와 `oauth2_authorization`(client · 코드 발급 · `access_token_issued_at`) 행.

---

# AC-0 닫힘 — 26차 데모 창 재현 (2026-10-11 UTC)

AMI `ami-00701c1fa528b5866`. 위 «다음 데모 창 절차» 그대로 · 소유자 브라우저 · Vercel Runtime Logs(소유자 복사) · auth_db/auth-service(SSM 읽기 전용).

| 단계 | 결과 |
|---|---|
| A 대조군 — 깨끗한 시크릿 창 · `/signup` 자동 이동만 · `shopper2@demo.com` | ✅ 성공 — 코드 09:52:38Z · 토큰 09:52:39Z |
| C — 시크릿 창에서 콘솔(`demo@`) 로그인 뒤 같은 창 `/signup` · `shopper4@demo.com` · 🔴 **소유자가 화면이 뜬 직후 «IAM 으로 이동» 을 누름** | 🔴 **재현** — 코드 09:57:49Z · 토큰 NULL · 스토어 `/login?error=Configuration` |
| B — 깨끗한 창 · 버튼 바로 누름 · `shopper5@demo.com` | ✅ 성공 — 경합에서 이긴 경우(아래) |

**원인 (Vercel 로그 원문, 09:57:49.180Z)**: `[auth][error] CallbackRouteError` · `[auth][cause]: unexpected "state" response parameter value` · `expected`(= 스토어 state 쿠키) `…r70ldHjHyplhTtP…` ≠ IAM 이 돌려준 state = auth_db 저장 authorize 요청의 state `…Ku0h4VSq6Rdh1jmz…` ⇒ **H-A(스토어 콜백에서 끊김, `InvalidCheck` 계열 state 검사)** 확정. 토큰 요청은 나가지 않았다(auth-service 로그 0 과 일치).

**두 번째 state 의 출처 (Vercel 요청 로그)**: `GET /signup` 18:55:13.96 KST → `POST /api/auth/signin/iam` **15.08** → 다시 providers · csrf → `POST /api/auth/signin/iam` **15.81** → IAM authorize 도착은 **한 번**(재인증 로그 09:55:16Z). ⇒ `/signup` 의 자동 `signup()` 과 리다이렉트 중에도 살아 있는 «IAM 으로 이동» 버튼(`app/(auth)/signup/page.tsx:62` 당시)이 `signIn` 을 **두 번** 시작했다. 두 번째가 state/PKCE 쿠키를 덮어쓰고, 브라우저는 첫 번째 이동으로 IAM 에 가 있었으므로 IAM 은 첫 요청을 저장 · 재개했다. 🔵 **경합**이라 같은 동작이 B 처럼 성공하기도 한다 — 25차 «가입 직후 첫 로그인만 실패 · 재시도는 성공» 과 맞다(재시도는 `/login` 의 단일 클릭).

- 기각: 15분 쿠키 만료 · 사이트 소속 거절 · 이메일 미인증(위 착수 시도 기록) · 콘솔 세션 자체(C 의 원인은 버튼이지 세션이 아니다 — 세션은 재인증 로그를 남겼을 뿐 같은 모양의 authorize 를 재개했다: `prompt=create` · `continue` · S256, A 와 동일).
- 🔴 같은 틈이 `/login` 에도 있다 — `LoginForm.handleClick` 이 `finally` 로 버튼을 다시 켜는데 `signIn()` 은 `window.location` 을 세팅하자마자 resolve 하므로 리다이렉트 중 재클릭이 가능했다.
- Failure Scenario 2(IAM 리다이렉트 모양) 아님 — 스토어 클라이언트 쪽 결함이라 크로스 프로젝트 분리 불필요.

## 구현 (AC-1 · AC-2)

- `/signup`: 자동 시작과 버튼이 **같은 가드**(`started` ref + `starting` 상태)를 쓴다 · 시작 뒤 버튼 비활성 «이동 중...» · 시작 **실패**(reject)일 때만 재무장 · bfcache 복원(`pageshow` `persisted`) 때 재무장.
- `LoginForm`: `finally` → `catch` — 성공 경로에서 버튼을 다시 켜지 않는다 · bfcache 복원 때 재무장.
- 시험: `signup-page.test.tsx` 3칸 · `login-form.test.tsx` 2칸(FE-108 표기). 로컬 vitest 는 Node 24 에서 기동 불가(`#module-evaluator`, 알려진 함정) ⇒ 🔴 **bite 는 CI 로**: 시험만 담은 커밋(수정 전 코드)을 먼저 푸시해 `Frontend unit tests` 가 FE-108 칸만 빨간지 보고, 그다음 수정 커밋.

# 리뷰 이동 기록 (2026-10-11 UTC)

- 구현 PR **#4301**(`69da5d1b5`) 머지 · 머지 시점 실패 0/68.
- **AC-1 ✅** — 🔴 bite 는 CI 로(로컬 web-store vitest 는 Node 24 기동 불가): 첫 커밋(시험만 · 수정 전 코드)에서 `Frontend unit tests` 가 **FE-108 3칸만 빨강**(`/signup` 자동 시작 뒤 버튼 잠김 · bfcache 재무장 · `/login` 리다이렉트 중 잠김), 회귀 칸 2(실패 시 재무장 ×2) 초록, 다른 파일 132 초록 + 수정 전 `LoginForm` 의 미처리 rejection 1. 수정 커밋에서 `signup-page`(7) · `login-form`(9) 초록 · web-store 134 파일 초록 · 미처리 0. 로컬 `tsc --noEmit` · `next lint` rc=0.
- **AC-2 ✅** — 재시도 경로(`/login` 단일 클릭)는 같은 `signIn` 이고 성공 경로에서 버튼만 잠긴 채 남는다 — 기존 `login-form` 칸(클릭 → `signIn('iam', {callbackUrl})` · `from` 전달 · 외부 URL 폴백) 전부 초록. e2e(`helpers/auth.ts` · `consent-decline` 등)는 새로 연 `/login` 에서 한 번 누르는 모양이라 영향 없음(grep).
- **AC-3 ⚪** — 다음 데모 창: 신규 가입 → 즉시 첫 로그인이 재시도 없이 성공 + `/signup` 에서 버튼을 눌러도(자동 이동 중) 실패하지 않음.
