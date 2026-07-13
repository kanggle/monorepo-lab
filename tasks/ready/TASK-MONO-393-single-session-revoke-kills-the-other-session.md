# Task ID

TASK-MONO-393

# Title

**세션 하나를 폐기했는데 다른 세션의 refresh token 도 죽었다** — 확률적이고, **CI 가 이걸 거의 볼 수 없는 구조다**

# Status

ready

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

- [ ] **AC-0 (재현 — 본체)** — 이 테스트를 **반복 실행해 확률을 실측한다**(예: `--rerun-tasks` 로 N=30, 또는 `@RepeatedTest`). **몇 번에 한 번 실패하는가.** 재현되지 않으면 **그 사실도 결과다** — 그때는 CI 로그·아티팩트만으로 H1/H2/H3 를 가른다. **"안 보이니 없다" 로 닫지 말 것**(`env_ci_flake_is_a_hypothesis_not_a_verdict`).
- [ ] **AC-1 (H1 vs H2 판별)** — 한도 카운트 쿼리의 술어 ↔ `findActiveByAccountId` 의 술어를 **대조**한다. 다르면 프로덕션 결함이고 그것이 이 티켓의 본체가 된다.
- [ ] **AC-2 (결함이면 고친다)** — *"기기 하나 로그아웃"* 이 **정확히 그 기기만** 로그아웃시킨다.
- [ ] **AC-3 (성질을 단위 테스트로 못 박는다)** — **확률적 결함은 CI-GREEN 이 증거가 되지 못한다.** IT 의 초록을 근거로 삼지 말고, 성질(*"세션 N개 중 하나를 폐기하면 나머지 N-1 의 토큰은 살아 있다"*)을 **결정론적 단위 테스트**로 단언한다.
- [ ] **AC-4 (픽스처 격리)** — H2 가 참이면 `@BeforeEach` 가 `refresh_tokens` 도 정리한다. **다만 격리를 고쳐 증상만 감추지 말 것** — AC-1 을 먼저 결론지은 뒤에.
- [ ] CI GREEN.

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
