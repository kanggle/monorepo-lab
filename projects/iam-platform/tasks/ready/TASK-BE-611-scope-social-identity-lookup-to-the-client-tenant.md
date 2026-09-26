# Task ID

TASK-BE-611

# Status

ready

# Title

소셜 신원 조회를 **시작 client 의 테넌트로 한정**한다 — 한 공급자 신원이 다른 테넌트 client 로 들어가 그 테넌트의 계정인 척하는 것을 끝낸다 (`TASK-BE-605` 결정 ② (iii) 의 구현)

# Owner

iam-platform

# Task Tags

- auth-service
- social-login
- multi-tenant

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus — 조회 한정 · 한정 미스 시 가입 · 기존 교차 신원의 처분이 스펙 규칙(«하나의 provider_user_id 는 하나의 계정에만 연결»)과 부딪친다.

---

# Goal

소유자 결정(2026-09-26 UTC, `TASK-BE-605` § AC-0 ②) = **(iii) 소셜 신원 조회를 client 테넌트로 한정 — 모집단 먼저.** 모집단 측정은 `TASK-MONO-672` 항목 18 이 들었고, 그 항목이 이 티켓의 기안 의무를 가졌다.

**측정 (16차 AMI 창, 2026-09-26 UTC — `TASK-MONO-672` § 16차 창 수확)**
- 모집단: `social_identities` = **0**(측정 창 시작 시) ⇒ 데모에 실사용자 소셜 신원 없음 — «데모에 없다» 이지 «운영에 없다» 가 아니다.
- **구조적 재현(판정의 실질 입력)**: Kakao 스텁으로 한 신원(`provider_user_id=60218261625`)을 **스토어 client → 팬 client** 순서로 소셜 로그인 — 둘 다 코드 발급.
  결과 계정 **1개**(`ecommerce`) · 신원 **1행**(`ecommerce`). ⇒ 팬 client 로의 로그인이 **스토어 테넌트 계정**으로 들어간다(전역 조회 `findByProviderAndProviderUserId` — `OAuthLoginUseCase.java:250` · `SocialLoginSteps.java:47`).
  세션 테넌트는 시작 client(`fan-platform`)로 찍힌다(`SocialLoginBrowserController.java:185`) ⇒ fan-platform 세션에 ecommerce 계정.
- 스펙과 저장소는 이미 테넌트별이다: `multi-tenancy.md` § 적용 범위(`social_identities`) · `V0007__add_tenant_id_to_auth_tables.sql:49` unique `(tenant_id, provider, provider_user_id)`.

# Scope

## In Scope

- 조회를 `(tenant_id = 시작 client 테넌트, provider, provider_user_id)` 로 한정.
- 한정 미스 = **그 테넌트에서 가입**(폼 로그인의 BE-604 결과와 같은 모양 — 테넌트마다 한 계정).
- `TASK-BE-602` 후속 ① 의 경쟁 조건(서로 다른 테넌트 동시 첫 로그인 → 전역 조회가 두 행 → 영구 `IncorrectResultSizeDataAccessException`)이 한정으로 **사라지는지** 판정(테넌트별 조회는 유니크 키와 같은 모양이다).
- **AC-0 (🔴 소유자 결정)**: ① `oauth-social-login.md` Business Rules «하나의 provider_user_id 는 하나의 계정에만 연결» 을 «테넌트마다 하나» 로 개정할지 ② 기존 교차 신원(다른 테넌트 client 로 이미 들어오던 사람)의 처분 — 그대로 두면 다음 로그인에 그 테넌트 새 계정이 생긴다(데모 모집단 0, 운영 모집단 미상).

## Out of Scope

- 발급 토큰의 `tenant_id` 의미(ADR-006 옵션 1 유지).

# Acceptance Criteria

- [ ] **AC-0** — 위 ①② 결정 + 스펙 개정(스펙 먼저).
- [ ] **AC-1** — 구현 + 단위/IT: 같은 신원으로 스토어 → 팬 = **계정 둘**(테넌트마다) · 같은 client 재로그인 = 같은 계정 · BE-507 이전 계정(신원 = ecommerce, 계정 = fan-platform)의 동작을 AC-0 ② 대로.
- [ ] **AC-2** — 🔴 **창 판정**: 위 구조적 재현을 다시 돌려 계정 2 · 신원 2(테넌트별)로 바뀌는지. 절차 = `TASK-MONO-672` § 16차 창 수확의 스텁 방식(`TASK-BE-602` § CORRECTION 2026-09-26 의 재생성 함정 셋 포함).

# Related Specs

- `specs/features/multi-tenancy.md` § 소셜 로그인 · `specs/features/oauth-social-login.md` § tenant 귀속 규칙 · Business Rules
- `TASK-BE-605` § AC-0 ② · `TASK-BE-602` § 후속 ① · `TASK-MONO-672` 항목 18

# Related Contracts

- 없음(이벤트 `tenantId` 는 BE-602 대로 계정의 실제 테넌트).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 같은 이메일의 폼 자격이 그 테넌트에 이미 있다 | AC-0 에서 정한다(연결 vs 별도 계정) — 🔴 자동 연결은 계정 탈취 경로가 될 수 있다 |
| 콘솔 client(`iam`) 로 소셜 | 콘솔은 소셜 대상이 아니다(현행 확인) |

# Failure Scenarios

1. **스펙 개정 없이 코드만 한정** → 스펙 규칙과 코드가 다시 갈린다.
2. **데모 모집단 0 을 «영향 없음» 으로 읽는다** → 운영 모집단은 미상이다.
