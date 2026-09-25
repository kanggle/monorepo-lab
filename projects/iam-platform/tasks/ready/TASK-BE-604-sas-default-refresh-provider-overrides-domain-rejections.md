# Task ID

TASK-BE-604

# Status

ready

# Title

🔴 SAS 기본 refresh provider 가 우리 provider 뒤에 살아 있다 — 도메인 거부(미러 행 폐기·만료·테넌트 불일치)가 전부 무시된다

# Owner

iam-platform

# Task Tags

- auth-service
- oauth2
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 — 인증 필터 체인 구성 변경 · 테넌트 비교 재정의 · 기존 세션 영향.

---

# Goal

`TASK-BE-603` 의 IT 가 CI 에서 드러냈다(2026-09-25 UTC, PR #4027 run 36134528069): 미러 행을 폐기해도 **SAS refresh 가 200 으로 성공한다**.

- `SasRefreshTokenAuthenticationProvider` 는 거부한다(`:189` 폐기 행 → `INVALID_GRANT`).
- 그러나 SAS 1.4.1 `OAuth2TokenEndpointConfigurer` 가 기본 `OAuth2RefreshTokenAuthenticationProvider` 를 **그대로 등록**하고, 우리 것은 그 **앞에 끼워질 뿐**이다
  (`AuthorizationServerConfig` 에 제거 코드 없음 — «먼저 추가 ⇒ 우선» 이 «대체» 로 읽혀 왔다).
- `ProviderManager` 는 `AuthenticationException` 을 받으면 **다음 provider 로 넘어간다** ⇒ SAS 기본 provider 는 SAS 인가만 보고 새 토큰을 발급하고,
  그 저장이 `DomainSync` 로 **새 미러 행까지 만든다** ⇒ 세션이 완전히 되살아난다.

**영향 (BE-603 이전부터)** — 미러 행만 폐기하는 경로는 SAS 세션을 끝내지 못한다:

| 경로 | 근거 | 효과 |
|---|---|---|
| 비밀번호 재설정 | `ConfirmPasswordResetUseCase.java:95` | 🔴 재설정 후에도 기존 브라우저 세션이 refresh 된다 |
| SAS 재사용 탐지의 «다른 세션 전부 폐기» | `SasRefreshTokenAuthenticationProvider.java` `handleReuseDetected` | 🔴 탈취 의심 후에도 다른 세션이 살아 있다 |
| 미러 행 만료 · `TOKEN_TENANT_MISMATCH` | 같은 provider `:193-216` | 거부가 무시된다 |
| force-logout · 계정 잠금 | BE-601 `SasAuthorizationRevocationAdapter` | ✅ SAS 인가 자체를 무효화하므로 **실제로 막히는 유일한 경로** |

🔴 **그냥 기본 provider 를 제거하면 안 될 수 있다** (코드 판독 · ⚪ 미측정): 계정 테넌트 ≠ client 테넌트인 세션(예: BE-507 이전 `fan-platform` 계정이
다른 테넌트 client 로 로그인 — `CredentialAuthenticationProvider.java:190-213` 교차 조회 · `:289-293` details 테넌트 = 계정 테넌트)은 미러 행 테넌트가
계정 테넌트라 매 refresh 가 `TOKEN_TENANT_MISMATCH` 로 떨어지고, **지금은 이 fall-through 덕분에 갱신되고 있을 수 있다**. 제거하면 그 사용자가 끊긴다.

# Scope

## 포함

- **AC-0 (🔴 소유자 결정)**: ⓐ 기본 provider 제거 + 테넌트 비교 재정의를 한 변경으로 · ⓑ 먼저 운영의 `auth.token.tenant.mismatch` 발생량을 측정하고 결정 ·
  ⓒ 기타. 결정 입력: fall-through 로만 갱신되는 세션의 실제 규모(측정 가능한가부터).
- 결정대로 구현 — 도메인 거부가 최종 거부가 되게.
- 비밀번호 재설정·재사용 탐지가 SAS 세션을 실제로 끝내는지 결과로 판정.

## 제외

- principal name 을 UUID 로 바꾸는 것(BE-603 제외 항목과 같음).

# Acceptance Criteria

- [ ] **AC-0** — 위 ⓐ/ⓑ/ⓒ 소유자 결정(+ ⓑ 면 측정 결과).
  🔵 **소유자 결정 (2026-09-25 UTC) = ⓑ `tenant.mismatch` 발생량을 먼저 측정한다.** 측정은 실행 중인 스택이 필요하므로
  `TASK-MONO-672` **항목 16** 으로 넘겼다: ① 살아 있는 SAS 세션의 (client 테넌트, 미러 행 테넌트) 교차표 — 불일치 세션 수 = 제거 시 끊길 세션 ·
  ② 창 동안의 `cross-tenant attempt detected` 로그 줄과 outbox `auth.token.tenant.mismatch` · ③ 코드 판독상 예측된 모집단 둘
  ((a) 테넌트 `'*'` SUPER_ADMIN 의 스토어 로그인 · (b) BE-507 이전 `fan-platform` 계정의 교차 테넌트 로그인)을 직접 재현하고 콘솔을 대조군으로.
  유효성 술어(세션 0 · refresh 0 · 조인 전부 NULL 이면 «판정 불가»)는 그 항목에 있다.
  🔴 **이 AC 는 측정 결과가 적히고 그것으로 ⓐ 또는 대안을 고르기 전에는 닫히지 않는다** — 측정 결과가 곧 결정은 아니다(0 이어도 데모 모집단 ≠ 운영).
- [ ] **AC-1** — IT: 미러 행 폐기 → 다음 refresh 400 `invalid_grant` (BE-603 IT `@Order(8)` 가 일부러 **단언하지 않은** 칸). 🔴 기존 fall-through 200 을 고정하는 단언을 만들지 마라.
- [ ] **AC-2** — IT: 비밀번호 재설정 후 기존 SAS 세션 refresh 거부 · 재사용 탐지 후 다른 세션 refresh 거부.
- [ ] **AC-3** — 계정 테넌트 ≠ client 테넌트 세션의 refresh 가 결정한 대로 동작(허용이면 성공, 차단이면 명시적 거부 + 계약 문서).

# Related Specs

- `specs/services/auth-service/data-model.md` (BE-603 이 «폐기된 미러 행이 SAS refresh 를 막지 않는다» 주석을 넣었다)
- `TASK-BE-603` (발견 · `## CORRECTION`) · `TASK-BE-601` (폐기 어댑터) · `TASK-BE-507` (테넌트별 계정)

# Related Contracts

- `auth.token.tenant.mismatch` — 발행 규칙이 바뀌면 계약부터.

# Edge Cases

| 상황 | 기대 |
|---|---|
| 배포 시점에 fall-through 로만 살아 있던 세션 | AC-0 결정대로 — 끊기면 재로그인 안내가 사용자 경로에서 보이는가 |
| SAS 인가는 살아 있고 미러 행이 없음(BE-603 이전 INSERT 실패분) | 미러 행 부재 = 거부인가 허용인가를 먼저 정한다 |

# Failure Scenarios

1. **기본 provider 만 제거한다** → 테넌트가 어긋난 세션이 전부 끊긴다(⚪ 규모 미측정).
2. **fall-through 200 을 단언하는 테스트를 넣는다** → 결함이 핀으로 얼어붙는다.
3. **force-logout 만 보고 «막힌다» 고 판정한다** → 그 경로만 BE-601 어댑터로 막혀 있다. 재설정·재사용 탐지는 별개다.
