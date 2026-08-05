# Task ID

TASK-BE-576

# Title

데모 운영자가 자기 테넌트(`demo-corp`)에만 assign 돼 있어 콘솔의 도메인 목록 화면이 전부 비어 있다 — 게이트웨이는 200 을 내므로 상태코드로는 보이지 않는다

# Status

ready

# Owner

backend

# Task Tags

- code
- infra
- demo

---

# 배경 — `TASK-MONO-506` 라이브 검증이 발견했다

면접관이 스토어프런트에서 **구매를 완주한 뒤 콘솔의 「주문」 탭을 열면 비어 있다.**
「상품」 「사용자」 「배송」 「정산」 도 마찬가지다. 데모의 서사가 정확히 그 지점에서 끊긴다.

## 근본 원인 — 두 테넌트가 만나지 않는다

| | 테넌트 |
|---|---|
| 스토어프런트가 쓰는 데이터 | `ecommerce` |
| 데모 운영자가 assume 할 수 있는 테넌트 | `demo-corp` **하나뿐** |

- 소비자 토큰의 `tenant_id` 는 `ecommerce` 로 **고정**이다 — ecommerce 게이트웨이가
  `required-tenant-id: ecommerce` 를 강제하므로 다른 값일 수 없다. 따라서 구매로 생기는
  모든 행(`orders` · `shippings` · `payments` · `user_profiles`)은 `tenant_id=ecommerce` 다.
- `operator_tenant_assignment` 실측 (admin_db, 2026-08-05):

  ```
  operator 4 → acme-corp
  operator 4 → globex-corp
  operator 5 → demo-corp        ← 데모 운영자, 이 한 줄이 전부
  ```

- `audience=ecommerce` 로 assume 을 시도하면:

  ```
  {"error":"invalid_grant","error_description":"operator is not assigned to the selected tenant"}
  ```

## 왜 지금까지 안 보였는가 — **이것이 이 티켓의 핵심**

`TASK-BE-571` 이 `demo-corp` 에 5개 도메인 구독을 심었고, 그 덕분에 assume 토큰은
`entitled_domains=[ecommerce, erp, finance, scm, wms]` 와 5개 `*_OPERATOR` 역할을 들고 나온다.
각 도메인 게이트웨이의 `TenantClaimValidator...trustEntitledDomains()` 가 이 토큰을 **정상적으로
받아들인다.** 실측:

```
GET /api/admin/products            200   {"content":[]}     ← DB 에는 상품 8개
GET /api/admin/orders              200   {"content":[]}     ← DB 에는 주문 4개
GET /api/admin/users               200   {"content":[]}
GET /api/shippings                 200   {"content":[]}     ← DB 에는 배송 3건
GET /api/admin/settlements/accruals 200  totalElements: 0   ← DB 에는 3건
```

**entitlement 레그는 통과하는데 행 수준 테넌트 필터가 전부 걸러낸다.** 200 + 빈 배열이라
헬스 체크 · 상태코드 스모크 · degraded 마커 탐지 어느 것도 잡지 못한다. `TASK-BE-572` 의
라이브 검증이 `/ecommerce`(개요) 200 을 확인하고 초록으로 지나간 것도 같은 이유다 — 개요는
테넌트 데이터를 나열하지 않는다.

> 교훈: **"게이트웨이가 토큰을 받았다" 와 "그 토큰이 데이터를 본다" 는 다른 명제다.**
> 후자는 목록의 원소 수로만 확인된다.

## 🔴 읽기만의 문제가 아니다 — 운영자의 쓰기도 막힌다

`MONO-506` 의 시드가 배송을 진행시키려다 같은 벽에 부딪혔다. 소비자 토큰으로 **방금 조회한**
배송 건이 운영자에게는 존재하지 않는다:

```
GET  /api/shippings/orders/{orderId}   200  (소비자 토큰)  → shippingId=ae08bf5c-…
PUT  /api/shippings/ae08bf5c-…/status  404  (운영자 토큰)  SHIPPING_NOT_FOUND
```

그 결과가 연쇄한다: 배송을 `DELIVERED` 로 만들 수 없고 → 리뷰를 쓸 수 없다(리뷰는 배송
완료된 주문에만 허용 — 정당한 도메인 규칙이다) → 스토어프런트 `/my/reviews` 와 PDP 리뷰
섹션이 빈다. **하나의 테넌트 분리가 화면 5개와 쓰기 경로 2개를 함께 막고 있다.**

이 사실은 선택지 판단에도 영향을 준다: 읽기만 고치는 접근(예: 목록 조회에만 org-scope 적용)은
쓰기 경로를 남긴다.

---

# Goal

데모 계정으로 스토어프런트에서 구매한 주문이 **콘솔의 「주문」 탭에 보인다.** 상품 · 사용자 ·
배송 · 정산도 같다.

---

# Scope

## In Scope

- 데모 운영자의 테넌트 assignment 보강 (`iam-platform` 의 dev/demo Flyway 시드)
- 그 결과 콘솔의 테넌트 스위처에 나타나는 목록 확인 (UX 영향)
- `docs/guides/interview-demo-walkthrough.md` § 4 의 "지금 열리지 않는 것" 절 삭제

## Out of Scope

- 프로덕션 테넌트 모델 변경 — 이것은 **데모 신원 시드**의 문제다
- ecommerce 게이트웨이의 `required-tenant-id` 변경 — 소비자 격리는 의도된 설계다

---

# 설계 선택지 (착수 시 판단 — ADR 필요 여부 포함)

| 안 | 방식 | 유의점 |
|---|---|---|
| A | 데모 운영자에게 `ecommerce`(및 다른 도메인 테넌트) assignment 를 **추가**한다 | 가장 작다. 콘솔 테넌트 스위처에 항목이 늘어 데모 서사가 "테넌트 하나" 에서 흐려진다 |
| B | 스토어프런트 데모 구매를 `demo-corp` 테넌트로 만든다 | 소비자 토큰의 `tenant_id` 를 바꿔야 하는데 게이트웨이가 막는다. **사실상 불가** |
| C | `demo-corp` 를 `ecommerce` 테넌트의 **상위 조직 노드**로 두고 org-scope 로 내려본다 | `org_scope=["*"]` 가 이미 토큰에 있다 — 도메인 서비스가 그것을 읽는지 **먼저 확인**할 것 |

C 가 성립하면 가장 정석이다(assume 토큰에 이미 `org_scope` 가 실려 있다). 각 도메인 서비스의
테넌트 필터가 `org_scope` 를 고려하는지 전수 확인이 선행이다 — 고려하지 않는다면 그것은
**이 티켓보다 큰 결정**이므로 ADR 로 올린다.

---

# Acceptance Criteria

- [ ] **AC-0 (재측정)** — 착수 시 `operator_tenant_assignment` 와 위 5개 엔드포인트의
      원소 수를 **다시 측정한다.** 그리고 **영향 받는 화면의 모집단을 다시 센다** — 이 티켓은
      ecommerce 5개만 확인했다. WMS · SCM · ERP · Finance 의 목록 화면도 같은 구조인지
      각 도메인 스택을 띄워 확인할 것(같은 구조라면 이 티켓의 범위가 5배다)
- [ ] **AC-1** — 데모 계정으로 스토어프런트에서 구매한 주문이 콘솔 `/ecommerce/orders` 에
      **브라우저로** 보인다. 200 이나 마커 부재로 판정하지 않는다 — **그 주문 id 가 화면에
      있는지**로 판정한다
- [ ] **AC-2** — `/ecommerce/products` · `/users` · `/shippings` · `/settlements` 의 원소 수가
      DB 실측과 일치한다
- [ ] **AC-3** — 테넌트 스위처의 목록이 데모 서사와 모순되지 않는다(안 A 를 고른 경우,
      가이드에 그 이유를 적는다)
- [ ] **AC-4** — 기존 `acme-corp`/`globex-corp` 데모 쌍의 동작이 회귀하지 않는다
- [ ] **AC-5** — 워크스루 가이드의 한계 표에서 이 줄을 제거한다

---

# Related Specs

- `projects/iam-platform/apps/account-service/src/main/resources/db/migration-dev/V9005__seed_demo_corp_tenant_and_consumer_accounts.sql` — `TASK-BE-571` 이 심은 것
- `projects/iam-platform/apps/auth-service/.../OperatorRoleDerivation.java` — 역할 파생
- `projects/ecommerce-microservices-platform/apps/gateway-service/src/main/resources/application.yml` — `required-tenant-id`
- `docs/guides/interview-demo-walkthrough.md` § 4

# Related Contracts

- `projects/iam-platform/specs/contracts/http/auth-api.md` — assume-tenant

---

# Edge Cases

- assignment 를 추가하면 그 운영자가 **실제 고객 테넌트**의 데이터를 보게 된다 —
  데모 환경에서만 유효한 시드인지 분명히 할 것(`migration-dev` 밴드)
- 콘솔이 마지막 선택 테넌트를 쿠키/세션에 기억한다 — 기존 세션의 `demo-corp` 가 남아 있으면
  변경이 즉시 보이지 않는다
- WMS 등 다른 도메인도 같은 구조라면, 테넌트 스위처 목록이 5개로 늘어난다

# Failure Scenarios

- **assignment 만 추가하고 화면을 안 본다** → 다른 필터(예: seller-scope)가 또 걸러내는데
  "고쳤다" 고 기록된다. AC-1 이 막는다
- **ecommerce 만 고치고 끝낸다** → 나머지 4개 도메인에서 같은 증상이 남는다. AC-0 이 막는다
- **200 을 성공 판정에 쓴다** → 이 결함은 200 이었다

# Test Requirements

- 콘솔 브라우저 실주행(구매 → 콘솔 주문 탭에서 그 주문 id 확인)
- 기존 데모 쌍(acme/globex) 회귀 확인

# Definition of Done

- [ ] 구현 + 테스트
- [ ] 브라우저 검증 증거(주문 id 대조)
- [ ] 워크스루 가이드 갱신
- [ ] Ready for review
