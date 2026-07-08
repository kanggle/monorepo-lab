# TASK-PC-FE-221 — 이커머스 정산(settlement) 운영자 콘솔 표면 신설

**Status:** ready
**Area:** platform-console / console-web · **Type:** feature (new domain surface)
**Analysis model:** Opus 4.8 · **Impl model:** Opus 4.8 (다운스트림 계약 정합 + 운영자 write 게이팅 — 복잡 도메인 배선)

---

## Goal

이커머스 백엔드 `settlement-service`는 완전 구현되어 게이트웨이에 라우팅(`Path=/api/admin/settlements/**`)되고 운영자용(ECOMMERCE_OPERATOR) REST 9개를 노출하지만, **platform-console에 대응 화면·메뉴·프록시·api 클라이언트가 전혀 없다.** 상품/주문/사용자/셀러/프로모션/배송/알림 7개 도메인은 모두 콘솔 표면이 있는데 정산만 비어 있다 — `features/ecommerce-guide/data.ts`가 `settlement-service … console: '—'` 및 "settlement·review·search 는 도메인에는 있으나 콘솔 표면에는 아직 없다"로 이 갭을 명시 문서화하고 있다.

기존 `ecommerce-ops` 도메인 화면과 동일한 패턴(사이드바 drill-in 자식 메뉴 → `/ecommerce/settlements` 라우트 → 프록시 → shared `callEcommerceGateway` core 소비 → 상태 매퍼 → 화면)으로 정산 운영자 표면을 신설한다. review·search는 고객 중심 기능이라 이 태스크 범위 밖(콘솔 미노출이 정상).

## Scope

기존 이커머스 화면 신설 선례(PC-FE-156 개요·products/orders/users 화면군)와 컨벤션(`[[proj_console_ecommerce_detail_conventions]]` DetailHeader ghost·dl 순서 명칭→상태→식별자→날짜, `[[proj_console_datetime_format_convention]]` `formatDateTime`)을 그대로 따른다.

**Phase A — 조회 표면 + 메뉴 + 가이드 (필수, 우선)**
- **사이드바 메뉴**: `src/shared/ui/ConsoleSidebarNav.tsx`의 `ecommerce` 그룹 `children`에 `정산` 항목 추가(`href: '/ecommerce/settlements'`, `testid: 'nav-ecommerce-settlements'`). 위치는 `셀러` 뒤·`알림` 앞(운영 도메인 → 정산 → 알림 흐름).
- **라우트/화면**: `app/(console)/ecommerce/settlements/page.tsx` → `SettlementsScreen`. 두 섹션 구성:
  - **정산 라인/잔액**: `GET /api/admin/settlements/accruals`(sellerId/orderId 필터·페이징) 리스트 + `GET /api/admin/settlements/sellers/{sellerId}/balance` 셀러별 잔액 조회. append-only ledger 성격 명시.
  - **수수료율 조회**: `GET /api/admin/settlements/commission-rates/{sellerId}`(bps 표시).
  - **정산 기간/지급**: `GET /api/admin/settlements/periods` 목록 + 기간 상세(`settlements/periods/[id]`)에서 `GET /api/admin/settlements/periods/{periodId}/payouts` payout 행 조회.
- **프록시**: `app/api/ecommerce/settlements/**` route handler(기존 `app/api/ecommerce/<x>` 선례대로 — accruals·sellers/[id]/balance·commission-rates/[id]·periods·periods/[id]/payouts).
- **api 클라이언트**: `features/ecommerce-ops/api/settlements-api.ts` — **shared `callEcommerceGateway`(PC-FE-213 `shared/api/ecommerce-gateway.ts`) 재사용**. settlement 경로도 동일 게이트웨이(`getDomainFacingToken`·FLAT 엔벨로프·admin subtree base) 경유이므로 신규 core 금지. 상태 매퍼(`settlements-state.ts`)로 seeded/loading/forbidden/degraded 분기(기존 화면 선례).
- **가이드 갱신**: `features/ecommerce-guide/data.ts` — settlement 행 `console: '—'` → `/ecommerce/settlements` 로 변경, "settlement·review·search 는 … 콘솔 표면에는 아직 없다" 문장에서 **settlement 제거**(review·search만 잔존).

**Phase B — 운영자 변이(write) (Phase A 위에)**
- **수수료율 설정**: `PUT /api/admin/settlements/commission-rates/{sellerId}`(bps 입력 폼 + confirm).
- **정산 기간 생명주기**: `POST /api/admin/settlements/periods`(개시, 201) · `POST …/{periodId}/close`(OPEN→CLOSED, confirm — accrual을 PENDING payout으로 접고 `settlement.period.closed.v1` 발행하는 비가역 전이).
- **시뮬레이션 지급**: `POST …/{periodId}/payouts/execute`(PENDING→PAID/FAILED, CLOSED 아니면 409 — confirm + 상태 가드).
- 모든 변이는 기존 이커머스 변이 화면 컨벤션(ConfirmDialog reason/확인 게이팅) 준수. accrual 쓰기 경로는 **의도적으로 없음**(이벤트 스트림에서만 적립) — UI에서 수동 적립 노출 금지.

> **구현 PR 분할 권장**: Phase A(조회+메뉴+가이드) → Phase B(변이) 순으로 2 PR 랜딩 권장. 규모가 크면 Phase B를 후속 태스크로 분리해도 무방(그 경우 이 태스크는 Phase A로 review 이동 + 후속 태스크가 원 태스크 ID 참조).

**Out of scope:** 백엔드 로직·계약·이벤트·producer 무변경(전부 done). review-service·search-service 콘솔 표면(고객 중심, 별건). console-bff outbound(정산은 게이트웨이 직접 프록시 — 기존 이커머스 화면과 동일하게 console-bff 미경유).

## Acceptance Criteria
- **AC-1** 사이드바 `E-Commerce` 드릴에 `정산`(`nav-ecommerce-settlements`) 노출, `/ecommerce/settlements` 진입·활성 하이라이트·딥링크 자동 오픈 동작(기존 `parentKeyForPath`/`matchesRoute` 로직 그대로).
- **AC-2** (Phase A) accruals 리스트·셀러 잔액·수수료율·기간 목록·payout 목록이 실제 게이트웨이 응답으로 렌더. seeded/empty/forbidden(비-ECOMMERCE_OPERATOR)/degraded(503/timeout) 4-상태 분기 존재.
- **AC-3** api 클라이언트가 shared `callEcommerceGateway` 소비(신규 call-core 미생성), `getDomainFacingToken` 사용·`getOperatorToken`/`getAccessToken` 미호출·admin subtree base·FLAT 엔벨로프 파싱(기존 8 slice와 동형).
- **AC-4** (Phase B) 수수료율 PUT·기간 open/close·payout execute가 confirm 게이팅으로 동작, CLOSED-아님 payout execute의 **409를 사용자 메시지로 정확히 표면화**(가짜 500/degraded 아님).
- **AC-5** 가이드(`ecommerce-guide/data.ts`)에서 settlement가 `console: '—'` → 실 라우트로 갱신되고 "콘솔 표면에 아직 없다" 목록에서 제거됨.
- **AC-6** 날짜·금액 표기 컨벤션 준수(`formatDateTime`/`formatDate`, ₩·bps 표기), 상세/헤더 컨벤션(DetailHeader ghost·dl 순서) 준수.
- **AC-7** `tsc --noEmit` + `pnpm lint` + `vitest`(settlements-api·proxy·state·screen 신규 테스트 포함) green. **`pnpm lint` 필수**(`[[env_console_web_local_verify_needs_lint]]` — no-unused-vars가 tsc/vitest에 안 잡힘).

## Related Specs
- `projects/ecommerce-microservices-platform/specs/features/marketplace-settlement.md` (정산 피처 SoT — accrual/기간/payout/수수료율)
- `projects/ecommerce-microservices-platform/specs/services/settlement-service/overview.md`
- 화면 컨벤션: `[[proj_console_ecommerce_detail_conventions]]`, `[[proj_console_datetime_format_convention]]`

## Related Contracts
- `projects/ecommerce-microservices-platform/specs/contracts/http/settlement-api.md` (운영자 REST 9개 — 이 화면이 소비)
- `projects/ecommerce-microservices-platform/specs/contracts/events/settlement-events.md` (`settlement.period.closed.v1` — close 변이 부수효과 이해용, 콘솔 직접 소비 아님)
- 게이트웨이 라우팅: `gateway-service/src/main/resources/application.yml` route id `settlement-service`(`Path=/api/admin/settlements/**`)

## Edge Cases / Failure Scenarios
- **권한**: 전 엔드포인트 `X-User-Role ∋ ECOMMERCE_OPERATOR`. assume-tenant로 이커머스 스코프 진입한 운영자만 접근 → 미보유 시 403을 forbidden-상태로(가짜 degraded 아님). `[[env_console_wms_outbound_403_assume_tenant]]` 유사 함정 주의.
- **payout execute 409**: 기간이 CLOSED가 아니면 백엔드 409 → "기간 마감 후 지급 가능" 류 명시 메시지. BFF/프록시가 non-2xx 본문을 삼켜 500으로 변질시키지 않도록(`[[env_bff_proxy_null_body_status_500]]` 계열 — null-body-status 주의).
- **기간 close 비가역**: OPEN→CLOSED는 accrual을 payout으로 접는 비가역 전이 → confirm 문구에 명시.
- **cold-start 타임아웃**: 재배포 직후 cold-JVM에서 조회가 5s 초과 가능 → 데모 오버레이 타임아웃 예산 확인(`[[env_console_cold_start_timeout_cascade]]` 계열, 필요 시 override).
- **accrual write 부재**: REST에 적립 쓰기 경로 없음(이벤트 전용) — UI에 수동 적립 버튼 만들지 말 것.

## Related
- 원본 갭 발견: 2026-07-08 이커머스 기능↔콘솔 메뉴 배치 감사(운영자 백엔드 완비·콘솔 표면만 공백 = REAL-GAP, module-liveness+live-sibling+grep 3중 검증 완료).
- 백엔드 선행(전부 done): TASK-BE-365(commission)·BE-415(period close+outbox)·BE-416(simulated payout)·BE-425(부분환불 비례 clawback)·BE-447(outbox v2).
- shared core 선례: TASK-PC-FE-213(ecommerce-gateway 승격 — 이 화면이 그 core 소비).
- 화면 신설 선례: 기존 `ecommerce-ops` products/orders/users/sellers 화면군.
