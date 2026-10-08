/**
 * E-Commerce 가이드 화면의 정적 참조 데이터 (TASK-PC-FE-184).
 *
 * E-Commerce 콘솔의 8개 라이브 운영 화면 — **상품(`/ecommerce/products`)·
 * 주문(`/ecommerce/orders`)·배송(`/ecommerce/shippings`)·프로모션
 * (`/ecommerce/promotions`)·사용자(`/ecommerce/users`)·셀러(`/ecommerce/sellers`)·
 * 정산(`/ecommerce/settlements`)·알림(`/ecommerce/notifications/templates`)** —
 * 이 실제로 보여주는 상태값의
 * 의미와, 그 뒤의 이커머스 마이크로서비스 구성을 운영자에게 설명한다. IAM
 * 가이드(`features/iam-guide/data.ts`, TASK-PC-FE-163)·WMS 가이드
 * (`features/wms-guide/data.ts`, TASK-PC-FE-183)와 같은 원칙: 타입 있는 정적
 * 배열 + 정적 화면. 데이터 페치·권한 게이트 없음(콘솔 진입자 누구나 열람).
 *
 * 🔵 **문장은 처음 보는 사람용이다**(TASK-PC-FE-323) — 쉬운 말, 카드/행 하나에
 * 한두 문장. 티켓 번호·ADR 번호·파일 경로·「출처」 표시는 화면 문장에 쓰지 않는다
 * (`tests/unit/domain-guides-plain.test.tsx` 가 검사한다).
 *
 * **SoT** (드리프트 시 이 파일 카피도 동반 갱신):
 *   - 도메인 enum/상태머신: `projects/ecommerce-microservices-platform/apps/
 *     {product,order,payment,shipping,promotion,user,notification}-service`
 *     도메인 모델(`ProductStatus`·`OrderStatus`·`PaymentStatus`·`ShippingStatus`·
 *     `PromotionStatus`·`SellerStatus` …).
 *   - 콘솔 소비 타입(producer enum verbatim 반영 — 2차 SoT):
 *     `features/ecommerce-ops/api/{types,order-types,shipping-types,seller-types,
 *     user-types,notification-types}.ts`.
 *   - 도메인 롤: auth-service `OperatorRoleDerivation`(assume-tenant 파생 — ecommerce ECOMMERCE_OPERATOR).
 *
 * 테스트(EcommerceGuideScreen.test.tsx)는 섹션/행 존재 등 **구조만** 단언하며,
 * 설명 텍스트 자체는 사람이 스펙과 맞춘다(iam-guide/wms-guide data.ts 동일 정책).
 */

import type {
  GlossaryEntry,
  GuideRecipeData,
} from '@/shared/ui/guide-primitives';

// ───────────────────────── 도메인 서비스 맵 ─────────────────────────

/** 이커머스 마이크로서비스 1개. */
export interface DomainService {
  key: string;
  /** 서비스명(참조용). */
  name: string;
  /** 바운디드 컨텍스트(한글). */
  context: string;
  /** 한 줄 책임. */
  desc: string;
  /** 이 서비스를 소비하는 콘솔 화면(없으면 '—'). */
  console: string;
}

/**
 * E-Commerce 도메인은 여러 마이크로서비스로 분리된 이벤트 기반 시스템이다. 콘솔
 * 은 이 중 8개 서비스의 운영자 API를 호출해 화면을 렌더한다(상품·주문·배송·
 * 프로모션·사용자·셀러·정산·알림). review·search 는 도메인에는 있으나 콘솔
 * 표면에는 아직 없다.
 */
export const DOMAIN_SERVICES: DomainService[] = [
  {
    key: 'product',
    name: 'product-service',
    context: '상품 · 재고 · 셀러',
    desc: '상품을 등록·수정·삭제하고, 옵션(variant)·카테고리·가격·재고와 셀러 정보를 관리합니다.',
    console: '상품 · 셀러',
  },
  {
    key: 'order',
    name: 'order-service',
    context: '주문',
    desc: '주문이 접수되고 끝날 때까지 전체 흐름을 관리합니다. 결제 결과에 따라 주문 상태가 바뀝니다.',
    console: '주문',
  },
  {
    key: 'payment',
    name: 'payment-service',
    context: '결제',
    desc: '주문이 들어오면 결제를 만들고 Toss Payments 로 승인·환불을 처리합니다. 전용 화면은 없고 주문 상태로 결과가 보입니다.',
    console: '— (주문에 반영)',
  },
  {
    key: 'shipping',
    name: 'shipping-service',
    context: '배송',
    desc: '주문이 확정되면 배송을 만들고, 정해진 순서대로만 진행되게 하며 운송장 배송 현황을 갱신합니다.',
    console: '배송',
  },
  {
    key: 'promotion',
    name: 'promotion-service',
    context: '프로모션 · 쿠폰',
    desc: '프로모션을 등록·관리하고 쿠폰을 발급하며 할인 금액을 계산합니다.',
    console: '프로모션',
  },
  {
    key: 'user',
    name: 'user-service',
    context: '사용자 프로필',
    desc: '회원 가입 시 기본 프로필을 만들고, 프로필·배송지를 관리하며 탈퇴 시 정보를 처리합니다.',
    console: '사용자',
  },
  {
    key: 'notification',
    name: 'notification-service',
    context: '알림',
    desc: '주문·결제·배송·가입 알림을 이메일·SMS·푸시로 보냅니다. 알림 템플릿과 수신 설정을 관리합니다.',
    console: '알림',
  },
  {
    key: 'settlement',
    name: 'settlement-service',
    context: '정산',
    desc: '셀러별로 수수료를 쌓아두고, 정해진 기간이 끝나면 정산금을 지급합니다.',
    console: '정산',
  },
  {
    key: 'review',
    name: 'review-service',
    context: '리뷰 · 평점',
    desc: '구매한 사람만 쓸 수 있는 리뷰를 관리하고 평균 평점을 계산합니다.',
    console: '—',
  },
  {
    key: 'search',
    name: 'search-service',
    context: '검색 · 색인',
    desc: '상품·리뷰 정보를 모아 검색할 수 있게 합니다.',
    console: '—',
  },
];

// ───────────────────────── 주문 (Order) ─────────────────────────

/** 주문(Order) 애그리거트 상태. */
export interface OrderState {
  name: string;
  label: string;
  terminal: boolean;
  /** 운영자가 이 상태에서 직접 전이시킬 수 있는가(콘솔 상태변경 다이얼로그). */
  operatorActionable: boolean;
  desc: string;
}

/**
 * 콘솔 주문 상태(6). 정상 경로 `대기(PENDING) → 확정(CONFIRMED) → 배송중
 * (SHIPPED) → 배송완료(DELIVERED)` + 예외 종료 2개(취소 · 복구실패). 콘솔이
 * 렌더하는 enum(`ORDER_STATUS_VALUES`)은 백엔드의 `BACKORDERED`(재고부족 이월,
 * 결제 후 예약 실패 시)를 운영자-선택 상태로 노출하지 않는다 — READ_NOTE 참조.
 *
 * **핵심 구분**(order-types.ts 헤더): SHIPPED/DELIVERED 는 **운영자가 못 바꾼다**.
 * 오직 배송 서비스의 return-leg(`ShippingStatusChanged`)가 구동하며 상세에 읽기
 * 전용으로 표시된다(운영자가 SHIPPED/DELIVERED 를 시도하면 producer 가 400).
 * 운영자 가능 전이는 `대기→{확정,취소}`, `확정→{취소}` 뿐이다.
 */
export const ORDER_STATES: OrderState[] = [
  {
    name: 'PENDING',
    label: '대기',
    terminal: false,
    operatorActionable: true,
    desc: '주문이 접수되어 결제를 기다리는 상태입니다. 결제가 끝나면 자동으로 확정됩니다. 운영자는 확정하거나 취소할 수 있습니다.',
  },
  {
    name: 'CONFIRMED',
    label: '확정',
    terminal: false,
    operatorActionable: true,
    desc: '결제가 끝나 확정된 상태입니다. 배송이 자동으로 준비됩니다. 운영자는 취소만 할 수 있고 대기 상태로 되돌릴 수는 없습니다.',
  },
  {
    name: 'SHIPPED',
    label: '배송중',
    terminal: false,
    operatorActionable: false,
    desc: '배송이 발송되어 운송 중인 상태입니다. 배송 쪽 상태가 바뀌면서 자동으로 바뀌며, 운영자가 직접 바꿀 수 없습니다.',
  },
  {
    name: 'DELIVERED',
    label: '배송완료',
    terminal: true,
    operatorActionable: false,
    desc: '배송이 완료된 상태입니다. 배송이 끝나면 자동으로 바뀌며, 더 이상 바뀌지 않습니다.',
  },
  {
    name: 'CANCELLED',
    label: '취소',
    terminal: true,
    operatorActionable: false,
    desc: '주문이 취소된 상태입니다(운영자 · 구매자 · 결제 시간 초과로 취소될 수 있습니다). 결제가 이미 이뤄졌다면 환불됩니다.',
  },
  {
    name: 'STUCK_RECOVERY_FAILED',
    label: '복구실패',
    terminal: true,
    operatorActionable: false,
    desc: '결제가 끝나지 않은 주문을 자동으로 취소하는 과정에서 문제가 생겨 멈춘 상태입니다. 정상적으로는 거의 생기지 않습니다.',
  },
];

/**
 * 결제 → 확정 흐름 + BACKORDERED 안내. 콘솔 주문 enum 이 노출하지 않는 백엔드
 * 상태의 존재를 설명한다.
 */
export const ORDER_LIFECYCLE_NOTE = {
  title: '결제가 끝나면 자동으로 확정됩니다 (재고부족 이월: BACKORDERED)',
  body: '주문은 결제가 끝나야 확정됩니다. 결제가 승인되면 주문이 자동으로 확정(CONFIRMED)되고, 이때 재고를 확보합니다. 재고가 부족하면 주문은 재고부족 이월(BACKORDERED) 상태가 되어 재고를 차감하지 않고 기다립니다 — 다시 입고되면 먼저 들어온 주문부터 확정됩니다. 이 상태는 취소할 수 있지만 콘솔 화면의 상태 목록에는 나타나지 않고, 조회로만 확인할 수 있습니다.',
} as const;

// ───────────────────────── 결제 (Payment) ─────────────────────────

/** 결제(Payment) 상태 — 콘솔 전용 화면은 없고 주문 상태로 간접 노출. */
export interface PaymentState {
  name: string;
  label: string;
  desc: string;
}

/**
 * 결제 상태머신(`PaymentStatus`, 6). Toss Payments PG 를 `PgGatewayPort`(서킷
 * 브레이커·재시도·벌크헤드) 뒤에서 호출한다. 4xx PG → FAILED(재시도 없음),
 * 5xx/타임아웃/CB-OPEN → 상태 보존 재시도.
 */
export const PAYMENT_STATES: PaymentState[] = [
  {
    name: 'PENDING',
    label: '대기',
    desc: '주문이 들어오면 만들어지는, 아직 승인되지 않은 결제입니다. 승인을 기다리는 상태입니다.',
  },
  {
    name: 'COMPLETED',
    label: '완료',
    desc: '결제 승인이 끝나 주문이 확정됩니다. 환불은 이 상태에서만 가능합니다.',
  },
  {
    name: 'FAILED',
    label: '실패',
    desc: '결제가 거절된 상태입니다. 다시 시도하지 않으며 주문은 취소로 이어집니다.',
  },
  {
    name: 'PARTIALLY_REFUNDED',
    label: '부분환불',
    desc: '결제 금액의 일부만 환불된 상태입니다.',
  },
  {
    name: 'REFUNDED',
    label: '환불완료',
    desc: '결제 금액 전체가 환불된 상태입니다. 더 이상 바뀌지 않습니다.',
  },
  {
    name: 'VOIDED',
    label: '보이드',
    desc: '아직 돈이 빠져나가지 않은 결제가 주문 취소와 함께 취소된 상태입니다. 환불할 금액이 없습니다. 더 이상 바뀌지 않습니다.',
  },
];

// ───────────────────────── 배송 (Shipping) ─────────────────────────

/** 배송(Shipping) 상태 — 엄격 선형 단일 후속. */
export interface ShippingState {
  name: string;
  label: string;
  terminal: boolean;
  desc: string;
}

/**
 * 배송 상태머신(`ShippingStatus`, 4) — **엄격 선형**: 각 상태는 후속이 하나뿐.
 * `준비중 → 발송 → 배송중 → 배송완료`. 배송완료가 종료. 준비중→발송 전이는
 * 운송사(carrier)+운송장번호(trackingNumber)가 필수(없으면 producer 400). 콘솔은
 * 이 한 방향 전이만 노출한다.
 */
export const SHIPPING_STATES: ShippingState[] = [
  {
    name: 'PREPARING',
    label: '준비중',
    terminal: false,
    desc: '주문이 확정되면 만들어지는 배송입니다. 발송을 기다리는 상태입니다.',
  },
  {
    name: 'SHIPPED',
    label: '발송',
    terminal: false,
    desc: '발송된 상태입니다. 발송 처리에는 운송사와 운송장번호가 반드시 필요합니다. 주문 상태도 함께 배송중으로 바뀝니다.',
  },
  {
    name: 'IN_TRANSIT',
    label: '배송중',
    terminal: false,
    desc: '운송 중인 상태입니다. 운송장 조회로 최신 상태를 확인할 수 있습니다.',
  },
  {
    name: 'DELIVERED',
    label: '배송완료',
    terminal: true,
    desc: '수령이 완료된 상태입니다. 주문 상태도 함께 배송완료로 바뀝니다. 더 이상 바뀌지 않습니다.',
  },
];

/**
 * WMS 연계(ADR-MONO-022) 안내 — 이커머스 배송이 WMS 풀필먼트로 라우팅된 경우.
 */
export const SHIPPING_WMS_NOTE = {
  title: 'WMS 창고와 연결된 배송의 재고 차감',
  body: '주문이 WMS 창고를 거쳐 가는 경우, 배송 목록에 "WMS 재고 차감" 토글이 나타납니다. 발송 처리를 할 때 이 토글을 켜면 WMS 창고에서 실제 재고가 차감됩니다. 운송사·운송장번호가 비어 있으면 화면에는 "—" 로 표시됩니다.',
} as const;

// ───────────────────────── 상품 (Product) ─────────────────────────

/** 상품(Product) 판매 상태(`ProductStatus`, 3). */
export interface ProductState {
  name: string;
  label: string;
  desc: string;
}

export const PRODUCT_STATES: ProductState[] = [
  {
    name: 'ON_SALE',
    label: '판매중',
    desc: '판매 중인 정상 상태입니다.',
  },
  {
    name: 'SOLD_OUT',
    label: '품절',
    desc: '재고가 다 팔린 상태입니다. 재고를 다시 채우면 판매중으로 돌아갑니다.',
  },
  {
    name: 'HIDDEN',
    label: '숨김',
    desc: '판매 목록에서 숨겨진 상태입니다.',
  },
];

/**
 * 상품 핵심 개념(콘솔 상품 화면이 다루는 것). SoT: product-service +
 * `features/ecommerce-ops/api/types.ts`(product 섹션).
 */
export interface ProductConcept {
  key: string;
  term: string;
  desc: string;
}

export const PRODUCT_CONCEPTS: ProductConcept[] = [
  {
    key: 'variant',
    term: 'variant (옵션)',
    desc: '색상·사이즈 같은 상품의 옵션 단위입니다. 상품을 등록할 때 최소 1개가 필요합니다. 옵션 이름과 추가금은 수정할 수 있지만, 재고는 따로 조정합니다.',
  },
  {
    key: 'stock',
    term: '재고 조정',
    desc: '재고는 옵션(variant)별로 따로 조정합니다. 늘리거나 줄일 때마다 사유를 함께 적어야 하고, 재고가 음수가 될 수는 없습니다.',
  },
  {
    key: 'image',
    term: '이미지',
    desc: '상품 이미지 목록입니다. 보여주는 순서를 정하고 대표 이미지를 지정할 수 있습니다.',
  },
  {
    key: 'seller',
    term: '셀러 소유',
    desc: '상품은 등록한 셀러에게 귀속되며, 판매되면 그 셀러에게 수수료가 쌓입니다.',
  },
];

// ───────────────────────── 프로모션 (Promotion) ─────────────────────────

/** 프로모션(Promotion) 상태(`PromotionStatus`, 3) — 기간으로 파생. */
export interface PromotionState {
  name: string;
  label: string;
  desc: string;
}

/**
 * 프로모션 상태는 저장값이 아니라 시작/종료일과 현재시각으로 파생된다
 * (`resolve`): now<start → 예정, now>end → 종료, 그 사이 → 진행중.
 */
export const PROMOTION_STATES: PromotionState[] = [
  {
    name: 'SCHEDULED',
    label: '예정',
    desc: '시작일 이전입니다. 아직 적용되지 않습니다.',
  },
  {
    name: 'ACTIVE',
    label: '진행중',
    desc: '시작일과 종료일 사이입니다. 할인 적용과 쿠폰 발급이 가능합니다.',
  },
  {
    name: 'ENDED',
    label: '종료',
    desc: '종료일이 지났습니다. 끝난 프로모션입니다.',
  },
];

/** 할인 종류(`discountType`, 2). */
export const DISCOUNT_TYPES: { name: string; label: string; desc: string }[] = [
  {
    name: 'FIXED',
    label: '정액',
    desc: '정해진 금액을 깎아주는 할인입니다.',
  },
  {
    name: 'PERCENTAGE',
    label: '정률',
    desc: '정해진 비율(%)로 깎아주는 할인입니다. 최대로 깎아주는 금액에 상한을 둘 수 있습니다.',
  },
];

/**
 * 쿠폰 발급·생명주기 안내. 콘솔 프로모션 화면은 프로모션과 발급 카운트
 * (issuedCount/maxIssuanceCount)를 보여주며, 개별 쿠폰 상태는 백엔드 전용.
 */
export const COUPON_NOTE = {
  title: '쿠폰 발급',
  body: '프로모션 상세 화면에서 대상 고객에게 쿠폰을 발급할 수 있습니다. 발급 수는 미리 정한 최대 발급 수를 넘지 못합니다. 쿠폰은 발급된 뒤 주문에 쓰이고, 주문이 취소되면 다시 쓸 수 있게 되거나 기간이 지나면 만료됩니다. 콘솔에는 프로모션별 발급 현황만 보이고, 쿠폰 하나하나의 상태는 보이지 않습니다.',
} as const;

// ───────────────────────── 셀러 (Seller) ─────────────────────────

/** 셀러(Seller) 생명주기 상태(`SellerStatus`, 4 — ADR-MONO-042). */
export interface SellerState {
  name: string;
  label: string;
  /** 이 상태에서 가능한 운영자 액션(콘솔 셀러 상세). */
  actions: string;
  desc: string;
}

/**
 * 셀러는 CRUD(수정/삭제)가 없고 **상태 전이**로만 변한다. 온보딩 시
 * `PENDING_PROVISIONING` 으로 태어나 provision 되면 `ACTIVE`, 이후 `SUSPENDED`
 * (되돌릴 수 있는 잠금) 또는 `CLOSED`(종료, 백킹 계정 비활성) 로 간다. 테넌트별
 * `default` 셀러는 항상 ACTIVE.
 */
export const SELLER_STATES: SellerState[] = [
  {
    name: 'PENDING_PROVISIONING',
    label: '프로비저닝 대기',
    actions: '프로비저닝',
    desc: '셀러로 등록된 직후의 상태입니다. 준비를 마치면 활성 상태가 됩니다.',
  },
  {
    name: 'ACTIVE',
    label: '활성',
    actions: '정지 · 종료',
    desc: '정상적으로 영업 중인 상태입니다. 정지(되돌릴 수 있음) 또는 종료(되돌릴 수 없음)할 수 있습니다.',
  },
  {
    name: 'SUSPENDED',
    label: '정지',
    actions: '종료',
    desc: '일시적으로 막힌 상태입니다. 다시 활성화하거나 종료할 수 있습니다.',
  },
  {
    name: 'CLOSED',
    label: '종료',
    actions: '—',
    desc: '완전히 종료된 상태입니다(되돌릴 수 없음). 더 이상 바뀌지 않습니다.',
  },
];

// ───────────────────────── 사용자 (User) ─────────────────────────

/** 사용자(User) 상태(`USER_STATUS_VALUES`, 3) — 콘솔에서 읽기 전용. */
export interface UserState {
  name: string;
  label: string;
  desc: string;
}

export const USER_STATES: UserState[] = [
  {
    name: 'ACTIVE',
    label: '활성',
    desc: '정상 회원입니다.',
  },
  {
    name: 'SUSPENDED',
    label: '정지',
    desc: '일시 정지된 회원입니다.',
  },
  {
    name: 'WITHDRAWN',
    label: '탈퇴',
    desc: '탈퇴한 회원입니다. 일정 기간이 지나면 이메일·이름 같은 개인정보가 사라집니다.',
  },
];

/**
 * 사용자 화면은 **읽기 전용**(상태 변경·상태머신 없음)이며, 이메일·이름이
 * 비어 있을 수 있음을 설명.
 */
export const USER_NOTE = {
  title: '읽기 전용 · 비어 있을 수 있는 정보',
  body: '사용자 화면은 조회만 가능하고 상태를 바꿀 수 없습니다. 이메일이나 이름이 비어 있는 회원도 있습니다 — 가입 직후 아직 정보를 입력하지 않았거나, 탈퇴해서 개인정보가 사라진 경우입니다. 이런 경우 화면에는 "—" 로 표시됩니다.',
} as const;

// ───────────────────────── 알림 (Notification) ─────────────────────────

/** 알림 템플릿 타입(`TemplateType`, 4) — 이벤트 트리거에 대응. */
export interface TemplateType {
  name: string;
  label: string;
  desc: string;
}

export const TEMPLATE_TYPES: TemplateType[] = [
  {
    name: 'ORDER_PLACED',
    label: '주문 완료',
    desc: '주문 접수 시 발송.',
  },
  {
    name: 'PAYMENT_COMPLETED',
    label: '결제 완료',
    desc: '결제 완료 시 발송.',
  },
  {
    name: 'SHIPPING_STATUS_CHANGED',
    label: '배송 상태 변경',
    desc: '배송 상태가 바뀔 때 발송.',
  },
  {
    name: 'WELCOME',
    label: '회원 가입',
    desc: '가입(WELCOME) 시 발송.',
  },
];

/** 알림 채널(`NotificationChannel`, 3). */
export const NOTIFICATION_CHANNELS: { name: string; label: string }[] = [
  { name: 'EMAIL', label: '이메일' },
  { name: 'SMS', label: 'SMS' },
  { name: 'PUSH', label: '푸시' },
];

/**
 * 알림 템플릿의 불변 규칙 안내. notification-service 는 소비 전용(주문/결제/배송/
 * 인증 이벤트 → 발송)이며 콘솔은 템플릿 관리 표면(삭제 없음)만 흡수한다.
 */
export const NOTIFICATION_NOTE = {
  title: '알림 발송과 템플릿 수정 규칙',
  body: '주문·결제·배송·가입 관련 알림을 이메일·SMS·푸시로 보냅니다. 수신을 꺼둔 사람에게는 보내지 않습니다. 콘솔에서는 템플릿을 조회·생성·수정할 수 있지만 삭제는 할 수 없습니다. 템플릿의 타입과 채널은 한번 만들면 바꿀 수 없고, 제목과 본문만 수정할 수 있습니다.',
} as const;

// ───────────────────────── 도메인 롤 ─────────────────────────

/**
 * E-Commerce 도메인 롤. 운영자가 ecommerce 구독 테넌트로 assume-tenant 할 때
 * auth-service `OperatorRoleDerivation` 이 파생한다. WMS(세분 롤 다수)와 달리
 * **단일 coarse `ECOMMERCE_OPERATOR`** 하나뿐 — 7개 화면이 모두 동일하게 게이트된다. IAM
 * 가이드의 admin-console 역할과는 다른 축(도메인 롤).
 */
export const ECOMMERCE_ROLE_NOTE = {
  title: 'E-Commerce 권한 (ECOMMERCE_OPERATOR 하나)',
  body: 'E-Commerce 화면은 ECOMMERCE_OPERATOR 라는 권한 하나로만 열립니다. 회사(테넌트)를 선택하면 이 권한이 자동으로 부여되고, 상품·주문·배송·프로모션·사용자·셀러·알림 7개 화면이 모두 똑같이 이 권한으로 열립니다. WMS 처럼 화면마다 권한이 따로 나뉘어 있지 않습니다. 이 권한이 없으면 "접근 권한이 없습니다" 라는 안내가 나옵니다.',
} as const;

// ───────────────────────── 작업 레시피 (TASK-PC-FE-256) ─────────────────────────

/**
 * E-Commerce 작업 레시피 — 주문 취소→환불 보상 · 배송 발송(운송사/운송장 필수·
 * WMS 토글) · 셀러 상태 전이라는 이 화면의 실제 상태·화면만 참조한다. 결제는
 * 전용 화면이 없다는 점(주문 상태로 간접 노출)을 정직하게 반영한다.
 */
export const ECOMMERCE_RECIPES: GuideRecipeData[] = [
  {
    title: '환불 요청이 들어왔을 때',
    intro: '결제 전용 화면이 없어, 환불은 주문을 취소하면서 함께 처리됩니다.',
    steps: [
      '주문 화면(/ecommerce/orders)에서 해당 주문을 엽니다 — 운영자는 대기(PENDING)·확정(CONFIRMED) 상태에서만 취소할 수 있습니다.',
      '주문을 취소하면 결제가 이미 되어 있던 경우 자동으로 환불되고, 아직 결제가 되지 않았던 경우에는 그대로 취소됩니다.',
      '환불이 끝나면 결제 상태가 환불완료 또는 부분환불로 바뀌고, 주문 화면에는 취소 상태로 표시됩니다.',
    ],
  },
  {
    title: '주문을 발송 처리할 때',
    steps: [
      '배송 화면(/ecommerce/shippings)에서 준비중(PREPARING) 배송을 엽니다.',
      '발송(SHIPPED) 처리하려면 운송사와 운송장번호가 반드시 있어야 합니다 — 없으면 처리되지 않습니다. 발송하면 주문도 배송중(SHIPPED)으로 함께 바뀝니다.',
      'WMS 창고를 거쳐 가는 주문이면 "WMS 재고 차감" 토글을 켜서 실제 재고를 차감합니다.',
    ],
  },
  {
    title: '셀러를 정지하거나 종료할 때',
    steps: [
      '셀러 화면(/ecommerce/sellers)에서 대상 셀러의 상태를 확인합니다 — 셀러는 수정·삭제가 없고 상태 전이로만 바뀝니다.',
      '활성(ACTIVE) 상태에서 정지(SUSPENDED, 되돌릴 수 있음) 또는 종료(CLOSED, 되돌릴 수 없음)를 선택합니다.',
      '종료는 되돌릴 수 없고 관련 계정도 함께 비활성화됩니다(테넌트별 기본 셀러는 항상 활성 상태로 유지됩니다).',
    ],
  },
];

// ───────────────────────── 용어집 (TASK-PC-FE-256) ─────────────────────────

/**
 * E-Commerce 용어집 — 화면에 실제 렌더되는 문자열 중 일반 운영자가 모를 법한
 * 용어만. 주문·배송·셀러 상태 enum 은 표가 이미 한글 라벨로 설명하므로 제외.
 */
export const ECOMMERCE_GLOSSARY: GlossaryEntry[] = [
  {
    key: 'variant',
    term: '옵션 (variant)',
    meaning:
      '상품의 색·사이즈 같은 옵션 단위입니다. 상품 등록에 최소 1개가 필요하고, 재고는 옵션별로 따로 조정합니다.',
  },
  {
    key: 'SKU',
    term: 'SKU',
    full: 'Stock Keeping Unit',
    meaning: '재고를 관리하는 최소 상품 단위입니다. 옵션(variant)마다 재고를 따로 가집니다.',
  },
  {
    key: 'PG',
    term: '결제대행 (PG)',
    full: 'Payment Gateway',
    meaning:
      '카드·간편결제를 대신 처리해 주는 곳입니다(여기선 Toss Payments). 결제 승인과 환불이 이 곳을 통해 이뤄집니다.',
  },
  {
    key: 'backorder',
    term: '재고부족 이월 (BACKORDERED)',
    meaning:
      '주문을 확정할 때 재고가 모자라 잠시 미뤄지는 상태입니다. 다시 입고되면 먼저 들어온 주문부터 확정됩니다. 콘솔 상태 목록에는 없고 조회로만 보입니다.',
  },
  {
    key: 'assume-tenant',
    term: '테넌트 선택 (assume-tenant)',
    meaning:
      '운영자가 회사(테넌트)를 고르면 ECOMMERCE_OPERATOR 권한이 자동으로 부여되는 것을 말합니다. 7개 운영 화면이 모두 이 권한으로 열립니다.',
  },
];
