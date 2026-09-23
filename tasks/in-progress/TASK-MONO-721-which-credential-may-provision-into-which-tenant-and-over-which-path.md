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

---

# 진행 기록 (2026-09-23 UTC)

## ✅ AC-0 — 소유자 결정 **ⓑ**

소유자 응답: *"추천대로 진행"* (2026-09-23). 내 추천은 갈래 **ⓑ** 하나였고 다른 후보를 제시하지
않았으므로 지시 대상이 유일하다. 🔵 **그 말이 무엇을 골랐는지는 적어 둔다** — ⓑ 는
「게이트웨이 파일을 고친다」가 아니라 **«규칙을 인가 평면에 둔다»** 를 고른 것이다
(ⓐ 의 네트워크도 아니고, ⓒ 의 테넌트마다 늘어나는 자격도 아니다).

🔴 **그리고 이것은 ADR 을 ACCEPT 하지 않는다.**
[`platform/architecture-decision-rule.md`](../../platform/architecture-decision-rule.md)
§ The ACCEPTED Gate 가 *"A bare 「진행」/「proceed」… does **NOT** accept an ADR, even when it replies
directly to the message that proposed it"* 라고 못 박는다. AC-1·AC-2 는 **PAUSE**.

## 🔴🔴 기안 중에 **AC-0 표가 몰랐던 네 번째 모양**이 나왔다

세 갈래(ⓐ·ⓑ·ⓒ)는 **«토큰의 테넌트는 발급 시점에 고정된다»** 는 **공유 전제** 위에 있었다.
그 전제를 깨는 기전이 이 저장소에 **이미 구현돼 있다**:

```
AssumeTenantAuthenticationProvider          RFC 8693 token-exchange (ADR-MONO-020)
AssumeTenantAuthenticationConverter          — subject_token 검증 → 게이트 → tenant_id 재발급
V0020__add_token_exchange_grant_to_platform_console.sql   — grant 는 클라이언트별 부여
```

🔵 **다만 「이미 있으니 그냥 쓰면 된다」가 아니다.** 지금은 **운영자 신원 전용**이다 — 게이트가
`OperatorAssignmentPort.resolveAssignment`(admin-service 의 **배정 조회**, fail-closed)이고
워크로드에는 계정도 배정도 없다. 갈래 D 는 그 기전에 **워크로드 분기와 그 분기의 게이트를
새로 만드는 것**이고, 그 비용은 ADR 의 표에 적었다.

⇒ [`ADR-MONO-076`](../../docs/adr/ADR-MONO-076-which-workload-credential-may-act-on-which-tenant.md)
을 **PROPOSED** 로 기안했다. 갈래 **A**(새 클레임) · **B**(게이트웨이 설정) ·
**C**(수신 측 카탈로그) · **D**(교환을 워크로드로 확장, **추천**).

🔵 **D 를 추천하는 한 문장**: A·B·C 는 전부 판정기(게이트웨이 / `TenantScopeGuard` / 둘 다)를
고치는데, 이 표면의 규칙은 *"tenant-scoping 이 곧 인가"* 이므로 **그 규칙을 느슨하게 하지 않고**
요구를 만족시키는 갈래가 있으면 그쪽이 낫다. D 는 두 판정기를 **한 줄도** 안 건드린다.

## 🔴 기각한 것 — `tenant_id: *` 와일드카드 (다음 사람이 반드시 다시 발견한다)

계약에 이미 있다: *"`*` is the SUPER_ADMIN platform-scope wildcard, admitted only by gateways that
opt in"*. `product-service-client` 에 `*` 를 주면 **오늘 당장 통과한다.**
🔴 그리고 그것이 이 티켓 § Failure Scenarios **1 번 그 자체**다. 싸고, 즉시 작동하고,
되돌리기 어렵다. ADR § Alternatives 에 기각 사유를 남겼다.

## 🔴🔴 AC-3 은 **지금 그대로 하면 안 된다** — 실측으로 확인

AC-3 은 `V0019` 헤더의 낡은 문장을 고치라고 한다. **문장이 거짓인 것은 맞다.**
그러나 **그 파일의 바이트를 고치면 기존 볼륨을 가진 DB 가 기동에서 죽는다**:

| 잰 것 | 값 |
|---|---|
| `V0019` 가 이미 적용된 마이그레이션인가 | ✅ (auth-service `db/migration`) |
| Flyway 체크섬이 **주석**을 포함하는가 | ✅ 파일 전체 |
| auth-service 가 `validateOnMigrate` 를 끄는가 | 🔴 **안 끈다** — `application.yml`·`application-e2e.yml` 어디에도 없다 ⇒ 기본값(참) |
| 데모 인스턴스의 볼륨이 stop/start 를 건너 사는가 | ✅ (15차 창에서 실측) |
| CI 가 이것을 잡을 수 있는가 | 🔴 **영원히 못 잡는다** — CI 는 언제나 **빈 볼륨** |

🔵 **이 저장소는 같은 부류를 이미 한 번 밟고 문서로 남겼다** —
[`projects/iam-platform/docs/flyway-dev-seed-migrations.md`](../../projects/iam-platform/docs/flyway-dev-seed-migrations.md)
§ 1~2: *"a dev-only seed broke production-shaped startup for every developer with an existing
volume, and **nothing in CI could ever have caught it**."*

⇒ ADR **D5** 가 대안을 정한다: 정정은 **계약 문서**와 **새 `V0037` 헤더**로 가고,
`V0019` 의 바이트 수정은 **신선 볼륨 게이트**에 묶어 `TASK-MONO-672` 가 항목으로 든다.
🔵 **D5 는 갈래와 무관하게 참이다** — A·B·C 를 골라도 같은 방식으로 죽는다.

## ⏸️ 지금 막혀 있는 것

| AC | 상태 |
|---|---|
| AC-0 | ✅ **ⓑ** 확정 |
| AC-1 · AC-2 | ⏸️ `ADR-MONO-076` **ACCEPT 대기** (정확형 = ADR 이름 + `ACCEPTED` + **갈래 letter**) |
| AC-3 | 🔴 **재정의됨** — ADR D5 로 이관(바이트 수정 금지, 신선 볼륨 게이트) |
| AC-4 | ⏸️ 구현 뒤 `TASK-MONO-672` 로 |

🔴 **이 PR 은 코드를 한 줄도 바꾸지 않는다.** ADR ACCEPT 전 구현은 HARDSTOP-09 다.
