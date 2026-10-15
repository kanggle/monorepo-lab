# Task ID

TASK-MONO-792

# Title

`ADR-MONO-083` P5 — SCM 세분 서버 인가: 발주 · 수요계획 · 물류 · 재고 가시성의 조회/변경 역할 · `SCM_OPERATOR` 는 업무 API 를 열지 않는 진입 역할로 · iam 카탈로그 확장

# Status

in-progress

# Owner

monorepo

# Task Tags

- code
- api
- test
- iam

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (서버 인가 신설 · 카탈로그 · 이전 호환 세트 동시 갱신 — 원자 PR)
> 🔴 **cross-project 원자 PR**: scm 서비스 인가 + iam 카탈로그 · «이전 호환» SCM 세트 갱신이 한 PR 이어야 한다. 갈라지면 기존 운영자가 그 사이에 SCM 업무 0 이 되거나 세분이 무력화된다.

# Dependency Markers

- **선행**: `TASK-BE-631`(카탈로그 · 세트) · `TASK-BE-632`(발급 전환 — 이 단계의 역할은 세트로만 토큰에 실린다).
- 같은 모양의 형제: `TASK-MONO-793`(ERP) · `794`(Finance) · `795`(E-commerce).

# Goal

지금 SCM 4 서비스는 `authenticated()` 만 검사한다(`demand-planning-service …/SecurityConfig.java:45` · `logistics-service …/SecurityConfig.java:39` · `inventory-visibility` `:63-64` · `procurement` `:58`). 업무 구분(ADR-083 § 도메인 업무 구분 제안, OD-6 기본값)대로 서버 인가를 넣어 «SCM 조회 세트» · «발주 운영 세트» 가 서버에서 실제로 구분되게 한다.

# Scope

## In Scope

- 계약 먼저: `projects/scm-platform/specs/services/*/architecture.md` · `specs/contracts/http/*` 에 역할 · 판정 표.
- 서비스 인가: procurement · demand-planning · logistics(조회/변경) · inventory-visibility(조회). 이름은 계약에서 확정(예: `SCM_PROCUREMENT_READ/WRITE`). 기존 procurement 의 상태 기계 판정(발주 확정 = OPERATOR)과 공급사 등록 `requireOperator()` 는 **함께 유지** — 역할 판정과 겹쳐도 넓히지 않는다.
- `SCM_OPERATOR` = 진입 역할: 게이트웨이 통과 · 화면 진입용, 업무 API 를 단독으로 열지 않는다. 업무 역할이 하나라도 있으면 서버(auth)가 파생.
- iam: 카탈로그에 SCM 업무 역할 추가 · «이전 호환» SCM 세트를 새 역할 전체로 갱신(기존 운영자 = 지금과 같은 업무 범위) · 시스템 프리셋 «SCM 조회» 등.
- 시험: 각 서비스 컨트롤러 슬라이스 · 통합.

## Out of Scope

- 관리 등급(ADMIN) SCM 역할 신설(ADR-083 D2 — 별도 설계).
- SCM 데이터 범위(조직 · `org_scope`) — 지금 없음, 이 ADR 밖.

# Acceptance Criteria

- [ ] **AC-0** — 재측정: SCM 4 서비스의 판정 · 컨트롤러 전수(엔드포인트 × 조회/변경) · procurement 상태 기계 · `ActorContext.isOperator()` 의 역할 집합 · 업무 구분안을 소유자 OD-6 기본값과 대조(바꿀 점이 있으면 착수 전 소유자에게).
- [ ] **AC-1** — 🔴 대조군(실패 쪽 먼저): 수요계획 세트만 → 발주 변경 API 403 · 수요계획 200 / 조회 세트 → GET 200 · 변경 403 / `SCM_OPERATOR` 만(업무 역할 0) → 업무 API 403.
- [ ] **AC-2** — 이전 호환: 백필된 기존 운영자가 이 PR 전후로 같은 API 집합에 200(대조군).
- [ ] **AC-3** — WMS 세트만 가진 운영자의 SCM 직접 API 403 · SCM 세트만 가진 운영자의 WMS 직접 API 403.
- [ ] **AC-4** — 배포 상태(`main` · AMI · 데모 창 라이브)를 따로 적는다 · 라이브 없으면 ⚪ 와 이유.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` D2 · D3 · § 도메인 업무 구분 제안
- `projects/scm-platform/PROJECT.md` · `specs/services/*/architecture.md`

# Related Contracts

- `projects/scm-platform/specs/contracts/http/*` · `projects/iam-platform/specs/contracts/http/admin-api.md` § 카탈로그

# Edge Cases

- 공급사 웹훅(HMAC, JWT 없음) — 역할 판정 대상 아님, 무변경.
- `/internal/**`(네트워크 신뢰) — 무변경.

# Failure Scenarios

1. 게이트웨이에만 역할 판정을 넣는다 — 서비스 직접 호출이 우회한다.
2. 카탈로그만 넓히고 서비스 판정을 안 넣는다 — 세트 경계가 화면에만 있다(ADR-083 § 새로 생기는 위험 1).
3. 이전 호환 세트 갱신을 다른 PR 로 — 기존 운영자 업무 중단.

---

# AC-0 기록 — 2026-10-15 UTC (base `091290a57`) · 🔴 **STOP — 착수 전 소유자 결정 필요**

> 측정 = 코드 읽기(정적). 라이브 DB · 라이브 토큰은 열지 않았다(⚪ 표시). 이 기록 뒤 구현 커밋은 **없다** — 아래 질문 Q1~Q5 의 답을 받은 뒤 계약부터 시작한다.

## 1. 지금 판정 (4 서비스)

| 서비스 | 필터 체인 | 그 밖의 판정 | 출처 |
|---|---|---|---|
| procurement | `/api/procurement/webhooks/**` permitAll(HMAC 필터) · `/api/procurement/**` `authenticated()` · 나머지 deny | 공급사 등록 `requireOperator()` · PO 상태 기계(`ActorType` — BUYER/OPERATOR) | `procurement-service …/infrastructure/security/SecurityConfig.java:56-59` · `SupplierApplicationService.java:57, 133-136` · `PoStatusMachine.java:39-63` |
| demand-planning | `/api/demand-planning/**` `authenticated()` | 없음 | `demand-planning-service …/config/SecurityConfig.java:43-46` |
| logistics | `/api/logistics/**` `authenticated()` | 없음 | `logistics-service …/config/SecurityConfig.java:37-40` |
| inventory-visibility | `/internal/inventory-visibility/**` permitAll(네트워크 신뢰) · `/api/inventory-visibility/**` `authenticated()` | 없음 | `inventory-visibility-service …/config/SecurityConfig.java:51-65` |
| scm gateway | `RoleAdmissions.roleOrScope()` — **아무 역할 · 범위나** 통과 | — | `gateway-service …/GatewayIdentityConfig.java:67-78` |

`ActorContext.isOperator()` 역할 집합 = `{OPERATOR, ADMIN, SUPER_ADMIN, SCM_OPERATOR}` → `OPERATOR` 행위자, 그 밖의 인증 호출자 = `BUYER`(`ActorContext.java:18-36`). 상태 기계: BUYER 는 DRAFT→SUBMITTED · 취소, **OPERATOR 만 ACKNOWLEDGED→CONFIRMED**.

## 2. 엔드포인트 전수 (JWT 경로 27 = 조회 14 · 변경 13 · 웹훅 2 · 내부 1 — 컨트롤러 `@*Mapping` 전수)

| 서비스 | 조회(GET) | 변경 |
|---|---|---|
| procurement | `GET /po/{poId}` · `GET /po` · `GET /suppliers/{id}` · `GET /suppliers` (4) | `POST /po` · `POST /po/from-suggestion` · `POST /po/{id}/submit` · `POST /po/{id}/confirm`(+OPERATOR) · `POST /po/{id}/cancel` · `POST /suppliers`(+`requireOperator`) (6) |
| demand-planning | `GET /suggestions` · `GET /suggestions/{id}` · `GET /policies/{sku}` · `GET /sku-supplier-map/{sku}` (4) | `POST /suggestions/{id}/approve` · `POST /suggestions/{id}/dismiss` · `PUT /policies/{sku}` · `PUT /sku-supplier-map/{sku}` (4) |
| logistics | `GET /dispatches/{id}` · `GET /dispatches/by-shipment/{shipmentId}` (2) | `POST /dispatches/{id}:retry` (1) |
| inventory-visibility | `GET /snapshot` · `GET /sku/{sku}` · `GET /nodes` · `GET /staleness` (4) | 🔴 **`POST /nodes`(3PL 노드 등록) · `POST /nodes/{nodeId}/observed-stock`(3PL 재고 관측)** (2) |
| (무변경) | 웹훅 `POST /webhooks/supplier-ack` · `POST /webhooks/asn`(HMAC) · `GET /internal/inventory-visibility/snapshot`(네트워크 신뢰) | |

## 3. OD-6 기본값과 대조 — 그대로 못 가는 점 다섯

- 🔴 **Q1 — 재고 가시성은 «조회만» 이 아니다.** `NodeRegistrationController.java:52, 77` 에 변경 2개(ADR-MONO-054 D2 · D4, SCM-BE-046/047). OD-6 의 «재고 가시성(조회만)» 으로는 이 둘을 어느 역할에 둘지 정해지지 않는다. 콘솔에는 이 두 변경을 부르는 화면이 없다(`/scm/inventory` READ-ONLY, `app/api/scm/nodes/route.ts` = GET) · 데모 시드도 안 부른다(`seed-scm.sh:372` «쓰지 않는다»).
- 🔴 **Q2 — 수요계획 승인은 발주 변경을 부른다.** `POST /suggestions/{id}/approve` → `ProcurementDraftPoClient` 가 **운영자 토큰을 그대로** 실어 `POST /api/procurement/po/from-suggestion` 을 부른다(`ProcurementDraftPoClient.java:23-27, 112-119`). 발주 변경에 `SCM_PROCUREMENT_WRITE` 를 걸면 **수요계획 세트만 가진 운영자의 승인은 403 → 제안이 APPROVED 에 멈춘다**(재시도도 같은 403). AC-1 의 «수요계획 세트만 → 수요계획 200» 이 승인에서는 이 선택 없이 성립하지 않는다.
- 🔴 **Q3 — 물류의 유일한 화면 소비자는 WMS 출고 화면이다.** 콘솔 `features/wms-outbound-ops/api/outbound-logistics-api.ts:73-116` 이 SCM 게이트웨이로 `GET /dispatches/by-shipment` · `POST /dispatches/{id}:retry` 를 부른다(계약 § 2.4.3.4 `:281` «발송 재시도 … `authenticated()` only»). SCM 역할을 걸면 **WMS 출고 세트만 가진 운영자의 «발송 재시도» 와 배차 조회가 403** 이 된다 — AC-3(«WMS 세트만 → SCM 직접 API 403»)과 같은 방향이지만, 그 결과 WMS 화면의 버튼이 SCM 세트를 요구하게 된다.
- 🔴 **Q4 — `SCM_OPERATOR` «진입 역할 파생» 은 따를 선례가 없다.** 착수 지시의 «`WMS_OPERATOR` 를 auth 가 파생한 방식을 따르라» 는 전제는 **코드에 없다**: `WMS_OPERATOR` 는 파생되지 않고 카탈로그의 독립 항목(`wms-ops-actions` 프리셋, `DomainRoleCatalog.java:46-47`)이며, auth 는 `배정 ∩ 상한` 만 한다(`TenantClaimTokenCustomizer.java:992-1025`). WMS 게이트웨이도 SCM 게이트웨이도 진입 역할을 요구하지 않는다(`roleOrScope` = 아무 역할). 계약은 이것을 이 단계에 넘겨 두었다(`admin-api.md:3739` «세분 역할이 들어온 도메인의 진입 역할 파생은 그 도메인 단계(P5 ~ P8)가 정의한다»). ⇒ 정할 것: (a) auth 가 SCM 업무 역할 ≥1 이면 `SCM_OPERATOR` 를 덧붙인다(auth-service 코드 변경 — 이 PR 에 auth 가 들어온다) 또는 (b) 파생 없음 · `SCM_OPERATOR` 를 WMS_OPERATOR 처럼 «업무 API 를 열지 않는» 카탈로그 항목으로 남긴다. 어느 쪽이든 `isOperator()` 가 `SCM_OPERATOR` 에 기대므로(발주 확정 · 공급사 등록) (b) 면 `isOperator()` 에 `SCM_PROCUREMENT_WRITE` 를 더해야 확정이 산다.
- 🔴 **Q5 — 데이터: «SCM 전체» 로 만든 테넌트 세트.** BE-631 이후 관리자가 만든 테넌트 세트 중 `SCM_OPERATOR`(라벨 «SCM 전체 업무(세분 미지원)») 를 담은 것은, 세분 역할이 들어오는 순간 **SCM 업무 0** 이 된다. 티켓 범위는 `legacy-compat` · 프리셋 갱신만 적는다. ⇒ 마이그레이션이 `SCM_OPERATOR` 를 담은 **모든 세트**(프리셋 + 테넌트 세트)에 SCM 업무 역할 전부를 더할지. ⚪ 라이브 행 수는 못 쟀다(라이브 DB 미접속).

부수 사실(질문 아님, 기록):
- 기계 클라이언트 `scm-platform-internal-services-client`(범위 `scm.read/scm.write`, 역할 없음 — `WorkloadRoleCatalog.java:214`) 는 역할 판정이 들어오면 업무 API 403 이 된다. WMS 와 같은 «역할만» 모델이면 그것이 맞다. 데모 · e2e · 스크립트 사용처 0(`infra/` · `scripts/` · e2e grep 0). `ClientCredentialsActorOverflowIntegrationTest` 는 그 토큰을 직접 서비스에 쏘므로 역할을 더해 고쳐야 한다.
- `/scm/config` 화면은 매핑 편집 때 `GET /api/scm/procurement/suppliers`(발주 조회)를 부른다 — 수요계획 세트만으로는 공급사 선택 목록이 403(화면 안 «권한 없음»).
- 데모 시드(`seed-scm.sh`)는 운영자 토큰(assume demo-corp)으로 PO · 공급사 · 정책 · 매핑을 쓴다 — 시드 운영자는 `legacy-compat` 를 받으므로 세트 갱신이 같은 PR 이면 무변경.

## 4. 콘솔 · 배포 순서

- 계약 `console-integration-contract.md:304, 307` — 지금 «SCM leaves = `serverRequires` 없음(`authenticated()` only)», 그리고 **«P5~P8 에서 세분 판정이 들어오면 그 단계가 `serverRequires`(·`writeRequires`) 를 더한다 — 데이터만»**. 즉 서버를 바꾸면 콘솔 데이터(`permission-map.ts:684-740` SCM 6행 · § 2.4.3.4 표 · `:281` 발송 재시도 줄)도 바뀌어야 한다.
- 🔴 배포가 갈라진다: 콘솔 = 머지 즉시 Vercel · 백엔드(scm 4 + admin 마이그레이션 + (Q4-a 면) auth) = **다음 AMI 굽기**. 콘솔 데이터를 이 PR 에 같이 넣으면 머지 ~ 굽기 사이에 라이브 `/me/domain-access` 는 아직 `SCM_OPERATOR` 만 내므로 **모든 운영자의 SCM 메뉴에 거짓 «업무 권한 없음» 배지 + 쓰기 버튼 숨김**(승인 · 기각 · 정책/매핑 저장)이 된다 — 서버는 여전히 열어 주는데 화면이 닫는다.
- ⇒ 추천 순서: **이 PR = 백엔드 + 계약(scm · iam · 콘솔 계약 § 2.4.3.4 의 «예정» 문장)만**, 콘솔 `permission-map.ts` 데이터는 **굽기 라이브 확인 뒤 별도 콘솔 PR**. 그 사이(굽기 ~ 콘솔 PR)에는 조회 세트 운영자에게 쓰기 버튼이 보이고 누르면 서버 403(화면 안 «권한 없음») — 안전 쪽 실패(서버가 판정). 반대 순서는 화면이 서버보다 **좁게** 틀린다.

## 5. 질문과 추천 (소유자)

| Q | 질문 | 추천 (구현자 선호) |
|---|---|---|
| Q1 | 재고 가시성 변경 2개(3PL 노드 등록 · 관측 재고) | **`SCM_INVENTORY_VISIBILITY_WRITE` 신설**(OD-6 «조회만» 을 «조회 + 3PL 온보딩 변경» 으로 정정). 대안: 물류 변경에 묶기 · 조회 역할로 열기(세분 무력화 — 비추천) |
| Q2 | 승인 → 발주 초안 | **`POST /po/from-suggestion` = `SCM_PROCUREMENT_WRITE ∨ SCM_DEMAND_PLANNING_WRITE`**(초안만 만들고 확정 · 제출은 발주 역할 그대로). 대안: 승인에 두 역할 모두 요구(수요계획 세트만으로는 승인 불가) |
| Q3 | 물류 = WMS 출고 화면의 발송 재시도 | **SCM 역할 그대로(`SCM_LOGISTICS_READ/WRITE`)** — WMS 출고 운영자가 재시도하려면 SCM 물류 세트도 받는다. 콘솔 후속 PR 에서 그 버튼에 `writeRequires: [SCM_LOGISTICS_WRITE]`. 대안: 물류가 `OUTBOUND_WRITE` 도 받기(도메인 경계를 넘는 판정 — AC-3 와 충돌) |
| Q4 | 진입 역할 | **(a) auth 파생** — 업무 역할 ≥1 이면 auth 가 `SCM_OPERATOR` 를 덧붙이고, 카탈로그에서는 `SCM_OPERATOR` 를 **뺀다**(배정 불가 · 단독으로 의미 없음). `isOperator()` 무변경 · 발주 확정 · 공급사 등록은 URL 판정(`SCM_PROCUREMENT_WRITE`) ∧ 기존 판정. 🔴 auth-service 가 이 PR 에 들어온다 |
| Q5 | `SCM_OPERATOR` 를 담은 테넌트 세트 | **마이그레이션이 그 세트 전부에 SCM 업무 역할 전부를 더하고 `SCM_OPERATOR` 행은 지운다**(관리자가 고른 «SCM 전체» 의미 보존) |
| 순서 | 콘솔 데이터 | **별도 콘솔 PR, 굽기 라이브 뒤**(§ 4) |

역할 이름(추천, 계약에서 확정): `SCM_PROCUREMENT_READ` · `SCM_PROCUREMENT_WRITE` · `SCM_DEMAND_PLANNING_READ` · `SCM_DEMAND_PLANNING_WRITE` · `SCM_LOGISTICS_READ` · `SCM_LOGISTICS_WRITE` · `SCM_INVENTORY_VISIBILITY_READ` (+ Q1 이면 `_WRITE`). 프리셋 추천: `scm-read`(조회 전부) · `scm-procurement-operate` · `scm-demand-planning-operate` · `scm-logistics-operate` · `scm-inventory-visibility-read` · `scm-full` 확장 · `all-domains-full` · `legacy-compat` 확장.

Flyway: admin-service 다음 빈 번호 = **`V0056`**(base 기준). 🔴 `TASK-MONO-773` 이 같은 영역을 병행 중이면 충돌 가능 — 구현 착수 때 `scripts/check-flyway-version-collision.sh` 로 다시 본다.
