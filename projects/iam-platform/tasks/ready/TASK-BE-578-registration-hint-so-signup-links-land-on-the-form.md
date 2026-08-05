# Task ID

TASK-BE-578

# Title

가입 링크가 IAM **가입 화면**에 바로 닿을 수 있게 registration hint 를 받는다 — 지금은 로그인 화면을 한 번 거쳐야 한다

# Status

ready

# Owner

backend

# Task Tags

- code
- contract
- test

---

# 배경 — `TASK-FE-097` 이 스토어 쪽에서 할 수 있는 데까지 간 뒤 남은 것

FE-097 은 스토어의 "회원가입" 이 `?error=Configuration` 을 내던 것을 고쳤다. 이제 그 링크는
`signIn('iam')` 으로 OIDC 플로우를 시작하고, 사용자는 **IAM 로그인 화면**에 도착한 뒤 거기서
"회원가입" 을 한 번 더 눌러 가입 폼에 닿는다.

FE-097 의 Goal 은 "가입 화면에 **도착한다**" 였는데, 스토어만으로는 그 한 클릭을 없앨 수 없다.
이유는 우회가 아니라 **설계**다:

- SAS 는 미인증 authorize 요청을 항상 `.loginPage("/login")` 으로 보낸다.
- `SavedRequestTenantResolver` 는 **저장된 `/oauth2/authorize` 요청만** 신뢰한다(코드 주석이
  명시: "guards against an arbitrary saved URL carrying a client_id param"). 그래서 IAM
  `/signup` 으로 **직링크하면** 저장된 요청이 없어 계정이 `fan-platform` 에 태어나고,
  ecommerce 엣지가 그 계정을 받아 주지 않는다 — 실측으로 확인된 동작이다.

즉 "authorize 를 태우면서 가입 화면으로 보내는" 경로는 **IAM 만 열어 줄 수 있다.**

---

# Goal

가입 의도를 가지고 온 사용자가 authorize 를 거친 채로 IAM **가입 폼**에 바로 도착한다.
그 결과 계정은 개시한 클라이언트의 tenant 에 태어난다.

---

# Scope

## In Scope

- authorize 요청에 실린 registration hint 를 인식해, 미인증 사용자를 `/login` 대신
  `/signup` 으로 보낸다
- hint 의 이름/형식 결정 (아래)과 계약 문서화
- 힌트가 없을 때 **기존 동작이 바이트 동일**함을 고정하는 테스트

## Out of Scope

- 스토어 쪽 변경 — FE-097 이 이미 `signIn('iam')` 로 통일해 뒀고, hint 는
  `signIn(provider, options, authorizationParams)` 세 번째 인자로 얹으면 된다
- 소셜 가입 경로

---

# 선택지 (착수 시 판단, ADR 필요 여부 포함)

| 안 | hint | 유의점 |
|---|---|---|
| A | OIDC 표준 `prompt=create` (*Initiating User Registration via OpenID Connect*) | 표준이라는 게 장점. **SAS 가 미지의 `prompt` 값을 어떻게 다루는지 먼저 실측할 것** — 무시인지 거부인지에 따라 안전성이 갈린다 |
| B | 커스텀 파라미터(예: `screen_hint=signup`) | SAS 검증을 건드리지 않는다. 비표준이라 계약에 명시 필요 |
| C | 별도 진입 경로(`/signup/start?client_id=…`)가 authorize 를 서버측에서 먼저 태운다 | 저장된 요청을 스스로 만든다. 표면이 하나 는다 |

> 어느 안이든 **hint 는 tenant 를 정하지 않는다.** tenant 는 계속 저장된 authorize 요청의
> `client_id` 에서 나와야 한다 — hint 를 tenant 힌트로 승격시키면 FE-097 이 피한 바로 그
> 구멍(임의 값으로 tenant 를 고르는 것)을 다시 연다.

---

# Acceptance Criteria

- [ ] **AC-0 (재측정)** — 착수 시 현재 동작을 다시 확인한다: 스토어 "회원가입" → 어디에
      도착하는가, 그리고 **SAS 가 미지의 `prompt` 값을 받으면 무엇을 하는가**(무시/에러).
      후자는 안 A 의 가부를 가른다
- [ ] **AC-1** — hint 를 실은 authorize 요청은 미인증 사용자를 `/signup` 으로 보낸다
- [ ] **AC-2** — hint 가 **없으면** 기존과 동일하게 `/login` 이다(회귀 없음)
- [ ] **AC-3** — 그렇게 가입한 계정의 `tenant_id` 가 개시 클라이언트의 tenant 다
      (`fan-platform` 이면 저장된 요청을 못 쓴 것이다)
- [ ] **AC-4** — 이미 인증된 사용자가 hint 를 달고 오면 가입 폼이 아니라 정상 authorize 로
      진행한다(로그인 상태에서 가입 폼을 보여 주지 않는다)
- [ ] **AC-5** — 계약 문서에 hint 가 적힌다

---

# Related Specs

- `projects/iam-platform/apps/auth-service/.../LoginPageController.java` · `SignupPageController.java`
- `projects/iam-platform/apps/auth-service/.../SavedRequestTenantResolver.java`
- `projects/iam-platform/specs/contracts/http/account-api.md` (§ `X-Tenant-Id`)
- `projects/ecommerce-microservices-platform/apps/web-store/src/app/(auth)/signup/page.tsx` (FE-097)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/account-api.md`

---

# Edge Cases

- hint 와 `prompt=none` 이 함께 온다 — 상충한다
- hint 를 달고 왔는데 이미 그 이메일이 있다 — 가입 폼의 기존 409 경로
- 콘솔·팬 등 다른 클라이언트가 같은 hint 를 쓴다 — tenant 는 여전히 client 에서 나와야 한다

# Failure Scenarios

- **hint 를 tenant 소스로 쓴다** — 임의 값으로 tenant 를 고를 수 있게 된다. AC-3 이 그것을 잡는다
- **`/signup` 으로 보내면서 저장된 요청을 소비/삭제한다** — 가입 후 authorize 를 재개하지
  못해 로그인으로 되돌아간다

# Test Requirements

- hint 유/무 양쪽의 라우팅 테스트
- 가입된 계정의 tenant 확인
- 브라우저 실주행(스토어 "회원가입" → 가입 폼 한 번에)

# Definition of Done

- [ ] 구현 + 테스트
- [ ] 계약 문서 갱신
- [ ] Ready for review
