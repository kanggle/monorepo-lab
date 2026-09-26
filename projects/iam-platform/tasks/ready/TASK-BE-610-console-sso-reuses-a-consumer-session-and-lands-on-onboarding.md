# Task ID

TASK-BE-610

# Status

ready

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

- [ ] **AC-0** — 위 결정.
- [ ] **AC-1** — IT: 팬 세션 → 콘솔 authorize → 결정대로 · 대조군: iam 자격 없는 소비자의 콘솔 진입(셀프 온보딩)은 그대로.
- [ ] **AC-2** — 창 판정: 같은 브라우저 스토어 → 팬 → 콘솔 순서로 `demo@demo.com` 이 운영자 화면에 도달(또는 결정된 안내).

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
