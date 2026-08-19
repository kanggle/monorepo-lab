# TASK-BE-581 — 콘솔 로그인에서 제공하는 **회원가입은 절대 성공할 수 없다** (예약 슬러그 `iam` 에는 테넌트 행이 없다)

- **Status**: ready
- **Project**: iam-platform
- **Service**: auth-service (SAS browser login/signup surface)
- **Type**: bug fix (design decision required)
- **Analysis model**: Opus 5 / **구현 권장**: Opus (문구 수정이 아니라 **어느 경로를 살릴지**의 판단이 본체)

---

## 배경 — 2026-08-19(UTC) 데모에서 사용자가 밟았다

포트폴리오 데모에서 콘솔을 열어 회원가입을 눌렀더니
*"인증 서비스가 일시적으로 불가합니다"* 가 떴다. 문구 자체의 결함은 **TASK-BE-580**
이고, 이 티켓은 **그 뒤에 있던 진짜 이유**다.

## 사슬 — 끝에서 끝까지 실측했다

```
console.<host>/                                    (console-web)
  → iam.<host>/oauth2/authorize?client_id=platform-console-web    ← 세션에 saved request 저장
  → SAS login.html      "계정이 없으신가요? 회원가입"   ← login.html:83, 조건 없음
  → SAS signup.html   POST /signup
  → SavedRequestTenantResolver → saved request 의 client_id = platform-console-web
                                → oauth_clients.tenant_id = "iam"
  → auth → account  POST /api/accounts/signup   X-Tenant-Id: iam
  → 404 {"code":"TENANT_NOT_FOUND","message":"Tenant not found: iam"}
```

## 🔴 그리고 `iam` 은 **설계상** 테넌트가 아니다 — 실수가 아니다

`V0024__rename_gap_slug_to_iam.sql` 이 명시한다:

> *"platform-console-web **reserved** tenant_id 'gap' → 'iam'. The console's own
> operational tenant slug (V0015). `iam` is added to the admin-service
> **reserved-word set** (CreateTenantUseCase / multi-tenancy.md) in the same PR
> **so no consumer can register it**."*

즉 `iam` 은 콘솔 자신의 운영 슬러그이고, **아무도 그 이름의 테넌트를 만들 수 없게
막아 두었다.** account-service 의 `tenants` 에 `iam` 행이 없는 것은 정상이다.

⇒ **콘솔 경로의 회원가입은 100% 실패한다. 일시적 실패가 아니라 구조적 불가능이다.**

## 실측 — 대조군까지 갈랐다 (라이브 인스턴스, account-service 직접 호출)

| 칸 | `X-Tenant-Id` | 출처 | 결과 |
|---|---|---|---|
| **bite** | `iam` | `platform-console-web` | **404 `TENANT_NOT_FOUND: iam`** |
| **대조군** | `wms` | `wms-user-flow-client` | **201 CREATED** |
| 대조군 | `ecommerce` | `ecommerce-web-store-client` | **201 CREATED** |
| 대조군 | `fan-platform` | `fan-platform-user-flow-client` | **201 CREATED** |
| bite2 | `global-account-platform` | 내부 클라이언트 **4개** | **404 `TENANT_NOT_FOUND`** |

🔵 대조군이 201 이라는 것이 중요하다 — **기전은 멀쩡하다.** 고장난 것은 *이 테넌트에서*
가입하려는 시도 하나다. (*"account-service 가 이상하다"* 로 번지지 않게 하는 칸이다.)

## 🔴 `oauth_clients.tenant_id` 8개 중 **2개**가 존재하지 않는 테넌트를 가리킨다

전수로 셌다. `oauth_clients` 의 distinct `tenant_id`:

```
ecommerce  erp  fan-platform  finance  iam  scm  wms  global-account-platform
```

`account_db.tenants` 에 있는 것:

```
acme-corp  demo-corp  ecommerce  erp  fan-platform  finance
globex-corp  initech-corp  ip-pilot-corp  scm  umbrella-corp  wms
```

⇒ **매달린 값 2개**: `iam`(브라우저에서 도달 가능) · `global-account-platform`
(`account-service-client`·`admin-service-client`·`auth-service-client`·`security-service-client`
— 전부 client_credentials 라 지금은 잠복).

🔴 **이 불일치를 검사하는 것이 아무것도 없다.** 두 DB(`auth_db` / `account_db`)에 걸친
참조라 FK 로는 못 잡고, 마이그레이션도 각자 돈다. 잠복한 쪽이 언젠가 브라우저 경로를
얻으면 오늘의 `iam` 과 똑같이 터진다.

## Goal

콘솔(및 예약 슬러그를 쓰는 모든 클라이언트) 경로에서 **성공할 수 없는 회원가입을
제시하지 않는다.**

## Scope

먼저 **결정**이 필요하다. AC-0 이 그 결정을 강제한다.

- **(A) 링크를 조건부로 만든다** — saved request 의 클라이언트 테넌트가 가입 불가면
  `login.html` 에서 회원가입 링크를 감춘다. 🔵 콘솔 운영자는 관리자가 만드는 것이
  제품 의도로 보이므로(운영자 생성 흐름이 따로 있다) 이 안이 의도와 맞아 보인다.
- **(B) 예약 슬러그를 실제 테넌트로 시드한다** — V0024 가 *"아무도 등록 못 하게"* 막아 둔
  것을 정면으로 거스른다. 🔴 채택하려면 **그 예약을 왜 되돌리는지** ADR 급 근거가 필요하다.
- **(C) 가입 시 기본 테넌트로 강등한다** — 콘솔에서 가입한 사람이 조용히 `fan-platform`
  계정이 된다. 🔴 **가장 나빠 보인다** — 실패가 조용한 오배치로 바뀔 뿐이다.

**범위 밖**: 4xx 분류·문구(**TASK-BE-580**). 🔴 **둘 중 하나만 고치면 결함이 남는다** —
이것만 고치면 다른 영구 4xx 가 여전히 *"잠시 후 다시"* 로 보고된다.

## Acceptance Criteria

**AC-0 — 결정과 그 근거. 🔴 이게 이 티켓의 본체다.**
(A)/(B)/(C) 중 하나를 고르고 **왜 나머지를 버렸는지** 적어라. 판단 근거는 추측이 아니라
문서에서: `multi-tenancy.md` 의 예약어 정책, 운영자 생성 흐름의 스펙, `V0015`/`V0024` 의
의도. 🔴 **`iam` 이 예약된 이유를 읽지 않고 (B)를 고르지 마라.**

**AC-1 — 콘솔 경로에서 회원가입이 제시되지 않거나(A), 성공한다(B). 🔴 대조군 필수.**

| 칸 | 진입 클라이언트 | 기대 |
|---|---|---|
| (1) | `platform-console-web` (**bite**) | 채택안대로 — 링크 없음(A) 또는 201(B) |
| (2) | `fan-platform-user-flow-client` (**대조군**) | **지금 그대로** 회원가입 가능 |
| (3) | `ecommerce-web-store-client` (**대조군**) | **지금 그대로** 회원가입 가능 |
| (4) | saved request **없음**(직접 `/signup` 진입) | 기존 폴백(기본 테넌트) 유지 |

🔴 (2)(3)(4)를 빼지 마라. `login.html:83` 의 링크는 **TASK-BE-470 이 "dead-end 였던 것을
고치려고" 넣은 것**이다 — 그걸 조건부로 만들면서 실수로 전부 감추면 BE-470 을 되돌리는
것이 되고, 그 회귀는 콘솔이 아니라 **일반 사용자 경로**에서 터진다.

**AC-2 — 매달린 `tenant_id` 를 잡는 가드. 🔴 이 티켓의 오래 가는 산출물.**
`oauth_clients.tenant_id` 중 `account_db.tenants` 에도 없고 **예약 슬러그 목록에도 없는**
값이 있으면 실패하는 검사를 만든다.

- 🔴 **예약 슬러그를 그냥 통과시키지 마라** — 그러면 `iam` 은 조용히 넘어가고 가드가
  오늘의 결함을 못 본다. 예약 슬러그는 *"테넌트 없음이 정상이지만 브라우저 가입 경로가
  닿으면 안 되는 값"* 으로 **따로 분류**되어야 한다.
- 🔴 **대조군**: 존재하지 않는 `tenant_id` 를 가진 클라이언트를 하나 주입했을 때 **실제로
  실패하는지** 확인하고 결과를 적어라. 안 물면 술어가 틀린 것이다.
- 러너를 명시하라 — **어느 CI 잡이 이걸 돌리는가.** 러너 없는 검사는 썩는다.

**AC-3 — `global-account-platform` 도 같이 처리한다.**
잠복이지 무해가 아니다. AC-2 의 가드가 이 값을 **어떻게 분류하는지** 명시하라
(예약인가, 누락인가). 🔵 *"이번 왕복에서 안 터졌다"* 는 근거가 아니다.

**AC-4 — 라이브 판정.**
콘솔에서 로그인 화면까지 가서 채택안대로 동작하는지 **브라우저로** 확인한다.
🔴 SSR HTML grep 으로 판정하지 마라 — console-web 은 클라이언트 렌더라 grep 0건이
*"링크가 없다"* 를 뜻하지 않는다.

## Related Specs

- `projects/iam-platform/specs/features/signup.md`
- `projects/iam-platform/apps/auth-service/src/main/resources/db/migration/V0015__seed_platform_console_oidc_client.sql`
- `projects/iam-platform/apps/auth-service/src/main/resources/db/migration/V0024__rename_gap_slug_to_iam.sql` — 예약 근거
- `projects/iam-platform/apps/auth-service/src/main/resources/templates/login.html:83` — 무조건 링크 (TASK-BE-470)
- `.../auth-service/.../infrastructure/security/SavedRequestTenantResolver.java` — 테넌트 유도
- **TASK-BE-580** — 같은 사건의 앞면(문구/분류). 둘 다 필요하다.
- **TASK-BE-507** — `X-Tenant-Id` 전달을 도입한 티켓. 폴백이 `fan-platform` 인 근거.

## Edge Cases

- **saved request 가 만료된 뒤 가입** — 세션이 끊기면 클라이언트를 못 찾아 기본 테넌트로
  떨어진다. 콘솔에서 시작했는데 **성공**할 수도 있다는 뜻이다(오배치). (A)를 골라도 이
  경로는 남는다 — 명시하고 판단하라.
- **`demo-spa-client`·`test-internal-client`** — 둘 다 `fan-platform` 이라 지금은 무해하지만
  AC-2 의 모집단에 들어간다.
- **테넌트가 존재하지만 `status != ACTIVE`** — 404 가 아니라 403 일 수 있다. AC-2 의
  술어를 *"행이 있는가"* 로만 두면 이 칸을 놓친다.

## Failure Scenarios

- **(C)를 고르면**: 콘솔에서 가입한 사람이 조용히 다른 테넌트 계정이 된다. 실패가
  **오배치**로 바뀌고, 오배치는 404 보다 훨씬 늦게 발견된다.
- **AC-2 없이 (A)만 하면**: 오늘의 증상만 사라지고 `global-account-platform` 은 그대로
  남는다. 다음에 그 클라이언트가 브라우저 경로를 얻는 날 같은 조사를 처음부터 다시 한다.
- **가드가 예약 슬러그를 무조건 통과시키면**: 그 가드는 **오늘의 결함에 초록**이다 —
  즉 아무것도 안 지킨다.
