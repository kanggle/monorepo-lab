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
>
> ⏳ **DO NOT START — 보류(소유자 결정 2026-10-02 UTC «보류 + 작은 방어만»).** 날짜 조건이 아니다. 착수 전 **AC-00** 을 재서 참일 때만 시작하고, 거짓이면 이 파일 끝에 측정값과 날짜(UTC)를 덧붙이고 `ready/` 에 그대로 둔다.
>
> - [ ] **AC-00 (보류 게이트)** — 실제 소셜 제공자 키가 어느 배포 환경에 주입되었다: `OAUTH_{GOOGLE,KAKAO,MICROSOFT,NAVER}_CLIENT_ID` 중 하나가 데모·운영 설정(compose env · AMI 시드 · 비밀 저장소)에서 `application.yml` 의 `test-*-client-id` 기본값이 아닌 값을 받는다. 2026-10-02 측정: 어느 배포 설정에도 없음(기본값 = 가짜 → 소셜 로그인 성공 불가 · 소셜 신원 0개).
> - 보류 동안의 방어: `TASK-BE-620` — 풀 계정이 있는 이메일로 오는 사이트 소셜 가입을 거절(§ 2 공존 금지). 이 티켓이 소셜을 풀로 바꾸면 그 방어는 «풀-먼저 조회» 로 대체된다 — 그때 620 의 검사를 지울지 남길지 정한다.
> - 🔴🔴 **순서 규칙 (2026-10-06 UTC 추가 · `TASK-MONO-743` 종결의 대가)** — **실제 소셜 키를 배포 환경에 넣는 것은 이 티켓 머지 이후, 또는 이 티켓과 같은 PR 에서만.** 이유: 이 티켓 전에는 소셜 가입이 **사이트별 계정**을 만들고, 620 은 «같은 이메일의 풀 계정이 이미 있을 때» 만 막는다 ⇒ 풀 계정이 없는 사람이 팬·스토어에서 각각 소셜 가입하면 **따로 된 사이트 계정 둘**이 생긴다. 그 계정을 합칠 «묶기»(743)는 구현 없이 닫혔으므로, 그런 계정은 **생기지 않게 하는 것**이 유일한 방어다.
>   - 그래서 AC-00 이 «키가 들어왔다» 로 참이 되는 순간은 **이미 늦은 것**일 수 있다 — 키를 넣으려는 작업(소유자 · 다른 티켓)이 보이면 그 자리에서 이 티켓을 먼저 착수하도록 알린다. 키가 이 티켓보다 먼저 들어갔다면 착수 첫 단계로 AC-00′ 을 잰다:
>   - [ ] **AC-00′ (키가 먼저 들어간 경우만)** — 같은 `email` 이 `fan-platform` · `ecommerce` 두 테넌트에 모두 있는 **사이트 소셜 계정** 행 수(데모 시드 제외). 0 이 아니면 STOP — `TASK-MONO-743` 닫기 기록의 «되살리는 조건» ② 가 참이다(묶기 티켓을 새로 연다).

---

# Dependency Markers

- **선행**: `TASK-BE-614` · `TASK-BE-615` · `TASK-BE-616` (~~`TASK-MONO-743`~~ — 2026-10-02 보류 → **2026-10-06 구현 없이 종결**: 따로 된 계정의 모집단이 더는 생기지 않는다. 남은 구멍(소셜)은 위 «순서 규칙» 으로 막는다)

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

- 같은 사람이 비밀번호 풀 계정과 소셜 신원을 둘 다 가지려는 경우 — § 2 의 공존 금지대로 거절된다. 묶기(`TASK-MONO-743`)는 2026-10-06 구현 없이 종결됐다 — 이 경우가 실제로 문제가 되면 묶기 티켓을 새로 연다(743 닫기 기록 «되살리는 조건»).

# Failure Scenarios

1. 소셜 이메일 일치로 기존 풀 계정에 자동 연결 — 이메일을 검증하지 않는 제공자 경유 탈취.

---

## 인계 (TASK-BE-618, 2026-10-02 UTC)

- `TASK-BE-618` 의 한 사이트 계정 이동기(`POST /internal/consumer-pool/legacy-moves`, account-service →
  `POST /internal/auth/consumer-pool/moves`, auth-service)는 **`social_identities` 행이 하나라도 있는 계정을 통째로 건너뛴다**
  (auth 의 `409 POOL_MOVE_SOCIAL_LINKED` → 실행 보고서 `skipped.SOCIAL_LINKED`). 그 계정들은 지금 **사이트 계정 그대로**다.
  이유: 지금 소셜 로그인은 `(사이트 테넌트, provider, provider_user_id)` 로 신원을 찾는다 — 신원 행을 풀로 옮기면 그 조회가 비어 «새 소셜 가입» 으로 가고,
  그 가입은 같은 이메일의 (방금 옮긴) 풀 계정에 막혀 그 사람의 소셜 로그인이 끊긴다(618 착수 시 정정 ②).
- 이 티켓은 둘 중 하나를 **정하고 적어야** 한다:
  1. 소셜 조회를 풀-먼저로 바꾼 뒤 **같은 이동기**로 그 계정들을 옮긴다 — auth 이동 엔드포인트의 판정 4(`SOCIAL_LINKED`)를 «신원 행도 같이 옮긴다» 로 바꾸고,
     그 판정을 바꾼 시험이 이 티켓의 것이 된다. 또는
  2. 그 계정들은 사이트 계정으로 **남는다**고 결정한다(그 경우 § 2 의 «사이트 계정 이메일로 풀 가입 거절» 이 그 이메일들에 계속 걸린다).
- 어느 쪽이든 **AC-3**(«기존 테넌트별 소셜 신원은 그대로 동작한다») 은 여전히 지켜져야 한다 — 이동기가 건너뛴 계정의 소셜 로그인이 이 티켓 뒤에도 같은 계정으로 들어가는지가 그 대조군이다.
