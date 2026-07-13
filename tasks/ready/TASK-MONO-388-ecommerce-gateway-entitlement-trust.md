# Task ID

TASK-MONO-388

# Title

ecommerce 게이트웨이를 **entitlement-trust 로 이행** — `acceptAnyWellFormedTenant` 는 제거가 아니라 **교체**다 (그리고 교체하면 *"게이트를 여는 함대 유일의 스위치"* 가 라이브러리에서 사라진다)

# Status

ready

# Owner

monorepo

# Task Tags

- security
- tenant
- gateway
- ecommerce
- lib

---

# ⚠️ `TASK-MONO-386` 이 이 티켓을 "플래그 제거" 라 불렀다. 그건 틀렸다.

386 은 후속을 *"`acceptAnyWellFormedTenant` 제거"* 라 적었다. **그대로 하면 멀티테넌트 마켓플레이스가 깨진다.**

플래그를 그냥 지우면 게이트는 `tenant_id == 'ecommerce'` 만 통과시킨다 ⇒ **자기 스토어를 운영하는 고객 테넌트의 운영자**(`tenant_id=acme-corp`, `entitled_domains ∋ ecommerce`)가 **403** 이 된다. 그건 `ADR-MONO-030` D1-A 가 **결정한** 구조를 되돌리는 것이다:

> **D1-A (CHOSEN)** — *"ecommerce becomes the 6th **entitlement-trust** domain … ecommerce store-admin/seller services trust GAP/IAM `tenant_id` claims (entitlement-trust gate) + row-level isolation (M1-M7)."*

**즉 목표는 게이트를 좁히는 것이 아니라 `ADR-019 D5` 진화를 ecommerce 에 **드디어** 적용하는 것이다.**

---

# 실측 (2026-07-13) — ecommerce 는 함대에서 홀로 그 진화를 안 했다

```
ecommerce   .acceptAnyWellFormedTenant()                          ← 여는 스위치, 이것 하나뿐
erp         .allowSuperAdminWildcard()  .trustEntitledDomains()
fan         .allowSuperAdminWildcard()  .trustEntitledDomains()
finance     .allowSuperAdminWildcard()  .trustEntitledDomains()
scm         .allowSuperAdminWildcard()  .trustEntitledDomains()
```

`acceptAnyWellFormedTenant` 는 entitlement-trust 의 **대역(stand-in)** 이다. 진짜 게이트가 *"이 토큰의 tenant 가 **구독한** tenant 인가"* 를 묻는 자리에서, 대역은 *"tenant 가 **있기는** 한가"* 만 묻는다.

라이브러리 자신이 이 스위치를 이렇게 부른다 (`TenantGatePolicyLeakTest`):

> *"`acceptAnyWellFormedTenant` is the only switch in this library that **opens** a gate rather than narrowing one."*

---

# 교체 후 의미론 (행동이 바뀌는 지점을 정확히 적는다)

| 토큰 | 지금 | 교체 후 |
|---|---|---|
| 쇼핑객 — `tenant_id=ecommerce` (BE-507 이후 정상) | ✅ | ✅ `expectedTenantId` 일치 (첫 분기) |
| 고객 테넌트 운영자 — `tenant_id=acme-corp`, `entitled_domains ∋ ecommerce` | ✅ (아무 tenant 나 통과) | ✅ `trustEntitledDomains` |
| SUPER_ADMIN — `tenant_id=*` | ✅ (well-formed 이므로) | ✅ `allowSuperAdminWildcard` (**명시적으로**) |
| **미구독 tenant 의 토큰** (예: 떠도는 `fan-platform` 계정) | 🔴 **통과** | 🟢 **403** ← **BE-506 이 짚은 구멍이 닫힌다** |
| `tenant_id` 없음/공백 | 🟢 403 | 🟢 403 (불변) |

**행동이 바뀌는 칸은 정확히 한 줄이다** — 미구독 tenant 가 거부된다. 그것이 이 티켓의 전부이자 목적이다.

---

# 선행이 이미 충족됐다 (`TASK-MONO-386` AC-0, 2026-07-13 실측)

깨질 정당한 소비자가 **없다**. 현존하는 유일한 장수 인스턴스(로컬 `federation-hardening-e2e`, 44시간)에서:

- `accounts`: fan-platform **29** / acme-corp 1 / ecommerce 1 — **29개는 전부 에이전트 디버깅 프로브**(`be470fix-*`, `coldprobe-*`, `warm-*`, `curve-*`, `probe-*` …). 쇼핑객이 아니다.
- 웹스토어 클라이언트(`ecommerce-web-store-client-id`)로 로그인한 **서로 다른 사람 = 1명** = `ecommerce-operator@example.com` — **운영자이고 이미 `tenant=ecommerce`**.
- `social_identities` **0행**, `oauth_consent` **0행**.
- ecommerce `orders` 의 `user_id` 9개 중 **IAM 계정과 일치하는 것 0개**(전부 e2e 픽스처).

⇒ **오배정된 ecommerce 소비자 0명.** 386 은 D1=A(무행동)로 닫혔다.

---

# Scope

## In Scope

- `projects/ecommerce-microservices-platform/apps/gateway-service/.../OAuth2ResourceServerConfig.java` — `tenantGate()` 를 `.allowSuperAdminWildcard().trustEntitledDomains()` 로 교체 + Javadoc 재작성(현재 Javadoc 은 *"이 게이트는 tenant 를 물을 수 없다"* 고 주장하는데, 그건 entitlement-trust 를 안 했을 때만 참이다).
- **`acceptAnyWellFormedTenant` 를 `libs/java-security` 에서 삭제** — 교체 후 함대 소비자 **0**. 남겨두면 *"켜면 되는 스위치"* 로 남는다.
- `TenantGatePolicyLeakTest` — 지금은 *"wms·scm·fan 에서 OFF"* 를 단언한다. 스위치가 사라지면 이 테스트의 명제가 바뀐다: **"게이트를 여는 스위치는 라이브러리에 존재하지 않는다"** 를 단언하도록 재작성(빌더 API 표면에 그 이름이 없음 + ecommerce 가 다른 넷과 같은 스위치 집합을 쓴다).
- ecommerce `TenantClaimValidatorTest` — 위 표의 5행을 전부 단언(**특히 "미구독 tenant → 403"**; 그게 새로 생기는 행동이다).

## Out of Scope

- **소급 재배정/폐기** — `TASK-MONO-386` 이 D1=A 로 닫았다(대상 0).
- `RoleSeedPolicy` vacuity → `TASK-MONO-381`.
- ecommerce 도메인 데이터의 row-level `tenant_id` 확대(ADR-030 D2 의 나머지 슬라이스).

---

# Acceptance Criteria

- [ ] **AC-1 — 교체, 제거가 아님.** ecommerce `tenantGate()` 가 나머지 넷과 **같은 스위치 집합**(`allowSuperAdminWildcard` + `trustEntitledDomains`)을 쓴다. `acceptAnyWellFormedTenant` 는 **호출되지 않는다**.
- [ ] **AC-2 — 스위치를 라이브러리에서 삭제.** `grep -r acceptAnyWellFormedTenant` 가 **0건**(테스트·주석 포함). 소비자 0인 채 남겨두지 않는다.
- [ ] **AC-3 — 새 행동을 단언한다.** *"미구독 tenant 의 well-formed 토큰 → 403"* 을 ecommerce validator 테스트가 **직접** 단언한다. **이 단언이 없으면 이 티켓은 아무것도 증명하지 않는다** — 나머지 4행은 교체 전에도 통과했다.
- [ ] **AC-4 — 회귀 없음(양성 3행).** 쇼핑객(`tenant_id=ecommerce`) · 고객 테넌트 운영자(`entitled_domains ∋ ecommerce`) · SUPER_ADMIN(`*`) 이 **전부 통과**한다. **운영자 행이 가장 중요하다** — 순진한 "제거" 가 깨뜨리는 것이 바로 이 행이고, 그 행이 없으면 리뷰어가 제거와 교체를 구분할 수 없다.
- [ ] **AC-5 — mutation.** ① `trustEntitledDomains()` 제거 → **운영자 행 RED** ② `allowSuperAdminWildcard()` 제거 → **SUPER_ADMIN 행 RED** ③ 정상 트리 → GREEN(공허하지 않음). **적용 여부를 먼저 출력하고 결과를 읽는다**(이 저장소 5회 재발).
- [ ] **AC-6 — 라이브 왕복.** 로컬 fed-e2e 스택에서 web-store 쇼핑객 로그인 + 콘솔 운영자의 ecommerce 접근이 **둘 다** 동작한다(정적 테스트가 못 보는 배선을 본다).
- [ ] CI GREEN.

---

# Edge Cases

- **쇼핑객은 `entitled_domains` 를 갖지 않는다** — 소비자 평면(ADR-030 D4-A)이라 구독 개념이 없다. 그래도 통과하는 이유는 **첫 분기**(`expectedTenantId.equals(tenantId)`)에서 걸리기 때문이다. `trustEntitledDomains` 가 쇼핑객을 통과시키는 게 **아니다** — 이걸 헷갈리면 "쇼핑객에게 entitled_domains 를 발급하자" 는 잘못된 수정으로 간다.
- **`tenant_id` 부재 시 분기** — `TASK-MONO-383` § 1.9 가 실측했다: `trustEntitledDomains` 를 켜면 null 분기가 스위치를 참조한다. ecommerce 는 지금 그 스위치가 꺼져 있으므로 **이 티켓이 그 경로를 처음 켠다.** `AgreesWithTheDecoder` 가드가 이미 그 일치를 단언하고 있으니 그것을 재사용/확장한다.
- **`omni-corp` 다중 도메인 운영자** — 여러 도메인을 구독한 테넌트. ecommerce 가 그 목록에 있으면 통과해야 한다(`project_omni_all_domain_test_tenant`).

# Failure Scenarios

- **F1 — "제거" 로 착수** → 게이트가 `tenant_id=='ecommerce'` 만 통과 → **고객 테넌트 운영자 전원 403** → 콘솔의 ecommerce 도메인이 죽는다. **단위 테스트는 초록일 수 있다**(쇼핑객만 테스트하면). AC-4 의 운영자 행이 이걸 막는다.
- **F2 — 스위치를 라이브러리에 남김** → 소비자 0인 "열림 스위치" 가 남아 다음 사람이 켠다. `TenantGatePolicyLeakTest` 의 존재 이유가 *"열림 스위치가 어디에 없는지"* 를 말하는 것이었다 — **없애면 그 테스트가 지키던 명제가 참으로 강화된다.**
- **F3 — AC-3 없이 머지** → 교체 전에도 통과하던 단언만 남고, **새로 닫은 구멍을 아무도 증언하지 않는다.**

# Test Requirements

- ecommerce `TenantClaimValidatorTest` 5행 + `TenantGatePolicyLeakTest` 재작성.
- `./gradlew check` — `libs/` 를 건드리므로 통합 스위트 전체가 실제로 돈다.
- AC-6 라이브 왕복(로컬 fed-e2e).

# Definition of Done

- [ ] AC-1~6 + CI GREEN
- [ ] `grep -r acceptAnyWellFormedTenant` = 0
- [ ] `tasks/INDEX.md` done entry

---

# Dependency Markers

- **선행 (done)**: `TASK-BE-506`(반경 실측) · `TASK-BE-507`(소비자 tenant 해석) · **`TASK-MONO-386`**(D1=A — 깨질 소비자가 없음을 실측으로 확정. **이것 없이는 이 교체가 누구를 깨는지 알 수 없다**).
- **연관**: `TASK-MONO-383` § 1.9(null 분기 ↔ entitled 스위치 상호작용) · `TASK-MONO-381`(role 가드 vacuity, 독립 축).

# Related Specs

- `docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md` § D1-A — **ecommerce = 6번째 entitlement-trust 도메인**(이 티켓이 그 결정을 드디어 이행한다)
- `docs/adr/ADR-MONO-019-platform-console-customer-tenant-model.md` § D5 — entitlement-trust 진화
- `libs/java-security/src/main/java/com/example/security/oauth2/TenantClaimValidator.java` — 스위치 정의
- `libs/java-gateway/src/test/java/com/example/apigateway/security/TenantGatePolicyLeakTest.java` — *"여는 스위치는 하나뿐"* 을 이름 붙인 곳
- `projects/ecommerce-microservices-platform/apps/gateway-service/.../config/OAuth2ResourceServerConfig.java:81-85` — 유일한 사용처

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` — `tenant_id` · `entitled_domains`

---

# Provenance

발굴 2026-07-13, `TASK-MONO-386` 의 AC-2(*"플래그 제거 가능 여부를 확정해 후속으로 넘긴다"*)를 수행하다 **386 의 전제 자체가 틀렸음**을 발견했다.

386 은 이 플래그를 *"BE-506 이 짚은 구멍"* 으로만 서술했다. **절반만 참이다** — 구멍인 것은 맞지만, **동시에 ADR-030 이 결정한 멀티테넌트 마켓플레이스를 지탱하는 유일한 것**이기도 했다. 그래서 *제거*는 구멍을 닫으면서 **마켓플레이스도 함께 닫는다**. 옳은 동작은 **교체**이고, 교체는 ecommerce 가 4년 전 결정된 진화를 **함대에서 마지막으로** 따라잡는 일이다.

**이 저장소가 반복해 배우는 것의 또 다른 얼굴** — *"제거"* 는 작업처럼 들렸지만 사실은 **잘못된 작업**이었다. 386 을 문자 그대로 착수했다면 단위 테스트는 초록이고(쇼핑객만 봤을 테니) 콘솔의 ecommerce 도메인이 조용히 죽었을 것이다. **티켓의 후속 지시도 선행 문서의 숫자와 같다 — 출처가 아니라 가설이다.**

분석=Opus 4.8 / 구현 권장=**Opus**(스위치 하나를 바꾸는 일처럼 보이지만, **잘못 뭉개면 단위 테스트가 초록인 채로 고객 테넌트 운영자 전원이 403** 이 된다).
