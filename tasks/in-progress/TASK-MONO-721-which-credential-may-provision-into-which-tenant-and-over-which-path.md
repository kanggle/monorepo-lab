# Task ID

TASK-MONO-721

# Title

🔴 **어느 자격이 어느 테넌트에 프로비저닝할 수 있고, 어느 경로로 가는가** — 게이트웨이는 403, 직접 경로는 네트워크가 없다

# Status

in-progress (2026-09-23 UTC — AC-0 소유자 결정 **ⓑ** · 🔴 구현은 `ADR-MONO-076` ACCEPT 까지 PAUSE)

# Owner

미지정

# Task Tags

- iam
- ecommerce
- security
- architecture

---

> **분석 모델:** Opus 5 / **구현 권장:** Opus 5 — 세 갈래 전부 «누가 어느 테넌트에 쓸 수 있는가» 를
> 건드린다. 배선이 아니다.

---

# 배경 — 이것은 추론이 아니라 **2026-09-23 창 실측**이다

`TASK-MONO-717`(소유자 결정 ⓐ)이 `product-service-client` 를 등록하고 주소를 배선했다.
그 AC-1 판정을 `TASK-MONO-672` 항목 12 가 창에서 받았고, **FAIL** 했다.

## 🟢 고쳐진 것 (이 티켓이 되돌릴 것이 아니다)

```
등록 전  →  401 {"error":"invalid_client"}
등록 후  →  200
scope 생략            →  토큰의 scope 클레임 **비어 있음**
scope=internal.invoke →  "scope":["internal.invoke"]
```

⇒ 등록은 **필요했고**, 스코프 명시 요청도 **필요했다**(「생략하면 서버가 등록 스코프를 전부
준다」는 이 서버에서 **거짓**이다). 🔵 두 변경은 유효하다.

## 🔴 그런데 호출은 **두 경로 모두** 실패한다

```
게이트웨이 경유 POST /internal/tenants/ecommerce/identities:resolveOrCreate
    (스코프를 실은 토큰으로도)  →  403 TENANT_SCOPE_DENIED
                                   "path tenantId does not match token claim"
product-service → account-service 직접   →  000 (연결 불가)
```

**기전**:

| 마디 | 무엇을 하나 |
|---|---|
| `gateway-service` `JwtAuthenticationFilter` | 위조 `X-Tenant-Id` 를 **제거하고**, 검증된 토큰의 `tenant_id` 로 **주입**한다 |
| account-service `TenantScopeGuard` | 그 헤더가 **있으면** 경로의 `{tenantId}` 와 같아야 한다. **없으면 건너뛴다** — *"the gateway's mTLS / shared-token layer is trusted"* |
| 이 자격 | `tenant_id = global-account-platform`(V0019 모양) |
| 경로 | `/internal/tenants/**ecommerce**/…` |

⇒ 게이트웨이를 지나는 한 **영원히 불일치**다. 그리고 직접 경로는 네트워크가 없다:

```
ecommerce-product-service  →  ecommerce_ecommerce-net
iam-account-service-1      →  iam_iam-e2e · traefik-net        (공유 네트워크 0)
```

## 🔵 그리고 V0019 의 헤더 문장이 낡았다

> *"The receiving resource servers (account/security) validate signature + issuer only and do
> NOT pin tenant (they serve all tenants), so the tenant claim is informational here."*

게이트웨이+가드 쌍이 **핀한다**(게이트웨이 경유에 한해). `TASK-MONO-717` 은 V0036 의 테넌트를
고를 때 그 문장을 인용했고, 그 인용이 FAIL 의 절반이다.

---

# Goal

`/internal/tenants/{tenantId}/**` 를 **어떤 자격이 어느 경로로** 부를 수 있는지를 정하고,
셀러 프로비저닝이 그 규칙 안에서 성립하게 한다.

---

# Scope

## 포함

- 세 갈래 중 하나의 선택(AC-0)과 그 구현.
- 🔴 `V0019` 헤더의 **낡은 문장 정정** — 어느 갈래를 고르든 그 문장은 지금 거짓이다.

## 제외

- `TASK-MONO-717` 이 한 **등록**과 **스코프 명시 요청** 을 되돌리는 것. 🔵 둘 다 실측으로
  필요했다(위 § 고쳐진 것).
- fail-soft 정책(`ADR-MONO-042` D3).
- `POST /internal/accounts/{a}/lock` 의 라우트 부재 — `TASK-MONO-713` ⓑ 소관(별건).

---

# Acceptance Criteria

## AC-0 — 🔴 갈래를 고른다 (소유자 결정 · **보안 표면**이다)

| 갈래 | 무엇을 한다 | 대가 |
|---|---|---|
| **ⓐ 직접 경로를 연다** | ecommerce 와 account-service 를 **공유 네트워크**에 두고 `ACCOUNT_SERVICE_BASE_URL` 을 `http://iam-account-service-1:8082` 로 | 🔴 **게이트웨이를 우회한다.** 가드의 «헤더 없으면 건너뛴다» 는 *"게이트웨이 계층을 신뢰한다"* 를 근거로 한 것이고, 다른 프로젝트 네트워크에서의 우회는 그 근거를 바꾼다 |
| **ⓑ 게이트웨이가 워크로드 토큰을 다르게 본다** | 워크로드(`client_credentials`) 토큰에는 경로 테넌트를 허용 | 🔴 «어느 워크로드가 어느 테넌트에 쓸 수 있는가» 를 **게이트웨이에 새로 정의**해야 한다 ⇒ **ADR** |
| **ⓒ 자격의 테넌트를 바꾼다** | `product-service-client` 를 `tenant_id=ecommerce` 로 | 🔴 셀러는 **어느 테넌트에서도** 등록될 수 있다 ⇒ 테넌트마다 자격이 필요해지고 명단이 테넌트 수만큼 자란다 |

- [ ] 🔵 **추천은 ⓐ 가 아니다.** 가장 싸 보이지만 신뢰 경계를 조용히 옮긴다.
      🔵 **ⓑ 가 문제의 모양에 맞다** — 「플랫폼 워크로드가 대상 테넌트를 **경로로** 지정한다」는
      이 API 의 설계 그대로이고, 빠진 것은 **그것을 허가하는 규칙**뿐이다.
      🔴 다만 ⓑ 는 ADR 이 필요하다. **내 추천이지 소유자 선택이 아니다.**

## AC-1 — 🔴 «되나» 가 아니라 «**안 되어야 할 것이 안 되나**» 로 잰다

- [ ] 성공 경로: 셀러 등록 → `account_db` 에 셀러-운영자 계정 **행이 생긴다**.
- [ ] 🔴 **대조군(필수)**: 같은 자격으로 **다른 테넌트**(예: `wms`)에 프로비저닝을 시도하면
      **거절되는가**. 갈래 ⓑ 를 고르면 이 칸이 그 티켓의 전부다 — 허용 규칙을 넣으면서
      «전부 허용» 이 되는 것이 이 변경의 가장 값비싼 실패다.

## AC-2 — bite

- [ ] 그 허용 규칙을 지우면(또는 네트워크를 떼면) **빨개지는가**.

## AC-3 — 🔴 `V0019` 헤더 정정

- [ ] *"do NOT pin tenant … informational here"* 문장을 실측에 맞게 고친다. 🔵 그 문장이
      `TASK-MONO-717` 을 틀린 선택으로 이끌었다 — 고치지 않으면 다음 사람도 같은 인용을 한다.

## AC-4 — 창 판정

- [ ] 🔴 단위·통합 초록으로 닫지 마라. `TASK-MONO-672` 로 항목을 넘긴다 —
      `TASK-MONO-718`(단위 초록 → 창 FAIL)과 `TASK-MONO-717`(로컬 초록 → 창 FAIL)이
      **연속 두 번** 그것을 보여 줬다.

---

# Related Specs / Contracts

- `projects/iam-platform/apps/gateway-service/.../filter/JwtAuthenticationFilter.java`
- `projects/iam-platform/apps/account-service/.../presentation/internal/TenantScopeGuard.java`
- `projects/iam-platform/apps/auth-service/.../db/migration/V0019__seed_internal_service_workload_clients.sql` (§ Tenant — **낡은 문장**)
- `tasks/review/TASK-MONO-717-…` § CORRECTION (이 티켓을 만든 실측)
- `ADR-MONO-042` D2/D4/D5 · `ADR-MONO-061`

---

# Edge Cases

- **셀러가 `demo-corp` 에서 등록되는 경우** — 데모의 기본 테넌트다. 경로 테넌트는 그때 `demo-corp` 다.
- **`lock` 호출** — 게이트웨이에 라우트가 없어 어느 갈래든 이 호출만 404 로 남는다(713 ⓑ).
- **로컬(`DEMO_DOMAIN=local`)** — 갈래 ⓐ 를 고르면 로컬 compose 에도 같은 네트워크가 필요하다.

---

# Failure Scenarios

1. **ⓑ 를 고르고 「워크로드면 전부 허용」으로 구현한다** → 한 도메인의 워크로드가 **모든 테넌트**에
   계정을 만들 수 있게 된다. AC-1 의 대조군이 이것을 문다.
2. **ⓐ 를 고르고 신뢰 경계 변경을 기록하지 않는다** → 다음 감사가 «게이트웨이가 모든 내부 호출을
   본다» 를 전제로 읽는다.
3. **V0019 헤더를 안 고친다** → 다음 사람이 같은 문장을 인용해 같은 선택을 한다(717 이 그랬다).
