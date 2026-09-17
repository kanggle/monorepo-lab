/**
 * ecommerce domain sample fixtures parse with the SAME production schemas the
 * real screens use, and behave like the real producer over filters/
 * pagination/detail lookups/money arithmetic (TASK-PC-FE-284 AC-1 / AC-2 /
 * AC-3 / AC-4 / AC-5 / AC-7).
 *
 * 🔴 AC-1 — a fixture that broke its schema would render as the section
 *    degrade state, indistinguishable from "broken" (task Failure Scenario).
 *    Every assertion below goes THROUGH `sampleResponse` (not the internal
 *    seed arrays) so routing, status codes and the query-string handling are
 *    exercised too — a genuinely black-box, producer-shaped check.
 * 🔴 AC-3 — every order line's `productId`/`sellerId` and every order's
 *    `userId` resolve inside the SAME fixture world (walked below via the
 *    actual list responses, not the internal arrays) — and a detail id
 *    absent from the fixture 404s with the real backend shape.
 * 🔴 AC-4 — every filter cell asserts the filtered result is a PROPER SUBSET
 *    of the unfiltered one (narrower, non-empty, every row satisfies it).
 * 🔴 money must add up (task Edge Case): every order's `totalPrice` is
 *    checked against Σ(item.unitPrice × item.quantity); every seller's
 *    settlement balance is checked against Σ(that seller's accrual lines).
 */
import { describe, it, expect } from 'vitest';
import { sampleResponse } from '@/shared/sample/router';
import { SAMPLE_READ_ONLY } from '@/shared/sample/codes';
import { messageForCode } from '@/shared/api/errors';
import { ProductListSchema, ProductDetailSchema, ProductAreaSummarySchema } from '@/features/ecommerce-ops/api/product-types';
import { ImageListSchema } from '@/features/ecommerce-ops/api/image-types';
import {
  OrderListSchema,
  OrderDetailSchema,
  OrderAreaSummarySchema,
  OrderInsightsSchema,
} from '@/features/ecommerce-ops/api/order-types';
import { UserListSchema, UserDetailSchema, UserAreaSummarySchema } from '@/features/ecommerce-ops/api/user-types';
import {
  PromotionListSchema,
  PromotionDetailSchema,
  PromotionAreaSummarySchema,
} from '@/features/ecommerce-ops/api/promotion-types';
import { ShippingListSchema, ShippingAreaSummarySchema } from '@/features/ecommerce-ops/api/shipping-types';
import {
  NotificationTemplateListSchema,
  NotificationTemplateDetailSchema,
  NotificationAreaSummarySchema,
} from '@/features/ecommerce-ops/api/notification-types';
import { SellerListSchema, SellerDetailSchema, SellerAreaSummarySchema } from '@/features/ecommerce-ops/api/seller-types';
import {
  AccrualsResponseSchema,
  SellerBalanceSchema,
  CommissionRateSchema,
  PeriodsResponseSchema,
  PayoutsResponseSchema,
} from '@/features/ecommerce-ops/api/settlement-types';

function get(surface: string, path: string): Response {
  return sampleResponse({ core: 'ecommerce', surface, method: 'GET', path });
}
async function json(res: Response): Promise<unknown> {
  return res.json();
}

describe('products (AC-1 / AC-4)', () => {
  it('list parses with ProductListSchema and reports a real totalElements', async () => {
    const res = get('ecommerce', '/api/admin/products?page=0&size=20');
    expect(res.status).toBe(200);
    const parsed = ProductListSchema.parse(await json(res));
    expect(parsed.totalElements).toBe(parsed.content.length);
    expect(parsed.totalElements).toBeGreaterThan(0);
  });

  it('status=SOLD_OUT narrows the result (AC-4) — a subset, non-empty, every row SOLD_OUT', async () => {
    const all = ProductListSchema.parse(await json(get('ecommerce', '/api/admin/products?page=0&size=20')));
    const soldOut = ProductListSchema.parse(
      await json(get('ecommerce', '/api/admin/products?status=SOLD_OUT&page=0&size=20')),
    );
    expect(soldOut.totalElements).toBeGreaterThan(0);
    expect(soldOut.totalElements).toBeLessThan(all.totalElements);
    expect(soldOut.content.every((p) => p.status === 'SOLD_OUT')).toBe(true);
  });

  it('categoryId narrows the result (AC-4)', async () => {
    const all = ProductListSchema.parse(await json(get('ecommerce', '/api/admin/products?page=0&size=20')));
    const byCategory = ProductListSchema.parse(
      await json(get('ecommerce', '/api/admin/products?categoryId=cat-sample-0001&page=0&size=20')),
    );
    expect(byCategory.totalElements).toBeGreaterThan(0);
    expect(byCategory.totalElements).toBeLessThan(all.totalElements);
    expect(byCategory.content.every((p) => p.categoryId === 'cat-sample-0001')).toBe(true);
  });

  it('summary parses with ProductAreaSummarySchema, total = list totalElements', async () => {
    const list = ProductListSchema.parse(await json(get('ecommerce', '/api/admin/products?page=0&size=20')));
    const summary = ProductAreaSummarySchema.parse(await json(get('ecommerce', '/api/admin/products/summary')));
    expect(summary.total).toBe(list.totalElements);
    expect(summary.today).toBeLessThanOrEqual(summary.week);
    expect(summary.week).toBeLessThanOrEqual(summary.month);
    expect(summary.month).toBeLessThanOrEqual(summary.total);
  });

  it('AC-3 — a list id resolves in the (admin-path) detail lookup', async () => {
    const list = ProductListSchema.parse(await json(get('ecommerce', '/api/admin/products?page=0&size=20')));
    const first = list.content[0];
    const detailRes = get('ecommerce', `/api/admin/products/${first.id}`);
    expect(detailRes.status).toBe(200);
    const detail = ProductDetailSchema.parse(await json(detailRes));
    expect(detail.id).toBe(first.id);
  });

  it('TASK-MONO-703 — the detail is answered on the ADMIN path (where getProduct now asks), not the public one', async () => {
    const list = ProductListSchema.parse(await json(get('ecommerce', '/api/admin/products?page=0&size=20')));
    const first = list.content[0];
    // The sample router must follow the real call: the public path is no longer a product-detail address.
    expect(get('ecommerce', `/api/products/${first.id}`).status).not.toBe(200);
    // `/summary` is not swallowed by the detail template.
    ProductAreaSummarySchema.parse(await json(get('ecommerce', '/api/admin/products/summary')));
  });

  it('AC-3 — an id absent from the fixture 404s with the real backend shape (FLAT envelope, PRODUCT_NOT_FOUND)', async () => {
    const res = get('ecommerce', '/api/admin/products/no-such-product');
    expect(res.status).toBe(404);
    const body = (await json(res)) as { code?: string; message?: string; timestamp?: string };
    expect(body.code).toBe('PRODUCT_NOT_FOUND');
    expect(typeof body.message).toBe('string');
    expect(typeof body.timestamp).toBe('string');
  });

  it('AC-7 — no image origin: thumbnailUrl is null on every row and every detail', async () => {
    const list = ProductListSchema.parse(await json(get('ecommerce', '/api/admin/products?page=0&size=20')));
    for (const row of list.content) {
      expect(row.thumbnailUrl).toBeNull();
      const detail = ProductDetailSchema.parse(
        await json(get('ecommerce', `/api/admin/products/${row.id}`)),
      );
      expect(detail.thumbnailUrl).toBeNull();
      expect(detail.images).toEqual([]);
    }
  });
});

describe('ecommerce_image — AC-7 (always empty, no image origin)', () => {
  it('list images parses with ImageListSchema and is empty for every product', async () => {
    for (const id of ['prod-sample-0001', 'prod-sample-0002', 'prod-sample-0003']) {
      const res = get('ecommerce_image', `/api/admin/products/${id}/images`);
      expect(res.status).toBe(200);
      const parsed = ImageListSchema.parse(await json(res));
      expect(parsed.images).toEqual([]);
    }
  });
});

describe('orders (AC-1 / AC-3 / AC-4 / money)', () => {
  it('list parses with OrderListSchema', async () => {
    const res = OrderListSchema.parse(await json(get('ecommerce_order', '/api/admin/orders?page=0&size=20')));
    expect(res.totalElements).toBeGreaterThan(0);
  });

  it('status=CANCELLED narrows the result', async () => {
    const all = OrderListSchema.parse(await json(get('ecommerce_order', '/api/admin/orders?page=0&size=20')));
    const cancelled = OrderListSchema.parse(
      await json(get('ecommerce_order', '/api/admin/orders?status=CANCELLED&page=0&size=20')),
    );
    expect(cancelled.totalElements).toBeGreaterThan(0);
    expect(cancelled.totalElements).toBeLessThan(all.totalElements);
    expect(cancelled.content.every((o) => o.status === 'CANCELLED')).toBe(true);
  });

  it('status=DELIVERED narrows to an empty (real) result — no delivered order in this world yet', async () => {
    const res = OrderListSchema.parse(
      await json(get('ecommerce_order', '/api/admin/orders?status=DELIVERED&page=0&size=20')),
    );
    expect(res.totalElements).toBe(0);
    expect(res.content).toEqual([]);
  });

  it('summary parses; total = list totalElements', async () => {
    const list = OrderListSchema.parse(await json(get('ecommerce_order', '/api/admin/orders?page=0&size=20')));
    const summary = OrderAreaSummarySchema.parse(await json(get('ecommerce_order', '/api/admin/orders/summary')));
    expect(summary.total).toBe(list.totalElements);
  });

  it('AC-3 — a list id resolves in the detail lookup; money adds up (totalPrice = Σ unitPrice×quantity)', async () => {
    const list = OrderListSchema.parse(await json(get('ecommerce_order', '/api/admin/orders?page=0&size=20')));
    for (const row of list.content) {
      const detail = OrderDetailSchema.parse(
        await json(get('ecommerce_order', `/api/admin/orders/${row.orderId}`)),
      );
      expect(detail.orderId).toBe(row.orderId);
      const sum = detail.items.reduce((s, it) => s + it.unitPrice * it.quantity, 0);
      expect(detail.totalPrice).toBe(sum);
      expect(detail.totalPrice).toBe(row.totalPrice);
    }
  });

  it('AC-3 world consistency — every item productId/sellerId and every order userId resolve inside the SAME fixture world', async () => {
    const orders = OrderListSchema.parse(await json(get('ecommerce_order', '/api/admin/orders?page=0&size=20')));
    const products = ProductListSchema.parse(await json(get('ecommerce', '/api/admin/products?page=0&size=20')));
    const sellers = SellerListSchema.parse(await json(get('ecommerce_seller', '/api/admin/sellers?page=0&size=20')));
    const users = UserListSchema.parse(await json(get('ecommerce_user', '/api/admin/users?page=0&size=20')));
    const productIds = new Set(products.content.map((p) => p.id));
    const sellerIds = new Set(sellers.content.map((s) => s.sellerId));
    const userIds = new Set(users.content.map((u) => u.userId));

    for (const row of orders.content) {
      expect(userIds.has(row.userId)).toBe(true);
      const detail = OrderDetailSchema.parse(
        await json(get('ecommerce_order', `/api/admin/orders/${row.orderId}`)),
      );
      for (const item of detail.items) {
        expect(productIds.has(item.productId)).toBe(true);
        expect(sellerIds.has(item.sellerId)).toBe(true);
      }
    }
  });

  it('AC-3 — an id absent from the fixture 404s with the real backend shape (ORDER_NOT_FOUND)', async () => {
    const res = get('ecommerce_order', '/api/admin/orders/no-such-order');
    expect(res.status).toBe(404);
    const body = (await json(res)) as { code?: string };
    expect(body.code).toBe('ORDER_NOT_FOUND');
  });

  it('insights parses with OrderInsightsSchema and CANCELLED orders are excluded (§ 2.4.10 #21)', async () => {
    const insights = OrderInsightsSchema.parse(await json(get('ecommerce_order', '/api/admin/orders/insights')));
    // Every ranking is sorted DESC by value.
    for (const ranking of [
      insights.topProductsByOrderCount,
      insights.topProductsByRevenue,
      insights.topSellersByOrderCount,
      insights.topSellersByRevenue,
    ]) {
      for (let i = 1; i < ranking.length; i += 1) {
        expect(ranking[i].value).toBeLessThanOrEqual(ranking[i - 1].value);
      }
    }
    // Revenue by product excludes the CANCELLED order-sample-0004's line
    // (prod-sample-0001 would otherwise carry 38000+20000+19000=77000, not
    // 57000 — order-1's 38000 + order-5's 19000 item-1 subtotal).
    const prod1 = insights.topProductsByRevenue.find((r) => r.id === 'prod-sample-0001');
    expect(prod1?.value).toBe(57000);
    // Seller label is already resolved to the seller's own (suffixed)
    // displayName (§ ecommerce.ts doc comment on `computeInsights`).
    const seller1 = insights.topSellersByRevenue.find((r) => r.id === 'seller-sample-0001');
    expect(seller1?.label).toContain('(샘플)');
    // order-1(38000) + order-2(45000) + order-5 item-1 subtotal(19000).
    expect(seller1?.value).toBe(102000);
  });
});

describe('users (AC-1 / AC-4 / AC-7)', () => {
  it('list parses with UserListSchema', async () => {
    const res = UserListSchema.parse(await json(get('ecommerce_user', '/api/admin/users?page=0&size=20')));
    expect(res.totalElements).toBeGreaterThan(0);
  });

  it('status=WITHDRAWN narrows the result', async () => {
    const all = UserListSchema.parse(await json(get('ecommerce_user', '/api/admin/users?page=0&size=20')));
    const withdrawn = UserListSchema.parse(
      await json(get('ecommerce_user', '/api/admin/users?status=WITHDRAWN&page=0&size=20')),
    );
    expect(withdrawn.totalElements).toBeGreaterThan(0);
    expect(withdrawn.totalElements).toBeLessThan(all.totalElements);
    expect(withdrawn.content.every((u) => u.status === 'WITHDRAWN')).toBe(true);
  });

  it('email search with the PLAIN typed address narrows to the seeded user (AC-4)', async () => {
    const res = UserListSchema.parse(
      await json(get('ecommerce_user', `/api/admin/users?email=${encodeURIComponent('hana')}`)),
    );
    expect(res.totalElements).toBe(1);
    expect(res.content[0].userId).toBe('user-sample-0001');
  });

  it('email search with the SUFFIXED rendered value (copy-pasted from the table) ALSO narrows to the same user', async () => {
    const res = UserListSchema.parse(
      await json(
        get(
          'ecommerce_user',
          `/api/admin/users?email=${encodeURIComponent('hana.kim.sample@example.com (샘플)')}`,
        ),
      ),
    );
    expect(res.totalElements).toBe(1);
    expect(res.content[0].userId).toBe('user-sample-0001');
  });

  it('email search with a needle matching nobody narrows to empty', async () => {
    const res = UserListSchema.parse(
      await json(get('ecommerce_user', '/api/admin/users?email=nobody-here')),
    );
    expect(res.totalElements).toBe(0);
  });

  it('summary parses; total = list totalElements', async () => {
    const list = UserListSchema.parse(await json(get('ecommerce_user', '/api/admin/users?page=0&size=20')));
    const summary = UserAreaSummarySchema.parse(await json(get('ecommerce_user', '/api/admin/users/summary')));
    expect(summary.total).toBe(list.totalElements);
  });

  it('AC-3 — a list id resolves in the detail lookup (incl. the anonymized/withdrawn row with null email/name)', async () => {
    const list = UserListSchema.parse(await json(get('ecommerce_user', '/api/admin/users?page=0&size=20')));
    for (const row of list.content) {
      const detail = UserDetailSchema.parse(
        await json(get('ecommerce_user', `/api/admin/users/${row.userId}`)),
      );
      expect(detail.userId).toBe(row.userId);
    }
    const withdrawn = UserDetailSchema.parse(
      await json(get('ecommerce_user', '/api/admin/users/user-sample-0003')),
    );
    expect(withdrawn.email).toBeNull();
    expect(withdrawn.name).toBeNull();
    expect(withdrawn.profileImageUrl).toBeNull();
  });

  it('AC-3 — an id absent from the fixture 404s (USER_PROFILE_NOT_FOUND)', async () => {
    const res = get('ecommerce_user', '/api/admin/users/no-such-user');
    expect(res.status).toBe(404);
    const body = (await json(res)) as { code?: string };
    expect(body.code).toBe('USER_PROFILE_NOT_FOUND');
  });
});

describe('promotions (AC-1 / AC-3 / AC-4)', () => {
  it('list parses with PromotionListSchema', async () => {
    const res = PromotionListSchema.parse(await json(get('ecommerce_promotion', '/api/promotions?page=0&size=20')));
    expect(res.totalElements).toBeGreaterThan(0);
  });

  it('status=ENDED narrows the result', async () => {
    const all = PromotionListSchema.parse(await json(get('ecommerce_promotion', '/api/promotions?page=0&size=20')));
    const ended = PromotionListSchema.parse(
      await json(get('ecommerce_promotion', '/api/promotions?status=ENDED&page=0&size=20')),
    );
    expect(ended.totalElements).toBeGreaterThan(0);
    expect(ended.totalElements).toBeLessThan(all.totalElements);
    expect(ended.content.every((p) => p.status === 'ENDED')).toBe(true);
  });

  it('summary parses; total = list totalElements', async () => {
    const list = PromotionListSchema.parse(await json(get('ecommerce_promotion', '/api/promotions?page=0&size=20')));
    const summary = PromotionAreaSummarySchema.parse(
      await json(get('ecommerce_promotion', '/api/promotions/summary')),
    );
    expect(summary.total).toBe(list.totalElements);
  });

  it('AC-3 — a list id resolves in the detail lookup', async () => {
    const list = PromotionListSchema.parse(await json(get('ecommerce_promotion', '/api/promotions?page=0&size=20')));
    for (const row of list.content) {
      const detail = PromotionDetailSchema.parse(
        await json(get('ecommerce_promotion', `/api/promotions/${row.promotionId}`)),
      );
      expect(detail.promotionId).toBe(row.promotionId);
    }
  });

  it('AC-3 — an id absent from the fixture 404s (PROMOTION_NOT_FOUND)', async () => {
    const res = get('ecommerce_promotion', '/api/promotions/no-such-promotion');
    expect(res.status).toBe(404);
    const body = (await json(res)) as { code?: string };
    expect(body.code).toBe('PROMOTION_NOT_FOUND');
  });
});

describe('shippings (AC-1 / AC-4) — no detail-by-id GET (producer defines none)', () => {
  it('list parses with ShippingListSchema', async () => {
    const res = ShippingListSchema.parse(await json(get('ecommerce_shipping', '/api/shippings?page=0&size=20')));
    expect(res.totalElements).toBeGreaterThan(0);
  });

  it('status=SHIPPED narrows the result', async () => {
    const all = ShippingListSchema.parse(await json(get('ecommerce_shipping', '/api/shippings?page=0&size=20')));
    const shipped = ShippingListSchema.parse(
      await json(get('ecommerce_shipping', '/api/shippings?status=SHIPPED&page=0&size=20')),
    );
    expect(shipped.totalElements).toBeGreaterThan(0);
    expect(shipped.totalElements).toBeLessThan(all.totalElements);
    expect(shipped.content.every((s) => s.status === 'SHIPPED')).toBe(true);
  });

  it('summary parses; total = list totalElements', async () => {
    const list = ShippingListSchema.parse(await json(get('ecommerce_shipping', '/api/shippings?page=0&size=20')));
    const summary = ShippingAreaSummarySchema.parse(
      await json(get('ecommerce_shipping', '/api/shippings/summary')),
    );
    expect(summary.total).toBe(list.totalElements);
  });
});

describe('notification templates (AC-1 / AC-3)', () => {
  it('list parses with NotificationTemplateListSchema', async () => {
    const res = NotificationTemplateListSchema.parse(
      await json(get('ecommerce_notification', '/api/notifications/templates?page=0&size=20')),
    );
    expect(res.totalElements).toBeGreaterThan(0);
  });

  it('summary parses; total = list totalElements', async () => {
    const list = NotificationTemplateListSchema.parse(
      await json(get('ecommerce_notification', '/api/notifications/templates?page=0&size=20')),
    );
    const summary = NotificationAreaSummarySchema.parse(
      await json(get('ecommerce_notification', '/api/notifications/templates/summary')),
    );
    expect(summary.total).toBe(list.totalElements);
  });

  it('AC-3 — a list id resolves in the (full, incl. body) detail lookup', async () => {
    const list = NotificationTemplateListSchema.parse(
      await json(get('ecommerce_notification', '/api/notifications/templates?page=0&size=20')),
    );
    for (const row of list.content) {
      const detail = NotificationTemplateDetailSchema.parse(
        await json(get('ecommerce_notification', `/api/notifications/templates/${row.templateId}`)),
      );
      expect(detail.templateId).toBe(row.templateId);
      expect(detail.type).toBe(row.type);
      expect(detail.channel).toBe(row.channel);
      expect(typeof detail.body).toBe('string');
    }
  });

  it('AC-3 — an id absent from the fixture 404s (TEMPLATE_NOT_FOUND)', async () => {
    const res = get('ecommerce_notification', '/api/notifications/templates/no-such-template');
    expect(res.status).toBe(404);
    const body = (await json(res)) as { code?: string };
    expect(body.code).toBe('TEMPLATE_NOT_FOUND');
  });
});

describe('sellers (AC-1 / AC-3 / AC-7)', () => {
  it('list parses with SellerListSchema', async () => {
    const res = SellerListSchema.parse(await json(get('ecommerce_seller', '/api/admin/sellers?page=0&size=20')));
    expect(res.totalElements).toBeGreaterThan(0);
  });

  it('summary parses; total = list totalElements', async () => {
    const list = SellerListSchema.parse(await json(get('ecommerce_seller', '/api/admin/sellers?page=0&size=20')));
    const summary = SellerAreaSummarySchema.parse(await json(get('ecommerce_seller', '/api/admin/sellers/summary')));
    expect(summary.total).toBe(list.totalElements);
  });

  it('AC-3 — a list id resolves in the detail lookup; every lifecycle status is represented', async () => {
    const list = SellerListSchema.parse(await json(get('ecommerce_seller', '/api/admin/sellers?page=0&size=20')));
    const statuses = new Set(list.content.map((s) => s.status));
    expect(statuses).toEqual(new Set(['ACTIVE', 'PENDING_PROVISIONING', 'SUSPENDED']));
    for (const row of list.content) {
      const detail = SellerDetailSchema.parse(
        await json(get('ecommerce_seller', `/api/admin/sellers/${row.sellerId}`)),
      );
      expect(detail.sellerId).toBe(row.sellerId);
    }
  });

  it('AC-3 — an id absent from the fixture 404s (SELLER_NOT_FOUND)', async () => {
    const res = get('ecommerce_seller', '/api/admin/sellers/no-such-seller');
    expect(res.status).toBe(404);
    const body = (await json(res)) as { code?: string };
    expect(body.code).toBe('SELLER_NOT_FOUND');
  });
});

describe('settlements (AC-1 / AC-3 / AC-4 / money) — the "합계 = 행 합" edge case', () => {
  it('accruals list parses; sellerId filter and orderId filter each narrow (AC-4)', async () => {
    const all = AccrualsResponseSchema.parse(
      await json(get('ecommerce_settlement', '/api/admin/settlements/accruals?page=0&size=20')),
    );
    expect(all.totalElements).toBeGreaterThan(0);

    const bySeller = AccrualsResponseSchema.parse(
      await json(
        get(
          'ecommerce_settlement',
          '/api/admin/settlements/accruals?sellerId=seller-sample-0002&page=0&size=20',
        ),
      ),
    );
    expect(bySeller.totalElements).toBeGreaterThan(0);
    expect(bySeller.totalElements).toBeLessThan(all.totalElements);
    expect(bySeller.items.every((a) => a.sellerId === 'seller-sample-0002')).toBe(true);

    const byOrder = AccrualsResponseSchema.parse(
      await json(
        get(
          'ecommerce_settlement',
          '/api/admin/settlements/accruals?orderId=order-sample-0002&page=0&size=20',
        ),
      ),
    );
    expect(byOrder.totalElements).toBe(2); // the ACCRUAL + its REVERSAL clawback
    expect(byOrder.items.every((a) => a.orderId === 'order-sample-0002')).toBe(true);
  });

  it('money adds up — every seller balance = Σ(that seller\'s accrual lines), rate-consistent', async () => {
    for (const sellerId of ['seller-sample-0001', 'seller-sample-0002', 'seller-sample-0003']) {
      const accruals = AccrualsResponseSchema.parse(
        await json(
          get('ecommerce_settlement', `/api/admin/settlements/accruals?sellerId=${sellerId}&page=0&size=50`),
        ),
      );
      const balance = SellerBalanceSchema.parse(
        await json(get('ecommerce_settlement', `/api/admin/settlements/sellers/${sellerId}/balance`)),
      );
      const rate = CommissionRateSchema.parse(
        await json(get('ecommerce_settlement', `/api/admin/settlements/commission-rates/${sellerId}`)),
      );

      const sumGross = accruals.items.reduce((s, a) => s + a.grossMinor, 0);
      const sumCommission = accruals.items.reduce((s, a) => s + a.commissionMinor, 0);
      const sumNet = accruals.items.reduce((s, a) => s + a.sellerNetMinor, 0);

      expect(balance.grossMinor).toBe(sumGross);
      expect(balance.platformCommissionMinor).toBe(sumCommission);
      expect(balance.accruedNetMinor).toBe(sumNet);
      expect(balance.accrualCount).toBe(accruals.items.length);
      // The seller's effective rate applied to its gross reproduces its commission
      // (integer basis-points math — never a float rate).
      expect(Math.round((sumGross * rate.rateBps) / 10000)).toBe(sumCommission);
    }
  });

  it('CORRECTION (coordinator review) — every accrual\'s orderId exists in the order fixtures, and each ACCRUAL\'s grossMinor equals that SELLER\'s subtotal on that order', async () => {
    // Walks accruals → orders through `sampleResponse` (the router + real
    // schemas), not through the internal seed arrays — so this is a genuine
    // cross-screen check: the same money a visitor sees on `/ecommerce/orders`
    // must be the money the settlement screen shows for that order's line.
    const accruals = AccrualsResponseSchema.parse(
      await json(get('ecommerce_settlement', '/api/admin/settlements/accruals?page=0&size=50')),
    );
    expect(accruals.items.length).toBeGreaterThan(0);

    const orders = OrderListSchema.parse(await json(get('ecommerce_order', '/api/admin/orders?page=0&size=20')));
    const orderIds = new Set(orders.content.map((o) => o.orderId));
    const cancelledOrderIds = new Set(
      orders.content.filter((o) => o.status === 'CANCELLED').map((o) => o.orderId),
    );

    // Cache order details (several accrual lines share the same order).
    const detailCache = new Map<string, ReturnType<typeof OrderDetailSchema.parse>>();
    async function orderDetailFor(orderId: string) {
      if (!detailCache.has(orderId)) {
        const detail = OrderDetailSchema.parse(
          await json(get('ecommerce_order', `/api/admin/orders/${orderId}`)),
        );
        detailCache.set(orderId, detail);
      }
      return detailCache.get(orderId)!;
    }

    // ACCRUAL gross must equal the referenced order's own total when the
    // order is single-seller, or that seller's line SUBTOTAL when the order
    // has items from more than one seller (order-sample-0005).
    const accruedGrossBySellerOrder = new Map<string, number>();

    for (const line of accruals.items) {
      expect(orderIds.has(line.orderId), `${line.accrualId} references ${line.orderId}`).toBe(true);
      expect(
        cancelledOrderIds.has(line.orderId),
        `${line.accrualId} references CANCELLED order ${line.orderId}`,
      ).toBe(false);

      const detail = await orderDetailFor(line.orderId);
      const sellerSubtotal = detail.items
        .filter((it) => it.sellerId === line.sellerId)
        .reduce((s, it) => s + it.unitPrice * it.quantity, 0);
      const key = `${line.sellerId}:${line.orderId}`;

      if (line.type === 'ACCRUAL') {
        expect(line.grossMinor, `${line.accrualId} gross vs order ${line.orderId}`).toBe(sellerSubtotal);
        accruedGrossBySellerOrder.set(key, (accruedGrossBySellerOrder.get(key) ?? 0) + line.grossMinor);
      } else {
        // REVERSAL — a clawback must never exceed what that seller actually
        // accrued on that order (a partial refund, not manufactured money).
        expect(Math.abs(line.grossMinor)).toBeLessThanOrEqual(
          accruedGrossBySellerOrder.get(key) ?? 0,
        );
      }
    }
  });

  it('AC-3 — an unknown sellerId 404s on both balance and commission-rate lookups (SETTLEMENT_NOT_FOUND)', async () => {
    const balanceRes = get('ecommerce_settlement', '/api/admin/settlements/sellers/no-such-seller/balance');
    expect(balanceRes.status).toBe(404);
    expect(((await json(balanceRes)) as { code?: string }).code).toBe('SETTLEMENT_NOT_FOUND');

    const rateRes = get('ecommerce_settlement', '/api/admin/settlements/commission-rates/no-such-seller');
    expect(rateRes.status).toBe(404);
    expect(((await json(rateRes)) as { code?: string }).code).toBe('SETTLEMENT_NOT_FOUND');
  });

  it('periods list parses; a CLOSED period\'s sellerCount = its own payout row count (this domain\'s "합계 = 행 합")', async () => {
    const periods = PeriodsResponseSchema.parse(
      await json(get('ecommerce_settlement', '/api/admin/settlements/periods?page=0&size=20')),
    );
    expect(periods.totalElements).toBeGreaterThan(0);

    for (const period of periods.items) {
      const payouts = PayoutsResponseSchema.parse(
        await json(
          get(
            'ecommerce_settlement',
            `/api/admin/settlements/periods/${period.periodId}/payouts?page=0&size=20`,
          ),
        ),
      );
      if (period.status === 'CLOSED') {
        expect(period.sellerCount).toBe(payouts.items.length);
        expect(payouts.items.length).toBeGreaterThan(0);
      } else {
        // OPEN period — the deliberate empty-state screen (AC-6 empty-state Edge Case).
        expect(payouts.items).toEqual([]);
      }
    }
  });

  it('AC-3 — an unknown periodId 404s on the payouts lookup (SETTLEMENT_NOT_FOUND)', async () => {
    const res = get('ecommerce_settlement', '/api/admin/settlements/periods/no-such-period/payouts');
    expect(res.status).toBe(404);
    const body = (await json(res)) as { code?: string };
    expect(body.code).toBe('SETTLEMENT_NOT_FOUND');
  });
});

describe('AC-5 — a representative write is refused with the sample copy', () => {
  it('POST order status change → 403 SAMPLE_READ_ONLY → the R1ⓐ copy via messageForCode', async () => {
    const res = sampleResponse({
      core: 'ecommerce',
      surface: 'ecommerce_order',
      method: 'POST',
      path: '/api/admin/orders/order-sample-0001/status',
    });
    expect(res.status).toBe(403);
    const body = (await json(res)) as { code: string };
    expect(body.code).toBe(SAMPLE_READ_ONLY);
    expect(messageForCode(body.code)).toBe(
      '샘플 화면에서는 실행되지 않습니다. 로그인하면 실제로 실행됩니다',
    );
  });
});
