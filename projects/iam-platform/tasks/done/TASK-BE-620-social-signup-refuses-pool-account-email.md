# Task ID

TASK-BE-620

# Status

done

# Title

전역 소비자 계정 — 소셜 가입이 **풀 계정이 있는 이메일**에 사이트 계정을 하나 더 만들지 않게 막는다 (`TASK-BE-617` 보류 동안의 방어 · § 2 공존 금지)

# Owner

iam-platform

# Task Tags

- account-service
- auth-service
- oauth-social

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 급(기존 검사 한 줄 재사용 + 오류 매핑) — 이번에는 Opus 5.5 가 직접 구현

---

# Dependency Markers

- **출처**: `TASK-BE-617` 착수 전 측정(2026-10-02 UTC) → 소유자 결정 «보류 + 작은 방어만». 617 은 ⏳ 보류(소셜 키가 실제로 주입될 때 착수).
- **재사용**: `ConsumerAccountPool#refuseIfEmailHasPoolAccount`(`TASK-BE-616` — 내부 계정 생성의 같은 방어).

# Goal

소셜 가입은 아직 풀이 아니라 **사이트 계정**을 만든다(풀 소셜 = `TASK-BE-617`, 보류). 그 사이트 안에서 이메일로만 계정을 찾으므로, 같은 이메일의 **풀 계정**(비밀번호 가입)이 있는 사람이 소셜 로그인하면 같은 이메일의 사이트 계정이 **하나 더** 생긴다 — `multi-tenancy.md` § 소비자 계정 풀 § 2 가 금지하는 공존. 그 경우를 거절하고 로그인 화면에서 «이메일·비밀번호로 로그인» 으로 안내한다.

착수 전 측정(2026-10-02 UTC): 데모·라이브의 소셜 키는 가짜 기본값(`test-google-client-id` 등, `application.yml`)이고 어떤 배포 설정도 실제 키를 넣지 않는다 — 지금 이 경로에 닿는 사용자는 없다. 소셜 신원이 있는 계정 0개. 이 티켓은 **키가 들어오는 날** 공존이 생기지 않게 하는 방어다.

# Scope

## In Scope

- account-service `SocialSignupUseCase`: 같은 사이트 계정 연결(기존) 다음에 `refuseIfEmailHasPoolAccount` — 소비자 사이트만, B2B 무변경
- auth-service: `409 ACCOUNT_ALREADY_EXISTS` 를 본문 `code` 로 구별 → `SocialSignupEmailRegisteredException` → 브라우저 `/login?error=email_registered`(새 문구) · 전역 핸들러 409
- 계약 `auth-to-account-social.md` § Errors · `oauth-social-login.md` 오류 매핑 표 · `multi-tenancy.md` § 2

## Out of Scope

- 소셜을 풀 계정 규칙으로(풀-먼저 조회 · 풀 신원 · 618 이 건너뛴 소셜 연결 계정 이동) — `TASK-BE-617`(보류)

# Acceptance Criteria

- [x] **AC-1** — 소비자 사이트 · 그 사이트에 같은 이메일 계정 없음 · 풀 계정 있음 → 거절, 계정·프로필·이벤트 생성 없음.
- [x] **AC-2** — 🔴 대조군: 같은 사이트 계정이 있으면 지금처럼 그 계정으로(풀 조회 안 함) · B2B 테넌트는 풀 이메일이어도 생성(D1).
- [x] **AC-3** — auth-service 가 두 409 를 `code` 로 구별한다: `ACCOUNT_ALREADY_EXISTS` → `email_registered`, `TENANT_SUSPENDED` · 읽을 수 없는 본문 → 이전 그대로(`temporarily_unavailable`). 재시도 없음.

# Related Specs

- `projects/iam-platform/specs/features/multi-tenancy.md` § 소비자 계정 풀 § 2
- `projects/iam-platform/specs/features/oauth-social-login.md` § 에러 → redirect 매핑

# Related Contracts

- `projects/iam-platform/specs/contracts/http/internal/auth-to-account-social.md`

# Edge Cases

- 같은 이메일에 사이트 계정과 풀 계정이 **이미** 공존(옛 결함 데이터) — 사이트 연결이 먼저라 지금처럼 그 사이트 계정으로 들어간다(이 티켓은 새 공존만 막는다).
- 이메일 열거: 거절 문구는 «이미 가입된 이메일» 부류 — 가입 화면의 기존 응답과 같다(§ 2 마지막 문장). 게다가 소셜 경로는 제공자가 그 이메일을 이미 확인했다.

# Failure Scenarios

1. `TENANT_SUSPENDED`(같은 409)를 «이미 가입된 이메일» 로 안내 — `TASK-BE-580` 이 가입 화면에서 고친 같은 혼동. 그래서 상태가 아니라 `code` 로 가른다.

---

# 닫기 기록 (2026-10-02 UTC)

- 머지: PR [#4100](https://github.com/kanggle/monorepo-lab/pull/4100) squash `f5b436669` · `state=MERGED` · `origin/main` 끝 일치 · 머지 전 체크 17건 pass(실패 0, iam 통합 A/B · iam E2E 포함).
- AC-1·AC-2: `SocialSignupUseCaseTest#execute_poolAccountWithEmail_refused` · `#execute_siteAccountExists_stillLinks_poolNotConsulted` · `#execute_b2bTenant_poolEmail_created` (로컬 10/0 실패).
- AC-3: `AccountServiceClientSocialSignupConflictTest`(409 `ACCOUNT_ALREADY_EXISTS` → 예외 · 요청 1회=재시도 없음 · `TENANT_SUSPENDED`·판독 불가 → 이전 그대로, 3/0) · `SocialLoginBrowserControllerTest#callback_socialSignupEmailRegistered_redirectsToEmailRegistered`(13/0).
- ⚪ 라이브 미측정 — 소셜 키가 어느 배포에도 없어 이 경로는 실제로 닿을 수 없다(그것이 `TASK-BE-617` 보류의 이유).
