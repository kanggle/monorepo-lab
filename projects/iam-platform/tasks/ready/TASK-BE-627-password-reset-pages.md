# Task ID

TASK-BE-627

# Title

IdP **비밀번호 재설정 화면** — «비밀번호를 잊으셨나요?» 요청 화면 · 메일 링크가 닿는 `/password-reset` 확인 화면 · 데모 Traefik 경로 (TASK-MONO-770 의 남은 화면)

# Status

ready

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

- [ ] **AC-0** — 착수 시 재측정: 위 표 전부 file:line · 선례 컨트롤러가 유스케이스를 직접 부르는지 HTTP 로 부르는지 · 경로 이름을 정하고 메일 링크 기본값과 맞는지 확인.
- [ ] **AC-1** — 🔴 존재 비노출: 있는 이메일 · 없는 이메일 · 홍수 제한에 걸린 요청이 **같은 화면 · 같은 문구**를 받는다(세 칸을 한 시험에서 비교).
- [ ] **AC-2** — 유효 토큰으로 새 비밀번호 저장 → 로그인 화면 안내 · 옛 비밀번호 로그인 실패 · 새 비밀번호 로그인 성공(IT, 선례 IT 모양).
- [ ] **AC-3** — 만료 · 사용된 토큰 → «링크가 만료됐습니다» + 요청 화면 링크 · 정책 위반 → 정책 문구, 토큰 유지.
- [ ] **AC-4** — CSRF 토큰 없는 POST 두 개 모두 거절 · 비로그인 GET 은 200(로그인으로 튕기지 않는다).
- [ ] **AC-5** — bite: AC-1 의 «같은 문구» 를 깨면(없는 이메일에 다른 문구) AC-1 칸만 빨강.
- [ ] **AC-6** — 데모: Traefik 규칙에 경로가 들어갔다(정적 확인) · 라이브 «메일 → 링크 → 재설정 → 로그인» 1회는 재굽기 + Mailpit 창(TASK-MONO-770 AC-1 과 같은 창, ⚪ 가능).

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
