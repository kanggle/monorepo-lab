# Task ID

TASK-BE-617

# Status

done

# Title

전역 소비자 계정 6단계 — 소셜 로그인을 **풀 계정** 규칙으로 (`ADR-MONO-078` A · D4)

# Owner

iam-platform

# Task Tags

- auth-service
- account-service
- oauth-social

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (소셜 자동 가입 경로 — 잘못되면 남의 풀 계정에 붙는다)
>
> 🟢 **보류 해제 (2026-10-05 UTC, 소유자 결정 «추천대로 진행»)** — 소유자가 Google · Naver 키를 발급해 SSM 에 **저장**했다(`TASK-MONO-763` § 실측). 아래 AC-00 의 글자(«배포 환경에 주입»)는 **아직 거짓**이다 — 키는 저장만 됐고 데모가 읽지 않는다. 해제 근거는 아래 «순서 규칙» 의 둘째 줄(«키를 넣으려는 작업이 보이면 이 티켓을 먼저 착수»): 키를 배포하는 `TASK-MONO-763` 이 기안됐고 그 머지가 이 티켓 뒤로 묶였다. ⇒ 이 티켓은 지금 착수할 수 있다. AC-00 칸은 763 재굽기 때 참이 된다.
>
> (옛 배너, 기록) ⏳ **DO NOT START — 보류(소유자 결정 2026-10-02 UTC «보류 + 작은 방어만»).** 날짜 조건이 아니다. 착수 전 **AC-00** 을 재서 참일 때만 시작하고, 거짓이면 이 파일 끝에 측정값과 날짜(UTC)를 덧붙이고 `ready/` 에 그대로 둔다.
>
> - [ ] **AC-00 (보류 게이트)** — 실제 소셜 제공자 키가 어느 배포 환경에 주입되었다: `OAUTH_{GOOGLE,KAKAO,MICROSOFT,NAVER}_CLIENT_ID` 중 하나가 데모·운영 설정(compose env · AMI 시드 · 비밀 저장소)에서 `application.yml` 의 `test-*-client-id` 기본값이 아닌 값을 받는다. 2026-10-02 측정: 어느 배포 설정에도 없음(기본값 = 가짜 → 소셜 로그인 성공 불가 · 소셜 신원 0개).
> - 보류 동안의 방어: `TASK-BE-620` — 풀 계정이 있는 이메일로 오는 사이트 소셜 가입을 거절(§ 2 공존 금지). 이 티켓이 소셜을 풀로 바꾸면 그 방어는 «풀-먼저 조회» 로 대체된다 — 그때 620 의 검사를 지울지 남길지 정한다.
> - 🔴🔴 **순서 규칙 (2026-10-05 UTC 추가 · `TASK-MONO-743` 종결의 대가)** — **실제 소셜 키를 배포 환경에 넣는 것은 이 티켓 머지 이후, 또는 이 티켓과 같은 PR 에서만.** 이유: 이 티켓 전에는 소셜 가입이 **사이트별 계정**을 만들고, 620 은 «같은 이메일의 풀 계정이 이미 있을 때» 만 막는다 ⇒ 풀 계정이 없는 사람이 팬·스토어에서 각각 소셜 가입하면 **따로 된 사이트 계정 둘**이 생긴다. 그 계정을 합칠 «묶기»(743)는 구현 없이 닫혔으므로, 그런 계정은 **생기지 않게 하는 것**이 유일한 방어다.
>   - 그래서 AC-00 이 «키가 들어왔다» 로 참이 되는 순간은 **이미 늦은 것**일 수 있다 — 키를 넣으려는 작업(소유자 · 다른 티켓)이 보이면 그 자리에서 이 티켓을 먼저 착수하도록 알린다. 키가 이 티켓보다 먼저 들어갔다면 착수 첫 단계로 AC-00′ 을 잰다:
>   - [ ] **AC-00′ (키가 먼저 들어간 경우만)** — 같은 `email` 이 `fan-platform` · `ecommerce` 두 테넌트에 모두 있는 **사이트 소셜 계정** 행 수(데모 시드 제외). 0 이 아니면 STOP — `TASK-MONO-743` 닫기 기록의 «되살리는 조건» ② 가 참이다(묶기 티켓을 새로 연다).

---

# Dependency Markers

- **선행**: `TASK-BE-614` · `TASK-BE-615` · `TASK-BE-616` (~~`TASK-MONO-743`~~ — 2026-10-02 보류 → **2026-10-05 구현 없이 종결**: 따로 된 계정의 모집단이 더는 생기지 않는다. 남은 구멍(소셜)은 위 «순서 규칙» 으로 막는다)

# Goal

소셜 로그인으로 오는 소비자도 풀 계정으로 로그인하고 사이트를 오갈 때 같은 규칙(첫 방문 동의 · 재입력 없음)을 타게 한다. 기존 테넌트별 소셜 신원은 **본인 확인으로만** 묶는다(`multi-tenancy.md:373` 소유자 결정 유지).

# Scope

## In Scope

- `OAuthLoginUseCase` · `SocialSignupUseCase` — 소비자 client 의 새 소셜 가입은 풀로
- `social_identities` 의 풀 단위 저장(`TASK-MONO-742` 저장 모양)

## Out of Scope

- 소셜 제공자 추가

# Acceptance Criteria

- [x] **AC-1** — 새 소셜 사용자: 팬에서 가입 → 스토어 이동 시 동의 화면만, 재로그인 없음.
- [x] **AC-2** — 🔴 **대조군**: 소셜 제공자가 준 이메일이 기존 **비밀번호 풀 계정**의 이메일과 같아도 자동으로 붙지 않는다(D2) — 지금 `SocialSignupUseCase` 의 «그 테넌트에서 이메일로 찾으면 연결» 동작(`SocialSignupUseCase.java:30-66`)이 풀에서 그대로 쓰이면 안 된다.
- [x] **AC-3** — 기존 테넌트별 소셜 신원은 그대로 동작한다(묶기 전).

# Related Specs

- `projects/iam-platform/specs/features/oauth-social-login.md`
- `projects/iam-platform/specs/features/multi-tenancy.md` § 소셜

# Related Contracts

- `platform/contracts/jwt-standard-claims.md`(`TASK-MONO-742` 갱신본)

# Edge Cases

- 같은 사람이 비밀번호 풀 계정과 소셜 신원을 둘 다 가지려는 경우 — § 2 의 공존 금지대로 거절된다. 묶기(`TASK-MONO-743`)는 2026-10-05 구현 없이 종결됐다 — 이 경우가 실제로 문제가 되면 묶기 티켓을 새로 연다(743 닫기 기록 «되살리는 조건»).

# Failure Scenarios

1. 소셜 이메일 일치로 기존 풀 계정에 자동 연결 — 이메일을 검증하지 않는 제공자 경유 탈취.

---

## 인계 (TASK-BE-618, 2026-10-02 UTC)

- `TASK-BE-618` 의 한 사이트 계정 이동기(`POST /internal/consumer-pool/legacy-moves`, account-service →
  `POST /internal/auth/consumer-pool/moves`, auth-service)는 **`social_identities` 행이 하나라도 있는 계정을 통째로 건너뛴다**
  (auth 의 `409 POOL_MOVE_SOCIAL_LINKED` → 실행 보고서 `skipped.SOCIAL_LINKED`). 그 계정들은 지금 **사이트 계정 그대로**다.
  이유: 지금 소셜 로그인은 `(사이트 테넌트, provider, provider_user_id)` 로 신원을 찾는다 — 신원 행을 풀로 옮기면 그 조회가 비어 «새 소셜 가입» 으로 가고,
  그 가입은 같은 이메일의 (방금 옮긴) 풀 계정에 막혀 그 사람의 소셜 로그인이 끊긴다(618 착수 시 정정 ②).
- 이 티켓은 둘 중 하나를 **정하고 적어야** 한다:
  1. 소셜 조회를 풀-먼저로 바꾼 뒤 **같은 이동기**로 그 계정들을 옮긴다 — auth 이동 엔드포인트의 판정 4(`SOCIAL_LINKED`)를 «신원 행도 같이 옮긴다» 로 바꾸고,
     그 판정을 바꾼 시험이 이 티켓의 것이 된다. 또는
  2. 그 계정들은 사이트 계정으로 **남는다**고 결정한다(그 경우 § 2 의 «사이트 계정 이메일로 풀 가입 거절» 이 그 이메일들에 계속 걸린다).
- 어느 쪽이든 **AC-3**(«기존 테넌트별 소셜 신원은 그대로 동작한다») 은 여전히 지켜져야 한다 — 이동기가 건너뛴 계정의 소셜 로그인이 이 티켓 뒤에도 같은 계정으로 들어가는지가 그 대조군이다.

---

# 구현 기록 (2026-10-05 UTC, 구현자 — 분석=Opus 5.5 / 구현=Opus 5.5)

> 🔵 날짜 주의: 위 배너의 «2026-10-05 UTC» 는 착수 시각 `date -u` = **2026-10-05** 와 맞지 않는다(KST 날짜로 보인다). 이 절의 날짜는 `date -u` 다.

## 착수 게이트

- **AC-00** — 여전히 **거짓**(배너 그대로: 키는 SSM 에 저장만, 배포 설정이 읽지 않음). 착수 근거는 배너의 «순서 규칙» 둘째 줄(키 배포 `TASK-MONO-763` 이 이 티켓 뒤로 묶임).
- **AC-00′** — **해당 없음**: «키가 이 티켓보다 먼저 들어간 경우만» 의 조건이 거짓이다(배포 환경에 키 없음 → 사이트 소셜 계정이 생길 수 없었다). 재지 않았다.

## 바뀐 것

**계약·스펙 (먼저)**: `specs/contracts/http/internal/auth-to-account-social.md`(동작 상세를 순서표로 · 풀 가입 3.1–3.3 · 응답 **추가 필드 `tenantId`** · 409 조건 확장) ·
`specs/features/oauth-social-login.md`(§ 계정 연결 전략 0 = 풀-먼저 · 3.b 풀 가입과 «이메일로 풀 계정에 붙지 않는다» · 세션 details · 업무 규칙 · 엣지 · 대가) ·
`specs/features/multi-tenancy.md`(§ 소셜 로그인 문단 · § 2 소셜 줄 · § 3 표 `social_identities` 행 = 618 인계 결정 · § 7 시험 자리 2줄) ·
`specs/contracts/http/internal/account-maintenance-internal.md` · `auth-internal.md`(`SOCIAL_LINKED` = 사이트 계정으로 남는다) · `specs/services/auth-service/data-model.md`(`social_identities` 풀 행).

**account-service**: `SocialSignupUseCase` — 같은 사이트 연결(기존, 맨 앞, 무변경) 다음에 `ConsumerAccountPool#signupGoesToPool`(폼 가입과 같은 술어)이면 `signUpIntoPool`:
① `refuseIfEmailHasPoolAccount`(풀 이메일 → 409, **연결하지 않는다**) ② `refuseIfEmailHasSiteAccount`(다른 소비자 사이트의 사이트 계정 → 409) ③ 풀 계정 · 신원 mint(풀) · 프로필 ·
`joinOnSignup` 멤버십 · `account.created`(사이트) — UNIQUE 경합도 409(경합한 풀 계정을 돌려주지 않는다). 그 밖(플래그 꺼짐 · B2B)은 이전 줄 그대로.
`SocialSignupResult` · `SocialSignupResponse` 에 `tenantId`(계정 행의 테넌트).

**auth-service**: `SocialIdentityRepository#findPoolIdentity`(default — `CredentialRepository#findPoolCredentialByEmail` 와 같은 모양) ·
`OAuthLoginUseCase` — `poolIdentityFor`(폼 로그인 `poolCredentialFor` 와 같은 판정: `poolPrincipalMapsTo` → 풀 신원 있음 → `getConsumerSiteMembership(...).consumerSite()`, 실패 = fail-closed)
→ 없으면 client 테넌트 신원(기존) → 없으면 `socialSignup`; 응답 `tenantId = consumer-pool` ∧ `poolPrincipalMapsTo(client)` 일 때만 풀 계정. 신원 행 upsert 테넌트 = 풀 계정이면 `consumer-pool`, 아니면 client 테넌트(기존).
`SocialSignupResult` 에 `tenantId` + `poolAccount()`(3-인자 생성자 유지) · `BrowserLoginResolution` 에 `poolAccount`(3-인자 유지) ·
`SocialLoginBrowserController` — 풀 계정이면 details `tenant_id = consumer-pool`(폼 로그인의 풀 principal 과 같은 모양 → 토큰 · SSO 게이트 · refresh 가 `AuthorizationSessionTenant` 하나로 사이트에 사상).
마이그레이션 없음.

## 🔴 결정 — `TASK-BE-620` 의 검사: **남긴다 (지우지 않는다)**

- 풀 경로에서 620 의 술어(`refuseIfEmailHasPoolAccount`)가 **곧 AC-2 대조군**이다. 풀-먼저 조회(auth-service)는 «이 제공자 신원이 풀에 있나» 만 답하고, 없을 때 오는 가입은
  «같은 이메일의 풀 계정이 이미 있다» 를 따로 물어야 한다 — 이 술어를 지우면 남는 선택지는 «그 계정에 붙인다»(D2 위반) 또는 «UNIQUE 위반 → 경합 경로 → 그 계정을 돌려준다»(옛 경합 처리, 같은 D2 위반) 뿐이다.
- 플래그 꺼짐 경로에서는 620 그대로의 방어다(풀 계정은 플래그를 켰다 끈 뒤에도 남을 수 있다).
- 배너의 «풀-먼저 조회로 대체된다» 는 절반만 맞았다: **신원** 쪽은 풀-먼저가 맡고, **이메일** 쪽은 620 이 계속 맡는다.

## 🔴 결정 — 618 인계: **선택지 2 — 사이트 계정으로 남긴다** (이동기 판정 4 `SOCIAL_LINKED` 무변경)

- 모집단 = 0 이 근거다: 실제 소셜 키는 어느 배포에도 들어간 적이 없고(620 착수 측정 · 이 티켓의 AC-00 거짓), 이 티켓 뒤 소비자 사이트의 새 소셜 가입은 풀로 태어난다(플래그 켜짐 — 데모 기본).
  옮길 대상이 없는 이동 코드를 두 서비스에 걸쳐 바꾸는 것(선택지 1)은 «아무것도 옮기지 않는 이동기 변경» 이다.
- AC-3 은 그대로 지켜진다 — 사이트 신원은 풀-먼저 조회가 비면 기존 client 테넌트 조회로 그 사이트 계정에 간다(시험: 아래 AC-3 칸).
- 대가: 그 이메일들에는 § 2 의 «사이트 계정 이메일로 풀 가입 거절» 이 계속 걸린다. 남는 생성 경로 하나 — 풀 플래그가 켜진 뒤 **이동기가 아직 돌지 않은** 한 사이트 비밀번호 계정의 주인이
  같은 이메일로 소셜 로그인하면 기존 «같은 사이트 이메일 연결» 로 사이트 신원이 붙어 그 계정이 `SOCIAL_LINKED` 가 된다(이동 대상에서 영구 제외). ⚪ 데모에서 이 창이 열리는지는 재지 않았다 — 데모 호스트는 재굽기마다 신선 볼륨 + 시드(ADR-MONO-078 CORRECTION 2026-10-05)라 078 이전 사이트 계정이 쌓이지 않는다는 것이 기록된 사실의 전부다.
- **되살리는 조건**: `SOCIAL_LINKED` 로 건너뛴 계정 수가 0 이 아닌 것이 실측될 때 — 판정 4 를 «신원 행도 같이 옮긴다» 로 바꾸는 티켓을 연다(정본: `multi-tenancy.md` § 3 표).

## 구현자 선택 (리뷰어가 줄 단위로 볼 곳)

| # | 선택 | 이유 |
|---|---|---|
| D-1 | **같은 사이트의 이메일 연결은 풀 모드에서도 남긴다**(사이트 계정 한정) | ADR-MONO-078 D2 «묶기 전에는 지금처럼 동작한다 — 나빠지는 사람은 없다». AC-2 는 **풀** 계정을 말한다. 이 연결은 078 이전부터 있던 테넌트 안 동작이고(`oauth-social-login.md` 3.a), 바꾸면 사이트 계정 주인의 소셜 로그인이 «거절» 로 바뀐다. 🔴 같은 D2 위험(이메일 미검증 제공자)이 사이트 계정에는 **그대로 남는다** — 새로 생긴 것은 아니다 |
| D-2 | 계정이 어디 태어났는지는 social-signup 응답의 **추가 필드 `tenantId`** 로 안다 | `status-with-tenant` 조회 결과로 대신할 수 있었지만 그 조회는 404 를 «진행» 으로 읽는다 — 그 경우 풀 계정의 신원 행이 사이트 테넌트에 써진다. 가입한 쪽이 답하게 했다(계약 추가 필드, 기존 필드 무변경, 이전 account-service 는 필드 없음 = 이전 동작) |
| D-3 | 풀 principal 의 `tenant_type` = client 의 값 | 풀 신원은 소비자 사이트(`B2C_CONSUMER`)에서만 풀린다. 폼 로그인은 `resolve("consumer-pool")` = `B2C_CONSUMER`(614 D-1) — 같은 값. 발급은 풀 principal 이면 이 값을 쓰지 않는다(사이트의 권위 값) |
| D-4 | 응답이 풀이라 해도 client 가 풀 사상 대상이 아니면(콘솔) 풀 principal 로 세우지 않는다 | account-service 는 `iam` 가입을 404 로 막지만, 방어를 한 서비스에만 두지 않는다 |
| D-5 | 오류 문구는 그대로(«이미 이메일·비밀번호로 가입된 주소») | 새 열거 경로 · 새 문구를 만들지 않는다(§ 2 마지막 문장). 🔴 소셜 전용 풀 계정(구글 가입) 주인이 같은 이메일의 **다른 제공자**로 오면 이 문구는 정확하지 않다 — 풀 계정에 이메일로 두 번째 제공자를 붙이는 길이 없다(로그인 후 연결 흐름 없음). 필요해지면 «본인 확인 후 연결» 티켓 |

## 발견 (고치지 않음 — 범위 밖)

- auth-service `SocialSignupResult` 는 `accountStatus` · `newAccount` 를 읽는데 account-service 응답은 `status` 를 보내고 `newAccount` 는 보내지 않는다(201/200 로 구분) —
  운영에서 `isNewAccount` 는 **언제나 false**, `accountStatus` 는 null 이다. 지금 쓰는 곳은 로그 한 줄뿐(상태 판정은 `status-with-tenant` 가 한다)이라 사용자 영향은 없다.
  `SocialLoginSasBrowserIntegrationTest` 의 WireMock 스텁이 auth 쪽 이름(`accountStatus`/`newAccount`)으로 응답해 이 불일치를 가린다.

## 시험

| AC | 단위 (로컬 실행 ✅) | 통합 (작성 · ⚪ 로컬 미실행) |
|---|---|---|
| AC-1 | account `SocialSignupUseCaseConsumerPoolTest#fanSocialSignup_bornInPool` · auth `OAuthLoginUseCaseConsumerPoolTest#newFanSocialSignup_poolAccount_identityRowInPool` · `#poolIdentity_onTheOtherSite_sameAccount_noSignup` · `SocialLoginBrowserControllerTest#callback_poolAccount_establishesPoolPrincipal` · `AccountServiceClientSocialSignupConflictTest#poolResponse_tenantIdRead` | auth `ConsumerPoolSocialLoginIntegrationTest#newFanSocialSignup_poolPrincipal_storeShowsConsentNotLogin`(팬 토큰 sub=풀 · tenant_id=fan-platform → 같은 세션 스토어 = `/consent`, `/login` 아님) · `#returningPoolIdentity_onStore_sameAccount` · account `ConsumerPoolSocialSignupIntegrationTest#fanSocialSignup_bornInPool` |
| 🔴 AC-2 | account `#passwordPoolAccountEmail_refused_neverLinked` · `#uniqueRace_refused_notLinkedToTheRacedPoolAccount` · auth `#passwordPoolAccountEmail_refused_noIdentityRow` | account `#passwordPoolAccountEmail_refused_notLinked`(MySQL — 행 1 · 그 풀 계정 멤버십 무변경) · auth `#socialEmailOfPasswordPoolAccount_notLinked`(`/login?error=email_registered` · 신원 행 0 · 재 authorize = `/login`) |
| AC-3 | account `#sameSiteAccount_stillLinks_poolPathNotTaken` · auth `#preDecisionSiteIdentity_stillResolvesToItsSiteAccount` · `#signupLinkedToSiteAccount_isNotAPoolPrincipal` | auth `#preDecisionSiteIdentity_unchanged` · account `#sameSiteLegacyAccount_stillLinked` |
| 경계 | B2B client 는 풀 신원을 안 본다 · 콘솔은 풀 principal 이 안 된다 · 소비자 사이트 판정 실패 = fail-closed · 플래그 꺼짐 = 이전 동작(620 유지) · B2B 가입 = 테넌트별 | — |

**bite (직접, 각각 손으로 되돌림 — `git checkout` 안 씀)**:
1. 🔴 AC-2 대조군 — 풀 경로의 `refuseIfEmailHasPoolAccount` 를 «풀에서 이메일로 찾으면 그 계정을 돌려준다»(옛 연결의 이식)로 바꿈 → `SocialSignupUseCaseConsumerPoolTest` **7 중 3 실패**
   (대조군 `passwordPoolAccountEmail_refused_neverLinked` 포함). 되돌린 뒤 7/7.
2. 경합 경로 — 풀 경로의 `catch (DataIntegrityViolationException)` 를 «경합한 풀 계정 반환» 으로 → **7 중 1 실패**(`uniqueRace_refused_notLinkedToTheRacedPoolAccount`). 되돌림.
3. 풀-먼저 조회를 끔(`poolIdentityFor` 가 항상 빈 값) + 컨트롤러가 풀 details 를 안 씀 → auth 두 클래스 **22 중 6 실패**(풀 신원 재방문 · 풀 principal details 칸 포함). 되돌림.
   되돌린 뒤 `BITE` 문자열이 소스에 남지 않은 것을 grep 으로 확인.

## 게이트 (각각 단독 실행 · `cmd > log 2>&1; echo rc=$?`)

| 명령 | rc | 비고 |
|---|---|---|
| `./gradlew :…:auth-service:test :…:account-service:test --continue` | 0 | 결과 XML auth 130 · account 105, 실패 0 (auth 1034 · account 690 칸, skipped 33 · 47 = Docker 없는 JPA 슬라이스). 첫 실행은 실제 실행, bite 를 되돌린 뒤의 재실행은 같은 입력이라 빌드 캐시(`FROM-CACHE`) |
| `./gradlew :…:auth-service:check :…:account-service:check --continue` | 0 | |
| 새 통합 시험 2 클래스 | ⚪ 못 돌렸다 | 이 호스트에 Docker 없음 — `@Tag("integration")` 은 `test` 가 제외하고, `integrationTest` 는 Testcontainers 가 뜨지 않는다. **컴파일만 됐다.** CI `iam-integration-tests` 가 첫 실측이다 |

## AC (머지 전 상태)

- 🟡 **AC-1** — 단위 ✅. 브라우저 경로 «팬 가입 → 스토어 = 동의 화면만» 은 IT 작성 · 로컬 미실행(CI). 라이브는 키 배포(`TASK-MONO-763`) 뒤에만 잴 수 있다.
- 🟡 **AC-2** — 단위 ✅ + bite ✅. DB 수준 · 브라우저 수준 IT 작성 · 로컬 미실행(CI).
- 🟡 **AC-3** — 단위 ✅. IT 작성 · 로컬 미실행(CI).

---

## review 이관 (2026-10-06 UTC)

- impl PR #4165 · 스쿼시 `71b66135c` (2026-10-05T20:15:50Z 머지 · 머지 시점 실패 0, 장애로 취소된 2 job 재실행 SUCCESS) · main 재실행: CI ✅ · Nightly ✅(1회차 finance jar 손상 = 장애 뒤 재실행 산출물 문제, 전체 재실행 SUCCESS).
- AC-1 · AC-2 · AC-3 ✅ — 단위 + CI 통합(`Integration (iam A/B)` SUCCESS, 새 IT 2개 포함). 🔴 병렬로 만든 `TASK-BE-623` 과 합칠 때 이 티켓의 `ConsumerPoolSocialLoginIntegrationTest` 가 623 판정(test-* = 미설정)에 막혀 4/145 실패 → 623 PR 에서 google 키 덮어쓰기를 넣어 해소(#4166 `b7093adfc`).
- ⏳ 라이브 = `TASK-MONO-763` AC-4(재굽기 뒤 창: Google 가입 → 팬 → 스토어 재로그인 없음). 그 창 판정 뒤 done.
- 🔵 범위 밖 발견(이 티켓이 기록): auth-service `SocialSignupResult` 가 응답 필드 이름을 잘못 읽어 `isNewAccount` 가 운영에서 늘 false(로그 한 줄만 영향).

---

## 23차 창 (2026-10-06 UTC)

- ⏳ 라이브(소셜 가입 → 풀 계정 · 사이트 이동 시 재로그인 없음)는 미측정 — 소유자 계정이 필요한데 창이 08:55Z 상한으로 닫혔다. 이 창에 이 티켓의 코드는 실렸다(23차 AMI `d44dd0d61`, 인스턴스 클론 `d44dd0d6` 확인). 🔵 같은 창에서 이메일 가입 계정은 `consumer-pool` 에 생겼다(IAM `account_db.accounts` 테넌트 분포 consumer-pool 8) — 소셜 경로의 증거는 아니다. 다음 창.

---

## 23차 창 라이브 + done 이관 (2026-10-06 UTC)

- 창: 2026-10-06 UTC 12:08:55Z `/bundle/start {fan, store}`(대상 `i-036521b58c68566f2`, 23차 AMI `d44dd0d61`, 소유자 승인 «둘다해») → 12:22Z ready → 13:45:45Z 정지(제어 API · EC2 `stopped`, 예산 629/1800).
- 소유자가 직접 실행(시크릿 창): **Google** — 팬 «IAM 로그인» → 로그인 화면 버튼 Google · Naver 둘뿐 → Google 계정 선택 → 팬 로그인 상태 → 같은 창 스토어 «로그인» = 비밀번호·Google 재선택 없이 통과(소유자 «완료»). **Naver** — 새 시크릿 창, 멤버 등록 계정으로 로그인(소유자 «네이버도 돼»).
- DB(`iam-mysql`, 값 미출력): `auth_db.social_identities` 0 → `GOOGLE · consumer-pool · 13:15:34Z` → `NAVER · consumer-pool · 13:25:37Z`, 각 연결 계정 `account_db.accounts.tenant_id = consumer-pool`(풀 계정 8 → 9 → 10, ecommerce 1 불변 — 사이트별 계정 0). auth-service: `authorize: pool session on consumer site ecommerce (client ecommerce-web-store-client) without a membership — first-visit consent (TASK-BE-616)` 13:16:07Z.
- 🔵 같은 소유자의 Google · Naver 는 **별개의 풀 계정 두 개**가 됐다 — 617 «이메일로 풀 계정에 붙지 않는다» 규칙대로(결함 아님).
- ⇒ 라이브 ✅: 소셜 가입은 풀 계정 · 팬 → 스토어 이동 시 재로그인 없이 첫 방문 동의만.
- 4-dim: (a) #4165 MERGED (b) `71b66135c` ∈ origin/main (c) 머지 시점 실패 0 (SUCCESS 17) (d) AC 섹션 열린 칸 0 + 위 라이브.
