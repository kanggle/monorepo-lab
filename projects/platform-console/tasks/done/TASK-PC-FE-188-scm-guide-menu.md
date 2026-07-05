# TASK-PC-FE-188 — SCM 가이드 메뉴 신설 (도메인 서비스 정적 참조 화면)

**Status:** done
**Area:** platform-console / console-web · **Route:** `/scm/guide` (신규) · **Nav:** SCM drill 자식에 `가이드` 추가 (`nav-scm-guide`)
**Analysis model:** Opus 4.8 · **Impl model:** Opus 4.8 (다-서비스 도메인 상태머신·enum 정확도 요구 — 콘텐츠 집약)

---

## Goal

SCM 도메인 운영자에게 콘솔의 **3개 라이브 운영 화면**(개요=발주+재고 가시성 · 보충 · 설정)이 보여주는 값의 **의미와 상태머신**을 한 화면에서 설명하는 **정적 참조 가이드**를 신설한다. IAM 가이드(`/iam/guide`, `IamGuideScreen`, TASK-PC-FE-163/180) · WMS 가이드(`/wms/guide`, TASK-PC-FE-183) · E-Commerce 가이드(`/ecommerce/guide`, TASK-PC-FE-184)와 동일한 패턴 — 순수 server component, 데이터 페치·권한 게이트 없음(콘솔 진입자 누구나 열람), 콘텐츠는 `data.ts`로 분리하고 화면은 Card/table로 렌더.

배경: SCM nav는 현재 `개요 · 보충 · 설정` 3개 자식을 갖지만, 각 화면의 값(발주 9-상태 생명주기 · 재고 가시성 S5 경고·노드 staleness · 보충 추천 4-상태 + ADR-MONO-027 루프 · 재주문 정책/공급사 매핑 필드)과 그 뒤의 scm-platform 4개 마이크로서비스(gateway/procurement/inventory-visibility/demand-planning) 구성을 설명하는 곳이 없다. IAM·WMS·E-Commerce처럼 `가이드` 자식을 추가한다.

## Scope

**신규 feature `features/scm-guide/`** (iam-guide / wms-guide / ecommerce-guide 미러):
- `data.ts` — 타입 있는 정적 콘텐츠 배열: 도메인 서비스 맵(gateway/procurement/inventory-visibility/demand-planning-service), 발주(PO) 생명주기(DRAFT·SUBMITTED·ACKNOWLEDGED·CONFIRMED·PARTIALLY_RECEIVED·RECEIVED·SETTLED·CLOSED·CANCELED + 콘솔 읽기전용·보충승인이 DRAFT 생성), 재고 가시성(S5 경고 상시 부착 · staleness FRESH·STALE·UNREACHABLE · 노드 교차조회), 보충 추천(SUGGESTED·APPROVED·MATERIALIZED·DISMISSED, source ALERT, ADR-MONO-027 루프), 설정(재주문 정책 reorderPoint/safetyStock/reorderQty · 공급사 매핑 supplierId/defaultOrderQty/leadTimeDays/currency), 도메인 롤(단일 SCM_OPERATOR + 단일테넌트).
- `components/ScmGuideScreen.tsx` — 정적 렌더(server component, no `'use client'`). `data-testid="scm-guide"` 루트 + 섹션별 testid.
- `index.ts` — `export { ScmGuideScreen }`.

**신규 라우트** `app/(console)/scm/guide/page.tsx` → `<ScmGuideScreen />` (force-dynamic 불필요 — 정적).

**Nav** `shared/ui/ConsoleSidebarNav.tsx` — SCM `children`에 `{ href: '/scm/guide', label: '가이드', testid: 'nav-scm-guide' }`를 **개요 다음**(보충 앞)에 삽입 — IAM·WMS·E-Commerce의 개요→가이드 순서와 일치.

**Out of scope:** 3개 라이브 화면 로직 변경 없음. producer/contract 무변경. 권한 게이트 없음(가이드는 공개). 백엔드 무변경.

## Acceptance Criteria
- **AC-1** `/scm/guide` 진입 시 도메인 서비스·발주·재고 가시성·보충·설정 설명이 렌더된다(`scm-guide` testid). SCM nav를 열면 `가이드`(`nav-scm-guide`) 자식이 개요와 보충 사이에 보이고 클릭 시 `/scm/guide`로 이동, 딥링크 시 SCM drill이 자동 오픈되고 `가이드`가 active.
- **AC-2** 발주 섹션: 9-상태 생명주기(초안→…→입고→정산, 마감·취소 종료)와 **콘솔 발주 목록은 읽기 전용** · **보충 승인만이 DRAFT PO 를 생성** · 확정(confirm)은 roles∋OPERATOR 요구를 설명.
- **AC-3** 재고 가시성 섹션: **S5 경고 상시 부착**(발주 결정 근거 아님, 콘솔이 숨기지 않음) · 노드 staleness(FRESH/STALE/UNREACHABLE) · 다중 노드 교차 조회를 설명.
- **AC-4** 보충 섹션: 추천 4-상태(SUGGESTED/APPROVED/MATERIALIZED/DISMISSED) + source ALERT + **ADR-MONO-027 루프**(wms 저재고 알림 → 추천 → 승인 → DRAFT PO)를 설명. 설정 섹션: 재주문 정책·공급사 매핑 필드와 SKU 단위 upsert·404=미설정 빈 상태를 설명.
- **AC-5** 도메인 롤 섹션: 단일 `SCM_OPERATOR` + 단일테넌트(tenant_id∈{scm,*}, assume-tenant 필수)를 설명.
- **AC-6** `pnpm lint` + `tsc --noEmit` + `vitest run` green. 가이드 화면·nav 테스트 통과.

## Edge Cases / Failure Scenarios
- 가이드는 정적이라 백엔드 장애와 무관(페치 없음) — 어느 scm 서비스가 degrade여도 가이드는 항상 열림.
- nav 배열에 자식 1개 추가 → 기존 SCM drill 딥링크(보충/설정 active) 회귀 없어야 함(longest-match active 단언).
- enum/상태는 각 서비스 도메인 모델/계약을 SoT로 하며, 드리프트 시 가이드 카피를 동반 갱신(테스트는 구조만 단언, 설명 텍스트는 사람이 맞춤 — iam-guide/wms-guide/ecommerce-guide data.ts 동일 원칙). 콘솔 소비 타입(`features/scm-ops/*` · `features/scm-replenishment/api/types.ts` · `features/scm-config/api/types.ts`)이 이미 producer enum을 verbatim 반영하므로 그것을 2차 SoT로 참조.

## Related
- 미러 패턴: `features/iam-guide/`(TASK-PC-FE-163/180) · `features/wms-guide/`(TASK-PC-FE-183) · `features/ecommerce-guide/`(TASK-PC-FE-184) — 정적 참조 화면 + data.ts 분리.
- 콘솔 소비 타입(enum verbatim, 2차 SoT): `features/scm-ops/api/types.ts` + `components/scm-ops-helpers.ts`(PO status/staleness) · `features/scm-replenishment/api/types.ts`(suggestion status/source) · `features/scm-config/api/types.ts`(policy/supplier-map 필드).
- 도메인 SoT: `projects/scm-platform/specs/contracts/http/{procurement,inventory-visibility,demand-planning}-api.md` + `apps/{gateway,procurement,inventory-visibility,demand-planning}-service`.
- 연계 ADR: ADR-MONO-013(콘솔 federation Phase 4 slice 2 — scm) · ADR-MONO-027(수요계획·보충 루프) · ADR-MONO-032/035(roles-only 아이덴티티).
- 도메인 롤: auth-service `OperatorRoleDerivation`(assume-tenant 파생 — scm → 단일 SCM_OPERATOR).
