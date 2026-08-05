# Task ID

TASK-BE-576

# Title

데모 운영자가 자기 테넌트(`demo-corp`)에만 assign 돼 있어 콘솔의 도메인 목록 화면이 전부 비어 있다 — 게이트웨이는 200 을 내므로 상태코드로는 보이지 않는다

# Status

review

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

- [x] **AC-0 (재측정)** — 착수 시 `operator_tenant_assignment` 와 위 5개 엔드포인트의
      원소 수를 **다시 측정한다.** 그리고 **영향 받는 화면의 모집단을 다시 센다** — 이 티켓은
      ecommerce 5개만 확인했다. WMS · SCM · ERP · Finance 의 목록 화면도 같은 구조인지
      각 도메인 스택을 띄워 확인할 것(같은 구조라면 이 티켓의 범위가 5배다)
- [x] **AC-1** — 데모 계정으로 스토어프런트에서 구매한 주문이 콘솔 `/ecommerce/orders` 에
      **브라우저로** 보인다. 200 이나 마커 부재로 판정하지 않는다 — **그 주문 id 가 화면에
      있는지**로 판정한다
- [x] **AC-2** — `/ecommerce/products` · `/users` · `/shippings` · `/settlements` 의 원소 수가
      DB 실측과 일치한다
- [x] **AC-3** — 테넌트 스위처의 목록이 데모 서사와 모순되지 않는다(안 A 를 고른 경우,
      가이드에 그 이유를 적는다)
- [x] **AC-4** — 기존 `acme-corp`/`globex-corp` 데모 쌍의 동작이 회귀하지 않는다
- [x] **AC-5** — 워크스루 가이드의 한계 표에서 이 줄을 제거한다

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

- [x] 구현 + 테스트
- [x] 브라우저 검증 증거(주문 id 대조)
- [x] 워크스루 가이드 갱신
- [x] Ready for review

---

# 실행 결과 (2026-08-05)

## AC-0 재측정 — 범위는 5배가 아니라 **1배였다**

착수 전 재측정으로 티켓의 수치는 그대로 재현됐다(원소 수 0/0/0/0/0, DB 8/4/1/3/3;
assignment `operator 5 → demo-corp` 한 줄).

범위 질문("나머지 네 도메인도 같은 구조인가")의 답은 **기전은 같고 증상은 다르다**:

| | 영속층에 tenant 를 실어 나르는 파일 | 콘솔 밖 writer |
|---|---|---|
| ecommerce | 85 | **있다 — 스토어프런트** |
| scm | 43 | 없다(데모에서) |
| finance | 44 | 없다(데모에서) |
| erp | 33 | 없다(데모에서) |
| wms | 5 (outbound 만) | 없다(데모에서) |

**기전은 도메인 무관**하다 — 다섯 도메인 전부 리포지터리 호출에 tenant 를 끼워 넣는다.
그런데 **증상은 콘솔 밖 writer 가 있어야 생긴다.** 데모에서 그런 writer 를 가진 도메인은
ecommerce 뿐이고(스토어프런트), 나머지 네 도메인의 데이터는 같은 운영자가 콘솔에서
`demo-corp` 를 assume 한 채 만들 것이므로 `demo-corp` 에 들어가 보인다.

⇒ **assignment 는 `ecommerce` 하나만 추가했다.** 넷을 더 넣으면 스위처에 네 칸이 더
붙는데, 그건 아무도 측정하지 않은 증상을 위한 것이다. `TASK-MONO-510` 의 AC-0 이 도메인별
원소 수를 DB 와 대조하므로, 그중 하나가 실제로 콘솔 밖 writer 를 가졌다면 여기 한 줄을 더
넣으면 된다(도메인마다 `*-internal-services-client` 가 자기 도메인 테넌트로 등록돼 있어
후보이긴 하다).

### 부수 발견 — 모든 도메인 클라이언트는 자기 도메인 테넌트에 산다

| tenant | clients |
|---|---|
| ecommerce | web-store · admin-dashboard |
| wms | user-flow · internal-services |
| scm / erp / finance | internal-services |
| fan-platform | user-flow · community-service · demo-spa · test-internal |
| iam | **platform-console-web 뿐** |

`demo-corp` 는 **어느 클라이언트도 속하지 않는 테넌트**다. 그것이 "권한은 있는데 데이터는
없다" 의 구조적 이유다.

## 선택지 판단 — B 는 측정으로 죽었다

티켓이 적은 안 B(구매를 `demo-corp` 로 만든다)는 **불가능이 아니라 무용**임이 밝혀졌다:
**카탈로그가 `ecommerce` 에 산다.** `product-service` V8 은 tenant 컬럼을 적지 않아 기본값을
타고, 그 값이 `ecommerce` 다(상품 8/8 · 카테고리 7/7 실측). `demo-corp` 로 뜨는
스토어프런트는 **텅 빈 상점**이 된다.

안 C(`org_scope`)도 죽었다 — 토큰에 `["*"]` 가 실려 있지만 **도메인 서비스의 행 필터는
`tenantId` 동등 비교**다(`TenantContext.currentTenant()` → 리포지터리 인자). ecommerce 에서
`org_scope` 가 언급되는 곳은 seller-scope 의 *형태* 주석뿐이다.

⇒ **안 A**. 그리고 A 는 dev 전용 시드 한 줄이고 **제품 코드 변경이 0** 이다.

## 수정

`R__seed_demo_operator.sql` 에 `ecommerce` assignment 추가. **콘솔 코드 변경 없음** —
스위처는 콘솔 레지스트리가 보고하는 assignment 에서 목록을 만들므로 저절로
`[demo-corp] → [demo-corp, ecommerce]` 가 됐다(실측).

`infra/demo/seed/seed-ecommerce.sh` 의 운영자 토큰도 `demo-corp` → `ecommerce` 로 바꿨다.
안 그러면 콘솔이 **반쪽**이 된다 — 셀러·프로모션·템플릿은 보이는데 바로 옆의 상품·주문·
배송·정산은 다른 테넌트라 비어 있다.

## AC-1 / AC-2 — 라이브 (술어에 **음성 대조**를 넣었다)

`ORDER_ID` 를 콘솔 화면 HTML에서 찾는다. 200 이나 마커 부재로 판정하지 않는다:

```
PASS  스위처에 ecommerce 테넌트가 나타난다            ["demo-corp","ecommerce"]
PASS  [demo-corp] /ecommerce/orders — 주문 id 가 안 보인다   orderIdInHtml=false  23,291 bytes
PASS  [ecommerce] /ecommerce/orders — 주문 id 가 보인다      orderIdInHtml=true   29,396 bytes
… 11/11 PASS (products 23,599 → 41,076 bytes)
```

**음성 대조가 이 검증의 핵심이다** — 양성만 보면 "페이지가 렌더됐다" 와 구별되지 않는다.

원소 수 ↔ DB 대조(assume `ecommerce`): products **8**, orders **4**, users **1**,
shippings **3**, accruals **3** — 전부 일치.

## AC-1 — 쓰기 경로도 복구됐다

시드가 실제로 완주했다(직전까지 첫 전이에서 404):

```
배송 ae08bf5c-… 현재 상태=PREPARING → DELIVERED 까지 진행
  → SHIPPED / → IN_TRANSIT / → DELIVERED
생성  리뷰(베이직 코튼 티셔츠)
요약 — 생성 8 · 기존 5 · 실패 0
```

리뷰는 배송 완료 주문에만 쓸 수 있으므로, **이 티켓이 리뷰 시드까지 함께 풀었다.**

## 테스트 — 이 시드에는 테스트가 하나도 없었다

`R__seed_demo_operator.sql` 은 콘솔 데모의 신원 전부를 담고 있는데 **`TASK-BE-571` 이래
아무것도 단언하지 않았다.** 모든 행이 조용히 삭제 가능하다 — `oidc_subject` 를 잃으면
401 이 5초 타임아웃과 같은 문구로 렌더되고, assignment 를 잃으면 **200 + 빈 목록**이다.

`DemoOperatorSeedIntegrationTest` 3건 추가(실제 마이그레이션이 만든 DB 를 단언):
operator 행(테넌트·상태·`oidc_subject` 리터럴) · assignment 집합 **정확히**
`{demo-corp, ecommerce}` · SUPER_ADMIN 이 home 테넌트에만 묶여 있음.

집합을 **양방향으로** 못박은 이유: 빠지는 것이 이 티켓의 회귀이고, **늘어나는 것**은
데모 운영자의 도달 범위가 아무도 검토하지 않은 테넌트로 넓어졌다는 뜻이라 더 위험하다.

**부정 대조 실행**: `ecommerce` INSERT 를 지우고 돌리면 **정확히 그 한 건만** FAILED,
나머지 2건은 PASSED. 복구 후 3/3 PASSED.
🔴 복구 직후 첫 실행이 `BUILD SUCCESSFUL in 17s` 였는데 **PASSED 0줄** — Gradle 캐시 히트라
아무것도 돌지 않았다. `--rerun-tasks` 로 재실행해 3/3 을 확인했다.

## AC-4 — 회귀

`operator_tenant_assignment` 의 다른 행(`operator 4 → acme-corp, globex-corp`)은
건드리지 않았다. 이웃 IT(`OperatorAssignmentCheckIntegrationTest` ·
`TenantAdminRoleSeedIntegrationTest`) 재실행 통과.

## 남은 것

- **`demo-corp` 에 남은 옛 백오피스 행**(셀러 1 · 프로모션 1 · 템플릿 3) — 수정 전 시드가
  만든 잔재다. 갓 기동한 데모에는 존재하지 않으므로 제품 결함이 아니고, 지우는 것은
  파괴적 작업이라 하지 않았다.
- 나머지 네 도메인은 `TASK-MONO-510` AC-0 이 원소 수로 재확인한다.

