# Task ID

TASK-BE-604

# Status

ready

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

- [ ] **AC-0** — 위 ⓐ/ⓑ/ⓒ 소유자 결정(+ ⓑ 면 측정 결과).
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
  🔴 **다음 소유자 결정 대기 — 아래 선택지 A–D.**
- [ ] **AC-1** — IT: 미러 행 폐기 → 다음 refresh 400 `invalid_grant` (BE-603 IT `@Order(8)` 가 일부러 **단언하지 않은** 칸). 🔴 기존 fall-through 200 을 고정하는 단언을 만들지 마라.
- [ ] **AC-2** — IT: 비밀번호 재설정 후 기존 SAS 세션 refresh 거부 · 재사용 탐지 후 다른 세션 refresh 거부.
- [ ] **AC-3** — 계정 테넌트 ≠ client 테넌트 세션의 refresh 가 결정한 대로 동작(허용이면 성공, 차단이면 명시적 거부 + 계약 문서).

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

# Failure Scenarios

1. **기본 provider 만 제거한다** → 테넌트가 어긋난 세션이 전부 끊긴다(⚪ 규모 미측정).
2. **fall-through 200 을 단언하는 테스트를 넣는다** → 결함이 핀으로 얼어붙는다.
3. **force-logout 만 보고 «막힌다» 고 판정한다** → 그 경로만 BE-601 어댑터로 막혀 있다. 재설정·재사용 탐지는 별개다.
