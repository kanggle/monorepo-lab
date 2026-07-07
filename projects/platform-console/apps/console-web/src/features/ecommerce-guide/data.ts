/**
 * E-Commerce 가이드 화면의 정적 참조 데이터 (TASK-PC-FE-184).
 *
 * E-Commerce 콘솔의 7개 라이브 운영 화면 — **상품(`/ecommerce/products`)·
 * 주문(`/ecommerce/orders`)·배송(`/ecommerce/shippings`)·프로모션
 * (`/ecommerce/promotions`)·사용자(`/ecommerce/users`)·셀러(`/ecommerce/sellers`)·
 * 알림(`/ecommerce/notifications/templates`)** — 이 실제로 보여주는 상태값의
 * 의미와, 그 뒤의 이커머스 마이크로서비스 구성을 운영자에게 설명한다. IAM
 * 가이드(`features/iam-guide/data.ts`, TASK-PC-FE-163)·WMS 가이드
 * (`features/wms-guide/data.ts`, TASK-PC-FE-183)와 같은 원칙: 타입 있는 정적
 * 배열 + 정적 화면. 데이터 페치·권한 게이트 없음(콘솔 진입자 누구나 열람).
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
 * 은 이 중 7개 서비스의 운영자 API를 호출해 화면을 렌더한다(상품·주문·배송·
 * 프로모션·사용자·셀러·알림). settlement·review·search 는 도메인에는 있으나 콘솔
 * 표면에는 아직 없다.
 */
export const DOMAIN_SERVICES: DomainService[] = [
  {
    key: 'product',
    name: 'product-service',
    context: '상품 · 재고 · 셀러',
    desc: '상품 마스터(등록/수정/삭제)·variant(옵션)·카테고리·가격·재고, 그리고 셀러 애그리거트를 소유.',
    console: '상품 · 셀러',
  },
  {
    key: 'order',
    name: 'order-service',
    context: '주문',
    desc: '주문 애그리거트 생명주기를 소유. 주문 검증·접수 후 결제 이벤트에 반응해 상태를 전이.',
    console: '주문',
  },
  {
    key: 'payment',
    name: 'payment-service',
    context: '결제',
    desc: 'OrderPlaced 시 PENDING 결제 생성 → Toss Payments PG 로 승인, 환불 처리. 콘솔 전용 화면 없음(주문 상태로 간접 노출).',
    console: '— (주문에 반영)',
  },
  {
    key: 'shipping',
    name: 'shipping-service',
    context: '배송',
    desc: 'OrderConfirmed 시 배송 생성, 선형 상태머신 강제, 운송장 추적 갱신.',
    console: '배송',
  },
  {
    key: 'promotion',
    name: 'promotion-service',
    context: '프로모션 · 쿠폰',
    desc: '프로모션 CRUD + 쿠폰 발급/생명주기, 할인 계산.',
    console: '프로모션',
  },
  {
    key: 'user',
    name: 'user-service',
    context: '사용자 프로필',
    desc: 'IAM `account.created` 로 최소 프로필 생성, 프로필·배송지 CRUD, 탈퇴/익명화 반응.',
    console: '사용자',
  },
  {
    key: 'notification',
    name: 'notification-service',
    context: '알림',
    desc: '소비 전용. 주문/결제/배송/인증 이벤트를 이메일·SMS·푸시로 발송. 템플릿·수신 설정 관리.',
    console: '알림',
  },
  {
    key: 'settlement',
    name: 'settlement-service',
    context: '정산',
    desc: '셀러별 라인 단위 수수료 적립, 기간 마감 → 셀러 정산금 지급.',
    console: '—',
  },
  {
    key: 'review',
    name: 'review-service',
    context: '리뷰 · 평점',
    desc: '구매 검증 리뷰 CRUD, 평균 평점 캐시.',
    console: '—',
  },
  {
    key: 'search',
    name: 'search-service',
    context: '검색 · 색인',
    desc: '상품/리뷰 이벤트로 Elasticsearch 색인 구축, 검색 질의 API.',
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
    desc: '주문 접수, 결제 대기. 결제 완료(PaymentCompleted) 시 자동 확정. 운영자는 확정 또는 취소 가능.',
  },
  {
    name: 'CONFIRMED',
    label: '확정',
    terminal: false,
    operatorActionable: true,
    desc: '결제 완료로 확정됨 → 배송 서비스가 배송 생성. 운영자는 취소만 가능(확정 전으로 되돌릴 수 없음).',
  },
  {
    name: 'SHIPPED',
    label: '배송중',
    terminal: false,
    operatorActionable: false,
    desc: '배송이 발송됨. 배송 상태변경(ShippingStatusChanged)이 구동하는 read-only 상태 — 운영자가 직접 못 바꾼다.',
  },
  {
    name: 'DELIVERED',
    label: '배송완료',
    terminal: true,
    operatorActionable: false,
    desc: '배송 완료. 배송 return-leg 이 구동하는 정상 종료 상태(read-only).',
  },
  {
    name: 'CANCELLED',
    label: '취소',
    terminal: true,
    operatorActionable: false,
    desc: '주문 취소(운영자·사용자·결제 타임아웃). 결제가 캡처됐으면 환불/보이드 보상. 종료.',
  },
  {
    name: 'STUCK_RECOVERY_FAILED',
    label: '복구실패',
    terminal: true,
    operatorActionable: false,
    desc: '결제 미완 주문의 자동취소 보상을 co-commit 하지 못한 방어적 폴백 종료(정상 경로에선 거의 안 보임). 종료.',
  },
];

/**
 * 결제 → 확정 흐름 + BACKORDERED 안내. 콘솔 주문 enum 이 노출하지 않는 백엔드
 * 상태의 존재를 설명한다.
 */
export const ORDER_LIFECYCLE_NOTE = {
  title: '결제 구동 확정과 재고부족 이월(BACKORDERED)',
  body: '주문은 결제로 구동된다: 접수(PENDING) 시 결제 서비스가 PENDING 결제를 만들고 Toss PG 승인 후 PaymentCompleted 를 발행 → 주문이 CONFIRMED 로 확정된다. 확정 시 상품 서비스가 전부-아니면-전무 재고 예약을 시도하며, 한 라인이라도 부족하면 주문은 백엔드에서 BACKORDERED(재고부족 이월, 재고 차감 없음)로 이동한다 — 이후 재입고 시 FIFO 재예약으로 CONFIRMED 가 된다. BACKORDERED 는 취소 가능하지만 콘솔 주문 화면의 상태 선택지에는 나타나지 않으며(백엔드 전용), 읽기 API 로만 관측된다.',
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
    desc: 'OrderPlaced 로 생성된 미승인 결제(orderId 로 멱등). PG 승인 대기.',
  },
  {
    name: 'COMPLETED',
    label: '완료',
    desc: 'PG 승인 완료 → PaymentCompleted 발행 → 주문 확정. 환불은 이 상태에서만 가능.',
  },
  {
    name: 'FAILED',
    label: '실패',
    desc: 'PG 측 거절(4xx). 재시도 없음. 주문은 취소로 이어진다.',
  },
  {
    name: 'PARTIALLY_REFUNDED',
    label: '부분환불',
    desc: '일부 금액 환불됨(잔액 존재).',
  },
  {
    name: 'REFUNDED',
    label: '환불완료',
    desc: '전액 환불 완료. 종료.',
  },
  {
    name: 'VOIDED',
    label: '보이드',
    desc: '캡처된 적 없는 PENDING 결제의 시스템 머니-세이프 취소(확정 전 주문 취소). FAILED(PG 거절)와 구분 — 환불 채무 없음. 종료.',
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
    desc: 'OrderConfirmed 로 생성된 배송. 발송 대기.',
  },
  {
    name: 'SHIPPED',
    label: '발송',
    terminal: false,
    desc: '발송됨. 이 전이에는 운송사+운송장번호가 필수. 주문을 CONFIRMED→SHIPPED 로 되돌려 반영.',
  },
  {
    name: 'IN_TRANSIT',
    label: '배송중',
    terminal: false,
    desc: '운송 중. 운송장 추적 갱신(refresh-tracking)으로 상태를 최신화.',
  },
  {
    name: 'DELIVERED',
    label: '배송완료',
    terminal: true,
    desc: '수령 완료. 주문을 DELIVERED 로 반영. 종료 상태.',
  },
];

/**
 * WMS 연계(ADR-MONO-022) 안내 — 이커머스 배송이 WMS 풀필먼트로 라우팅된 경우.
 */
export const SHIPPING_WMS_NOTE = {
  title: 'WMS 라우팅 배송과 재고 차감',
  body: '주문이 WMS 풀필먼트로 라우팅되면(wmsRouted) 배송 행에 "WMS 재고 차감" 토글이 뜬다. 발송(SHIPPED) 확정 시 토글을 켜면 이커머스가 `ecommerce.shipping.manual-confirm-requested.v1` 를 발행해 WMS 가 물리 재고를 차감한다. 게이트의 최종 권위는 producer 이며 콘솔은 wmsRouted 행에만 토글을 노출한다. 목록/상세 DTO 는 운송사·운송장번호가 비어 있으면 `null` 을 주며 콘솔은 `—` 로 렌더한다.',
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
    desc: '판매 노출. 정상 판매 상태.',
  },
  {
    name: 'SOLD_OUT',
    label: '품절',
    desc: '재고 소진. 주의 필요 상태(운영자가 재고 조정으로 복귀).',
  },
  {
    name: 'HIDDEN',
    label: '숨김',
    desc: '비노출. 판매 목록에서 감춘 비활성 상태.',
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
    desc: 'SKU/옵션 단위 — `optionName·stock·additionalPrice`. 상품 등록에 최소 1개 필수. 옵션 수정은 optionName·추가금만(재고 제외).',
  },
  {
    key: 'stock',
    term: '재고 조정',
    desc: '재고는 variant 별로 분리 조정 — 부호 있는 증감(quantity)+사유(reason)로 조정, 음수 재고 불가.',
  },
  {
    key: 'image',
    term: '이미지',
    desc: '`sortOrder·isPrimary` 를 갖는 이미지 목록. presigned(S3 방식) 업로드로 관리(별도 이미지 매니저).',
  },
  {
    key: 'seller',
    term: '셀러 소유',
    desc: '상품은 `sellerId` 로 셀러에 귀속되며, 정산은 라인별로 그 셀러에 수수료를 적립한다.',
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
    desc: '시작일 이전. 아직 적용 안 됨.',
  },
  {
    name: 'ACTIVE',
    label: '진행중',
    desc: '시작~종료 사이. 할인 적용·쿠폰 발급 가능.',
  },
  {
    name: 'ENDED',
    label: '종료',
    desc: '종료일 이후. 완료.',
  },
];

/** 할인 종류(`discountType`, 2). */
export const DISCOUNT_TYPES: { name: string; label: string; desc: string }[] = [
  {
    name: 'FIXED',
    label: '정액',
    desc: '고정 금액 할인(discountValue 원).',
  },
  {
    name: 'PERCENTAGE',
    label: '정률',
    desc: '비율 할인(discountValue %). `maxDiscountAmount` 상한을 둘 수 있다.',
  },
];

/**
 * 쿠폰 발급·생명주기 안내. 콘솔 프로모션 화면은 프로모션과 발급 카운트
 * (issuedCount/maxIssuanceCount)를 보여주며, 개별 쿠폰 상태는 백엔드 전용.
 */
export const COUPON_NOTE = {
  title: '쿠폰 발급과 생명주기',
  body: '프로모션 상세에서 대상 사용자(userIds)에게 쿠폰을 발급한다(발급 카운트가 최대 발급수 maxIssuanceCount 를 넘지 못함). 개별 쿠폰은 백엔드에서 발급(ISSUED) → 주문 시 사용(USED) → 주문 취소 시 사용복구(USED→ISSUED) 또는 만료(EXPIRED) 로 흐른다. 콘솔은 프로모션 단위 발급 현황만 보여주고 개별 쿠폰 상태는 노출하지 않는다.',
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
    desc: '온보딩 직후(IAM 셀러-운영자 계정 fail-soft 발급). 프로비저닝하면 ACTIVE.',
  },
  {
    name: 'ACTIVE',
    label: '활성',
    actions: '정지 · 종료',
    desc: '정상 영업. 정지(가역) 또는 종료(비가역) 가능.',
  },
  {
    name: 'SUSPENDED',
    label: '정지',
    actions: '종료',
    desc: '되돌릴 수 있는 잠금. 종료로만 이어지거나 다시 활성화.',
  },
  {
    name: 'CLOSED',
    label: '종료',
    actions: '—',
    desc: '종료(비가역). 백킹 계정 비활성. 종료 상태.',
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
    desc: '정상 회원.',
  },
  {
    name: 'SUSPENDED',
    label: '정지',
    desc: '일시 정지된 회원.',
  },
  {
    name: 'WITHDRAWN',
    label: '탈퇴',
    desc: '탈퇴 회원. 유예 후 PII 익명화(이메일·이름이 null 로 바뀜).',
  },
];

/**
 * 사용자 화면은 **읽기 전용**(상태 변경·상태머신 없음)이며, 이메일·이름이
 * 비어 있을 수 있음을 설명.
 */
export const USER_NOTE = {
  title: '읽기 전용 · 익명화된 프로필',
  body: '콘솔 사용자 화면은 조회 전용이다(상태 변경 없음). 이메일·이름은 null 일 수 있다 — IAM `account.created` 후 첫 프로필 수정 전의 최소 프로필이거나, 탈퇴/익명화된 계정(ADR-MONO-037)이 그렇다. 이런 행도 목록에서 걸러지지 않으므로 `—` 로 렌더한다.',
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
  title: '템플릿 불변 필드와 발송 서비스',
  body: 'notification-service 는 소비 전용 서비스로, 4개 상위 서비스(주문/결제/배송/인증)의 이벤트를 이메일·SMS·푸시로 발송하고 채널별 수신 설정(opt-out)을 지킨다. 콘솔은 템플릿 관리(목록/생성/수정, 삭제 없음)만 담당한다. 템플릿의 **타입·채널은 생성 후 불변**이며, 수정은 제목(subject)·본문(body)만 가능하다 — 본문의 변수 치환으로 발송 내용을 채운다.',
} as const;

// ───────────────────────── 도메인 롤 ─────────────────────────

/**
 * E-Commerce 도메인 롤. 운영자가 ecommerce 구독 테넌트로 assume-tenant 할 때
 * auth-service `OperatorRoleDerivation` 이 파생한다. WMS(세분 롤 다수)와 달리
 * **단일 coarse `ECOMMERCE_OPERATOR`** 하나뿐 — 7개 화면이 모두 동일하게 게이트된다. IAM
 * 가이드의 admin-console 역할과는 다른 축(도메인 롤).
 */
export const ECOMMERCE_ROLE_NOTE = {
  title: 'E-Commerce 도메인 롤 (단일 ECOMMERCE_OPERATOR)',
  body: 'E-Commerce 화면은 단일 도메인 롤 `ECOMMERCE_OPERATOR` 으로 게이트된다. 운영자가 ecommerce 구독 테넌트로 테넌트 선택(assume-tenant)할 때 자동 파생되어 주입되며(auth-service OperatorRoleDerivation), 상품·주문·배송·프로모션·사용자·셀러·알림 7개 화면이 모두 이 하나의 롤로 동일하게 열린다. WMS 처럼 화면별 세분 롤(READ/WRITE)은 없다. ecommerce 구독이 없거나 롤이 없으면 도메인 게이트웨이가 403 을 반환하고 콘솔은 "접근 권한이 없습니다"로 표시한다. (IAM 콘솔을 게이트하는 admin-console 역할과는 별도 축 — IAM 가이드 참조.)',
} as const;
