# Task ID

TASK-FE-097

# Title

스토어 로그인 화면의 "회원가입" 이 next-auth v4 시절 URL 로 보내 `?error=Configuration` 을 낸다 — 스토어프런트에 가입 입구가 없다

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

# 배경 — `TASK-BE-575` 의 브라우저 검증 중 발견했다

`/login` 의 "계정이 없으신가요? **회원가입**" 은 `/signup` 으로 가고, `/signup` 은
`redirect('/api/auth/signin/iam?callbackUrl=%2F')` 다. 그런데 이 앱은 **next-auth
`5.0.0-beta.25`** 이고, v5 는 `GET /api/auth/signin/:provider` 를 액션으로 취급하지 않는다.

## 실측

```
클릭  → http://web.ecommerce.local/signup
      → http://web.ecommerce.local/login?error=Configuration
화면  "인증 서버 설정에 문제가 있습니다. 잠시 후 다시 시도해 주세요."
로그  [auth][error] UnknownAction: Unsupported action  (web-store 컨테이너)
```

같은 화면의 **"Global Account 로 로그인" 버튼은 정상**이다 — 그쪽은 클라이언트
`signIn('iam')` 이라 CSRF 와 함께 POST 한다. 즉 프로바이더 설정은 멀쩡하고,
**GET 진입점만 죽었다.** 에러 문구가 "인증 서버 설정 문제" 라 원인을 서버 쪽으로
오인하게 만든다는 점도 나쁘다.

`signup/page.tsx` 의 주석은 이 URL 이 "IAM authorize 로 직행한다" 고 적어 두었다 —
v4 에서는 참이었다. **버전 이전 때 함께 옮겨지지 않은 문장이다.**

## 왜 지금 중요한가

면접 데모의 시작점이 회원가입이다. 지금 지원되는 유일한 가입 경로는
"Global Account 로 로그인" → IAM 로그인 화면 → 거기의 "회원가입" 이라, **스토어를 통해
들어온 사람은 가입 링크를 눌러 실패부터 본다.** (BE-575 의 AC-1 검증은 그래서 IAM
로그인 화면의 링크로 우회해 수행했다.)

---

# Goal

스토어의 "회원가입" 을 누른 사람이 IAM 가입 화면에 도착한다.

---

# Scope

## In Scope

- `app/(auth)/signup/page.tsx` 의 리다이렉트 대상 수정
- 그 파일의 **주석을 v5 사실로 갱신** (지금 주석이 잘못된 근거를 제공한다)
- 회귀 테스트

## Out of Scope

- IAM 가입 화면 자체
- `prompt=create` 힌트 도입 (별도 판단)

---

# 유의점

가입은 **저장된 `/oauth2/authorize` 요청**이 있어야 tenant 가 `ecommerce` 로 유도된다
(`SavedRequestTenantResolver`, TASK-BE-507). IAM `/signup` 으로 **직접** 보내면 저장된
요청이 없어 계정이 `fan-platform` 에 태어난다 — 실측으로 확인된 동작이다. 그러므로
"IAM `/signup` 절대 URL 로 보낸다" 는 오답이다. authorize 를 먼저 태워야 한다.

---

# Acceptance Criteria

- [ ] **AC-0 (재측정)** — 착수 시 위 실측을 다시 한다. 그리고 `/api/auth/*` 로 보내는
      **다른** 링크가 더 있는지 전수로 센다(이 티켓은 `/signup` 하나만 확인했다)
- [ ] **AC-1** — 브라우저로 `/login` → "회원가입" → 가입 → 로그인 → `/my/profile` 까지
      끊김 없이 간다
- [ ] **AC-2** — 그렇게 만들어진 계정의 IAM `tenant_id` 가 `ecommerce` 다
      (`fan-platform` 이면 authorize 를 안 태운 것이다)
- [ ] **AC-3** — 회귀 테스트가 붙는다. 링크가 다시 v4 URL 을 가리키면 실패해야 한다
- [ ] **AC-4** — `signup/page.tsx` 주석이 실제 동작을 설명한다

---

# Related Specs

- `projects/ecommerce-microservices-platform/apps/web-store/src/app/(auth)/signup/page.tsx`
- `projects/iam-platform/specs/contracts/http/account-api.md` (§ `X-Tenant-Id`)

# Related Contracts

- 없음 (프런트 라우팅)

---

# Edge Cases

- 이미 로그인된 사용자가 `/signup` 을 연다
- 레거시 링크(`/signup`)는 404 가 아니라 계속 살아 있어야 한다 — 원래 그 이유로 남긴 라우트다

# Failure Scenarios

- **IAM `/signup` 절대 URL 로 고친다** → 화면은 열리지만 계정이 `fan-platform` 에 생겨
  스토어에서 401 이 된다. AC-2 가 그것을 잡는다

# Test Requirements

- 브라우저 실주행(AC-1/AC-2)
- 링크 대상 회귀 테스트

# Definition of Done

- [ ] 수정 + 테스트
- [ ] 브라우저 검증 증거
- [ ] Ready for review
