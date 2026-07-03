# TASK-FE-089 — web-store 헤더 알림 버튼 + 콘솔식 DetailHeader 통일 + 주문내역 스켈레톤 갱신

- **Status**: ready
- **Project**: ecommerce-microservices-platform
- **Service**: web-store
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (직접)

## Goal

콘솔(platform-console)과 웹스토어(web-store) 고객 화면의 상단/뒤로가기 affordance 를 일관되게 맞추고, 주문 내역 로딩 스켈레톤을 현행 카드 레이아웃에 맞춘다. 4개 UI 조정:

1. **헤더 알림 버튼** — 콘솔 상단에 알림 벨(`NotificationBell`)이 있는 것처럼, 웹스토어 헤더에도 알림 진입 버튼을 추가한다.
2. **알림 설정 뒤로가기** — `알림 설정` 페이지의 `← 알림 목록`(상단 좌측 ghost 링크)을 콘솔 `DetailHeader` 형식(제목 좌 + ghost 목록 버튼 우, 같은 행)으로 변경.
3. **주문 상세 뒤로가기** — `주문 상세` 페이지의 `← 주문내역`을 동일하게 콘솔 `DetailHeader` 형식으로 변경(상태 배지는 목록 버튼 좌측 action 슬롯).
4. **주문내역 스켈레톤** — 주문 내역 목록의 로딩 스켈레톤을 현행 `OrderCard` 2줄 레이아웃(날짜+상태 배지 / 상품명·총액)에 맞춰 갱신.

## Scope

**In scope** (web-store only, 순수 프레젠테이션):

1. `src/widgets/header/Header.tsx` — `ThemeToggle` 와 장바구니 사이에 알림 벨 `Link`(→ `/my/notifications`, `aria-label="알림"`, bell SVG, `iconButton` 클래스) 추가. 장바구니와 동일하게 `!isLoading && isAuthenticated` 게이트.
2. `src/shared/ui/DetailHeader.tsx` (신규) — 제목(좌) + 우측 정렬 ghost 뒤로 버튼(+ 선택적 `actions`) 을 렌더하는 공유 컴포넌트. 콘솔 `features/ecommerce-ops/components/DetailHeader.tsx` 레이아웃 미러. `shared/ui/index.ts` 에 export.
3. `src/features/notification/ui/NotificationSettings.tsx` — 상단 `BackLink` + `<h1>` 을 `DetailHeader title="알림 설정" backHref="/my/notifications" backLabel="알림 목록"` 로 교체.
4. `src/features/order/ui/OrderDetailView.tsx` — 상단 `BackLink` + 제목/배지 행을 `DetailHeader title="주문 상세" backHref="/my/orders" backLabel="주문내역" actions={<OrderStatusBadge/>}` 로 교체. 로딩 스켈레톤의 상단부도 동일 레이아웃(제목 + [배지·버튼])으로 조정.
5. `src/features/order/ui/OrderHistory.tsx` — 로딩 스켈레톤을 `OrderCard`(`.card`, 1줄=날짜+배지 / 2줄=상품·총액) 구조에 맞춰 재구성.
6. `src/__tests__/header.test.tsx` — 인증/비인증 시 알림 링크 표시/숨김 유닛 테스트 추가.

**Out of scope**: `NotificationDetail`(알림 상세 — 제목행에 채널 배지가 이미 있어 별도 레이아웃, 사용자 미요청) · `BackLink` 컴포넌트 자체(NotificationDetail 이 계속 사용) · 알림 데이터 모델(read/unread) · 알림 API · 미읽음 카운트 배지(백엔드 `NotificationSummary` 에 read 상태 없음 → 콘솔식 카운트 배지 불가, 배지 없는 아이콘 버튼으로 구현).

## Acceptance Criteria

- **AC-1 — 헤더 알림 버튼.** 로그인 상태에서 헤더에 `aria-label="알림"` 링크가 표시되고 `/my/notifications` 로 이동한다. 비로그인/로딩 중에는 숨겨진다(장바구니와 동일 게이트).
- **AC-2 — 알림 설정 DetailHeader.** `알림 설정` 페이지가 제목 좌측 + `알림 목록` ghost 버튼 우측(같은 행) 으로 렌더되고, 버튼은 `/my/notifications` 로 이동한다(← 화살표 제거).
- **AC-3 — 주문 상세 DetailHeader.** `주문 상세` 페이지가 제목 좌측 + [상태 배지 · `주문내역` ghost 버튼] 우측(같은 행) 으로 렌더되고, 버튼은 `/my/orders` 로 이동한다.
- **AC-4 — 주문내역 스켈레톤.** 주문 내역 로딩 시 스켈레톤이 `OrderCard` 와 동일한 카드형 2줄 구조(1줄=날짜+상태 자리, 2줄=상품·총액 자리)로 표시된다.
- **AC-5 — 게이트.** `pnpm lint` + `tsc --noEmit` GREEN(로컬 확인 완료). web-store 프런트 유닛(vitest) GREEN — 기존 `notification-settings`/`orders-page`/`order-ui`/`header` 테스트 통과 + header 알림 테스트 추가. ⚠️ 로컬 vitest 불가(Node24 ↔ vitest4 `#module-evaluator`) → CI Node20 권위.

## Related Specs

- `specs/services/web-store/architecture.md` — 헤더/주문·알림 화면(프레젠테이션 조정, 데이터 흐름 무변경).

## Related Contracts

- 없음(`NotificationSummary`/`OrderSummary` 타입·알림/주문 API 무변경, 순수 UI).

## Edge Cases

- 알림 벨: 비로그인/인증 로딩 중 → 미표시. 미읽음 카운트 배지 없음(백엔드 read 상태 부재).
- 주문 상세 DetailHeader: 상태 배지가 목록 버튼 좌측에 위치, 좁은 화면에서 `flex-wrap` 으로 줄바꿈.
- 알림 설정: 데이터 로딩/에러 상태에서도 DetailHeader(제목+버튼)는 항상 표시.
- 주문내역 스켈레톤: 로딩 상태 전용 — order-card/empty-state testid 를 렌더하지 않아 기존 로딩 테스트(0 카드) 보존.

## Failure Scenarios

- 콘솔식 미읽음 카운트 배지를 그대로 이식하면 `NotificationSummary` 에 read 상태가 없어 절대 clear 되지 않는 오해성 배지가 됨 → 배지 없는 순수 아이콘 버튼으로 구현.
- `DetailHeader` 뒤로 버튼의 접근명이 바뀌면 `notification-settings.test` 의 `getByText(/알림 목록/)` 이 깨질 수 있음 → backLabel="알림 목록" 그대로 유지(정규식 매칭 보존).
- 주문 스켈레톤에 order-card/empty-state testid 를 넣으면 `orders-page.test` 로딩 단언(0 카드)이 깨짐 → 스켈레톤은 순수 `.card`+`Skeleton` 만 사용.
