# Task ID

TASK-BE-604

# Status

review

# Title

🔴 SAS 기본 refresh provider 가 우리 provider 뒤에 살아 있다 — 도메인 거부(미러 행 폐기·만료·테넌트 불일치)가 전부 무시된다

# Owner

iam-platform

# Task Tags

- auth-service
- oauth2
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 — 인증 필터 체인 구성 변경 · 테넌트 비교 재정의 · 기존 세션 영향.

---

# Goal

`TASK-BE-603` 의 IT 가 CI 에서 드러냈다(2026-09-25 UTC, PR #4027 run 36134528069): 미러 행을 폐기해도 **SAS refresh 가 200 으로 성공한다**.

- `SasRefreshTokenAuthenticationProvider` 는 거부한다(`:189` 폐기 행 → `INVALID_GRANT`).
- 그러나 SAS 1.4.1 `OAuth2TokenEndpointConfigurer` 가 기본 `OAuth2RefreshTokenAuthenticationProvider` 를 **그대로 등록**하고, 우리 것은 그 **앞에 끼워질 뿐**이다
  (`AuthorizationServerConfig` 에 제거 코드 없음 — «먼저 추가 ⇒ 우선» 이 «대체» 로 읽혀 왔다).
- `ProviderManager` 는 `AuthenticationException` 을 받으면 **다음 provider 로 넘어간다** ⇒ SAS 기본 provider 는 SAS 인가만 보고 새 토큰을 발급하고,
  그 저장이 `DomainSync` 로 **새 미러 행까지 만든다** ⇒ 세션이 완전히 되살아난다.

**영향 (BE-603 이전부터)** — 미러 행만 폐기하는 경로는 SAS 세션을 끝내지 못한다:

| 경로 | 근거 | 효과 |
|---|---|---|
| 비밀번호 재설정 | `ConfirmPasswordResetUseCase.java:95` | 🔴 재설정 후에도 기존 브라우저 세션이 refresh 된다 |
| SAS 재사용 탐지의 «다른 세션 전부 폐기» | `SasRefreshTokenAuthenticationProvider.java` `handleReuseDetected` | 🔴 탈취 의심 후에도 다른 세션이 살아 있다 |
| 미러 행 만료 · `TOKEN_TENANT_MISMATCH` | 같은 provider `:193-216` | 거부가 무시된다 |
| force-logout · 계정 잠금 | BE-601 `SasAuthorizationRevocationAdapter` | ✅ SAS 인가 자체를 무효화하므로 **실제로 막히는 유일한 경로** |

🔴 **그냥 기본 provider 를 제거하면 안 될 수 있다** (코드 판독 · ⚪ 미측정): 계정 테넌트 ≠ client 테넌트인 세션(예: BE-507 이전 `fan-platform` 계정이
다른 테넌트 client 로 로그인 — `CredentialAuthenticationProvider.java:190-213` 교차 조회 · `:289-293` details 테넌트 = 계정 테넌트)은 미러 행 테넌트가
계정 테넌트라 매 refresh 가 `TOKEN_TENANT_MISMATCH` 로 떨어지고, **지금은 이 fall-through 덕분에 갱신되고 있을 수 있다**. 제거하면 그 사용자가 끊긴다.

# Scope

## 포함

- **AC-0 (🔴 소유자 결정)**: ⓐ 기본 provider 제거 + 테넌트 비교 재정의를 한 변경으로 · ⓑ 먼저 운영의 `auth.token.tenant.mismatch` 발생량을 측정하고 결정 ·
  ⓒ 기타. 결정 입력: fall-through 로만 갱신되는 세션의 실제 규모(측정 가능한가부터).
- 결정대로 구현 — 도메인 거부가 최종 거부가 되게.
- 비밀번호 재설정·재사용 탐지가 SAS 세션을 실제로 끝내는지 결과로 판정.

## 제외

- principal name 을 UUID 로 바꾸는 것(BE-603 제외 항목과 같음).

# Acceptance Criteria

- [x] **AC-0** — 위 ⓐ/ⓑ/ⓒ 소유자 결정(+ ⓑ 면 측정 결과). ✅ **닫힘 (2026-09-26 UTC) — 소유자 결정 = 선택지 D** (아래 이력 끝 🔵 항목).
  🔵 **소유자 결정 (2026-09-25 UTC) = ⓑ `tenant.mismatch` 발생량을 먼저 측정한다.** 측정은 실행 중인 스택이 필요하므로
  `TASK-MONO-672` **항목 16** 으로 넘겼다: ① 살아 있는 SAS 세션의 (client 테넌트, 미러 행 테넌트) 교차표 — 불일치 세션 수 = 제거 시 끊길 세션 ·
  ② 창 동안의 `cross-tenant attempt detected` 로그 줄과 outbox `auth.token.tenant.mismatch` · ③ 코드 판독상 예측된 모집단 둘
  ((a) 테넌트 `'*'` SUPER_ADMIN 의 스토어 로그인 · (b) BE-507 이전 `fan-platform` 계정의 교차 테넌트 로그인)을 직접 재현하고 콘솔을 대조군으로.
  유효성 술어(세션 0 · refresh 0 · 조인 전부 NULL 이면 «판정 불가»)는 그 항목에 있다.
  🔴 **이 AC 는 측정 결과가 적히고 그것으로 ⓐ 또는 대안을 고르기 전에는 닫히지 않는다** — 측정 결과가 곧 결정은 아니다(0 이어도 데모 모집단 ≠ 운영).
  🟢 **측정 결과 (2026-09-26 UTC 창 · 상세 = `TASK-MONO-672` § 2026-09-26 창 수확)**:
  ① 살아 있는 SAS 세션 **불일치 0 / 6**(콘솔 iam/iam 4 · 스토어 ecommerce/ecommerce 2 · 미러 없음 0). ② 재현 전 `cross-tenant attempt detected` **0**.
  ③(a) 모양 없음 — 데모 운영자 계정은 테넌트별 자격 행을 가져 자기 테넌트 client 로 일치한다(`'*'` 자격 행 없음).
  ③(b) 🔴 **재현됨** — fan-platform 전용 계정이 `platform-console-web`(iam) 로 로그인 → refresh → `clientTenant=iam, tokenTenant=fan-platform` 로그 **+ HTTP 200**.
  ⇒ **결정 입력**: 지금 그 모양의 세션은 0 이지만 **누구나 만들 수 있는 모양**이다(소비자 계정 → 다른 테넌트 client 로그인 → 교차 테넌트 조회). 기본 provider 만
  제거하면 그 세션의 refresh 는 400 이 된다. 선택지는 (i) 제거 + «교차 테넌트 로그인 세션은 refresh 가능» 을 테넌트 비교에서 명시적으로 허용 ·
  (ii) 제거 + 그런 세션은 refresh 불가(재로그인)로 정하고 계약에 적기 · (iii) 교차 테넌트 로그인 자체의 허용 범위부터 재검토.
  🔵 **소유자 결정 (2026-09-26 UTC) = (iii) 교차 테넌트 로그인의 허용 범위부터 재검토.** 검토 결과(코드·이력 판독, 분석=Opus 5.5)는 아래 § AC-0 (iii) 검토.
  ~~🔴 **다음 소유자 결정 대기 — 아래 선택지 A–D.**~~
  🔵 **소유자 결정 (2026-09-26 UTC) = D** — 아래 § AC-0 (iii) 검토의 선택지 표 그대로:
  ① 폼 로그인 교차 테넌트 자격 폴백(`CredentialAuthenticationProvider.resolveCredential`)은 **시작 client 의 테넌트 = 콘솔(`iam`) 일 때만** 남긴다.
  소비자 client(그 밖의 모든 client)에서는 제거 — 범위 조회 실패 = 일반 로그인 실패. 시작 client 없음(`clientTenant == null`)은 현행 유지(1건 일치 / 모호 fail-closed).
  ② refresh 테넌트 비교(선택지 A 부분) = 미러 행 테넌트 대 **로그인 시점 principal 테넌트**(SAS 인가의 principal details `TENANT_ID` = 토큰 `tenant_id` claim) — client 테넌트가 아니다. D5 셀프 온보딩 운영자의 콘솔 세션은 계속 갱신된다.
  ③ 곁가지 결함(회전 행 테넌트 = client 테넌트)을 같이 고친다 — 회전 행도 로그인 시점 테넌트.
  ④ SAS 기본 `OAuth2RefreshTokenAuthenticationProvider` 를 토큰 엔드포인트에서 **제거** — 우리 provider 의 거부가 최종이 된다.
  **하위 기본값 (소유자가 나중에 뒤집을 수 있음)**: (가) 소비자 client 로그인 실패 = 일반 `/login?error`(테넌트 힌트 없음 — 열거 방지) ·
  (나) 소셜 로그인 테넌트 스탬프(`SocialLoginBrowserController:185` 가 client 테넌트를 찍음)는 **범위 밖** — 후속 후보로만 기록 ·
  (다) `multi-tenancy.md:200` 의 «403» vs 코드 400 → **400 `invalid_grant`(OAuth2 토큰 엔드포인트 규약) 가 정본**, 스펙 문장을 고친다.
  미결 ①(실제 운영자 중 자격 테넌트 ≠ `iam` 인 수) · ②(C/(ii) 전용) 은 D 에서 결정에 필요하지 않게 되었다 — ① 은 D 가 그 운영자들을 보존하므로 규모와 무관.
- [x] **AC-1** — IT: 미러 행 폐기 → 다음 refresh 400 `invalid_grant` (BE-603 IT `@Order(8)` 가 일부러 **단언하지 않은** 칸). 🔴 기존 fall-through 200 을 고정하는 단언을 만들지 마라.
  ✅ `OAuth2RefreshTokenIntegrationTest.java:530-549` — `revokeAllByAccountId(uuid)` 직후(인가는 그대로) refresh → `400` + `$.error=invalid_grant` + SAS 인가가 여전히 그 토큰을 쥐고 있음(= 회전 없음 — 기본 provider 는 여기서 인가를 회전시키고 DomainSync 로 새 행을 만들었다). BE-603 의 강제 로그아웃 단언은 **새 세션**에 옮겨 유지(`:551-570`). ⚪ Docker 부재로 로컬 미실행 → CI 판정. 로컬 대체 측정은 § 구현 기록 ⑤.
- [x] **AC-2** — IT: 비밀번호 재설정 후 기존 SAS 세션 refresh 거부 · 재사용 탐지 후 다른 세션 refresh 거부.
  ✅ 재설정: `OAuth2RefreshTokenIntegrationTest.java:579-607` (`@Order(9)`) — 대조군 refresh 200 → `ConfirmPasswordResetUseCase` → 미러 행 revoked → refresh `400 invalid_grant`.
  ✅ 재사용: `:622-659` (`@Order(10)`) — 세션 A·B(같은 계정) · 대조군 B refresh 200 → A 의 자식 행 주입 → A refresh `400`(`error_description` «reuse detected») → B 의 행 revoked → B refresh `400 invalid_grant`.
  🔴 **재사용 분기는 SAS 경로에서 자연 재현이 안 된다** — 이유와 뜻은 § 구현 기록 ⑦. ⚪ 둘 다 CI 판정.
- [x] **AC-3** — 계정 테넌트 ≠ client 테넌트 세션의 refresh 가 결정한 대로 동작(허용이면 성공, 차단이면 명시적 거부 + 계약 문서).
  ✅ 신규 `CrossTenantLoginRefreshIntegrationTest` — 실제 브라우저 경로(비인증 authorize → 저장된 요청 → `POST /login` → 재개 authorize → 코드 → 토큰 → refresh), fan-platform **전용** 자격 1개:
  (a) `:151-178` 콘솔 client → 로그인 성공(콘솔 한정 폴백) · 토큰 `tenant_id=fan-platform` · 첫 행 fan-platform · refresh **200** · 회전 행 **fan-platform** · 그 회전 행으로 다음 refresh 200 ·
  (b) `:180-201` ecommerce 소비자 client → `302 /login?error` · 세션 없음 · 상태 조회 0회(자격을 찾지 않았다) ·
  (c) `:203-216` 대조군 fan-platform 소비자 client(`demo-spa-client`) → 로그인 · refresh 200. 계약 문서 = `multi-tenancy.md` § 로그인 가능한 계정과 client + § Refresh Token. ⚪ CI 판정(로컬 대체 측정은 ⑤).

## AC-0 (iii) 검토 — 교차 테넌트 로그인은 어디까지 허용되나 (2026-09-26 UTC · 코드·이력 판독)

🔴 실측은 BE-604 AC-0 측정(0/6 · ③(b) 재현)과 `TASK-MONO-386`(모집단 0) 뿐이다. 나머지는 코드 판독이다.

**경로 셋 — 서로 다르게 동작한다**

| 경로 | 발동 | 토큰 `tenant_id` · 첫 미러 행 | 도달 client |
|---|---|---|---|
| 폼 `CredentialAuthenticationProvider.resolveCredential` (`:187-210`) | client 테넌트 조회 실패 → 전체 이메일 조회가 정확히 1건 | **계정 테넌트** (`:288-290` · `TenantClaimTokenCustomizer:363` → `DomainSyncOAuth2AuthorizationService:191-199`) | 전부 |
| 소셜 `OAuthLoginUseCase:249-258` | 신원을 테넌트 없이 provider+userId 로 조회 | **client 테넌트** (`SocialLoginBrowserController:185`) — refresh 불일치가 구조적으로 안 난다 | 전부 |
| 운영자 assume-tenant `AssumeTenantAuthenticationProvider:150-162` | admin 배정 게이트 통과 시(fail-closed) | 선택 테넌트 · **refresh token 미발급** (`:227-229`) | 콘솔 |

🔴 곁가지 결함: 회전 시 미러 행 테넌트 = **client 테넌트**(`SasRefreshTokenAuthenticationProvider:483-495`) — 첫 행(claim 테넌트)과 어긋난다. 어느 선택지든 같이 고친다.

**왜 있나**
- `TASK-BE-309` 가 폼 로그인을 만들며 «v1 단일 테넌트 가정» 으로 전체 이메일 조회를 넣었다 — **부수적**.
- `TASK-BE-507` D1-a 가 «BE-507 이전 `fan-platform` 쇼핑객» 을 살리는 폴백으로 **의도적으로** 유지.
- `TASK-MONO-386` 이 그 모집단을 **실측 0 명**으로 확인 ⇒ 소비자 client 폴백의 원래 근거는 **사라졌다**.
- 🔴 **새로 생긴 의존 — ADR-MONO-044 D5**(ACCEPTED): 기존 소비자가 운영자가 된다. 셀프 온보딩은 운영자 `oidc_subject` = 소비자 `account_id`,
  비밀번호 NULL(`FirstAdminProvisioner.java:80-91`) ⇒ **그 운영자들은 교차 폴백으로만 콘솔에 로그인한다**(콘솔 경로 가입은 불가, `signup.md:54-56`).

**교차 토큰이 하류에서 하는 일**
- fan 소비자 → 콘솔 client: admin 교환은 `sub` 로만 운영자를 찾는다(`TokenExchangeService:28-31,81-88`). 매핑 없으면 401 → `/onboarding`(`callback/route.ts:178-193`).
  콘솔 셸 진입 불가, 역할 증가 없음 ⇒ 닿는 곳은 **설계된 온보딩뿐**.
- fan 계정 → 스토어 client(폼): 역할 비어 web-store 가 익명 세션으로 떨어지고, 게이트웨이도 테넌트로 막는다(`OAuth2ResourceServerConfig:122-125`) ⇒ **성공하지만 쓸모없는 세션**.
- 🔴 소셜은 client 테넌트를 찍으므로 같은 사람이 폼으로는 쓸모없는 세션, 소셜로는 CUSTOMER 로 입장한다 — 폼·소셜이 갈라진다.

**스펙** — «어느 계정이 어느 client 로 로그인할 수 있나» 규칙은 **찾지 못했다**(`specs/{features,services,contracts}` · ADR-MONO-044 · BE-309/507 · MONO-334/386).
가까운 문장: `multi-tenancy.md:200`(교차 refresh 금지, **403** — 코드는 400 `invalid_grant`, 불일치) · `:337` · `:379-382` · `signup.md:57-63`.

**선택지**

| | 내용 | 깨지는 것 | 비용 | BE-604 에 주는 뜻 |
|---|---|---|---|---|
| A | 교차 로그인 그대로 · refresh 는 «미러 행 테넌트 == 로그인 당시 principal 테넌트» 로 판정 | 없음 | 작음 | 기본 provider 안전 제거 |
| B | (계정 테넌트 → client 테넌트) 허용 목록 | 목록 밖 쌍 | 큼(새 스펙·계약 · 소셜 포함) | A + 목록 |
| C | 교차 로그인 전면 금지(SUPER_ADMIN 예외) | **셀프 온보딩·OIDC-only 운영자 전원의 콘솔 로그인** | ADR-MONO-044 D5 개정 필요 | client 테넌트 비교로 충분 |
| **D (권고)** | 폴백을 **client 테넌트 = `iam`(콘솔) 일 때만** 허용, 소비자 client 에선 제거 + refresh 는 A 방식 | 소비자 client 교차 로그인 — 실측 모집단 0, 지금도 쓸모없는 세션(해당자는 그 테넌트에서 새로 가입 가능, `(tenant_id,email)` 복합 unique) | 중간(`resolveCredential` 한 곳 + 스펙 한 줄 + A) | 기본 provider 안전 제거 |

**미결** — ① 실제 운영자 중 자격 테넌트가 `iam` 이 아닌 사람 수(`admin_operators.oidc_subject` ↔ `credentials.tenant_id` — 창 측정) ·
② C/(ii) 를 고르면 콘솔 refresh 400 뒤 IdP 세션으로 무화면 재로그인이 되는가 · ③ 소비자 client 폴백 제거 시 `/login?error` 가 «잘못된 비밀번호» 로만
보이는 것을 받아들일지 · ④ 소셜 경로 테넌트 스탬프를 계정 테넌트로 맞출지(`TASK-BE-602` 와 연결) · ⑤ `multi-tenancy.md:200` 의 403 vs 코드 400.

# Related Specs

- `specs/services/auth-service/data-model.md` (BE-603 이 «폐기된 미러 행이 SAS refresh 를 막지 않는다» 주석을 넣었다)
- `TASK-BE-603` (발견 · `## CORRECTION`) · `TASK-BE-601` (폐기 어댑터) · `TASK-BE-507` (테넌트별 계정)

# Related Contracts

- `auth.token.tenant.mismatch` — 발행 규칙이 바뀌면 계약부터.

# Edge Cases

| 상황 | 기대 |
|---|---|
| 배포 시점에 fall-through 로만 살아 있던 세션 | AC-0 결정대로 — 끊기면 재로그인 안내가 사용자 경로에서 보이는가 |
| SAS 인가는 살아 있고 미러 행이 없음(BE-603 이전 INSERT 실패분) | 미러 행 부재 = 거부인가 허용인가를 먼저 정한다 |

**처리 (2026-09-26 UTC)**:
- **배포 시점 fall-through 세션** — 모양은 «미러 행 테넌트 ≠ client 테넌트» 인데, D 의 비교 기준(로그인 시점 테넌트)으로는 그 세션들이 **일치**한다(첫 행 = claim = 로그인 테넌트 · fall-through 시절의 회전 행도 기본 provider → DomainSync 가 claim 에서 썼다) ⇒ 끊기지 않는다. 🔴 예외 하나(코드 판독, ⚪ 미측정): BE-603 배포(2026-09-25)~BE-604 배포 사이에 **첫 행이 없던** 교차 테넌트 세션이 우리 provider 로 회전했다면 그 회전 행은 client 테넌트(`iam`)를 담았다 → BE-604 이후 첫 refresh 에서 `400`. 조건(교차 테넌트 · 첫 INSERT 실패 = 36자 초과 이메일 · BE-603 이후 회전)이 겹쳐야 하고 데모 측정은 불일치 0/6. 콘솔 BFF 는 400 에서 한 번 재시도 후 `session_expired` 로 재로그인을 안내한다(`session-refresh.ts:124-134`).
- **미러 행 부재 = 허용 (기존 동작 유지로 결정)** — 판정의 1차 저장소는 SAS 인가이고(BE-601 어댑터가 그것을 닫는다), 그 회전이 세션 테넌트로 행을 새로 쓰므로 다음 refresh 부터 행 검증이 붙는다. 거부로 바꾸면 INSERT 가 삼켜진 세션(`DomainSyncOAuth2AuthorizationService:156-165`)이 이유 없이 끊긴다.

# Failure Scenarios

1. **기본 provider 만 제거한다** → 테넌트가 어긋난 세션이 전부 끊긴다(⚪ 규모 미측정). → ✅ 피함: 비교 기준을 같이 바꿨다. 로컬 프로브로 **재현**했다(§ 구현 기록 ⑤ bite ②: client 테넌트 비교로 되돌리면 콘솔 교차 세션 refresh 400).
2. **fall-through 200 을 단언하는 테스트를 넣는다** → 결함이 핀으로 얼어붙는다. → ✅ 200 단언 없음. BE-603 의 Order(8) 주석(«A revoked mirror row does NOT by itself refuse…») 을 뒤집었다.
3. **force-logout 만 보고 «막힌다» 고 판정한다** → 그 경로만 BE-601 어댑터로 막혀 있다. 재설정·재사용 탐지는 별개다. → ✅ AC-2 IT 두 개가 각 경로의 결과(다음 refresh)를 따로 단언한다 — 둘 다 SAS 인가를 **건드리지 않는** 경로라 기본 provider 가 남아 있으면 200 이 된다.

---

## 구현 기록 (2026-09-26 UTC · Opus 5.5)

**① 설계 (file:line)**

| 변경 | 위치 | 요지 |
|---|---|---|
| 기본 refresh provider 제거 | `AuthorizationServerConfig.java:246-252` · `:350-378` `removeBuiltInRefreshTokenProvider` | SAS 1.4.1 `OAuth2TokenEndpointConfigurer.init`(javap): 기본 목록에 커스텀을 `addAll(0, …)` 한 **뒤** `authenticationProvidersConsumer.accept(전체 목록)` → 각 항목을 `http.authenticationProvider` 로 등록. 소비자에서 `instanceof OAuth2RefreshTokenAuthenticationProvider` 제거. 🔵 **가드**: 남은 목록에서 `supports(OAuth2RefreshTokenAuthenticationToken)` 가 정확히 1개 · 그것이 `SasRefreshTokenAuthenticationProvider` 가 아니면 **기동 실패**(다른 클래스 이름으로 fall-through 가 돌아오는 것까지 막는다). 다른 SAS 설정기(클라이언트 인증 · revoke · introspect · OIDC)는 refresh 토큰을 지원하는 provider 를 등록하지 않는다(코드 판독) |
| 세션 테넌트 | 신규 `AuthorizationSessionTenant.java:40-51` | principal details 에 `tenant_id`+`tenant_type` 둘 다 있으면 `tenant_id`, 아니면 client 테넌트 — **`TenantClaimTokenCustomizer.customizeForAuthorizationCode:359-386` 의 claim 규칙을 그대로 복제**(첫 미러 행은 그 claim 에서 온다 — 규칙이 다르면 첫 행과 회전 행이 다시 어긋난다) |
| refresh 테넌트 비교 | `SasRefreshTokenAuthenticationProvider.java:176-179` · `:206-231` | 미러 행 ↔ **세션 테넌트**. 거부 이벤트 `expectedTenantId` = 세션 테넌트(계약 정의 «새 token 의 tenant_id» 와 일치). WARN 로그는 `sessionTenant/clientTenant/tokenTenant` 를 찍고 🔵 **refresh 토큰 값은 더 이상 로그에 찍지 않는다**(identity-platform «refresh token 로깅 금지» — 같은 줄을 고치며 제거; 이벤트 `reusedJti` 는 계약대로 유지). 로그 문구 «cross-tenant attempt detected» 는 `TASK-MONO-672` 항목 16 이 grep 하므로 유지 |
| 회전 행 테넌트 | `:394` · `:495-513` `persistRotation` | client 테넌트 → 세션 테넌트 |
| `auth.token.refreshed.tenantId` | `:406-411` | client 테넌트 → 세션 테넌트(계약 «토큰의 tenant_id» 와 일치 — 교차 테넌트 세션에서만 값이 바뀐다) |
| 폼 로그인 폴백 한정 | `CredentialAuthenticationProvider.java:178-229` `resolveCredential` · `:207` | 범위 조회 실패 + client 테넌트 ≠ `iam` → `CREDENTIALS_INVALID`(없는 이메일과 같은 응답 · 같은 이벤트) |
| 콘솔 테넌트 상수 | `TenantContext.java:28-41` `CONSOLE_TENANT_ID = "iam"` | 자바 코드에 기존 상수 **없음**(SQL `V0015/V0024` 와 주석에만) → 상수 1개 신설. 근거: `PLATFORM_SCOPE_TENANT_ID` 와 같은 종류의 사실(예약 플랫폼 슬러그 — `multi-tenancy.md` 예약어) · 배포별 선택이 아니다. client id 로 조회해 파생하는 안은 client id 라는 다른 마법 문자열을 들여올 뿐이라 택하지 않았다 |
| 시작 client 판정 | `SavedRequestTenantResolver.java:79-95` `initiatingClientTenant` · `CredentialAuthenticationProvider.java:258-280` | 🔴 **결정이 가정한 `clientTenant == null` 이 운영에서는 도달하지 않았다** — `resolve()` 는 시작 client 가 없으면 `fan-platform` 을 **대신 넣는다**(`:135-148`). 그래서 `/login` 직접 방문은 «null → 교차 조회» 가 아니라 «fan-platform 범위 조회 → 폴백» 이었다(javadoc 의 «null when … no saved request» 는 요청 컨텍스트가 없을 때만 참). D 를 그대로 적용하면 직접 방문이 «fan-platform 소비자 client» 로 취급되어 fan-platform 밖 자격(콘솔 `iam` 운영자 포함)이 전부 거부된다 ⇒ 대체값을 넣지 않는 조회를 추가해 **결정이 말한 «시작 client 없음» 분기를 실제로 도달 가능하게** 했다. 🔴 **이것도 동작 변화다**: 직접 방문에서 이메일이 둘 이상의 테넌트에 있고 그중 하나가 fan-platform 이면, 전에는 fan-platform 자격이 이겼고 이제는 `LOGIN_TENANT_AMBIGUOUS` 로 거부된다. 한 건 일치(대부분)는 결과가 같다 |

**② 파일** — main 9(위 표 + 주석만 바꾼 `SasAuthorizationRevocationAdapter` · `OAuthAuthorizationRevocationPort` · `ForceLogoutUseCase`) · test 7(`CredentialAuthenticationProviderTest` · `SavedRequestTenantResolverTest` · `SasRefreshTokenAuthenticationProviderTest` · 신규 `AuthorizationServerConfigRefreshProviderTest` · `OAuth2RefreshTokenIntegrationTest` · 신규 `CrossTenantLoginRefreshIntegrationTest`) · spec/contract 5(`multi-tenancy.md` · `auth-service/data-model.md` · `auth-service/architecture.md` · `auth-api.md` · `auth-events.md`) · 교차 프로젝트 e2e 4(아래 ⑥).

**③ 테스트 (2026-09-26 UTC, 이 worktree)**
- `./gradlew :projects:iam-platform:apps:auth-service:test` → **rc=0, 833 / 실패 0 / 오류 0 / 건너뜀 28** (JUnit XML 합산, 109 클래스). 이 태스크는 `@Tag("integration")` 을 돌리지 않는다.
- `./gradlew :projects:iam-platform:apps:auth-service:integrationTest --tests "*CrossTenantLoginRefreshIntegrationTest" --tests "*OAuth2RefreshTokenIntegrationTest"` → rc=0, **13 / 13 건너뜀** — 로컬 Docker 없음(Testcontainers). ⚪ **AC-1·2·3 IT 는 CI 판정**이다 — 로컬 초록이 아니다.
- 새/바뀐 단위: `CredentialAuthenticationProviderTest` 29 · `SavedRequestTenantResolverTest` 6 · `SasRefreshTokenAuthenticationProviderTest` 17 · `AuthorizationServerConfigRefreshProviderTest` 3 — 전부 통과.

**④ bite (코드를 깨고 → 실패 확인 → 복원, `git diff` 로 복원 확인)**
1. 테넌트 비교를 `clientTenant` 로 되돌림 → 단위 **A**(콘솔 교차 세션 회전) · **B**(대조군 — 행이 client 테넌트면 통과해 버림) 실패.
2. 소비자 client 조기 거부 제거(`if (false && …)`) → `consumerClient_scopedMiss_noCrossTenantFallback` 실패(교차 조회가 돌았다).
3. `persistRotation` 에 `clientTenant` 전달 → 단위 **A**(회전 행 테넌트) · `missingMirrorRow_rotatedRowCarriesSessionTenant` 실패.
4. `removeIf` 주석 처리 → `removesBuiltInKeepsOursAndOtherGrants` 실패(가드가 refresh provider 2개를 보고 던짐).

**⑤ 로컬 대체 측정 — 실제 토큰 엔드포인트 (임시 H2 프로브, 커밋하지 않음)** — Docker 가 없어 IT 가 못 도는 대신, 기존 `OAuth2AuthorizationServerSliceTest` 와 같은 H2 부트 설정으로 임시 테스트를 만들어 `POST /oauth2/token` 을 직접 쳤다(미러 테이블 `jti` 를 255 로 늘려야 했다 — 아래 후속 ⑥). 비권위 측정이다(H2 · Flyway 없음).

| 케이스 | 이 변경 | bite ① 기본 provider 복원(`authenticationProviders(p -> {})`) | bite ② client 테넌트 비교 복원 |
|---|---|---|---|
| 미러 행 폐기 → refresh | **400** `invalid_grant` | **200** (CI run 36134528069 의 fall-through 재현) | 400 |
| 콘솔(iam) client · 로그인 테넌트 fan-platform → refresh ×2 | **200 · 200**, 첫 행 · 회전 행 모두 fan-platform | 200 · 200 | **400 · 400** (Failure Scenario 1 재현) |
| 폼 로그인(저장된 authorize 경유) fan-platform 전용 자격 → 콘솔 client | 로그인 성공 · refresh 200 · 회전 행 fan-platform | — | — |
| 같은 자격 → ecommerce client | **`/login?error`** | — | — |
| 같은 자격 → fan-platform client (대조군) | 로그인 성공 · refresh 200 | — | — |

**⑥ 교차 프로젝트 영향 — web-store nightly e2e** (`nightly-e2e.yml` 전용 레인이라 CI 초록으로는 안 보인다): `account-type-guard.spec.ts` 는 `'*'`(SUPER_ADMIN) 자격을 **web-store client 로** 로그인시켜 IAM 이 받아들이고 web-store 역할 가드가 튕기는 것(`/login?error=account_type_mismatch`)을 단언했다 — 그 «IAM 이 받아들인다» 가 **바로 D 가 막은 폴백**이다 ⇒ 그대로 두면 다음 nightly 가 빨강. 스펙을 «IAM 이 `/login?error` 로 거부 · web-store 로 돌아가지 않음» 으로 바꿨다(`helpers/auth.ts` `loginAndExpectIamRefusal` · 시드 SQL · `assert-specs-ran.mjs` 주석). ⚪ 로컬 미실행(풀스택 필요) — **머지 후 다음 nightly 를 한 번 확인할 것**. 대가: 이 레인은 더 이상 web-store 역할 가드를 실행하지 않는다(CUSTOMER 없는 스토어 토큰을 만들 경로가 아래 ⑧ 의 SSO 뿐).
콘솔 e2e(`'*'` · `acme-corp` 자격 → 콘솔 client)는 콘솔 폴백이 남으므로 영향 없음(코드 판독). 콘솔 BFF refresh(`session-refresh.ts:103-134`)는 공개 client `refresh_token` grant — IT (a) 와 같은 모양이고 400 은 기존 경로(1회 재시도 → `session_expired`)로 간다.

**⑦ 재사용 탐지는 SAS 경로에서 «회전된 토큰의 재제출» 로는 발동하지 않는다 (코드 판독 + IT 주석)** — `JdbcOAuth2AuthorizationService.findByToken(…, REFRESH_TOKEN)` 은 인가의 **현재** refresh 토큰 값만 찾는다 ⇒ 회전되어 사라진 토큰은 `authorization == null` → `invalid_grant` 로 **재사용 판정 전에** 끝난다. `IT @Order(4)` 의 400 은 재사용 분기가 아니다(BE-603 CORRECTION 이 이미 «우연한 일치» 로 지적). 재사용 분기(`handleReuseDetected`)에 닿는 것은 «SAS 는 아직 그 토큰을 쥐고 있는데 미러에는 자식이 있는» 상태 — **같은 토큰의 동시 refresh 두 개**가 남기는 상태뿐이다. AC-2 IT 는 그 상태를 직접 만든다.
🔴 **뜻 1 (플랫폼 MUST 미충족)**: `platform/service-types/identity-platform.md:88` «회전된 토큰이 다시 제시되면 가족 전체 폐기» — SAS 경로는 그 토큰을 거부만 하고 가족을 폐기하지 않는다(탈취된 옛 토큰의 재제출이 경보도 폐기도 없이 400). **이 티켓 이전부터**다.
🔴 **뜻 2 (이 변경이 키운 위험)**: 동시 refresh 경쟁(콘솔 다중 탭 — `TASK-MONO-674` § 2.6.1)이 재사용 분기에 닿으면 계정의 **모든** 미러 행이 폐기되고 `auth.token.reuse.detected`(security-service 자동 잠금 입력)가 나간다. 이벤트는 전에도 났지만, 전에는 폐기된 행이 fall-through 로 되살아났고 **이제는 그 계정의 모든 SAS 세션이 실제로 끝난다**. 경쟁 창은 좁다(패자가 승자 커밋 전에 인가를 읽고 커밋 후에 미러를 읽어야 한다) ⚪ 미측정.

**⑧ 🔴 결정이 코드에서 성립하지 않는 곳 — IAM 브라우저 세션 재사용(SSO)** — D 는 «소비자 client 에서 교차 테넌트 로그인 제거» 를 `resolveCredential` 에서 집행하는데, 그 함수는 **비밀번호를 받을 때만** 돈다. 이미 인증된 IAM 세션으로 다른 client 의 `/oauth2/authorize` 를 열면 SAS 는 자격 조회 없이 코드를 준다. **로컬 측정(⑤ 프로브)**: fan-platform 자격으로 콘솔 client 로그인 → 같은 세션으로 ecommerce client authorize → 코드 발급 → 토큰 `tenant_id=fan-platform`, `aud=ecommerce client`, `roles` 없음. 시작 client 없는 `/login` 직접 방문 세션도 같다. ⇒ 소비자 client 의 교차 테넌트 세션은 **여전히 만들어진다**(여전히 쓸모는 없다 — 역할 없음 · 게이트웨이 테넌트 차단). 그 세션의 refresh 는 이 변경으로 계속 된다(로그인 테넌트 일치). 막으려면 authorize 시점의 «principal 테넌트 ↔ client 테넌트» 게이트가 필요하다 — 새 결정이라 범위 밖.

**⑨ 이탈 · 판단** — (1) 소유자 결정 문구 «`clientTenant == null` 현행 유지» 는 ① 표의 이유로 **«시작 client 없음 = null 이 되도록 고치고 그 분기를 유지»** 로 구현했다. (2) 비밀번호 재설정은 **바꾸지 않았다** — UUID 키 미러 행만 폐기하므로 BE-603 이전의 이메일 키 행(배수 기간, 최대 30일)을 가진 세션은 재설정 뒤에도 refresh 된다(그 세션의 **다음 회전**부터는 UUID 키라 닿는다). IT 는 BE-603 이후 모양만 단언하고 그 칸을 단언하지 않았다(결함을 핀으로 박지 않음). 닫으려면 `ConfirmPasswordResetUseCase` 가 `OAuthAuthorizationRevocationPort` 도 부르면 된다(강제 로그아웃과 같은 모양) — 후속 후보.

**⑩ 후속 후보 (파일 만들지 않음 — 소유자 판단)**
1. 🔴 SSO 로 소비자 client 교차 테넌트 세션이 생긴다 — authorize 시점 테넌트 게이트(⑧).
2. 🔴 SAS 경로 재사용 탐지가 회전된 토큰의 재제출에 발동하지 않음(identity-platform MUST) + 동시 refresh 경쟁이 계정 전체 폐기·자동 잠금 입력이 되는 위험(⑦).
3. 소셜 로그인 테넌트 스탬프를 계정 테넌트로 맞출지(`SocialLoginBrowserController:185` — 하위 기본값 (나), `TASK-BE-602` 와 연결).
4. 비밀번호 재설정이 SAS 인가도 닫게(`OAuthAuthorizationRevocationPort`) — 배수 기간의 이메일 키 세션(⑨-2).
5. `RefreshTokenJpaEntity` 의 `jti`/`rotated_from` 길이 선언(36)이 Flyway `V0014`(255)와 어긋남 — Hibernate DDL 을 쓰는 H2 슬라이스에서 SAS 토큰 INSERT 가 깨진다(⑤ 에서 발견 · 운영 DB 는 Flyway 라 무해).
6. web-store 역할 가드(`account_type_mismatch`)의 e2e 커버리지 상실(⑥) — 필요하면 SSO 경로로 다시 구성(단, 1 을 고치면 그 경로도 사라진다).

---

## CORRECTION (2026-09-26 UTC) — PR #4033 CI `Integration (iam B)` 111 중 2 실패 (run 36222202640, job 108349543320)

**1. `CrossTenantLoginRefreshIntegrationTest` AC-3 (b) — `Expected exactly 0 requests matching GET /internal/accounts/…/status but received 2` → 테스트 결함, 코드 결함 아님.**
- 코드 판독: 소비자 client 범위 조회 실패는 `CredentialAuthenticationProvider.resolveCredential` 에서 `Lookup.failed` 로 끝나고, `authenticate` 는 `lookup.credential() == null` 이면 상태 조회(`lookupAccountStatus`) **전에** `BadCredentialsException` 을 던진다. 단위 `consumerClient_scopedMiss_noCrossTenantFallback` 이 `verifyNoInteractions(accountServicePort)` 로 이미 고정한다.
- 원인: WireMock 서버가 `static`(클래스 공유)이고 요청 저널이 테스트 사이에 초기화되지 않았다. JUnit 기본 메서드 순서에서 (a) · (c) 가 (b) 보다 먼저 돌았고, 각자 로그인 1회 = 상태 조회 1회 → 합계 2.
- 수정: `@BeforeEach` 에서 `accountService.resetRequests()`(스텁 유지, 저널만 비움). 단언은 그대로 두고, (c) 에 대조군 `verify(1, …/status)` 를 추가 — 자격을 찾으면 정확히 1회, 이 테스트 시작부터 센다는 것을 같은 저널로 보인다.

**2. `OAuth2RefreshTokenIntegrationTest` AC-2 비밀번호 재설정 — `ObjectOptimisticLockingFailureException … CredentialJpaEntity#71` → 🔴 운영 결함(이 티켓 이전부터).**
- 위치: `Credential.changePassword` 가 `version + 1` 을 돌려줬다(`domain/credentials/Credential.java`). `CredentialRepositoryImpl.save` → `CredentialJpaEntity.fromDomain`(버전 복사) → `save` = `merge`. Hibernate 는 detached 엔티티의 `@Version`(v+1)이 행(v)과 다르면 `StaleObjectStateException` 을 던진다 ⇒ **기존 자격의 비밀번호 변경(`ChangePasswordUseCase`) · 재설정 확인(`ConfirmPasswordResetUseCase`)은 항상 실패**했다. 픽스처 문제가 아니다 — 이 IT 가 처음으로 두 경로를 실제 JPA 저장소에 태웠다(다른 IT · e2e 에 호출자 0, grep).
- 로컬 재현(임시 H2 `@DataJpaTest`, 미커밋): 수정 전 `read → changePassword → save` = CI 와 **같은 메시지**의 `ObjectOptimisticLockingFailureException`. 수정 후 저장 성공 · DB 버전 0→1 · 같은 버전에서 읽은 두 번째 변경은 **여전히** 낙관적 락으로 거부(락 의미 보존).
- 수정: `changePassword` 는 읽은 버전을 **그대로** 싣는다 — 그것이 낙관적 락 토큰이고 증가는 `@Version` 이 한다. 단위 `CredentialTest`(3→3 · 0→0) · `ConfirmPasswordResetUseCaseTest` · `ChangePasswordUseCaseTest`(저장 버전 = 읽은 버전)로 갱신. 권위 테스트 = `CredentialJpaRepositoryTest`(Testcontainers MySQL) 2건 추가: 읽기→변경→저장 성공 + 버전 +1 · 오래된 버전의 동시 변경은 거부. ⚪ Docker 부재로 로컬 미실행 → CI 판정.
- 🔴 영향: `PATCH /api/auth/password` · `POST /api/auth/password-reset/confirm` 은 배포된 코드에서 기존 사용자에게 500(또는 매핑된 오류)이었을 것이다 — 운영 로그로 확인할 후속 후보.
