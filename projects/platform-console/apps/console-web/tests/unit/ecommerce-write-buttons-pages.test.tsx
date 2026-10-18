/**
 * TASK-PC-FE-346 — page wiring of the E-commerce write-button gate (contract § 2.4.3.4).
 *
 * Failure Scenario 3 of the ticket: «선언만 하고 배선을 빠뜨린 화면». The screens' `canWrite` is
 * pinned in `ecommerce-write-buttons.test.tsx`; THIS file pins that every E-commerce route computes
 * it from the real chain — `resolveEcommerceCanWrite` → `getDomainBusinessRoles` (row 12 ∩ the
 * registry's ecommerce tenants) → `canWriteOn(href, roles)` — and hands it to the screen. Only the
 * data sources (session, row 12, registry eligibility) and the screen bodies are stubbed.
 *
 * Failure Scenario 2: on `new` / `edit` a non-writer gets the «조회 전용» route notice INSTEAD of
 * the form (a filled-in form would only end in a `403`).
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import type { ReactElement } from 'react';

const session = vi.hoisted(() => ({ sample: false, tenant: 'demo-corp' as string | null }));
const row12 = vi.hoisted(() => ({
  value: [] as { domainKey: string; role: string }[] | null,
}));
const eligibility = vi.hoisted(() => ({
  value: {
    eligible: true,
    registryDegraded: false,
    tenants: ['demo-corp'] as string[] | null,
  },
}));

vi.mock('@/shared/lib/session', () => ({
  isSampleVisitor: async () => session.sample,
  getActiveTenant: async () => session.tenant,
}));
vi.mock('@/shared/api/iam-operators-read', () => ({
  getMyDomainRolesOrNull: async () => row12.value,
}));
vi.mock('@/app/(console)/ecommerce/products/_eligibility', () => ({
  resolveEcommerceEligibility: async () => eligibility.value,
}));
vi.mock('next/navigation', () => ({
  notFound: () => {
    throw new Error('NOT_FOUND');
  },
  redirect: () => {
    throw new Error('REDIRECT');
  },
}));

vi.mock('@/features/ecommerce-ops', async () => {
  const { ReadOnlyNote, ReadOnlyRouteNotice } = await import(
    '@/features/ecommerce-ops/components/ReadOnlyNotice'
  );
  const { DetailHeader } = await import('@/shared/ui/DetailHeader');
  // every section state is «happy» for every route; each carries all the data keys.
  const ok = async () => ({
    notEligible: false,
    forbidden: false,
    notFound: false,
    degraded: false,
    products: {},
    sellers: {},
    shippings: {},
    promotions: {},
    templates: {},
    accruals: {},
    periods: {},
    payouts: {},
    detail: { sellerId: 's-1' },
  });
  const screen_ =
    (testId: string) =>
    ({ canWrite }: { canWrite?: boolean }) => (
      <div data-testid={testId} data-can-write={String(canWrite)} />
    );
  const form = () => <div data-testid="stub-form" />;
  return {
    ReadOnlyNote,
    ReadOnlyRouteNotice,
    DetailHeader,
    getProductsSectionState: ok,
    getProductDetailSectionState: ok,
    getSellersSectionState: ok,
    getSellerDetailSectionState: ok,
    getOrderDetailSectionState: ok,
    getShippingsSectionState: ok,
    getPromotionsSectionState: ok,
    getPromotionDetailSectionState: ok,
    getSettlementsSectionState: ok,
    getPeriodPayoutsSectionState: ok,
    getNotificationsSectionState: ok,
    getNotificationDetailSectionState: ok,
    ProductsScreen: screen_('screen'),
    ProductDetail: screen_('screen'),
    SellersScreen: screen_('screen'),
    SellerDetail: screen_('screen'),
    OrderDetail: screen_('screen'),
    ShippingsScreen: screen_('screen'),
    PromotionsScreen: screen_('screen'),
    PromotionDetail: screen_('screen'),
    SettlementsScreen: screen_('screen'),
    PeriodPayoutsScreen: screen_('screen'),
    NotificationsScreen: screen_('screen'),
    ProductForm: form,
    SellerRegisterForm: form,
    PromotionForm: form,
    TemplateForm: form,
  };
});

import ProductsPage from '@/app/(console)/ecommerce/products/page';
import ProductDetailPage from '@/app/(console)/ecommerce/products/[id]/page';
import NewProductPage from '@/app/(console)/ecommerce/products/new/page';
import EditProductPage from '@/app/(console)/ecommerce/products/[id]/edit/page';
import SellersPage from '@/app/(console)/ecommerce/sellers/page';
import SellerDetailPage from '@/app/(console)/ecommerce/sellers/[id]/page';
import NewSellerPage from '@/app/(console)/ecommerce/sellers/new/page';
import OrderDetailPage from '@/app/(console)/ecommerce/orders/[id]/page';
import ShippingsPage from '@/app/(console)/ecommerce/shippings/page';
import PromotionsPage from '@/app/(console)/ecommerce/promotions/page';
import PromotionDetailPage from '@/app/(console)/ecommerce/promotions/[id]/page';
import NewPromotionPage from '@/app/(console)/ecommerce/promotions/new/page';
import EditPromotionPage from '@/app/(console)/ecommerce/promotions/[id]/edit/page';
import SettlementsPage from '@/app/(console)/ecommerce/settlements/page';
import SettlementPeriodPage from '@/app/(console)/ecommerce/settlements/periods/[id]/page';
import TemplatesPage from '@/app/(console)/ecommerce/notifications/templates/page';
import NewTemplatePage from '@/app/(console)/ecommerce/notifications/templates/new/page';
import EditTemplatePage from '@/app/(console)/ecommerce/notifications/templates/[id]/edit/page';

type PageFn = (props: { params: Promise<{ id: string }> }) => Promise<ReactElement>;
const params = { params: Promise.resolve({ id: 'x-1' }) };
async function renderPage(page: unknown) {
  render(await (page as PageFn)(params));
}

const R = (domainKey: string, ...roles: string[]) => roles.map((role) => ({ domainKey, role }));
const CATALOG = [
  'ORDER', 'PRODUCT', 'SETTLEMENT', 'PROMOTION', 'SHIPPING', 'NOTIFICATION',
].flatMap((a) => [`ECOMMERCE_${a}_READ`, `ECOMMERCE_${a}_WRITE`]).concat('ECOMMERCE_MEMBER_READ');
/** `ecommerce-read` system preset. */
const READ_SET = CATALOG.filter((r) => r.endsWith('_READ'));

/** screen routes (one per list/detail page) → the screen's `canWrite` must follow `writeRequires`. */
const SCREEN_PAGES: { name: string; page: unknown; write: string }[] = [
  { name: 'products', page: ProductsPage, write: 'ECOMMERCE_PRODUCT_WRITE' },
  { name: 'products/[id]', page: ProductDetailPage, write: 'ECOMMERCE_PRODUCT_WRITE' },
  { name: 'sellers', page: SellersPage, write: 'ECOMMERCE_PRODUCT_WRITE' },
  { name: 'sellers/[id]', page: SellerDetailPage, write: 'ECOMMERCE_PRODUCT_WRITE' },
  { name: 'orders/[id]', page: OrderDetailPage, write: 'ECOMMERCE_ORDER_WRITE' },
  { name: 'shippings', page: ShippingsPage, write: 'ECOMMERCE_SHIPPING_WRITE' },
  { name: 'promotions', page: PromotionsPage, write: 'ECOMMERCE_PROMOTION_WRITE' },
  { name: 'promotions/[id]', page: PromotionDetailPage, write: 'ECOMMERCE_PROMOTION_WRITE' },
  { name: 'settlements', page: SettlementsPage, write: 'ECOMMERCE_SETTLEMENT_WRITE' },
  { name: 'settlements/periods/[id]', page: SettlementPeriodPage, write: 'ECOMMERCE_SETTLEMENT_WRITE' },
  { name: 'notifications/templates', page: TemplatesPage, write: 'ECOMMERCE_NOTIFICATION_WRITE' },
];

/** form routes → a non-writer gets the notice, a writer the form. */
const FORM_PAGES: { name: string; page: unknown; write: string; notice: string }[] = [
  { name: 'products/new', page: NewProductPage, write: 'ECOMMERCE_PRODUCT_WRITE', notice: 'product-form-read-only' },
  { name: 'products/[id]/edit', page: EditProductPage, write: 'ECOMMERCE_PRODUCT_WRITE', notice: 'product-form-read-only' },
  { name: 'sellers/new', page: NewSellerPage, write: 'ECOMMERCE_PRODUCT_WRITE', notice: 'seller-form-read-only' },
  { name: 'promotions/new', page: NewPromotionPage, write: 'ECOMMERCE_PROMOTION_WRITE', notice: 'promotion-form-read-only' },
  { name: 'promotions/[id]/edit', page: EditPromotionPage, write: 'ECOMMERCE_PROMOTION_WRITE', notice: 'promotion-form-read-only' },
  { name: 'notifications/templates/new', page: NewTemplatePage, write: 'ECOMMERCE_NOTIFICATION_WRITE', notice: 'notification-form-read-only' },
  { name: 'notifications/templates/[id]/edit', page: EditTemplatePage, write: 'ECOMMERCE_NOTIFICATION_WRITE', notice: 'notification-form-read-only' },
];

const canWriteOfScreen = () => screen.getByTestId('screen').getAttribute('data-can-write');

beforeEach(() => {
  cleanup();
  session.sample = false;
  session.tenant = 'demo-corp';
  row12.value = R('ecommerce', ...CATALOG);
  eligibility.value = { eligible: true, registryDegraded: false, tenants: ['demo-corp'] };
});

describe('TASK-PC-FE-346 — screens receive canWrite from the operator\'s area roles', () => {
  describe.each(SCREEN_PAGES)('$name', ({ page, write }) => {
    it('`ecommerce-read` set → canWrite=false', async () => {
      row12.value = R('ecommerce', ...READ_SET);
      await renderPage(page);
      expect(canWriteOfScreen()).toBe('false');
    });

    it('holds the area `*_WRITE` role → canWrite=true', async () => {
      row12.value = R('ecommerce', write);
      await renderPage(page);
      expect(canWriteOfScreen()).toBe('true');
    });

    it('demo operator (legacy-compat: all 13 roles) → canWrite=true', async () => {
      row12.value = R('ecommerce', ...CATALOG);
      await renderPage(page);
      expect(canWriteOfScreen()).toBe('true');
    });

    it('an ecommerce role of ANOTHER area only → canWrite=false', async () => {
      const other = CATALOG.find((r) => r.endsWith('_WRITE') && r !== write)!;
      row12.value = R('ecommerce', other);
      await renderPage(page);
      expect(canWriteOfScreen()).toBe('false');
    });

    it('fail-open (AC-3): row 12 failed → canWrite=true', async () => {
      row12.value = null;
      await renderPage(page);
      expect(canWriteOfScreen()).toBe('true');
    });

    it('fail-open (AC-3): no active tenant → canWrite=true', async () => {
      session.tenant = null;
      await renderPage(page);
      expect(canWriteOfScreen()).toBe('true');
    });

    it('fail-open (AC-3): sample visitor → canWrite=true', async () => {
      session.sample = true;
      row12.value = R('ecommerce', ...READ_SET);
      await renderPage(page);
      expect(canWriteOfScreen()).toBe('true');
    });

    it('fail-open (AC-3): registry tenants unknown → canWrite=true', async () => {
      eligibility.value = { eligible: true, registryDegraded: false, tenants: null };
      row12.value = R('ecommerce', ...READ_SET);
      await renderPage(page);
      expect(canWriteOfScreen()).toBe('true');
    });
  });
});

describe('TASK-PC-FE-346 AC-2 — control groups across the 7 screens', () => {
  it('order-operate set only → the order screen writes, the other six do not', async () => {
    const result: Record<string, string | null> = {};
    for (const { name, page } of SCREEN_PAGES) {
      cleanup();
      row12.value = R('ecommerce', 'ECOMMERCE_ORDER_READ', 'ECOMMERCE_ORDER_WRITE');
      await renderPage(page);
      result[name] = canWriteOfScreen();
    }
    expect(result['orders/[id]']).toBe('true');
    for (const [name, v] of Object.entries(result)) {
      if (name !== 'orders/[id]') expect(v, name).toBe('false');
    }
  });

  it('product-operate set → products AND sellers write (sellers = the product role); promotions do not', async () => {
    const result: Record<string, string | null> = {};
    for (const { name, page } of SCREEN_PAGES) {
      cleanup();
      row12.value = R('ecommerce', 'ECOMMERCE_PRODUCT_READ', 'ECOMMERCE_PRODUCT_WRITE');
      await renderPage(page);
      result[name] = canWriteOfScreen();
    }
    for (const n of ['products', 'products/[id]', 'sellers', 'sellers/[id]']) expect(result[n], n).toBe('true');
    for (const n of ['promotions', 'promotions/[id]', 'shippings', 'settlements', 'notifications/templates']) {
      expect(result[n], n).toBe('false');
    }
  });

  it('settlement payout has no control role: SETTLEMENT_WRITE alone is enough', async () => {
    row12.value = R('ecommerce', 'ECOMMERCE_SETTLEMENT_WRITE');
    await renderPage(SettlementPeriodPage);
    expect(canWriteOfScreen()).toBe('true');
  });

  it('🔴 the derived entry role `ECOMMERCE_OPERATOR` alone opens nothing', async () => {
    row12.value = R('ecommerce', 'ECOMMERCE_OPERATOR');
    await renderPage(ProductsPage);
    expect(canWriteOfScreen()).toBe('false');
  });
});

describe('TASK-PC-FE-346 — new / edit routes: a non-writer gets the notice instead of the form', () => {
  describe.each(FORM_PAGES)('$name', ({ page, write, notice }) => {
    it('no area write role → «조회 전용» notice, no form', async () => {
      row12.value = R('ecommerce', ...READ_SET);
      await renderPage(page);
      expect(screen.getByTestId(notice)).toHaveTextContent('조회 전용');
      expect(screen.queryByTestId('stub-form')).toBeNull();
    });

    it('control: holds the area write role → the form, no notice', async () => {
      row12.value = R('ecommerce', write);
      await renderPage(page);
      expect(screen.getByTestId('stub-form')).toBeInTheDocument();
      expect(screen.queryByTestId(notice)).toBeNull();
    });

    it('fail-open: row 12 failed → the form', async () => {
      row12.value = null;
      await renderPage(page);
      expect(screen.getByTestId('stub-form')).toBeInTheDocument();
      expect(screen.queryByTestId(notice)).toBeNull();
    });
  });
});
