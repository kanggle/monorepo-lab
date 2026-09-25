# Task ID

TASK-BE-603

# Status

ready

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

- [ ] **AC-0** — 기존 행 처리 결정(이행 마이그레이션 vs 만료 대기 — refresh TTL 로 자연 소멸하는 기간) · 이벤트 의미 변경의 소비자 목록과 영향.
- [ ] **AC-1** — 구현 + 단위 테스트: 긴 이메일(> 36자) 계정의 발급 → 미러 행 저장 성공(UUID) → refresh 성공. 🔴 픽스처는 **실제 모양의 긴 이메일**을 쓴다(BE-601 IT 의 짧은 이메일은 이 결함 회피였다 — 그 픽스처도 되돌려라).
- [ ] **AC-2** — `accountId` 로 폐기가 SAS 경로 행을 맞힌다(BE-601 의 우회가 필요 없어졌는지 판정).
- [ ] **AC-3** — 계약·데이터 모델 문서(`data-model.md` · `auth-events.md` 의 `accountId` 의미) 갱신.

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
