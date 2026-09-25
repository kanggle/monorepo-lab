# Task ID

TASK-BE-600

# Status

ready

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

- [ ] **AC-0** — ⚪ 셋(갱신 경로 · DELETED 자격 행 · 다른 테넌트 유형)을 코드로 재고 범위를 확정한다. 🔴 갱신 경로가 뚫려 있으면 «로그인만 막고 끝» 은 잠금의 절반이다.
- [ ] **AC-1** — 🔴 **응답 모양 결정**: 폼 로그인은 지금 모든 실패를 `/login?error` 로 돌려 계정 존재를 숨긴다. 잠긴 계정에 «잠겼다» 를 보여 주면
      비밀번호가 **맞았다**는 사실이 새어 나간다(잠긴 계정의 비밀번호 확인 오라클). ⇒ 선택지: ⓐ 같은 `/login?error` (정보 없음) ·
      ⓑ 비밀번호가 맞은 경우에만 «잠김» 안내(오라클 수용) · ⓒ 비밀번호 확인 **전에** 상태로 거부(항상 «잠김» — 존재 여부는 새지만 비밀번호 정답은 안 샘).
      🔴 **소유자 결정**. JSON 경로는 423 `ACCOUNT_LOCKED` 를 냈다(API 였고 폼이 아니다 — 그대로 가져오지 마라).
- [ ] **AC-2** — 구현 + 단위 테스트: ACTIVE 통과 · LOCKED · DORMANT · DELETED 각각 거부 · account-service 조회 실패 시 동작(🔴 fail-closed 인가 fail-open 인가를 결정하고 테스트로 박는다 — 소셜 경로의 현재 동작과 맞춘다).
- [ ] **AC-3** — 🔴 **결과로 판정**(창 또는 iam e2e compose): 일회용 계정을 잠그고(`POST /internal/accounts/{id}/lock` 또는 `auth.token.reuse.detected` 주입 — 2026-09-25 창의 방법) 폼 로그인 → 거부. 대조군 = 잠그기 전 같은 계정으로 로그인 성공. 🔴 데모 공유 계정은 잠그지 마라.
- [ ] **AC-4** — `auth-api.md`(폼 로그인 절)와 `architecture.md` 에 상태 거부 규칙을 적는다(스펙이 먼저).

# Related Specs

- `specs/contracts/http/auth-api.md` · `specs/services/auth-service/architecture.md`
- `tasks/done/TASK-BE-309-auth-service-form-login-html-surface.md` (`:94` — minimal credential check 설계)
- `tasks/done/TASK-BE-398-legacy-custom-jwt-flow-sunset-removal.md`
- `TASK-BE-599` (이 결함이 드러난 곳 · 데모 완화)
- `TASK-MONO-672` 항목 15 ② (잠금 경로 PASS)

# Related Contracts

- account-service 상태 조회(`AccountServicePort.getAccountStatus`) — 바꾸지 않는다.

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
