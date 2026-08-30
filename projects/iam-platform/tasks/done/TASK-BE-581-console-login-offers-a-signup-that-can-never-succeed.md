# TASK-BE-581 — 콘솔 로그인에서 제공하는 **회원가입은 절대 성공할 수 없다** (예약 슬러그 `iam` 에는 테넌트 행이 없다)

- **Status**: done
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


---

# 실행 기록 (2026-08-22 UTC)

## AC-0 — 결정: **(A′)** = (A) 링크 조건부 + `/signup` 표면 자체도 정직하게 거절

소유자 결정. (B)/(C)를 버린 근거는 추측이 아니라 아래 실측이다.

### 티켓이 인용한 근거 — 전부 축자 확인함

| 인용 | 확인 |
|---|---|
| `V0024` *"so no consumer can register it"* | ✅ 축자 일치 |
| `iam` 예약어 | ✅ `multi-tenancy.md` L60 + `CreateTenantUseCase.RESERVED` (11개, `400 TENANT_ID_RESERVED`) |
| `login.html:83` 무조건 링크 | ✅ `th:if` 없음 |
| 404 → *"일시적으로 불가"* | ✅ `AccountServiceClient.signup` L211-215, "any other 4xx" 가지 |

### 🔴 (B)를 버린 진짜 이유 — 티켓에 없던 사실: **(B)는 문제를 고치지 못한다**

`V0015` 가 남긴 문장 *"Operator identity is resolved by admin-service, **NOT** by this client's
tenant_id"* 를 끝까지 따라갔다. 콘솔 로그인은 OIDC 뒤에 **토큰 교환**이 있다
([admin-service `security.md` § OIDC Subject → Operator Resolution](../../specs/services/admin-service/security.md)):

> `sub`(account_id)가 어떤 `admin_operators` row 와도 매칭되지 않음 → **fail-closed `401 TOKEN_INVALID`**

운영자는 SUPER_ADMIN 이 `POST /api/admin/operators` 로만 만든다
([operator-management.md](../../specs/features/operator-management.md)) — 셀프 가입 경로가 없다.
⇒ `iam` 을 시드해 가입이 201 이 되어도 그 계정은 `accounts` 에만 생기고 `admin_operators` 에는
없으므로 **로그인에서 401 재로그인 루프**가 된다. (B)는 예약 정책을 거스르는 대가로 *404(명확한
실패)* 를 *"가입은 됐는데 절대 못 들어감"(불명확한 실패)* 으로 **악화**시킨다.

(C)는 티켓 판단대로 가장 나쁘다: `TenantContext.DEFAULT_TENANT_ID = "fan-platform"` 이므로 콘솔에서
가입한 사람이 조용히 fan-platform 계정이 되고, **로그인은 여전히 401** 이다 — 실패는 그대로 남고
엉뚱한 테넌트에 계정만 하나 늘어난다.

### (A′) 를 (A) 로 좁히지 않은 이유

`GET`/`POST /signup` 은 `permitAll` 이라 링크를 감춰도 **URL·북마크·뒤로가기**로 도달한다. 그
경로에서는 여전히 404 → *"일시적으로 불가"* 가 뜬다. 링크만 고치면 오늘의 증상이 **가장 흔한
재방문 경로에 그대로 남고**, 그 처리를 TASK-BE-580 의 문구 변경에 통째로 떠넘기게 된다.

### 술어를 예약어 목록이 아니라 **테넌트 레코드**로 둔 이유

백엔드의 실제 판정자는 account-service `ActiveTenantGuard` = **행 존재 AND `status=ACTIVE`**.
예약어 목록으로 판정하면 *"우리가 생각해 둔 값인가"* 라는 다른 질문에 답하게 되고, **정지 테넌트
칸(403, 404 아님)** 을 통째로 놓친다. `GET /internal/tenants/{id}` 는 이미 `status` 를 반환하고
`AccountServicePort` 는 이미 404 를 `Optional.empty` 로 구별하고 있었으므로 **새 API 없이** 같은
축을 물을 수 있었다.

## AC-1 — 대조군 포함 판정 (`LoginPageSignupLinkSliceTest`, `SignupPageBlockedSliceTest`)

🔴 모델 속성이 아니라 **실제 Thymeleaf 템플릿을 렌더해 HTML 바이트를 읽는다** — `th:if` 오타·삭제·
엉뚱한 요소 부착에도 모델 단언은 통과하기 때문이다. 음성 칸은 **페이지가 실제로 렌더됐는지**를
같이 단언한다(렌더 실패도 "링크 없음"으로 채점되므로).

| 칸 | 진입 클라이언트 | 기대 | 결과 |
|---|---|---|---|
| (1) **bite** | `platform-console-web` (`iam`) | 링크 없음 · `/signup` 폼 없음 · 프록시 호출 0회 | ✅ |
| (2) 대조군 | `fan-platform-user-flow-client` | **지금 그대로** 가입 가능 | ✅ |
| (3) 대조군 | `ecommerce-web-store-client` | **지금 그대로** 가입 가능 | ✅ |
| (4) 대조군 | saved request 없음 (직접 `/signup`) | 기존 폴백(`fan-platform`) 유지 | ✅ |
| (5) 추가 | `ecommerce` 인데 **가입 불가**(정지 가정) | 링크 없음 | ✅ |

칸 (5)는 *"문자열 `iam` 을 특별취급"* 하는 가짜 수정을 막는다.

### 🔴 bite 확인 — 고치기 전 코드에 실제로 무는가 (3회, 전부 물음)

| 주입 | 결과 |
|---|---|
| `login.html` 의 `th:if` 제거 | 5칸 중 **2칸 빨강**(음성 칸만), 대조군 3칸 초록 유지 |
| `POST /signup` 게이트 제거 | 5칸 중 **2칸 빨강** |
| 술어를 *존재 여부만* 으로 약화 | 7칸 중 **1칸 빨강**(SUSPENDED 칸만) |

주입은 매번 `git diff --stat` 으로 **주입됐음을 먼저 확인**한 뒤 실행했다 — "안 물었다" 와
"주입이 0건이었다" 는 로그에서 구별되지 않는다.

### 🔴 도중에 발견한 내 술어의 오류 (기록)

`doesNotContain("패스워드는 8자 이상이어야 합니다")` 를 **페이지 전체**에 걸었더니 실패했다.
서버가 그 메시지를 만든 적이 없는데도 — 같은 문장이 페이지의 **클라이언트 사전검사 `alert()`**
에도 있었다. 한 문자열, 두 출처. 판정은 서버가 말한 곳(`.error` 요소)만 읽도록 고쳤다.

부수 발견: 폼을 감추면 `document.getElementById('signup-form').addEventListener(...)` 가 null 에
걸려 TypeError 가 난다 → 스크립트도 같이 `th:if` 로 묶었다.

## AC-2 — 매달린 `tenant_id` 가드 (`OAuthClientTenantReferenceIntegrationTest`)

**러너**: `@Tag("integration")` → CI 잡 **`Integration (iam <shard>, Testcontainers)` 샤드 B**
(`:projects:iam-platform:apps:auth-service:integrationTest`).

### 🔴 정적 grep 을 쓰지 않은 이유 — 실측

- 마이그레이션 **파일**의 distinct `tenant_id` 리터럴 = **9개**, 그중 `gap` 은 **어떤 행도 갖지
  않는다**(`V0024` 가 `UPDATE` 로 `iam` 으로 바꿈 — grep 은 UPDATE 를 재생하지 못한다).
- 런타임 실제 = **8개**. ⇒ 텍스트 스캔의 모집단은 **양방향으로 틀리다**.
- 그래서 Flyway 로 **실제로 마이그레이션한 뒤 테이블을 읽는다**.

### 🔴 모집단은 production 마이그레이션만

`account-service` 의 테넌트 시드는 `db/migration`(prod, 7개) 과 `db/migration-dev`(demo/e2e 전용,
5개)로 갈린다. dev 시드가 섞인 DB에서 재면 **production 이 깨져 있어도 초록**이다. 판정 모집단은
prod 전용이며, 그 사실을 주석이 아니라 **대조군 테스트**로 증명한다 — dev 마이그레이션을 별도 DB에
적용해 차집합으로 dev 전용 집합을 **유도**하고(복사하지 않고), 비었으면 실패한다.

### 분류 — 예약 슬러그를 그냥 통과시키지 않는다

| 범주 | 판별자 (하드코딩 아님) | `tenants` 행 |
|---|---|---|
| 실재 테넌트 | prod `tenants` 조회 | 있어야 함 |
| **예약 슬러그** | `CreateTenantUseCase.RESERVED` 를 **소스에서 파싱** + `multi-tenancy.md` 와 교차검증 | 없는 것이 정상 |
| **INTERNAL 센티넬** | 클라이언트 행 자신의 `tenant_type='INTERNAL'` | 없는 것이 정상 |
| **UNKNOWN** | 위 어디에도 없음 | **실패** |

예약/INTERNAL 은 "통과"가 아니라 **별도 단언을 받는다**: 그 값이 `tenants` 에 **실제로 없는지**
확인한다. 즉 누군가 `iam` 을 테넌트로 시드하면(= 이번에 기각한 (B)) 이 가드가 **빨간불**이 된다.

### 대조군 (AC-2 요구)

존재하지 않는 `tenant_id` 를 가진 클라이언트 행을 **마이그레이션된 DB에 실제로 INSERT** 하고
**같은 read+classify 경로로 다시 읽어** UNKNOWN 으로 잡히는지 확인한다. 분류 함수만 호출하는 약한
대조군은 `SELECT` 가 `tenant_id` 를 빠뜨려도 통과하므로 쓰지 않았다. 주입이 리더에 **보이는지**를
먼저 단언한다. 반대 방향도 함께 고정한다(`wms`→EXISTS, `iam`→RESERVED,
`global-account-platform`→INTERNAL) — 네 칸 모두에 발화하는 가드는 똑같이 무용하다.

⚠️ **이 가드는 로컬에서 실행하지 못했다** — 이 호스트는 Docker 데몬이 꺼져 있다
(`npipe:////./pipe/docker_engine` 연결 실패). Testcontainers 판정은 CI 가 권위다. 컴파일은 통과했고,
CI 결과로 확증해야 한다.

## AC-3 — `global-account-platform`

분류: **INTERNAL 워크로드 센티넬**(예약도 누락도 아닌 제3범주). 근거는 `V0019` 자신의 문장 —
*"GAP platform infrastructure, not bound to a product tenant … the tenant claim is informational
here."* 판별자는 문서가 아니라 **클라이언트 행의 `tenant_type='INTERNAL'`** 이라 새 클라이언트가
생겨도 따라간다.

*"이번 왕복에서 안 터졌다"* 를 근거로 쓰지 않기 위해, 가드는 **INTERNAL 클라이언트가
`authorization_code` grant 를 갖지 않는지** 단언한다 — 그 grant 가 생기는 순간 브라우저 경로가
열리고 오늘의 `iam` 과 같은 결함이 된다.

### 🔴 이 과정에서 발견한 **인접 미해소 결함** (본 티켓 범위 밖 — 별도 티켓 필요)

`global-account-platform` 은 예약어 목록에 **없고**, 슬러그 정규식 `^[a-z][a-z0-9-]{1,31}$`(23자)
를 통과한다 ⇒ **지금 어떤 소비자든 그 이름으로 테넌트를 등록할 수 있다.** 등록되면 내부 워크로드
센티넬이 실재 제품 테넌트와 충돌한다. 예약어 추가는 `admin-api.md § Tenant ID 규칙` 계약 변경이라
본 티켓에서 처리하지 않고 [multi-tenancy.md](../../specs/features/multi-tenancy.md) 에 명시적으로
기록했다.

## AC-4 — 라이브 판정: **미충족**

데모 인스턴스가 정지 상태이며 기동은 사용자 승인 대상이다(컨트롤 API `POST /start`, 부팅 약 11분,
월 예산 잔여 104분). 🔴 SSR grep 으로 대체하지 않았다 — console-web 은 클라이언트 렌더라 grep 0건이
*"링크 없음"* 을 뜻하지 않는다. **이 티켓은 AC-4 미충족 상태로 review 에 있다.**

## 변경된 것

- `AccountServicePort.getTenant(...)` + `TenantLookupResult(tenantType, status)` — 기존
  `GET /internal/tenants/{id}` 응답에서 이미 오던 `status` 를 버리지 않고 읽는다. 두 리더의 실패
  정책(404=답 / non-404 4xx=장애)은 `tenantLookup(...)` 하나로 합쳐 서로 어긋날 수 없게 했다.
- `TenantSignupEligibilityPort` / `TenantSignupEligibilityResolver` — `ActiveTenantGuard` 와 같은
  술어. 양성만 캐시(음성은 나중에 프로비저닝될 수 있으므로). **장애 시 fail-open** — 이건 UX
  게이트이지 권한 경계가 아니고, 닫는 쪽으로 실패시키면 account-service 장애 동안 **모든 소비자
  화면**에서 가입 링크가 사라져 BE-470 을 조용히 되돌린다.
- `LoginPageController` / `login.html` — 링크 조건부.
- `SignupPageController` / `signup.html` — `GET`/`POST` 양쪽 게이트. 거절이 **필드 검증보다 먼저**
  온다(패스워드를 고쳐도 가능해지지 않는 일에 대해 패스워드를 지적하지 않기 위해).
- `specs/features/signup.md` § 브라우저 회원가입 화면의 제시 조건 (신규)
- `specs/features/multi-tenancy.md` § `oauth_clients.tenant_id` 가 실재 테넌트가 아닌 경우 (신규)
- `auth-service/build.gradle` — 가드가 읽는 **모듈 밖 파일**을 `integrationTest` 의 입력으로 선언.
  없으면 형제 파일만 바뀐 PR에서 태스크가 UP-TO-DATE 로 남아 **가드가 겨눈 드리프트 위에서 초록**이
  된다(같은 모듈의 `DemoSeedCredentialTest` 가 이미 겪은 일이라 그 선례를 따랐다).

## TASK-BE-580 과의 관계

580 은 이제 **지울 문구가 아니라 쓸 문구**를 갖는다: 콘솔 경로의 404 는 581 이 먼저 막으므로,
580 이 다룰 영구 4xx 는 *"이 집합에 또 뭐가 참인가"*(403 정지·401·410) 쪽으로 좁혀진다.

## CORRECTION (2026-08-29) — **AC-4 라이브 판정 완료. 채택안대로 동작한다.**

AC-4 가 *"미충족 — 데모 인스턴스가 정지 상태"* 로 닫혀 있었다. `TASK-MONO-581` 이 묶은
재굽기 + 기동 한 창에서 **판정했다.**

| | |
|---|---|
| AMI | `ami-0caf015f7cd9144fd` — `main` **`6bc2a44e7`** |
| 창 | 2026-08-29 15:50~17:08Z · IdP `iam.3-38-176-240.sslip.io` |

**측정** — `console/api/auth/login` 이 만드는 authorize 요청으로 세션을 만들고, 그 쿠키로
IdP 의 `/login` 을 받았다:

| | `signup` 문자열 | **`<a href="…signup…">`** |
|---|---|---|
| **대조군 · 세션 없음** (4,247 B) | 5건 | **있다** — `href="/signup"` |
| **콘솔 flow** (4,052 B) | 4건 | **없다** — 남은 4건은 전부 HTML 주석 |

🔴 **문자열 개수로 판정하면 틀린다.** 5→4 는 «줄었다» 일 뿐이고, 판정 대상은 **앵커의 유무**다.
남은 4건은 `TASK-BE-470`/`TASK-BE-581` 을 설명하는 주석이라 **링크가 사라져도 남는다** —
순진한 `grep -c signup` 이면 «여전히 있다» 로 읽힌다.

🔵 AC-4 가 *"SSR HTML grep 으로 판정하지 마라 — console-web 은 클라이언트 렌더"* 라고 경고한
그 함정은 **console-web 의 페이지**에 대한 것이다. 여기서 판정한 페이지는 auth-service 가
**서버 렌더**하는 `login.html` 이므로 HTML 판정이 유효하다. 🔴 다만 위 문단이 보이듯
**같은 매체에 다른 함정**이 있었다(주석이 판정 문자열을 담는다).
[[feedback_a_discriminator_can_match_its_own_documentation]]

🔵 그리고 이 판정은 술어의 **경로 전체**를 지난다: saved request → `client_id=platform-console-web`
→ `oauth_clients.tenant_id='iam'` → `tenants` 에 없는 예약 슬러그 → `signupAvailable=false`.
세션이 없으면 폴백 테넌트를 받아 링크가 **살아 있는** 것도 같이 확인했다 — 즉 게이트가
«항상 끄는» 것이 아니라 **테넌트에 따라 갈린다.**

⇒ **AC-4 충족.**

### 3-dim 검증 (close chore, 2026-08-29)

| 축 | 결과 |
|---|---|
| (a) | `state=MERGED` — PR [#3430](https://github.com/kanggle/monorepo-lab/pull/3430) |
| (b) | 스쿼시 `def5fe981` = `origin/main` 조상 ✔ |
| (c) | 🔴 **실패 체크가 있었다 — 그러나 전부 `Vercel – …` 행이다** |

🔴 **(c) 를 「0건」으로 적으면 거짓이다.** 실제로는 1건(`Vercel – kanggle-portfolio`) 이 FAILURE 였다.
🔵 **다만 코드 체크는 하나도 안 빨갰다** — 빌드·테스트·가드 잡 전부 통과했고, 빨간 것은
배포 행뿐이다. 이 PR 이 머지된 **2026-08-22 는 `TASK-MONO-562`(*"Vercel build rate limit
reds every PR"*)가 물던 시기**이고, 그 축은 이 저장소의 코드와 무관하다.
🔴 그리고 이때는 `main` 의 **required 집합이 비어 있었다**(`TASK-MONO-598` 이 08-28 에 넷을
등록하기 전) ⇒ 규칙 문구 그대로의 «실패 required 0건» 은 **어떤 상태에서도 참**이었다.
그래서 그 문구에 기대지 않고 **무엇이 빨갰는지 열어서** 판정했다.
