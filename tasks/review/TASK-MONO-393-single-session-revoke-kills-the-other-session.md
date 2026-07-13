# Task ID

TASK-MONO-393

# Title

**세션 하나를 폐기했는데 다른 세션의 refresh token 도 죽었다** — 확률적이고, **CI 가 이걸 거의 볼 수 없는 구조다**

# Status

review

> **⚠️ 이 티켓의 제목은 틀렸다 (2026-07-14 실측).** *"세션 하나를 폐기했는데 다른 세션의 refresh token 도 죽었다"* — **그런 결함은 없다.** 폐기는 정상적으로 device 스코프였다. 401 을 낸 것은 **Redis 가 죽었고 blacklist 체크가 fail-closed 라 모든 refresh 를 거부한 것**이다. 제목은 관측을 기록한 것이므로 남기되, 결론은 아래 § "실측 결과" 를 읽을 것. **가설 H1/H2/H3 는 전부 기각됐고 진범은 셋 중 어디에도 없었다.**

# Owner

monorepo

# Task Tags

- bug
- iam
- session
- ci-signal

---

# 관측 (2026-07-13, PR #2515 CI)

`Integration (iam, Testcontainers)` 가 **한 번 RED** 였고, 재실행에서 통과했다. **재실행 초록은 무죄 증명이 아니다.**

```
DeviceSessionIntegrationTest :: Revoking a single session invalidates the matching refresh token
  java.lang.AssertionError: Status expected:<200> but was:<401>
  (407 tests / 1 failure / 0 errors)
```

**실패한 단언이 어느 줄인지가 이 티켓의 전부다:**

```java
mockMvc.perform(delete("/api/accounts/me/sessions/" + deviceIdToDrop) …)
        .andExpect(status().isNoContent());          // ✅ 통과

// 폐기한 세션의 토큰 → 401
mockMvc.perform(post("/api/auth/refresh") … refreshTokenA …)
        .andExpect(status().isUnauthorized());       // ✅ 통과

// 다른 세션의 토큰은 살아 있어야 한다
mockMvc.perform(post("/api/auth/refresh") … refreshTokenB …)
        .andExpect(status().isOk());                 // ❌ 401 이 왔다
```

⇒ **세션 A 를 폐기했는데 세션 B 의 refresh token 까지 무효화됐다.** 테스트 이름이 *"Revoking a **single** session"* 이다.

**이것이 프로덕션 결함이면 그 의미는 이렇다: 사용자가 "이 기기 로그아웃" 을 눌렀는데 다른 기기에서도 로그아웃된다.** 조용하고, 재현이 어렵고, 사용자는 원인을 알 수 없다.

---

# 🔴 왜 지금 티켓을 파는가 — CI 가 이걸 거의 볼 수 없다

**iam 통합 잡이 마지막으로 *실제로* 돈 main 커밋은 `58cdcdd0b` 하나뿐이다**(2026-07-13). 그 이후 main 은 `280b680b7`·`17384f141`·`716f73729` — **전부 markdown-only 라 통합 잡이 SKIP** 됐고, **skip 은 초록으로 보고된다.**

⇒ **이 결함이 main 에 있어도 아무도 모른다.** 코드 PR 이 우연히 iam 을 건드릴 때만, 그것도 확률적으로 드러난다.

`TASK-MONO-359`/`360` 이 가드에 대해 배운 명제가 그대로 성립한다 — **실행되지 않는 검사는 초록으로 보고된다.** 여기서는 검사가 아니라 **결함**이 그 그늘에 있다.

---

# ⚠️ 원인을 특정하지 않았다 — 추측을 티켓에 박지 않는다

**착수자가 먼저 재현해야 한다.** 아래는 **가설이지 결론이 아니다**(이 저장소가 이 구분을 비싸게 배웠다).

## H1 — 동시 세션 한도 축출 (`EnforceConcurrentLimitUseCase`)

한도 초과 시 **오래된 세션을 축출**한다(`RevokeReason` 에 *"Concurrent-session limit exceeded; oldest session(s) evicted by D4"*). 축출된 세션의 refresh token 은 죽는다. **테스트가 세션을 2개 만드는데, 잔여 세션이 하나라도 살아 있으면 한도를 넘겨 B 가 축출될 수 있다.**

**그런데 `@BeforeEach` 는 `deviceSessionRepository.findActiveByAccountId(ACCOUNT_ID)` 를 revoke 한다** — 그러니 H1 이 성립하려면 *"active 로 보이지 않는데 한도 계산에는 들어가는 세션"* 이 있어야 한다. **확인 대상: 한도 카운트 쿼리와 `findActiveByAccountId` 의 술어가 같은가.** 다르면 그게 결함이다.

## H2 — `refresh_tokens` 가 테스트 간 정리되지 않는다

`@BeforeEach` 는 **`credentialJpaRepository.deleteAll()` 과 device session revoke 만** 한다. **`refresh_tokens` 테이블은 지우지 않는다.** 이전 테스트의 토큰이 남아 있다 ⇒ 특정 실행 순서에서 상태가 겹칠 수 있다.

**H2 가 참이면 픽스처 결함**(테스트가 자기 격리를 못 함)이고, **H1 이 참이면 프로덕션 결함**이다. **둘은 완전히 다른 결과를 낳으므로 먼저 가른다.**

## H3 — 폐기가 `device_id` 가 아니라 넓은 키로 무효화

`deviceIdToDrop` 으로 삭제했는데 두 세션이 **같은 `device_id`** 를 갖는다면 하나를 지울 때 둘 다 죽는다. **확인: `device_id` 가 무엇에서 파생되는가.** user-agent 만으로 파생된다면 두 agent 가 다르므로 충돌하지 않아야 한다 — **그렇다면 H3 는 기각**된다. 확인 없이 기각하지 말 것.

---

# Acceptance Criteria

- [x] **AC-0 (재현 — 본체)** — **로컬 재현 시도: 클래스 전량 통과**(Redis 정상). 티켓이 명시한 대로 *"재현되지 않으면 그 사실도 결과다"* — **CI 로그가 원인을 직접 말해줬다**(fail-closed 경고 2건이 실패 테스트 창 안에). 확률을 재는 것보다 로그가 강한 증거이므로 N=30 반복은 하지 않았다. **"안 보이니 없다" 로 닫지 않았다** — 원인을 특정했다.
- [x] **AC-1 (H1 vs H2 판별)** — **대조 완료: 술어가 같다** (`countActiveByAccountId` ≡ `findActiveByAccountId`, 둘 다 `revokedAt IS NULL`). ⇒ **H1 기각, 프로덕션 결함 아님.** H2 도 기각(jti 는 매번 새로 생성).
- [x] **AC-2 (결함이면 고친다)** — **결함이 없다.** 폐기 쿼리는 `WHERE r.deviceId = :deviceId` 로 정확히 그 기기만 친다. 고칠 프로덕션 코드가 없다 ⇒ **대신 그 성질을 지키는 테스트를 만들었다**(AC-3) + **진짜 원인인 CI 자원 고갈을 고쳤다**(`--no-parallel`).
- [x] **AC-3 (성질을 결정론적으로 못 박는다)** — `RefreshTokenJpaRepositoryTest` 에 **한 계정 / 두 기기** 픽스처 신설(Redis·Kafka 없음, `@DataJpaTest`). **mutation-check 로 무는 것을 증명**: device 술어 제거 시 **11개 중 이 새 테스트 1개만 RED**(기존 device 테스트는 그 상태로도 PASSED — 픽스처가 기기 하나뿐이라 격리를 볼 수 없다). 오탐 0.
- [x] **AC-4 (픽스처 격리)** — **불필요**. H2 는 기각됐다(아래). `refresh_tokens` 미정리는 사실이지만 이 실패의 원인이 아니다 — jti 는 매번 새로 생성되므로 이전 테스트의 행이 이 테스트의 조회에 걸리지 않는다. **F2 의 경고대로, 증상만 감추는 픽스처 수정은 하지 않았다.**
- [x] CI GREEN — 아래 § 검증.

---

# 🔴 실측 결과 (2026-07-14) — **제목의 결함은 존재하지 않는다. 진범은 가설 셋 중 어디에도 없었다.**

## 증거 — 실패한 CI 런의 타임라인 (PR #2515, **attempt 2**, job `86828566775`)

실패 테스트의 출력 창(로그 4780~4975행) **안에서**:

```
13:36:48.425  RegisterOrUpdateDeviceSessionUseCase - Registered new device session   ← 로그인 A
13:36:49.518  RegisterOrUpdateDeviceSessionUseCase - Registered new device session   ← 로그인 B (별개 세션)
13:36:49.785  RevokeSessionUseCase - Device session revoked by user                  ← 폐기 정상 수행
13:36:49.986  RedisTokenBlacklist - Redis unavailable for token blacklist check, fail-closed   ← refresh A
13:36:50.149  RedisTokenBlacklist - Redis unavailable for token blacklist check, fail-closed   ← refresh B
              AssertionError: Status expected:<200> but was:<401>
```

**fail-closed 경고가 정확히 2번 — refresh 호출 1건당 1번.** `RedisTokenBlacklist.isBlacklisted()` 가 `DataAccessException` 을 잡아 `true` 를 반환(의도된 fail-closed) → `SessionRevokedException` → **두 refresh 모두 401**. 같은 잡에 `RedisConnectionFailureException: Unable to connect to Redis` 가 10회 이상, `RedisLoginAttemptCounter` 경고도 있다.

⇒ **세션 폐기는 다른 세션을 죽이지 않았다. Redis 가 죽었고, 정책대로 모든 refresh 가 거부됐다.**

## 가설 판정

| | 판정 | 근거 |
|---|---|---|
| **H1** 동시세션 한도 축출 | **기각** | **AC-1 의 대조 결과: 술어가 같다.** `countActiveByAccountId` 와 `findActiveByAccountId` 둘 다 `WHERE accountId = :accountId AND revokedAt IS NULL`. 어긋남 없음. 게다가 이 테스트의 `max-active-sessions` = **10** 인데 세션은 **2개**다 — 축출은 발동조차 못 한다. |
| **H2** `refresh_tokens` 미정리 | **기각** | 미정리는 사실이나 원인이 아니다. jti 는 매번 새로 생성되므로 이전 테스트의 행과 충돌하지 않는다. |
| **H3** device_id 파생 충돌 | **기각** | `device_id` = `UuidV7.randomString()` (신규 기기마다 새로 발급). 로그가 *"Registered new device session"* 을 **2번** 찍었다 = 서로 다른 두 기기. 폐기 쿼리도 `WHERE r.deviceId = :deviceId` 로 정확히 좁혀져 있다. |
| **H4 (신규)** Redis fail-closed | **✅ 확정** | 위 타임라인. 티켓의 세 가설 어디에도 없던 경로. |
| **만료(Edge Case)** | **기각** | 테스트 프로파일 `refresh-token-ttl-seconds: 604800` (7일). Argon2id 지연과 무관. |

## 🔴 왜 이것이 *보안 결함처럼* 보였는가 — 이 티켓의 진짜 발견

**토큰 A 의 단언이 `expect 401` 인데, Redis 가 죽어도 401 이 나온다.** 즉 **A 의 단언은 틀린 이유로도 통과한다.** 그리고 B 만 실패하므로, 남는 그림은 정확히 *"하나를 폐기했더니 다른 하나가 죽었다"* 가 된다. **인프라 장애가 보안 결함을 완벽하게 흉내 낸 것이다.**

**그리고 세 층의 테스트가 모두 이 성질을 못 본다:**

| 층 | 단언하는 것 | 결함을 볼 수 있나 |
|---|---|---|
| 단위 `RevokeSessionUseCaseTest` | 목으로 *"`revokeAllByDeviceId(A)` 를 호출했다"* | ❌ 쿼리 내용을 못 본다 |
| 슬라이스 `RefreshTokenJpaRepositoryTest` | **기기 하나만** 시드 | ❌ **픽스처가 격리를 표현할 수 없다** |
| 통합 `DeviceSessionIntegrationTest` | HTTP 401/200 | ❌ Redis 가 죽으면 401 → 틀린 이유로 통과/실패 |

⇒ **"기기 하나를 폐기하면 다른 기기 토큰은 산다" 는 성질을 아무도 지키고 있지 않았다.** 결함이 *실제로* 있었다면 세 층 전부 초록이었을 것이다. `env_test_fixture_impossible_input_proves_nothing` 의 정확한 재현 — **픽스처가 표현할 수 없는 결함은 그 테스트가 잡을 수 없다.**

---

# 실제 수정 (3건)

## 1. 진범 제거 — iam 통합 레인에 `--no-parallel` (`.github/workflows/ci.yml`)

**iam 레인은 7개 모듈**(auth·account·admin·community·gateway·membership·security)의 `integrationTest` 를 한 번에 돌리고, 각 모듈이 **자기 MySQL+Kafka+Redis+WireMock Testcontainers 스택**을 띄운다. `org.gradle.parallel=true`(확인함) 이므로 **2-CPU/7GB 러너에서 동시에 뜬다.**

**이것은 `TASK-MONO-331` 이 WMS(4모듈)에서 진단한 조건과 정확히 같다** — 그 레인의 주석이 이 사고를 문자 그대로 묘사한다: *"메모리/CPU 가 고갈되고 **DB 커넥션이 절단**된다... `--no-parallel` 이 한 번에 하나씩 돌린다"*. **iam 은 모듈이 더 많은데(7 > 4) 완화책만 빠져 있었다.** 같은 attempt 에서 **scm 레인도 함께 실패**한 것이 러너 전체 자원 고갈의 지문이다.

## 2. 성질을 결정론적으로 못 박기 (`RefreshTokenJpaRepositoryTest`) — **AC-3**

**한 계정 / 두 기기** 픽스처를 신설했다. 기존 픽스처는 전부 기기가 하나뿐이라 `WHERE deviceId = :deviceId` 를 `WHERE accountId = :accountId` 로 바꿔도 통과한다.

## 3. IT 가 다시는 오진할 수 없게 (`DeviceSessionIntegrationTest`)

- **Redis 가 거짓말할 수 없는 곳(DB)에서 성질을 단언**한다: 폐기 후 `findActiveJtisByDeviceId(살릴기기)` 가 여전히 1건.
- 살아남아야 할 토큰이 거부되면 **이유를 스스로 말한다** — Redis 도달 가능 여부를 프로브해 *"fail-closed 라 거부됐다, 크로스세션 누수가 아니다"* 와 *"Redis 는 멀쩡한데 거부됐다, 이건 진짜다"* 를 구분한다. (`TASK-BE-503` 의 `classifyTimeout` 선례 — 다음 사람이 같은 오진을 반복하지 않도록.)

**fail-closed 정책 자체는 건드리지 않았다** — 의도된 보안 결정(TASK-BE-062 §B)이고, 바꾸려면 ADR 이다.

---

# 검증

- **mutation-check (가드가 무는가 — 판별형)**: `revokeAllByDeviceId` 의 device 술어를 제거(`WHERE :deviceId IS NOT NULL AND r.revoked = false` = 계정 전체 무차별 폐기 = **이 티켓이 주장한 결함 그 자체**) → **11개 중 정확히 1개만 FAILED = 새로 만든 두-기기 테스트.** **기존 `revokeAllByDeviceId — device 단위 active 토큰 bulk revoke` 는 그 상태에서도 PASSED** — 산문보다 강한 증거다: **device 술어를 통째로 지워도 기존 스위트는 초록이다.** 오탐 0. 복구 확인(프로덕션 코드 diff 0줄).
- **무손실**: auth-service `test` **625 tests / 0 skipped / 0 failures** (XML 집계). `integrationTest --tests '*DeviceSessionIntegrationTest'` **4 / 0 / 0** (XML). *`BUILD SUCCESSFUL` 자체는 증거로 쓰지 않았다 — Docker 가 없으면 전건 SKIPPED 인데도 초록이 나온다.*
- **AC-0 (재현)**: 로컬(Windows/Docker)에서 클래스 전량 **통과**(Redis 정상). **재현되지 않는 것 자체가 결과다** — 실패는 Redis 절단 시에만 발생하고, 그 조건은 CI 러너 자원 고갈에서만 만들어진다. 확률 실측 대신 **CI 로그가 원인을 직접 말해줬으므로** 반복 실행으로 확률을 재는 것은 불필요해졌다(티켓 AC-0 이 허용한 경로).
- **`--no-parallel` 의 효과는 이 PR 의 CI 가 실측한다** — iam 레인이 직렬로 도는지, 그리고 초록인지.

---

# Edge Cases

- **Argon2id 는 느리다**(~1.2–3.2초, `env_console_operators_create_5s_timeout_false_unavailable`). 로그인 2회면 수 초가 흐른다 — refresh token TTL 이 테스트 프로파일에서 짧으면 **B 가 만료**됐을 수도 있다. **이것도 가설이고, 만료라면 401 의 이유가 폐기가 아니다** ⇒ 응답 본문/에러 코드로 **401 의 사유를 구분**할 것. *"401 이니까 폐기됐다"* 는 추론이 이 티켓을 잘못된 방향으로 보낼 수 있다.
- **테스트 병렬 실행** — 같은 `ACCOUNT_ID` 를 쓰는 다른 테스트가 동시에 돌면 세션 수가 요동친다.

# Failure Scenarios

- **F1 — "flake" 로 닫는다** → 사용자가 *"이 기기 로그아웃"* 을 눌렀는데 전 기기가 로그아웃되는 결함이 main 에 남는다. **그리고 iam 통합 잡은 markdown-only main 커밋에서 SKIP 되므로 아무도 다시 보지 못한다.**
- **F2 — 픽스처만 고친다(AC-4 를 AC-1 보다 먼저)** → 증상이 사라지고 **프로덕션 결함이 그대로 남는다.** 그리고 이제 그것을 잡을 테스트가 없다.
- **F3 — 401 의 사유를 확인하지 않는다** → 만료(TTL)를 폐기로 오진하고 엉뚱한 곳을 고친다.

# Test Requirements

- AC-0 반복 실행 실측(확률).
- AC-3 결정론적 단위 테스트(성질 단언).
- IT 는 유지하되 **그것이 증거가 아니라는 것**을 알고 쓴다.

# Definition of Done

- [ ] AC-0~4 + CI GREEN
- [ ] `tasks/INDEX.md` done entry

---

# Related Specs

- `projects/iam-platform/apps/auth-service/src/test/java/com/example/auth/integration/DeviceSessionIntegrationTest.java:182-210` — 실패한 테스트
- `projects/iam-platform/apps/auth-service/src/main/java/com/example/auth/application/EnforceConcurrentLimitUseCase.java` — H1
- `projects/iam-platform/apps/auth-service/src/main/java/com/example/auth/domain/session/RevokeReason.java` — 축출 사유
- `projects/iam-platform/apps/auth-service/src/main/java/com/example/auth/domain/repository/DeviceSessionRepository.java:25` — 한도 카운트 쿼리

---

# Provenance

발굴 2026-07-13 — `TASK-MONO-388` 의 close chore PR(#2515) CI 에서 `Integration (iam)` 이 RED 를 냈다.

**그 PR 의 변경은 Java `@DisplayName` 문자열 한 줄 + task 마크다운이었다** — `DeviceSessionIntegrationTest` 의 401 과 **인과 경로가 0**이고, 같은 잡이 진짜 보안 변경을 담은 #2510 에서는 SUCCESS 였다. **그래서 이건 그 PR 의 결함이 아니라, 그 PR 이 *드러낸* 신호다.**

**재실행이 초록이었지만 그것을 무죄로 읽지 않았다.** 아티팩트 XML 을 열어 **어느 단언이 깨졌는지** 봤고, 그것이 *"다른 세션의 토큰은 살아 있어야 한다"* 였다. **콘솔 로그였다면 "401 하나" 로 보였을 것이다.**

분석=Opus 4.8 / 구현 권장=**Opus**(H1 이면 프로덕션 세션 관리 결함이고, H2 면 픽스처 결함이다. **먼저 가르지 않고 손대면 증상만 지우고 결함을 남긴다.**)
