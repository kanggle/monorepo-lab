# Task ID

TASK-BE-600

# Status

review

# Title

🔴 잠긴 계정도 폼 로그인을 통과한다 — BE-398 이 상태 검사가 있던 유일한 경로를 걷어냈다

# Owner

iam-platform

# Task Tags

- auth-service
- security
- regression

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 — 인증 경로의 거부 규칙과 응답 모양(계정 열거 방지)이 걸린 보안 수정.

---

# Goal

`TASK-BE-599` 를 구현하던 중(2026-09-25) 드러났다: 관리자 잠금이든 security-service 자동 잠금이든 **`account_db.accounts.status=LOCKED`
가 된 계정이 비밀번호 로그인을 그대로 통과한다.** 잠금이 비밀번호 로그인 앞에서 아무 일도 하지 않는다. DORMANT · DELETED 도 같은 경로로
통과할 수 있다(미측정).

## 실측 (2026-09-25 UTC · 저장소만)

| 사실 | 근거 |
|---|---|
| 폼 로그인 provider 는 계정 상태를 **조회하지 않는다** | `auth-service/…/infrastructure/security/CredentialAuthenticationProvider.java` — `status`·`LOCKED`·`AccountStatus` grep 0 · 자격 확인 → 비밀번호 검증 → `tenantType` 해석 → 인증 토큰 |
| 옛 JSON 경로는 막았다 | `LoginUseCase.java:91-95` `accountServicePort.getAccountStatus(accountId)` → `:222-229` `checkAccountStatus` 가 LOCKED 면 `AccountLockedException`(→ 423 `ACCOUNT_LOCKED`, `AuthExceptionHandler.java:105-107`) |
| 그 경로는 이제 없다 | BE-398 이 `POST /api/auth/login` 을 제거 ⇒ `LoginUseCase` 호출자 0. BE-309 의 provider 는 «minimal credential check» 로 설계되며 상태 검사를 옮기지 않았다(`tasks/done/TASK-BE-309-…:94`) |
| 소셜 로그인은 막는다 | `SocialLoginSteps.java:69-75` `checkAccountStatus` — LOCKED → `AccountLockedException` · DORMANT · DELETED → `AccountStatusException` (`OAuthLoginUseCase.java:199` 가 상태를 조회) ⇒ **같은 계정이 소셜로는 막히고 비밀번호로는 들어간다** |
| 잠금 쪽은 돈다 | 2026-09-25 데모 창: `auth.token.reuse.detected` → security-service → account-service `/lock` → `status=LOCKED` 결과 상태 PASS(`TASK-MONO-672` 항목 15 ②) — **잠그는 데는 성공하는데 잠금이 효력이 없다** |
| ⚪ 미측정 | ① SAS `refresh_token` grant(`RefreshTokenUseCase`)가 상태를 보는가 — application 패키지 grep 에 호출 없음, 즉 **이미 받은 세션이 잠금 뒤에도 갱신될 수 있다**는 추정 ② DELETED 계정의 자격 행이 남아 있는가(남아 있으면 삭제된 계정도 로그인) ③ 콘솔 운영자(`demo-operator`) 등 다른 테넌트 유형에서도 같은가 |

🔴 **`TASK-BE-599` 와의 관계**: BE-599 가 로그인 이벤트를 되살리면 VELOCITY 자동 잠금이 입력을 받는다. 소유자 결정(2026-09-25)으로 데모에서만
임계를 올려 공유 계정이 잠기지 않게 했다. **이 티켓이 잠금을 효력 있게 만들면, 데모 완화가 없는 환경에서는 공유 계정이 실제로 막힌다** —
그래서 데모 완화(BE-599)는 이 티켓보다 **먼저** 머지돼야 한다 — 🟢 **충족**(#4016 squash `64f1ae516`, `infra/demo/iam-traefik.override.yml`
security-service `DETECT_VELOCITY_THRESHOLD: "1000000"`). 🔴 단 **데모 AMI 에는 아직 없다**(오버레이는 구워지는 클론) — 이 티켓의 수정이
재굽기로 데모에 닿을 때 그 오버레이도 같은 굽기에 들어가야 한다. 같은 커밋 범위라 자연히 함께 들어가지만, 굽기 전에 확인하라.
🔵 참고: #4016 의 squash 제목은 «…와 디바이스 세션을 되살린다» 로 남았지만 **실제 머지 내용은 ⓐ 이벤트만**이다(소유자 재결정으로 ⓒ 철회 —
PR 제목만 1차 그대로 남았다). 판단은 제목이 아니라 `LoginEventRecorder` 의 의존(= `AuthEventPublisher` 하나)으로 하라.

# Scope

## 포함

- 폼 로그인에서 계정 상태를 확인해 ACTIVE 가 아니면 거부한다. 규칙은 **소셜 로그인과 같은 것**을 쓴다(`SocialLoginSteps.checkAccountStatus` 공유 — 두 경로가 다른 규칙을 들지 않게).
- ⚪ ① 을 재고, 갱신 경로도 막아야 하면 이 티켓에 포함(같은 결함의 둘째 입구). 범위가 커지면 AC-0 에서 분리 결정.

## 제외

- 잠금 해제 UX · 잠금 알림 메일.
- security-service 규칙 조정.

# Acceptance Criteria

- [x] **AC-0** — ⚪ 셋(갱신 경로 · DELETED 자격 행 · 다른 테넌트 유형)을 코드로 재고 범위를 확정한다. 🔴 갱신 경로가 뚫려 있으면 «로그인만 막고 끝» 은 잠금의 절반이다.
      → **측정 결과(2026-09-25 UTC, 코드 읽기)** — 아래 § AC-0 측정. 요약: ① 갱신 경로는 **뚫려 있다**(상태 조회 0) → **후속으로 분리**(이 티켓은 로그인만 막는다 — 이유는 § AC-0 ①) ·
      ② DELETED 계정의 자격 행은 **남는다**(삭제 경로 0) → 이 티켓의 규칙이 막는다 · ③ 다른 테넌트: 🔴 **새로 발견된 둘째 구멍** — 상태 조회
      엔드포인트가 `fan-platform` 에 고정돼 있어 그 밖의 계정(데모 스토어 `demo@demo.com` 포함)은 404 로 보였다 → **이 티켓에 포함**(헤더 추가) ·
      콘솔 운영자(테넌트 `iam`)는 계정 행이 **설계상 없다** → 404 = «규칙 미적용» 으로 정했다.
- [x] **AC-1** — 🔴 **응답 모양 결정**: 폼 로그인은 지금 모든 실패를 `/login?error` 로 돌려 계정 존재를 숨긴다. 잠긴 계정에 «잠겼다» 를 보여 주면
      비밀번호가 **맞았다**는 사실이 새어 나간다(잠긴 계정의 비밀번호 확인 오라클). ⇒ 선택지: ⓐ 같은 `/login?error` (정보 없음) ·
      ⓑ 비밀번호가 맞은 경우에만 «잠김» 안내(오라클 수용) · ⓒ 비밀번호 확인 **전에** 상태로 거부(항상 «잠김» — 존재 여부는 새지만 비밀번호 정답은 안 샘).
      🔴 **소유자 결정**. JSON 경로는 423 `ACCOUNT_LOCKED` 를 냈다(API 였고 폼이 아니다 — 그대로 가져오지 마라).
      → 🟢 **소유자 결정 (2026-09-25) = ⓐ**: «LOCKED / DORMANT / DELETED (ACTIVE 가 아닌 모든) 계정의 폼 로그인은 오답 비밀번호와 **정확히 같은
      결과**를 받는다: `/login?error`, 같은 문구, 힌트 없음. 비밀번호 정답 오라클 없음, 열거 없음. 흐름의 어디서 검사할지(비밀번호 검증 전/후)는
      타이밍·모양도 새지 않도록 정하고 근거를 적는다.»
      → **구현된 위치와 근거**: 자격을 찾으면 **① 상태 조회 → ② 비밀번호 검증(상태와 무관하게 항상) → ③ 비밀번호 판정 → ④ 상태 판정**.
      찾은 자격은 상태 · 비밀번호 정답 여부와 무관하게 같은 두 비용(account-service 조회 1회 + Argon2 검증 1회)을 치른다. 검증 **뒤** 조회는
      «비밀번호가 맞았을 때만» 조회 왕복이 붙어 **정답 타이밍 오라클**, 상태로 **먼저 끊기**(ⓒ 모양)는 비활성 계정만 해시를 건너뛰어 **상태 타이밍
      오라클**이 된다. 비밀번호가 틀리면 상태가 무엇이든 `CREDENTIALS_INVALID`. 응답은 같은 예외 · 같은 메시지(`BadCredentialsException("Invalid
      credentials")`) — 테스트가 오답 비밀번호 결과를 **실제로 만들어** 클래스 · 메시지 · cause 를 비교한다(`assertRejectedLikeWrongPassword`).
      (없는 이메일은 두 비용을 모두 치르지 않는다 — 계정 존재의 타이밍 차이는 BE-600 이전부터 있던 것이고 이 결정의 범위 밖이다.)
- [x] **AC-2** — 구현 + 단위 테스트: ACTIVE 통과 · LOCKED · DORMANT · DELETED 각각 거부 · account-service 조회 실패 시 동작(🔴 fail-closed 인가 fail-open 인가를 결정하고 테스트로 박는다 — 소셜 경로의 현재 동작과 맞춘다).
      → 🟢 **소유자 결정 (2026-09-25) = 조회 실패는 폼 · 소셜 둘 다 fail-CLOSED.** «소셜 경로의 "empty" 는 (i) account-service 실패/타임아웃과
      (ii) 정당한 "아직 계정 없음 / 404" 를 뒤섞고 있을 수 있다. (i) 만 fail-closed 가 돼야 하고 소셜 가입 · 첫 소셜 로그인을 깨면 안 된다.»
      → **바꾼 옛 의미 (BE-063)**: `AccountServiceClient.getAccountStatus` 는 404 · **그 밖의 4xx** · **body/`status` 없는 200** 을 모두
      `Optional.empty()` 로 돌려줬고(5xx · 타임아웃 · circuit-open 만 예외), 소셜 경로는 empty 를 «조회 불가 → 상태 검사 생략» 으로 읽었다
      (`OAuthLoginUseCase` 주석 · `SocialIdentityPersistStep` `accountStatus.ifPresent(...)`). 즉 401/403(깨진 워크로드 토큰)이나 읽을 수 없는 응답이면
      **잠긴 계정이 소셜로도 통과**했다. → **구분 도입**: empty = **404 만**. 그 밖의 4xx · 읽을 수 없는 200 은 `AccountServiceUnavailableException`
      (5xx 와 같은 부류) — 포트 javadoc 이 계약이다. 404 가 empty 로 남는 이유: 첫 소셜 로그인은 `socialSignup` 이 계정을 **먼저** 만든 뒤라
      상태 조회가 200 이고, BE-507 이후 `ecommerce` 에서 태어난 소셜 계정은 fan-platform 고정 조회에서 404 다 — 이걸 실패로 읽으면 스토어의 소셜
      로그인이 전부 막힌다. 폼 경로도 같은 이유 + 콘솔 운영자(계정 행 없음) 때문에 404 = 규칙 미적용.
      → **하나의 규칙**: `application/AccountStatusRule.enforce` — `SocialLoginSteps.checkAccountStatus` 는 위임만, 폼 provider 도 이것을 부른다.
      → **텔레메트리**: 비밀번호 일치 + 비-ACTIVE → `auth.login.failed` (`accountId` 채움, `failureReason` = `ACCOUNT_LOCKED` / `ACCOUNT_DORMANT` /
      `ACCOUNT_DELETED` — 셋 다 **이미 enum 에 있는 값**, 계약 신규 값 없음). 계약 밖 상태 값 · 조회 실패 → `failed` 없음(`attempted` 만 —
      tenant_type 조회 장애와 같은 모양, enum 에 없는 값을 만들지 않는다).
      → 테스트: `CredentialAuthenticationProviderTest` +9 · `AccountServiceClientUnitTest` (기타 4xx → 예외로 **뒤집음** +4) ·
      `OAuthLoginUseCaseTest` +2(조회 실패 fail-closed · 신규 계정 404 는 진행) · 신규 `AccountStatusRuleTest` 6 ·
      account-service `InternalControllerSliceTest` +2 · Testcontainers `FormLoginIntegrationTest` +2(LOCKED → `/login?error` + `X-Tenant-Id` 검증,
      503 → `/login?error`) — **IT 는 CI 에서 판정**(이 호스트 Docker 데몬 DOWN). 실행 수치는 § 검증.
- [ ] **AC-3** — 🔴 **결과로 판정**(창 또는 iam e2e compose): 일회용 계정을 잠그고(`POST /internal/accounts/{id}/lock` 또는 `auth.token.reuse.detected` 주입 — 2026-09-25 창의 방법) 폼 로그인 → 거부. 대조군 = 잠그기 전 같은 계정으로 로그인 성공. 🔴 데모 공유 계정은 잠그지 마라.
      → **미완 — 런북은 § AC-3 런북.** 단위 · IT 는 «잠금 → 로그인 거부» 의 결과 상태를 모른다(Failure Scenario 4).
- [x] **AC-4** — `auth-api.md`(폼 로그인 절)와 `architecture.md` 에 상태 거부 규칙을 적는다(스펙이 먼저).
      → `specs/contracts/http/auth-api.md` § «POST /login — HTML 폼 로그인의 계정 상태 규칙»(신설) · `specs/services/auth-service/architecture.md`
      (BE-600 갱신 블록 + client 주석) · `specs/contracts/http/internal/auth-to-account.md`(`X-Tenant-Id` 헤더 · 매핑 규약 표 · 바뀐 BE-063 의미) ·
      `specs/contracts/events/auth-events.md`(폼 경로 `failureReason` 에 `ACCOUNT_*` 추가 — 기존 enum 값) · `specs/features/oauth-social-login.md`(소셜 fail-closed).

## AC-0 측정 (2026-09-25 UTC, 저장소 코드 읽기)

| ⚪ | 결과 | 근거 | 처리 |
|---|---|---|---|
| ① `refresh_token` grant 가 상태를 보는가 | **안 본다** | `auth-service/…/oauth2/SasRefreshTokenAuthenticationProvider.java:173-219` — 재사용 탐지 · revoked · expired · 크로스-테넌트만 검사, `AccountServicePort` 의존 없음(생성자 `:118-135`). 레거시 `RefreshTokenUseCase.java:52` 도 상태 조회 0. auth-service 는 `account.locked` 를 소비하지 않는다(`@KafkaListener` 0 · architecture.md «Event consumption: none») → **admin/자동 잠금은 이미 받은 refresh token 을 끊지 않는다**(끊기는 건 reuse 탐지 경로의 `handleReuseDetected` `:517-583` 뿐) | 🔴 **후속으로 분리 — 이 티켓은 하지 않았다.** 이유: (a) 규칙 적용 자체는 작지만 **조회 실패 정책이 결정되지 않았다** — 소유자 결정은 «폼 · 소셜» 의 fail-closed 이고, refresh 에 fail-closed 를 적용하면 account-service 장애 동안 **모든 활성 세션이 갱신 불가 → 일제 로그아웃**이라 로그인 거부와 영향 크기가 다르다(별도 소유자 결정). (b) provider 가 `AuthorizationServerConfig` 에서 수동 생성되고 SAS `invalid_grant` 응답 모양 · 테넌트 출처(principal details 의 `tenant_id`) · 운영자(계정 행 없음) 처리를 새로 정해야 한다. (c) 대안인 «`account.locked` 소비 → 세션 revoke» 도 후보라 설계 선택이 남는다. **파일은 만들지 않았다**(지시). 스펙에는 «refresh 경로는 아직 상태를 보지 않는다» 를 명시했다(auth-api.md · auth-to-account.md · architecture.md) |
| ② DELETED 계정의 자격 행이 남는가 | **남는다** | auth-service 에 자격 삭제 경로 0(`InternalCredentialController` 는 create · identity-backfill · account-id-by-email 뿐, 이벤트 소비 0). account-service 도 계정 행을 **지우지 않는다**(`accounts` 에 `delete*` 0 — 탈퇴는 status=DELETED + 익명화, `AccountAnonymizationScheduler.java:138`) | 상태 조회가 `DELETED` 를 돌려주므로 **이 티켓의 규칙이 막는다**(테스트 `deletedAccount_rejectedLikeWrongPassword`). BE-600 이전에는 삭제된 계정도 비밀번호로 들어갔다 |
| ③ 다른 테넌트 유형 | 🔴 **두 가지가 드러났다** | (a) **상태 조회가 `fan-platform` 고정**: `account-service/…/internal/AccountStatusQueryController.java` 가 헤더를 받지 않고 `AccountStatusUseCase.java:49` `getStatus(accountId, TenantId.FAN_PLATFORM)` → `:57` `findById(tenantId, accountId)`. 형제 `/lock` · `/unlock` · `/delete` 는 `X-Tenant-Id` 를 받는다(`AccountLockController.java:37,66,90`). ⇒ `ecommerce` 등 다른 테넌트 계정은 **잠글 수는 있는데 상태 조회에서는 404**. 데모 스토어 계정 `0199de70-…-ec01`(테넌트 `ecommerce`, `R__05_…accounts.sql`) 이 바로 그 경우. (b) **콘솔 운영자**(`demo@demo.com` 콘솔 행 `…ad03` · `requester@demo.com` `…ad04` · `viewer@demo.com` `…ad05`, 테넌트 `iam` — `R__01_…:104` · `R__seed_demo_second_operator_credential.sql:103` · `R__seed_demo_viewer_operator_credential.sql:38`) 는 **계정 행이 설계상 없다**(`R__05_…accounts.sql:62-63` «NO account row … deliberate») | (a) → **이 티켓에 포함**: 상태 조회에 선택 `X-Tenant-Id` 추가(헤더 없음 = 이전과 같은 fan-platform 고정 → membership · admin · 소셜 net-zero), 폼 provider 는 자격 행의 테넌트를 보낸다. 없었다면 404 를 «거부» 로 읽는 순간 스토어 로그인 전부가 막히고, «통과» 로 읽으면 스토어 계정 잠금이 여전히 무효다. (b) → 404 = «규칙을 적용할 계정 레코드가 없다» → 비밀번호대로 진행. **운영자 잠금은 이 규칙의 범위가 아니다**(운영자 평면은 admin-service `admin_operators` 가 판정). 🔴 옛 `LoginUseCase` 는 404 를 `CREDENTIALS_INVALID` 로 거부했다 — 그대로 옮기면 콘솔 로그인이 전부 죽는다 |

🔵 **배포 순서 안전성**: 새 auth-service + 옛 account-service(헤더 무시) 조합이면 폼 조회가 fan-platform 고정으로 떨어져 다른 테넌트 계정은 404 →
**통과**(잠금 미적용 = BE-600 이전과 같음), 로그인이 막히는 쪽으로는 가지 않는다. 두 서비스는 같은 커밋이라 재굽기에서 함께 들어가야 AC-3 이
`ecommerce` 계정으로도 성립한다.

## AC-3 런북 (창 또는 iam e2e compose — 미실행)

🔴 **데모 공유 계정(`demo@demo.com` · `requester@demo.com` · `viewer@demo.com` · 아티스트 계정)은 잠그지 마라.** 이 티켓의 auth-service **와**
account-service 가 모두 떠 있어야 한다(이미지 시각 ≥ 머지 시각 확인 — 호스트 `:latest` 가 낡을 수 있다).

1. **일회용 계정 만들기** — 스토어 가입(`/signup`, web-store client 경유 → 테넌트 `ecommerce`) 으로 `be600-<UTC날짜>@example.com` 생성.
   `account_db.accounts` 에서 `id`(= `<AID>`) · `tenant_id` · `status=ACTIVE` 확인.
2. **대조군 (잠그기 전)** — 같은 계정으로 폼 로그인(`POST /login`, 맞는 비밀번호) → **302 저장된 authorize 로 성공**. 틀린 비밀번호 1회 →
   302 `/login?error` 와 화면 문구 «Invalid email or password.» 를 기록(아래 5 의 비교 기준).
3. **잠그기** — 둘 중 하나: (a) `POST /internal/accounts/<AID>/lock` · 헤더 `Authorization: Bearer <internal.invoke 워크로드 토큰>` ·
   `X-Tenant-Id: ecommerce` · 본문 `{"reason":"ADMIN_LOCK","operatorId":"be600-verify"}` (b) 2026-09-25 창(TASK-MONO-672 항목 15 ②)처럼
   `auth.token.reuse.detected` 를 그 계정으로 주입 → security-service 가 `/lock` 호출.
4. **결과 상태 확인** — `account_db.accounts.status = LOCKED` (`<AID>`). 🔴 판정은 이 행으로 한다(REST 200 이 아니라).
5. **판정** — 같은 계정 · **맞는 비밀번호**로 폼 로그인 → **302 `/login?error`**, 화면 문구가 2 의 틀린 비밀번호와 **같다**, 세션 인증 없음.
   보조: auth outbox 에 `auth.login.failed` `accountId=<AID>` `failureReason=ACCOUNT_LOCKED` 가 1건.
6. **오라클 부재 보조 확인(선택)** — 같은 잠긴 계정 · 틀린 비밀번호 → 같은 `/login?error`, outbox `failureReason=CREDENTIALS_INVALID`.
7. **정리** — `POST /internal/accounts/<AID>/unlock`(`{"reason":"ADMIN_UNLOCK","operatorId":"be600-verify"}`, 같은 헤더) → 다시 로그인 성공까지 보면
   잠금 해제 경로의 결과 상태도 덤으로 잰다. 일회용 계정은 남겨도 된다(공유 계정이 아니므로).

판정 기준: 2 성공 AND 4 LOCKED AND 5 `/login?error`(문구 동일). 2 가 실패하면 판정 불가(대조군 없음) — 5 를 PASS 로 읽지 마라.

## 검증 (2026-09-25 UTC · 이 호스트)

| 무엇 | 결과 | 비고 |
|---|---|---|
| `./gradlew :projects:iam-platform:apps:auth-service:test` | **rc=0 · 766 tests / 실패 0 / 오류 0 / skipped 28** (JUnit XML 102 스위트 합산) | BE-599 기록 745 + 이 티켓 +21(provider +9 · client +4 · OAuth use case +2 · `AccountStatusRuleTest` 6) = 766 — 수가 맞는다 |
| bite — provider 가 규칙을 건너뛰게(`statusRejection` 이 항상 `null`) | **4 / 24 실패**(LOCKED · DORMANT · DELETED · 계약 밖 값) | 되돌린 뒤 24/24 통과. 순서 테스트(`lockedAccount_wrongPassword_…`)는 구조상 bite — 조회를 검증 뒤로 옮기면 틀린 비밀번호에서 조회가 0회라 `InOrder` 가 실패 |
| account-service `InternalControllerSliceTest` · `InternalDualAuthSliceTest` | **rc=0 · 18 / 실패 0** | `X-Tenant-Id` 있음 → 그 테넌트 · 없음 → 기존 fan-platform 오버로드 |
| `./gradlew :projects:iam-platform:apps:account-service:test` | **rc=0 · 506 tests / 실패 0 / 오류 0 / skipped 47** (79 스위트) | skipped = Docker 없는 IT |
| Testcontainers IT (`FormLoginIntegrationTest` +2 · 기존 happy path 가 새 상태 조회를 WireMock 으로 받는지) | ⚪ **CI 에서 판정** | 이 호스트 Docker 데몬 DOWN — IT 는 `DockerAvailableCondition` 으로 skip(위 28 에 포함). 🔴 이 IT 는 BE-600 전에는 account-service 가 필요 없었다 — 새 조회가 fail-closed 라 스텁이 없으면 happy path 가 죽는다. 그래서 WireMock + 워크로드 토큰 `@MockitoBean` 을 넣었다 |

## 후속 (파일 미생성 — 기록만)

1. 🔴 **refresh 경로의 상태 검사** (AC-0 ①) — 이미 받은 세션은 잠금 뒤에도 `refresh_token` grant 로 계속 산다. 먼저 필요한 소유자 결정:
   조회 실패 시 refresh 를 fail-closed 로 할지(장애 = 전원 갱신 불가) · 아니면 `account.locked` 소비 → 세션 revoke 쪽으로 갈지.
2. **소셜 상태 조회의 테넌트 인지화** — 소셜은 헤더 없이(fan-platform 고정) 조회하므로 BE-507 이후 `ecommerce` 에서 태어난 소셜 계정은 상태
   검사가 404 로 빠진다(잠겨도 소셜로 들어간다). identity 행의 테넌트를 보내면 BE-507 이전 계정(identity=`ecommerce`, 계정=`fan-platform`)이
   거꾸로 빠진다 — 계정의 실제 테넌트를 아는 출처(`socialSignup` 응답 등)를 정해야 한다.
3. 호출자 없는 `LoginUseCase.checkAccountStatus` 는 이 규칙의 **옛 사본**으로 남아 있다(죽은 코드 — BE-398 후속 정리 때 함께).

# Related Specs

- `specs/contracts/http/auth-api.md` · `specs/services/auth-service/architecture.md`
- `tasks/done/TASK-BE-309-auth-service-form-login-html-surface.md` (`:94` — minimal credential check 설계)
- `tasks/done/TASK-BE-398-legacy-custom-jwt-flow-sunset-removal.md`
- `TASK-BE-599` (이 결함이 드러난 곳 · 데모 완화)
- `TASK-MONO-672` 항목 15 ② (잠금 경로 PASS)

# Related Contracts

- account-service 상태 조회(`AccountServicePort.getAccountStatus`) — 바꾸지 않는다.
  → 🔴 **이탈 (구현 중 결정, 소유자 확인 대상)**: 두 곳을 바꿨다. (1) `GET /internal/accounts/{accountId}/status` 에 **선택** `X-Tenant-Id`
  (additive — 헤더 없음은 바이트 동일, 형제 `/lock` · `/unlock` · `/delete` 와 같은 해석). 이유 = AC-0 ③(a): 이것 없이는 `fan-platform` 밖 계정의
  잠금이 폼 로그인에서 여전히 무효이거나(404 → 통과), 404 를 거부로 읽는 순간 스토어 로그인 전부가 막힌다. (2) 포트의 «empty» 의미를 404 로
  좁혔다(AC-2 소유자 결정의 직접 결과). 계약 문서: `specs/contracts/http/internal/auth-to-account.md` 를 먼저 갱신.

# Edge Cases

| 상황 | 기대 |
|---|---|
| 로그인 도중 계정이 잠긴다 | 다음 요청부터 거부. 이미 발급된 access token 은 만료까지 유효(갱신 경로는 AC-0 결과에 따름) |
| account-service 가 응답하지 않는다 | AC-2 에서 정한 대로 — 🔴 조용한 fail-open 이면 결함이 장애 때 되살아난다 |
| 데모 공유 계정 | BE-599 의 데모 완화가 먼저 머지돼 있어야 한다(자동 잠금이 공유 계정을 막지 않도록) |

# Failure Scenarios

1. **소셜과 다른 규칙을 새로 만든다** → 두 경로가 갈라진다(지금 결함이 바로 그 모양이다).
2. **«잠겼습니다» 를 무심코 보여 준다** → 비밀번호 정답 오라클(AC-1).
3. **로그인만 막는다** → 이미 받은 세션이 갱신으로 계속 산다(AC-0).
4. **단위 테스트로 닫는다** → 실제 잠금 → 로그인 거부의 결과 상태는 모른다(AC-3).

## CORRECTION (2026-09-26 UTC) — AC-3 창 판정: 🟢 PASS

15차 AMI(`46aa3191`) 데모, 일회용 계정(상세 = `TASK-MONO-672` § 2026-09-26 창 수확 § 로그인 판정). 공개 클라이언트 `demo-spa-client` 로 폼 로그인.

| | 잠그기 전 (대조군) | 잠근 뒤 (합성 재사용 이벤트 → 자동 잠금, `accounts.status=LOCKED` 를 DB 로 확인) |
|---|---|---|
| A `wa-260249@ex.io` 폼 로그인 | 성공 (코드 → 토큰) | **`/login?error`** |
| B `wb-260249@ex.io` 폼 로그인 (잠그지 않음) | 성공 | 성공 |

⇒ **AC-3 닫힘.** 공유 데모 계정은 잠그지 않았다.
