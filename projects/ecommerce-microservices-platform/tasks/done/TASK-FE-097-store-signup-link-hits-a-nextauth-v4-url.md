# Task ID

TASK-FE-097

# Title

스토어 로그인 화면의 "회원가입" 이 next-auth v4 시절 URL 로 보내 `?error=Configuration` 을 낸다 — 스토어프런트에 가입 입구가 없다

# Status

done

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

- [x] 수정 + 테스트
- [x] 브라우저 검증 증거
- [x] Ready for review

---

# 결과 (2026-08-05)

## AC-0 재측정 — 모집단은 **1**이다

`/api/auth/signin/<provider>` 를 URL 로 직접 치는 곳은 `signup/page.tsx` **한 곳뿐**이다.
나머지 `/api/auth/*` 참조는 전부 v5 가 실제로 서빙하는 것들이다 — `signIn()` API 호출
(`auth-context`), `/api/auth/session`, `/api/auth/csrf`, 자체 라우트인
`/api/auth/end-session-url`, 그리고 콜백 URL 문자열(주석·클라이언트 등록). 재현도 그대로:
클릭 → `/signup` → `/login?error=Configuration`, 컨테이너 로그에 `UnknownAction`.

## 🔴 Goal 의 letter 는 스토어만으로 달성할 수 없다 — 그리고 그건 우회가 아니라 설계다

티켓의 Goal 은 "IAM **가입 화면**에 도착한다" 인데, 코드를 읽어 그 경로가 왜 막혀 있는지
확인했다:

- SAS 는 미인증 authorize 요청을 항상 `.loginPage("/login")` 으로 보낸다.
- `SavedRequestTenantResolver` 는 **저장된 `/oauth2/authorize` 요청만** 신뢰한다. 주석이
  이유까지 적어 뒀다 — *"guards against an arbitrary saved URL carrying a client_id param"*.
  그래서 IAM `/signup` 직링크는 tenant 를 못 얻어 계정이 `fan-platform` 에 태어난다.
  **티켓의 Failure Scenario 가 코드로 확증됐다.**

⇒ authorize 를 태우면서 가입 화면으로 보내는 경로는 **IAM 만 열어 줄 수 있다.** 이 티켓의
Out of Scope("IAM 쪽 변경")를 지키는 선에서 할 수 있는 것은 **에러를 없애고 지원되는
경로로 태우는 것**이고, 그렇게 했다. 남은 한 클릭은 **`TASK-BE-578`** 로 분리했다
(registration hint). 코드 주석이 그 티켓을 가리키므로 함께 파일했다.

## 수정

`/signup` 을 클라이언트 컴포넌트로 바꿔 **로그인 버튼과 같은 `login()`(=`signIn('iam')`)**
을 호출한다 — 진입점을 하나로 합쳐 둘이 갈라질 수 없게 했다. 자동 시작은 `useRef` 로 한 번만
걸고, JS 가 늦거나 막히면 **버튼이 남는 화면**을 그대로 보여 준다(빈 화면으로 리다이렉트를
기다리지 않는다). 낡은 주석 — 그 URL 이 "IAM authorize 로 직행한다" 는 v4 시절 문장 — 은
왜 틀렸는지와 함께 다시 썼다.

## AC 별 결과

| AC | 결과 |
|---|---|
| AC-0 재측정 | ✅ 재현 + 모집단 전수 = **1** |
| AC-1 브라우저 | ✅ 5/5 PASS |
| AC-2 tenant | ✅ `ecommerce` (`fan-platform` 아님) |
| AC-3 회귀 테스트 | ✅ 4건. 술어는 **행위**다(아래) |
| AC-4 주석 | ✅ v5 사실로 다시 씀 |

### AC-1 브라우저 실주행

```
PASS  "회원가입" 이 error=Configuration 없이 IAM 으로 넘어간다
      trail = /login -> /signup -> http://iam.local/login
PASS  넘어간 곳은 저장된 authorize 요청을 가진 IAM 로그인 화면이다   /login
PASS  가입이 성공한다                                             iam.local/login?registered
PASS  /my/profile 까지 끊김 없이 도달한다
PASS  레거시 /signup 직접 방문도 IAM 으로 간다 (에러 페이지가 아니다)
ALL 5 CHECKS PASS
```

**AC-2 실측**: `accounts.tenant_id = ecommerce`. 같은 요청에 BE-575 의 프로필 프로비저닝도
함께 걸려 `user_profiles` 에 `tenant_id=ecommerce` 행이 생겼다.

**음성 대조**는 수정 전 같은 환경의 실측이다 — `landed=http://web.ecommerce.local/login?error=Configuration`,
`[auth][error] UnknownAction`. 지금 스크립트의 첫 두 판정이 그 상태에서 RED 다.

### AC-3 — 술어를 URL 문자열이 아니라 행위로 잡았다

`signIn('iam', { callbackUrl: '/' })` 가 호출되는지를 단언한다. **두 오답이 모두 걸린다**:
v4 URL 로 되돌리면 `signIn` 이 호출되지 않고, IAM `/signup` 직링크로 "고쳐도" 마찬가지다
(게다가 그쪽은 tenant 까지 틀린다). 문자열을 기억하지 않아도 회귀가 잡힌다.

## 🔴 로컬에서 vitest 를 돌릴 수 없다 — CI 가 권위다

`vitest 4 × Node 24` 의 `#module-evaluator` 로 **러너가 기동조차 안 된다**(이 저장소에 이미
기록된 블로커). 로컬 Node 는 24 뿐이고 CI 는 20 이다. 그래서 신규 테스트 4건의 실행 증거는
**CI 의 Frontend unit 레인**이다. 로컬에서 확인한 것은 `tsc --noEmit` 통과와
`next lint` 무경고, 그리고 위의 브라우저 실주행이다.

## 함께 올린 티켓

- **TASK-BE-578** — registration hint. Goal 의 남은 한 클릭을 없애는 IAM 쪽 작업이며,
  hint 를 tenant 소스로 승격시키지 말 것(그러면 이 티켓이 피한 구멍이 다시 열린다)을
  AC 로 못박아 뒀다.
