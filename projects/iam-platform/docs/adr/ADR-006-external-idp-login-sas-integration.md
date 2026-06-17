# ADR-006: 외부 IdP(소셜) 로그인의 SAS 브라우저 플로우 통합 — Upstream Identity Brokering

**Status**: ACCEPTED
**Date**: 2026-06-17 (proposed) → 2026-06-17 (accepted)
**Deciders**: kanggle
**Supersedes**: — (단, 본 ADR 채택 시 `specs/features/oauth-social-login.md` 의 토큰 발급·플로우 절은 갱신 필요)
**Relates to**: ADR-001 (OIDC Adoption — SAS 채택), ADR-005 (workload identity), ADR-MONO-027 (consumer OIDC resource-server), ADR-MONO-032 (통합 identity·roles 축), ADR-MONO-036 (born-unified identity provisioning)

---

## Context

### 현재 두 개의 단절된 로그인 플로우

IAM `auth-service` 는 OIDC Authorization Server(SAS)다. 하지만 **소셜 로그인 capability 가 SAS 브라우저 플로우와 단절된 채 레거시 경로에만 존재**한다.

| | SAS 브라우저 플로우 (consumer 가 실제 쓰는 경로) | 레거시 소셜 플로우 |
|---|---|---|
| 엔드포인트 | `/oauth2/authorize` → `/login` → `/oauth2/token` | `/api/auth/oauth/authorize` · `/api/auth/oauth/callback` (JSON) |
| 로그인 화면 | `WebLoginSecurityConfig` chain[0] = **Spring Security 기본 생성 폼 (email/password 전용)** | 없음 — 클라이언트가 직접 JSON 호출 |
| 발급 토큰 | **SAS 표준 토큰** (issuer `http://iam.local`, JWKS 검증) | `auth-service` **자체 커스텀 JWT** (`OAuthLoginUseCase`) |
| 소셜 버튼 | ❌ 없음 (`oauth2Login()` 미설정, 커스텀 템플릿 없음) | N/A |
| 스펙 | `consumer-integration-guide.md` (PKCE) | `oauth-social-login.md` (BFF 커스텀-JWT) |

### 갭의 구체적 증상

1. **consumer 가 소셜 로그인에 도달 불가** — web-store "Global Account 로 로그인" → IAM `/oauth2/authorize` → `/login` 은 email/password 폼뿐이다. Google/Kakao/Microsoft 클라이언트가 [`OAuthClientFactory`](../../apps/auth-service/src/main/java/com/example/auth/infrastructure/oauth/OAuthClientFactory.java)에 존재하지만 **어느 브라우저 화면에도 노출되지 않는다.**
2. **레거시 플로우의 토큰을 consumer gateway 가 거부** — `/api/auth/oauth/**` 는 SAS 토큰이 아닌 커스텀 JWT 를 발급한다. ecommerce gateway 등 consumer 는 ADR-MONO-027 이후 **SAS issuer(`http://iam.local`) + JWKS** 만 1차 검증 경로로 신뢰한다. 즉 레거시 소셜 토큰은 표준 OIDC consumer 에서 동작하지 않는다.
3. **레거시 커스텀-JWT 계열은 일몰 예정** — 동일 계열인 `POST /api/auth/login`(`LoginController`)은 `@Deprecated(forRemoval, 2026-08-01)`. 소셜 플로우만 커스텀-JWT 로 남기는 것은 방향에 역행한다.
4. **credential 이 테스트 stub** — `application.yml` 의 `OAUTH_GOOGLE_CLIENT_ID:test-google-client-id` 등 기본값이 stub 이라, 실 env 주입 없이는 라이브 동작 자체가 불가.

### 결론

소셜 로그인 "코드"는 IAM 에 있으나 **고아(orphaned) 상태**다: 브라우저 OIDC 플로우에서 도달 불가 + 잘못된 토큰 타입 + 일몰 계열. 외부 IdP 로그인은 SAS 의 **upstream identity brokering**(Keycloak identity provider / Auth0 social connection 에 해당하는 IdP 계층 책임)으로 통합되어야 한다.

---

## Decision

**외부 IdP(소셜) 로그인을 SAS 브라우저 플로우에 upstream identity brokering 으로 통합한다.** 소셜 인증은 **서버 사이드 인증 세션**으로 종결되어 SAS 가 그 세션을 소비해 `authorization_code` → **SAS 표준 토큰**을 발급한다. 더 이상 커스텀 JWT 를 발급하지 않는다.

### 불변식 (invariant)

> 소셜 인증의 성공은 **SAS chain 이 소비하는 인증된 HTTP 세션(JSESSIONID 의 SecurityContext)**으로 끝나야 한다 — SPA 로 반환되는 커스텀 JWT 가 아니라.

이 불변식이 충족되면, 기존 `SavedRequestAwareAuthenticationSuccessHandler` 가 원래의 `/oauth2/authorize` 로 복귀시키고 SAS 가 표준 토큰을 발급한다(email/password 로그인과 동일한 종착).

### 옵션 비교

| 옵션 | 설명 | 재사용 | 단점 |
|---|---|---|---|
| **A. Spring Security `oauth2Login()`** | chain[0] 에 `.oauth2Login()` 추가, 제공자를 `ClientRegistration` 으로 모델링. 커스텀 `OAuth2UserService`/success handler 가 born-unified 계정 해소 | Spring 이 upstream authorization_code+state(+OIDC PKCE/JWKS) 관리 | 전 제공자를 `ClientRegistration` 으로 재모델링 필요. **Kakao/Naver 는 비-OIDC OAuth2** → 커스텀 userinfo. 계정연결 로직을 `OAuth2UserService` 안에 **재구현**(기존 `OAuthLoginUseCase` 와 중복) |
| **B. 커스텀 로그인 페이지 + 기존 OAuthController 플로우를 세션으로 브리지** | 기본 폼을 커스텀 템플릿(password 폼 + 소셜 버튼)으로 교체. 소셜 버튼 → 기존 `/api/auth/oauth/authorize` → **신규 브라우저 콜백**이 커스텀 JWT 대신 **인증 세션 확립** 후 saved request 복귀 | 기존 `OAuthLoginUseCase`·`social_identities`·`/internal/accounts/social-signup`·state(Redis)·Kakao/Microsoft 특이처리·ADR-036 born-unified 연결을 **그대로 재사용**. 신규는 "JWT 발급 대신 세션 확립" 꼬리부분뿐 | 커스텀 Thymeleaf 템플릿 필요(어차피 필요). 세션 확립을 수작업 |
| C. 레거시 유지 + 버튼만 노출 | — | — | 잘못된 토큰 타입 + 일몰 계열 → **기각** |

### 권장 — 옵션 B (A 는 대안으로 기록)

**근거**:

1. **검증된 자산 최대 재사용** — 계정연결(`social_identities`, auto-link/auto-create), born-unified mint(ADR-036), state CSRF 방어, Kakao 이메일 미동의·Microsoft tenant 특이처리는 이미 `oauth-social-login.md` 로 스펙화·구현·테스트되어 있다. 옵션 B 는 이 전부를 재사용하고, **유일한 신규 설계점은 "커스텀 JWT 발급(스펙 step g~i) 대신 SAS 세션 확립"** 이다.
2. **커스텀 로그인 페이지는 어차피 필요** — `WebLoginSecurityConfig` 주석이 이미 *"custom Thymeleaf template 은 별도 future task"* 로 명시. 소셜 버튼은 이 페이지에 자연히 얹힌다.
3. **A 는 중복·재작업 비용** — `oauth2Login()` 은 idiomatic 하지만 전 제공자 `ClientRegistration` 재모델링 + 비-OIDC 제공자(Kakao/Naver) 커스텀 처리 + 계정연결 재구현을 강제한다. 이미 동작하는 로직과 중복된다.

### 핵심 설계 (옵션 B)

- **커스텀 로그인 페이지** (`/login`): password 폼 + 소셜 버튼(Google/Kakao/Microsoft, 후속 Naver). `DefaultLoginPageGeneratingFilter` 대체.
- **신규 브라우저 콜백** (예: `GET /login/oauth/{provider}/callback`): 기존 `OAuthLoginUseCase` 의 계정해소 부분을 재사용해 born-unified `account_id`·roles 를 확정 → `SecurityContextHolder` + 세션 영속 → saved `/oauth2/authorize` 로 redirect. **커스텀 JWT 미발급.**
- **role 시딩 (결정적)** — 소셜로 생성/연결된 계정은 **target consumer 의 OIDC 토큰 `roles` claim 이 올바라야** 한다 (예: web-store 는 `CUSTOMER` 필수, 없으면 storefront 가드가 거부). authorize 요청의 `client_id`(예: `ecommerce-web-store-client`) → tenant 귀속 → ADR-032 RoleSeedPolicy 로 기본 role 시딩.
- **레거시 JSON 플로우 유지(deprecation window)** — `/api/auth/oauth/**` 는 standalone 소비자를 위해 일몰 윈도우 동안 보존, `/api/auth/login` 과 함께 후속 정리.

---

## Consequences

### Positive

- web-store 등 consumer 가 표준 OIDC 플로우로 **실제 동작하는 소셜 로그인** 획득.
- 단일 identity 표면 — edge 신원(password + social)이 전부 SAS 토큰으로 수렴(ADR-MONO-027 consumer 와 정합).
- 고아 커스텀-JWT 소셜 플로우의 일몰 경로 확보.

### Negative / Cost

- 커스텀 Thymeleaf 로그인 템플릿 + 브라우저 콜백 신규 작성.
- 라이브 동작에 **실 제공자 credential 필요**(기본 stub). 포트폴리오 데모는 단일 Google dev 앱만 실값으로 두고 나머지는 stub 유지 가능.

### Risks / Open Questions

- **계정연결 edge** — provider 이메일 미제공(Kakao 미동의), 동일 이메일 다계정 충돌. 기존 스펙의 `EMAIL_REQUIRED`/auto-link 규칙 계승하되 born-unified 와 정합 확인.
- **tenant 귀속** — multi-tenant trait: 소셜 생성 계정의 `tenant_id`·role 을 authorize `client_id` 로부터 결정하는 규칙을 task 에서 확정.
- **per-tenant vs 전역 제공자 config** — 포트폴리오는 전역 공유 dev 앱으로 단순화(후속 재검토).

---

## Implementation (후속 task)

- **TASK-BE-396** — 옵션 B 통합 메커니즘 + 기존 제공자(Google 우선, Kakao/Microsoft 코드재사용)를 SAS 브라우저 플로우로 통합. 커스텀 로그인 페이지 + 세션 브리지 + role 시딩. `oauth-social-login.md` 스펙 갱신.
- **TASK-BE-397** — Naver 제공자 신규 추가(enum + `NaverOAuthClient` + config + 버튼). 396 의 메커니즘 위에서 기계적 확장(Kakao 미러링). **396 선행 의존.**
