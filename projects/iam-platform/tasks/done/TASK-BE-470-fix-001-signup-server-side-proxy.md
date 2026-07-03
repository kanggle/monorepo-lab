# Task ID

TASK-BE-470-fix-001

# Title

IAM 회원가입 폼 서버사이드 프록시 전환 (client fetch → auth-service `POST /signup` → account-service)

# Status

done

# Owner

backend

# Task Tags

- code
- ui
- fix

---

# Goal

TASK-BE-470의 회원가입 화면을 **라이브 브라우저 검증 중 발견된 통합 갭**에 맞춰 수정한다.

**발견된 갭**: BE-470은 signup.html의 클라이언트 JS가 `fetch('/api/accounts/signup')`로 직접 제출하는 설계였고, "signup 페이지와 `/api/accounts`가 same-origin"을 전제했다. 그러나 실제 IAM 토폴로지에서 이 전제가 거짓이다 — SAS 브라우저 페이지(`/login`·`/signup`)는 **auth-service 오리진**이 서빙하고, `/api/accounts/signup`은 **게이트웨이/account-service 오리진**이다. IAM 게이트웨이는 `/login`·`/signup`을 프록시하지 않고([gateway application.yml routes](../../apps/gateway-service/src/main/resources/application.yml)), account-service엔 CORS 설정이 없다. 따라서 상대경로 fetch는 auth-service로 가서 403, 절대경로 cross-origin fetch는 CORS 차단 → **폼이 화면엔 뜨지만 제출이 실패**한다. (라이브 검증: `:8081/signup`=200이나 `:8081/api/accounts/signup`=403.)

**수정**: 폼 제출을 auth-service의 서버사이드 프록시로 전환한다. `POST /signup`을 auth-service가 받아 기존 `AccountServicePort`(이미 존재하는 auth→account 클라이언트)로 account-service `POST /api/accounts/signup`을 호출한다. 전 과정이 auth-service 오리진에서 same-origin(로그인 폼과 동일 방식)이 되어 CORS·게이트웨이 라우트 추가가 불필요하다.

완료 후 참: 브라우저에서 `/signup` 폼을 채워 제출하면 실제 계정이 생성되고 `/login?registered`로 이동해 로그인할 수 있다.

---

# Scope

## In scope

- `SignupPageController` — `POST /signup` 추가. `AccountServicePort.signup` 호출, 성공 시 `redirect:/login?registered`, 오류 시 signup 뷰 재렌더 + 오류 메시지(email/displayName 보존).
- `AccountServicePort` + `AccountServiceClient` — 공개 `POST /api/accounts/signup`(no bearer) 호출하는 `signup(email,password,displayName)` 추가. 409→`SignupEmailConflictException`, 400/422→`SignupInvalidException`, 그 외 4xx/5xx/IO→`AccountServiceUnavailableException`. **retry/circuit-breaker 미적용**(비멱등 write — blind retry 시 이중생성 위험).
- `signup.html` — 클라이언트 fetch 제거, 표준 폼 POST(`method=post action=/signup`) + CSRF 토큰(`_csrf`) + 서버렌더 오류배너 + 입력 보존. 패스워드 일치/길이 선검증은 progressive JS로 유지(서버가 SoT).
- `WebLoginSecurityConfig` — `@Order(0)` 체인 `securityMatcher`에 `POST /signup` 추가(기존 GET만). CSRF 유지.
- 신규 예외 `SignupEmailConflictException`·`SignupInvalidException`.

## Out of scope

- 자동 로그인(가입 후 세션 자동 생성) — 여전히 `/login`에서 1회 로그인. (별도 백로그.)
- CORS / 게이트웨이 라우트 변경 — 서버사이드 프록시라 불필요.
- tenant 컨텍스트(`FAN_PLATFORM` 홈, ADR-MONO-032) 변경 없음.

---

# Acceptance Criteria

- [ ] **AC-1**: `POST /signup`이 유효 입력에 대해 `AccountServicePort.signup`을 호출하고 `redirect:/login?registered`를 반환한다.
- [ ] **AC-2**: 오류별 signup 뷰 재렌더 + 오류 메시지 — 409(이미 가입), 400/422(입력/패스워드 규칙), 5xx·기타(일시 불가). email/displayName 보존.
- [ ] **AC-3**: 서버사이드 선검증 — email/password 누락, 8자 미만, 패스워드 불일치 시 account-service 호출 없이 재렌더.
- [ ] **AC-4**: signup.html이 표준 폼 POST + `_csrf` 토큰을 포함한다(클라이언트 fetch 제거). `WebLoginSecurityConfig`가 GET·POST `/signup`을 공개로 매칭.
- [ ] **AC-5**: `SignupPageControllerTest`(standalone MockMvc, mock 포트) GREEN — GET 뷰, POST 성공 redirect, 불일치 재렌더(포트 미호출), 409 재렌더. `:auth-service:test` 컴파일 통과.
- [ ] **AC-6 (라이브 검증)**: federation-hardening-e2e 스택에서 auth-service 재배포 후 브라우저(또는 세션 쿠키 curl)로 `/signup` 폼 제출 → 계정 생성 201 → `/login?registered` → 로그인 성공.

---

# Related Specs

- [signup.md](../../specs/features/signup.md), ADR-006(SAS 브라우저 세션), ADR-MONO-032(통합 신원)
- 선행: TASK-BE-470(done) — 회원가입 화면 최초 도입

# Related Contracts

- HTTP: [account-api.md](../../specs/contracts/http/account-api.md) `POST /api/accounts/signup` (201/409/422/429/503)

# Edge Cases

- 패스워드 8자+ 이나 3종 미달 → account-service가 422/400 → `SignupInvalidException` → 규칙 안내 재렌더.
- displayName 공백 → 요청 바디에서 생략.
- account-service 다운 → `AccountServiceUnavailableException` → "일시 불가" 재렌더(무음 실패 없음).
- CSRF 토큰 누락/불일치 → `@Order(0)` 체인 CSRF가 403(정상 방어). 폼은 `_csrf` 주입.

# Failure Scenarios

- 프록시 호출이 비멱등 write이므로 retry 미적용 — 타임아웃 시 사용자가 재렌더 화면에서 수동 재시도(중복 시 409로 안전 처리).
- account-service가 201 반환했으나 auth-service→login redirect 실패(드묾) → 계정은 생성됨(멱등), 사용자가 로그인으로 수동 이동 가능.
