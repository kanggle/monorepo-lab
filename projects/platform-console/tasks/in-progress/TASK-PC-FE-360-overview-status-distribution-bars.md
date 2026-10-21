# Task ID

TASK-PC-FE-360

# Title

그래프 2차 묶음 ② — 이커머스 개요에 배송 상태 분포 · ERP 개요에 결재 상태 분포 막대 (상태별 총 건수 호출, 개요 화면에만)

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (이커머스 주문 상태 막대 — `TASK-PC-FE-357` `OrderStatusBar` — 와 같은 모양)
> 기안 2026-10-21 UTC — 콘솔 그래프 조사의 2차 묶음. 소유자 결정 2026-10-22 KST: **각 도메인 개요 화면에만**(목록 화면은 그대로 — 필터를 바꿀 때마다 호출이 늘지 않게).

# Dependency Markers

- 선행 없음. 같은 2차 묶음: `TASK-PC-FE-359`(독립). 선례: `TASK-PC-FE-357`(주문 상태 막대 · `shared/ui/InlineBar.tsx`).

# Goal

| # | 화면 | 더하는 것 | 데이터 (새 호출) |
|---|---|---|---|
| 1 | 이커머스 개요 `/ecommerce` | 배송 상태 4단계(`PREPARING` → `SHIPPED` → `IN_TRANSIT` → `DELIVERED`) 누적 막대 + 상태별 건수 | 상태마다 `listShippings({status, size:1}).totalElements` — **4 호출**. 주문 상태(`overview-state.ts:188-192`)와 같은 방식 |
| 2 | ERP 개요 `/erp` | 결재 상태 6종(`DRAFT` · `SUBMITTED` · `IN_REVIEW` · `APPROVED` · `REJECTED` · `WITHDRAWN`) 누적 막대 + 건수 | 상태마다 결재 목록 `status` 필터 `size:1` 의 `meta.totalElements` — **6 호출**. 지금 «결재 대기» 타일이 이미 하나를 부른다(재사용 가능하면 5) |

# Scope

## In Scope

- **AC-0 측정**: ① 배송 목록 API 의 `status` 필터 · `totalElements`(전체 모집단인가) ② 결재 목록 API 의 `status` 필터 · 범위(운영자의 데이터 범위 — 부서 서브트리 — 가 건수에 걸리는가: 걸리면 «내가 볼 수 있는 결재» 의 분포다, 그렇게 밝힌다) ③ 두 개요의 셀 저하 규칙(`cell()` · 403 · 401) ④ 샘플 고정값에 상태별 값이 있는가.
- 구현: 상태별 셀 — 하나라도 저하 · 권한 없음이면 막대 없음(357 `buildOrderStatusSegments` 와 같은 규칙: 일부만 그린 분포는 전체처럼 읽힌다) · 전부 0 → 빈 상태 문구 · 숫자 글자 · 색 견본 · 기존 testid 유지. 공용 `InlineBar` 재사용.
- 시험: 비율 · 하나 저하 · 전부 0 · 403 · 401(세션 전체 재로그인 — 형제와 같게) · 호출 수(4 · 6 — 그 이상 안 나간다).

## Out of Scope

- 목록 화면(`/ecommerce/shippings` · `/erp/approval`) 위의 막대(소유자 결정).
- 감사 로그 그래프(보류).

# Acceptance Criteria

- [ ] **AC-0** — 위 ①~④ 를 file:line 과 함께.
- [ ] **AC-1** — 구현 + **사용자에게 보이는 새 문구 목록을 소유자에게 보이고 승인** 받은 뒤 머지.
- [ ] **AC-2** — 시험 위 목록 + 기존 스위트(`--maxWorkers=4 --minWorkers=2`) · `tsc --noEmit` · `next lint` · `node scripts/check-client-graph-server-only.mjs` · `check-client-graph-backend-origins.mjs`(로컬, 스테이지 뒤) rc=0. SSR 이 바뀌므로 머지 전 브랜치 nightly 콘솔 잡.
- [ ] **AC-3** — 배포(Vercel Production 기록) · 익명 샘플 화면 확인.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` — 이커머스 개요 · ERP 개요 절(AC-0 에서 이름 확인 · 읽기 추가를 적는다)

# Edge Cases

- 결재 데이터 범위가 운영자마다 다르면 같은 회사에서도 분포가 다르게 보인다 — 정상, 밝힘 문구로.
- 알 수 없는 새 상태가 생기면 — 고정 목록만 센다(합계가 전체와 다를 수 있음을 시험 · 주석으로).

# Failure Scenarios

1. 상태 하나의 실패가 개요 전체를 저하시킨다 → 셀 독립 시험.
2. 호출이 상태 수보다 많이 나간다(재시도 · 중복) → 호출 수 단언.

## AC-0 측정 (2026-10-21 UTC)

**① 배송 목록 API** — 상태 필터 있음, `totalElements` 는 필터 뒤 건수.
- 생산자: `projects/ecommerce-microservices-platform/apps/shipping-service/src/main/java/com/example/shipping/interfaces/rest/controller/ShippingController.java:86-99` — `@RequestParam(required=false) String status`(`:91`) → `parseStatus`(`:95`, 모르는 값은 `InvalidShippingException`) → `listShippings(userRole, status, pageQuery)`. 계약 `ecommerce-microservices-platform/specs/contracts/http/shipping-api.md:86-111`(`status` 선택 필터 · `totalElements`).
- 콘솔 클라이언트: `src/features/ecommerce-ops/api/shippings-api.ts:100-117` `listShippings({status,page,size})` — `status` 를 쿼리에 싣고(`:105`) 응답은 `ShippingListSchema`(`shipping-types.ts:154-162`, `totalElements: z.number().nonnegative()`). 상태 값 4종 `SHIPPING_STATUS_VALUES`(`shipping-types.ts:30-35`), 색 `shippingStatusTone`(`:44-53`).
- 모집단: 테넌트 JWT 클레임(`shippings-api.ts:44-49`)에 묶인 전체 — 사용자 범위 없음.

**② ERP 결재 목록 API** — 상태 필터 있음. 범위: **운영자·플랫폼 행위자는 테넌트 전체, 그 외는 «내가 참여한 결재»**.
- 생산자: `projects/erp-platform/apps/approval-service/src/main/java/com/example/erp/approval/presentation/controller/ApprovalRequestController.java:64-78`(`status` · `role` · `page` · `size`) → `ApprovalApplicationService.java:280-298`: `role == null && (actor.isOperator() || actor.isPlatformScope())` 이면 `findAll(tenantId, status, …)`(`:284-286`, **테넌트 전체**), 아니면 호출자의 직원 id 가 상신자·승인자인 행만(`:287-296`, 연결된 직원 없으면 빈 페이지). **부서 서브트리 데이터 범위는 v1 에서 적용되지 않는다** — `approval-api.md:182-188`(`DATA_SCOPE_FORBIDDEN` 예약만, 미발행). `IN_REVIEW` 필터 가능: `parseStatus` 가 enum 이름 그대로(DB CHECK `V2__multi_stage_routing.sql:22`에 6종), 계약 쿼리 열거(`approval-api.md:357`)는 `IN_REVIEW` 를 빠뜨렸지만 v2.0 개정(`:28-30`)이 상태를 추가했다 — 계약 문구 낡음, 생산자는 받는다.
- 콘솔 클라이언트: `src/features/erp-ops/api/approval-reads.ts:32-42` `listApprovalRequests({status,…})` · `approval-call.ts:134-140` `listQs`(`status`). 6종 `APPROVAL_STATUSES`(`approval-types.ts:42-50`), 색 `approvalStatusTone`(`:62-73`).
- **«결재 대기» 호출 재사용 불가** — 그 타일은 `listApprovalInbox`(`erp-ops/api/overview-state.ts:137`, `/inbox` = 호출자 본인 큐: 현재 단계 승인자 == 호출자 직원)이고 `/requests?status=SUBMITTED` 는 다른 모집단(테넌트 전체 SUBMITTED). 그래서 **6 호출 전부 새로** (개요 호출 총 7 → 13). 콘솔 운영자는 `ERP_OPERATOR` 류 역할이면 테넌트 전체를 보지만, 아니면 «내가 참여한 결재» 분포다 → 막대 바로 위 안내 문구로 밝힘.

**③ 셀 저하 규칙** — 두 개요 모두 같은 `cell()`: 성공 `ok`, `ApiError 403` → `forbidden`, 그 외 → `degraded`, **401 은 재던져** 최상위 catch 가 `redirect('/login?error=session_expired')`.
- 이커머스: `ecommerce-ops/api/overview-state.ts:153-165`(cell) · `:272-278`(401). 주문 상태 선례 `:188-192`, 변환 `:228-232`, 막대 규칙 `OrderStatusBar.tsx:15-27`(하나라도 비`ok` · `count null` → 막대 없음).
- ERP: `erp-ops/api/overview-state.ts:84-96`(cell) · `:167-173`(401) · `countOf` `:102-109`(`meta.totalElements` 없으면 페이지 길이).

**④ 샘플 고정값** — 상태별 값 있음, 픽스처가 `status` 쿼리를 이미 거른다(수정 불필요).
- 배송 `src/shared/sample/fixtures/ecommerce.ts:776-820`(행 3), 필터 `:828-834`, `totalElements = rows.length`(`:79`): PREPARING 1 · SHIPPED 1 · IN_TRANSIT 0 · DELIVERED 1 (합 3 = `SHIPPINGS_SUMMARY.total` `:822`).
- 결재 `src/shared/sample/fixtures/erp.ts:726-818`(행 5), 필터 `:872-878`: DRAFT 0 · SUBMITTED 2 · IN_REVIEW 1 · APPROVED 1 · REJECTED 1 · WITHDRAWN 0 (합 5). 결재 대기(inbox) 타일은 2 로 SUBMITTED 와 우연히 같다(`:849-861`).

**계약 선(先)수정** — `console-integration-contract.md` § 2.4.10.6(이커머스 개요: «Shipping-status distribution» 항목 추가), § 2.4.8.1(ERP 개요: «결재 상태 분포» 항목 + 독립 저하 문장에 6 호출 반영). 코드 앞 별도 커밋.
