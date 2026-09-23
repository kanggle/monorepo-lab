# ADR-MONO-076 — 워크로드 자격이 «어느 테넌트로» 행동할 수 있는가

**Status:** PROPOSED
**Date:** 2026-09-23
**주관 티켓:** `TASK-MONO-721` (AC-0 = 갈래 **ⓑ**, 소유자 결정 2026-09-23)
**선행 실측:** `TASK-MONO-717` § CORRECTION · `TASK-MONO-672` 항목 12 (16차 데모 창, 2026-09-23)
**관련 결정:** [`ADR-MONO-061`](ADR-MONO-061-workload-token-authorization-plane.md) (워크로드 토큰의 **role** 축 — 이 ADR 은 그 옆에 **tenant** 축을 놓는다) · [`ADR-MONO-020`](ADR-MONO-020-operator-multitenant-assignment.md) (assume-tenant 교환) · [`ADR-MONO-042`](ADR-MONO-042-ecommerce-seller-onboarding-iam-provisioning.md) (셀러 온보딩)

> 🔴 **이 ADR 은 PROPOSED 다.** `TASK-MONO-721` 의 AC-1·AC-2 구현은 소유자의 정확형
> (`ADR-MONO-076 ACCEPTED` + 갈래 letter)이 도착할 때까지 **PAUSE** 한다.
> 규정: [`platform/architecture-decision-rule.md`](../../platform/architecture-decision-rule.md) § The ACCEPTED Gate.

---

## Context

### 무엇이 막혔나 (추론 아님 — 라이브 실측)

`ecommerce` 의 셀러 온보딩은 `product-service` 가 iam 의 계정 프로비저닝 API 를 부르는 것으로
끝난다. 16차 데모 창(2026-09-23)에서 그 호출은 **두 경로 모두** 실패했다.

```
게이트웨이 경유  POST /internal/tenants/ecommerce/identities:resolveOrCreate
    (스코프를 실은 유효 토큰으로)  →  403 {"code":"TENANT_SCOPE_DENIED",
                                          "message":"path tenantId does not match token claim"}
직접 경로        product-service → account-service
                                  →  000   (연결 자체가 안 됨)
```

원인은 배선이 아니라 **모델**이다.

- 게이트웨이 `JwtAuthenticationFilter` 는 `/internal/tenants/{tenantId}/**` 에서
  **경로의 `{tenantId}` 와 토큰의 `tenant_id` 클레임이 같아야** 통과시킨다.
- account-service `TenantScopeGuard` 는 게이트웨이가 주입한 `X-Tenant-Id` 를 경로와 다시 대조한다
  (2선 방어). 헤더가 **없으면** 건너뛴다 — 근거는 *"게이트웨이 계층을 신뢰한다"* 이다.
- `product-service-client` 는 `tenant_id = global-account-platform` 이고 경로는 `ecommerce` 다
  ⇒ **영원히 불일치.**
- 직접 경로가 000 인 이유는 네트워크다: `ecommerce_ecommerce-net` 과
  `iam_iam-e2e`·`traefik-net` 은 **공유 네트워크가 0** 이다.

### 🔴 이것은 결함이 아니라 **빠진 결정**이다

두 판정기는 **운영자 토큰**을 위해 설계됐고, 운영자에게는 «토큰의 테넌트» 와
«행동 대상 테넌트» 가 **같은 것**이다. [`jwt-standard-claims.md`](../../platform/contracts/jwt-standard-claims.md)
§ JWT Validation 규칙 6 이 그것을 못 박아 두었다:

> *"**iam is intentionally excluded** (not a row above): the IdP authorizes by **tenant-scoping on its
> own `/internal/tenants/{id}/**` surface**, not by a platform-surface role."*

즉 `tenant_id == 경로 테넌트` 는 이 표면의 **인가 규칙 그 자체**다. 워크로드를 위해 그것을
느슨하게 하는 것은 «설정» 이 아니라 **인가 평면의 변경**이고, 그래서 ADR 이다.

**워크로드는 다르다**: 플랫폼 구성요소가 **경로가 지명한 테넌트를 대신해** 행동한다.
`product-service` 는 `global-account-platform` 에 살면서 `ecommerce`(와 데모에서는 `demo-corp`)에
계정을 만들어야 한다. 이 모양을 허가하는 규칙이 **아직 아무 데도 없다.**

### 🔵 `ADR-MONO-061` 이 이미 답의 절반을 정해 뒀다

061 은 **role** 축에 대해 같은 질문을 받고 세 제약을 세웠다. 이 ADR 은 그것을
**tenant** 축에 그대로 옮기는 것을 전제로 삼는다(그러므로 이 셋은 갈래가 아니다):

1. **클라이언트별 명시 열거, 기본은 빈 집합.** 등록돼 있다는 이유로 권한을 받지 않는다.
2. **요청이 받은 scope 에 걸어 둔다.** 등록이 토큰을 정하는 것이 아니라 요청이 정한다.
3. **워크로드는 여전히 신원이 아니다.** `sub` 는 계정이 아니라 클라이언트다.

### 🔴🔴 그리고 기안 중에 **네 번째 모양**이 나왔다 — 전제가 하나 깨졌다

`TASK-MONO-721` AC-0 의 세 갈래(ⓐ 네트워크 · ⓑ 게이트웨이 규칙 · ⓒ 자격의 테넌트 교체)는
**«토큰의 테넌트는 발급 시점에 고정된다»** 는 공유 전제 위에 서 있었다. 그런데 이 저장소에는
그 전제를 깨는 기전이 **이미 구현돼 있다**:

```
AssumeTenantAuthenticationProvider  (RFC 8693 token-exchange, ADR-MONO-020)
   subject_token 검증 → OperatorAssignmentPort.resolveAssignment (fail-closed)
                      → tenant_id = 선택된 테넌트 로 재발급
V0020__add_token_exchange_grant_to_platform_console.sql  (grant 는 클라이언트별)
```

다만 지금은 **운영자 신원 전용**이다 — 게이트가 admin-service 의 **배정 조회**이고, 워크로드에는
계정도 배정도 없다. 🔵 **그래서 「이미 있으니 그냥 쓰면 된다」가 아니다**; 갈래 D 는 그 기전에
**워크로드 분기와 그 분기의 게이트를 새로 만드는 것**이고, 그 비용은 아래 표에 적는다.

🔴 이 발견은 소유자가 ⓑ 를 고른 **뒤에** 나왔다. 그러므로 이 ADR 은 ⓑ 를 「게이트웨이를 고친다」로
좁게 실행하지 않고, ⓑ 가 실제로 고른 것 — **«규칙은 인가 평면에 둔다»**(네트워크도 아니고,
테넌트마다 자격을 늘리는 것도 아니다) — 을 놓고 그 규칙이 **어디에 사는가**를 묻는다.

---

## Decision

> 🔴 **아직 결정되지 않았다.** 아래 D1~D6 은 갈래 **D** 가 ACCEPT 될 경우의 결정문이다.
> 다른 갈래가 선택되면 이 절은 그 갈래로 다시 쓰인다(ACCEPT 는 *finalise* 이지 *re-decide* 가 아니므로,
> 갈래가 바뀌면 그것은 새 제안이다).

### D1 — 워크로드는 **교환으로** 대상 테넌트를 얻는다. 두 판정기는 한 줄도 안 바뀐다

`product-service` 는 ① 자기 자격으로 `client_credentials` 토큰을 받고
② 그 토큰을 **subject_token** 으로 `urn:ietf:params:oauth:grant-type:token-exchange` 를 호출해
`tenant_id = <대상 테넌트>` 인 **단명 토큰**을 받는다. 프로비저닝 호출은 ②의 토큰으로 한다.

⇒ 게이트웨이의 `pathTenantId.equals(tenantId)` 도, `TenantScopeGuard` 도 **그대로 참**이 된다.
`tenant_id` 는 **유일한 테넌트 축**으로 남는다.

### D2 — 게이트는 **워크로드 테넌트 카탈로그**다 (클라이언트별 명시, 기본 없음)

`WorkloadRoleCatalog` 옆에 형제 카탈로그를 둔다: `client_id → 허용 테넌트 집합`.

- **열거되지 않은 클라이언트는 어떤 테넌트도 assume 할 수 없다**(오늘의 동작과 **정확히 동일**).
- **빈 집합은 «쟀고, 답이 없음»** 이고 **부재는 «아무도 안 봤다»** 다 — `ADR-MONO-061` 이 세운 구별을 따른다.
- 운영자 경로의 게이트(`OperatorAssignmentPort`)는 **건드리지 않는다.** 워크로드 분기는 그 포트를
  **부르지 않는다**(워크로드에는 배정이 없다).

### D3 — 교환 grant 도 클라이언트별로 **명시 부여**한다

`product-service-client` 의 `authorization_grant_types` 에 `token-exchange` 를 더하는 **새 마이그레이션**
(`V0037`)을 낸다. 🔴 **`V0036` 을 고치지 않는다** — 적용된 마이그레이션의 바이트 변경은 D5 가 금지한다.

### D4 — 🔴 **교환 토큰은 신원이 아니다.** 운영자 분기의 파생을 타지 않는다

assume-tenant 의 운영자 분기는 선택된 테넌트의 entitled domains 에서 `roles` 를 파생하고
`entitled_domains` 를 싣는다. 워크로드 분기는 **그 둘을 하지 않는다** — `email` 없음, `roles` 는
`ADR-MONO-061` 의 카탈로그가 정한 것만, `sub` 는 계정이 아니라 클라이언트.
🔴 워크로드 교환 토큰이 **운영자 토큰처럼 보이면** 그것이 이 결정의 가장 비싼 실패다.

### D5 — 🔴🔴 `V0019` 의 낡은 문장은 **그 파일을 고쳐서 정정하지 않는다**

`TASK-MONO-721` AC-3 은 `V0019` 헤더의
*"receiving resource servers … do NOT pin tenant … informational here"* 를 고치라고 한다.
그 문장이 **거짓인 것은 맞다**(위 § Context). 그러나 **그 파일의 바이트를 고치면 안 된다**:

- `V0019` 는 **이미 적용된** 마이그레이션이고, Flyway 체크섬은 **주석을 포함한 파일 전체**다.
- auth-service 는 `validateOnMigrate` 기본값(참) 아래 돈다(`application.yml` 에 끄는 설정이 없다).
- ⇒ 바이트가 바뀌면 **기존 볼륨을 가진 DB 는 기동에서 죽는다.** 데모 인스턴스의 볼륨은
  stop/start 를 건너 살아남으므로 **정확히 그 대상**이다.
- 🔴 **CI 는 이것을 영원히 못 잡는다** — CI 와 `docker compose up` 은 언제나 **빈 볼륨**에서 시작한다.
  이 저장소는 같은 부류를 이미 한 번 밟고 문서로 남겼다:
  [`projects/iam-platform/docs/flyway-dev-seed-migrations.md`](../../projects/iam-platform/docs/flyway-dev-seed-migrations.md)
  § 1~2 (*"a dev-only seed broke production-shaped startup for every developer with an existing
  volume, and **nothing in CI could ever have caught it**"*).

**그래서 정정은 이렇게 한다** — 낡은 문장이 **구속력을 갖는 자리**를 고친다:

1. `platform/contracts/jwt-standard-claims.md` — 워크로드 토큰의 **테넌트 축**을 이 ADR 에 맞게 개정한다
   (계약이 구현보다 먼저다 — `ADR-MONO-061` D2 가 role 축에 대해 한 것과 같은 순서).
2. **새 마이그레이션 `V0037` 의 헤더**에 정정을 싣는다 — 다음 사람이 `V0019` 를 읽을 때 옆 파일에서
   반증을 만난다.
3. `V0019` 자체의 바이트 정정은 **신선 볼륨을 얻는 날**로 미루고, 그 의무는 **`TASK-MONO-722`**
   (`tasks/ready/`)가 자기 티켓으로 들고 있는다 — AC-0 이 **먼저 재고 그 다음에 행동하는** 게이트다.
   🔴🔴 **정정 (2026-09-23, 발행 당일)**: 이 줄의 첫 판은 *"`TASK-MONO-672` 가 항목으로 들고 있는다"*
   였고 **두 번 틀렸다.** ① 672 는 *"«닫히면서 집을 잃는» 의무만 받는다"* 이고 `TASK-MONO-721` 은
   **살아 있다** ⇒ 672 의 **Failure Scenario 2**(«살아 있는 티켓의 ⚪ 를 여기로 옮긴다 → 그 티켓이
   자기 의무를 잃는다») 그 자체였다. ② 672 는 **«스택이 떠야만 잴 수 있는 측정»** 의 집인데,
   이 의무는 측정이 아니라 **조건에 묶인 변경**이다 — 창을 열어도 잴 것이 없다.
   🔵 집을 **두 번** 잘못 골랐고, 두 번 다 「비슷해 보이는 집」이었다.

🔵 **D5 는 갈래와 무관하게 참이다** — A·B·C 를 골라도 `V0019` 를 고치면 같은 방식으로 죽는다.

### D6 — 검증은 **«안 되어야 할 것이 안 되나»** 로 한다

`TASK-MONO-721` AC-1 의 대조군이 이 결정의 **본체**다: 같은 자격으로 **`wms`** 를 assume 하려 하면
**거절**되어야 한다. 🔴 「전부 허용」이 되는 것이 이 변경의 가장 비싼 실패이고,
성공 경로만 보는 테스트는 그것을 **구조적으로** 못 본다.

---

## Alternatives Considered

| 갈래 | 규칙이 사는 곳 | 바뀌는 판정기 | 대가 |
|---|---|---|---|
| **A. 새 클레임** | auth-service 가 `provision_tenants` 같은 **두 번째 테넌트 클레임**을 싣는다 | 게이트웨이 **+** `TenantScopeGuard` **둘 다** | 🔴 `tenant_id` 옆에 **두 번째 테넌트 축**이 생긴다. 계약은 `tenant_id` 를 *"the tenant-isolation axis"* 라고 못 박고 있고, 같은 축을 두 키로 나눠 두면 **드리프트한다**(계약 자신이 `aud`-behind-the-edge 에서 쓴 논거). 🔴 앞으로 생길 모든 `/internal/tenants/**` 수신자가 이 클레임을 **배워야** 한다 |
| **B. 게이트웨이 설정** | 게이트웨이의 `client_id → tenants` 맵 | 게이트웨이 | 🔴 account-service 의 2선 방어가 **여전히 거절**한다 ⇒ ① 게이트웨이가 `X-Tenant-Id` 를 경로 테넌트로 **다시 써서** 2선 방어를 장식으로 만들거나 ② 같은 맵을 account-service 에 **복제**해야 한다(프로젝트 스코프 `libs/` 모듈 + 그 자체로 또 하나의 ADR). 둘 다 나쁘다 |
| **C. 수신 측 카탈로그** | account-service 가 «누가 내 테넌트에 쓸 수 있나» 를 정한다 | 게이트웨이(워크로드에 한해 핀 해제) **+** `TenantScopeGuard` | 🔵 «자원 소유자가 정한다» 는 논거는 **정당하다**. 🔴 그러나 게이트웨이의 균일한 규칙에 **토큰 종류로 키를 갖는 구멍**이 생기고, `/internal/tenants/**` 를 가진 **모든** 서비스가 같은 카탈로그를 반복해야 한다 |
| **✅ D. 교환을 워크로드로 확장 (추천)** | auth-service — **발급 시점** | **없음** (둘 다 그대로 참이 된다) | 🔴 호출자가 **두 단계**가 된다(토큰 → 교환 → 호출). 🔴 `AssumeTenantAuthenticationProvider` 에 워크로드 분기와 그 게이트를 새로 만들어야 하고, D4 를 어기면 운영자 분기의 파생이 **새어 들어온다** |

### 왜 **D** 인가

1. **판정기를 하나도 안 바꾼다.** A·B·C 는 전부 게이트웨이나 가드(또는 둘 다)를 고친다.
   이 표면의 규칙은 *"tenant-scoping 이 곧 인가"* 이므로, 그 규칙을 **느슨하게 하지 않고**
   요구를 만족시키는 갈래가 하나라도 있으면 그쪽이 낫다.
2. **축이 하나로 남는다.** `tenant_id` 가 계속 유일한 테넌트 축이다 — A 의 드리프트 비용이 0 이 된다.
3. **권한이 한 곳에서, 발급 시점에 결정된다.** 발급자가 유일한 권위이고 카탈로그도 하나다.
   B 의 복제 문제도, C 의 반복 문제도 생기지 않는다.
4. **최소 권한이 토큰 단위로 유지된다.** 기본 워크로드 토큰으로는 `ecommerce` 를 **못 건드린다**;
   단명 교환 토큰만 건드린다. 061 의 제약 2(«등록이 아니라 요청이 정한다»)와 같은 성질이다.
5. **기전이 이미 있고 ADR 로 뒷받침된다**(`ADR-MONO-020`) — 새 개념을 발명하지 않는다.

### 기각한 것 — **`tenant_id: *` 와일드카드**

계약은 이미 `*` 를 *"the SUPER_ADMIN platform-scope wildcard, admitted only by gateways that opt in"*
으로 갖고 있다. `product-service-client` 에 `*` 를 주면 **오늘 당장 통과한다.**
🔴 **그리고 그것이 `TASK-MONO-721` § Failure Scenarios 1 그 자체다** — 한 도메인의 워크로드가
**모든 테넌트**에 계정을 만들 수 있게 된다. 싸고, 즉시 작동하고, 되돌리기 어렵다. 기각한다.
🔵 이 줄을 남기는 이유: 다음 사람이 같은 발견을 하고 «이미 있는 기능» 이라고 읽을 것이기 때문이다.

---

## 라이더 — 🔴 이것들은 **내 선택**이지 소유자 결정이 아니다

[`platform/architecture-decision-rule.md`](../../platform/architecture-decision-rule.md) § Riders 대로,
갈래만 고르는 plain ACCEPT 는 아래를 **확정하지도 기각하지도 않는다**. 뒤집으려면 이름으로 지목해야 한다.

| # | 라이더 | 뒤집으면 |
|---|---|---|
| **R1** | `product-service-client` 의 허용 테넌트 = **`["ecommerce", "demo-corp"]`** — `ecommerce` 는 실제 테넌트, `demo-corp` 는 **데모의 기본 테넌트**이고 `TASK-MONO-721` § Edge Cases 가 그 경로를 지목한다 | `["ecommerce"]` 만 두면 데모에서 셀러 등록이 계속 실패한다 |
| **R2** | 교환 요청에 `internal.invoke` **scope 가 실려 있을 때만** 허용한다(061 제약 2) | 등록이 토큰을 정하게 된다 |
| **R3** | 교환 토큰의 수명은 운영자 것과 같은 **단명 · refresh 없음** | 장수 토큰은 회수 지연을 만든다 |
| **R4** | 카탈로그는 **auth-service 안**에 둔다(`WorkloadRoleCatalog` 형제). 공유 `libs/` 로 올리지 않는다 | 올리면 그 자체로 또 하나의 ADR 이다(CLAUDE.md § 프로젝트 스코프 모듈) |
| **R5** | `WorkloadRoleCatalogTest` 의 **인구조사 칸**(17/11/6)은 **건드리지 않는다** — 이 ADR 은 role 축을 안 바꾼다 | 바꾸면 061 의 fail-closed 기본값을 잰 계측기가 흔들린다 |
| **R6** | 실패 응답은 `invalid_grant`(교환 거절) — 오늘의 `403 TENANT_SCOPE_DENIED` 와 **다른 자리**에서 난다 | 관측 경로가 바뀌므로 AC-1 대조군의 기대값도 바뀐다 |

---

## Consequences

### ACCEPT 가 인가하는 것

`TASK-MONO-721` 의 AC-1·AC-2 를 D1~D6 의 범위에서 구현하는 것. 그 밖의 테넌트 부여,
다른 워크로드 클라이언트로의 확장, 운영자 경로의 변경은 **인가하지 않는다**(각각 별도 task — HARDSTOP-09).

### 순서 — 계약이 먼저다

1. `platform/contracts/jwt-standard-claims.md` 개정 (D5-1). 🔴 **구현보다 먼저** —
   `ADR-MONO-061` D2 가 role 축에 대해 세운 순서이고, `scripts/check-jwt-claims-registry.sh`
   (CI 잡 `JWT claims registry`)가 발행 클레임과 이 문서를 대조한다.
2. auth-service: 워크로드 분기 + 카탈로그 + `V0037`.
3. `product-service`: 두 단계 토큰 획득.
4. `docker-compose.yml` / `infra/demo/demo.env`: `ACCOUNT_SERVICE_BASE_URL` 은 **게이트웨이** 그대로
   (D1 이 게이트웨이 경유를 성립시키므로 `TASK-MONO-717` 의 그 배선은 **되돌릴 필요가 없다**).

### 건드리지 않는 것

- 게이트웨이 `JwtAuthenticationFilter` — **한 줄도**.
- account-service `TenantScopeGuard` — **한 줄도**.
- 운영자 assume-tenant 경로와 `OperatorAssignmentPort` 게이트.
- `ADR-MONO-061` 의 role 카탈로그와 그 인구조사(R5).

### 새로 생기는 위험

- 🔴 **워크로드 분기가 운영자 분기의 파생을 타면**(D4 위반) 워크로드 토큰이 `entitled_domains` 와
  파생 `roles` 를 갖게 된다 — 이 결정의 가장 비싼 실패. 구현은 그것을 **무는 칸**을 먼저 둔다.
- 🔴 교환 한 번이 왕복 1회를 더한다. 셀러 등록은 드문 호출이므로 캐시는 **일부러 하지 않는다**
  (캐시가 생기면 회수 지연이 R3 의 단명 설계를 무효화한다).

---

## Verification

| 무엇 | 어떻게 | 어디 |
|---|---|---|
| 성공 경로 | 셀러 등록 → `account_db` 에 행이 **생긴다** | `TASK-MONO-721` AC-1 |
| 🔴 **대조군(본체)** | 같은 자격으로 **`wms`** assume → **거절** | AC-1 |
| bite | 카탈로그에서 그 테넌트를 빼면 **빨개지나** | AC-2 |
| D4 | 워크로드 교환 토큰에 `email`·`entitled_domains`·파생 `roles` 가 **없다** | AC-2 |
| 🔴 창 판정 | 로컬 초록으로 닫지 않는다 | AC-4 → `TASK-MONO-672` |

🔴🔴 **마지막 줄이 이 ADR 의 검증 규율이다.** `TASK-MONO-718`(단위 초록 → 창 FAIL)과
`TASK-MONO-717`(로컬 초록 → 창 FAIL)이 **연속 두 번** 같은 방식으로 틀렸다.

---

## Outstanding follow-ups

- 🔴 **`V0019` 헤더의 바이트 정정** — 신선 볼륨을 얻는 날까지 보류(D5-3). **`TASK-MONO-722`** 가 자기 티켓으로 든다(`tasks/ready/`, AC-0 = 먼저 재고 그 다음에 행동하는 게이트). 🔵 첫 판은 `TASK-MONO-672` 를 지목했고 그것이 틀렸다 — D5-3 의 정정을 보라.
- **`POST /internal/accounts/{a}/lock` 의 게이트웨이 라우트 부재** — 어느 갈래든 이 호출만 404 로 남는다
  (`TASK-MONO-713` ⓑ 소관, 별건).
- **다른 워크로드 클라이언트의 테넌트 부여** — 이 ADR 은 `product-service-client` 하나만 다룬다.
  카탈로그의 나머지 항목은 **부재**(= «아무도 안 봤다»)로 남으며, 그것이 fail-closed 기본값이다.

---

## History

- **2026-09-23 — PROPOSED** (PR [#3961](https://github.com/kanggle/monorepo-lab/pull/3961), squash `740b080ba`).
  주관 `TASK-MONO-721` AC-0 이 갈래 **ⓑ** 로 닫히면서 요구한 ADR. 소유자 결정은 **아직 없다.**
- **2026-09-23 — D5-3 정정 (같은 날, PROPOSED 상태)**: 보류된 `V0019` 바이트 정정의 소유자를
  `TASK-MONO-672` 에서 **`TASK-MONO-722`** 로 옮겼다. 🔴 672 는 *"«닫히면서 집을 잃는» 의무만
  받는다"* 이고 `TASK-MONO-721` 은 살아 있으므로 첫 판은 672 의 **Failure Scenario 2** 였고,
  게다가 672 는 **측정**의 집인데 이 의무는 측정이 아니다. § Decision 의 D1~D4·D6 과
  § Alternatives 는 **한 바이트도 안 바꿨다.**
  🔵 **PROPOSED 이므로 고칠 수 있다** — ACCEPT 는 *finalise* 이지 *re-decide* 가 아니고,
  ACCEPT 뒤였다면 이 줄은 정정이 아니라 **새 제안**이어야 했다.
