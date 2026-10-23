import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import {
  ShippingStatusBar,
  buildShippingStatusSegments,
} from '@/features/ecommerce-ops/components/ShippingStatusBar';
import {
  ApprovalStatusBar,
  buildApprovalStatusSegments,
} from '@/features/erp-ops/components/ApprovalStatusBar';
import { EcommerceOverview } from '@/features/ecommerce-ops';
import type { EcommerceOverviewState } from '@/features/ecommerce-ops';
import { ErpOverviewScreen } from '@/features/erp-ops';
import type { ErpOverviewState } from '@/features/erp-ops';
import type { ShippingStatusCount } from '@/features/ecommerce-ops/api/overview-state';
import type { ApprovalStatusCount } from '@/features/erp-ops';

/**
 * TASK-PC-FE-360 — status-distribution bars on the ecommerce overview (shipping,
 * 4 statuses) and the ERP overview (approval, 6 statuses). Same rules as the
 * TASK-PC-FE-357 order-status bar: ratios, ANY non-ok bucket → no bar (a partial
 * distribution reads as the whole), all-zero → note, numbers remain as text.
 */

vi.mock('next/link', () => ({
  default: ({
    children,
    href,
    ...rest
  }: { children: ReactNode; href: string } & Record<string, unknown>) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));

const pct = (el: HTMLElement) => Number(el.getAttribute('data-pct'));

const ship = (
  status: ShippingStatusCount['status'],
  count: number | null,
  cellStatus: ShippingStatusCount['cellStatus'] = 'ok',
): ShippingStatusCount => ({ status, count, cellStatus });

const appr = (
  status: ApprovalStatusCount['status'],
  count: number | null,
  cellStatus: ApprovalStatusCount['cellStatus'] = 'ok',
): ApprovalStatusCount => ({ status, count, cellStatus });

const SHIPPING_ALL = (c: number): ShippingStatusCount[] => [
  ship('PREPARING', c),
  ship('SHIPPED', c),
  ship('IN_TRANSIT', c),
  ship('DELIVERED', c),
  ship('CANCELLED', c),
];
const APPROVAL_ALL = (c: number): ApprovalStatusCount[] => [
  appr('DRAFT', c),
  appr('SUBMITTED', c),
  appr('IN_REVIEW', c),
  appr('APPROVED', c),
  appr('REJECTED', c),
  appr('WITHDRAWN', c),
];

describe('① shipping status bar', () => {
  it('segments follow lifecycle order with the shipping-status tones', () => {
    const segs = buildShippingStatusSegments([
      ship('PREPARING', 1),
      ship('SHIPPED', 3),
      ship('IN_TRANSIT', 0),
      ship('DELIVERED', 4),
      ship('CANCELLED', 2),
    ]);
    expect(segs?.map((s) => [s.key, s.value, s.tone])).toEqual([
      ['PREPARING', 1, 'warning'],
      ['SHIPPED', 3, 'progress'],
      ['IN_TRANSIT', 0, 'progress'],
      ['DELIVERED', 4, 'success'],
      ['CANCELLED', 2, 'danger'],
    ]);
  });

  it('renders proportional segments; a zero bucket gets no segment', () => {
    render(
      <ShippingStatusBar
        buckets={[
          ship('PREPARING', 1),
          ship('SHIPPED', 3),
          ship('IN_TRANSIT', 0),
          ship('DELIVERED', 4),
        ]}
      />,
    );
    const bar = screen.getByTestId('ecommerce-shipping-status-bar');
    expect(bar).toHaveAttribute('aria-hidden', 'true');
    const seg = (k: string) => bar.querySelector(`[data-segment="${k}"]`) as HTMLElement;
    expect(pct(seg('PREPARING'))).toBe(12.5);
    expect(pct(seg('SHIPPED'))).toBe(37.5);
    expect(seg('IN_TRANSIT')).toBeNull();
    expect(pct(seg('DELIVERED'))).toBe(50);
  });

  it('ONE degraded bucket → no bar and no note (never a partial distribution)', () => {
    const { container } = render(
      <ShippingStatusBar
        buckets={[
          ship('PREPARING', 1),
          ship('SHIPPED', null, 'degraded'),
          ship('IN_TRANSIT', 2),
          ship('DELIVERED', 4),
        ]}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('a forbidden (403) bucket → no bar', () => {
    const { container } = render(
      <ShippingStatusBar
        buckets={[ship('PREPARING', null, 'forbidden'), ship('SHIPPED', 1)]}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('empty list → nothing; all zero → «배송이 없습니다.» instead of an empty bar', () => {
    const empty = render(<ShippingStatusBar buckets={[]} />);
    expect(empty.container).toBeEmptyDOMElement();
    empty.unmount();
    render(<ShippingStatusBar buckets={SHIPPING_ALL(0)} />);
    expect(screen.getByTestId('ecommerce-shipping-status-empty')).toHaveTextContent(
      '배송이 없습니다.',
    );
    expect(screen.queryByTestId('ecommerce-shipping-status-bar')).toBeNull();
  });
});

describe('② approval status bar', () => {
  it('segments follow lifecycle order with the approval-status tones', () => {
    const segs = buildApprovalStatusSegments(APPROVAL_ALL(1));
    expect(segs?.map((s) => [s.key, s.tone])).toEqual([
      ['DRAFT', 'neutral'],
      ['SUBMITTED', 'warning'],
      ['IN_REVIEW', 'progress'],
      ['APPROVED', 'success'],
      ['REJECTED', 'danger'],
      ['WITHDRAWN', 'neutral'],
    ]);
  });

  it('renders proportional segments', () => {
    render(
      <ApprovalStatusBar
        buckets={[
          appr('DRAFT', 0),
          appr('SUBMITTED', 2),
          appr('IN_REVIEW', 1),
          appr('APPROVED', 4),
          appr('REJECTED', 1),
          appr('WITHDRAWN', 0),
        ]}
      />,
    );
    const bar = screen.getByTestId('erp-overview-approval-status-bar');
    const seg = (k: string) => bar.querySelector(`[data-segment="${k}"]`) as HTMLElement;
    expect(pct(seg('SUBMITTED'))).toBe(25);
    expect(pct(seg('IN_REVIEW'))).toBe(12.5);
    expect(pct(seg('APPROVED'))).toBe(50);
    expect(pct(seg('REJECTED'))).toBe(12.5);
    expect(seg('DRAFT')).toBeNull();
    expect(seg('WITHDRAWN')).toBeNull();
  });

  it('ONE degraded bucket → no bar', () => {
    const buckets = APPROVAL_ALL(2);
    buckets[3] = appr('APPROVED', null, 'degraded');
    const { container } = render(<ApprovalStatusBar buckets={buckets} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('a forbidden (403) bucket → no bar', () => {
    const buckets = APPROVAL_ALL(2);
    buckets[1] = appr('SUBMITTED', null, 'forbidden');
    const { container } = render(<ApprovalStatusBar buckets={buckets} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('empty list → nothing; all zero → «결재가 없습니다.»', () => {
    const empty = render(<ApprovalStatusBar buckets={[]} />);
    expect(empty.container).toBeEmptyDOMElement();
    empty.unmount();
    render(<ApprovalStatusBar buckets={APPROVAL_ALL(0)} />);
    expect(screen.getByTestId('erp-overview-approval-status-empty')).toHaveTextContent(
      '결재가 없습니다.',
    );
    expect(screen.queryByTestId('erp-overview-approval-status-bar')).toBeNull();
  });
});

const ecommerceState = (
  shippingStatus: ShippingStatusCount[],
): EcommerceOverviewState => ({
  notEligible: false,
  counts: [],
  orderStatus: [],
  shippingStatus,
  recentOrders: null,
  recentOrdersStatus: 'ok',
  recentSellers: null,
  recentSellersStatus: 'ok',
  insights: null,
  insightsStatus: 'ok',
  period: 'month',
  performance: { status: 'degraded', value: null },
});

describe('③ EcommerceOverview shipping section', () => {
  it('bar + a labelled number per status (swatch beside the text); order block untouched', () => {
    render(
      <EcommerceOverview
        state={ecommerceState([
          ship('PREPARING', 1),
          ship('SHIPPED', 3),
          ship('IN_TRANSIT', 0),
          ship('DELIVERED', 4),
        ])}
      />,
    );
    expect(screen.getByRole('heading', { name: '배송 상태' })).toBeInTheDocument();
    expect(screen.getByTestId('ecommerce-shipping-status-bar')).toBeInTheDocument();
    expect(screen.getByTestId('shipping-status-PREPARING')).toHaveTextContent('1');
    expect(screen.getByTestId('shipping-status-SHIPPED')).toHaveTextContent('3');
    expect(screen.getByTestId('shipping-status-IN_TRANSIT')).toHaveTextContent('0');
    expect(screen.getByTestId('shipping-status-DELIVERED')).toHaveTextContent('4');
    const list = screen.getByTestId('ecommerce-shipping-status');
    expect(list).toHaveTextContent('준비중');
    expect(list).toHaveTextContent('배송완료');
    // existing order-status block still there
    expect(screen.getByTestId('ecommerce-order-status')).toBeInTheDocument();
  });

  it('one degraded bucket: its number shows «—», the others keep their numbers, no bar', () => {
    render(
      <EcommerceOverview
        state={ecommerceState([
          ship('PREPARING', 1),
          ship('SHIPPED', null, 'degraded'),
          ship('IN_TRANSIT', 2),
          ship('DELIVERED', 4),
        ])}
      />,
    );
    expect(screen.queryByTestId('ecommerce-shipping-status-bar')).toBeNull();
    expect(screen.getByTestId('shipping-status-SHIPPED')).toHaveTextContent('—');
    expect(screen.getByTestId('shipping-status-DELIVERED')).toHaveTextContent('4');
  });

  it('all zero: note instead of bar, numbers 0', () => {
    render(<EcommerceOverview state={ecommerceState(SHIPPING_ALL(0))} />);
    expect(screen.getByTestId('ecommerce-shipping-status-empty')).toBeInTheDocument();
    expect(screen.getByTestId('shipping-status-PREPARING')).toHaveTextContent('0');
  });
});

const erpState = (approvalStatus: ApprovalStatusCount[]): ErpOverviewState => ({
  notEligible: false,
  counts: [{ key: 'departments', label: '부서', count: 5, status: 'ok' }],
  approvalStatus,
  period: 'month',
  performance: { status: 'degraded', value: null },
});

describe('④ ErpOverviewScreen approval section', () => {
  it('bar + scope note + a labelled number per status; count tiles untouched', () => {
    render(
      <ErpOverviewScreen
        state={erpState([
          appr('DRAFT', 0),
          appr('SUBMITTED', 2),
          appr('IN_REVIEW', 1),
          appr('APPROVED', 4),
          appr('REJECTED', 1),
          appr('WITHDRAWN', 0),
        ])}
      />,
    );
    expect(screen.getByRole('heading', { name: '결재 상태' })).toBeInTheDocument();
    expect(screen.getByTestId('erp-overview-approval-status-scope')).toHaveTextContent(
      '운영자 권한이면 전체 결재',
    );
    expect(screen.getByTestId('erp-overview-approval-status-bar')).toBeInTheDocument();
    expect(screen.getByTestId('erp-overview-approval-status-SUBMITTED')).toHaveTextContent('2');
    expect(screen.getByTestId('erp-overview-approval-status-APPROVED')).toHaveTextContent('4');
    const list = screen.getByTestId('erp-overview-approval-status-list');
    expect(list).toHaveTextContent('상신됨');
    expect(list).toHaveTextContent('회수됨');
    expect(screen.getByTestId('erp-overview-departments-count')).toHaveTextContent('5');
  });

  it('one degraded bucket: «—» for it, no bar, the other numbers stay', () => {
    const buckets = APPROVAL_ALL(3);
    buckets[2] = appr('IN_REVIEW', null, 'degraded');
    render(<ErpOverviewScreen state={erpState(buckets)} />);
    expect(screen.queryByTestId('erp-overview-approval-status-bar')).toBeNull();
    expect(screen.getByTestId('erp-overview-approval-status-IN_REVIEW')).toHaveTextContent('—');
    expect(screen.getByTestId('erp-overview-approval-status-APPROVED')).toHaveTextContent('3');
  });

  it('all zero → note; no distribution at all (empty list) → section hidden', () => {
    const zero = render(<ErpOverviewScreen state={erpState(APPROVAL_ALL(0))} />);
    expect(screen.getByTestId('erp-overview-approval-status-empty')).toBeInTheDocument();
    zero.unmount();
    render(<ErpOverviewScreen state={erpState([])} />);
    expect(screen.queryByTestId('erp-overview-approval-status')).toBeNull();
  });
});
