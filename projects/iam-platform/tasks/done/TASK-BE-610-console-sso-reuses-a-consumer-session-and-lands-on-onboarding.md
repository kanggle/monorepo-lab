# Task ID

TASK-BE-610

# Status

done

# Title

같은 브라우저에서 스토어 · 팬에 로그인한 뒤 콘솔을 열면 **소비자 세션이 SSO 로 재사용되어** 운영자(`demo@demo.com`)가 `/onboarding` 에 떨어진다

# Owner

iam-platform

# Task Tags

- auth-service
- sso
- multi-tenant
- console

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus — `TASK-BE-605` 결정 ①(콘솔 면제)과 `ADR-MONO-044` D5(소비자의 셀프 온보딩)를 건드리는 설계 판단이다. 🔴 AC-0 은 소유자 결정.

---

# Goal

16차 AMI 창(2026-09-26 UTC) 관측 — 소유자 PC Chrome:
- 15:58 스토어(ecommerce 자격) → 팬(fan-platform 자격) 폼 로그인(`login_history` 두 줄).
- 그 뒤 `console.hubwang.com` → **`/onboarding`**. 새 로그인 이벤트 **없음**(= SSO).
- 해석(코드): `TASK-BE-605` 게이트는 **콘솔 client 를 면제**한다(ADR-MONO-044 D5 — 셀프 온보딩 운영자). 그래서 IAM 브라우저 세션의 principal(마지막 = fan-platform 계정 `…fa02`)이 콘솔 토큰이 되고,
  운영자 교환이 «운영자 아님» → 콘솔 `api/auth/callback` 이 `/onboarding` 으로 보낸다(`operator_exchange_not_provisioned_to_onboarding`).
- 같은 사람이 iam 자격(`…ad03`, SUPER_ADMIN)을 갖고 있어도 **SSO 는 그것을 고르지 않는다**. 론처는 «세 화면 모두 같은 계정» 이라고 안내한다 — 방문자 경로의 막다른 길.
- 회피: 시크릿 창 · 콘솔 로그아웃 후 재로그인(폼 로그인은 콘솔 client 의 테넌트 `iam` 자격으로 범위 조회 — BE-604).

# Scope

## In Scope

- **AC-0 (🔴 소유자 결정)**: ① 콘솔도 «세션 테넌트 ≠ `iam` 이고 그 사람이 `iam` 자격을 가지면 재인증» ② 콘솔 면제 유지 + 온보딩 화면에서 «운영자 계정으로 다시 로그인» 안내 ③ 현행 유지(론처 문구만 «콘솔은 먼저 열거나 새 창에서»). 🔴 ① 은 셀프 온보딩 소비자(iam 자격 없음)를 막으면 안 된다(D5).
- 결정대로 구현 + `multi-tenancy.md` § SSO 표 갱신.

## Out of Scope

- 소비자 client 간 SSO(`TASK-BE-605` 로 결정됨).

# Acceptance Criteria

- [x] **AC-0** — 위 결정. → § AC-0 소유자 결정 (2026-09-26 UTC) 참조.
- [x] **AC-1** (→ § 구현 결과 2026-09-29 UTC) — IT: 팬 세션 → 콘솔 authorize → 결정대로 · 대조군: iam 자격 없는 소비자의 콘솔 진입(셀프 온보딩)은 그대로.
- [ ] **AC-2** — 창 판정: 같은 브라우저 스토어 → 팬 → 콘솔 순서로 `demo@demo.com` 이 운영자 화면에 도달(또는 결정된 안내).

---

## AC-0 소유자 결정 (2026-09-26 UTC)

**① 조건부 재인증을 채택한다.** 콘솔 authorize 시점에 세션 테넌트 ≠ `iam` 이고 그 사람이 `iam` 자격을 갖고 있으면 재인증(폼 로그인 → iam 자격으로 스코프)한다 —
재인증 뒤 운영자 화면(iam 자격 → 콘솔)으로 간다. `iam` 자격이 없는 소비자는 지금처럼 셀프 온보딩(ADR-MONO-044 D5)을 그대로 유지한다 — ①은 그 경로를 막지 않는다.

**기각**: ② 콘솔 면제 유지 + 온보딩 화면 안내 — 사용자가 매번 수동으로 로그아웃/재로그인해야 하는 마찰이 남는다. ③ 론처 문구만 정정 — 근본 원인(SSO 가 소비자
principal 을 재사용)이 그대로 남아 결함이 안 고쳐진다.

이 결정대로 구현하고 `multi-tenancy.md` § SSO 표를 갱신한다(AC-1·AC-2 는 미해결로 남는다 — 구현·검증은 별도 진행).

# Related Specs

- `specs/features/multi-tenancy.md` § 로그인 가능한 계정과 client (BE-604 · BE-605 SSO 표)
- `ADR-MONO-044` D5 · `TASK-BE-605` § AC-0 소유자 결정

# Related Contracts

- 없음(토큰 claim 의미 불변이어야 한다 — 바뀌면 `auth-api.md` 먼저).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 소비자 전용 계정(iam 자격 없음)이 콘솔을 연다 | 셀프 온보딩 그대로(D5) |
| 여러 테넌트 자격 + iam 자격 | 콘솔에서는 iam 자격이 이긴다(결정 ① 일 때) |

# Failure Scenarios

1. **콘솔 면제를 그냥 없앤다** → D5 셀프 온보딩이 깨진다(BE-605 Failure Scenario 1).
2. **론처 문구만 고치고 닫는다** → 결정 ③ 이 아니라면 결함이 남는다.

---

## 구현 결과 (2026-09-29 UTC)

**스펙 먼저** — `multi-tenancy.md` § SSO 표의 콘솔 행을 «다른 테넌트 세션 → 그 이메일에 `iam` 자격이 있으면 재인증, 없으면 통과(D5)» 로,
그 아래에 판정 키 · 루프 없음 · D5 유지 · 조회 실패 처리를 적었다.

**코드 (auth-service)** — `AuthorizeSessionTenantGate` 의 콘솔 면제를 조건부로:
- 세션 테넌트 = client 테넌트면 지금처럼 통과(조회 없음).
- 콘솔 client 이고 세션 테넌트가 다르면 `credentials (tenant_id='iam', email)` 을 찾아 **있으면 재인증**, 없으면 통과. 이메일 = principal details
  `email`(폼 · 소셜 둘 다 넣는다), 없으면 principal 이름.
- 조회가 **실패**하면 재인증하지 않고 통과(경고 로그). 실패에 재인증으로 답하면 D5 소비자가 재로그인 → 같은 소비자 세션 → 같은 실패의 루프가 된다.
- `AuthorizationServerConfig` 가 `CredentialRepository` 를 게이트에 넘긴다.

**판정 키를 이메일로 정한 이유 (앞선 제안 정정)** — 이 티켓 착수 전 대화에서 «`identity_id`(ADR-036) 로 같은 사람을 판정하면 이메일에 기대지 않는다» 고
제안했다. 코드를 보고 이메일로 정했다:
- 폼 로그인 표가 자격을 고르는 키가 이미 `(tenant_id, email)` 이다 — 재인증 뒤 콘솔 로그인이 고를 자격과 게이트가 «있다» 고 본 자격이 **같은 행**이다.
  다른 키로 판정하면 둘이 어긋날 수 있다(게이트는 재인증을 요구했는데 로그인은 다른 자격을 고르는 경우).
- 재인증은 **아무것도 잇지 않는다** — 그 `iam` 자격의 비밀번호를 알아야 들어간다. BE-611 이 기각한 «이메일 자동 연결» 의 탈취 우려가 여기엔 없다.
- `identity_id` 는 `credentials` 에 매핑되지 않은 컬럼이고(`CredentialRepository.assignIdentityId` 주석), 발급 자체가 이메일 기준(`mintIdentity(tenant, email)`)이다.

**테스트**
| 셀 | 무엇을 |
|---|---|
| `AuthorizeSessionTenantGateTest.consoleClient_consumerSessionHoldingConsoleCredential_requiresReauthentication` | 팬 세션 + iam 자격 → 재인증 |
| `…consoleClient_noConsoleCredential_passes` (대조군) | iam 자격 없는 소비자 → 통과(D5) |
| `…consoleClient_consoleSession_passesWithoutLookup` | 재인증 뒤 iam 세션 → 통과 · 조회 없음(루프 없음) |
| `…consoleClient_lookupFailure_passes` | 조회 실패 → 통과 |
| `…consoleClient_emailFallsBackToPrincipalName` | details 에 email 없으면 principal 이름 |
| `…otherConsumerTenant_requiresReauthentication` (보강) | 소비자 client 경로는 자격을 조회하지 않는다 |
| `SsoTenantGateIntegrationTest.fanSession_openingConsole_withConsoleCredential_reauthenticatesIntoIt` (IT) | 팬 세션 → 콘솔 authorize → `/login` → 소비자 비밀번호는 `/login?error` → iam 비밀번호 → 재개된 authorize 가 코드 → 토큰 `tenant_id=iam` · `sub`=운영자 계정 |
| `…fanSession_openingConsole_isExempt` (기존, 대조군) | iam 자격 없는 팬 세션 → 콘솔 코드 그대로 |

- 로컬: auth-service `test` 전체 **rc=0**, 게이트 단위 15/15. 🔴 IT 는 `@Tag("integration")` → CI(iam B 샤드) 판정.
- bite ①: 콘솔 분기를 `return false`(BE-610 이전 면제)로 → 5 실패. **단언으로 실패한 것은 재인증 셀 둘**(iam 자격 · 이메일 폴백). 나머지 셋은
  strict stubs 의 «쓰이지 않은 스텁» 으로 실패했다 — 계측 산물이지 그 셀들의 단언이 문 것이 아니다.
  bite ②: 조회 실패 분기를 `return true` 로 → 1 실패(`lookupFailure`). 두 번 모두 scratchpad 백업으로 복원, md5 일치.

**남기는 것**
- 론처 문구 «세 화면 모두 같은 계정» 은 손대지 않았다(결정 ③ 기각 — 근본 원인을 고치는 쪽을 택했다). 이제 콘솔 로그인 화면이 한 번 뜨므로 문구가
  정확하지 않을 수 있다 — 프런트 표면이라 이 티켓 밖.
- UX 대가(BE-605 와 같은 모양): 콘솔에 iam 자격으로 들어간 뒤 스토어 · 팬으로 돌아가면 그쪽 로그인이 한 번 다시 뜬다.

### AC-2 창 런북 (재굽기 뒤 — 이 PR 의 squash 이후 커밋으로)
1. 새 시크릿 창이 아닌 **한 브라우저**에서 스토어 → 팬 순서로 `demo@demo.com` 폼 로그인(16차 창 재현 순서).
2. `console.hubwang.com` 을 연다 → 기대: **IAM 로그인 화면**(BE-610 이전 = 곧장 `/onboarding`). 새 `login_history` 줄이 **iam 자격으로** 하나 생긴다.
3. iam 비밀번호로 로그인 → 운영자 화면 도달(`/onboarding` 아님).
4. 대조: iam 자격이 없는 소비자 계정으로 같은 순서 → 콘솔이 로그인 화면 없이 `/onboarding`(D5 그대로).

---

## CORRECTION (2026-10-02 UTC) — AC-2 라이브 🟢

창: 18차 AMI `ami-03fa427e858219e47`(RepoCommit `1feb9fc6d` — AMI 태그·Lambda `AMI_REPO_COMMIT`·`check-ami-generation.sh --with-aws` rc=0 세 곳 일치), 인스턴스 `i-05395a5a7baa23bb8`, 2026-10-02 09:16–10:19 UTC. 측정 대상 변경은 전부 `1feb9fc6d` 의 조상(이미지 시각 ≥ 머지 시각). 브라우저 측정 증거 = 세션 스크래치 `live18/`(스크린샷·로그), 인스턴스 측정 = SSM 읽기 + 일회용 계정 쓰기.

- 한 브라우저 컨텍스트에서 스토어(비밀번호 로그인) → 팬(SSO, 화면 없음) → 콘솔 `/login` → `iam-login` → **운영자 브랜딩의 IAM 비밀번호 폼**(«운영자 계정으로 로그인합니다» — 의도된 재인증) → 입력 → `/dashboards/overview` **200** 운영자 화면(테넌트 선택기). 오류·온보딩 화면 없음. `m4-03` · `m4-05`.
⇒ `done/`.
