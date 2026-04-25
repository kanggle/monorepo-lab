import type {
  PaginatedResponse,
  ProductSummary,
  ProductDetail,
  ProductListParams,
  AdminOrderSummary,
  AdminOrderDetail,
  OrderListParams,
  OrderStatus,
  AdminUserSummary,
  AdminUserDetail,
  AdminUserListParams,
  PromotionSummary,
  PromotionDetail,
  PromotionListParams,
  ShippingSummary,
  ShippingListParams,
  NotificationTemplateSummary,
  NotificationTemplateDetail,
  NotificationTemplateListParams,
} from '@repo/types';

export const isMock = (): boolean =>
  process.env.NEXT_PUBLIC_USE_MOCK === 'true';

const delay = <T>(v: T): Promise<T> =>
  new Promise((r) => setTimeout(() => r(v), 120));

const paginate = <T>(
  content: T[],
  page = 0,
  size = 20,
): PaginatedResponse<T> => ({
  content: content.slice(page * size, page * size + size),
  page,
  size,
  totalElements: content.length,
});

const iso = (d: Date | string): string =>
  (typeof d === 'string' ? new Date(d) : d).toISOString();

const daysAgo = (n: number): string => {
  const d = new Date();
  d.setDate(d.getDate() - n);
  d.setHours(10, 30, 0, 0);
  return iso(d);
};

const hoursAgo = (n: number): string => {
  const d = new Date();
  d.setHours(d.getHours() - n);
  return iso(d);
};

// ─── Products ───────────────────────────────────────────────────────────

const PRODUCTS: ProductDetail[] = [
  {
    id: 'prd-1001',
    name: '프리미엄 원두 1kg',
    description: '에티오피아 예가체프 싱글오리진. 베리 향과 산미가 특징.',
    status: 'ON_SALE',
    price: 28000,
    categoryId: 'cat-coffee',
    thumbnailUrl: 'https://picsum.photos/seed/coffee1/300/300',
    images: [{ imageId: 'img-1001-1', url: 'https://picsum.photos/seed/coffee1/800/600', sortOrder: 0, isPrimary: true }],
    variants: [
      { id: 'v-1', optionName: '분쇄(홀빈)', stock: 48, additionalPrice: 0 },
      { id: 'v-2', optionName: '에스프레소용 분쇄', stock: 22, additionalPrice: 0 },
    ],
  },
  {
    id: 'prd-1002',
    name: '드립 커피 필터 100매',
    description: '표백 없는 천연 펄프 필터.',
    status: 'ON_SALE',
    price: 6500,
    categoryId: 'cat-accessory',
    thumbnailUrl: 'https://picsum.photos/seed/filter/300/300',
    variants: [{ id: 'v-3', optionName: '1-2인용', stock: 152, additionalPrice: 0 }],
  },
  {
    id: 'prd-1003',
    name: '핸드드립 서버 600ml',
    description: '내열 유리 서버.',
    status: 'ON_SALE',
    price: 19800,
    categoryId: 'cat-accessory',
    thumbnailUrl: 'https://picsum.photos/seed/server/300/300',
    variants: [{ id: 'v-4', optionName: '기본', stock: 34, additionalPrice: 0 }],
  },
  {
    id: 'prd-1004',
    name: '콜드브루 스타터 키트',
    description: '콜드브루 메이커 + 원두 200g 세트.',
    status: 'SOLD_OUT',
    price: 39000,
    categoryId: 'cat-set',
    thumbnailUrl: 'https://picsum.photos/seed/coldbrew/300/300',
    variants: [{ id: 'v-5', optionName: '기본', stock: 0, additionalPrice: 0 }],
  },
  {
    id: 'prd-1005',
    name: '에티오피아 시다모 500g',
    description: '시트러스 계열 산미, 화사한 향미.',
    status: 'ON_SALE',
    price: 16500,
    categoryId: 'cat-coffee',
    thumbnailUrl: 'https://picsum.photos/seed/sidamo/300/300',
    variants: [{ id: 'v-6', optionName: '홀빈', stock: 12, additionalPrice: 0 }],
  },
  {
    id: 'prd-1006',
    name: '케냐 AA 500g',
    description: '진한 바디감과 와이니한 산미.',
    status: 'SOLD_OUT',
    price: 17500,
    categoryId: 'cat-coffee',
    thumbnailUrl: 'https://picsum.photos/seed/kenya/300/300',
    variants: [{ id: 'v-7', optionName: '홀빈', stock: 0, additionalPrice: 0 }],
  },
  {
    id: 'prd-1007',
    name: '모카포트 3컵',
    description: '알루미늄 이탈리안 모카포트.',
    status: 'ON_SALE',
    price: 34000,
    categoryId: 'cat-accessory',
    thumbnailUrl: 'https://picsum.photos/seed/moka/300/300',
    variants: [{ id: 'v-8', optionName: '3컵', stock: 18, additionalPrice: 0 }],
  },
  {
    id: 'prd-1008',
    name: '디카페인 원두 500g',
    description: '스위스 워터 프로세스 디카페인.',
    status: 'HIDDEN',
    price: 19000,
    categoryId: 'cat-coffee',
    thumbnailUrl: 'https://picsum.photos/seed/decaf/300/300',
    variants: [{ id: 'v-9', optionName: '홀빈', stock: 7, additionalPrice: 0 }],
  },
];

export const mockGetProducts = (
  p: ProductListParams = {},
): Promise<PaginatedResponse<ProductSummary>> => {
  let list: ProductSummary[] = PRODUCTS.map(
    ({ id, name, status, price, thumbnailUrl, categoryId }) => ({
      id,
      name,
      status,
      price,
      thumbnailUrl: thumbnailUrl ?? '',
      categoryId,
    }),
  );
  if (p.status) list = list.filter((x) => x.status === p.status);
  if (p.categoryId) list = list.filter((x) => x.categoryId === p.categoryId);
  if (p.name) list = list.filter((x) => x.name.includes(p.name!));
  return delay(paginate(list, p.page, p.size));
};

export const mockGetProduct = (id: string): Promise<ProductDetail> => {
  const found = PRODUCTS.find((x) => x.id === id) ?? PRODUCTS[0];
  return delay(found);
};

// ─── Orders ─────────────────────────────────────────────────────────────

const ORDER_NAMES = [
  '프리미엄 원두 1kg',
  '콜드브루 스타터 키트',
  '드립 커피 필터 100매',
  '모카포트 3컵',
  '에티오피아 시다모 500g',
  '핸드드립 서버 600ml',
  '케냐 AA 500g',
  '디카페인 원두 500g',
];

const ORDERS: AdminOrderSummary[] = Array.from({ length: 42 }).map((_, i) => {
  const statuses: OrderStatus[] = [
    'PENDING',
    'CONFIRMED',
    'SHIPPED',
    'DELIVERED',
    'DELIVERED',
    'CANCELLED',
  ];
  const status = statuses[i % statuses.length];
  const itemCount = (i % 4) + 1;
  return {
    orderId: `ord-${20000 + i}`,
    userId: `usr-${3000 + (i % 12)}`,
    status,
    totalPrice: 15000 + (i * 1370) % 90000,
    itemCount,
    firstItemName: ORDER_NAMES[i % ORDER_NAMES.length],
    createdAt:
      i < 8 ? hoursAgo(i * 2 + 1) : daysAgo(Math.min(29, Math.floor(i / 2))),
  };
});

export const mockGetOrders = (
  p: OrderListParams = {},
): Promise<PaginatedResponse<AdminOrderSummary>> => {
  let list = ORDERS;
  if (p.status) list = list.filter((x) => x.status === p.status);
  return delay(paginate(list, p.page, p.size ?? 20));
};

export const mockGetOrder = (id: string): Promise<AdminOrderDetail> => {
  const base = ORDERS.find((x) => x.orderId === id) ?? ORDERS[0];
  return delay({
    orderId: base.orderId,
    userId: base.userId,
    status: base.status,
    totalPrice: base.totalPrice,
    items: [
      {
        productId: 'prd-1001',
        variantId: 'v-1',
        productName: base.firstItemName ?? '상품',
        optionName: '기본',
        quantity: base.itemCount,
        unitPrice: Math.round(base.totalPrice / base.itemCount),
      },
    ],
    shippingAddress: {
      recipient: '김고객',
      phone: '010-1234-5678',
      zipCode: '06236',
      address1: '서울 강남구 테헤란로 123',
      address2: '4층',
    },
    createdAt: base.createdAt,
    updatedAt: base.createdAt,
  });
};

// ─── Users ──────────────────────────────────────────────────────────────

const KOREAN_NAMES = [
  '김민수',
  '이서연',
  '박지훈',
  '최유진',
  '정도윤',
  '강하은',
  '윤재원',
  '장서윤',
  '임예준',
  '한수아',
  '오시현',
  '신다영',
  '권우진',
  '배은채',
];

const USERS: AdminUserDetail[] = KOREAN_NAMES.map((name, i) => ({
  userId: `usr-${3000 + i}`,
  email: `user${i + 1}@example.com`,
  name,
  nickname: i % 3 === 0 ? null : `${name.slice(-1)}${i}`,
  phone: i % 4 === 0 ? null : `010-${1000 + i}-${5000 + i}`,
  profileImageUrl: null,
  status: i === 3 ? 'SUSPENDED' : i === 7 ? 'WITHDRAWN' : 'ACTIVE',
  createdAt: daysAgo(i * 3 + 1),
  updatedAt: daysAgo(i),
}));

export const mockGetUsers = (
  p: AdminUserListParams = {},
): Promise<PaginatedResponse<AdminUserSummary>> => {
  let list: AdminUserSummary[] = USERS.map(
    ({ userId, email, name, nickname, status, createdAt }) => ({
      userId,
      email,
      name,
      nickname,
      status,
      createdAt,
    }),
  );
  if (p.status) list = list.filter((x) => x.status === p.status);
  if (p.email) list = list.filter((x) => x.email.includes(p.email!));
  return delay(paginate(list, p.page, p.size));
};

export const mockGetUser = (id: string): Promise<AdminUserDetail> =>
  delay(USERS.find((x) => x.userId === id) ?? USERS[0]);

// ─── Promotions ─────────────────────────────────────────────────────────

const PROMOS: PromotionDetail[] = [
  {
    promotionId: 'pro-501',
    name: '봄맞이 신규회원 10% 할인',
    description: '신규 가입 후 7일 내 사용 가능.',
    discountType: 'PERCENTAGE',
    discountValue: 10,
    maxDiscountAmount: 5000,
    maxIssuanceCount: 1000,
    issuedCount: 642,
    startDate: daysAgo(10),
    endDate: daysAgo(-20),
    status: 'ACTIVE',
    createdAt: daysAgo(12),
    updatedAt: daysAgo(1),
  },
  {
    promotionId: 'pro-502',
    name: '5만원 이상 3천원 할인',
    description: '전 상품 대상.',
    discountType: 'FIXED',
    discountValue: 3000,
    maxDiscountAmount: 3000,
    maxIssuanceCount: 500,
    issuedCount: 213,
    startDate: daysAgo(5),
    endDate: daysAgo(-10),
    status: 'ACTIVE',
    createdAt: daysAgo(7),
    updatedAt: daysAgo(2),
  },
  {
    promotionId: 'pro-503',
    name: '블랙프라이데이 20%',
    description: '연중 최대 할인 이벤트.',
    discountType: 'PERCENTAGE',
    discountValue: 20,
    maxDiscountAmount: 20000,
    maxIssuanceCount: 2000,
    issuedCount: 0,
    startDate: daysAgo(-14),
    endDate: daysAgo(-7),
    status: 'SCHEDULED',
    createdAt: daysAgo(3),
    updatedAt: daysAgo(3),
  },
  {
    promotionId: 'pro-504',
    name: '신학기 커피 세트 쿠폰',
    description: '종료된 이벤트.',
    discountType: 'FIXED',
    discountValue: 5000,
    maxDiscountAmount: 5000,
    maxIssuanceCount: 300,
    issuedCount: 300,
    startDate: daysAgo(40),
    endDate: daysAgo(20),
    status: 'ENDED',
    createdAt: daysAgo(45),
    updatedAt: daysAgo(20),
  },
];

export const mockGetPromotions = (
  p: PromotionListParams = {},
): Promise<PaginatedResponse<PromotionSummary>> => {
  let list: PromotionSummary[] = PROMOS.map((x) => ({
    promotionId: x.promotionId,
    name: x.name,
    discountType: x.discountType,
    discountValue: x.discountValue,
    maxIssuanceCount: x.maxIssuanceCount,
    issuedCount: x.issuedCount,
    startDate: x.startDate,
    endDate: x.endDate,
    status: x.status,
  }));
  if (p.status) list = list.filter((x) => x.status === p.status);
  return delay(paginate(list, p.page, p.size));
};

export const mockGetPromotion = (id: string): Promise<PromotionDetail> =>
  delay(PROMOS.find((x) => x.promotionId === id) ?? PROMOS[0]);

// ─── Shippings ──────────────────────────────────────────────────────────

const SHIPPINGS: ShippingSummary[] = Array.from({ length: 18 }).map((_, i) => {
  const statuses = ['PREPARING', 'SHIPPED', 'IN_TRANSIT', 'DELIVERED'] as const;
  const status = statuses[i % statuses.length];
  return {
    shippingId: `shp-${7000 + i}`,
    orderId: `ord-${20000 + i}`,
    status,
    trackingNumber: status === 'PREPARING' ? null : `CJ${1000000000 + i}`,
    carrier: status === 'PREPARING' ? null : 'CJ대한통운',
    createdAt: daysAgo(i + 1),
    updatedAt: daysAgo(i),
  };
});

export const mockGetShippings = (
  p: ShippingListParams = {},
): Promise<PaginatedResponse<ShippingSummary>> => {
  let list = SHIPPINGS;
  if (p.status) list = list.filter((x) => x.status === p.status);
  return delay(paginate(list, p.page, p.size));
};

// ─── Notification Templates ─────────────────────────────────────────────

const TEMPLATES: NotificationTemplateDetail[] = [
  {
    templateId: 'tpl-1',
    type: 'ORDER_PLACED',
    channel: 'EMAIL',
    subject: '[첫 프로젝트] 주문이 접수되었습니다',
    body: '{{userName}}님, 주문번호 {{orderId}}가 정상 접수되었습니다.',
    createdAt: daysAgo(30),
  },
  {
    templateId: 'tpl-2',
    type: 'PAYMENT_COMPLETED',
    channel: 'EMAIL',
    subject: '결제가 완료되었습니다',
    body: '{{userName}}님, 결제금액 {{amount}}원이 승인되었습니다.',
    createdAt: daysAgo(28),
  },
  {
    templateId: 'tpl-3',
    type: 'SHIPPING_STATUS_CHANGED',
    channel: 'SMS',
    subject: '배송 상태 알림',
    body: '[첫프로젝트] 주문 {{orderId}} 배송 상태: {{status}}',
    createdAt: daysAgo(25),
  },
  {
    templateId: 'tpl-4',
    type: 'WELCOME',
    channel: 'PUSH',
    subject: '가입을 환영합니다',
    body: '{{userName}}님, 가입 축하 10% 쿠폰을 드려요!',
    createdAt: daysAgo(20),
  },
];

export const mockGetTemplates = (
  p: NotificationTemplateListParams = {},
): Promise<PaginatedResponse<NotificationTemplateSummary>> => {
  const list: NotificationTemplateSummary[] = TEMPLATES.map(
    ({ templateId, type, channel, subject, createdAt }) => ({
      templateId,
      type,
      channel,
      subject,
      createdAt,
    }),
  );
  return delay(paginate(list, p.page, p.size));
};

export const mockGetTemplate = (
  id: string,
): Promise<NotificationTemplateDetail> =>
  delay(TEMPLATES.find((x) => x.templateId === id) ?? TEMPLATES[0]);
