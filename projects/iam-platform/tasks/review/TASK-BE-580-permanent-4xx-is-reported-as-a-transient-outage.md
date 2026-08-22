# TASK-BE-580 — 회원가입의 **영구** 실패(404 `TENANT_NOT_FOUND`)가 *"잠시 후 다시 시도해 주세요"* 로 보고된다

- **Status**: review
- **Project**: iam-platform
- **Service**: auth-service (SAS browser signup surface)
- **Type**: bug fix
- **Analysis model**: Opus 5 / **구현 권장**: Sonnet

---

## 배경 — 2026-08-19(UTC) 데모에서 사용자가 밟았다

포트폴리오 데모(`54-116-50-47.sslip.io`)에서 회원가입을 시도하니 화면에:

> **잠시 후 다시 시도해 주세요. 인증 서비스가 일시적으로 불가합니다.**

**아무리 기다려도 안 됩니다.** 일시적 장애가 아니기 때문입니다. `auth-service` 로그를
보면 실제로 일어난 일은 이것입니다:

```
WARN  Signup proxy got client error 404 NOT_FOUND from account-service
      logger=com.example.auth.infrastructure.client.AccountServiceClient
```

그리고 account-service 가 실제로 돌려준 본문은:

```json
{"code":"TENANT_NOT_FOUND","message":"Tenant not found: iam"}
```

## 🔴 결함 — 미분류 4xx 를 전부 "일시적" 으로 접는다

`AccountServiceClient.signup` (L203-215):

```java
if (status == 409) throw new SignupEmailConflictException(...);
if (status == 400 || status == 422) throw new SignupInvalidException(...);
// 429 (rate limit) or any other 4xx from the public endpoint: treat as a
// transient failure so the page shows the generic "try again" message
log.warn("Signup proxy got client error {} from account-service", e.getStatusCode());
throw new AccountServiceUnavailableException("Signup temporarily unavailable", e);
```

주석이 겨눈 것은 **429** 이고 그건 옳다 — 429 는 진짜로 일시적이다. 그런데 조건이
*"그 밖의 모든 4xx"* 라서 **404 처럼 영구적인 것까지 같이 삼킨다.**

**4xx 는 "클라이언트가 잘못했다" 는 뜻이지 "나중에 되면 된다" 는 뜻이 아니다.**
429 만 일시적이고 나머지 4xx 는 재시도해도 같은 답이 온다.

## 🔴 이게 왜 단순한 문구 문제가 아닌가

1. **사용자에게 거짓말을 한다.** 영원히 안 될 일을 *"잠시 후 다시"* 라고 안내한다.
   면접관이 이 데모에서 회원가입을 눌렀다면 그 화면을 봤을 것이다.
2. **원인을 지운다.** 화면에도 로그 요약에도 `TENANT_NOT_FOUND` 라는 단어가 없다.
   나는 이 결함을 **컨테이너 로그를 직접 읽어서** 찾았다 — UI 만 보는 사람에게는
   *"인증 서비스가 가끔 죽는다"* 로 보인다. 있지도 않은 가용성 문제를 쫓게 만든다.
3. **같은 집합에 다른 것도 있다.** 404 만이 아니다 — 403(테넌트 비활성/정지),
   401, 410 등도 지금 전부 *"잠시 후 다시"* 가 된다. 🔵 **"이 집합에 또 뭐가 참인가"**
   를 물어야지 404 한 칸만 빼면 안 된다.

## Goal

**재시도로 해결되는 실패**와 **재시도가 무의미한 실패**를 구별해서 보고한다.

## Scope

- `projects/iam-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/client/AccountServiceClient.java`
  — `signup()` 의 4xx 분류.
- `projects/iam-platform/apps/auth-service/src/main/java/com/example/auth/presentation/SignupPageController.java`
  — 새 예외의 렌더링.
- 필요하면 `templates/signup.html`.

**범위 밖**: 왜 테넌트가 `iam` 인지(= 콘솔 경로에서 회원가입이 구조적으로 불가능한 것).
그건 **TASK-BE-581** 이다. 🔴 **두 티켓 중 하나만 고쳐도 결함이 남는다** — 이것만
고치면 콘솔에서 회원가입은 여전히 100% 실패하고 메시지만 정확해진다.

## Acceptance Criteria

**AC-0 — 재확인 (verify-then-act).**
착수 시점에 `signup()` 의 catch 블록을 다시 읽고, **지금 어떤 상태 코드들이
`AccountServiceUnavailableException` 으로 접히는지 전수로 적어라.** 위 목록(403·401·410…)은
추론이다 — account-service 의 `GlobalExceptionHandler` 가 이 엔드포인트에서 실제로 낼 수
있는 4xx 를 확인해서 확정하라.

**AC-1 — 영구 실패는 영구 실패로 보고된다. 🔴 대조군 필수.**

| 칸 | account-service 응답 | 기대 |
|---|---|---|
| (1) | **429** (**대조군**) | 지금과 동일 — *"잠시 후 다시 시도해 주세요"* |
| (2) | **404 `TENANT_NOT_FOUND`** (**bite**) | 재시도를 권하지 **않는** 메시지 |
| (3) | 409 / 400 / 422 (**회귀 대조군**) | 지금 문구 그대로(BE-472·BE-484 가 정한 것) |
| (4) | 5xx / 연결 실패 (**회귀 대조군**) | *"일시적"* 유지 — 그건 진짜 일시적이다 |

🔴 (1)을 빼지 마라. 없으면 *"전부 영구로 바꿨다"* 와 구별되지 않고, 그러면 실제
rate-limit 이 걸린 사용자에게 *"다시 시도하지 마세요"* 라고 말하게 된다.

**AC-2 — 진단이 로그에 남는다.**
`log.warn` 이 상태 코드만 찍고 **본문(`code`)을 안 찍는다** — 그래서 로그만 봐도
`TENANT_NOT_FOUND` 인지 다른 404 인지 알 수 없었다. 응답 본문의 `code` 를 로그에 넣어라.
🔵 이메일·패스워드는 **찍지 마라**(그 본문에는 없지만, 로깅을 넓힐 때 같이 새기 쉽다).

**AC-3 — 사용자 문구는 안내가 되어야 한다.**
*"오류가 발생했습니다"* 로 바꾸는 것은 개선이 아니다 — 여전히 사용자가 할 수 있는 게
없다. 무엇이 잘못됐고 **어디로 가야 하는지**를 담아라(예: 이 경로로는 가입할 수 없다).
구체 문구는 BE-581 의 결정과 함께 정하는 것이 자연스럽다.

**AC-4 — 테스트.**
`AccountServiceClient` 의 4xx 분류에 대한 단위 테스트를 AC-1 의 네 칸으로 추가한다.
🔴 **고침 전 코드에 대고 돌려 (2)에서 실패하는 것을 확인**하고 적어라 — 안 물리는
테스트는 아무것도 안 지킨다.

## Related Specs

- `projects/iam-platform/specs/features/signup.md` (§User Flow — server-side proxy write path)
- `projects/iam-platform/apps/account-service/.../presentation/advice/GlobalExceptionHandler.java:166` — `TENANT_NOT_FOUND` 발생 지점
- **TASK-BE-484** (done) — *같은 문구*가 나오는 다른 원인(타임아웃 예산 역전). 🔴 이번 것은
  **BE-484 가 아니다**: `ACCOUNT_SERVICE_READ_TIMEOUT` 은 설정돼 있지 않아 BE-484 가 넣은
  기본값 20000 이 그대로 적용 중이고, 실제 실패는 타임아웃이 아니라 404 였다.
  **같은 문구가 두 원인을 덮고 있다는 것 자체가 이 티켓의 논거다.**
- **TASK-BE-472** (done) — 400/422 를 이메일·패스워드 양쪽으로 안내하게 고친 선례.

## Edge Cases

- **429 에 `Retry-After` 가 있는 경우** — 있으면 그 값을 안내에 쓸 수 있다(범위 밖이지만
  분류를 건드리는 김에 확인해 둘 것).
- **본문이 비어 있거나 JSON 이 아닌 4xx** — `code` 추출이 실패한다. 🔴 그때 *"영구"* 로
  단정하지 마라. 추출 실패는 **판정 불가**이고, 판정 불가의 안전한 쪽은 기존 동작이다.
- **account-service 가 아니라 중간 프록시가 낸 404** — 지금은 직접 호출(`http://account-service:8082`)
  이라 없지만, 게이트웨이가 끼면 경로 오타도 404 다. 본문 `code` 로 갈라야 하는 이유다.

## Failure Scenarios

- **404 만 특별 취급하면**: 403(테넌트 정지)·410 이 그대로 *"잠시 후 다시"* 로 남는다.
  AC-0 이 전수를 요구하는 이유다.
- **전부 영구로 뒤집으면**: 진짜 일시적인 429·5xx 에 재시도하지 말라고 안내하게 된다 —
  AC-1 (1)·(4) 가 그 대조군이다.
- **로그에 본문 전체를 찍으면**: 지금 본문엔 PII 가 없지만 계약이 바뀌면 샌다. `code` 만
  찍어라.


---

# 실행 기록 (2026-08-22 UTC)

## AC-0 — 전수. 🔴 **티켓의 추론이 틀렸고, 그 자리에 404보다 나쁜 결함이 있었다**

티켓은 *"403(테넌트 비활성/정지)"* 를 후보로 적었다. account-service `GlobalExceptionHandler`
를 실제로 읽으니 **`TenantSuspendedException` → `409 CONFLICT` (`TENANT_SUSPENDED`)** 다.

그리고 auth-service 는 **모든 `409` 를 이메일 중복으로** 분류하고 있었다:

> 정지된 테넌트에서 가입 시도 → *"이미 가입된 이메일입니다. **로그인해 주세요.**"*

🔴 **이쪽이 404 케이스보다 나쁘다.** 404 는 최소한 *"서비스 문제"* 라고 (틀리게나마) 말했지만,
이건 **거짓인데다 행동 가능해 보이는 지시**라 사용자를 **역시 실패할 로그인으로** 보낸다.
403 이었다면 미분류 4xx 로 떨어져 *"잠시 후 다시"* 가 됐을 것이다 — 즉 **틀린 추론이 이 칸을
숨기고 있었다.** 이미 옳아 보이는 가지에 떨어졌기 때문이다.

⇒ **설계 귀결: 상태 코드만으로는 분류가 불가능하다.** `409` 하나가 정반대 두 뜻을 나른다.
판별자는 **본문 `code`** 여야 한다.

### `POST /api/accounts/signup` 이 낼 수 있는 4xx 전수 (핸들러에서 확인)

| 상태 | `code` | 도달 경로 | 분류 |
|---|---|---|---|
| `409` | `ACCOUNT_ALREADY_EXISTS` | `AccountAlreadyExistsException` | 기존 유지 — 이메일 중복 |
| **`409`** | **`TENANT_SUSPENDED`** | `ActiveTenantGuard` | **영구** (신규) |
| **`404`** | **`TENANT_NOT_FOUND`** | `ActiveTenantGuard` | **영구** (신규) |
| `422` | `VALIDATION_ERROR` | `PasswordPolicyViolationException` | 기존 유지 |
| `400` | — | `@Valid` / 이메일 형식 | 기존 유지 |
| `429` | `RATE_LIMITED` | `RateLimitedException` | 기존 유지 — **진짜 일시적** |

🔵 `403 TENANT_SCOPE_DENIED` 는 이 엔드포인트에 **도달하지 않는다**(내부/관리 경로 전용).
가입 경로는 `ActiveTenantGuard` 만 통과한다.

## AC-1 / AC-4 — 대조군 포함 판정 (`AccountServiceClientSignupClassificationTest`, 10칸)

| 칸 | 응답 | 기대 | 결과 |
|---|---|---|---|
| (1) **대조군** | `429 RATE_LIMITED` | 지금 그대로 *"잠시 후 다시"* | ✅ |
| (2) **bite** | `404 TENANT_NOT_FOUND` | 영구 | ✅ |
| (AC-0 발견) **bite** | `409 TENANT_SUSPENDED` | 영구 | ✅ |
| (3) 회귀 대조군 | `409 ACCOUNT_ALREADY_EXISTS` | 이메일 중복 유지 | ✅ |
| (3) 회귀 대조군 | `400` · `422` | 입력값 안내 유지 | ✅ |
| (4) 회귀 대조군 | `503` · 연결 리셋 | *"일시적"* 유지 | ✅ |
| 판정 불가 | 빈 본문 / 비-JSON / 처음 보는 `code` | **기존 동작 유지** | ✅ |

### 🔴 bite 확인 — 고침 전 코드에 대고 (칸을 **격리**해서 실행)

처음엔 스위트 전체로 돌렸는데 **콘솔 로그와 결과 XML 이 서로 다른 테스트 이름에 실패를
귀속**시켰다. 그 상태의 계측기로 bite 를 보고할 수 없어 칸을 하나씩 격리해 다시 쟀다.

| 칸 | 고침 전 |
|---|---|
| `notFoundTenantIsPermanent` (bite) | **FAILED — 문다** |
| `suspendedTenantIsPermanentNotAnEmailConflict` (bite) | **FAILED — 문다** |
| `rateLimitedStaysTransient` (대조군) | passed — 초록 유지 |
| `unknownCodeIsNotJudgedPermanent` (대조군) | passed — 초록 유지 |

정확히 bite 두 칸만 물고 대조군은 그대로다. 주입은 `git diff --numstat` 으로 **주입됐음을
먼저 확인**한 뒤 실행했다.

### 🔴 그 과정에서 내 코드의 실제 버그를 대조군이 잡았다

`Set.of(...).contains(null)` 은 `false` 를 반환하지 않고 **NPE 를 던진다**. `code` 는 본문을
못 읽으면 `null` 이므로, 판정 불가 칸 전부가 NPE 로 죽었다. 대조군이 없었다면 bite 두 칸만
초록인 채 **모든 미분류 4xx 가 NPE** 인 상태로 나갔을 것이다.

## AC-2 — 진단이 로그에 남는다

`log.warn("Signup proxy got client error {} code={} ...")` — 상태 코드에 **본문 `code`** 를
더했다. 이전엔 상태만 찍혀서 `TENANT_NOT_FOUND` 인지 다른 404 인지 로그만 봐선 알 수 없었고,
그래서 원 결함을 **컨테이너 로그를 손으로 읽어** 찾아야 했다.
🔵 본문 **전체는 찍지 않는다** — 지금은 PII 가 없지만 계약이 넓어지면 샌다. `code` 만.
파싱 실패는 `<unparsed>` 로 찍어 *"코드가 없었다"* 와 *"파싱을 못 했다"* 를 구별한다.

## AC-3 — 사용자 문구

`"이 경로로는 회원가입할 수 없습니다. 계정 생성은 관리자에게 문의해 주세요."`

🔴 **TASK-BE-581 의 게이트 문구와 같은 상수를 쓴다.** 사용자 입장에서 같은 상황이고, 이
저장소는 *"한 사실이 두 절에 있으면 한쪽만 고쳐진다"* 에 반복해서 데였다. 원래 581 의 문구는
*"계정은 관리자가 생성합니다"* 였는데, **정지된 테넌트**에는 참이 아니라 양쪽에 참인 문장으로
바꿨다. 갈라야 할 이유가 생기면 **의도적으로** 갈라라.

## 🔴 581 에서 내가 쓴 틀린 문장 2곳을 정정했다

581 을 구현할 때 *"suspended → 403"* 이라고 적었다. AC-0 실측이 **409** 임을 밝혔으므로
`TenantSignupEligibilityPort` javadoc 과 `TenantSignupEligibilityResolver` 주석, 그리고
`specs/features/signup.md` 의 해당 문장을 고쳤다. 방금 머지한 내 텍스트라도 틀린 건 틀린 것이다.

## 변경된 것

- `SignupNotPossibleException`(신규) — `errorCode` 를 나른다.
- `AccountServiceClient.classifySignupClientError(...)` — 본문 `code` 기반 분류.
  영구 목록은 **상태 규칙이 아니라 `code` allowlist** 다: 처음 보는 4xx 는 기존 동작(일시적)으로
  떨어져야 한다. 상태 규칙이면 **아무도 안 본 실패를 영구로 단정**해 사용자에게 포기하라고 한다.
- `SignupPageController` — `SignupNotPossibleException` 렌더 + 문구 상수 통합.
- `specs/features/signup.md` § 실패의 종류를 구별해 보고한다 (신규).

## 남은 것

**AC-3 의 문구가 라이브에서 실제로 보이는지는 확인하지 않았다.** 데모 인스턴스가 정지 상태이고
현재 AMI(`e632e3b54`)에는 이 변경도 581 도 없다 — `TASK-MONO-399` 가 기록한 대로 다음 재굽기가
필요하다. 🔵 581 AC-4 와 **같은 기동에 묶으면** 한 번으로 끝난다.
