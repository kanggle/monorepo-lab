# Task ID

TASK-BE-601

# Status

review

# Title

계정이 잠기면 그 계정의 세션을 폐기한다 — 잠금 전에 받은 세션이 토큰 갱신으로 계속 산다

# Owner

iam-platform

# Task Tags

- auth-service
- security
- event-consumer

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 — 이벤트 소비 · 멱등 · 두 토큰 저장소(SAS 인가 · 자체 refresh) 정합이 걸린다.

---

# Goal

`TASK-BE-600`(#4020 `9fc286ba3`)이 **새 로그인**에서 잠긴 계정을 거부하게 했다. 그러나 **잠기기 전에 받은 세션**은 그대로 산다 —
`SasRefreshTokenAuthenticationProvider.java:173-219` 는 계정 상태를 보지 않고, auth-service 는 `account.locked` 를 구독하지 않는다
(BE-600 AC-0 ① 실측). ⇒ 자동 잠금(security-service)이든 관리자 잠금이든, 이미 로그인한 공격자는 refresh 로 **계속 머문다**.

🔵 **소유자 결정 (2026-09-25 UTC)** — 방식 = **잠금 이벤트로 세션 폐기**. 기각: «갱신 때마다 상태 조회(fail-closed)» — account-service
장애가 모든 활성 세션의 갱신 장애로 번진다. 받아들인 대가: 이벤트 전파 지연만큼의 틈.

## 실측 (2026-09-25 UTC · 저장소만)

| 사실 | 근거 |
|---|---|
| 폐기 유스케이스는 이미 있다 | `auth-service/…/application/ForceLogoutUseCase.java` — `refreshTokenRepository.revokeAllByAccountId` + Redis 무효화 마커(access token TTL 동안). 테넌트 한정 오버로드(BE-468) 포함 |
| 지금 부르는 곳은 관리자 «세션 폐기» 하나 | `InternalCredentialController.java:77` `POST /internal/auth/accounts/{id}/force-logout` ← admin-service `SessionAdminUseCase` → `AuthServiceClient.forceLogout`. 🔴 **잠금과는 별개 동작** — account-service 잠금 경로에 force-logout 호출 0(grep) |
| `account.locked` 는 발행되고 있다 | account-service `AccountStatusEvents` / outbox → 토픽 `account.locked`. 현재 소비자 = security-service `AccountLockedConsumer`(잠금 이력 기록)뿐 |
| ⚪ 미측정 | ① `revokeAllByAccountId` 가 **SAS 인가 저장소**(`oauth2_authorization`)의 refresh 까지 무효화하는가, 자체 `refresh_tokens` 만인가 — BE-599 보고에 «SAS refresh 미러 행» 언급. 한쪽만 지우면 다른 쪽으로 갱신된다 ② 이벤트 봉투의 테넌트 필드 — BE-468 한정 오버로드에 무엇을 넘길지 ③ 관리자 잠금이 이미 같은 효과를 내는 다른 경로가 있는가 |

# Scope

## 포함

- auth-service 에 `account.locked` 소비자 — `ForceLogoutUseCase` 로 그 계정의 세션을 폐기한다. 멱등(재전달·중복 안전) · 테넌트 봉투 검증(security-service 소비자의 DLQ 규칙과 같은 결).
- ⚪ ① 결과에 따라 SAS 인가 저장소의 refresh 도 무효화(필요하면 `ForceLogoutUseCase` 확장 — 관리자 폐기도 같은 구멍이면 같이 고쳐진다).
- 계약 문서: `account.locked` Consumers 에 auth-service 추가.

## 제외

- 갱신 때 상태 조회(기각된 방식).
- DORMANT · DELETED 이벤트 — 별도 판단(필요하면 AC-0 에서 이름으로 남긴다).

# Acceptance Criteria

- [x] **AC-0** — ⚪ 셋을 재고 범위를 확정한다. 🔴 ① 이 «자체 저장소만» 이면, 이 티켓이 폐기해도 SAS 쪽 refresh 로 여전히 산다 — 그것을 고치지 않으면 AC-3 이 실패해야 정상이다.
      → **① 은 «자체 저장소만» 보다 나빴다 — `revokeAllByAccountId` 는 SAS 세션의 어느 저장소에도 닿지 않는다**(키가 이메일). 두 저장소를 다 닫는 폐기를 추가했고
      관리자 force-logout 도 같이 고쳐졌다. ② net-zero 로 호출(근거 아래). ③ 잠금 시 폐기하는 다른 경로 없음. 상세 § AC-0 측정.
- [x] **AC-1** — 소비자 구현 + 단위 테스트: 잠금 이벤트 → 폐기 호출 · 중복 이벤트 → 두 번째는 no-op · 테넌트 없는 봉투 → DLQ · 폐기 실패 → 재시도/DLQ(조용한 삼킴 금지).
      → `AccountLockedConsumer` → `RevokeSessionsOnAccountLockedUseCase` → `ForceLogoutUseCase`. 단위 신규 25건 + 기존 `ForceLogoutUseCaseTest` 에 2건 추가 —
      § 구현 · § 테스트. Testcontainers IT `AccountLockedSessionRevocationIntegrationTest`(잠금 전 refresh 성공 → 이벤트 → **같은 세션의 SAS refresh 가 400
      `invalid_grant`** · 같은 이메일의 다른 테넌트 계정 생존 · DLQ · 실제 MySQL dedupe)는 **이 호스트에서 미실행**(Docker 데몬 없음) — CI `integrationTest` 가 권위.
- [x] **AC-2** — 계약·아키텍처 문서 갱신(소비자 추가 · 지연 틈 명시).
      → `specs/contracts/events/account-events.md` § account.locked(소비자 정정 + auth-service 소비 규칙 + 🔵 수용한 틈) ·
      `specs/services/auth-service/architecture.md`(Service Type `identity-platform + event-consumer` · § Event Consumption · § Subscribed Topics · event-consumer MUST 현황) ·
      `specs/contracts/http/internal/admin-to-auth.md`(force-logout 이 SAS 세션도 끊음 · `revokedTokenCount` 의미) · `specs/services/auth-service/data-model.md`
      (`principal_name` 은 account_id 가 **아니다** 정정 · V0039).
- [ ] **AC-3** — 🔴 **결과로 판정**(창 또는 iam e2e compose): 일회용 계정 로그인 → refresh 성공(대조군) → 잠금 → 같은 refresh 토큰으로 갱신 **거부**. 🔴 공유 데모 계정은 잠그지 마라.
      → **미실행 — 런북은 § AC-3 런북.** 단위 · IT 는 배포된 스택(실제 account-service 발행 → Kafka → 소비)의 결과 상태를 모른다(Failure Scenario 3).

## AC-0 측정 (2026-09-25 UTC, 저장소 코드 읽기)

| ⚪ | 결과 | 근거 |
|---|---|---|
| ① `revokeAllByAccountId` 가 SAS 저장소까지 닫는가 | 🔴 **어느 쪽도 못 닫는다** — SAS 세션에 대해 `ForceLogoutUseCase` 는 **no-op** 이었다 | **SAS refresh 수락을 정하는 곳**: `SasRefreshTokenAuthenticationProvider.java:156` `authorizationService.findByToken(value, REFRESH_TOKEN)` → `:169` `!refreshTokenHolder.isActive()` 면 `invalid_grant` (= `oauth2_authorization` 행, `JdbcOAuth2AuthorizationService` — `AuthorizationServerConfig.java:382-389` `DomainSync(JpaOAuth2AuthorizationService)`). 그다음 `:175` `refreshTokenRepository.findByJti(value)` 미러 행이 **있으면** `:189` revoked → 거부; **없으면 `:218` 그냥 진행**. 즉 권위는 SAS 인가 행, 미러 행은 «있으면» 추가 거부. 🔴 **두 저장소 모두 principal 이름 = 로그인 이메일로 키가 잡힌다**: 폼 `CredentialAuthenticationProvider.java:372-373` `UsernamePasswordAuthenticationToken(credential.getEmail(), …)`, 소셜 `SocialLoginBrowserController.java:185-186` `(login.email(), …)` → SAS `principal_name` = 이메일; 미러 행은 `DomainSyncOAuth2AuthorizationService.java:135` `accountId = authorization.getPrincipalName()` 을 `refresh_tokens.account_id` 에 쓴다(회전 시 `SasRefreshTokenAuthenticationProvider.java:478` 동일 — `TASK-BE-465` 가 «cosmetic» 으로 남겨 둔 라벨). 폐기 쿼리는 `RefreshTokenJpaRepository.java:18` `WHERE r.accountId = :accountId`(UUID) → **이메일 키 행과 절대 일치하지 않는다**. `revokeAllByAccountId` 가 잡는 UUID 키 행은 레거시 `LoginUseCase`(HTTP 진입점 없음, BE-398) 발급분뿐. ⇒ **관리자 «세션 폐기»(`InternalCredentialController.java:77`)도 SAS 세션을 끊지 못했다.** (스펙도 틀렸다: `data-model.md` 는 `principal_name` 을 «account_id 또는 client_id» 라 적었다 — 정정함.) |
| ② 봉투의 테넌트 필드 · BE-468 오버로드에 무엇을 넘기나 | `tenantId` (flat, 루트) = **계정 자신의 테넌트** | `AccountEventFactory.java:66` `"tenantId", account.getTenantId().value()`; 발행 `OutboxAccountEventPublisher.java:85-89`(flat wire, `requireTenantId`). **결정: `ForceLogoutUseCase.execute(accountId)` — net-zero.** 이유: BE-468 한정은 «운영자가 자기 활성 테넌트 밖의 계정을 끊는 것» 을 막는 장치이고, 이 이벤트는 account-service 가 자기 계정에 대해 알리는 사실이며 계정 id(UUID)는 계정 하나만 가리킨다. 한정 오버로드는 `credentials` 행의 테넌트로 소유를 판정하는데(`ForceLogoutUseCase.java` `ownsAccount`), **소셜 전용 계정은 자격 행이 없다**(account-service `SocialSignupUseCase` 에 credential 생성 0) → 테넌트를 넘기면 그 계정의 잠금이 **조용한 no-op** 이 된다. 테넌트는 봉투 검증(누락 → DLQ)과 로그에 쓴다. SAS 폐기는 테넌트가 아니라 **principal details 의 `account_id`** 로 확정하므로 같은 이메일의 다른 테넌트 계정은 건드리지 않는다(단위 + IT). |
| ③ 잠금이 이미 같은 효과를 내는 다른 경로 | **없다** | `forceLogout` 호출처는 admin-service `SessionAdminUseCase.java:32`(운영자 «세션 폐기») 하나; account-service 잠금 경로(`AccountStatusEvents.java:44-46`)는 이벤트만 발행. 자동 잠금도 같은 이벤트. reuse 탐지(`SasRefreshTokenAuthenticationProvider.java:517-583`)는 잠금과 무관 — 게다가 그것도 `revokeAllByAccountId(email)` 로 부르므로 SAS 미러 행만 닫는다(인가 행은 그대로 — 별개 관찰, 범위 밖). |
| 부수: 같은 구멍의 형제 | 🔵 **이름만 남긴다** | `ConfirmPasswordResetUseCase.java:95` 도 `revokeAllByAccountId(accountId)` — **비밀번호 재설정 후에도 SAS 세션이 산다**(같은 원인). 이 티켓은 `ForceLogoutUseCase` 만 고쳤다 — 후속 후보. `account.deleted` 계약 줄(«auth-service 전체 세션 즉시 무효화»)도 소비자 코드 0 — 같은 모양의 문서-코드 불일치(티켓 제외 항목, DELETED). DORMANT 는 특화 이벤트가 없다(`retention.md:86`). |

## 구현

- **SAS 저장소 폐기** — port `application/port/OAuthAuthorizationRevocationPort` ← `infrastructure/oauth2/SasAuthorizationRevocationAdapter`:
  계정의 principal 이름 후보(자격 행 이메일 + 연결된 소셜 식별자 provider 이메일)로 `oauth2_authorization` 에서 만료 안 된 refresh 를 가진 인가를 찾고,
  **principal details `account_id` == 대상 계정**인 것만 refresh · access 에 `metadata.token.invalidated=true`(SAS revoke 와 같은 metadata)를 써서 저장,
  미러 행(`refresh_tokens`, jti = 토큰 값)을 revoke. ID 토큰은 남긴다(RP-initiated logout 의 `id_token_hint` 해석용). `account_id` 가 없는 인가는 귀속 불가 → 건드리지 않음(WARN).
  `ForceLogoutUseCase` 가 레거시 revoke 다음에 이것을 부른다 → **관리자 force-logout 도 같은 수정**. `revokedTokenCount` = 레거시 행 + SAS 인가.
- **소비자** — `infrastructure/messaging/AccountLockedConsumer`(group `auth-service-account-locked`) → `application/RevokeSessionsOnAccountLockedUseCase`
  (`@Transactional`: `EventDedupePort.process(eventId, …)` 안에서 `ForceLogoutUseCase.execute(accountId)`).
  봉투: flat/`payload` 둘 다 · `eventId`(UUID) · `accountId` 필수 → 없으면 `InvalidEventPayloadException`; `tenantId` 없으면 `MissingTenantIdException`; 둘 다 재시도 없이 DLQ.
  `eventVersion`/`schemaVersion` 은 있으면 1·2 만. 메트릭 `auth.account_locked.sessions{outcome=revoked|duplicate}` · `auth.account_locked.propagation.lag`(lockedAt → 폐기).
- **멱등** — `infrastructure/persistence/JdbcEventDedupeAdapter`(V0004 `processed_events`, BE-450 이후 읽는 곳 없던 테이블 재사용): `INSERT IGNORE` 영향 행 수로 판정,
  `Propagation.MANDATORY` — 폐기 실패 시 dedupe 행도 롤백되어 재시도가 다시 처리한다. 🔵 dedupe 가 필요한 진짜 이유: 「잠금 → 해제 → 재로그인」 뒤 옛 잠금 이벤트가
  재전달되면 **새 세션**을 끊는다.
- **에러 처리** — `infrastructure/config/KafkaConsumerConfig`: `DefaultErrorHandler`(지수 백오프 1s×2 · 최대 30s · 3회) → `<topic>.dlq`, 메트릭 `outbox.dlq.size{reason}`
  (security-service 와 같은 이름). DLQ 발행은 **파티션 -1**(프로듀서 선택) — compose 가 DLQ 를 1 파티션으로 만든다.
- **설정** — `application.yml`: consumer `auto-offset-reset=earliest`(event-consumer.md 가 `latest` 금지 — 첫 기동 때 보존 기간 안 잠금 재생은 안전한 쪽의 대가) ·
  `enable-auto-commit=false` · String deserializer · `spring.kafka.listener.observation-enabled=true`. 토픽 `account.locked` / `.dlq` 는 compose `kafka-init` TOPICS 에 이미 있다(변경 없음).
- **마이그레이션** — `V0039__index_oauth2_authorization_principal_name.sql`(SAS 는 만료 인가를 지우지 않아 무인덱스면 잠금마다 전표 스캔).
- **Service Type** — `scripts/check-service-type-drift.sh` 가 `@KafkaListener` ⇒ `event-consumer` 선언을 강제 → `identity-platform + event-consumer`
  (service-types INDEX Selection Rule 2: 보조 타입 추가는 재분류가 아니라 명확화, ADR 불요).

## 테스트 (2026-09-25 UTC, 이 호스트)

- `./gradlew :projects:iam-platform:apps:auth-service:test` → **rc=0, 793 tests · 0 failures · 0 errors · 28 skipped**(Docker 없음 — `@Tag("integration")` 은 `test` 에서 제외).
  신규/변경: `RevokeSessionsOnAccountLockedUseCaseTest` 4 · `AccountLockedConsumerTest` 7 · `KafkaConsumerConfigTest` 3 · `SasAuthorizationRevocationAdapterTest` 7 ·
  `JdbcEventDedupeAdapterTest` 4 · `ForceLogoutUseCaseTest` +2(7).
- **제거 검사**(Edit 로 무력화 → 실행 → Edit 로 복구, `git checkout` 미사용):
  (a) 유스케이스의 폐기 호출 + `ForceLogoutUseCase` 의 SAS 폐기 호출 제거 → `ForceLogoutUseCaseTest` · `RevokeSessionsOnAccountLockedUseCaseTest` **11 중 6 실패**;
  (b) 어댑터의 `account_id` 확정 제거 → `sameEmailOtherAccount_isLeftAlone` **실패**. 복구 후 전체 재실행 rc=0.
- IT `AccountLockedSessionRevocationIntegrationTest` — 컴파일만 확인, **실행 안 됨**(Docker 데몬 없음). CI `integrationTest` 결과가 권위.
- **CI 1차(run 36115767160) = IT 실패** — 기존 결함(`refresh_tokens.account_id` VARCHAR(36), 아래 § 기존 결함) 때문에 대조군 refresh 에서 터짐.
  픽스처 이메일을 36자 이하로 바꿨다(결함 회피 — 정상 조건 아님).

## AC-3 런북 (창 또는 iam e2e compose — 미실행)

🔴 **공유 데모 계정(`demo@demo.com` · `requester@demo.com` · `viewer@demo.com` · 아티스트 계정)은 잠그지 마라.** 이 브랜치의 auth-service 이미지가 떠 있어야 한다
(재굽기 전 데모 창에는 없다). 🔵 첫 기동 때 `earliest` 로 최근 7일 잠금이 재생된다 — 그 사이 해제된 계정은 세션을 한 번 잃는다(정상).

1. **일회용 계정 둘** — `b6a-<MMDDhhmm>@ex.io`(잠글 것) · `b6b-<MMDDhhmm>@ex.io`(대조군), **36자 이하**(아래 보정) — 같은 테넌트(`fan-platform`)로 가입(`/signup`).
2. 각각 공개 클라이언트로 authorization_code + PKCE 로그인 → refresh 토큰 `A0` · `B0` 확보.
3. **대조군**: `POST /oauth2/token grant_type=refresh_token refresh_token=A0 client_id=<client>` → **200**, 새 토큰 `A1`. 같은 방법으로 `B0` → `B1`.
4. **잠금**: 운영자로 A 만 잠근다(콘솔 계정 잠금 또는 admin-service `POST /api/admin/accounts/{A의 id}/lock`). account-service 가 `account.locked` 를 발행.
5. auth-service 로그에서 `account.locked: revoked sessions account=<A의 id> … revokedTokens=1 propagationLagMs=…` 확인(수 초 내). 없으면 `Sending to DLQ` 로그와
   `account.locked.dlq` 를 본다.
6. **판정**: `A1` 으로 refresh → **400 `invalid_grant`** 이어야 한다. `B1` 으로 refresh → **200** 이어야 한다(잠그지 않은 계정은 무관).
7. (선택) 운영자 «세션 폐기»(force-logout)도 같은 방식으로 SAS 세션을 끊는지 — 응답 `revokedTokenCount ≥ 1` 이고 그 뒤 refresh 400.
8. 정리: A 잠금 해제 또는 삭제 · 두 계정 폐기.

🔴 **런북 보정 (2026-09-25 UTC)**: 1단계의 일회용 이메일은 **36자 이하**여야 한다(예: `b6a-0925@ex.io`) — 아래 § 기존 결함 때문에 그보다 길면
3단계 대조군 refresh 가 잠금과 무관하게 실패해 판정이 성립하지 않는다.

## 기존 결함 — `refresh_tokens.account_id` VARCHAR(36) (🔴 별도 티켓 필요 · 기안은 코디네이터)

**발견 경위 (2026-09-25 UTC)**: PR #4022 CI `Integration (iam B, Testcontainers)` (run 36115767160, job 108009462700) 에서 이 티켓의 IT 가
`DataIntegrityViolationException: Data too long for column 'account_id'` 로 실패. 스택: `SasRefreshTokenAuthenticationProvider.persistRotation`
(`:498`) ← `authenticate` 의 회전 트랜잭션(`:351,:372`) — **대조군 refresh 단계**(잠금 이전)에서 터졌다. BE-601 코드 경로가 아니다.

**판정: 운영에서도 나는 기존 결함이다 (IT 픽스처만의 문제가 아니다).**

| 사실 | 근거 |
|---|---|
| 컬럼 길이 36 | `V0001__create_credentials_and_refresh_tokens.sql:16` `account_id VARCHAR(36) NOT NULL` (이후 넓힌 마이그레이션 0 — V0014 는 `jti`/`rotated_from` 만). 엔티티 `RefreshTokenJpaEntity.java:24` `length = 36` |
| 그 컬럼에 이메일이 들어간다 | SAS 미러 행이 `authorization.getPrincipalName()`(= 로그인 이메일)을 `account_id` 로 쓴다: `DomainSyncOAuth2AuthorizationService.java:135` · `SasRefreshTokenAuthenticationProvider.java:478` (principal 이름 = 이메일: `CredentialAuthenticationProvider.java:372-373`, `SocialLoginBrowserController.java:185-186`) |
| 이메일 길이 상한은 36 보다 크다 | 가입 `SignupRequest.java:8-10` `@NotBlank @Email` 뿐(길이 제한 없음) · 소셜 `SocialSignupRequest.java:9` 동일 · 저장 `accounts.email VARCHAR(255)`(account-service `V0001__create_accounts_and_profiles.sql:3`) · `credentials.email VARCHAR(320)`(auth `V0006…:15`). ⇒ **37자 이상 이메일은 정상 가입된다** |
| 최초 발급: 실패가 **삼켜진다** | `DomainSyncOAuth2AuthorizationService.java:154-163` `try { save } catch (Exception e) { log.error(...) }` — 토큰은 발급되고 미러 행만 없다(CI 로그의 `SAS_SYNC: failed to persist refresh token to domain store` 가 이 경로) |
| 첫 refresh: **실패한다(삼키지 않음)** | 미러 행이 없으니 `:218` 통과 → 회전 트랜잭션의 `persistRotation` `:498` `refreshTokenRepository.save(newDomainToken)` 가 같은 INSERT 로 실패(`GenerationType.IDENTITY` — `RefreshTokenJpaEntity.java:17-18` — 라 즉시 실행) → 예외가 `authenticate` 밖으로 → **refresh 요청 자체가 오류**. 즉 **이메일이 36자를 넘는 모든 사용자는 refresh 가 불가능**하다(access token TTL 이 지나면 재로그인) |
| 기존 IT 가 안 걸린 이유 | 모든 SAS refresh IT 의 principal 이 짧다: `OAuth2RefreshTokenIntegrationTest.java:162` `user("rt-account-001")`(14자) · `:368` `"cross-tenant-account"` · `OAuth2RevokeIntrospectIntegrationTest.java:213` `"revoke-test-account"`. 이 티켓의 IT 가 처음으로 **실제 모양의 이메일**(`be601-<UUID>@example.com`, 54자)을 principal 로 썼다 |
| BE-601 에 대한 파급 (🔵 코드 읽기 추론 · 미측정) | 긴 이메일 계정에는 미러 행이 없으므로, 이 티켓의 `SasAuthorizationRevocationAdapter` 가 인가를 저장할 때 `DomainSync.save` 가 미러 행 INSERT 를 다시 시도한다. 그 실패는 `:154-163` 에서 삼켜지지만 이번에는 `ForceLogoutUseCase` 의 트랜잭션 **안**이라 `SimpleJpaRepository.save` 의 트랜잭션 프록시가 그 트랜잭션을 rollback-only 로 표시할 가능성이 높다 → 커밋 시 `UnexpectedRollbackException` → 잠금 이벤트는 재시도 후 DLQ, 관리자 force-logout 은 오류 응답. 그 계정은 위 결함으로 이미 refresh 가 불가능하므로 «잠긴 뒤에도 refresh 로 산다» 는 일어나지 않는다 |

**이 PR 에서 한 것 (소유자 결정 없이 컬럼 확장 · principal 매핑 변경은 하지 않는다 — 코디네이터 지시)**: IT 픽스처 이메일을 36자 이하
(`b6-<8hex>@ex.io`, 18자)로 바꿔 BE-601 자체 검증만 초록으로 만들었다. 테스트에 «짧은 이메일은 결함 회피이지 정상 조건 아님» 주석을 남겼다.
**후속 티켓의 결정 후보**(소유자): ⓐ `refresh_tokens.account_id` 를 320 으로 넓힌다(이메일이 `account_id` 라는 이름으로 계속 남는다 — PII · 이름 거짓 유지)
ⓑ 미러 행에 principal details 의 실제 `account_id`(UUID)를 쓴다(`revokeAllByAccountId` 가 SAS 세션도 잡게 된다 · 기존 이메일 키 행 이행 필요 ·
`auth.token.refreshed.accountId` 도 UUID 가 된다 — 계약 확인 필요).

## 후속 (이 티켓 범위 밖 — 이름만)

- 🔴 위 § 기존 결함 — 37자 이상 이메일은 SAS refresh 불가(별도 티켓, 기안은 코디네이터).
- `ConfirmPasswordResetUseCase.java:95` — 비밀번호 재설정 후 SAS 세션 생존(같은 원인). `ForceLogoutUseCase` 경로 재사용 또는 포트 호출 추가.
- reuse 탐지(`SasRefreshTokenAuthenticationProvider.handleReuseDetected`)도 미러 행만 닫고 SAS 인가 행은 그대로 — 다른 인가로의 refresh 는 계속 가능한지 측정 필요.
- `refresh_tokens.account_id` 에 이메일이 들어가는 SAS 미러 행(`DomainSync…:135`, `persistRotation :478`) — `auth.token.refreshed` 의 `accountId` 도 이메일(PII)이다.
- `processed_events` 정리 작업(행 무한 성장) · consumer lag 메트릭/알림 · `account.deleted` 소비자(계약이 약속하지만 코드 0).

# Related Specs

- `specs/contracts/events/account-events.md`(또는 `account.locked` 를 정의하는 파일) · `specs/services/auth-service/architecture.md`
- `TASK-BE-600` (새 로그인 거부 · AC-0 ① 이 이 티켓의 출처)
- `TASK-BE-468` (테넌트 한정 force-logout)

# Related Contracts

- `account.locked` — 스키마 불변, 소비자 추가.
- `POST /internal/auth/accounts/{id}/force-logout` — 불변(같은 유스케이스를 이벤트로도 부른다).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 잠금 직후 해제 | 폐기는 이미 일어났다 — 사용자는 다시 로그인한다(정상) |
| 이벤트가 늦게 온다 | 그 사이 갱신은 성공할 수 있다 — 수용한 틈(소유자 결정). 로그로 지연을 볼 수 있게 한다 |
| auth-service 가 내려가 있을 때 발행 | Kafka 가 보관 — 복귀 후 소비(오프셋) |

# Failure Scenarios

1. **자체 refresh 저장소만 지운다** → SAS 인가 저장소로 계속 갱신(AC-0 ①).
2. **소비자가 실패를 삼킨다** → 잠금은 됐는데 세션은 산다, 그리고 아무도 모른다.
3. **단위 테스트로 닫는다** → 실제 갱신 거부는 모른다(AC-3).
