# TASK-BE-580 — 회원가입의 **영구** 실패(404 `TENANT_NOT_FOUND`)가 *"잠시 후 다시 시도해 주세요"* 로 보고된다

- **Status**: ready
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
