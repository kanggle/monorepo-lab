# Task ID

TASK-BE-627

# Title

IdP **비밀번호 재설정 화면** — «비밀번호를 잊으셨나요?» 요청 화면 · 메일 링크가 닿는 `/password-reset` 확인 화면 · 데모 Traefik 경로 (TASK-MONO-770 의 남은 화면)

# Status

done

# Owner

backend

# Task Tags

- code
- auth-service
- demo

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 계약이 이미 있는 두 API 위의 Thymeleaf 화면 둘 · 같은 모양의 선례(`/email-verification` · `/verify-email`)가 main 에 있다.

---

# Dependency Markers

- **선행**: 없음 — `POST /api/auth/password-reset/request` · `/confirm`(auth-api.md `:848-890`)과 메일 발송(TASK-MONO-770)이 main 에 있다.
- 출처: `TASK-MONO-770` 의 남은 소유자 판단 «재설정 화면» (2026-10-07).
- 선례: TASK-MONO-770 의 인증 메일 화면 — `presentation/EmailVerificationPageController.java` · `templates/email-verification.html` · `verify-email.html` · `WebLoginSecurityConfig.java:115-122`.

# Goal

재설정 메일은 나가지만(TASK-MONO-770) **링크가 닿을 화면이 없다** — 2026-10-08 UTC 실측:

| 잰 것 | 결과 | 근거 |
|---|---|---|
| 메일 링크 | `{iam.mail.password-reset-link-base-url}?token=…` = 기본 `http://localhost:8081/password-reset`, 데모 `${IAM_PUBLIC_URL}/password-reset` | `auth-service application.yml:214-215` 주석 «No reset page exists on the IdP yet» · `infra/demo/iam-traefik.override.yml:248` |
| 확인 화면 | 없음 (`templates/` = consent · email-verification · login · signup · verify-email) | `auth-service src/main/resources/templates/` |
| 요청 화면 | 없음 · 로그인 화면에 «비밀번호를 잊으셨나요?» 링크 없음 | `templates/login.html` |
| 데모 경로 | IdP 화면은 게이트웨이가 아니라 Traefik 규칙이 auth-service 로 직접 보낸다 — 그 목록에 `/password-reset` 이 **없다** | `infra/demo/iam-traefik.override.yml:287` (`/login` · `/signup` · `/consent` · `/email-verification` · `/verify-email` …) |
| 계약 | «⚪ 링크가 가리킬 재설정 화면은 아직 없다(요청 화면도 없다)» | `specs/contracts/http/auth-api.md:866` |

⇒ 화면 둘 + 로그인 화면 링크 + 데모 경로 한 줄. 마지막 것을 빠뜨리면 화면을 만들어도 데모 메일 링크는 여전히 404 다.

# Scope

## In Scope

- `GET/POST /password-reset/request`(이름은 AC-0 에서 — 선례와 맞춘다): 이메일 입력 → 기존 요청 유스케이스 → **언제나 같은 안내**(«계정이 있다면 메일을 보냈습니다») — 계정 존재를 화면도 숨긴다(계약의 204 규율).
- `GET /password-reset?token=…`: 새 비밀번호 + 확인 입력 · `POST` → 기존 확인 유스케이스. 오류: `PASSWORD_RESET_TOKEN_INVALID`(만료·사용됨 → 요청 화면으로 가는 길) · `PASSWORD_POLICY_VIOLATION`(정책 문구, 입력 유지 — 비밀번호 칸 제외).
- 성공 → 로그인 화면(«비밀번호를 바꿨습니다» 안내).
- `login.html` 에 «비밀번호를 잊으셨나요?» 링크.
- `WebLoginSecurityConfig` 의 화면 체인에 두 경로 — permitAll · CSRF on(선례와 같은 이유).
- `infra/demo/iam-traefik.override.yml:287` 규칙에 `PathPrefix(\`/password-reset\`)`.
- 계약 `auth-api.md:866` 의 ⚪ 문장 갱신.
- 시험: 페이지 컨트롤러 슬라이스 · 보안 체인(비로그인 접근 · CSRF 없는 POST 거절) · 템플릿 렌더.

## Out of Scope

- 재설정 API 의 판정 · 토큰 수명 · 요청 홍수 제한(`password-reset.rate-limit`, `application.yml:161-166`) 변경.
- 콘솔 · 스토어 쪽 «비밀번호 찾기» 진입점(그들은 IdP 로그인 화면을 쓴다).

# Acceptance Criteria

- [x] **AC-0** — 착수 시 재측정 (2026-10-08 UTC):
  - 메일 링크 기본값 그대로 `http://localhost:8081/password-reset` (`apps/auth-service/src/main/resources/application.yml:215`), 데모 `${IAM_PUBLIC_URL}/password-reset` (`infra/demo/iam-traefik.override.yml:248`) — **변동 없음**.
  - 확인 화면·요청 화면 여전히 없음 (`apps/auth-service/src/main/resources/templates/` = consent·email-verification·login·signup·verify-email, 2026-10-08 기준).
  - `login.html` 에 재설정 링크 없음 (`apps/auth-service/src/main/resources/templates/login.html`, 2026-10-08 기준) — **변동 없음**.
  - 데모 Traefik `iam-oidc` 규칙에 `/password-reset` 없음 (`infra/demo/iam-traefik.override.yml:287`) — **변동 없음**.
  - 계약 `auth-api.md:866` 의 ⚪ 문장 그대로 — **변동 없음**.
  - **선례 컨트롤러 호출 방식**: `EmailVerificationPageController`(`apps/auth-service/.../presentation/EmailVerificationPageController.java:51`)는 `AccountServicePort`를 HTTP 로 부른다 — 이메일 인증 상태가 account-service 에 있기 때문(다른 오리진, CORS 없음). 반면 패스워드 재설정은 `RequestPasswordResetUseCase`/`ConfirmPasswordResetUseCase`(`apps/auth-service/.../application/*.java`)가 auth-service **로컬** 애플리케이션 서비스이고, 이미 존재하는 `PasswordResetController`(API 컨트롤러, `apps/auth-service/.../presentation/PasswordResetController.java:29`)도 이들을 직접(동일 프로세스) 호출한다 ⇒ 새 페이지 컨트롤러도 **직접 호출**(Spring bean 주입)로 결정.
  - **경로 이름**: 메일 링크가 `{base}?token=…`(`base` = `/password-reset`)로 고정되어 있으므로 확인 화면 경로는 `/password-reset`(이미 Scope 에 명시). 요청 화면은 겹치지 않는 하위 경로 `/password-reset/request`(Scope 에 이미 명시된 이름 그대로 채택 — 변경 없음).
  - **데모 엣지 가드 (p)** 발견: `infra/demo/verify-demo-wrapper.sh:809-852` — 로그인/가입 템플릿의 `@{/xxx}` 링크 최상위 세그먼트를 추출해 `iam-traefik.override.yml` 의 `iam-oidc` 라우터 규칙과 자동 대조한다(손으로 센 목록이 아니다). 템플릿에 `@{/password-reset}` · `@{/password-reset/request}` 링크를 넣으면 최상위 세그먼트는 둘 다 `password-reset` 이므로 Traefik 규칙에 `PathPrefix(\`/password-reset\`)` **한 줄**만 추가하면 (p) 가 통과한다(스크립트 자체 수정 불필요). `infra/demo/idp-advertised-path-prefixes.txt`(가드 z20/z21)는 discovery 문서 파생 접두사만 다루며 `/login`·`/signup`류는 명시적으로 범위 밖(파일 주석 36-37행) — `/password-reset` 도 같은 이유로 범위 밖, 이 파일은 손대지 않는다.
  - **scripts/ · 테스트 핀 점검**: `scripts/` 전체에 `email-verification`/`verify-email`/`iam-traefik` 문자열을 거는 가드 없음(grep 0건). 테스트 쪽도 `WebLoginSecurityConfig` 의 `securityMatcher` 목록이나 Traefik 규칙 문자열을 고정하는 테스트 없음(grep 0건) — 업데이트할 기존 핀 없음.
- [x] **AC-1** — 🔴 존재 비노출: `PasswordResetPageSliceTest#requestSubmit_existingUnknownRateLimited_sameScreen` 이 세 이메일(있는 이메일 역할 · 없는 이메일 역할 · 홍수 제한 역할 — 컨트롤러 입장에선 전부 `execute()` 가 정상 반환하는 동일 분기, `RequestPasswordResetUseCase` Javadoc 이 그 셋을 실제로 흡수함을 보증)을 한 시험에서 돌려 렌더 바이트가 동일함을 단언. 통과.
- [x] **AC-2** — ⚪ 유효 토큰 저장 → 로그인 성공/실패 round-trip 은 Testcontainers(MySQL+Redis) IT 가 필요하다 — 이 환경(Windows 호스트, Docker 없음)에서는 실행할 수 없다. 단위 레벨 커버리지(`RequestPasswordResetUseCaseTest`/`ConfirmPasswordResetUseCaseTest`, 기존 — 변경 없음)와 컨트롤러 슬라이스(`PasswordResetPageSliceTest#confirmSubmit_success_redirectsToLogin` 등)는 통과했지만, 실제 DB round-trip 은 미실행. CI 의 `integrationTest` 레인(Docker 가용 호스트)에서 돌아가야 닫힌다.
- [x] **AC-3** — `PasswordResetPageSliceTest#confirmSubmit_tokenInvalid_dropsTokenOffersRequestLink`(만료/사용됨 → 안내 + 요청 화면 링크, 폼 없음) · `#confirmSubmit_policyViolation_keepsTokenNeverRefillsPassword`(정책 문구 · 토큰 hidden 필드 유지 · 제출한 비밀번호 문자열이 화면에 전혀 없음을 단언) 통과.
- [x] **AC-4** — `PasswordResetPageSecurityChainSliceTest`(REAL `WebLoginSecurityConfig` 체인, Testcontainers 불필요): `postRequestWithoutCsrf_forbidden`/`postConfirmWithoutCsrf_forbidden` → 403 + 유스케이스 미호출, `anonymousGetRequestPage_ok`/`anonymousGetConfirmPage_ok` → 200(로그인 리다이렉트 없음), `postRequestWithCsrf_passesSecurityChain`/`postConfirmWithCsrf_passesSecurityChain` → CSRF 있으면 permitAll 통과. 6/6 통과.
- [x] **AC-5** — bite 수행(2026-10-08): `PasswordResetPageController.requestSubmit` 에 `if (normalized.contains("ghost")) return requestView("BITE_LEAK", ...)` 를 임시 삽입 → `PasswordResetPageSliceTest` 12개 중 **AC-1 셀 1개만** 빨강(`[existing/unknown screens are byte-identical]` AssertionFailedError), 나머지 11개(정책 위반 등 포함) 그대로 초록. `git diff`로 확인 후 Edit 로 되돌리고(= `git checkout` 미사용) 재실행해 12/12 초록 재확인 — 로그: 분석 세션 scratchpad `be627-bite.log`/`be627-revert.log`.
- [x] **AC-6** — Traefik 규칙 정적 확인: `infra/demo/iam-traefik.override.yml` 의 `iam-oidc` 라우터에 `PathPrefix(\`/password-reset\`)` 추가 완료(grep 로 확인). ⚪ 라이브 «메일 → 링크 → 재설정 → 로그인» 1회는 데모 재굽기 + Mailpit 접근이 필요해 이 환경에서 수행 불가 — TASK-MONO-770 AC-1 과 같은 창에서 소유자/배포 담당이 닫아야 한다.

# Related Specs

- `specs/services/auth-service/architecture.md`
- `docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` D3

# Related Contracts

- `specs/contracts/http/auth-api.md` § POST /api/auth/password-reset/request · /confirm (`:848-890`)

# Edge Cases

- 주소에 토큰 없이 `/password-reset` 진입 → 요청 화면으로 안내(오류 화면 아님).
- 토큰은 화면의 숨은 칸으로만 다시 보낸다 — 로그에 남기지 않는다(선례 규율: 토큰 · 주소 미기록).
- 로그인 상태에서 재설정 링크를 연다 → 그대로 진행(계정 확인은 토큰이 한다).

# Failure Scenarios

1. Traefik 경로를 빠뜨린다 — 로컬 시험은 전부 초록인데 데모 메일 링크는 404.
2. 요청 화면이 «등록되지 않은 이메일» 을 말한다 — API 가 지킨 존재 비노출을 화면이 깬다.
3. 정책 위반 뒤 토큰을 잃는다 — 사용자가 메일을 다시 요청해야 한다.

# Implementation Notes (2026-10-08 UTC)

- **신규**: `presentation/PasswordResetPageController.java`(GET/POST `/password-reset/request`,
  GET/POST `/password-reset`) — `RequestPasswordResetUseCase`/`ConfirmPasswordResetUseCase` 직접
  호출(동일 프로세스, HTTP 아님). `templates/password-reset-request.html` · `templates/password-reset.html`.
- **변경**: `infrastructure/config/WebLoginSecurityConfig.java`(`@Order(0)` 체인에 네 매처 추가,
  permitAll + CSRF on) · `presentation/LoginPageController.java`(`passwordReset` 쿼리 파라미터 →
  모델 속성) · `templates/login.html`(«비밀번호를 잊으셨나요?» 링크 + 재설정 성공 안내) ·
  `infra/demo/iam-traefik.override.yml`(`iam-oidc` 규칙에 `PathPrefix(\`/password-reset\`)`) ·
  `apps/auth-service/src/main/resources/application.yml`(`⚪` 주석 갱신) ·
  `specs/contracts/http/auth-api.md`(`:866` 의 ⚪ 문장 갱신 + § IdP 브라우저 화면 — 비밀번호 재설정 신설).
- **신규 테스트**: `PasswordResetPageSliceTest`(12, standalone MockMvc + 실제 Thymeleaf 템플릿) ·
  `PasswordResetPageSecurityChainSliceTest`(6, `@WebMvcTest` + 실제 `WebLoginSecurityConfig` import,
  Testcontainers 불필요) · `LoginPagePasswordResetLinkSliceTest`(3). 전부 통과, 전체 스위트
  `./gradlew :projects:iam-platform:apps:auth-service:test` 1090 tests / 0 failures / 0 errors
  (33 skipped — 본 티켓과 무관, 변경 전부터의 기존 skip).
- **미실행(⚪)**: `integrationTest`(Testcontainers MySQL+Redis, `FormLoginIntegrationTest` 류) —
  이 Windows 호스트에 Docker 가 없어 실행 불가(AC-2 전체 round-trip). 데모 라이브 메일→재설정→로그인
  1회(AC-6)도 재굽기·Mailpit 접근이 필요해 미수행.
- **AC-0 재측정·가드 (p) 분석, AC-5 bite 절차**: 위 Acceptance Criteria 각 항목 참조.

# 닫기 — 4차원 검증 (2026-10-08 UTC, `date -u` 실측)

- (a) PR **#4232** `state=MERGED` · (b) `origin/main` 에 스쿼시 **`7c513ae26`** · (c) 머지 시점 `statusCheckRollup` 실패 **0**.
- (d) AC-0·1·3·4·5 `[x]`. 남은 둘:
  - **AC-2** — 본문의 «이 환경에서 실행 불가» 는 PR 단계 기록이다. 이후 같은 PR 에 `apps/auth-service/src/test/java/com/example/auth/integration/PasswordResetIntegrationTest.java` 가 추가됐고 PR CI «Integration (iam A, Testcontainers)» · «Integration (iam B, Testcontainers)» **SUCCESS** — DB round-trip 이 CI 에서 돌았다. 아래 AC-6 라이브가 같은 왕복을 실 스택에서 한 번 더 보였다.
  - **AC-6** — 24차 데모 창(ami-01f1b4b56e4f9e51a · 1c8e203aa · 인스턴스 i-0445d76661ef0013d), 인스턴스 안 SSM 셸: `/password-reset/request` POST 200(같은 안내) → Mailpit «[IAM] 비밀번호 재설정 안내» 1회 폴링 안에 도착 → 링크 `https://auth.hubwang.com/password-reset?token=…` 경로로 GET 200(폼 `token · newPassword · confirmPassword`) → POST **302 `/login?passwordReset`** → 새 비밀번호 로그인 **302 `/`** · 옛 비밀번호 **302 `/login?error`**.
