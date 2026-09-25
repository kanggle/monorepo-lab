# Task ID

TASK-BE-603

# Status

review

# Title

🔴 갱신 토큰 미러 행의 `account_id` 에 이메일이 들어간다 — 36자 넘는 이메일 사용자는 토큰 갱신이 안 된다

# Owner

iam-platform

# Task Tags

- auth-service
- oauth2
- data-model
- pii

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 — 기존 행 이행 · 이벤트 의미 변경(소비자 영향) · SAS 저장소와의 정합.

---

# Goal

`TASK-BE-601` 의 통합 테스트가 CI 에서 처음으로 **실제 모양의 이메일(54자)** 을 쓰자 드러났다(2026-09-25): SAS 갱신 토큰의 미러 행
(`refresh_tokens`)이 `account_id` 칸에 **계정 UUID 가 아니라 로그인 이메일**을 넣는다. 칸은 `VARCHAR(36)` 이다.

- **사용자 피해**: 이메일이 36자를 넘으면 첫 발급 때 미러 INSERT 가 **조용히 삼켜지고**(`DomainSyncOAuth2AuthorizationService.java:154-163`),
  첫 refresh 에서 `SasRefreshTokenAuthenticationProvider.persistRotation:498` 의 같은 INSERT 는 **삼켜지지 않아** 갱신 요청이 실패한다
  ⇒ **긴 이메일 사용자는 토큰 갱신이 전혀 안 된다.**
- **의미 결함**: 같은 원인으로 `accountId` 로 폐기하는 쿼리(`RefreshTokenJpaRepository.java:18`)가 SAS 경로 행을 **못 맞힌다** — BE-601 이
  그래서 이메일로 찾는 폐기 어댑터를 따로 만들어야 했다. `auth.token.refreshed` 이벤트의 `accountId` 에도 **이메일(PII)** 이 실린다.

🔵 **소유자 결정 (2026-09-25 UTC)** — **ⓑ 미러 행에 실제 계정 UUID 를 쓴다.** 기각: ⓐ 칸을 320자로 넓힘(갱신 실패는 고치지만 «account_id 에
이메일» 이라는 잘못된 의미와 이벤트의 PII 가 남는다).

## 실측 (2026-09-25 UTC · BE-601 보고 + 직접 확인)

| 사실 | 근거 |
|---|---|
| 칸 길이 36 | `auth-service/…/db/migration/V0001__create_credentials_and_refresh_tokens.sql:16` `account_id VARCHAR(36)` (넓힌 마이그레이션 없음 — V0014 는 `jti`·`rotated_from` 만) · `RefreshTokenJpaEntity.java:24` `length = 36` |
| 들어가는 값 = principal name = 이메일 | `DomainSyncOAuth2AuthorizationService.java:135` · `SasRefreshTokenAuthenticationProvider.java:478` ← principal: `CredentialAuthenticationProvider.java:372-373` · `SocialLoginBrowserController.java:185-186` |
| 가입 이메일 길이 제한 없음 | `SignupRequest.java:8-10` · `SocialSignupRequest.java:9` (`@Email` 뿐) · `accounts.email VARCHAR(255)` · `credentials.email VARCHAR(320)` |
| 기존 IT 가 못 잡은 이유 | principal 이 전부 짧다(`"rt-account-001"` 등) — BE-601 IT 가 첫 실제 모양 이메일 |
| 🔵 데모는 안 걸린다 | 데모 계정 이메일이 짧다(`demo@demo.com` 등) — **데모 초록이 이 결함의 부재 증거가 아니다** |

# Scope

## 포함

- 미러 행에 **계정 UUID** 를 쓴다. 출처 = SAS 인가의 principal details(`account_id` — BE-601 폐기 어댑터가 이미 이것으로 계정을 판별한다).
- `auth.token.refreshed` 등 미러 행에서 `accountId` 를 채우는 이벤트가 **UUID** 를 싣게 한다 — 🔴 **소비자 영향 검토**(security-service
  `TokenRefreshedConsumer` · login_history 가 지금 이메일을 accountId 로 받아 왔다: 규칙·이력 조인이 이메일 전제였는지).
- **기존 행**: 이메일이 들어 있는 행을 어떻게 할지(이행 · 만료 대기 · 병행 허용) — AC-0 결정.
- BE-601 의 이메일 기반 폐기 어댑터를 UUID 기반으로 단순화할 수 있는지(가능하면) — 🔴 기존 행이 남아 있는 동안은 둘 다 필요할 수 있다.

## 제외

- principal name 자체를 UUID 로 바꾸는 것(SAS 세션·introspection 전반 영향) — 필요하면 별도 판단.

# Acceptance Criteria

- [x] **AC-0** — 기존 행 처리 결정(이행 마이그레이션 vs 만료 대기 — refresh TTL 로 자연 소멸하는 기간) · 이벤트 의미 변경의 소비자 목록과 영향.
  → **만료 대기(이행 없음) + 재사용 탐지 한 곳만 배수 기간 이중 키.** 판독자·소비자 표와 근거는 아래 § AC-0.
- [x] **AC-1** — 구현 + 단위 테스트: 긴 이메일(> 36자) 계정의 발급 → 미러 행 저장 성공(UUID) → refresh 성공. 🔴 픽스처는 **실제 모양의 긴 이메일**을 쓴다(BE-601 IT 의 짧은 이메일은 이 결함 회피였다 — 그 픽스처도 되돌려라).
  → 구현 = 신규 `AuthorizationAccountId`(details `account_id`) 를 발급(`DomainSyncOAuth2AuthorizationService.java:137`) · 회전(`SasRefreshTokenAuthenticationProvider.java:482`) · 이벤트(`:207` tenant-mismatch · `:384` refreshed) · 재사용(`:527`) 전부에 적용. 단위 4셀(54자 이메일 `first.last.long-name+tag@subdomain.example-company.com`) — **제거 검사 3/3 빨강**(아래 § 검증). IT = `OAuth2RefreshTokenIntegrationTest` `@Order(8)` — 발급 직후 **미러 행 존재 + account_id = UUID 를 단언**(발급 INSERT 의 삼킴이 초록의 이유가 되지 못하게) → refresh 200 → 새 행 UUID. BE-601 IT 픽스처를 54자 이메일(`be601-<UUID>@example.com`)로 **되돌림**. 🔴 IT 는 로컬 Docker 부재로 **SKIPPED — CI 판정**.
- [x] **AC-2** — `accountId` 로 폐기가 SAS 경로 행을 맞힌다(BE-601 의 우회가 필요 없어졌는지 판정).
  → 같은 IT 가 `revokeAllByAccountId(uuid)` = 1(살아 있는 SAS 미러 행) 을 단언하고, **그것만으로** 다음 refresh 가 `400 invalid_grant` 임을 단언한다(CI 판정). **판정: BE-601 어댑터는 계속 필요하다** — § AC-0 ③.
- [x] **AC-3** — 계약·데이터 모델 문서(`data-model.md` · `auth-events.md` 의 `accountId` 의미) 갱신.
  → `data-model.md` `refresh_tokens.account_id` 행(UUID · 폴백 규칙 · 배수 기간) · `auth-events.md` `auth.token.refreshed` / `auth.token.reuse.detected` / `auth.token.tenant.mismatch` 필드 노트. 소비자 코드 변경 = 없음(소비자는 처음부터 UUID 전제 — § AC-0 ②).

## AC-0 — 판독자 · 소비자 실측과 결정 (2026-09-25 UTC)

### ① `refresh_tokens.account_id` 를 읽는 곳 (auth-service `src/main` 전수 — `revokeAllByAccountId` · `findActiveJtisByAccountId` · `RefreshToken.getAccountId()`)

| 판독자 | 무엇으로 찾나 | BE-603 이전 | 배수 기간(이메일 행이 남은 30일) | 조치 |
|---|---|---|---|---|
| SAS 재사용 탐지 `SasRefreshTokenAuthenticationProvider.handleReuseDetected` (`:527` → `:609`) | principal **이름**(이메일) | 이메일 행은 맞힘(같은 이메일의 다른 테넌트 계정 행까지 과폐기 — 이중 키의 두 번째 UPDATE 가 배수 기간 동안 이 동작을 그대로 잇는다) · 기기 세션 조회(`:541`) · Redis invalidate-all 마커(`:590`) · 이벤트는 **이메일**로 키 → 전부 헛방 | UUID 만 쓰면 **아직 회전 안 한 다른 세션(이메일 행)** 을 못 폐기 = **회귀** | 🔴 **이중 키**: UUID + (다르면) principal name 두 번 UPDATE(`:609-613`). 배수 후엔 두 번째가 0행 — 무해 |
| 관리자/잠금 강제 로그아웃 `ForceLogoutUseCase.java:68` | UUID | SAS 미러 행 **못 맞힘** — BE-601 어댑터(`:72`)가 인가 자체를 무효화해 막음 | 이메일 행은 `:68` 이 못 맞히지만 어댑터가 인가 + 미러 행(jti) 을 닫음 → **누락 없음** | 없음 |
| 비밀번호 재설정 `ConfirmPasswordResetUseCase.java:95` | UUID | SAS 세션 **전부 못 맞힘**(BE-601 이 이름만 남긴 형제 결함) | 회전한 세션(= UUID 행)은 **이제 맞힘**, 회전 안 한 유휴 세션만 여전히 샘 — **이전보다 좁아짐, 회귀 아님** | 🔵 범위 밖 — BE-601 § 후속 후보 그대로(포트 호출 추가). 이 티켓 뒤엔 배수 기간 한정 구멍 |
| 레거시 `RefreshTokenUseCase.java:198` (재사용) | JWT claim 의 accountId(UUID) | 레거시 행만 | 변화 없음(SAS 불투명 토큰은 이 경로 파싱 불가) | 없음 |
| `findActiveJtisByAccountId` | — | `src/main` 호출자 0 | — | 없음 |
| jti 기반 조회(`findByJti` 재사용·만료·폐기 판정, `DomainSync.revokeRefreshTokenInDomainStore`, 어댑터의 jti 폐기) | jti | account_id 무관 | 무관 | 없음 |
| 세션 목록 · 단건 폐기 · 다른 세션 모두 폐기 · 로그아웃 (`RevokeSessionUseCase:41` · `RevokeAllOtherSessionsUseCase:44` · `LogoutUseCase:87` · `GetCurrentSessionUseCase:25`) | **`device_sessions`**.account_id | SAS 경로는 기기 세션을 만들지 않고 미러 행 `device_id` = null(`persistRotation` · `DomainSync`) | 무관 | 없음 |

⇒ **사용자 가시·보안상 누락이 배수 기간에 새로 생기는 판독자는 재사용 탐지 하나** — 그 한 곳만 (a) 이중 키로 막았다. (b) 이행 마이그레이션은 기각:
미러 행 `tenant_id` 가 경로마다 출처가 다르고(발급 = 액세스 토큰 claim · 회전 = **클라이언트** 테넌트) 소셜 전용 계정은 자격 행이 없으며 이메일은 테넌트 간 중복 가능 —
`credentials`/`social_identities` 조인으로 이메일 → UUID 를 안전하게 확정할 수 없다. 못 맞힌 행은 어차피 남으므로 이행이 배수를 없애지도 못한다.

### ② 미러 행 / principal name 에서 `accountId` 를 채우는 이벤트와 모든 소비자 (`projects/*/apps` 전수)

| 이벤트 (SAS 경로 발행 지점) | 소비자 | 키 가정 | 이메일일 때 실제로 일어나던 일 |
|---|---|---|---|
| `auth.token.refreshed` (`:384`) | security-service `TokenRefreshedConsumer` → `login_history`(outcome=REFRESH) | **UUID** — `login_history.account_id VARCHAR(36)`(security `V0001:4`) · `account.deleted` 익명화는 UUID 로 찾음 | 36자 초과 = 적재 실패 → 재시도 → DLQ · 이하 = UUID 이력과 **갈라진 PII 행**(익명화가 못 찾음) |
| `auth.token.reuse.detected` (`:570`, `:527` 의 accountId) | security-service `TokenReuseDetectedConsumer` → `TokenReuseRule`(`reuse:{tenant}:{accountId}` 카운터) → 자동 잠금 `AccountServiceClient:63` `/internal/accounts/{accountId}/lock` · `suspicious_events.account_id VARCHAR(36)`(`V0006:3`) | **UUID** | 자동 잠금이 **이메일로 호출**(잠금 불가) · 36자 초과면 의심 이벤트 적재 실패 |
| `auth.session.revoked` (재사용 cascade `:579`) | 없음(`auth-events.md` 가 이미 «미구현» 명시) | — | SAS 경로는 기기 세션이 없어 실제로는 발행되지 않음 |
| `auth.token.tenant.mismatch` (`:207`) | 없음 — security-service 리스너 0(계약 문서의 «Consumers: security-service» 는 코드와 불일치, 🔵 이 티켓 범위 밖 · 기록만) | — | — |
| Kafka 파티션 키(outbox `aggregate_id` = accountId, `OutboxAuthEventPublisher:199,220,237`) | 전 소비자 | UUID | 같은 계정의 로그인 이벤트(UUID)와 **다른 파티션** — 순서 보장 깨짐 |

⇒ **소비자 코드는 이메일을 전제한 곳이 없다** — 전부 UUID 전제였고 이메일 값이 조용한 불일치였다. 소비자 변경 없음, 계약 문서의 의미만 바로잡았다.
🔵 **남는 데이터**: security-service `login_history` 에 이미 들어간 이메일 키 REFRESH 행(36자 이하 이메일)은 이행하지 않았다 — `account.deleted` 익명화가 닿지 않는 PII 잔존물. 보존 정책/정리는 별도 판단(후속 후보).

### ③ BE-601 이메일 기반 폐기 어댑터 — 단순화 불가, 유지

- SAS 인가 테이블(`oauth2_authorization.principal_name`)은 **여전히 로그인 이메일**이다 — 이 티켓은 principal name 을 바꾸지 않는다(제외 범위). 인가 자체를 닫는 방법은 여전히 «이메일 후보 → details `account_id` 로 확정» 뿐이다.
- `revokeAllByAccountId(uuid)` 는 이제 새 미러 행을 맞히지만(AC-2) **미러 행만** 닫는다. 미러 행이 없으면 provider 는 SAS 인가만으로 통과시키므로(발급 INSERT 가 삼켜지는 경로), 인가를 닫는 어댑터가 권위로 남는다.
- 변경 = details 판독을 공용 `AuthorizationAccountId.fromPrincipalDetails` 로 옮기고(`SasAuthorizationRevocationAdapter.java:139`) 클래스·포트 javadoc 의 «미러 행도 이메일 키» 서술을 정정.

### ④ details 에 `account_id` 가 없을 때 — **principal name 폴백 + WARN** (fail-loud 기각)

- 운영 principal 생산자는 둘뿐이고(`CredentialAuthenticationProvider.java:368` · `SocialLoginBrowserController.java:178`) 둘 다 details 를 채운다 — `src/main` 의 `new UsernamePasswordAuthenticationToken(` 전수 2건.
- 폴백 규칙은 토큰 `sub` 의 규칙(`TenantClaimTokenCustomizer.alignSubToAccountId` — details 없으면 framework 기본값 유지)과 **같다** ⇒ 미러 행은 항상 그 세션의 토큰이 싣는 주체와 같은 키. fail-loud 는 `sub` 쪽이 명시적으로 택하지 않은 «details 없는 principal 은 발급 거부» 라는 새 정책이 되고, `user("…")` principal 을 쓰는 기존 IT 5개(8곳)를 Docker 없이 고쳐야 한다.
- 조용하지 않게: 폴백 시 WARN(인가 id 만 — 이메일 로그 안 함). 새 생산자가 details 를 빠뜨리면 로그에 보인다.
- 🔴 이 결정은 브리프의 선호(«이메일을 다시 쓰지 말 것»)에서 벗어난다 — 운영 경로에선 폴백이 발생하지 않는다는 실측이 근거. 소유자가 fail-loud 를 원하면 `AuthorizationAccountId.forMirrorRow` 한 곳 + IT 픽스처 5개 교체.

## 검증 (2026-09-25 UTC)

| 무엇 | 명령 | 결과 |
|---|---|---|
| auth-service 단위 전체 | `./gradlew :projects:iam-platform:apps:auth-service:test` | BUILD SUCCESSFUL (rc=0) — **797 테스트 / 실패 0 / 오류 0 / 건너뜀 28**(BE-601 의 793 + 새 4셀; 건너뜀은 기존분) · provider 12/0 · DomainSync 11/0 · `SasAuthorizationRevocationAdapterTest` 7/0 |
| 제거 검사 | `forMirrorRow` 를 principal name 반환으로 임시 변경 후 위 두 클래스 | **3/3 새 셀 빨강**(23 중 3 실패) — 폴백 셀은 설계상 통과. 원복 후 재실행 초록 |
| IT (`OAuth2RefreshTokenIntegrationTest` 8셀) | `./gradlew :projects:iam-platform:apps:auth-service:integrationTest --tests '*OAuth2RefreshTokenIntegrationTest'` | ⚪ **rc=0 이지만 8/8 SKIPPED** — Testcontainers «Could not find a valid Docker environment»(`docker info`: `open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified`). **rc=0 은 초록이 아니다 — CI 판정.** `AccountLockedSessionRevocationIntegrationTest`(픽스처 되돌림)도 같은 이유로 CI 판정 |

# Related Specs

- `specs/services/auth-service/data-model.md` · `specs/contracts/events/auth-events.md`
- `TASK-BE-601` (발견 · 이메일 기반 폐기 어댑터 · 짧은 이메일 IT 픽스처)

# Related Contracts

- `auth.token.refreshed` 등 — 필드 불변, **값의 의미가 이메일 → UUID** 로 바로잡힌다(소비자 검토 필수).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 배포 시점에 이메일이 든 기존 행 | AC-0 결정대로 — 폐기·조회가 두 모양을 다 처리하거나, 이행으로 하나로 |
| 소셜 로그인 principal | 이메일이 없는 공급자 신원이면 principal 이 무엇인가부터 확인(UUID 출처는 details) |

# Failure Scenarios

1. **칸만 넓힌다** → 기각된 ⓐ. 의미·PII 가 남는다.
2. **짧은 이메일로 테스트한다** → 결함이 다시 안 보인다(AC-1 🔴).
3. **이벤트 소비자를 안 본다** → security-service 이력이 이메일/UUID 혼재로 갈라진다.
