# Task ID

TASK-BE-606

# Status

ready

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

- [ ] **AC-0** — 위 ①②③ 판독 + 소유자 결정(특히 ② 의 오탐 허용 범위).
- [ ] **AC-1** — IT: 로그인 → refresh(A→B) → **A 재제출** → 400 + 계정의 다른 SAS 세션 refresh 거부 + `auth.token.reuse.detected` 1건.
- [ ] **AC-2** — IT: 결정한 대로 동시 refresh 경쟁이 재사용으로 오판되지 않는다(또는 결정한 정책대로 동작).
- [ ] **AC-3** — `identity-platform.md:88` MUST 충족 근거를 티켓에 file:line 으로.

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
