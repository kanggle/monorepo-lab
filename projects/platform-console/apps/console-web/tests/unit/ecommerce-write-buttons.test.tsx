/**
 * TASK-PC-FE-346 — E-commerce write buttons follow the area `*_WRITE` roles (contract § 2.4.3.4
 * «Read-only sets hide write buttons», `Declared today` E-commerce rows).
 *
 * Each of the 7 screens takes `canWrite` (REQUIRED — a caller cannot forget it). `false` hides every
 * mutation control and says «조회 전용»; `true` is the control group (nothing hidden, no note). The
 * page-level wiring (`canWriteOn(href, getDomainBusinessRoles(...))`) is pinned in
 * `ecommerce-write-buttons-pages.test.tsx`; the declarations in `console-nav-business-permission.test.ts`.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactElement, ReactNode } from 'react';
import {
  ProductsScreen,
  ProductDetail,
  SellersScreen,
  SellerDetail,
  OrderDetail,
  ShippingsScreen,
  PromotionsScreen,
  PromotionDetail,
  SettlementsScreen,
  PeriodPayoutsScreen,
  NotificationsScreen,
} from '@/features/ecommerce-ops';
import { ImageManager } from '@/features/ecommerce-ops/components/ImageManager';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), refresh: vi.fn() }),
  usePathname: () => '/ecommerce',
}));

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

/** Every GET resolves to `{}` (sections degrade quietly); image list gets its own fixture. */
function stubFetch(imageList?: unknown) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: unknown) => {
      const url = String(input);
      const body = url.includes('/images') && imageList !== undefined ? imageList : {};
      return new Response(JSON.stringify(body), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }),
  );
}

beforeEach(() => {
  vi.unstubAllGlobals();
  stubFetch();
});

const page = { page: 0, size: 20, totalElements: 1 };

const PRODUCTS = {
  content: [{ id: 'p-1', name: 'Tee', status: 'ON_SALE', price: 12000, sellerId: 's-1' }],
  ...page,
};
const PRODUCT = {
  id: 'p-1',
  name: 'Tee',
  description: 'a tee',
  status: 'ON_SALE',
  price: 12000,
  sellerId: 's-1',
  images: [],
  variants: [{ id: 'v-1', optionName: 'M', stock: 10, additionalPrice: 0 }],
};
const SELLERS = {
  content: [
    { sellerId: 'acme', displayName: 'Acme', status: 'ACTIVE', createdAt: '2026-06-14T00:00:00Z' },
  ],
  ...page,
};
const SELLER = {
  sellerId: 'acme',
  displayName: 'Acme',
  status: 'ACTIVE',
  createdAt: '2026-06-14T00:00:00Z',
};
const ORDER = {
  orderId: 'o-1',
  userId: 'u-1',
  status: 'PENDING',
  totalPrice: 10000,
  items: [],
  shippingAddress: {
    recipient: '홍길동',
    phone: '010-0000-0000',
    zipCode: '12345',
    address1: '서울',
    address2: null,
  },
  createdAt: '2026-06-14T00:00:00Z',
};
const SHIPPINGS = {
  content: [
    { shippingId: 'sh-0', orderId: 'o-0', status: 'PREPARING', createdAt: '2026-06-14T00:00:00Z' },
    { shippingId: 'sh-1', orderId: 'o-1', status: 'SHIPPED', createdAt: '2026-06-14T00:00:00Z' },
  ],
  page: 0,
  size: 20,
  totalElements: 2,
};
const PROMOTIONS = {
  content: [
    {
      promotionId: 'pr-1',
      name: 'Summer',
      discountType: 'PERCENTAGE',
      discountValue: 10,
      issuedCount: 0,
      maxIssuanceCount: 100,
      startDate: '2026-06-01',
      endDate: '2026-09-01',
      status: 'ACTIVE',
    },
  ],
  ...page,
};
const PROMOTION = { ...PROMOTIONS.content[0], description: null, maxDiscountAmount: null };
const ACCRUALS = { items: [], page: 0, size: 20, totalElements: 0 };
const PERIODS = {
  items: [
    {
      periodId: '2026-07',
      from: '2026-07-01T00:00:00Z',
      to: '2026-08-01T00:00:00Z',
      status: 'OPEN',
      closedAt: null,
      sellerCount: 2,
    },
  ],
  ...page,
};
const PAYOUTS = {
  items: [
    {
      payoutId: 'po-1',
      sellerId: 'acme',
      payableNetMinor: 90000,
      commissionMinor: 10000,
      accrualCount: 1,
      status: 'PENDING',
      payoutReference: null,
      paidAt: null,
    },
  ],
  ...page,
};
const TEMPLATES = {
  content: [
    {
      templateId: 't-1',
      type: 'ORDER_CONFIRMED',
      channel: 'EMAIL',
      subject: 'Hello',
      createdAt: '2026-06-14T00:00:00Z',
    },
  ],
  ...page,
};

interface Case {
  /** the screen — `canWrite` is what the page computes from `canWriteOn`. */
  name: string;
  ui: (canWrite: boolean) => ReactElement;
  /** every mutation control of the screen; all must vanish when `canWrite=false`. */
  writeTestIds: string[];
  /** the read-only note this screen shows instead. */
  note: string;
  /** a read control that must stay either way (the screen is not blanked). */
  kept: string;
}

// The `as never` casts: fixtures carry only the fields the view renders, not the full zod output.
const CASES: Case[] = [
  {
    name: '/ecommerce/products — list',
    ui: (canWrite) => <ProductsScreen products={PRODUCTS as never} canWrite={canWrite} />,
    writeTestIds: ['product-new-link', 'product-edit-0', 'product-delete-0'],
    note: 'product-read-only-note',
    kept: 'product-detail-0',
  },
  {
    name: '/ecommerce/products/[id] — detail (정보 · 옵션 · 재고)',
    ui: (canWrite) => <ProductDetail product={PRODUCT as never} canWrite={canWrite} />,
    writeTestIds: [
      'product-detail-edit',
      'product-detail-delete',
      'variant-add-row',
      'variant-editbtn-0',
      'variant-delete-0',
      'stock-adjust-section',
      'stock-adjust-open-v-1',
    ],
    note: 'product-detail-read-only-note',
    kept: 'variant-table',
  },
  {
    name: '/ecommerce/sellers — list',
    ui: (canWrite) => <SellersScreen sellers={SELLERS as never} canWrite={canWrite} />,
    writeTestIds: ['seller-new-link'],
    note: 'seller-read-only-note',
    kept: 'seller-detail-0',
  },
  {
    name: '/ecommerce/sellers/[id] — detail (생명주기 · 구성원 초대)',
    ui: (canWrite) => <SellerDetail seller={SELLER as never} canWrite={canWrite} />,
    writeTestIds: [
      'seller-action-suspend',
      'seller-action-close',
      'seller-invite-form',
      'seller-invite-submit',
    ],
    note: 'seller-detail-read-only-note',
    kept: 'seller-members',
  },
  {
    name: '/ecommerce/orders/[id] — detail (상태 전이)',
    ui: (canWrite) => <OrderDetail order={ORDER as never} canWrite={canWrite} />,
    writeTestIds: ['order-status-btn-cancelled'],
    note: 'order-read-only-note',
    kept: 'order-items',
  },
  {
    name: '/ecommerce/shippings — list (상태 전이 · 추적 동기화)',
    ui: (canWrite) => <ShippingsScreen shippings={SHIPPINGS as never} canWrite={canWrite} />,
    writeTestIds: ['shipping-transition-0', 'shipping-transition-1', 'shipping-refresh-1'],
    note: 'shipping-read-only-note',
    kept: 'shipping-row-0',
  },
  {
    name: '/ecommerce/promotions — list',
    ui: (canWrite) => <PromotionsScreen promotions={PROMOTIONS as never} canWrite={canWrite} />,
    writeTestIds: ['promotion-new-link', 'promotion-delete-0'],
    note: 'promotion-read-only-note',
    kept: 'promotion-detail-0',
  },
  {
    name: '/ecommerce/promotions/[id] — detail (수정 · 삭제 · 쿠폰 발급)',
    ui: (canWrite) => <PromotionDetail promotion={PROMOTION as never} canWrite={canWrite} />,
    writeTestIds: ['promotion-detail-edit', 'promotion-detail-delete', 'promotion-detail-issue'],
    note: 'promotion-detail-read-only-note',
    kept: 'promotion-detail-back',
  },
  {
    name: '/ecommerce/settlements — 수수료율 · 기간 개시 · 마감',
    ui: (canWrite) => (
      <SettlementsScreen accruals={ACCRUALS as never} periods={PERIODS as never} canWrite={canWrite} />
    ),
    writeTestIds: ['settlements-rate-set-form', 'settlements-period-open-form', 'period-close-0'],
    note: 'settlements-read-only-note',
    kept: 'period-detail-0',
  },
  {
    name: '/ecommerce/settlements/periods/[id] — 지급 실행',
    ui: (canWrite) => (
      <PeriodPayoutsScreen periodId="2026-07" initialPayouts={PAYOUTS as never} canWrite={canWrite} />
    ),
    writeTestIds: ['settlements-payouts-execute'],
    note: 'settlements-payouts-read-only-note',
    kept: 'settlements-period-back',
  },
  {
    name: '/ecommerce/notifications/templates — list',
    ui: (canWrite) => <NotificationsScreen templates={TEMPLATES as never} canWrite={canWrite} />,
    writeTestIds: ['notification-new-link', 'notification-edit-0'],
    note: 'notification-read-only-note',
    kept: 'notification-table',
  },
];

describe('TASK-PC-FE-346 — canWrite=false hides every write control and says «조회 전용»', () => {
  it.each(CASES)('$name', ({ ui, writeTestIds, note, kept }) => {
    render(ui(false), { wrapper: wrapper() });
    for (const id of writeTestIds) expect(screen.queryByTestId(id), id).toBeNull();
    expect(screen.getByTestId(note)).toHaveTextContent('조회 전용');
    expect(screen.getByTestId(kept), `${kept} stays`).toBeInTheDocument();
  });

  it.each(CASES)('control (canWrite=true): $name — every control present, no note', ({ ui, writeTestIds, note }) => {
    render(ui(true), { wrapper: wrapper() });
    for (const id of writeTestIds) expect(screen.queryByTestId(id), id).not.toBeNull();
    expect(screen.queryByTestId(note)).toBeNull();
  });
});

describe('TASK-PC-FE-346 — product images (ImageManager, loaded asynchronously)', () => {
  const IMAGES = {
    images: [
      { imageId: 'i-0', objectKey: 'k0', sortOrder: 0, isPrimary: true, url: 'https://x/0.png' },
      { imageId: 'i-1', objectKey: 'k1', sortOrder: 1, isPrimary: false, url: 'https://x/1.png' },
    ],
  };

  it('canWrite=false → thumbnails stay; no 대표로 · 삭제 · upload field', async () => {
    stubFetch(IMAGES);
    render(<ImageManager productId="p-1" canWrite={false} />, { wrapper: wrapper() });
    await waitFor(() => expect(screen.getByTestId('image-row-1')).toBeInTheDocument());
    expect(screen.queryByTestId('image-set-primary-1')).toBeNull();
    expect(screen.queryByTestId('image-delete-0')).toBeNull();
    expect(screen.queryByTestId('image-delete-1')).toBeNull();
    expect(screen.queryByTestId('image-upload-field')).toBeNull();
  });

  it('control: canWrite=true → 대표로 · 삭제 · upload field', async () => {
    stubFetch(IMAGES);
    render(<ImageManager productId="p-1" canWrite />, { wrapper: wrapper() });
    await waitFor(() => expect(screen.getByTestId('image-row-1')).toBeInTheDocument());
    expect(screen.getByTestId('image-set-primary-1')).toBeInTheDocument();
    expect(screen.getByTestId('image-delete-1')).toBeInTheDocument();
    expect(screen.getByTestId('image-upload-field')).toBeInTheDocument();
  });
});

describe('TASK-PC-FE-365 — a BACKORDERED order offers cancel only', () => {
  it('canWrite=true → only the 취소 button (no 확인/confirm, no BACKORDERED target)', () => {
    render(<OrderDetail order={{ ...ORDER, status: 'BACKORDERED' } as never} canWrite />, {
      wrapper: wrapper(),
    });
    expect(screen.getByTestId('order-status-btn-cancelled')).toBeInTheDocument();
    expect(screen.queryByTestId('order-status-btn-confirmed')).toBeNull();
    expect(screen.queryByTestId('order-status-btn-backordered')).toBeNull();
  });

  it('canWrite=false → read-only note shown (the cancel control is hidden)', () => {
    render(<OrderDetail order={{ ...ORDER, status: 'BACKORDERED' } as never} canWrite={false} />, {
      wrapper: wrapper(),
    });
    expect(screen.queryByTestId('order-status-btn-cancelled')).toBeNull();
    expect(screen.getByTestId('order-read-only-note')).toHaveTextContent('조회 전용');
  });
});

describe('TASK-PC-FE-366 — a PENDING order offers cancel only (no operator confirm)', () => {
  it('canWrite=true → only the 취소 button, no 확인/confirm', () => {
    render(<OrderDetail order={{ ...ORDER, status: 'PENDING' } as never} canWrite />, {
      wrapper: wrapper(),
    });
    expect(screen.getByTestId('order-status-btn-cancelled')).toHaveTextContent('취소');
    expect(screen.queryByTestId('order-status-btn-confirmed')).toBeNull();
  });
});

describe('TASK-PC-FE-346 — order screens that have no mutation control carry no note', () => {
  it('a terminal order (no allowed transition) with canWrite=false shows no note (nothing was hidden)', () => {
    render(<OrderDetail order={{ ...ORDER, status: 'DELIVERED' } as never} canWrite={false} />, {
      wrapper: wrapper(),
    });
    expect(screen.queryByTestId('order-read-only-note')).toBeNull();
    expect(screen.queryByTestId('order-status-actions')).toBeNull();
  });
});
