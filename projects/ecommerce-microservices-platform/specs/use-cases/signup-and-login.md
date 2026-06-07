# Use Case: 회원가입 및 로그인 (DEPRECATED — IAM 위임)

> **DEPRECATED — in-tree auth-service 폐기, IAM 위임.**
>
> TASK-MONO-027 / TASK-FE-067 / TASK-BE-132 으로 ecommerce 자체 auth-service 가
> 폐기되었다. 회원가입·로그인·토큰 갱신·로그아웃은 IAM (iam-platform)
> OIDC 가 소유한다 (web-store / admin-dashboard 는 NextAuth v5 + IAM OIDC).
>
> 권위 있는 출처:
> - `specs/integration/iam-integration.md`
> - `projects/iam-platform/specs/contracts/http/auth-api.md`
>
> 아래 흐름은 폐기된 in-tree auth-service 의 과거 동작을 역사적 참고로 보존한
> 것이며 런타임 동작을 반영하지 않는다.

---

## UC-1: 회원가입

### 액터

- 비인증 사용자 (Guest)

### 사전조건

- 사용자가 시스템에 가입되어 있지 않음

### 정상 흐름

1. 사용자가 회원가입 페이지에 접근한다.
2. 사용자가 이메일, 비밀번호, 이름을 입력한다.
3. 시스템이 입력값을 검증한다.
   - 이메일 형식 확인
   - 비밀번호 최소 요구사항 확인
   - 이름 필수값 확인
4. ~~auth-service~~ 가 계정을 생성한다.
5. ~~auth-service~~ 가 `UserSignedUp` 이벤트를 발행한다.
6. user-service가 이벤트를 수신하여 사용자 프로필을 생성한다.
7. 시스템이 userId, email, name, createdAt을 포함한 201 응답을 반환한다.

### 대안 흐름

- **AF-1: 소셜 로그인 가입** — 현재 미구현 (Out of Scope)

### 예외 흐름

- **EF-1: 이메일 중복** — 이미 가입된 이메일인 경우 `EMAIL_ALREADY_EXISTS` 오류를 반환한다 (409).
- **EF-2: 입력값 오류** — 필수 필드 누락 또는 형식 오류 시 `VALIDATION_ERROR`를 반환한다 (400).

---

## UC-2: 로그인

### 액터

- 가입된 사용자 (Registered User)

### 사전조건

- 사용자가 시스템에 가입되어 있음

### 정상 흐름

1. 사용자가 로그인 페이지에 접근한다.
2. 사용자가 이메일과 비밀번호를 입력한다.
3. ~~auth-service~~ 가 자격 증명을 검증한다.
4. 동시 세션 수를 확인한다.
5. 시스템이 JWT access token (HS256, TTL 1시간)과 opaque refresh token (Redis, TTL 30일)을 발급한다.
6. ~~auth-service~~ 가 `UserLoggedIn` 이벤트를 발행한다.
7. 시스템이 accessToken, refreshToken, expiresIn(3600)을 포함한 200 응답을 반환한다.

### 대안 흐름

- **AF-1: 동시 세션 초과** — 세션 한도 초과 시 가장 오래된 세션을 만료시키고 `SessionLimitExceeded` 이벤트를 발행한 뒤 새 세션을 생성한다.

### 예외 흐름

- **EF-1: 자격 증명 불일치** — 이메일 또는 비밀번호가 틀린 경우 `INVALID_CREDENTIALS` 오류를 반환하고 `LoginFailed` 이벤트를 발행한다 (401).
- **EF-2: 로그인 요청 제한** — 비율 제한 초과 시 `RATE_LIMIT_EXCEEDED` 오류를 반환하고 (429), `LoginFailed` (reason: RATE_LIMITED) 이벤트를 발행한다.

---

## UC-3: 토큰 갱신

### 액터

- 인증된 사용자 (Authenticated User)

### 사전조건

- 사용자가 유효한 refresh token을 보유하고 있음

### 정상 흐름

1. 클라이언트가 refresh token으로 토큰 갱신을 요청한다.
2. ~~auth-service~~ 가 refresh token의 유효성을 확인한다.
3. 새 access token과 refresh token 쌍을 원자적으로 발급한다 (토큰 로테이션).
4. 이전 refresh token을 즉시 폐기한다.
5. `TokenRefreshed` 이벤트를 발행한다.
6. 시스템이 새 토큰 쌍을 포함한 200 응답을 반환한다.

### 대안 흐름

- 없음

### 예외 흐름

- **EF-1: 유효하지 않은 토큰** — refresh token이 존재하지 않거나 만료된 경우 `INVALID_REFRESH_TOKEN` 오류를 반환한다 (401).
- **EF-2: 재사용 감지** — 이미 폐기된 refresh token으로 요청 시 `REFRESH_TOKEN_REVOKED` 오류를 반환한다 (401). 현재 유효한 세션은 무효화하지 않는다 (향후 개선 예정).

---

## UC-4: 로그아웃

### 액터

- 인증된 사용자 (Authenticated User)

### 사전조건

- 사용자가 유효한 access token을 보유하고 있음

### 정상 흐름

1. 클라이언트가 Bearer 토큰과 함께 로그아웃을 요청한다.
2. ~~auth-service~~ 가 해당 세션의 refresh token을 폐기한다.
3. `UserLoggedOut` 이벤트를 발행한다.
4. 시스템이 204 응답을 반환한다.

### 대안 흐름

- 없음

### 예외 흐름

- **EF-1: 미인증 요청** — 유효하지 않은 access token인 경우 `UNAUTHORIZED` 오류를 반환한다 (401).

---

## Related Contracts
- HTTP: `specs/contracts/http/auth-api.md`
- Events: `specs/contracts/events/auth-events.md`
