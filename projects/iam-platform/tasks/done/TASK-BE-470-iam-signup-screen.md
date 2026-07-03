# Task ID

TASK-BE-470

# Title

IAM 회원가입 화면 (SAS `/signup` Thymeleaf 페이지 → `POST /api/accounts/signup`)

# Status

done

# Owner

backend

# Task Tags

- code
- ui

---

# Goal

IAM 의 Spring Authorization Server(SAS) 브라우저 로그인 표면에 **self-service 회원가입 화면**을 추가한다.

현재 `POST /api/accounts/signup` API(account-service)와 도메인 로직은 완전히 구현·테스트되어 있으나, 사용자가 브라우저에서 계정을 만들 수 있는 **UI 가 어디에도 없다**. auth-service 의 유일한 UI 는 `templates/login.html`(로그인 폼 + 소셜 버튼)이며 회원가입 폼도, 가입 링크도 없다. web-store 의 `/signup` 링크는 OIDC authorize 를 경유해 이 로그인 화면으로 되돌아오는 **데드엔드**다.

완료 후 참이 되어야 하는 것: 브라우저에서 `GET /signup` 으로 회원가입 폼이 렌더되고, 폼 제출이 `POST /api/accounts/signup` 을 호출해 계정을 생성하며, 성공 시 로그인 화면(`/login?registered`)으로 이동해 곧바로 로그인할 수 있다. 로그인 화면에는 회원가입 화면으로 가는 링크가 생겨 데드엔드가 해소된다.

---

# Scope

## In scope

- `templates/signup.html` — 이메일/패스워드/패스워드 확인/표시이름(선택) 폼. `login.html` 과 동일한 인라인 CSS 카드 스타일.
- 클라이언트 JS: 동일 오리진 상대경로 `fetch('/api/accounts/signup', {method:'POST', json})` 로 제출. 201 → `/login?registered` 리다이렉트. 오류 코드별 한국어 메시지 인라인 표시.
- `SignupPageController` — `GET /signup` → `signup` 뷰 렌더.
- `WebLoginSecurityConfig` — `@Order(0)` 폼-로그인 체인의 `securityMatcher` 에 `GET /signup` 추가(해당 체인은 `anyRequest().permitAll()` 이므로 공개).
- `LoginPageController` + `login.html` — `?registered` 파라미터 성공 배너 + "회원가입" 링크(`/signup`) 추가.

## Out of scope

- **폼 제출 경로 = 클라이언트 fetch → 게이트웨이**로 확정(설계 결정). auth-service 는 signup API 를 서버사이드로 프록시하지 **않는다** — account-service 가 signup 의 유일 소유자로 유지되고, auth→account 신규 HTTP 결합을 만들지 않는다. (자동 로그인은 트레이드오프로 포기: 가입 후 사용자는 로그인 화면에서 1회 로그인.)
- 이메일 인증 필수화(현행 스코프: 선택). `signup.md` 백로그.
- web-store 프런트 변경. IAM 로그인 화면의 회원가입 링크가 UX 루프를 해소하므로 web-store `/signup` redirect 는 그대로 둔다.
- tenant 컨텍스트: `SignupUseCase` 가 `FAN_PLATFORM` 홈 테넌트로 계정 생성(ADR-MONO-032 통합 신원 = 홈 테넌트 1개 + 구독/롤 파생). 본 태스크는 이 동작을 변경하지 않는다.

---

# Acceptance Criteria

- [ ] **AC-1**: `GET /signup` 이 200 으로 회원가입 폼(email/password/confirm/displayName)을 렌더한다. 인증 없이 접근 가능(public).
- [ ] **AC-2**: 폼 제출이 `POST /api/accounts/signup` 을 JSON 으로 호출한다(displayName 은 비어있지 않을 때만 포함). 201 응답 시 `/login?registered` 로 이동한다.
- [ ] **AC-3**: 오류 코드별 한국어 메시지를 인라인으로 표시한다 — 409 `ACCOUNT_ALREADY_EXISTS`(이미 가입된 이메일), 400/422 `VALIDATION_ERROR`(입력값 확인/패스워드 규칙), 429 `RATE_LIMITED`(잠시 후 재시도), 503 `AUTH_SERVICE_UNAVAILABLE`(일시 불가), 그 외 = 일반 폴백(무음 실패 없음).
- [ ] **AC-4**: 클라이언트 선검증 — 패스워드 ≠ 패스워드 확인, 또는 8자 미만이면 서버 호출 없이 인라인 오류.
- [ ] **AC-5**: `GET /login` 에 "회원가입"(`/signup`) 링크가 있고, `?registered` 접근 시 "가입이 완료되었습니다. 로그인해 주세요." 배너가 표시된다.
- [ ] **AC-6**: `SignupPageController` standalone MockMvc 테스트 — `GET /signup` → 200 + 뷰네임 `signup`. `./gradlew :projects:iam-platform:apps:auth-service:test` GREEN.

---

# Related Specs

- [signup.md](../../specs/features/signup.md) — 가입 end-to-end 흐름·비즈니스 규칙(패스워드 8자+ 3종 조합, 이메일 lowercase 정규화)
- ADR-006 (auth-service SAS 브라우저 세션 플로우), ADR-MONO-032 (통합 신원 모델)

# Related Contracts

- HTTP: [account-api.md](../../specs/contracts/http/account-api.md) `POST /api/accounts/signup` — 요청(email/password/displayName/locale/timezone), 201 `{accountId,email,status,createdAt}`, 오류 409/422/429/503

# Edge Cases

- 패스워드는 8자+ 이나 3종 조합 미달 → account-service `@Size` 는 통과하나 auth-service `PasswordPolicy` 가 거부 → signup API 가 검증 오류 반환 → AC-3 폴백으로 인라인 표시. 폼 헬퍼 텍스트로 규칙을 사전 고지.
- displayName 미입력 → 요청 바디에서 필드 생략(null 허용).
- 이미 로그인된 세션에서 `/signup` 접근 → 공개 페이지이므로 렌더는 되나 가입은 계정 신규 생성. (재로그인 강제 안내는 out of scope.)
- 동일 오리진 가정: signup 페이지와 `/api/accounts/signup` 이 같은 게이트웨이 호스트로 서빙 → CORS 불필요. account-service signup 은 `permitAll` + CSRF disabled + STATELESS 이므로 fetch 에 CSRF 토큰 불필요.

# Failure Scenarios

- account-service 다운/게이트웨이 라우팅 실패 → fetch 네트워크 오류 → AC-3 일반 폴백 메시지("잠시 후 다시 시도해 주세요"). 무음 실패 금지.
- signup 성공(201) 후 `/login` 리다이렉트 실패(드묾) → 사용자는 수동으로 로그인 이동 가능(가입 자체는 커밋됨, 멱등).
- 동시 중복 가입 → 두 번째 요청 409 → AC-3 "이미 가입된 이메일".
