import { redirect } from 'next/navigation';
import { ApiError } from '@/shared/api/errors';
import { getProductsSummary } from './products-api';
import { getOrdersSummary, listOrders } from './orders-api';
import { getUsersSummary } from './users-api';
import { getPromotionsSummary } from './promotions-api';
import { getShippingsSummary } from './shippings-api';
import { getSellersSummary, listSellers } from './sellers-api';
import { getTemplatesSummary } from './notifications-api';
import {
  ORDER_STATUS_VALUES,
  type OrderStatus,
  type OrderSummary,
} from './order-types';
import type { SellerSummary } from './seller-types';

/**
 * Server-side ecommerce **operator overview snapshot** fan-out for the
 * `/ecommerce` landing (TASK-PC-FE-156 — realizes the "operator-overview
 * snapshot leg" the §2.4.10 landing deferred; TASK-PC-FE-164 — switches the
 * 7 area count legs from `list*({page:0,size:1})` to dedicated `/summary`
 * endpoints that return period-based counts: today / week / month / total).
 *
 * ── ARCHITECTURE (console-web direct fan-out; per-service `/summary` endpoints) ──
 * Per TASK-PC-FE-164 each area now calls a dedicated `GET .../summary` endpoint
 * (uniform response `{ today, week, month, total }`) instead of the previous
 * `list*({page:0,size:1})` leg. Per §2.4.10 the ecommerce operator surface is
 * console-web → ecommerce gateway DIRECT (domain-facing IAM OIDC token), so
 * each summary call reuses the feature's own `get*Summary()` api functions
 * server-side. No console-bff leg (contrast: the console-wide §2.4.9.1
 * operator overview is a BFF fan-out — this one is domain-internal, so the
 * direct model fits).
 *
 * ── RESILIENCE (§2.4.10 / §2.5) — the decisive rule ──
 * The fan-out is bounded + parallel. Each cell CATCHES its own error into a
 * cell status (ok / forbidden / degraded) EXCEPT `401`, which it re-throws so
 * the top-level catch performs a whole-session `redirect('/login')` (no partial
 * authed state — same invariant as `mapSectionResilience`). One area's degrade
 * never blanks the section. No auto-refetch (operator-overview discipline).
 */

export type CellStatus = 'ok' | 'forbidden' | 'degraded';

/** One operator-area count card (label + period counts + per-cell status + its link). */
export interface AreaCount {
  key: string;
  label: string;
  href: string;
  testid: string;
  /** `total` from the summary, or `null` when the cell did not resolve.
   *  Kept as `count` for back-compat with existing tests / consumers. */
  count: number | null;
  /** Period-scoped counts from the `/summary` endpoint (null when degraded/forbidden). */
  today: number | null;
  week: number | null;
  month: number | null;
  status: CellStatus;
}

/** One order-status distribution bucket. */
export interface OrderStatusCount {
  status: OrderStatus;
  count: number | null;
  cellStatus: CellStatus;
}

export interface EcommerceOverviewState {
  /** True when the operator is not ecommerce-eligible — no fan-out was run. */
  notEligible: boolean;
  counts: AreaCount[];
  orderStatus: OrderStatusCount[];
  recentOrders: OrderSummary[] | null;
  recentOrdersStatus: CellStatus;
  recentSellers: SellerSummary[] | null;
  recentSellersStatus: CellStatus;
}

const EMPTY: EcommerceOverviewState = {
  notEligible: false,
  counts: [],
  orderStatus: [],
  recentOrders: null,
  recentOrdersStatus: 'degraded',
  recentSellers: null,
  recentSellersStatus: 'degraded',
};

/** Period summary shape returned by each `/summary` endpoint. */
interface AreaSummary {
  today: number;
  week: number;
  month: number;
  total: number;
}

/**
 * The 7 operator areas, keyed to their `/summary` endpoint. Labels/hrefs/testids
 * mirror `ConsoleSidebarNav` + the PC-FE-155 landing grid (back-compat testids).
 * TASK-PC-FE-164: `count` thunk replaced by `summary` thunk returning period data.
 */
const AREAS: ReadonlyArray<{
  key: string;
  label: string;
  href: string;
  testid: string;
  summary: () => Promise<AreaSummary>;
}> = [
  { key: 'products', label: '상품', href: '/ecommerce/products', testid: 'ecommerce-products-link', summary: getProductsSummary },
  { key: 'orders', label: '주문', href: '/ecommerce/orders', testid: 'ecommerce-orders-link', summary: getOrdersSummary },
  { key: 'shippings', label: '배송', href: '/ecommerce/shippings', testid: 'ecommerce-shippings-link', summary: getShippingsSummary },
  { key: 'promotions', label: '프로모션', href: '/ecommerce/promotions', testid: 'ecommerce-promotions-link', summary: getPromotionsSummary },
  { key: 'users', label: '사용자', href: '/ecommerce/users', testid: 'ecommerce-users-link', summary: getUsersSummary },
  { key: 'sellers', label: '셀러', href: '/ecommerce/sellers', testid: 'ecommerce-sellers-link', summary: getSellersSummary },
  { key: 'notifications', label: '알림 템플릿', href: '/ecommerce/notifications/templates', testid: 'ecommerce-notifications-link', summary: getTemplatesSummary },
];

/** Recent-activity page size (clamped to [1, max] by the list clients). */
const RECENT_SIZE = 5;

interface Cell<T> {
  value: T | null;
  status: CellStatus;
}

/**
 * Resolve a single fan-out leg into a cell: success → `ok`; `403` → `forbidden`;
 * `503`/timeout/network/other → `degraded`. A `401` is RE-THROWN so the caller
 * can perform a whole-session `redirect('/login')` (never a per-cell degrade).
 */
async function cell<T>(p: Promise<T>): Promise<Cell<T>> {
  try {
    return { value: await p, status: 'ok' };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      throw err; // whole-session re-login — propagate, do not degrade.
    }
    if (err instanceof ApiError && err.status === 403) {
      return { value: null, status: 'forbidden' };
    }
    return { value: null, status: 'degraded' };
  }
}

/**
 * @param eligible whether the operator is ecommerce-eligible (resolved by the
 *   page from the data-driven registry). `false` ⇒ no fan-out, no calls.
 */
export async function getEcommerceOverviewState(
  eligible: boolean,
): Promise<EcommerceOverviewState> {
  if (!eligible) {
    return { ...EMPTY, notEligible: true };
  }

  try {
    const [summaryCells, statusCells, recentOrdersCell, recentSellersCell] =
      await Promise.all([
        Promise.all(AREAS.map((a) => cell(a.summary()))),
        Promise.all(
          ORDER_STATUS_VALUES.map((s) =>
            cell(listOrders({ status: s, page: 0, size: 1 })),
          ),
        ),
        cell(listOrders({ page: 0, size: RECENT_SIZE })),
        cell(listSellers({ page: 0, size: RECENT_SIZE })),
      ]);

    const counts: AreaCount[] = AREAS.map((a, i) => {
      const c = summaryCells[i];
      if (c.status === 'ok' && c.value !== null) {
        return {
          key: a.key,
          label: a.label,
          href: a.href,
          testid: a.testid,
          count: c.value.total,
          today: c.value.today,
          week: c.value.week,
          month: c.value.month,
          status: 'ok',
        };
      }
      return {
        key: a.key,
        label: a.label,
        href: a.href,
        testid: a.testid,
        count: null,
        today: null,
        week: null,
        month: null,
        status: c.status,
      };
    });

    const orderStatus: OrderStatusCount[] = ORDER_STATUS_VALUES.map((s, i) => ({
      status: s,
      count: statusCells[i].value?.totalElements ?? null,
      cellStatus: statusCells[i].status,
    }));

    return {
      notEligible: false,
      counts,
      orderStatus,
      recentOrders: recentOrdersCell.value?.content.slice(0, RECENT_SIZE) ?? null,
      recentOrdersStatus: recentOrdersCell.status,
      recentSellers:
        recentSellersCell.value?.content.slice(0, RECENT_SIZE) ?? null,
      recentSellersStatus: recentSellersCell.status,
    };
  } catch (err) {
    // Only a `401` re-thrown by a cell reaches here → whole-session re-login.
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login');
    }
    throw err;
  }
}
