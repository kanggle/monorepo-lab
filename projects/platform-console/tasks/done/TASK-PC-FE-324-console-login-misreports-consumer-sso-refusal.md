# Task ID

TASK-PC-FE-324

# Title

같은 브라우저에 스토어(소비자) 로그인 세션이 있으면 IdP 가 콘솔 토큰 발급을 거절하는데, 콘솔은 그것을 **«인증 서버에 연결할 수 없습니다»** 로 보인다

# Status

done

# Owner

platform-console

# Task Tags

- platform-console
- iam

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 콜백이 토큰 교환 실패의 **종류**를 가르는 일. IdP 쪽 동작(거절)은 옳다.

---

# Dependency Markers

- 출처: 24차 데모 창(2026-10-08 UTC) — `TASK-FE-107` 확인 뒤 같은 브라우저로 콘솔 로그인 시도.
- 관련: `TASK-BE-610`(done — 콘솔 SSO 가 소비자 세션을 재사용하던 결함) · `TenantClaimTokenCustomizer.refuseConsumerPoolTenant`(iam auth-service).

# Goal

관측(IdP 로그, 18:46–18:47Z 7회):

```
SECURITY: refused to mint a token whose tenant_id is the reserved pool value.
grantType=authorization_code, clientId=platform-console-web
```

1. 소유자가 스토어에서 소비자 계정으로 로그인 → `auth.hubwang.com` 세션 = 소비자 풀 계정.
2. 콘솔 로그인 → IdP 가 그 세션을 SSO 로 재사용 → authorization_code 교환에서 `invalid_grant`(풀 tenant 는 발급 금지 — 옳은 거절).
3. 콘솔 콜백은 그것을 `token_exchange_failed` 로 뭉개고 `/login` 이 **«인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.»** 를 보인다(`app/(auth)/login/page.tsx:22-23`).

⇒ 사용자는 «기다리면 된다» 로 읽고 계속 실패한다. 실제 해법은 «다른 계정으로 로그인돼 있다 — 로그아웃 후 다시». 데모 동선(스토어 ↔ 콘솔 같은 브라우저)에서 매번 밟힌다. 시크릿 창으로 우회했다.

# Scope

## In Scope

- 콜백이 토큰 응답의 `error=invalid_grant` + 풀 tenant 거절을 **별도 코드**로 구분해 `/login` 에 «다른(소비자) 계정으로 로그인돼 있습니다 — 로그아웃 후 운영자 계정으로 다시 로그인» + IdP 로그아웃(end_session) 경로를 보인다.
- 네트워크/5xx 실패는 지금 문구 유지.

## Out of Scope

- IdP 가 콘솔 authorize 에서 소비자 세션을 아예 재사용하지 않게(prompt=login 강제 등) — 별도 판단(BE-610 계열).

# Acceptance Criteria

- [x] **AC-0** — 콜백이 토큰 오류 본문에서 무엇을 받을 수 있는지(error · error_description) 재측정 · 구분 술어 결정(문자열 일치 vs IdP 전용 코드).

  **측정(2026-10-09 UTC):**

  | 축 | 값 | 근거 |
  |---|---|---|
  | 거절 지점 | IAM **토큰** 엔드포인트(`/oauth2/token`), `/oauth2/authorize` 는 통과(코드 발급됨) | `TenantClaimTokenCustomizer.refuseConsumerPoolTenant`, `iam-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/oauth2/TenantClaimTokenCustomizer.java:261-275` |
  | 던지는 예외 | `OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, "tenant_id '" + TenantContext.CONSUMER_POOL_TENANT_ID + "' is a reserved storage value and is never issued", null))` | 같은 파일 :272-274. `TenantContext.CONSUMER_POOL_TENANT_ID = "consumer-pool"` (`domain/tenant/TenantContext.java:52`) |
  | 콘솔이 실제로 받는 HTTP 본문(SAS 기본 토큰-엔드포인트 오류 직렬화) | HTTP 400, `{"error":"invalid_grant","error_description":"tenant_id 'consumer-pool' is a reserved storage value and is never issued"}` | 같은 거절 지점을 때리는 iam-platform 자체 통합시험 `AssumeTenantExchangeIntegrationTest.consumerPool_refusedEvenWhenAssigned` (`status 400` · `.contains("invalid_grant")`) + `error_description` 을 정확값 판별자로 쓰는 기존 선례(`TOKEN_TENANT_MISMATCH`, `ConsumerPoolSsoIntegrationTest.java:396`) — 둘 다 같은 SAS 기본 오류 직렬화를 전제한다 |
  | 다른 `invalid_grant` 들과 겹치는가 | 아니다 — 만료/재사용 코드(`"code expired"`, `"…reuse detected…"`), `TOKEN_TENANT_MISMATCH`, assume-tenant 거절은 모두 **다른** `error_description` 을 쓴다. 이 거절만 리터럴 `'consumer-pool'` 을 포함 | 위 인용 + `iam-platform` 전역 `error_description` grep (본 티켓 구현 중 수행) |

  **구분 술어 결정 — 문자열 일치 (IdP 전용 코드 아님):**
  - 콜백이 보는 `error_description` 은 SAS 가 그대로 돌려주는 자유 영어 문장이 아니라, **소스 상수**(`TenantContext.CONSUMER_POOL_TENANT_ID`)로 포매팅된 문자열이다 — Edge Case 가 우려하는 "문구가 바뀌면 조용히 빗나간다" 의 폭을 줄이려고, 판별은 **전체 문장이 아니라** 그 상수가 들어가는 자리의 리터럴 `'consumer-pool'`(인용부호 포함, 코드가 포맷하는 정확한 모양)만 본다 — `error === 'invalid_grant' && error_description?.includes("'consumer-pool'")`. 코드: `callback/route.ts` 의 `isConsumerPoolSsoRefusal` + `CONSUMER_POOL_REFUSAL_MARKER`.
  - IdP 전용 에러 코드를 새로 만드는 안은 **기각** — iam-platform 코드 변경이 필요해 이 티켓(platform-console 소유)의 범위 밖이고(크로스 프로젝트 원자적 PR 이 아니라 별도 ADR/티켓이 맞다), 기존 `error_description` 이 이미 소스 상수에 고정돼 있어 추가 없이도 안전한 판별이 가능하다.
  - 잔여 위험(인정): `TenantContext.CONSUMER_POOL_TENANT_ID` 자체의 **값**(`"consumer-pool"`)이 바뀌면 이 판별도 깨진다 — 그러나 그것은 저장 스키마를 흔드는 고의적 breaking change 라 자체 마이그레이션이 필요하고, 조용한 문구 수정과는 다른 등급의 변경이다.

- [x] **AC-1** — 새 오류 코드 + 문구 + 로그아웃 링크(단위 · 콜백 라우트 시험, 두 갈래: 풀 거절 / 연결 실패).

  - 새 코드 `sso_wrong_account` — `callback/route.ts` `isConsumerPoolSsoRefusal` 분기, `(auth)/login/page.tsx` `ERROR_MESSAGES`.
  - 새 문구: *"다른 계정(스토어 소비자 계정)으로 로그인돼 있어 콘솔에 들어갈 수 없습니다. 아래에서 로그아웃한 뒤, 스토어(쇼핑몰) 탭에서도 로그아웃하고 운영자 계정으로 다시 로그인하세요."*
  - 로그아웃 버튼 — `widgets/sso-wrong-account-logout/SsoWrongAccountLogout.tsx`, 기존 `performLogout`(`shared/lib/logout.ts`, TASK-PC-FE-033 — account-menu 의 로그아웃과 **동일 코드경로**) 재사용. `data-testid="sso-wrong-account-logout"`, `sso_wrong_account` 일 때만 렌더.
  - 🔴🔴 **이 버튼이 혼자서 막는 세션을 끝내지 못하는 이유(검증됨, 추측 아님)** — 이 실패는 콘솔이 이 로그인 시도에서 토큰을 **한 번도 받은 적이 없는** 지점(토큰 엔드포인트)에서 나므로 `id_token` 쿠키가 없다. `spring-security-oauth2-authorization-server-1.4.1.jar`(실제 사용 버전, `auth-service/build.gradle` 의존성 확인) 를 디컴파일해 `OidcLogoutAuthenticationProvider.authenticate` 바이트코드를 직접 읽었다: `authorizationService.findByToken(idTokenHint, ID_TOKEN_TOKEN_TYPE)` 를 **무조건** 호출하고 결과가 null(빈/없는 hint 는 항상 null) 이면 즉시 `throwError("invalid_token", "id_token_hint")` — 세션 레지스트리로 대체하는 경로가 이 버전엔 없다. 같은 결론이 이미 `tests/unit/logout.test.ts` 의 `TASK-MONO-705` 주석에도 쓰여 있다("id_token 이 없으면 로그아웃이 로컬 폴백으로 떨어지고 IdP 세션이 살아남는다"). 그래서 문구가 "스토어 탭에서도 로그아웃" 을 명시한다 — 그 id_token 은 스토어 탭의 쿠키에만 있다.
  - 단위시험(`tests/unit/login-error-messages.test.tsx`): 메시지 핀(EXPECTED 양방향 키 매칭 포함) · 버튼이 이 코드에서만 렌더 · 클릭 시 `performLogout` 재사용.
  - 콜백 라우트 시험(`tests/unit/auth-routes.test.ts`): 풀 거절→`sso_wrong_account` / 다른 `invalid_grant`(만료 코드)→`token_exchange_failed`(대조군, Failure Scenario 1) / 네트워크 실패(fetch throw)→`token_exchange_failed` / 5xx(본문 없음)→`token_exchange_failed`.

- [x] **AC-2** — bite: 구분 분기(`isConsumerPoolSsoRefusal` 호출 + `sso_wrong_account` 리다이렉트)를 지우고 `tests/unit/auth-routes.test.ts` 를 돌렸다 — **정확히 1개**(풀 거절 시험)만 빨강, 나머지 21개는 그대로 초록. 분기를 되돌리자 22/22 초록으로 복귀. (일회성 — diff 는 커밋에 남지 않음, 이 레코드가 증거.)
- [x] **AC-3** — ⚪→✅ 25차 데모 창(2026-10-09 UTC) 라이브 측정. 1차 시도는 스토어에서 `demo@demo.com` 로 로그인한 채 콘솔 로그인 → IAM 이 **콘솔 자격을 이미 쥔 그 계정**의 재인증을 요구하는 화면을 보였다(`TASK-BE-610` 재인증 — auth 로그 `re-authentication required (TASK-BE-610)`) — 이것은 이 AC 의 측정이 아니다(소비자 풀 거절이 아니라 다른 경로). 2차 시도: 새 소비자 계정 `shopper1@demo.com`(이메일 미인증)으로 스토어 로그인 → 콘솔 로그인 → 이 티켓이 만든 새 문구 «다른 계정(스토어 소비자 계정)으로 로그인돼 있어 콘솔에 들어갈 수 없습니다…» + 로그아웃 버튼이 보였다(auth 로그 `SECURITY: refused to mint a token whose tenant_id is the reserved pool value … platform-console-web`). AC-1 의 디컴파일 근거대로 콘솔 로그아웃 버튼 단독으로는 IdP 세션이 끝나지 않았다(설계대로 — `SsoWrongAccountLogout.tsx` 에 문서화됨); 소유자가 스토어 탭에서 로그아웃한 뒤 콘솔 로그인이 IAM 폼을 보였고 `demo@demo.com` 이 콘솔 토큰을 받았다(auth_db: code 19:19:15Z · token 19:19:16Z). AC 가 요구하는 전체 경로(새 문구 → 로그아웃 안내 → 성공)가 라이브로 성립했다 — 아래 § 25차 창 측정 기록.

# Related Specs

- `projects/platform-console/specs/` § 인증 콜백 · `projects/iam-platform/specs/contracts/` § 토큰 엔드포인트 오류

# Related Contracts

- OAuth2 토큰 응답 오류(RFC 6749 § 5.2) — iam auth-service

# Edge Cases

- error_description 문구가 바뀌면 문자열 일치가 조용히 빗나간다 — AC-0 에서 정한다.

# Failure Scenarios

1. 모든 invalid_grant 를 «다른 계정» 으로 읽는다 — 만료 코드도 그 문구가 된다.
2. 문구만 바꾸고 로그아웃 경로를 안 준다 — 사용자가 여전히 갇힌다.

# Implementation Record (2026-10-09 UTC)

- `projects/platform-console/apps/console-web/src/app/api/auth/callback/route.ts` — `isConsumerPoolSsoRefusal` 판별자 + `sso_wrong_account` 분기 추가(AC-0/AC-1).
- `projects/platform-console/apps/console-web/src/app/(auth)/login/page.tsx` — `ERROR_MESSAGES.sso_wrong_account` + 조건부 `SsoWrongAccountLogout` 렌더.
- `projects/platform-console/apps/console-web/src/widgets/sso-wrong-account-logout/SsoWrongAccountLogout.tsx` — 새 위젯, 기존 `performLogout` 재사용.
- `projects/platform-console/apps/console-web/tests/unit/auth-routes.test.ts` — 풀 거절 / 대조군 3종(다른 invalid_grant · 네트워크 실패 · 5xx) 추가.
- `projects/platform-console/apps/console-web/tests/unit/login-error-messages.test.tsx` — `EXPECTED` 에 새 코드 추가(양방향 키 매칭 가드 통과 필요) + 버튼 렌더/클릭 시험.

**검증 (각 명령의 실제 rc, `cmd > file 2>&1; echo rc=$?` 로 측정):**

| 게이트 | rc |
|---|---|
| `pnpm install --frozen-lockfile` (node_modules 가 이 worktree 에 없어 선행) | 0 |
| `npx tsc --noEmit` | 0 |
| `npm run lint` (`next lint`) | 0 |
| `npx vitest run tests/unit/auth-routes.test.ts tests/unit/login-error-messages.test.tsx` (타게팃) | 0 (44/44) |
| `npx vitest run` (전체) | 0 (351 파일 · 3997 시험) |
| bite: 분기 제거 후 `npx vitest run tests/unit/auth-routes.test.ts` | 1 (정확히 1개 실패 — 의도된 결과) |
| bite 복원 후 재실행 | 0 (22/22) |

- e2e: `e2e-smoke/login-page.spec.ts` 가 `provider_error`/`invalid_state`/`state_mismatch`/`token_exchange_failed` 를 검사하지만 이 코드들의 문구·동작을 바꾸지 않았다(새 코드만 추가) — 깨지지 않는다. `sso_wrong_account` 자체의 e2e 커버리지는 없음(범위 밖 — AC-3 참조).
- iam-platform 쪽 코드는 **전혀 건드리지 않았다** — 소유 범위(platform-console) 를 지켰고, Out of Scope(`prompt=login` 등 IdP authorize 변경) 도 손대지 않았다.

# 25차 데모 창 측정 기록 (2026-10-09 UTC, AMI `ami-03cc7efda4b0a7809`) — AC-3 닫음

- 1차(demo@demo.com, 콘솔 자격 보유 계정) — `TASK-BE-610` 재인증 경로를 밟아 이 AC 의 측정이 아님.
- 2차(shopper1@demo.com, 신규 소비자 계정) — 새 문구 + 로그아웃 버튼 노출(auth 로그로 거절 지점 재확인). 콘솔 로그아웃만으로는 IdP 세션이 안 끝남(설계대로, AC-1 디컴파일 근거와 일치) → 스토어 탭 로그아웃 후 콘솔 재로그인 → `demo@demo.com` 콘솔 토큰 발급 성공(19:19:16Z).
- 이 티켓의 전체 AC(0~3) 가 닫혔다.
