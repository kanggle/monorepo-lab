# Task ID

TASK-BE-606

# Status

review

# Title

🔴 SAS 경로의 refresh 재사용 탐지가 **회전된 토큰의 재제출**에 발동하지 않는다 — identity-platform MUST 위반

# Owner

iam-platform

# Task Tags

- auth-service
- oauth2
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 — 재사용 판정이 계정 전체 폐기와 자동 잠금 입력으로 이어진다.

---

# Goal

`TASK-BE-604` § ⑦·⑩-2 가 코드로 확인했다(2026-09-26 UTC): SAS 는 인가에 **현재** refresh 토큰만 저장한다 ⇒ 이미 회전된 옛 토큰을 다시 내면
`authorizationService.findByToken` 이 먼저 실패해 `invalid_grant` 로 끝나고, `SasRefreshTokenAuthenticationProvider` 의 재사용 분기(계정 전체 폐기 ·
`auth.token.reuse.detected` 발행)에 **도달하지 않는다**. `platform/service-types/identity-platform.md:88` 의 MUST(재사용 탐지 시 토큰 패밀리 폐기)와 어긋난다 — BE-604 이전부터.

🔴 반대 방향 위험(BE-604 가 새로 만든 것): 재사용 분기에 닿는 유일한 모양은 **같은 토큰의 동시 refresh 두 건**(콘솔 멀티 탭 경쟁)이다. BE-604 가 기본 provider 를
제거했으므로 그 경쟁이 나면 계정의 SAS 세션이 **실제로 전부 끝나고**, `auth.token.reuse.detected` 가 security-service 자동 잠금의 입력이 된다(⚪ 미측정).

# Scope

## 포함

- **AC-0**: ① 옛 토큰 재제출을 탐지할 근거 — 미러 행(`refresh_tokens.jti` + `rotated_from`)으로 «회전된 적 있는 jti» 를 `findByToken` **전에** 조회하는 모양이 가능한가 ·
  ② 동시 refresh 경쟁을 재사용과 구별할 수 있는가(유예 창 · 같은 기기/세션) · ③ 탐지 시 동작(패밀리 폐기 · 이벤트)과 자동 잠금 점수(`TokenReuseRule` 고정 100)의 적정성 — 🔴 소유자 결정 항목 포함.
- 결정대로 구현 + IT.

## 제외

- 레거시 `RefreshTokenUseCase` 경로(자체 탐지 보유).

# Acceptance Criteria

- [x] **AC-0** — 위 ①②③ 판독 + 소유자 결정(특히 ② 의 오탐 허용 범위). → § AC-0 판독 · 소유자 결정
- [x] **AC-1** — IT: 로그인 → refresh(A→B) → **A 재제출** → 400 + 계정의 다른 SAS 세션 refresh 거부 + `auth.token.reuse.detected` 1건. → § 구현 · 검증 (IT 는 ⚪ 로컬 미실행 — Docker 부재, CI 판정)
- [x] **AC-2** — IT: 결정한 대로 동시 refresh 경쟁이 재사용으로 오판되지 않는다(또는 결정한 정책대로 동작). → § 구현 · 검증 (IT ⚪ CI 판정 · 단위 green)
- [x] **AC-3** — `identity-platform.md:88` MUST 충족 근거를 티켓에 file:line 으로. → § AC-3

# Related Specs

- `platform/service-types/identity-platform.md` · `specs/services/auth-service/architecture.md` · `specs/contracts/events/auth-events.md`
- `TASK-BE-604`(발견 · § ⑦) · security-service `TokenReuseRule`

# Related Contracts

- `auth.token.reuse.detected` — 발행 조건이 바뀌면 계약 먼저.

# Edge Cases

| 상황 | 기대 |
|---|---|
| 콘솔 멀티 탭이 같은 refresh 토큰을 동시에 제출 | AC-0 ② 결정대로 — 계정 잠금으로 번지지 않게 |
| BE-603 이전 이메일 키 미러 행(배수 기간) | 탐지 조회가 jti 기반이면 무관 |

# Failure Scenarios

1. **`findByToken` 뒤에서만 판정한다** → 지금과 같다(도달 불가).
2. **경쟁을 재사용으로 본다** → 멀티 탭 사용자가 자동 잠금된다.

---

# AC-0 판독 · 소유자 결정 (2026-09-26 UTC)

## 판독 (main `16b45493e` 기준 — 착수 시 재확인)

- **(a) 회전된 토큰 재제출**: `SasRefreshTokenAuthenticationProvider` :159-163 `findByToken` → null → 400. 재사용 분기(:182-194)에 도달하지 않음 — 아무것도 폐기되지 않고 이벤트도 없다. `OAuth2RefreshTokenIntegrationTest` `@Order(4)` 의 400 은 재사용 탐지가 아니라 이 경로였다.
- **(b) 같은 토큰의 동시 refresh**:
  ① 패자가 승자 커밋 **뒤에** 읽음 → `findByToken` null → 400. 콘솔은 이 모양을 전제로 설계됨(`platform-console/apps/console-web/src/app/api/auth/refresh/route.ts:147-156` — 2초 대기 후 재시도).
  ② 둘 다 커밋 **전에** 읽음 → 둘 다 200, 미러에 `rotated_from=A` 자식 **둘**(`V0001:25` 비고유 인덱스) → `RefreshTokenJpaRepository.findByRotatedFrom`(`Optional`, :24)이 이후 조회에서 `IncorrectResultSizeDataAccessException` — 재사용 핸들러 자신이 쓰던 조회(P:567).
  ③ 좁은 창(인가는 커밋 전 읽고 미러는 커밋 후 읽음) → `TokenReuseDetector.java:36` true → `handleReuseDetected`(P:550)가 승자 포함 패밀리 전체 폐기 + 이벤트 → security `TokenReuseRule` 100 고정(`TokenReuseRule.java:27,57`) → AUTO_LOCK(≥80, `RiskLevel.java:22`) → 잠금 → `account.locked` → 전 세션 폐기.
- **(c)** 재사용 핸들러가 `OAuthAuthorizationRevocationPort` 를 부르지 않았다 — 미러 행이 없거나(최초 INSERT 삼킴) 이메일 키(BE-603 이전)인 SAS 세션은 살아남는다.
- **(d)** 미러 `jti`/`rotated_from` = SAS refresh 토큰 원문(DomainSync :128,146 · P:522,527). 콜레이션 `utf8mb4_unicode_ci` = **대소문자 무시 비교** → 조회 결과를 자바에서 정확 일치로 다시 거른다(구현 참조).

## 소유자 결정

1. **선택지 B, N = 30초.** `findByToken` **전에** 미러 표로 판정: `children = findAllByRotatedFrom(제출 토큰)`(List — 자식 둘에서 예외 없음). 비었으면 정상 흐름. **유예** = 자식이 정확히 하나 **그리고** 그 자식이 아직 사슬의 머리(`!existsByRotatedFrom(child)`) **그리고** `now - child.issuedAt ≤ 30s` → 400 `invalid_grant`, 폐기·이벤트 없음(로그만, 토큰 값 미기록). 그 밖 → **재사용**: `revokeAllByAccountId` + `OAuthAuthorizationRevocationPort.revokeActiveRefreshTokens` + `auth.token.reuse.detected` + 400. 유예 초는 이름 붙은 설정(기본 30). 제출 토큰 자신의 미러 행 존재에 기대지 않는다. 기존 in-flow 재사용 경로와 일관(이중 발행 금지).
2. **잠금 정책**: `TokenReuseRule` — 재사용 1건 = ALERT 대역(70), 같은 계정 1시간 카운터(`TokenReuseRule.java:50`) 안 2건 이상 = AUTO_LOCK(100). **계약 먼저**, 탐지 변경과 **같은 PR**.
3. 프런트 코드 변경 없음 — 후속 후보로 기록(아래 § 후속).

# 구현 · 검증

## 설계 (file:line — 이 브랜치)

- **판정 = 도메인 정책** `apps/auth-service/src/main/java/com/example/auth/domain/token/RotatedTokenReplayPolicy.java` — `assess` :91 · 콜레이션 대비 정확 일치 필터 :99 · 자식 1개 조건 :105 · 창 비교 :113(경계 포함 — 정확히 30s 는 유예) · 머리 조건 :118. 프레임워크 import 없음.
- **사전 검사(`findByToken` 전)** `SasRefreshTokenAuthenticationProvider.java:194-196`. 유예/재사용 응답 = `rejectReplay` :601. 유예는 `invalid_grant`(설명 없음), 재사용은 기존과 같은 `error_description`("…reuse detected…").
- **in-flow 경로(경쟁 ③)도 같은 정책** :237-239 — `TokenReuseDetector.isReuse` 가 참일 때 정책을 다시 물어 유예면 폐기 없이 400. 사전 검사가 자식을 보면 여기 오기 전에 던지므로 **한 요청에서 재사용 처리는 한 번뿐**(이중 발행 없음).
- **패밀리 폐기** `handleReuse` :655 — 미러 행 `revokeAllByAccountId`(UUID :799 + BE-603 배수 기간 이메일 키 :802) · 기기 세션 · **SAS 인가** `revokeActiveRefreshTokens` :721 — 한 트랜잭션. Redis invalidate-all :748 은 트랜잭션 밖(기존과 같음).
- **계정 식별(사전 검사 경로)**: 회전된 토큰은 자기 인가가 없다. `findFamilyAuthorization` :766 이 사슬을 머리까지 내려가(최대 64행, `MAX_FAMILY_WALK`) 머리를 쥔 SAS 인가를 찾는다 → `AuthorizationAccountId.forMirrorRow`(UUID) + principal name(이메일 키 미러 행용). 머리를 쥔 인가가 없으면(패밀리가 이미 다 닫힘) 가장 이른 자식 행의 `account_id` — BE-603 이후 행은 UUID, 이전 행은 이메일(그 이메일 키 형제 행은 `revokeAllByAccountId(email)` 로 닿고, SAS 포트는 자격 행을 못 찾아 0 — 살아 있는 인가가 있었다면 머리 탐색이 찾았을 것).
- **폐기 0건 ⇒ 이벤트 없음** :724 — 이미 닫힌 패밀리의 재제출. BE-606 이전 규칙(«이미 폐기 + 0건이면 생략»)과 같고, «같은 옛 토큰 두 번 = 이벤트 두 건 = 잠금» 을 막는다. `revokedCount` = 미러 행 + SAS 인가(`ForceLogoutUseCase` 와 같은 합산 — 계약 필드 노트 추가).
- **유예 설정** `auth.refresh-token.reuse-grace-seconds`(기본 30, env `AUTH_REFRESH_REUSE_GRACE_SECONDS`) — `application.yml:148` · `AuthorizationServerConfig.java:127,423`. 시계 = 주입 `Clock`(운영 `Clock.systemUTC()`); 회전 행의 `issued_at` 도 같은 시계에서(`persistRotation` 호출부).
- **포트 변경**: `RefreshTokenRepository.findByRotatedFrom`(Optional) → `findAllByRotatedFrom`(List) — 호출부 3곳(SAS provider · 레거시 `RefreshTokenUseCase` = 가장 이른 자식 · JPA 슬라이스 테스트).
- 순환 참조 회피: 포트는 `@Autowired` 필드가 아니라 `authorizationServerSecurityFilterChain` @Bean **메서드 파라미터**로 받는다(어댑터가 이 설정 클래스가 만드는 `OAuth2AuthorizationService` 에 의존).
- 로그에서 토큰 값 제거: 재사용 WARN · 회전 DEBUG(identity-platform: refresh 토큰 로그 금지).
- **security-service** `TokenReuseRule.java:84-85` — `count >= lockThreshold` → 100, 1건 → 70, 카운터 0(Redis 장애) → 100(**fail-closed — 내 판단, 아래 § 이탈**). 설정 `security.detection.token-reuse.{single-score,repeated-score,lock-threshold}` (70/100/2) — `DetectionProperties.TokenReuse` · `DetectionConfig`.

## 계약 · 스펙 (코드보다 먼저)

- `specs/contracts/events/auth-events.md` § auth.token.reuse.detected — **발행 조건**(유예/재사용/0건 미발행) 신설, `revokedCount`·`originalRotationAt`·`tenantId` 필드 노트, **Consumers** 줄 «즉시 `auto.lock.triggered`» → «1건 ALERT · 1시간 안 2건 이상 AUTO_LOCK · 카운터 불가 AUTO_LOCK».
- `specs/features/abnormal-login-detection.md` § TokenReuseRule · Business Rules · `specs/services/security-service/dependencies.md:52` · `specs/use-cases/refresh-token-rotation.md` UC-4(8·9단계 · SAS 대체 흐름 · Post-Condition).

## 테스트 (2026-09-26 UTC, 이 worktree)

| 명령 | rc | 결과 |
|---|---|---|
| `./gradlew :projects:iam-platform:apps:auth-service:test` | 0 | 850 tests · 실패 0 · skip 31 (Docker 의존 슬라이스) |
| `./gradlew :projects:iam-platform:apps:security-service:test` | 0 | 245 tests · 실패 0 · skip 13 |

- 신규 단위: `RotatedTokenReplayPolicyTest` 7 · `SasRefreshTokenAuthenticationProviderTest` +7 (AC-1 단위: `findByToken(A)` 한 번도 안 불림 · 미러 + SAS 폐기 · 이벤트 1건 / AC-2 단위: 유예 → 폐기·이벤트·SAS 조회 0, 손자 있음 → 재사용, 자식 둘 → 예외 없이 재사용 + originalRotationAt = 가장 이른 자식, in-flow 경쟁 유예, 이미 닫힌 패밀리 → 이벤트 없음, 머리 인가 없음 → 자식 account_id) · `TokenReuseRuleTest` +5(1건 ALERT · 2건째 AUTO_LOCK · 이후 AUTO_LOCK · 설정 준수 · 설정 검증) · JPA 슬라이스 +1(자식 둘 → 둘 다 반환).
- 신규 IT `SasRefreshTokenReplayIntegrationTest`(3 셀: AC-1 · AC-2 유예 · AC-2 자식 둘) + `OAuth2RefreshTokenIntegrationTest` `@Order(10)` 자식 행을 60초 전으로(지금 시각이면 유예 판정). 🔴 **⚪ 로컬 미실행** — Docker 없음(`docker info` rc=1), `integrationTest` 태스크 · CI 가 판정. 시계 제어 = `UPDATE … issued_at = issued_at - INTERVAL ? SECOND`(상대 이동 — 절대 `Timestamp` 바인딩은 KST 호스트에서 +9h 미래가 된다).

## bite (코드를 깨고 → 실패 확인 → 복구)

| 깨뜨린 것 | 결과 |
|---|---|
| provider: 재사용 시 `revokeActiveRefreshTokens` 호출 제거 | 단위 3 실패(AC-1 단위 · 닫힌 패밀리 · 머리 없음) → 복구 후 green |
| provider 테스트: 유예 30s → 0s | 단위 3 실패(유예 · in-flow 유예 · 손자 셀의 스텁 미사용) → 복구 |
| security: `repeated = true`(1건도 잠금 = BE-606 이전 동작) | 단위 4 실패(1건 ALERT · 2건째 · 교차 테넌트 · 설정 준수) → 복구 후 green |
| ⚪ provider: 사전 검사 비활성 · 정책: 유예 무조건 거부 | **분류기(auto mode, "Security Weaken")가 편집을 차단** — 미측정. 우회하지 않음. |

## AC-3 — `platform/service-types/identity-platform.md:88` MUST 충족 근거

> «if a refresh token is presented after it has already been rotated, the entire refresh-token family MUST be revoked and the user re-prompted to authenticate. This event MUST be audited and alerted.»

- «presented after it has already been rotated» 를 **탐지**한다 — SAS 가 그 토큰을 잊은 뒤에도: `SasRefreshTokenAuthenticationProvider.java:194-196` (사전 검사) · `RotatedTokenReplayPolicy.java:91-118`.
- «entire refresh-token family MUST be revoked»: 미러 행 `:799,:802` · 기기 세션(`doRevokeAllForReuse`) · SAS 인가 `:721`. 사슬 전체가 계정 키로 폐기된다(패밀리 ⊆ 계정 — 더 넓게 폐기).
- «user re-prompted to authenticate»: 폐기 후 그 계정의 모든 refresh 가 400 `invalid_grant`(BE-604 로 미러 행 폐기가 최종) + SAS 인가 무효화 → 재로그인 필요. IT `SasRefreshTokenReplayIntegrationTest` AC-1 셀이 다른 세션 · B 의 refresh 400 을 단언.
- «audited and alerted»: `auth.token.reuse.detected` outbox 행 `:728` → security-service `login_history`(TOKEN_REUSE) + `TokenReuseRule` 은 **1건이면 ALERT**(`suspicious_events` + `security.suspicious.detected`), 반복이면 잠금 `TokenReuseRule.java:84-85`.
- 🔵 **유예는 MUST 의 예외가 아니라 판정이다**: 30초 안 · 자식 하나 · 자식이 머리인 재제출은 같은 요청의 경쟁으로 보고 400 만 준다(소유자 결정 B). 그 창 밖 · 사슬이 갈라졌거나 이미 전진했으면 전부 재사용.

## 이탈 · 내 판단 (소유자 확인 권장)

1. **Redis 카운터 불가(0) → AUTO_LOCK**(fail-closed). 결정문에 없던 칸. 근거: 장애 중 모든 재사용이 조용히 경보로 내려가는 것보다 BE-606 이전 동작 유지가 안전. 반대 선택(경보)도 합리적 — 소유자 판단 대상.
2. **폐기 0건이면 이벤트 없음**을 사전 검사 경로에도 적용 — 기존 in-flow 규칙과 같은 모양. 대가: 이미 닫힌 패밀리의 재제출은 감사 이벤트가 남지 않는다(WARN 로그만).
3. **유예 경로 = 로그만, 메트릭 없음**. provider 에 `MeterRegistry` 가 없어 추가하지 않았다.
4. `SasRefreshTokenAuthenticationProvider` 생성자에 3 인자 추가(포트 · 정책 · Clock) — 테스트 2곳 갱신.
5. 레거시 `RefreshTokenUseCase` 는 범위 밖이지만 포트 시그니처 변경 때문에 `originalRotationAt` 계산 한 줄만 바뀜(가장 이른 자식). 동작 불변.

## 후속 후보

- 콘솔 POST 경로가 400 에서 세션을 지운다(`route.ts:70-71`) — 유예 400 에도 로그아웃된다. 유예 응답을 구별할 신호가 필요한지.
- 응답 유실 후 **30초 넘어** 재시도하는 늦은 재제출은 여전히 오탐(재사용 1건 = 패밀리 폐기 + ALERT, 잠금은 아님).
- «잠금 → 해제» 경로 미검증(ALERT/AUTO_LOCK 분기 이후 운영자 unlock 흐름).
- `RedisTokenReuseCounter`: TTL 을 첫 INCR 후 별도 EXPIRE 로 건다 — EXPIRE 실패 시 키가 영구가 되어 이후 모든 재사용이 잠금(카운터가 점수를 좌우하게 된 지금 의미가 커짐). `INCR`+`EXPIRE` 원자화 검토.
- 재사용 이벤트의 `reusedJti` 가 refresh 토큰 **원문**이다(계약 그대로) — identity-platform «refresh 토큰 로그 금지» 와 긴장. 해시화 여부 결정.
- IT 3셀 + `@Order(10)` 의 CI 결과 확인(이 티켓의 AC-1/AC-2 IT 판정).
