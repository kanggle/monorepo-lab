# Task ID

TASK-MONO-246

# Title

IAM auth-service HTTP 에러코드 `CREDENTIALS_INVALID` → `INVALID_CREDENTIALS` 통일 — TASK-MONO-244 WI-2 deferred follow-up 종결 (platform-common canonical 로 수렴, event failureReason enum 불변)

# Status

review

# Owner

monorepo (root tasks/ — shared `platform/error-handling.md` + iam-platform auth-service)

# Task Tags

- contract-change

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **origin**: TASK-MONO-244 WI-2 deferred follow-up. MONO-244 가 `CREDENTIALS_INVALID`↔`INVALID_CREDENTIALS` 를 registered alias 로 보류했고, 통일을 per-service follow-up 으로 nominate.
- **why now (조사 결과)**: HTTP code `CREDENTIALS_INVALID` 를 분기하는 **라이브 클라이언트 0** (프런트 `.ts/.tsx` 참조 0; iam-platform 프런트앱 없음; 타 서비스·게이트웨이 분기 0 — in-repo 소비처는 IAM 자체 테스트 + 계약문서뿐). 게다가 IAM 내부가 이미 **불일치**: `admin-service` 는 `INVALID_CREDENTIALS`, `auth-service` 는 `CREDENTIALS_INVALID` → 통일이 기존 불일치를 해소.
- **scope placement**: shared `platform/error-handling.md` 를 건드리므로 **root tasks/** (CLAUDE.md shared-path 규칙; MONO-106/244/245 동일 lineage). iam project 코드/스펙도 같은 atomic PR 에서 수정(MONO-106 선례 = shared+project 1 PR).
- **model**: 분석=Opus 4.8 / 구현=Opus 4.8 (contract-change + 멀티파일 + event/HTTP 네임스페이스 분리 판단).

---

# Goal

IAM `auth-service` 가 HTTP 에러 응답 `code` 로 내보내는 `CREDENTIALS_INVALID` 를 platform-common canonical `INVALID_CREDENTIALS` 로 통일한다. 통일 후 IAM 전체(auth + admin)가 단일 HTTP code `INVALID_CREDENTIALS` 로 일관되고, `platform/error-handling.md` 의 `CREDENTIALS_INVALID` HTTP-code alias 행이 제거되어 MONO-244 WI-2 drift 가 실제 해소된다.

**핵심 경계**: 통일 대상은 **HTTP 응답 code** 뿐. login-failed **이벤트의 `failureReason` enum**(`CREDENTIALS_INVALID | ACCOUNT_LOCKED | ...`)은 security-service 가 Kafka 로 소비하는 **별도 계약** 이므로 **불변**. 결과적으로 같은 로그인-실패 플로우에서 HTTP code=`INVALID_CREDENTIALS`, event failureReason=`CREDENTIALS_INVALID` 로 **의도적으로 분리**되며, 이는 두 네임스페이스가 다른 계약이기 때문에 정당하다.

---

# Scope

## In Scope

**WI-1 — IAM auth-service 코드 (HTTP emit 2곳).**
- `apps/auth-service/.../presentation/exception/AuthExceptionHandler.java`: `handleCredentialsInvalid`(401, L22) + `handleCurrentPasswordMismatch`(400, L50) 의 `ErrorResponse.of("CREDENTIALS_INVALID", ...)` → `"INVALID_CREDENTIALS"`.
- `apps/auth-service/.../application/ChangePasswordUseCase.java` L33 javadoc 코드 언급 갱신.
- **exception 클래스명 `CredentialsInvalidException` 은 유지** (내부 Java 식별자, wire contract 아님 — rename 은 불필요 churn).

**WI-2 — IAM 스펙 (spec-first).**
- `specs/features/authentication.md` L51: "일관되게 `CREDENTIALS_INVALID`" → `INVALID_CREDENTIALS` (이 mandate 가 이제 admin-service 와 충돌하므로 수정 정당).
- `specs/use-cases/signup-and-login.md` EF-1(L59)/EF-2(L60).
- `specs/contracts/http/auth-api.md` L362(login 401) + L726(password-change 400).
- `specs/contracts/http/account-api.md` L234(재인증 401).

**WI-3 — IAM 테스트 (HTTP `$.code` 단언만).**
- `LoginControllerTest`(L64), `PasswordControllerTest`(L62 displayname + L77 `$.code`), `AuthIntegrationTest`(L165, L178 `$.code`), `LoginUseCaseTest`(L252 displayname).

**WI-4 — shared registry.**
- `platform/error-handling.md` L462 (IAM `CREDENTIALS_INVALID` HTTP-code alias 행) 제거. canonical `INVALID_CREDENTIALS`(L77)는 유지 (이제 ecommerce auth + IAM admin + IAM auth 가 모두 emit).

**WI-5 — shared saas 도메인 규칙 (구현 중 발견).**
- `rules/domains/saas.md` L54 ("이 도메인 특유의 코드" 목록) + L137 (DON'T: "일반화된 `CREDENTIALS_INVALID`만 반환") → `INVALID_CREDENTIALS`. 이 공유 규칙이 saas 도메인 표준을 `CREDENTIALS_INVALID` 로 명시하고 있었고 `auth-api.md` 가 이를 참조함 → 통일과 함께 정렬해야 새 rule↔code 충돌이 생기지 않음. (admin-service 가 이미 `INVALID_CREDENTIALS` 라 규칙이 실제와도 어긋나 있었음.)

## Out of Scope (event 네임스페이스 — 불변)

- `LoginUseCase.java` L204(javadoc)/L212(`publishLoginFailed("CREDENTIALS_INVALID", ...)`) — event failureReason.
- `specs/contracts/events/auth-events.md` L72 failureReason enum.
- security-service consumer/test (`SecurityServiceIntegrationTest`, `DetectionE2EIntegrationTest`, `CrossTenantVelocityIntegrationTest`, `AuthEventMapperTest`).
- auth-service event 테스트 failureReason 단언 (`AuthEventPublisherTest`, `OutboxRelayIntegrationTest`).
- admin-service (`INVALID_CREDENTIALS` already canonical — 변경 0).
- `tasks/done/...` 이력 문서 — immutable.

---

# Acceptance Criteria

- [x] **AC-1 (HTTP emit 통일)**: `AuthExceptionHandler` 의 `handleCredentialsInvalid`(401) + `handleCurrentPasswordMismatch`(400) → `INVALID_CREDENTIALS`. (재인증 401 은 account 흐름이 같은 핸들러 경유.)
- [x] **AC-2 (event 불변)**: 최종 `grep "CREDENTIALS_INVALID"` — event 경로 전부 보존: `LoginUseCase`(204 javadoc/212 publish), `auth-events.md:72`, security-service 3 IT + AuthEventMapperTest, AuthEventPublisherTest(227/440/451), OutboxRelayIntegrationTest(156/178 failureReason). `AuthExceptionHandler:23` 의 잔존은 분리를 설명하는 **의도적 주석**.
- [x] **AC-3 (스펙 정합)**: authentication.md mandate + signup-and-login EF-1/2 + auth-api(401/400) + account-api(401) 전부 `INVALID_CREDENTIALS`.
- [x] **AC-4 (테스트 정합)**: HTTP `$.code` 단언 4파일(Login/Password ControllerTest, AuthIntegrationTest×2, LoginUseCaseTest displayname) 갱신, event failureReason 단언 불변. (CI 최종 권위 검증.)
- [x] **AC-5 (registry)**: `grep "CREDENTIALS_INVALID" platform/error-handling.md` = **0** (행 제거). canonical `INVALID_CREDENTIALS`(L77) 유지.
- [x] **AC-6 (atomic)**: shared(`platform/error-handling.md` + `rules/domains/saas.md`) + iam code/spec/test 가 1 PR.
- [x] **AC-7**: 신규 broken-ref 0. MONO-244 WI-2 drift 해소.
- [x] **AC-8 (WI-5, 구현 중 발견)**: `rules/domains/saas.md` L54/L137 이 `INVALID_CREDENTIALS` 로 정렬 — 공유 saas 도메인 규칙↔통일 코드 충돌 예방.

---

# Related Specs

- `projects/iam-platform/PROJECT.md` — domain=saas, traits=[transactional, regulated, audit-heavy, integration-heavy, multi-tenant]. saas "구체 원인 노출 금지" 규칙은 *실패 원인 비노출* 이지 특정 문자열 강제가 아니므로 통일과 무관(두 코드 모두 동등하게 opaque).
- `projects/iam-platform/specs/features/authentication.md` (WI-2), `specs/use-cases/signup-and-login.md` (WI-2).
- `platform/error-handling.md` (WI-4, shared registry SoT).
- `tasks/done/TASK-MONO-244-...` — origin disposition.

# Related Contracts

- `projects/iam-platform/specs/contracts/http/auth-api.md` (login 401 + password 400) — **edit (breaking in principle; in-repo 소비처 0)**.
- `projects/iam-platform/specs/contracts/http/account-api.md` (재인증 401) — edit.
- `projects/iam-platform/specs/contracts/events/auth-events.md` — **NOT edited** (event failureReason enum 보존).

---

# Edge Cases

- **HTTP code 와 event failureReason 가 갈라져 혼란** — 의도적. 다른 네임스페이스(HTTP 응답 vs Kafka event enum). task Goal + 코드 근처 주석으로 명시하여 향후 "버그로 오인한 재통일" 방지.
- **`CredentialsInvalidException` 클래스명이 코드와 불일치해 보임** — 무관. 클래스명은 내부 식별자, emit 문자열만 contract. rename 안 함(churn 회피).
- **saas 도메인 "원인 비노출"** — 통일이 위반 아님. `INVALID_CREDENTIALS` 도 이메일존재/패스워드불일치를 구분 노출하지 않음(동일 opaque).
- **standalone-extraction / 외부 소비처** — 현 monorepo 소비처 0. standalone 배포본도 monorepo 파생이라 동일. 신규 외부 소비처는 published canonical 을 따르면 됨.

# Failure Scenarios

- **event failureReason 까지 바꿔버림** → security-service fraud-detection Kafka 계약 breaking + 범위 폭발. WI-1/3 는 HTTP `$.code` 만; event 단언/publish 는 불변.
- **registry 행 제거를 코드 통일과 분리된 커밋/PR 로** → transiently-stale(코드는 INVALID_CREDENTIALS emit 하는데 registry 는 CREDENTIALS_INVALID 행 잔존, 또는 그 반대). AC-6 atomic 필수.
- **`auth-api.md`/`account-api.md` 만 고치고 코드/테스트 누락** → 계약↔구현 drift + CI RED. 전 계층 동시 변경.
- **admin-service 를 건드림** → 이미 canonical, 불필요 변경.

---

# Verification

**적용 (12 파일):**
- 코드: `AuthExceptionHandler`(2 emit) + `ChangePasswordUseCase`(javadoc).
- 스펙: authentication.md, signup-and-login.md, auth-api.md(×2), account-api.md.
- 테스트: LoginControllerTest, PasswordControllerTest(displayname+code), AuthIntegrationTest(×2), LoginUseCaseTest(displayname).
- 공유: `platform/error-handling.md`(행 제거), `rules/domains/saas.md`(×2, WI-5).

**최종 grep 검증:**
- HTTP 경로 `CREDENTIALS_INVALID` = 0 (error-handling.md 0, 스펙/코드/테스트 emit 0; `AuthExceptionHandler:23` 주석은 의도적).
- event 경로 `CREDENTIALS_INVALID` 전부 보존(LoginUseCase publish, auth-events enum, security-service, event 테스트).
- `INVALID_CREDENTIALS` = HTTP canonical(ecommerce + IAM admin + IAM auth + saas.md).

**CI**: `.java` 포함 → path-filter 가 iam Build & Test + Integration(iam, Testcontainers) 실행 = authoritative. 머지 전 GREEN 확인.
- 분석=Opus 4.8 / 구현=Opus 4.8.
