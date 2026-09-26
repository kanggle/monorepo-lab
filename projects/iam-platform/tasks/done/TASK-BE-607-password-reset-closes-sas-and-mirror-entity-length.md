# Task ID

TASK-BE-607

# Status

done (2026-09-26 UTC — 4차원 검증 · 맨 아래 § close)

# Title

비밀번호 재설정이 SAS 인가를 닫지 않는다 · 미러 엔티티 길이 선언이 스키마와 어긋난다 · 비밀번호 변경 실패의 실제 영향 확인

# Owner

iam-platform

# Task Tags

- auth-service
- oauth2

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 기존 포트 재사용과 선언 정정. AC-0 판독만 Opus.

---

# Goal

`TASK-BE-604` 가 남긴 셋(§ ⑨-2 · ⑩-4 · ⑩-5 · § CORRECTION 2):

1. **재설정이 SAS 인가를 안 닫는다** — `ConfirmPasswordResetUseCase` 는 미러 행을 계정 UUID 로만 폐기한다. BE-604 로 폐기된 미러 행은 refresh 를 막게 됐지만,
   BE-603 이전의 **이메일 키 미러 행**(배수 기간 최대 30일)을 가진 세션은 재설정 뒤에도 다음 회전 전까지 refresh 된다. 강제 로그아웃과 같은 모양
   (`OAuthAuthorizationRevocationPort` 로 SAS 인가 자체 무효화)이면 배수 기간과 무관하게 닫힌다.
2. **엔티티 길이** — `RefreshTokenJpaEntity` 의 `jti`/`rotated_from` 이 `length = 36`, Flyway `V0014` 는 255. Hibernate DDL 을 쓰는 H2 슬라이스에서 SAS 토큰 INSERT 가 깨진다
   (BE-604 § ⑤ 에서 발견 · 운영은 Flyway 라 무해).
3. **비밀번호 변경 실패의 실제 영향** — BE-604 CORRECTION 2: `Credential.changePassword` 의 `version + 1` 때문에 기존 계정의 `PATCH /api/auth/password` ·
   `POST /api/auth/password-reset/confirm` 이 항상 실패했다(#4033 에서 수정). 배포된 스택(데모)에서 그 실패가 실제로 났는지.

# Scope

## 포함

- 1 구현(포트 재사용) + IT · 2 선언 정정 · 3 측정(데모 창 또는 로그).

## 제외

- 재사용 탐지(`TASK-BE-606`) · 세션 생성 시점 테넌트(`TASK-BE-605`).

# Acceptance Criteria

- [x] **AC-1** — 재설정 확인이 `OAuthAuthorizationRevocationPort` 로 그 계정의 SAS 인가를 무효화한다 · IT: 재설정 뒤 기존 세션 refresh 400(미러 행 모양과 무관 — 이메일 키 행 픽스처 포함).
  ✅ `ConfirmPasswordResetUseCase.java:72`(필드) · `:110`(`oAuthAuthorizationRevocationPort.revokeActiveRefreshTokens(accountId)`, 미러 행 폐기(`:105`)와 나란히 — `ForceLogoutUseCase`(TASK-BE-601)와 같은 모양.
  단위(Mockito): `ConfirmPasswordResetUseCaseTest.java` happy-path(`:90-147`, 포트가 미러 행 폐기 다음 · bulk 마커 앞 순서로 호출되는 것을 `InOrder` 로 단언) · 실패 경로 3건(unknownToken·credentialMissing·policyViolation) 에 `verifyNoInteractions(oAuthAuthorizationRevocationPort)` 추가 · 신규 `execute_sasRevokeFailure_propagatesAndPreservesToken`(`:218-238`, 포트 예외가 트랜잭션 경계로 올라가고 토큰이 삭제되지 않음을 확인 — `ForceLogoutUseCaseTest.execute_sasRevokeFailure_propagates` 와 같은 모양).
  IT(Testcontainers, CI 판정 — 로컬 Docker 없음): `OAuth2RefreshTokenIntegrationTest.java` 신규 `@Order(11)` `passwordReset_closesSasAuthorization_evenWhenMirrorRowIsEmailKeyed`(`:669-718`) — 세션 발급 후 미러 행을 **이메일 키**(pre-BE-603 모양)로 직접 재작성 → `revokeAllByAccountId(uuid)` 가 그 행에 닿지 않음을 먼저 단언(sanity) → `confirmPasswordResetUseCase.execute()` → 미러 행이 (uuid 아닌 jti 매치로) revoked 로 바뀌고 다음 refresh 가 `400 invalid_grant`.
  **bite**: 포트 호출을 `int sasRevoked = 0;` 으로 임시 치환 → `ConfirmPasswordResetUseCaseTest` **rc=1**(happy-path 의 `InOrder` 단언 실패) → 원복 → `./gradlew …:test` rc=0. IT bite 는 Docker 부재로 로컬 미실행(CI 판정) — 단위 bite 로 대체.
- [x] **AC-2** — `RefreshTokenJpaEntity` 길이 선언 = 스키마(255) · H2 슬라이스에서 SAS 토큰 길이 INSERT 성공 단언.
  ✅ `RefreshTokenJpaEntity.java:27`(`jti` `length = 36` → `255`) · `:45`(`rotatedFrom` `length = 36` → `255`) — Flyway `V0014` 가 이미 두 컬럼을 255로 넓혔고(`specs/services/auth-service/data-model.md:35,:40` 도 이미 255로 정확히 서술) 엔티티 선언만 뒤에 남아 있었다(BE-604 § ⑤ 에서 발견 · BE-601 후속 ⑩-5).
  신규 `RefreshTokenJpaRepositoryH2Test.java`(H2 auxiliary-slice, `platform/testing-strategy.md` § H2 auxiliary-slice exception — 권위는 그대로 `RefreshTokenJpaRepositoryTest`, Testcontainers MySQL `ddl-auto=validate`): 128자 `jti` INSERT 성공(`:51-68`) · 128자 `rotated_from` INSERT 성공(`:70-86`).
  **bite**: `jti` 를 `length = 36` 으로 되돌리고 `--tests "*RefreshTokenJpaRepositoryH2Test"` 실행 → **rc=1**(두 케이스 모두 `JdbcSQLDataException`/`DataIntegrityViolationException` — "Value too long") → 원복 → 전체 재실행 rc=0. **왜 Testcontainers IT 는 이 결함을 못 잡는가**: 그 스위트는 `ddl-auto=validate`(Flyway 가 만든 실제 255 컬럼을 검증만) 이고 Hibernate 의 validate 모드는 선언 길이 불일치로 기동을 막지 않는다 — Hibernate 가 스키마를 **직접 생성**하는 경로(`ddl-auto=create-drop`, 이 H2 슬라이스 · 기존 `OAuth2AuthorizationServerSliceTest`)에서만 선언 길이가 실제로 컬럼에 반영되어 결함이 드러난다.
- [ ] **AC-3** — 🔴 **결과로 판정**: 데모 auth-service 로그/응답에서 비밀번호 변경·재설정 실패 흔적을 찾거나, 없으면 «호출 0 이라 흔적 없음» 인지 «호출됐고 실패» 인지를
      구별해 적는다(0건 ≠ 결함 없음). 재굽기 뒤 데모에서 일회용 계정으로 변경 1회 → 성공(대조군: 수정 전 이미지면 실패).
      🔴 **미착수 — 라이브 창 필요(이 worktree 에선 시도하지 않았다).** 런북: (1) 현재 데모 AMI 에 BE-604 수정(#4033, `06031b053`)이 포함돼 있는지 먼저 확인(포함 전 이미지면 대조군으로 실패가 나야 정상 — `env_pulled_checkout_holds_stale_build` 류 혼동 방지) (2) 일회용 계정으로 로그인 → `PATCH /api/auth/password`(현재+새 비밀번호) 1회 → 204 확인 (3) 같은 계정으로 `POST /api/auth/password-reset/request` + `/confirm` 1회 → 204 확인 (4) auth-service 컨테이너 로그에서 두 호출 주변에 `ObjectOptimisticLockingFailureException`/500 이 있는지 grep — 없으면 "0건이라 흔적 없음"(BE-604 수정이 이미 라이브에 있어 재현 자체가 안 됨)과 "호출이 실제로 없었다"를 반드시 구별해서 기록할 것(Failure Scenario 2).

## AC-1 부속 결정 — `ChangePasswordUseCase`(인증된 비밀번호 변경)도 세션을 닫아야 하는가 (2026-09-26 UTC)

**결정 = 바꾸지 않는다. 후속으로만 기록한다.**

- **현재 동작(코드 판독)** — `ChangePasswordUseCase.execute`(`ChangePasswordUseCase.java:48-67`)는 현재 비밀번호 검증 → 정책 검증 → 해시 저장만 하고, **어떤 세션도 폐기하지 않는다**(`refreshTokenRepository`·`bulkInvalidationStore`·`OAuthAuthorizationRevocationPort` 어느 것도 주입돼 있지 않다). `ConfirmPasswordResetUseCase`(재설정, 토큰 기반·비인증)와 대칭이 아니다.
- **계약(`auth-api.md:781-799` § `PATCH /api/auth/password`)** — 세션·revoke 언급 **없음**. 에러 코드 표만 있고, `CURRENT_PASSWORD_MISMATCH` 를 401 이 아니라 400 으로 둔 이유가 *"클라이언트가 세션 만료로 읽고 비밀번호 변경 도중 로그아웃시키는 것을 피하려는 의도"* — 계약 자체가 "이 경로 도중 세션을 끊지 않는다" 는 태도를 이미 서술하고 있다.
- **피처 스펙(`password-management.md:41-47` § 패스워드 변경)** — 5단계: *"현재 세션은 유지, 다른 모든 세션은 revoke (**선택적**, 보안 강화 모드)"*. 재설정 5단계(`:49-55`)는 대칭 문장에 **선택적이 아니고** *"모든 세션 revoke"* 로 무조건이다 — 스펙 저자가 둘을 의도적으로 다르게 적었다.
- **판정** — 스펙이 "선택적 보안 강화 모드"라고 부르는 기능은 (a) 그 모드를 트리거하는 요청 필드가 계약에 없고, (b) "현재 세션은 유지" 라는 절반(부분 revoke, force-logout 의 net-zero 모양과 다르다)까지 설계해야 해 이 티켓의 «포트 재사용» 범위를 넘는 신규 기능이다. 이것은 티켓이 규정한 "spec unspecified → 바꾸지 않는다" 케이스에 해당한다 — "선택적"은 "필수"가 아니고, 트리거할 방법이 계약에 없는 기능은 지금 미구현 상태와 동작이 같다.
- **후속 후보(파일 만들지 않음 — 소유자 판단)**: `PATCH /api/auth/password` 에 "다른 세션 전부 종료" 옵션(요청 필드 또는 항상-on)을 추가할지, 추가한다면 그 세션 집합에서 **호출자 자신의 현재 세션은 제외**해야 하는가(스펙이 요구하는 "현재 세션 유지") — force-logout/reset 의 기존 net-zero(전체 폐기) 유스케이스와 다른 세 번째 모양이 필요하다.

# Related Specs

- `specs/services/auth-service/data-model.md` · `specs/contracts/http/auth-api.md`
- `specs/features/password-management.md`(§ 패스워드 변경 vs § 패스워드 재설정 — 세션 revoke 대칭성 판단의 근거)
- `TASK-BE-604`(§ ⑨ · ⑩ · CORRECTION) · `TASK-BE-601`(폐기 포트)

# Related Contracts

- 없음.

# Edge Cases

| 상황 | 기대 |
|---|---|
| 소셜 전용 계정(자격 행 없음) | 재설정 대상 아님 — 무변경 |

# Failure Scenarios

1. **미러 행만 폐기한다** → 배수 기간 세션이 산다(지금 상태). → ✅ 피함: `OAuthAuthorizationRevocationPort` 를 나란히 부른다(AC-1).
2. **AC-3 에서 로그 0건을 «결함 없었다» 로 적는다** → 호출이 없었던 것일 수 있다. → AC-3 미착수로 남김(런북만 기록, 위 § AC-3).

---

## 구현 기록 (2026-09-26 UTC · Sonnet, worktree `mlab-607`)

**파일** — main 2(`ConfirmPasswordResetUseCase.java` · `RefreshTokenJpaEntity.java`) · test 3(`ConfirmPasswordResetUseCaseTest.java` +2건 · `OAuth2RefreshTokenIntegrationTest.java` +1건 · 신규 `RefreshTokenJpaRepositoryH2Test.java`) · spec 0(둘 다 이미 정확했다 — `data-model.md` 는 255를 이미 서술, `auth-api.md`/`password-management.md` 는 세션 대칭성 판단에만 근거로 인용, 변경 없음).

**테스트** — `./gradlew :projects:iam-platform:apps:auth-service:test` → **rc=0, 838 tests · 0 failures · 0 errors · 30 skipped**(Docker 없음 — `@Tag("integration")` 은 `test` 에서 제외; 신규 H2 슬라이스는 `@DataJpaTest` 라 Docker 불필요라 실행됨), 110 classes(BE-604 종료 시점 109 + 신규 H2 슬라이스 1). `OAuth2RefreshTokenIntegrationTest` 의 신규 `@Order(11)` 은 나머지 Testcontainers IT 와 함께 30건의 skip 안에 포함 — CI 판정.

**bite** —
1. `ConfirmPasswordResetUseCase` 의 포트 호출을 `int sasRevoked = 0;` 로 치환 → `--tests "*ConfirmPasswordResetUseCaseTest"` **rc=1**(happy-path `InOrder` 단언 실패) → 원복 → 전체 재실행 rc=0.
2. `RefreshTokenJpaEntity.jti` 를 `length = 36` 으로 되돌림 → `--tests "*RefreshTokenJpaRepositoryH2Test"` **rc=1**(두 케이스 모두 truncation 예외) → 원복 → 전체 재실행 rc=0(위 테스트 수치가 이 재실행).
3. IT(`@Order(11)`) 은 Docker 부재로 로컬에서 bite 불가 — 단위 bite(1)이 같은 코드 경로(포트 호출)를 잡으므로 대체.

**이탈 · 판단**
- `ChangePasswordUseCase` — 바꾸지 않음. 근거는 위 § AC-1 부속 결정(스펙이 "선택적 보안 강화 모드"로 명시, 트리거 필드가 계약에 없음).
- AC-3(비밀번호 변경 실패의 실제 영향, 데모 로그/응답) — 미착수, 라이브 창 필요. 런북은 위 § AC-3.
- `ForceLogoutUseCase`(TASK-BE-601)·`SasAuthorizationRevocationAdapter` 본체는 건드리지 않음 — 재사용만.

## CORRECTION (2026-09-26 UTC) — AC-3 라이브 판정: 🟢 PASS (서비스 수준) · 🔴 게이트웨이 경로는 둘 다 막혀 있다 → `TASK-BE-609`

16차 AMI(`58d4920c4` — BE-604 `06031b053` · BE-607 포함) 신선 볼륨. 일회용 계정 `b7-261521@ex.io`(`7e8ea56e-…`, fan-platform), 인스턴스 안에서 `curl`.
🔴 **호출 경로를 밝힌다**: auth-service 에 **직접**, 게이트웨이가 하는 `X-Account-Id` 주입을 흉내 냈다(아래 ②의 이유).

| 단계 | 결과 |
|---|---|
| 대조군: 가입 201 · PKCE 폼 로그인 · refresh | 성공 · 200 |
| 변경 `PATCH /api/auth/password` P1→P2 | **204** · P2 로그인 성공 · P1 로그인 `/login?error` |
| 재설정 `request` → Redis `pwd-reset:{token}`(값 = 계정 id) → `confirm` P3 | **204 · 204** · P3 성공 · P2 `/login?error` |
| 재설정 전 P2 세션 refresh (대조군 200) → 재설정 뒤 | **400 `invalid_grant`** (AC-1 의 SAS 폐기 라이브 확인) |
| auth-service 로그(T0 이후) | `OptimisticLock` **0** · ERROR **0** · `Password reset confirmed … revokedTokens=4, sasAuthorizations=4` |

⇒ Failure Scenario 2 구별: 호출이 **있었고**(204 · 로그 줄) 실패 흔적이 0 이다 — «호출 0 이라 흔적 없음» 이 아니다. **AC-3 닫힘.**

🔴 **② 새 결함 — 게이트웨이를 거치면 두 API 모두 쓸 수 없다** (iam 게이트웨이 `iam-gateway-service`, 같은 창):
- `POST /api/auth/password-reset/request|confirm` → **401 `TOKEN_INVALID`** — 게이트웨이 `public-paths` 에 없다(`gateway-service/src/main/resources/application.yml` § public-paths). 비로그인 사용자가 부르는 API 다.
- `PATCH /api/auth/password` + 유효한 사용자 Bearer → 게이트웨이는 통과, auth-service 가 **401 `Missing or invalid internal credentials`**. 직접 호출로 재현: `X-Account-Id` 만 → 400(정상 판정) · 같은 요청 + `Authorization: Bearer <사용자 토큰>` → 401. 게이트웨이가 넘긴 사용자 Bearer 를 auth-service 의 내부 자격 체인이 검사하는 것으로 보인다.
- 두 API 를 부르는 프런트는 **0**(저장소 grep) — 그래서 지금까지 안 보였다. 후속 = `TASK-BE-609`.

## CORRECTION (2026-09-26 UTC) — close (4차원)

- (a)(b) impl PR **#4035** MERGED · 스쿼시 `2150344c3` 가 `origin/main` 조상. (c) 머지 전 SUCCESS 16 · SKIPPED 51 · 실패 0.
- (d) AC-1·2 체크 · AC-3 = 위 § AC-3 라이브 판정 PASS(서비스 수준 — AC 가 묻는 «변경·재설정이 성공하는가 · 실패 흔적 0 과 호출 0 의 구별» 에 답했다).
- 집을 준 것: 게이트웨이 경유 401 두 건 → **`TASK-BE-609`** · `ChangePasswordUseCase` 의 다른 세션 폐기(스펙상 선택) → 후속 후보(티켓 없음, 기존 § AC-1 부속 결정 그대로). ⇒ **done.**
