# Task ID

TASK-MONO-780

# Title

스토어 주문의 wms 출고 주문은 `tenant_id=ecommerce` 인데 콘솔 WMS 화면은 로그인 토큰(demo-corp)으로만 조회한다 — **풀필먼트 출고가 콘솔에 영영 안 보인다** (소유자 결정)

# Status

review

# Owner

monorepo

# Task Tags

- wms
- ecommerce
- platform-console
- demo

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus — «풀필먼트 출고는 어느 테넌트 소속인가» 는 멀티테넌시 결정이다. 고칠 자리가 셋(wms 소비자 · 콘솔 wms 프록시 · 데모 시드) 중 어디인지가 결정의 결과다.

---

# Dependency Markers

- 출처: 24차 데모 창(2026-10-08 UTC) — `TASK-MONO-765` AC-4 의 WMS 콘솔 칸이 이 공백으로 닫히지 않는다(765 는 `review/` 에 남아 이 티켓을 가리킨다).
- 관련: `TASK-MONO-304`(wms 교차 테넌트 범위를 서명된 JWT 에서) · `TASK-BE-576`(콘솔 테넌트 축 — 같은 URL 이 테넌트에 따라 0/8) · `TASK-MONO-760`(scm 가시성 노드는 demo-corp).

# Goal

24차 창 실측:

| 사실 | 값 | 출처 |
|---|---|---|
| 스토어 주문 → wms 출고 주문 | `source=FULFILLMENT_ECOMMERCE` · **`tenant_id=ecommerce`** · 4건(시드 3 + 소유자 주문 1, PICKING) | `outbound_db.outbound_order` |
| 시드 수동 출고 | `SO-DEMO-0001` · `tenant_id=demo-corp` | 같은 표 |
| 콘솔 WMS 출고 목록 | demo@demo.com 로 **1건(SO-DEMO-0001)만** — 콘솔 상단 테넌트를 ecommerce 로 바꿔도 같다(소유자 브라우저) | |
| 왜 바꿔도 같은가 | 콘솔 wms 프록시는 **IAM OIDC 토큰을 그대로** 붙인다(테넌트 교환 토큰 아님) | `console-web/src/app/api/wms/_proxy.ts:10-13` |
| wms 가 거르는 값 | 클라이언트 필터가 아니라 **서명된 JWT 의 tenant** | `OrderQueryController.java:88-90` · `OrderJpaRepository.java:35` |
| scm 재고 가시성 WH-MAIN 노드 | `tenant_id=demo-corp` | `scm_inventory_visibility.inventory_nodes` |

⇒ 같은 창고(WH-MAIN)의 같은 흐름이 wms 출고는 ecommerce, scm 노드는 demo-corp 에 있다. 콘솔 운영자는 출고를 볼 길이 없다.

## 🔵 소유자 결정 (2026-10-08 UTC) — ⓑ, 그리고 그것은 **이미 ADR 이 정한 것**이다

> OWNER DECISION: «추천대로 진행» — 갈래 ⓑ(콘솔이 활성 테넌트의 토큰으로 wms 를 조회). 근거는 아래 ADR 대조.

기안 때 ⓐⓑⓒ 를 같은 무게의 선택지로 적었는데, 재측정해 보니 **`ADR-MONO-022` D9 (TASK-MONO-304) 가 이미 답을 갖고 있었다**:

| ADR-022 D9 가 정한 것 | 지금 코드 | 일치 |
|---|---|---|
| 풀필먼트 출고의 `tenant_id` = **주문을 낸 고객 테넌트**(facet d — 이벤트 봉투의 tenant) | `FulfillmentRequestedConsumer.java:113,170` `envelope.tenantId()` → `ReceiveOrderCommand` | ✅ |
| 고객 테넌트 운영자는 **서명된 `tenant_id`** 로 자기 테넌트의 `FULFILLMENT_ECOMMERCE` 주문만 본다 · wms 본래 운영자(`tenant_id=wms`)는 전부 | `SecurityContextCallerScopeProvider.java:68-87`(`wms.oauth2.required-tenant-id`, 기본 `wms` · 데모 `docker-compose.e2e.yml:64` 도 `wms`) | ✅ |
| «**콘솔은 이미 assume 한 테넌트 토큰을 넘긴다** — no console change» | 콘솔 wms 프록시는 **로그인 IAM OIDC 토큰**을 붙인다(`_proxy.ts:9-13` «NOT the GAP exchanged operator token — the wms gateway requires the IAM OIDC token») | ❌ **어긋난 곳은 여기 하나** |

⇒ 갈래 재평가:
- **ⓐ 창고 운영사 테넌트로 저장** — D9 의 격리 키를 뒤집는다(ecommerce 운영자가 자기 주문을 못 보게 된다). ADR 개정 없이는 불가 → **기각**.
- **ⓒ 데모에서만 운영자를 ecommerce 에 배정** — 단독으로는 **효과 없음**: 콘솔이 여전히 demo-corp 로그인 토큰을 보낸다. ⓑ 의 데모 측 보조(배정)로만 의미가 있다.
- **ⓑ 콘솔이 활성 테넌트 토큰으로 wms 조회** — D9 가 전제한 동작을 코드가 따라가게 한다. 데이터 모델·wms 코드 무변경이 목표.

# Scope

## In Scope

- **AC-0 = 재측정(구현 위치 판정)** — 🔴 프록시 주석은 «wms 게이트웨이는 IAM OIDC 토큰을 요구한다» 고 한다. 다음을 잰다:
  1. 콘솔 테넌트 전환(`POST /api/tenant`) 뒤 쿠키에 있는 토큰 중 무엇이 `tenant_id=<활성 테넌트>` 를 싣는가(IAM assume-tenant 교환 토큰인가, GAP 교환 토큰인가) — 발급자(`iss`) · `aud` · `tenant_id` · `entitled_domains` 표.
  2. wms 게이트웨이가 그 토큰을 받는가(발급자 · audience 검증 file:line). 받으면 구현 = 콘솔 `wms-api.ts` 의 토큰 선택만. 안 받으면 구현 위치가 게이트웨이(허용 발급자/audience) 쪽으로 바뀐다 — 그 경우 보안 영향 표를 먼저 쓰고 소유자에게 다시 묻는다.
  3. demo@ 의 ecommerce 테넌트 토큰이 `entitled_domains` 에 `wms` 를 싣는가(D9 의 dual-accept 전제). 안 실으면 데모 측 구독/배정(ⓒ 보조)이 필요하다.
- 판정된 위치의 구현 + 시험(콘솔: 활성 테넌트 토큰이 wms 호출에 실린다 · 활성 테넌트 없음 = 지금 동작 / wms: D9 시험이 이미 있으면 재사용) + 계약 행(`console-integration-contract` § 2.4.5 의 토큰 문장 정정).

## Out of Scope

- scm 가시성 노드 이름이 비어 콘솔에 UUID 로만 보이는 것(`inventory_nodes.name` 공백, `warehouse_code=WH-MAIN` 은 있음) — 작은 표시 개선, 결정 뒤 같은 축이면 함께, 아니면 별도.

# Acceptance Criteria

- [x] **AC-0** — 위 In Scope 의 재측정 1·2·3 표 + 구현 위치 판정(콘솔만 / 게이트웨이까지). 게이트웨이까지면 보안 영향 표를 쓰고 소유자 재확인 뒤 AC-1 로. (갈래 결정 자체는 위 «소유자 결정» 절에 기록됨 — ⓑ.) → **판정 = 콘솔만(CONSOLE-ONLY)**. 게이트웨이 무변경. 아래 § AC-0 재측정. 🔴 단 **기안의 전제 하나가 틀렸다**: 콘솔 wms 호출은 이미(2026-05-31 `TASK-MONO-158` #985 이후) 활성 테넌트의 assumed 토큰을 붙인다 — 어긋나 있던 것은 토큰 선택 코드가 아니라 `_proxy.ts` 주석·계약 § 2.4.5 문장, 그리고 **클라이언트 캐시**였다.
- [x] **AC-1** — 판정 위치 구현 · 단위/IT · bite(토큰 선택을 로그인 토큰으로 되돌리면 시험 빨강). → 토큰 선택 핀(`wms-active-tenant-token.test.ts` 8칸) + 증상을 실제로 내는 기전(클라이언트 캐시, `use-tenant-switch.ts`) 수정 + 시험 5칸 · bite 3종(아래 § 구현 기록).
- [ ] **AC-2** — ⚪ 재굽기 창: 스토어 주문 1건이 콘솔 WMS 출고 목록에 보인다 → `TASK-MONO-765` AC-4 의 WMS 칸을 닫는다. ⏳ **라이브 데모 창 필요**(콘솔은 Vercel 배포 ⇒ 이 PR 머지 뒤 재굽기 없이 창만 열면 된다). 창에서 볼 것: ① `/wms/outbound` 를 demo-corp 로 연 채 상단 스위처로 ecommerce → 목록이 `SO-DEMO-0001` 에서 풀필먼트 4건으로 바뀌는가 ② 다른 화면에서 전환한 뒤 `/wms/outbound` 진입 ③ demo-corp 로 되돌리면 `SO-DEMO-0001` 로 돌아오는가. 🔴 ①이 그래도 1건이면 이 PR 의 원인 지목(아래 § 원인)은 **틀린 것**이다 — 「증상이 살아남으면 원인이 아니었다」.

# Related Specs

- `docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md` § D9 (+ facet d)
- `projects/wms-platform/specs/services/outbound-service/` · `projects/platform-console/specs/` § wms 연동 · `rules/` 멀티테넌시 규칙

# Related Contracts

- wms `outbound-service-api.md` § 목록(테넌트 범위) · ecommerce `fulfillment.requested` 이벤트

# Edge Cases

- demo-corp 로 돌아오면 wms 화면은 지금처럼 demo-corp 출고(`SO-DEMO-0001`)를 보여야 한다 — 활성 테넌트를 따라가는 것이지 «ecommerce 고정» 이 아니다.
- 활성 테넌트가 wms 구독이 없는 테넌트 — 게이트웨이의 dual-accept 가 거절(403)하면 콘솔은 «이 테넌트는 WMS 를 구독하지 않습니다» 계열로(지금 403 매핑 재사용).
- scm 노드(demo-corp)와 wms 풀필먼트 출고(ecommerce)의 테넌트가 다른 것은 **D9 상 정상**이다(창고 재고 vs 고객 주문). 데모 동선 문구로만 안내한다.

# Failure Scenarios

1. 콘솔에서 테넌트 필터를 클라이언트 파라미터로 열어 준다 — `TASK-MONO-304` 가 막은 교차 테넌트 읽기를 되살린다.
2. 게이트웨이를 «아무 발급자나» 받게 넓힌다 — 프록시 주석이 지키던 불변식(#569)을 확인 없이 깬다. AC-0 의 2 가 «안 받는다» 면 멈추고 묻는다.
3. ⓐ 로 우회한다 — ADR-022 D9 의 격리 키를 조용히 뒤집는다.

---

# AC-0 재측정 (2026-10-08 UTC, 정적 — 코드 읽기, 라이브 미측정)

> 착수: `ready → in-progress` (2026-10-08 UTC). 판정 근거는 전부 이 브랜치(`origin/main` `65c2e3234` 기준)의 file:line 이다. 라이브 토큰을 디코드한 것이 **아니다** — 값은 발급 코드가 무엇을 싣는지로 읽었다.

## (1) 테넌트 전환 뒤 어느 토큰이 `tenant_id=<활성 테넌트>` 를 싣는가

전환 경로: `POST /api/tenant` → 레지스트리 허용 검사 → **assume-tenant 교환**(GAP 교환 아님) → 두 쿠키를 원자적으로.

| 단계 | 근거 |
|---|---|
| 기본 로그인 토큰을 `subject_token` 으로만 읽는다 | `console-web/src/app/api/tenant/route.ts:140` |
| assume-tenant RFC 8693 교환 호출(`audience=<선택 테넌트>`, `client_id=platform-console-web`, `POST <OIDC_ISSUER_URL>/oauth2/token`) | `route.ts:153` → `shared/lib/assume-tenant-exchange.ts:119-124,130` |
| `console_active_tenant` + `console_assumed_token` 을 **함께** 저장 | `route.ts:187-191` |
| wms 호출이 붙이는 토큰 = `getDomainFacingToken()` = assumed ?? 로그인 | `shared/api/wms-gateway.ts:276` → `shared/lib/session.ts:237-239` |

토큰별 클레임(발급 코드 기준):

| 토큰 | 쿠키 | `iss` | `aud` | `tenant_id` | `entitled_domains` | 근거 |
|---|---|---|---|---|---|---|
| **assume-tenant (전환 뒤 wms 가 받는 것)** | `console_assumed_token` | auth-service SAS 발급자 = 로그인 토큰과 **같은** `iss`/kid | `platform-console-web` (요청 클라이언트 id) | **선택 테넌트** (`ecommerce`) | 선택 테넌트의 ACTIVE 구독 ∩ org-node 상한 | `AssumeTenantAuthenticationProvider.java:53-55`(같은 iss/kid) · `:219-226`(registeredClient = 콘솔) · `TenantClaimTokenCustomizer.java:770-772`(tenant_id) · `:815,858-861`(entitled_domains) · `:830-838`(roles ← entitled) |
| 로그인(base) 토큰 | `console_access_token` | 같은 SAS 발급자 | `platform-console-web` | 클라이언트 운영 슬러그 `iam` | 생략 | contract § 2.7 «net-zero 철회»(TASK-PC-FE-292) · `DomainTenantGate.tsx:16-20` |
| GAP 교환 운영자 토큰 | `console_operator_token` | `admin-service` | — | — | — | `session.ts:19-23` — **wms 에 쓰이지 않는다**(`wms-gateway.ts` 는 `getOperatorToken` 을 부르지 않음) |

⇒ 전환 뒤 `tenant_id=ecommerce` 를 싣는 것은 **IAM assume-tenant 토큰**이고, wms 호출은 이미 그것을 쓴다.

## (2) wms 게이트웨이가 그 토큰을 받는가

| 검사 | 설정/코드 | assume 토큰(`ecommerce`) |
|---|---|---|
| 발급자 | `gateway-service/.../application.yml:159` `allowed-issuers = ${OIDC_ALLOWED_ISSUERS:${OIDC_ISSUER_URL}}` · 데모 `infra/demo/demo.env:199` `WMS_OIDC_ALLOWED_ISSUERS=${IAM_PUBLIC_URL}` | 같은 SAS 발급자 ⇒ ✅ |
| audience | `application.yml:169` `allowed-audiences = platform-console-web` · `:175` `ENFORCE` | `aud=platform-console-web` ⇒ ✅ |
| 테넌트 게이트 | `OAuth2ResourceServerConfig.java:101-105` `forTenant(wms).trustEntitledDomains()` (와일드카드 없음) → `libs/java-security/.../TenantClaimValidator.java:157-165`(legacy 일치 실패 시 `entitled_domains ∋ wms` 면 통과) | `tenant_id=ecommerce`≠`wms` 이지만 `entitled_domains ∋ wms` (아래 3) ⇒ ✅ |
| 서비스 측 재검증 | `outbound-service/.../OAuth2ResourceServerConfig.java:73` · `admin-service/.../OAuth2ResourceServerConfig.java:80` 둘 다 `.trustEntitledDomains()` | ✅ |
| 읽기 범위 | `SecurityContextCallerScopeProvider.java:79-86` — `tenant_id≠wms` ⇒ `restrictedTo(tenant)` → `OrderJpaRepository.java:35` | ecommerce 행만 |

⇒ **게이트웨이는 지금 그대로 받는다. 게이트웨이 변경 불필요 → 보안 영향 표 불요**(넓힐 것이 없다; #569 불변식 = IAM `/api/admin/**` 에 로그인 토큰 금지 — 이 티켓은 그 경계를 건드리지 않는다).

## (3) demo@ 의 ecommerce 토큰이 `entitled_domains ∋ wms` 인가

| 사실 | 근거 |
|---|---|
| `ecommerce` 테넌트의 `wms` 구독 ACTIVE | `iam-platform/apps/account-service/.../db/migration/V0025__seed_ecommerce_wms_domain_subscription.sql:20-23` (Flyway 정규 마이그레이션 — 데모 시드 아님, 모든 환경) |
| org-node 상한이 좁히지 않는다 | `V0028__backfill_org_node_per_tenant.sql` — 모든 기존 테넌트에 `UNBOUNDED`; `infra/demo` 에 org_node 조작 0건(grep) |
| 유효 entitled = ACTIVE ∩ 상한 | `TenantEntitledDomainsQueryUseCase.java:59-72` |
| demo@ 가 ecommerce 에 배정(assume 허용) | `iam-platform/apps/admin-service/.../db/migration-dev/R__seed_demo_operator.sql:149-150` (TASK-BE-576) |
| roles 도 따라온다 | `OperatorRoleDerivation.java:102` `"wms" -> WMS_OPERATOR_ROLES` |

⇒ ✅ **데모 시드 변경 불필요**.

## 판정

**CONSOLE-ONLY** — 그리고 토큰 선택 자체는 이미 맞다. 그렇다면 24차 창에서 본 «ecommerce 로 바꿔도 `SO-DEMO-0001` 1건» 은 무엇이 냈나:

- `SO-DEMO-0001` 은 `tenant_id=demo-corp`. 위 (2) 의 범위 규칙상 그 행은 **`tenant_id=demo-corp`(또는 `wms`/무JWT) 요청에만** 돌아온다 ⇒ 화면의 그 목록은 ecommerce 토큰으로 받은 응답이 **아니다**.
- 서버 쪽은 매 요청 쿠키를 읽는다(`wms/outbound/page.tsx:15` `force-dynamic`). 남는 자리는 **클라이언트 캐시**다: `features/wms-outbound-ops/hooks/use-outbound-ops.ts:84-97` 의 목록 쿼리는 서버 렌더로 시드되고(`initialData`, `staleTime 30s`, `refetchOnMount: false`) 키에 **테넌트 칸이 없다**(`ordersKey`, `:49-59`). 전환 훅 `shared/api/use-tenant-switch.ts` 는 `catalog`·`session`·`operators`·`audit` 만 무효화하고 `router.refresh()` 를 부른다 — React Query 는 이미 가진 키에 새 `initialData` 를 **무시**하므로(PC-FE-044 가 `operators`/`audit` 에서 고친 바로 그 모양) 이전 테넌트 행이 화면에 남는다.

## 원인 (지목 — 🔴 라이브 미확인)

위 캐시 기전이 증상을 **정확히** 낸다는 것은 단위 시험으로 재현했다(아래 «visited/mounted» 칸이 수정 전 코드에서 `SO-DEMO-0001` 을 유지). 그러나 24차 창의 그 화면이 **이 기전으로** 그랬는지는 라이브로 잰 것이 아니다 — 「검증 가능한 기전 ≠ 원인」. AC-2 가 판정한다. 다른 후보(전환이 403/503 으로 거절돼 쿠키가 그대로였다 · 배포된 콘솔이 다른 커밋이었다)는 정적으로 배제할 수 없어 ⚪ 로 남긴다.

---

# 구현 기록 (2026-10-08 UTC)

## 바꾼 것

| 파일 | 변경 |
|---|---|
| `console-web/src/shared/api/use-tenant-switch.ts` | 전환 성공 시 wms 쿼리 루트(`wms-ops`·`wms-outbound-ops`)를 **비활성은 제거 + 활성은 무효화**(`WMS_TENANT_SCOPED_QUERY_ROOTS`). 🔴 무효화만으로는 부족 — `refetchOnMount:false` 라 무효화된 비활성 항목이 다음 방문에 그대로 보인다(bite ③ 이 실측). `shared` 는 `features` 를 import 못 하므로 문자열 리터럴 + 시험이 features 의 키 빌더와 대조. |
| `console-web/src/app/api/wms/_proxy.ts` | 헤더 주석 정정: «IAM OIDC 토큰(NOT GAP)» → 접근자 `getDomainFacingToken()` 와 그것이 활성 테넌트 assumed 토큰임을 명시 + 이 오독의 경위. |
| `console-web/src/features/wms-ops/api/wms-client.ts` · `features/wms-ops/index.ts` | 같은 낡은 문장(`getAccessToken()`) 정정. |
| `platform-console/specs/contracts/console-integration-contract.md` § 2.4.5 | 자격 표의 wms 행 · 본문 «`getAccessToken()` 을 쓴다» 문장 정정 + D9 줄의 «platform `*` unrestricted» 정정(ADR-MONO-064 § D3 이후 `restrictedTo("*")`). |
| `console-web/tests/unit/wms-active-tenant-token.test.ts` (신규, 8칸) | wms **두 주문 표면**(outbound `/orders` · admin `/dashboard/orders`) × {ecommerce 전환 ⇒ `ASSUMED-ecommerce` · demo-corp 로 복귀 ⇒ `ASSUMED-demo-corp` · 활성 테넌트 없음 ⇒ 로그인 토큰 · 미구독 테넌트 403 ⇒ 기존 `ApiError(403)`}. |
| `console-web/tests/unit/use-tenant-switch.test.tsx` (신규, 5칸) | 키 루트 대조 · 마운트된 목록이 전환 뒤 ecommerce 로 · 전환 전 방문한(언마운트) 목록이 캐시로 안 돌아옴 · demo-corp 복귀 · 거절된 전환(403)은 캐시 무변경. |

🔵 측정 정정: 기안·주석 표류와 달리 «전환된 경우» 핀이 **0 은 아니었다** — `domain-facing-credential.test.ts:77` 이 admin read-model(`listInventory`)의 assumed 토큰을 이미 잡고 있었다. 빠져 있던 것은 **outbound 클라이언트**(24차 창이 본 «WMS 출고» 화면)의 전환 칸 · 복귀 칸 · 403 칸이다.

## 하지 않은 것

- 게이트웨이·wms·IAM·데모 시드 — 변경 0 (AC-0 (2)(3)).
- 클라이언트 테넌트 파라미터 — 없음(Failure Scenario 1). 테넌트는 여전히 서명된 토큰 안에만 있다(`X-Tenant-Id` 부재를 새 시험이 단언).

## bite (각각 되돌린 뒤 원복 — 원복 후 `git diff` 로 원본 일치 확인)

| # | 주입 | 결과 |
|---|---|---|
| ① | `wms-gateway.ts` 의 `getDomainFacingToken` 을 `getAccessToken` 으로(로그인 토큰으로 되돌림) | `wms-active-tenant-token.test.ts` **rc=1, 4 failed / 4 passed** — 전환·복귀 칸 × 두 표면이 `BASE-LOGIN-TOKEN` 을 받음. (없음·403 칸은 통과 = 대조군) |
| ② | `use-tenant-switch.ts` 의 wms 루프 삭제 | `use-tenant-switch.test.tsx` **rc=1, 3 failed / 2 passed** — 마운트·방문·복귀 칸이 `SO-DEMO-0001` 유지 |
| ③ | `removeQueries` 줄만 삭제(무효화만 — PC-FE-044 의 모양) | **rc=1, 1 failed / 4 passed** — «전환 전 방문한 목록» 칸만 빨강 ⇒ 무효화만으로는 재방문 경로가 열려 있다 |

## ⚪ 남긴 것

- **AC-2** — 라이브 창(위). 원인 지목의 판정은 거기서.
- 🔴 **같은 모양의 형제** — `operators`/`audit`(PC-FE-044)도 **무효화만** 한다 ⇒ bite ③ 이 보인 «전환 전 방문 → 전환 → 재방문 시 이전 테넌트 목록» 경로가 그 둘에도 열려 있을 수 있다. scm·erp·finance·ecommerce 섹션의 시드형 쿼리도 같은지는 **세지 않았다**. 이 티켓 범위(wms) 밖 — 후속 기안 후보.

