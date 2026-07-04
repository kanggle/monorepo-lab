# TASK-PC-FE-184 — E-Commerce 가이드 메뉴 신설 (도메인 서비스 정적 참조 화면)

**Status:** review
**Area:** platform-console / console-web · **Route:** `/ecommerce/guide` (신규) · **Nav:** E-Commerce drill 자식에 `가이드` 추가 (`nav-ecommerce-guide`)
**Analysis model:** Opus 4.8 · **Impl model:** Opus 4.8 (다-서비스 도메인 상태머신·enum 정확도 요구 — 콘텐츠 집약)

---

## Goal

E-Commerce 도메인 운영자에게 콘솔의 **7개 라이브 운영 화면**(상품·주문·배송·프로모션·사용자·셀러·알림)이 보여주는 값의 **의미와 상태머신**을 한 화면에서 설명하는 **정적 참조 가이드**를 신설한다. IAM 가이드(`/iam/guide`, `IamGuideScreen`, TASK-PC-FE-163/180) 및 WMS 가이드(`/wms/guide`, TASK-PC-FE-183)와 동일한 패턴 — 순수 server component, 데이터 페치·권한 게이트 없음(콘솔 진입자 누구나 열람), 콘텐츠는 `data.ts`로 분리하고 화면은 Card/table로 렌더.

배경: E-Commerce nav는 현재 `개요 · 상품 · 주문 · 배송 · 프로모션 · 사용자 · 셀러 · 알림` 8개 자식을 갖지만, 각 화면의 상태값(주문 6상태·배송 4단계 선형·상품 3상태·프로모션 3상태·셀러 4생명주기·사용자 3상태·알림 템플릿 타입/채널)과 그 뒤의 마이크로서비스 구성을 설명하는 곳이 없다. IAM·WMS처럼 `가이드` 자식을 추가한다.

## Scope

**신규 feature `features/ecommerce-guide/`** (iam-guide / wms-guide 미러):
- `data.ts` — 타입 있는 정적 콘텐츠 배열: 도메인 서비스 맵(product/order/payment/shipping/promotion/user/notification-service), 주문 상태머신(PENDING·CONFIRMED·SHIPPED·DELIVERED·CANCELLED·STUCK_RECOVERY_FAILED + 운영자 전이 vs 배송 return-leg 구동), 배송 선형 상태머신(PREPARING→SHIPPED→IN_TRANSIT→DELIVERED), 상품 상태(ON_SALE·SOLD_OUT·HIDDEN)+variant/재고, 프로모션(ACTIVE·SCHEDULED·ENDED, FIXED·PERCENTAGE, 쿠폰 발급), 셀러 생명주기(PENDING_PROVISIONING·ACTIVE·SUSPENDED·CLOSED), 사용자 상태(ACTIVE·SUSPENDED·WITHDRAWN, 읽기전용), 알림 템플릿(타입 4·채널 EMAIL/SMS/PUSH, type·channel 불변), 도메인 롤.
- `components/EcommerceGuideScreen.tsx` — 정적 렌더(server component, no `'use client'`). `data-testid="ecommerce-guide"` 루트 + 섹션별 testid.
- `index.ts` — `export { EcommerceGuideScreen }`.

**신규 라우트** `app/(console)/ecommerce/guide/page.tsx` → `<EcommerceGuideScreen />` (force-dynamic 불필요 — 정적).

**Nav** `shared/ui/ConsoleSidebarNav.tsx` — E-Commerce `children`에 `{ href: '/ecommerce/guide', label: '가이드', testid: 'nav-ecommerce-guide' }`를 **개요 다음**(상품 앞)에 삽입 — IAM·WMS의 개요→가이드 순서와 일치.

**Out of scope:** 7개 라이브 화면 로직 변경 없음. producer/contract 무변경. 권한 게이트 없음(가이드는 공개). 백엔드 무변경.

## Acceptance Criteria
- **AC-1** `/ecommerce/guide` 진입 시 도메인 서비스·주문·배송·상품·프로모션·셀러·사용자·알림 설명이 렌더된다(`ecommerce-guide` testid). E-Commerce nav를 열면 `가이드`(`nav-ecommerce-guide`) 자식이 개요와 상품 사이에 보이고 클릭 시 `/ecommerce/guide`로 이동, 딥링크 시 E-Commerce drill이 자동 오픈되고 `가이드`가 active.
- **AC-2** 주문 섹션: 6상태(정상 4단계 + 취소 + 복구실패)와 **운영자 전이(PENDING→CONFIRMED/CANCELLED, CONFIRMED→CANCELLED)** vs **배송 이벤트 구동(SHIPPED/DELIVERED는 read-only)** 구분을 설명.
- **AC-3** 배송 섹션: 선형 상태머신(준비중→발송→배송중→배송완료), SHIPPED 전이 시 carrier+trackingNumber 필수, WMS 라우팅(wmsRouted) 재고 차감 게이트를 설명.
- **AC-4** 상품·프로모션·셀러·사용자·알림 섹션이 각 enum과 핵심 개념(variant·쿠폰 발급·셀러 생명주기 액션·사용자 익명화·템플릿 불변 필드)을 설명.
- **AC-5** `pnpm lint` + `tsc --noEmit` + `vitest run` green. 가이드 화면·nav 테스트 통과.

## Edge Cases / Failure Scenarios
- 가이드는 정적이라 백엔드 장애와 무관(페치 없음) — 어느 도메인 서비스가 degrade여도 가이드는 항상 열림.
- nav 배열에 자식 1개 추가 → 기존 E-Commerce drill 딥링크(상품/주문/배송/… active) 회귀 없어야 함(longest-match active 단언).
- enum/상태는 각 서비스 도메인 모델을 SoT로 하며, 드리프트 시 가이드 카피를 동반 갱신(테스트는 구조만 단언, 설명 텍스트는 사람이 맞춤 — iam-guide/wms-guide data.ts 동일 원칙). 콘솔 소비 타입(`features/ecommerce-ops/api/*-types.ts`)이 이미 producer enum을 verbatim 반영하므로 그것을 2차 SoT로 참조.

## Related
- 미러 패턴: `features/iam-guide/`(TASK-PC-FE-163/180) · `features/wms-guide/`(TASK-PC-FE-183) — 정적 참조 화면 + data.ts 분리.
- 콘솔 소비 타입(enum verbatim, 2차 SoT): `features/ecommerce-ops/api/{types,order-types,shipping-types,seller-types,user-types,notification-types}.ts` + `components/shipping-labels.ts`.
- 도메인 SoT: `projects/ecommerce-microservices-platform/apps/{product,order,payment,shipping,promotion,user,notification}-service/` 도메인 모델·상태머신.
- 연계 ADR: ADR-MONO-031(콘솔 이커머스 흡수 Phase 1b~5b) · ADR-MONO-022(주문↔배송↔WMS 루프) · ADR-MONO-042(셀러 생명주기) · ADR-MONO-037(사용자 익명화).
- 도메인 롤: auth-service `OperatorRoleDerivation`(assume-tenant 파생 — ecommerce ADMIN).
