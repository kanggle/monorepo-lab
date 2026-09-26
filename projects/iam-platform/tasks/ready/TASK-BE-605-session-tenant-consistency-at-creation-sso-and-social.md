# Task ID

TASK-BE-605

# Status

ready

# Title

세션이 **만들어질 때** 테넌트가 갈린다 — SSO 로 소비자 client 교차 세션이 생기고, 소셜 로그인은 client 테넌트를 찍는다

# Owner

iam-platform

# Task Tags

- auth-service
- multi-tenant
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 — authorize 시점 게이트는 SSO 동작 전체에 걸리는 설계 결정이다.

---

# Goal

`TASK-BE-604`(소유자 결정 D, 2026-09-26 UTC)는 **폼 로그인**의 교차 테넌트 폴백을 콘솔 client(`iam`)로 한정했다. 그러나 같은 티켓이 두 경로를 남겼다(`TASK-BE-604` § ⑩ 1·3·6):

1. 🔴 **SSO 우회** — 이미 IAM 브라우저 세션이 있으면 비밀번호 없이 다른 client 의 authorize 를 통과한다. 로컬 측정(BE-604 ⑧): 콘솔 로그인 뒤 같은 세션으로
   ecommerce client authorize → `tenant_id=fan-platform`, 역할 없음 토큰 발급. ⇒ 결정 D 가 막으려던 «소비자 client 교차 세션» 이 여전히 만들어진다(역할이 없어 쓸모없는 세션).
2. **소셜 스탬프** — `SocialLoginBrowserController:185` 는 세션 테넌트를 **client 테넌트**로 찍는다(폼은 계정 테넌트). 같은 사람이 폼으로는 쓸모없는 세션,
   소셜로는 CUSTOMER 로 입장한다 — 두 경로가 갈린다. `TASK-BE-602` 가 소셜 경로에 계정의 실제 테넌트(account-service `status-with-tenant`)를 들여왔으므로 출처는 있다.
3. web-store 역할 가드(`account_type_mismatch`)의 e2e 커버리지 — BE-604 가 `account-type-guard.spec.ts` 를 «IAM 이 로그인을 거부» 로 바꾸며 잃었다. 1 을 고치면 SSO 로 다시 구성하는 길도 사라진다.

# Scope

## 포함

- **AC-0 (🔴 소유자 결정)**: ① authorize 시점 게이트 — 세션의 principal 테넌트 ≠ client 테넌트이면 (a) 거부 · (b) 재인증 요구 · (c) 현행 유지(쓸모없는 세션 허용) — 단 콘솔(`iam`) client 는 BE-604 D 대로 허용.
  ② 소셜 스탬프를 계정 테넌트로 맞출지. ③ 역할 가드 e2e 를 어떤 경로로 복원할지(또는 단위 테스트로 대체).
- 결정대로 구현 + `multi-tenancy.md` 의 «어느 계정이 어느 client 로 로그인할 수 있나» 절(BE-604 신설)에 SSO · 소셜 행 추가.

## 제외

- 폼 로그인 폴백(BE-604 에서 결정·구현됨).

# Acceptance Criteria

- [ ] **AC-0** — 위 ①②③ 소유자 결정.
- [ ] **AC-1** — IT: 콘솔 로그인 세션으로 소비자 client authorize → 결정대로(거부/재인증) · 콘솔 client 는 계속 통과(대조군).
- [ ] **AC-2** — 소셜: 결정대로 스탬프 · 폼과 같은 사람에게 같은 결과(IT 또는 단위).
- [ ] **AC-3** — 역할 가드 커버리지 복원 또는 대체의 근거.
- [ ] **AC-4** — 🔴 **머지 후 첫 nightly `Frontend E2E full-stack (web-store …)` 확인** — BE-604(#4033, `06031b053`)가 바꾼 `account-type-guard.spec.ts` 가 nightly 에서 초록인가. BE-604 는 `done/` 으로 닫혔으므로 **이 확인은 이 티켓이 들고 있다.**

# Related Specs

- `specs/features/multi-tenancy.md` § BE-604 신설 절 · `specs/features/oauth-social-login.md`
- `TASK-BE-604`(발견 · § ⑧ SSO 측정 · § ⑩) · `TASK-BE-602`(소셜의 계정 테넌트 출처) · ADR-MONO-044 D5(콘솔 교차 로그인의 근거)

# Related Contracts

- 없음(토큰 claim 의미가 바뀌면 `auth-events.md` · `auth-api.md` 먼저).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 셀프 온보딩 운영자(소비자 자격 · 콘솔) | 콘솔은 계속 통과(BE-604 D) |
| 테넌트별 자격을 여럿 가진 계정(`demo@demo.com`) | 각 client 에서 그 테넌트 자격으로 — 게이트가 이 계정을 막으면 안 된다 |

# Failure Scenarios

1. **authorize 게이트가 콘솔도 막는다** → 셀프 온보딩 운영자 전원 콘솔 불가(ADR-MONO-044 D5 위반).
2. **소셜만 고치고 폼과 비교하지 않는다** → 두 경로가 다른 방향으로 다시 갈린다.
3. **nightly 를 안 본다** → BE-604 의 e2e 변경이 검증 없이 남는다(AC-4).
