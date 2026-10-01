# Task ID

TASK-BE-617

# Status

ready

# Title

전역 소비자 계정 6단계 — 소셜 로그인을 **풀 계정** 규칙으로 (`ADR-MONO-078` A · D4)

# Owner

iam-platform

# Task Tags

- auth-service
- account-service
- oauth-social

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (소셜 자동 가입 경로 — 잘못되면 남의 풀 계정에 붙는다)

---

# Dependency Markers

- **선행**: `TASK-BE-614` · `TASK-BE-615` · `TASK-BE-616` · `TASK-MONO-743`(소셜 전용 계정의 본인 확인 수단)

# Goal

소셜 로그인으로 오는 소비자도 풀 계정으로 로그인하고 사이트를 오갈 때 같은 규칙(첫 방문 동의 · 재입력 없음)을 타게 한다. 기존 테넌트별 소셜 신원은 **본인 확인으로만** 묶는다(`multi-tenancy.md:373` 소유자 결정 유지).

# Scope

## In Scope

- `OAuthLoginUseCase` · `SocialSignupUseCase` — 소비자 client 의 새 소셜 가입은 풀로
- `social_identities` 의 풀 단위 저장(`TASK-MONO-742` 저장 모양)

## Out of Scope

- 소셜 제공자 추가

# Acceptance Criteria

- [ ] **AC-1** — 새 소셜 사용자: 팬에서 가입 → 스토어 이동 시 동의 화면만, 재로그인 없음.
- [ ] **AC-2** — 🔴 **대조군**: 소셜 제공자가 준 이메일이 기존 **비밀번호 풀 계정**의 이메일과 같아도 자동으로 붙지 않는다(D2) — 지금 `SocialSignupUseCase` 의 «그 테넌트에서 이메일로 찾으면 연결» 동작(`SocialSignupUseCase.java:30-66`)이 풀에서 그대로 쓰이면 안 된다.
- [ ] **AC-3** — 기존 테넌트별 소셜 신원은 그대로 동작한다(묶기 전).

# Related Specs

- `projects/iam-platform/specs/features/oauth-social-login.md`
- `projects/iam-platform/specs/features/multi-tenancy.md` § 소셜

# Related Contracts

- `platform/contracts/jwt-standard-claims.md`(`TASK-MONO-742` 갱신본)

# Edge Cases

- 같은 사람이 비밀번호 풀 계정과 소셜 신원을 둘 다 가지려는 경우 — 묶기(`TASK-MONO-743`) 경로로.

# Failure Scenarios

1. 소셜 이메일 일치로 기존 풀 계정에 자동 연결 — 이메일을 검증하지 않는 제공자 경유 탈취.
