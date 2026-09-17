import { SAMPLE_AS_OF, SAMPLE_LABEL_SUFFIX } from '../codes';
import { fixtureNotFound } from '../router';

/**
 * Same shape as `fixtures/index.ts`'s `FixtureHandler` — written out here
 * (rather than imported) so this module has no edge back to `index.ts`, which
 * imports THIS module (`shared/sample/**` must stay a DAG the isolation guard
 * can read — see `iam.ts`'s identical note).
 */
type EcommerceFixtureHandler = (path: string) => unknown;

/**
 * ecommerce domain fixtures (TASK-PC-FE-284 — `ADR-MONO-074` execution 3/8):
 * the 9 `callEcommerceGateway` surfaces (ecommerce [products+images-list] ·
 * ecommerce_image · ecommerce_user · ecommerce_shipping · ecommerce_order ·
 * ecommerce_notification · ecommerce_seller · ecommerce_settlement ·
 * ecommerce_promotion).
 *
 * Hand-authored synthetic data (ADR-MONO-074 A4 — no extraction path from any
 * backend). Each handler is parsed by the SAME zod schema the real screen uses
 * (`tests/unit/sample-fixtures-schema-ecommerce.test.ts`).
 *
 * R2ⓐ: human-readable strings end with «(샘플)»; ids, codes, enums, dates and
 * amounts do not (`shared/sample/label-rule.ts` — see that file's
 * TASK-PC-FE-284 additions for the new keys this file introduces). AC-7:
 * person-identifying values (user email/name/nickname, order recipient) are
 * unambiguously synthetic (`*.example` domains, invented Korean names) — and
 * NO fixture carries a MinIO/EC2/object-storage image origin: every product
 * and user image field is `null` (§ "AC-7 — image choice" below).
 *
 * ── AC-3 world consistency ───────────────────────────────────────────────
 * Every order line's `productId` / `sellerId` and every order's `userId`
 * resolve to a real row in `PRODUCTS` / `SELLERS` / `USERS` below (asserted by
 * a walk-the-references test) — so a screen that ever grows a product/seller/
 * user drill-through link from the order view resolves, not 404s.
 *
 * ── Money invariants (task Edge Case) ────────────────────────────────────
 * Every order's `totalPrice` = Σ(item.unitPrice × item.quantity) exactly.
 * Every seller's settlement balance = Σ(that seller's accrual lines) exactly
 * (REVERSAL lines are negative — a partial-refund clawback). Every CLOSED
 * period's `sellerCount` = the number of payout rows the period actually
 * lists (the period detail's "합계" = its row count, the closest analog this
 * domain has to "정산 기간 상세의 합계 = 행 합" — there is no other
 * cross-checkable total on the payouts screen).
 *
 * ── AC-7 — image choice (프로덕트/유저 이미지) ────────────────────────────
 * **선택: 이미지 없음.** `thumbnailUrl`/`profileImageUrl` = `null`,
 * `listImages()` = `{ images: [] }` for every product. **이유**: 이미 공개된
 * CDN 주소를 하나 고르더라도 그 주소가 나중에 만료·차단되면 "깨진 이미지 =
 * 고장"으로 보이는 것은 똑같다(과제 Failure Scenario) — 반면 "이미지 없음"은
 * 화면에 이미 있는 정상 placeholder 상태이고 외부 의존이 전혀 없다. UI 쪽도
 * 이 경로가 안전함을 확인했다: `ProductsTable`은 `thumbnailUrl`을 아예 읽지
 * 않고, `ProductDetail`도 마찬가지이며, `ImageManager`는 빈 `images:[]`를
 * "표시할 이미지가 없다"는 기존 빈 상태로 그린다(추가 코드 변경 없음).
 */

const SUFFIX = SAMPLE_LABEL_SUFFIX;

function splitPath(path: string): { pathname: string; query: URLSearchParams } {
  const [pathname, qs = ''] = path.split('?');
  return { pathname, query: new URLSearchParams(qs) };
}

function intParam(query: URLSearchParams, key: string, fallback: number): number {
  const raw = query.get(key);
  if (raw === null) return fallback;
  const n = Number(raw);
  return Number.isFinite(n) ? n : fallback;
}

/** `{ content, page, size, totalElements }` (products/orders/users/promotions/shippings/notifications/sellers). */
function paginateContent<T>(rows: readonly T[], page: number, size: number) {
  const safeSize = Math.max(1, size);
  const start = page * safeSize;
  return {
    content: rows.slice(start, start + safeSize),
    page,
    size: safeSize,
    totalElements: rows.length,
  };
}

/** `{ items, page, size, totalElements }` (settlements — accruals/periods/payouts). */
function paginateItems<T>(rows: readonly T[], page: number, size: number) {
  const safeSize = Math.max(1, size);
  const start = page * safeSize;
  return {
    items: rows.slice(start, start + safeSize),
    page,
    size: safeSize,
    totalElements: rows.length,
  };
}

// ===========================================================================
// sellers — GET /api/admin/sellers (+ /{id}, /summary)
// ===========================================================================

const SELLERS_PATH = '/api/admin/sellers';

export const ECOMMERCE_SELLERS = [
  {
    sellerId: 'seller-sample-0001',
    displayName: `예시 마켓 셀러${SUFFIX}`,
    status: 'ACTIVE',
    createdAt: '2026-05-01T00:00:00Z',
    updatedAt: '2026-08-01T00:00:00Z',
  },
  {
    sellerId: 'seller-sample-0002',
    displayName: `신규 입점 셀러${SUFFIX}`,
    status: 'PENDING_PROVISIONING',
    createdAt: '2026-08-20T00:00:00Z',
    updatedAt: null,
  },
  {
    sellerId: 'seller-sample-0003',
    displayName: `정지된 셀러${SUFFIX}`,
    status: 'SUSPENDED',
    createdAt: '2026-03-10T00:00:00Z',
    updatedAt: '2026-07-15T00:00:00Z',
  },
] as const;

const SELLERS_SUMMARY = { today: 0, week: 1, month: 2, total: 3 };

function sellersFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);
  if (pathname === `${SELLERS_PATH}/summary`) return SELLERS_SUMMARY;

  const detailMatch = pathname.match(new RegExp(`^${SELLERS_PATH}/([^/]+)$`));
  if (detailMatch) {
    const id = decodeURIComponent(detailMatch[1]);
    const found = ECOMMERCE_SELLERS.find((s) => s.sellerId === id);
    if (!found) return fixtureNotFound('SELLER_NOT_FOUND', 'seller not found');
    return found;
  }

  if (pathname !== SELLERS_PATH) return undefined;
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  return paginateContent(ECOMMERCE_SELLERS, page, size);
}

// ===========================================================================
// ecommerce (products slice — bare `ecommerce` logPrefix) — GET
// /api/admin/products (+ /summary), GET /api/admin/products/{id} (detail —
// TASK-MONO-703 moved it off the public tree, which the gateway closes to the operator)
// ===========================================================================

const PRODUCTS_ADMIN_PATH = '/api/admin/products';

interface ProductVariantSeed {
  id: string;
  optionName: string;
  stock: number;
  additionalPrice: number;
}

interface ProductSeed {
  id: string;
  name: string;
  description: string | null;
  status: string;
  price: number;
  categoryId: string;
  thumbnailUrl: string | null;
  sellerId: string;
  images: unknown[];
  variants: ProductVariantSeed[];
}

export const ECOMMERCE_PRODUCTS: readonly ProductSeed[] = [
  {
    id: 'prod-sample-0001',
    name: `베이직 반팔 티셔츠${SUFFIX}`,
    description: `부드러운 코튼 100% 소재의 반팔 티셔츠입니다.${SUFFIX}`,
    status: 'ON_SALE',
    price: 19000,
    categoryId: 'cat-sample-0001',
    thumbnailUrl: null,
    sellerId: 'seller-sample-0001',
    images: [],
    variants: [
      { id: 'variant-sample-0001', optionName: `M${SUFFIX}`, stock: 50, additionalPrice: 0 },
      { id: 'variant-sample-0002', optionName: `L${SUFFIX}`, stock: 30, additionalPrice: 1000 },
    ],
  },
  {
    id: 'prod-sample-0002',
    name: `코튼 후드 집업${SUFFIX}`,
    description: null,
    status: 'SOLD_OUT',
    price: 45000,
    categoryId: 'cat-sample-0002',
    thumbnailUrl: null,
    sellerId: 'seller-sample-0001',
    images: [],
    variants: [
      { id: 'variant-sample-0003', optionName: `Free${SUFFIX}`, stock: 0, additionalPrice: 0 },
    ],
  },
  {
    id: 'prod-sample-0003',
    name: `캔버스 에코백${SUFFIX}`,
    description: null,
    status: 'HIDDEN',
    price: 12000,
    categoryId: 'cat-sample-0001',
    thumbnailUrl: null,
    sellerId: 'seller-sample-0002',
    images: [],
    variants: [],
  },
];

const PRODUCTS_SUMMARY = { today: 1, week: 2, month: 3, total: 3 };

/** List-row projection — `ProductSummarySchema` (no `description`/`images`/`variants`). */
function productSummary(p: (typeof ECOMMERCE_PRODUCTS)[number]) {
  return {
    id: p.id,
    name: p.name,
    status: p.status,
    price: p.price,
    thumbnailUrl: p.thumbnailUrl,
    categoryId: p.categoryId,
    sellerId: p.sellerId,
  };
}

function productsFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);

  if (pathname === `${PRODUCTS_ADMIN_PATH}/summary`) return PRODUCTS_SUMMARY;

  if (pathname === PRODUCTS_ADMIN_PATH) {
    const status = query.get('status');
    const categoryId = query.get('categoryId');
    const page = intParam(query, 'page', 0);
    const size = intParam(query, 'size', 20);
    let rows: (typeof ECOMMERCE_PRODUCTS)[number][] = [...ECOMMERCE_PRODUCTS];
    if (status) rows = rows.filter((p) => p.status === status);
    if (categoryId) rows = rows.filter((p) => p.categoryId === categoryId);
    return paginateContent(rows.map(productSummary), page, size);
  }

  // Operator-plane detail path (§ 2.4.10 #2 — TASK-MONO-703). `/summary` is
  // answered above, so it never reaches this template.
  const detailMatch = pathname.match(new RegExp(`^${PRODUCTS_ADMIN_PATH}/([^/]+)$`));
  if (detailMatch) {
    const id = decodeURIComponent(detailMatch[1]);
    const found = ECOMMERCE_PRODUCTS.find((p) => p.id === id);
    if (!found) return fixtureNotFound('PRODUCT_NOT_FOUND', 'product not found');
    return found;
  }

  return undefined;
}

// ===========================================================================
// ecommerce_image — GET /api/admin/products/{id}/images (AC-7: always empty)
// ===========================================================================

const IMAGES_PATH_RE = /^\/api\/admin\/products\/[^/]+\/images$/;

function imagesFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  if (!IMAGES_PATH_RE.test(pathname)) return undefined;
  return { images: [] };
}

// ===========================================================================
// ecommerce_order — GET /api/admin/orders (+ /{id}, /summary, /insights)
// ===========================================================================

const ORDERS_PATH = '/api/admin/orders';

interface OrderItemSeed {
  productId: string;
  variantId: string | null;
  productName: string;
  optionName: string | null;
  quantity: number;
  unitPrice: number;
  sellerId: string;
}

interface OrderSeed {
  orderId: string;
  userId: string;
  status: string;
  items: OrderItemSeed[];
  shippingAddress: {
    recipient: string;
    phone: string;
    zipCode: string;
    address1: string;
    address2: string | null;
  };
  createdAt: string;
  updatedAt: string | null;
}

function orderTotal(items: OrderItemSeed[]): number {
  return items.reduce((sum, it) => sum + it.unitPrice * it.quantity, 0);
}

export const ECOMMERCE_ORDERS: readonly OrderSeed[] = [
  {
    orderId: 'order-sample-0001',
    userId: 'user-sample-0001',
    status: 'PENDING',
    items: [
      {
        productId: 'prod-sample-0001',
        variantId: 'variant-sample-0001',
        productName: `베이직 반팔 티셔츠${SUFFIX}`,
        optionName: `M${SUFFIX}`,
        quantity: 2,
        unitPrice: 19000,
        sellerId: 'seller-sample-0001',
      },
    ],
    shippingAddress: {
      recipient: `김하나${SUFFIX}`,
      phone: '010-2222-3333',
      zipCode: '06134',
      address1: `서울특별시 강남구 테헤란로 123${SUFFIX}`,
      address2: null,
    },
    createdAt: '2026-09-01T01:00:00Z',
    updatedAt: null,
  },
  {
    orderId: 'order-sample-0002',
    userId: 'user-sample-0002',
    status: 'CONFIRMED',
    items: [
      {
        productId: 'prod-sample-0002',
        variantId: 'variant-sample-0003',
        productName: `코튼 후드 집업${SUFFIX}`,
        optionName: `Free${SUFFIX}`,
        quantity: 1,
        unitPrice: 45000,
        sellerId: 'seller-sample-0001',
      },
    ],
    shippingAddress: {
      recipient: `이민준${SUFFIX}`,
      phone: '010-3333-4444',
      zipCode: '03187',
      address1: `서울특별시 종로구 세종대로 1${SUFFIX}`,
      address2: `101동 202호${SUFFIX}`,
    },
    createdAt: '2026-09-02T03:20:00Z',
    updatedAt: '2026-09-02T04:00:00Z',
  },
  {
    orderId: 'order-sample-0003',
    userId: 'user-sample-0001',
    status: 'SHIPPED',
    items: [
      {
        productId: 'prod-sample-0003',
        variantId: null,
        productName: `캔버스 에코백${SUFFIX}`,
        optionName: null,
        quantity: 3,
        unitPrice: 12000,
        sellerId: 'seller-sample-0002',
      },
    ],
    shippingAddress: {
      recipient: `김하나${SUFFIX}`,
      phone: '010-2222-3333',
      zipCode: '06134',
      address1: `서울특별시 강남구 테헤란로 123${SUFFIX}`,
      address2: null,
    },
    createdAt: '2026-08-20T05:00:00Z',
    updatedAt: '2026-08-22T09:00:00Z',
  },
  {
    orderId: 'order-sample-0004',
    userId: 'user-sample-0003',
    status: 'CANCELLED',
    items: [
      {
        productId: 'prod-sample-0001',
        variantId: 'variant-sample-0002',
        productName: `베이직 반팔 티셔츠${SUFFIX}`,
        optionName: `L${SUFFIX}`,
        quantity: 1,
        unitPrice: 20000,
        sellerId: 'seller-sample-0001',
      },
    ],
    shippingAddress: {
      recipient: `탈퇴 회원${SUFFIX}`,
      phone: '010-0000-0000',
      zipCode: '12345',
      address1: `주소 비공개${SUFFIX}`,
      address2: null,
    },
    createdAt: '2026-07-05T02:00:00Z',
    updatedAt: '2026-07-06T00:00:00Z',
  },
  {
    // CORRECTION (coordinator review) — a MULTI-SELLER order, the demo case
    // for "an order's items may belong to several sellers; an accrual is per
    // seller" (§ ecommerce_settlement below). seller-sample-0001's subtotal is
    // 19000 (item 1), seller-sample-0002's is 24000 (item 2) — both feed a
    // settlement accrual keyed to THIS order and cross-checked against it.
    orderId: 'order-sample-0005',
    userId: 'user-sample-0002',
    status: 'CONFIRMED',
    items: [
      {
        productId: 'prod-sample-0001',
        variantId: 'variant-sample-0001',
        productName: `베이직 반팔 티셔츠${SUFFIX}`,
        optionName: `M${SUFFIX}`,
        quantity: 1,
        unitPrice: 19000,
        sellerId: 'seller-sample-0001',
      },
      {
        productId: 'prod-sample-0003',
        variantId: null,
        productName: `캔버스 에코백${SUFFIX}`,
        optionName: null,
        quantity: 2,
        unitPrice: 12000,
        sellerId: 'seller-sample-0002',
      },
    ],
    shippingAddress: {
      recipient: `이민준${SUFFIX}`,
      phone: '010-3333-4444',
      zipCode: '03187',
      address1: `서울특별시 종로구 세종대로 1${SUFFIX}`,
      address2: null,
    },
    createdAt: '2026-08-25T10:00:00Z',
    updatedAt: null,
  },
];

const ORDERS_SUMMARY = { today: 0, week: 1, month: 5, total: 5 };

function orderDetail(o: OrderSeed) {
  return { ...o, totalPrice: orderTotal(o.items) };
}

function orderSummaryRow(o: OrderSeed) {
  const first = o.items[0];
  return {
    orderId: o.orderId,
    userId: o.userId,
    status: o.status,
    totalPrice: orderTotal(o.items),
    itemCount: o.items.length,
    firstItemName: first ? first.productName : '',
    createdAt: o.createdAt,
  };
}

/** Product/seller displayName lookup for the § insights ranking labels. */
function productLabel(productId: string): string {
  return ECOMMERCE_PRODUCTS.find((p) => p.id === productId)?.name ?? productId;
}
function sellerLabel(sellerId: string): string {
  return ECOMMERCE_SELLERS.find((s) => s.sellerId === sellerId)?.displayName ?? sellerId;
}

/**
 * TASK-PC-FE-284 insights fixture (§ 2.4.10 #21 — the overview's ranking
 * charts). The contract states the raw producer label for a seller ranking is
 * `seller_id` (the console overlays the real `displayName` client-side via
 * `overview-state.ts`'s `overlaySellers()`), but a raw id under the `label`
 * key would violate the R2ⓐ classification of `label` as human-readable
 * (`label-rule.ts` — the key is shared by BOTH the product-name rankings,
 * which genuinely are human-readable, and the seller-id rankings). Since this
 * fixture is wholly synthetic (ADR-MONO-074 A4 — no producer to mirror byte-
 * for-byte) and `overlaySellers()` looks the label up by `id` and OVERWRITES
 * it unconditionally when a match is found, pre-seeding the already-resolved
 * seller `displayName` here is behaviour-IDENTICAL for every consumer (the
 * overlay is a no-op on a match) while keeping the raw fixture document
 * R2ⓐ-compliant on its own. Documented here rather than silently deviating.
 */
function computeInsights() {
  const active = ECOMMERCE_ORDERS.filter((o) => o.status !== 'CANCELLED');

  const productOrderCount = new Map<string, number>();
  const productRevenue = new Map<string, number>();
  const sellerOrderCount = new Map<string, Set<string>>();
  const sellerRevenue = new Map<string, number>();

  for (const o of active) {
    const productsInOrder = new Set<string>();
    const sellersInOrder = new Set<string>();
    for (const item of o.items) {
      const revenue = item.unitPrice * item.quantity;
      productRevenue.set(item.productId, (productRevenue.get(item.productId) ?? 0) + revenue);
      sellerRevenue.set(item.sellerId, (sellerRevenue.get(item.sellerId) ?? 0) + revenue);
      productsInOrder.add(item.productId);
      sellersInOrder.add(item.sellerId);
    }
    for (const pid of productsInOrder) {
      productOrderCount.set(pid, (productOrderCount.get(pid) ?? 0) + 1);
    }
    for (const sid of sellersInOrder) {
      if (!sellerOrderCount.has(sid)) sellerOrderCount.set(sid, new Set());
      sellerOrderCount.get(sid)!.add(o.orderId);
    }
  }

  const rank = <K extends string>(
    entries: Map<K, number>,
    label: (id: K) => string,
  ) =>
    [...entries.entries()]
      .sort((a, b) => (b[1] !== a[1] ? b[1] - a[1] : a[0].localeCompare(b[0])))
      .slice(0, 5)
      .map(([id, value]) => ({ id, label: label(id), value }));

  return {
    topProductsByOrderCount: rank(productOrderCount, productLabel),
    topProductsByRevenue: rank(productRevenue, productLabel),
    topSellersByOrderCount: rank(
      new Map<string, number>(
        [...sellerOrderCount.entries()].map(([id, set]): [string, number] => [id, set.size]),
      ),
      sellerLabel,
    ),
    topSellersByRevenue: rank(sellerRevenue, sellerLabel),
  };
}

function ordersFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);

  if (pathname === `${ORDERS_PATH}/summary`) return ORDERS_SUMMARY;
  if (pathname === `${ORDERS_PATH}/insights`) return computeInsights();

  if (pathname === ORDERS_PATH) {
    const status = query.get('status');
    const page = intParam(query, 'page', 0);
    const size = intParam(query, 'size', 20);
    const rows = status ? ECOMMERCE_ORDERS.filter((o) => o.status === status) : ECOMMERCE_ORDERS;
    return paginateContent(rows.map(orderSummaryRow), page, size);
  }

  const detailMatch = pathname.match(new RegExp(`^${ORDERS_PATH}/([^/]+)$`));
  if (detailMatch) {
    const id = decodeURIComponent(detailMatch[1]);
    const found = ECOMMERCE_ORDERS.find((o) => o.orderId === id);
    if (!found) return fixtureNotFound('ORDER_NOT_FOUND', 'order not found');
    return orderDetail(found);
  }

  return undefined;
}

// ===========================================================================
// ecommerce_user — GET /api/admin/users (+ /{userId}, /summary)
// ===========================================================================

const USERS_PATH = '/api/admin/users';

export const ECOMMERCE_USERS = [
  {
    userId: 'user-sample-0001',
    email: `hana.kim.sample@example.com${SUFFIX}`,
    name: `김하나${SUFFIX}`,
    nickname: `hanakim${SUFFIX}`,
    status: 'ACTIVE',
    createdAt: '2026-01-15T00:00:00Z',
    phone: '010-2222-3333',
    profileImageUrl: null,
    updatedAt: '2026-08-01T00:00:00Z',
  },
  {
    userId: 'user-sample-0002',
    email: `minjun.lee.sample@example.com${SUFFIX}`,
    name: `이민준${SUFFIX}`,
    nickname: null,
    status: 'SUSPENDED',
    createdAt: '2026-03-02T00:00:00Z',
    phone: null,
    profileImageUrl: null,
    // `updatedAt` is `.optional()` but NOT `.nullable()` on `UserDetailSchema`
    // (unlike email/name/nickname/phone/profileImageUrl) — `undefined` (JSON-
    // stringified away, so the field is genuinely ABSENT on the wire) is the
    // right "no value" here, not `null`.
    updatedAt: undefined,
  },
  {
    // Anonymized/withdrawn — user-service nulls email/name (never tombstones)
    // on `account.deleted` (see `user-types.ts` TOLERANCE doc). Kept to prove
    // the fixture (like the producer) never crashes the list on this row.
    userId: 'user-sample-0003',
    email: null,
    name: null,
    nickname: null,
    status: 'WITHDRAWN',
    createdAt: '2026-04-11T00:00:00Z',
    phone: null,
    profileImageUrl: null,
    updatedAt: undefined,
  },
] as const;

const USERS_SUMMARY = { today: 0, week: 1, month: 2, total: 3 };

/** Strips the R2ⓐ suffix + trims + lowercases, so a plain-typed OR a
 *  copy-pasted (suffixed) address matches the same row (mirrors `iam.ts`
 *  accounts' `normalizeEmail`). Unlike accounts, this is a SUBSTRING match —
 *  the users screen exposes a free-text "이메일 검색" box, not an exact
 *  single-lookup, so narrowing (not exact identity) is the real producer
 *  behaviour AC-4 is checking. */
function normalizeEmail(value: string): string {
  return value.replace(SUFFIX, '').trim().toLowerCase();
}

function usersFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);

  if (pathname === `${USERS_PATH}/summary`) return USERS_SUMMARY;

  const detailMatch = pathname.match(new RegExp(`^${USERS_PATH}/([^/]+)$`));
  if (detailMatch) {
    const id = decodeURIComponent(detailMatch[1]);
    const found = ECOMMERCE_USERS.find((u) => u.userId === id);
    if (!found) return fixtureNotFound('USER_PROFILE_NOT_FOUND', 'user profile not found');
    return found;
  }

  if (pathname !== USERS_PATH) return undefined;
  const status = query.get('status');
  const email = query.get('email');
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  let rows: (typeof ECOMMERCE_USERS)[number][] = [...ECOMMERCE_USERS];
  if (status) rows = rows.filter((u) => u.status === status);
  if (email && email.trim() !== '') {
    const needle = normalizeEmail(email);
    rows = rows.filter((u) => u.email !== null && normalizeEmail(u.email).includes(needle));
  }
  return paginateContent(rows, page, size);
}

// ===========================================================================
// ecommerce_promotion — GET /api/promotions (+ /{id}, /summary)
// ===========================================================================

const PROMOTIONS_PATH = '/api/promotions';

export const ECOMMERCE_PROMOTIONS = [
  {
    promotionId: 'promo-sample-0001',
    name: `여름 시즌 15% 할인${SUFFIX}`,
    description: `여름 신상품 대상 15% 할인 프로모션입니다.${SUFFIX}`,
    discountType: 'PERCENTAGE',
    discountValue: 15,
    maxDiscountAmount: 10000,
    issuedCount: 42,
    maxIssuanceCount: 100,
    startDate: '2026-08-01T00:00:00Z',
    endDate: '2026-08-31T23:59:59Z',
    status: 'ACTIVE',
    createdAt: '2026-07-20T00:00:00Z',
    updatedAt: '2026-08-01T00:00:00Z',
  },
  {
    promotionId: 'promo-sample-0002',
    name: `신규가입 5000원 쿠폰${SUFFIX}`,
    description: null,
    discountType: 'FIXED',
    discountValue: 5000,
    maxDiscountAmount: 5000,
    issuedCount: 100,
    maxIssuanceCount: 100,
    startDate: '2026-01-01T00:00:00Z',
    endDate: '2026-06-30T23:59:59Z',
    status: 'ENDED',
    createdAt: '2025-12-15T00:00:00Z',
    updatedAt: '2026-07-01T00:00:00Z',
  },
  {
    promotionId: 'promo-sample-0003',
    name: `추석 프로모션 예약${SUFFIX}`,
    description: `추석 연휴 대상 사전 예약 프로모션입니다.${SUFFIX}`,
    discountType: 'PERCENTAGE',
    discountValue: 10,
    maxDiscountAmount: 20000,
    issuedCount: 0,
    maxIssuanceCount: 50,
    startDate: '2026-10-01T00:00:00Z',
    endDate: '2026-10-10T23:59:59Z',
    status: 'SCHEDULED',
    createdAt: '2026-09-10T00:00:00Z',
    updatedAt: null,
  },
] as const;

const PROMOTIONS_SUMMARY = { today: 0, week: 1, month: 3, total: 3 };

function promotionsFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);

  if (pathname === `${PROMOTIONS_PATH}/summary`) return PROMOTIONS_SUMMARY;

  const detailMatch = pathname.match(new RegExp(`^${PROMOTIONS_PATH}/([^/]+)$`));
  if (detailMatch) {
    const id = decodeURIComponent(detailMatch[1]);
    const found = ECOMMERCE_PROMOTIONS.find((p) => p.promotionId === id);
    if (!found) return fixtureNotFound('PROMOTION_NOT_FOUND', 'promotion not found');
    return found;
  }

  if (pathname !== PROMOTIONS_PATH) return undefined;
  const status = query.get('status');
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  const rows = status
    ? ECOMMERCE_PROMOTIONS.filter((p) => p.status === status)
    : ECOMMERCE_PROMOTIONS;
  return paginateContent(rows, page, size);
}

// ===========================================================================
// ecommerce_shipping — GET /api/shippings (+ /summary) — NO detail-by-id GET
// ===========================================================================

const SHIPPINGS_PATH = '/api/shippings';

export const ECOMMERCE_SHIPPINGS = [
  {
    shippingId: 'shipping-sample-0001',
    orderId: 'order-sample-0001',
    userId: 'user-sample-0001',
    status: 'PREPARING',
    trackingNumber: null,
    carrier: null,
    wmsRouted: false,
    statusHistory: [{ status: 'PREPARING', changedAt: '2026-09-01T01:30:00Z' }],
    createdAt: '2026-09-01T01:30:00Z',
    updatedAt: '2026-09-01T01:30:00Z',
  },
  {
    shippingId: 'shipping-sample-0002',
    orderId: 'order-sample-0002',
    userId: 'user-sample-0002',
    status: 'SHIPPED',
    trackingNumber: '1234567890123',
    carrier: 'CJ대한통운',
    wmsRouted: true,
    statusHistory: [
      { status: 'PREPARING', changedAt: '2026-09-02T04:10:00Z' },
      { status: 'SHIPPED', changedAt: '2026-09-03T00:00:00Z' },
    ],
    createdAt: '2026-09-02T04:10:00Z',
    updatedAt: '2026-09-03T00:00:00Z',
  },
  {
    shippingId: 'shipping-sample-0003',
    orderId: 'order-sample-0003',
    userId: 'user-sample-0001',
    status: 'DELIVERED',
    trackingNumber: '9876543210987',
    carrier: '한진택배',
    wmsRouted: false,
    statusHistory: [
      { status: 'PREPARING', changedAt: '2026-08-20T05:30:00Z' },
      { status: 'SHIPPED', changedAt: '2026-08-21T00:00:00Z' },
      { status: 'DELIVERED', changedAt: '2026-08-22T09:00:00Z' },
    ],
    createdAt: '2026-08-20T05:30:00Z',
    updatedAt: '2026-08-22T09:00:00Z',
  },
] as const;

const SHIPPINGS_SUMMARY = { today: 0, week: 1, month: 3, total: 3 };

function shippingsFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);
  if (pathname === `${SHIPPINGS_PATH}/summary`) return SHIPPINGS_SUMMARY;
  if (pathname !== SHIPPINGS_PATH) return undefined;
  const status = query.get('status');
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  const rows = status
    ? ECOMMERCE_SHIPPINGS.filter((s) => s.status === status)
    : ECOMMERCE_SHIPPINGS;
  return paginateContent(rows, page, size);
}

// ===========================================================================
// ecommerce_notification — GET /api/notifications/templates (+ /{id}, /summary)
// ===========================================================================

const TEMPLATES_PATH = '/api/notifications/templates';

export const ECOMMERCE_TEMPLATES = [
  {
    templateId: 'template-sample-0001',
    type: 'ORDER_PLACED',
    channel: 'EMAIL',
    subject: `주문이 완료되었습니다${SUFFIX}`,
    body: `주문해 주셔서 감사합니다. 주문 내역은 마이페이지에서 확인하실 수 있습니다.${SUFFIX}`,
    createdAt: '2026-02-01T00:00:00Z',
    updatedAt: '2026-06-01T00:00:00Z',
  },
  {
    templateId: 'template-sample-0002',
    type: 'SHIPPING_STATUS_CHANGED',
    channel: 'SMS',
    subject: `배송 상태가 변경되었습니다${SUFFIX}`,
    body: `주문하신 상품의 배송 상태가 변경되었습니다.${SUFFIX}`,
    createdAt: '2026-02-05T00:00:00Z',
    updatedAt: null,
  },
  {
    templateId: 'template-sample-0003',
    type: 'WELCOME',
    channel: 'PUSH',
    subject: `가입을 환영합니다${SUFFIX}`,
    body: `회원가입을 환영합니다! 첫 구매 시 사용할 수 있는 쿠폰을 확인해보세요.${SUFFIX}`,
    createdAt: '2026-01-10T00:00:00Z',
    updatedAt: '2026-05-01T00:00:00Z',
  },
] as const;

const TEMPLATES_SUMMARY = { today: 0, week: 1, month: 3, total: 3 };

function templateSummaryRow(t: (typeof ECOMMERCE_TEMPLATES)[number]) {
  return {
    templateId: t.templateId,
    type: t.type,
    channel: t.channel,
    subject: t.subject,
    createdAt: t.createdAt,
    updatedAt: t.updatedAt,
  };
}

function notificationsFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);

  if (pathname === `${TEMPLATES_PATH}/summary`) return TEMPLATES_SUMMARY;

  const detailMatch = pathname.match(new RegExp(`^${TEMPLATES_PATH}/([^/]+)$`));
  if (detailMatch) {
    const id = decodeURIComponent(detailMatch[1]);
    const found = ECOMMERCE_TEMPLATES.find((t) => t.templateId === id);
    if (!found) return fixtureNotFound('TEMPLATE_NOT_FOUND', 'template not found');
    return found;
  }

  if (pathname !== TEMPLATES_PATH) return undefined;
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  return paginateContent(ECOMMERCE_TEMPLATES.map(templateSummaryRow), page, size);
}

// ===========================================================================
// ecommerce_settlement — GET /api/admin/settlements/**
// ===========================================================================

const SETTLEMENTS_PATH = '/api/admin/settlements';

const ECOMMERCE_COMMISSION_RATES = [
  { sellerId: 'seller-sample-0001', rateBps: 1000, source: 'PLATFORM_DEFAULT' },
  { sellerId: 'seller-sample-0002', rateBps: 1200, source: 'SELLER_OVERRIDE' },
  { sellerId: 'seller-sample-0003', rateBps: 1000, source: 'PLATFORM_DEFAULT' },
] as const;

/**
 * CORRECTION (coordinator review, 2026-09-16 UTC) — every accrual's
 * `grossMinor` now equals the REFERENCING order's own total as
 * `/ecommerce/orders` shows it (or that seller's SUBTOTAL of the order, for
 * the multi-seller `order-sample-0005`). Before this correction the accrual
 * amounts were independent numbers that happened to sum correctly to the
 * seller balance in isolation, but did not match the order screen at all
 * (`minorToWon` renders minor units AS won on both screens, so a visitor
 * comparing an order to its settlement line saw two different numbers for
 * the same money — the exact "합성이어도 산술은 맞아야 한다" Edge Case this
 * ticket's own body names). Σ(accrual lines) per seller below is kept
 * byte-consistent with `ECOMMERCE_ACCRUALS` (asserted by a schema/arithmetic
 * test AND by `tests/unit/sample-fixtures-schema-ecommerce.test.ts`'s
 * accrual↔order cross-screen test, not just eyeballed).
 */
const ECOMMERCE_SELLER_BALANCES = [
  {
    sellerId: 'seller-sample-0001',
    grossMinor: 92000,
    platformCommissionMinor: 9200,
    accruedNetMinor: 82800,
    accrualCount: 4,
  },
  {
    sellerId: 'seller-sample-0002',
    grossMinor: 60000,
    platformCommissionMinor: 7200,
    accruedNetMinor: 52800,
    accrualCount: 2,
  },
  {
    sellerId: 'seller-sample-0003',
    grossMinor: 0,
    platformCommissionMinor: 0,
    accruedNetMinor: 0,
    accrualCount: 0,
  },
] as const;

/**
 * Append-only ledger, cross-screen-consistent with `ECOMMERCE_ORDERS` (see
 * the CORRECTION note on `ECOMMERCE_SELLER_BALANCES` above):
 *   - accrual-0001 = order-sample-0001's FULL total (38000, single-seller).
 *   - accrual-0002/-0003 = order-sample-0002's FULL total (45000) then a
 *     PARTIAL REVERSAL clawback (-10000, ≤ the 45000 accrued — a realistic
 *     partial-refund shape) on the SAME order.
 *   - accrual-0004 = seller-sample-0001's SUBTOTAL (19000, item 1) of the
 *     MULTI-SELLER order-sample-0005 (total 43000) — NOT the order's total.
 *   - accrual-0005 = order-sample-0003's FULL total (36000, single-seller).
 *   - accrual-0006 = seller-sample-0002's SUBTOTAL (24000, item 2) of the
 *     SAME multi-seller order-sample-0005.
 * `order-sample-0004` (CANCELLED) carries NO accrual — a cancelled order
 * never accrues seller commission.
 */
export const ECOMMERCE_ACCRUALS = [
  {
    accrualId: 'accrual-sample-0001',
    orderId: 'order-sample-0001',
    paymentId: 'payment-sample-0001',
    sellerId: 'seller-sample-0001',
    type: 'ACCRUAL',
    grossMinor: 38000,
    rateBps: 1000,
    commissionMinor: 3800,
    sellerNetMinor: 34200,
    occurredAt: '2026-08-05T00:00:00Z',
  },
  {
    accrualId: 'accrual-sample-0002',
    orderId: 'order-sample-0002',
    paymentId: 'payment-sample-0002',
    sellerId: 'seller-sample-0001',
    type: 'ACCRUAL',
    grossMinor: 45000,
    rateBps: 1000,
    commissionMinor: 4500,
    sellerNetMinor: 40500,
    occurredAt: '2026-08-10T00:00:00Z',
  },
  {
    accrualId: 'accrual-sample-0003',
    orderId: 'order-sample-0002',
    paymentId: 'payment-sample-0002',
    sellerId: 'seller-sample-0001',
    type: 'REVERSAL',
    grossMinor: -10000,
    rateBps: 1000,
    commissionMinor: -1000,
    sellerNetMinor: -9000,
    occurredAt: '2026-08-12T00:00:00Z',
  },
  {
    accrualId: 'accrual-sample-0004',
    orderId: 'order-sample-0005',
    paymentId: 'payment-sample-0004',
    sellerId: 'seller-sample-0001',
    type: 'ACCRUAL',
    grossMinor: 19000,
    rateBps: 1000,
    commissionMinor: 1900,
    sellerNetMinor: 17100,
    occurredAt: '2026-08-25T10:30:00Z',
  },
  {
    accrualId: 'accrual-sample-0005',
    orderId: 'order-sample-0003',
    paymentId: 'payment-sample-0005',
    sellerId: 'seller-sample-0002',
    type: 'ACCRUAL',
    grossMinor: 36000,
    rateBps: 1200,
    commissionMinor: 4320,
    sellerNetMinor: 31680,
    occurredAt: '2026-08-20T05:30:00Z',
  },
  {
    accrualId: 'accrual-sample-0006',
    orderId: 'order-sample-0005',
    paymentId: 'payment-sample-0006',
    sellerId: 'seller-sample-0002',
    type: 'ACCRUAL',
    grossMinor: 24000,
    rateBps: 1200,
    commissionMinor: 2880,
    sellerNetMinor: 21120,
    occurredAt: '2026-08-25T10:30:00Z',
  },
] as const;

/** period-sample-0001 is CLOSED and its 2 payouts are EXACTLY the folded
 *  accrual sums above (sellerCount = payouts.length = 2 — the "합계 = 행 합"
 *  edge case for this domain, since the payouts screen has no other total to
 *  cross-check). period-sample-0002 is OPEN (no accrual line falls in its
 *  Sept window) — 0 payouts, the deliberate empty-state screen (mirrors
 *  TASK-PC-FE-283's accounts-email-miss choice, one per domain ticket). */
export const ECOMMERCE_SETTLEMENT_PERIODS = [
  {
    periodId: 'period-sample-0001',
    from: '2026-08-01T00:00:00Z',
    to: '2026-09-01T00:00:00Z',
    status: 'CLOSED',
    closedAt: '2026-09-02T00:00:00Z',
    sellerCount: 2,
  },
  {
    periodId: 'period-sample-0002',
    from: '2026-09-01T00:00:00Z',
    to: '2026-10-01T00:00:00Z',
    status: 'OPEN',
    closedAt: null,
    sellerCount: null,
  },
] as const;

const ECOMMERCE_PAYOUTS_BY_PERIOD: Readonly<Record<string, readonly unknown[]>> = {
  'period-sample-0001': [
    {
      payoutId: 'payout-sample-0001',
      sellerId: 'seller-sample-0001',
      payableNetMinor: 82800,
      commissionMinor: 9200,
      accrualCount: 4,
      status: 'PAID',
      payoutReference: 'PO-SAMPLE-0001',
      paidAt: '2026-09-03T00:00:00Z',
    },
    {
      payoutId: 'payout-sample-0002',
      sellerId: 'seller-sample-0002',
      payableNetMinor: 52800,
      commissionMinor: 7200,
      accrualCount: 2,
      status: 'PAID',
      payoutReference: 'PO-SAMPLE-0002',
      paidAt: '2026-09-03T00:00:00Z',
    },
  ],
  'period-sample-0002': [],
};

function settlementsFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);

  if (pathname === `${SETTLEMENTS_PATH}/accruals`) {
    const sellerId = query.get('sellerId');
    const orderId = query.get('orderId');
    const page = intParam(query, 'page', 0);
    const size = intParam(query, 'size', 20);
    let rows: (typeof ECOMMERCE_ACCRUALS)[number][] = [...ECOMMERCE_ACCRUALS];
    if (sellerId) rows = rows.filter((a) => a.sellerId === sellerId);
    if (orderId) rows = rows.filter((a) => a.orderId === orderId);
    return paginateItems(rows, page, size);
  }

  const balanceMatch = pathname.match(
    new RegExp(`^${SETTLEMENTS_PATH}/sellers/([^/]+)/balance$`),
  );
  if (balanceMatch) {
    const sellerId = decodeURIComponent(balanceMatch[1]);
    const found = ECOMMERCE_SELLER_BALANCES.find((b) => b.sellerId === sellerId);
    if (!found) return fixtureNotFound('SETTLEMENT_NOT_FOUND', 'settlement not found');
    return { ...found, asOf: SAMPLE_AS_OF };
  }

  const rateMatch = pathname.match(
    new RegExp(`^${SETTLEMENTS_PATH}/commission-rates/([^/]+)$`),
  );
  if (rateMatch) {
    const sellerId = decodeURIComponent(rateMatch[1]);
    const found = ECOMMERCE_COMMISSION_RATES.find((r) => r.sellerId === sellerId);
    if (!found) return fixtureNotFound('SETTLEMENT_NOT_FOUND', 'settlement not found');
    return found;
  }

  if (pathname === `${SETTLEMENTS_PATH}/periods`) {
    const page = intParam(query, 'page', 0);
    const size = intParam(query, 'size', 20);
    return paginateItems(ECOMMERCE_SETTLEMENT_PERIODS, page, size);
  }

  const payoutsMatch = pathname.match(
    new RegExp(`^${SETTLEMENTS_PATH}/periods/([^/]+)/payouts$`),
  );
  if (payoutsMatch) {
    const periodId = decodeURIComponent(payoutsMatch[1]);
    if (!ECOMMERCE_SETTLEMENT_PERIODS.some((p) => p.periodId === periodId)) {
      return fixtureNotFound('SETTLEMENT_NOT_FOUND', 'settlement not found');
    }
    const page = intParam(query, 'page', 0);
    const size = intParam(query, 'size', 20);
    const rows = ECOMMERCE_PAYOUTS_BY_PERIOD[periodId] ?? [];
    return paginateItems(rows, page, size);
  }

  return undefined;
}

// ===========================================================================
// exports
// ===========================================================================

export const ECOMMERCE_FIXTURE_HANDLERS: Readonly<Record<string, EcommerceFixtureHandler>> = {
  'ecommerce:ecommerce': productsFixture,
  'ecommerce:ecommerce_image': imagesFixture,
  'ecommerce:ecommerce_order': ordersFixture,
  'ecommerce:ecommerce_user': usersFixture,
  'ecommerce:ecommerce_promotion': promotionsFixture,
  'ecommerce:ecommerce_shipping': shippingsFixture,
  'ecommerce:ecommerce_notification': notificationsFixture,
  'ecommerce:ecommerce_seller': sellersFixture,
  'ecommerce:ecommerce_settlement': settlementsFixture,
};

/**
 * The AGGREGATE of every seed array per surface (not just one branch's
 * output), for the R2ⓐ label guard — same reasoning as `iam.ts`'s identical
 * export.
 */
export const ECOMMERCE_FIXTURE_DOCUMENTS: Readonly<Record<string, unknown>> = {
  'ecommerce:ecommerce': { content: ECOMMERCE_PRODUCTS },
  'ecommerce:ecommerce_image': {},
  'ecommerce:ecommerce_order': {
    content: ECOMMERCE_ORDERS,
    insights: computeInsights(),
  },
  'ecommerce:ecommerce_user': { content: ECOMMERCE_USERS },
  'ecommerce:ecommerce_promotion': { content: ECOMMERCE_PROMOTIONS },
  'ecommerce:ecommerce_shipping': { content: ECOMMERCE_SHIPPINGS },
  'ecommerce:ecommerce_notification': { content: ECOMMERCE_TEMPLATES },
  'ecommerce:ecommerce_seller': { content: ECOMMERCE_SELLERS },
  'ecommerce:ecommerce_settlement': {
    items: ECOMMERCE_ACCRUALS,
    rates: ECOMMERCE_COMMISSION_RATES,
    balances: ECOMMERCE_SELLER_BALANCES,
    periods: ECOMMERCE_SETTLEMENT_PERIODS,
    payouts: Object.values(ECOMMERCE_PAYOUTS_BY_PERIOD).flat(),
  },
};
