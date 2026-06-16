# Task ID

TASK-BE-396

# Title

외부 IdP(소셜) 로그인을 SAS 브라우저 플로우에 통합 (upstream identity brokering)

# Status

in-progress

# Owner

backend

# Task Tags

- code
- api
- adr

---

# Goal

ADR-006(옵션 B) 을 구현한다. 외부 IdP(소셜) 로그인을 IAM SAS 브라우저 플로우에 통합하여, 소셜 인증이 **SAS 가 소비하는 인증된 HTTP 세션**으로 종결되고 그 결과 **SAS 표준 토큰**(issuer `http://iam.local`, JWKS 검증)이 발급되도록 한다. 더 이상 커스텀 JWT 를 발급하지 않는다.

완료 후 참이 되어야 하는 것:

- consumer(예: web-store) 가 "Global Account 로 로그인" → IAM `/oauth2/authorize` → `/login` 에서 **Google 소셜 버튼**을 보고, 클릭 → Google 인증 → 원래 `/oauth2/authorize` 로 복귀 → SAS `authorization_code` → SAS 토큰을 정상 수령한다.
- 발급 토큰의 `roles` claim 이 target consumer 요건을 충족한다(web-store = `CUSTOMER` 포함).

---

# Scope

## In Scope

- `/login` 커스텀 로그인 페이지(Thymeleaf 또는 동등): 기존 email/password 폼 + 소셜 버튼(Google / Kakao / Microsoft). `DefaultLoginPageGeneratingFilter` 대체. CSRF 토큰 유지.
- 신규 **브라우저 콜백** 엔드포인트(예: `GET /login/oauth/{provider}/callback`): 기존 `OAuthLoginUseCase` 의 계정해소(`social_identities` 조회 / auto-link / auto-create via `/internal/accounts/social-signup`, ADR-036 born-unified mint)를 재사용 → `account_id`·roles 확정 → `SecurityContextHolder` + 세션 영속 → saved `/oauth2/authorize` 로 redirect.
- 소셜 인증 시작 경로의 브라우저화: 기존 `/api/auth/oauth/authorize`(state Redis 저장) 재사용하되 로그인 페이지의 소셜 버튼에서 구동.
- **role 시딩**: authorize 요청 `client_id` → tenant 귀속 → ADR-032 RoleSeedPolicy 로 소셜 생성/연결 계정의 기본 role 시딩(web-store consumer = `CUSTOMER`).
- Google end-to-end 검증(설정된 제공자). Kakao/Microsoft 는 코드 재사용으로 경로 동작(라이브 E2E 는 credential 가용 시).
- `specs/features/oauth-social-login.md` 갱신: SAS 세션 종결 플로우 추가, 커스텀-JWT 종결을 deprecation 으로 표기.
- `WebLoginSecurityConfig` 의 `oauth2Login` 미설정 전제 주석 갱신.

## Out of Scope

- Naver 제공자 추가 → TASK-BE-397.
- 레거시 `/api/auth/oauth/**` JSON 플로우 제거(deprecation window 동안 보존).
- per-tenant 제공자 credential 분리(전역 공유 dev 앱 유지).
- 실 제공자 credential 프로비저닝(데모는 stub/단일 Google dev 앱).

---

# Acceptance Criteria

- [ ] `GET /login` 이 password 폼 + Google/Kakao/Microsoft 소셜 버튼이 포함된 HTML 을 렌더한다(CSRF 토큰 포함).
- [ ] Google 소셜 인증 성공 시 커스텀 JWT 가 **아니라** 인증된 HTTP 세션이 확립되고, 이어진 `/oauth2/authorize` 가 SAS `authorization_code` 를 발급한다(브라우저 E2E).
- [ ] 발급된 SAS 토큰의 `roles` claim 에 target consumer 요건 role 이 포함된다(web-store client → `CUSTOMER`).
- [ ] 신규 소셜 계정이 ADR-036 born-unified identity 로 mint 되고 올바른 `tenant_id` 로 귀속된다.
- [ ] 기존 이메일 일치 계정에 대한 auto-link 가 동작한다(중복 계정 생성 없음).
- [ ] `oauth-social-login.md` 가 SAS 세션 종결 플로우를 반영하고, 커스텀-JWT 종결이 deprecation 으로 표기된다.
- [ ] 레거시 `/api/auth/oauth/**` JSON 플로우의 기존 테스트가 여전히 통과한다(무중단).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus `rules/domains/saas.md` and `rules/traits/{transactional,regulated,audit-heavy,integration-heavy,multi-tenant}.md`. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-006-external-idp-login-sas-integration.md` (본 task 의 결정 근거)
- `docs/adr/ADR-001-oidc-adoption.md`
- `specs/features/oauth-social-login.md` (갱신 대상)
- `specs/features/consumer-integration-guide.md` (PKCE 브라우저 플로우)
- `specs/features/multi-tenancy.md` (tenant 귀속·role 시딩)
- `specs/services/auth-service/architecture.md`

# Related Skills

- `.claude/skills/backend/external-http-integration` (OAuth provider 연동 패턴)

---

# Related Contracts

- `specs/contracts/http/auth-api.md` (`/api/auth/oauth/*`; 신규 브라우저 콜백 경로 추가 시 갱신)
- `specs/contracts/http/internal/auth-to-account-social.md` (`/internal/accounts/social-signup`)
- `specs/contracts/events/auth-events.md` (`auth.login.succeeded`, loginMethod)

---

# Target Service

- `auth-service`

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md` (Service Type = identity-platform / SAS)
- 필터체인 순서 불변: WebLoginSecurityConfig `@Order(0)` (`/login`,`/logout`) → SAS `@Order(1)` (`/oauth2/**`) → legacy `@Order(2)` (`/api/auth/**`). 커스텀 로그인 페이지는 chain[0] 안에서만 변경.

---

# Implementation Notes

- **불변식**: 소셜 인증 성공은 SAS chain 이 소비하는 인증된 HTTP 세션(JSESSIONID SecurityContext)으로 끝나야 한다. SPA 로 커스텀 JWT 를 반환하면 안 된다.
- 계정해소 로직은 `OAuthLoginUseCase.callback()` 에서 추출/재사용. JWT 발급 꼬리(스펙 step g~i, `oAuthLoginTransactionalStep.persistLogin`)만 세션 확립으로 대체.

## 코드 정찰 + 확정 설계 (2026-06-17, 구현 착수 전)

- **role 시딩 = 신규 코드 0** — `TenantClaimTokenCustomizer.customizeForAuthorizationCode` → `populateRoles` → `RoleSeedPolicy.seed(platform)` 가 이미 처리. `platform = registered client 의 tenant_id`(ClientSettings) 로 키잉되므로, `ecommerce-web-store-client` 로 시작된 authorization_code 는 **principal.details 만 올바르면 `roles:[CUSTOMER]` 자동 주입**. seed 는 principal tenant 가 아니라 client platform 으로 키잉됨.
- **principal 템플릿** — `CredentialAuthenticationProvider` 가 정답: `UsernamePasswordAuthenticationToken(email, null, [ROLE_USER])` + `details = HashMap{tenant_id, tenant_type, account_id}`. details 맵은 **반드시 `HashMap`**(Map.of 는 SAS `JdbcOAuth2AuthorizationService` 의 SecurityJackson2Modules allowlist 밖 → /oauth2/token 라운드트립 깨짐). 소셜 콜백도 이 토큰을 세워 `SecurityContextRepository` 로 세션 영속 → saved `/oauth2/authorize` 복귀.
- **`.loginPage("/login")`** — 커스텀 페이지 제공 시 호출하는 게 맞음(default 폼 억제가 의도). BE-311 함정은 default 폼 의존 시 호출 금지였음(상황 반전).
- **Thymeleaf 추가** — `auth-service` 는 `spring-boot-starter-web` 만 → `spring-boot-starter-thymeleaf` 추가 + `templates/login.html`.

### tenant 귀속 — 개시 OIDC client 파생 (사용자 확정 2026-06-17, ADR-006 옵션 1)

소셜 principal 의 `tenant_id` = **로그인을 개시한 consumer 의 tenant**. 메커니즘: 콜백 시점에 세션의 `RequestCache`(saved authorization request `/oauth2/authorize?client_id=...`)에서 `client_id` 를 읽어 `RegisteredClientRepository.findByClientId` → `ClientSettings` 의 `OAuthClientMapper.SETTING_TENANT_ID`/`SETTING_TENANT_TYPE` 추출 → principal.details 에 주입. state 스레딩 불필요(saved-request 에 이미 client_id 존재). saved request 부재(직접 `/login` 진입) → `TenantContext.DEFAULT_TENANT_ID` fallback.

---

# Edge Cases

- provider 이메일 미제공(Kakao 미동의) → 기존 `EMAIL_REQUIRED`(422) 규칙 계승, 브라우저 플로우에선 `/login?error=email_required` 로 안내.
- 동일 이메일 기존 계정 존재 → auto-link(신규 계정 생성 없음).
- 계정 상태 비-ACTIVE(LOCKED/DORMANT/DELETED) → 소셜 로그인 거부, `/login?error` 안내.
- target consumer 요건 role 미시딩 계정 → 토큰에 role 누락 시 consumer 가드가 거부(web-store `account_type_mismatch`). 시딩 보장이 AC.
- state 만료/불일치(Redis) → 기존 `INVALID_STATE` 방어 계승.

---

# Failure Scenarios

- provider token endpoint 장애 → `/login?error=provider_error`(기존 502 PROVIDER_ERROR 의 브라우저 대응).
- account-service `/internal/accounts/social-signup` 장애 → fail-closed, 세션 미확립.
- 커스텀 로그인 페이지 렌더 회귀(스타일/CSRF 누락) → PC-FE Playwright 트레이스로 적발(BE-311 선례).

---

# Test Requirements

- unit test: 브라우저 콜백의 세션 확립·role 시딩 로직.
- integration test(`@SpringBootTest`): `/oauth2/authorize` → `/login`(소셜 버튼 렌더) → 소셜 콜백(provider 모킹) → 세션 확립 → `authorization_code` 발급. Docker-free `:check` 는 wiring 미적발이므로 Testcontainers `@SpringBootTest` IT 가 권위(메모리 `feedback_spring_boot_diagnostic_patterns`).
- contract test: auth-api.md 신규 경로 추가 시 정합.
- 무중단: 레거시 `/api/auth/oauth/**` 기존 테스트 그대로 통과.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required (`oauth-social-login.md`)
- [ ] Ready for review
