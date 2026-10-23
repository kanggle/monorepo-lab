import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ErpOverviewScreen } from '@/features/erp-ops';
import type { ErpOverviewState } from '@/features/erp-ops';

/**
 * TASK-PC-FE-232 — `ErpOverviewScreen` presentation, the `/erp` overview
 * landing. 7 count tiles (부서/직원/직급/원가센터/거래처/결재 대기/활성 위임)
 * as read-only stat tiles + a shortcuts nav to the other 5 erp screens
 * (PROMOTES the former masters-embedded `ErpMastersOverview` tile logic,
 * TASK-PC-FE-161).
 */

const baseState = (over: Partial<ErpOverviewState> = {}): ErpOverviewState => ({
  notEligible: false,
  counts: [
    { key: 'departments', label: '부서', count: 5, status: 'ok' },
    { key: 'employees', label: '직원', count: 120, status: 'ok' },
    { key: 'jobGrades', label: '직급', count: 8, status: 'ok' },
    { key: 'costCenters', label: '원가센터', count: 14, status: 'ok' },
    { key: 'businessPartners', label: '거래처', count: 37, status: 'ok' },
    { key: 'pendingApprovals', label: '결재 대기', count: 3, status: 'ok' },
    { key: 'activeDelegations', label: '활성 위임', count: 2, status: 'ok' },
  ],
  approvalStatus: [
    { status: 'DRAFT', count: 0, cellStatus: 'ok' },
    { status: 'SUBMITTED', count: 2, cellStatus: 'ok' },
    { status: 'IN_REVIEW', count: 1, cellStatus: 'ok' },
    { status: 'APPROVED', count: 1, cellStatus: 'ok' },
    { status: 'REJECTED', count: 1, cellStatus: 'ok' },
    { status: 'WITHDRAWN', count: 0, cellStatus: 'ok' },
  ],
  // TASK-MONO-809 — the «실적» strip (approval-service /metrics).
  period: 'month',
  performance: {
    status: 'ok',
    value: {
      period: 'month',
      timezone: 'Asia/Seoul',
      current: { from: '2026-10-01T00:00:00+09:00', to: '2026-10-24T14:05:00+09:00' },
      previous: { from: '2026-09-01T00:00:00+09:00', to: '2026-09-24T14:05:00+09:00' },
      metrics: [
        { key: 'submitted', unit: 'count', currency: null, current: '42', previous: '30' },
        { key: 'approved', unit: 'count', currency: null, current: '30', previous: '25' },
        { key: 'rejected', unit: 'count', currency: null, current: '5', previous: '6' },
      ],
      series: [
        { date: '2026-10-01', values: { submitted: '3', approved: '2', rejected: '0' } },
        { date: '2026-10-02', values: { submitted: '0', approved: '1', rejected: '1' } },
      ],
    },
  },
  ...over,
});

describe('ErpOverviewScreen (TASK-PC-FE-232)', () => {
  it('renders the heading + all 7 count tiles with their totals', () => {
    render(<ErpOverviewScreen state={baseState()} />);
    expect(
      screen.getByRole('heading', { name: 'ERP 개요' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('erp-overview-departments-count')).toHaveTextContent(
      '5',
    );
    expect(screen.getByTestId('erp-overview-employees-count')).toHaveTextContent(
      '120',
    );
    expect(screen.getByTestId('erp-overview-jobGrades-count')).toHaveTextContent(
      '8',
    );
    expect(
      screen.getByTestId('erp-overview-costCenters-count'),
    ).toHaveTextContent('14');
    expect(
      screen.getByTestId('erp-overview-businessPartners-count'),
    ).toHaveTextContent('37');
    expect(
      screen.getByTestId('erp-overview-pendingApprovals-count'),
    ).toHaveTextContent('3');
    expect(
      screen.getByTestId('erp-overview-activeDelegations-count'),
    ).toHaveTextContent('2');
  });

  it('renders shortcut links to 가이드/마스터/통합 조회/결재함/위임', () => {
    render(<ErpOverviewScreen state={baseState()} />);
    expect(screen.getByTestId('erp-overview-link-guide')).toHaveAttribute(
      'href',
      '/erp/guide',
    );
    expect(screen.getByTestId('erp-overview-link-masters')).toHaveAttribute(
      'href',
      '/erp/masters',
    );
    expect(screen.getByTestId('erp-overview-link-orgview')).toHaveAttribute(
      'href',
      '/erp/orgview',
    );
    expect(screen.getByTestId('erp-overview-link-approval')).toHaveAttribute(
      'href',
      '/erp/approval',
    );
    expect(
      screen.getByTestId('erp-overview-link-delegation'),
    ).toHaveAttribute('href', '/erp/delegation');
  });

  it('a degraded/forbidden cell renders a placeholder + reflects its status indicator, WITHOUT blanking sibling tiles', () => {
    render(
      <ErpOverviewScreen
        state={baseState({
          counts: [
            { key: 'departments', label: '부서', count: 5, status: 'ok' },
            {
              key: 'pendingApprovals',
              label: '결재 대기',
              count: null,
              status: 'degraded',
            },
            {
              key: 'activeDelegations',
              label: '활성 위임',
              count: null,
              status: 'forbidden',
            },
          ],
        })}
      />,
    );
    // The ok tile still renders its value.
    expect(screen.getByTestId('erp-overview-departments-count')).toHaveTextContent(
      '5',
    );
    // The degraded/forbidden tiles render placeholders, not the ok tile format.
    expect(
      screen.getByTestId('erp-overview-pendingApprovals-count-degraded'),
    ).toHaveTextContent('점검 필요');
    expect(
      screen.getByTestId('erp-overview-activeDelegations-count-degraded'),
    ).toHaveTextContent('권한 없음');
    expect(
      screen.queryByTestId('erp-overview-pendingApprovals-count'),
    ).toBeNull();
    expect(
      screen.getByTestId('erp-overview-pendingApprovals-service-status'),
    ).toHaveAttribute('data-status', 'degraded');
    expect(
      screen.getByTestId('erp-overview-activeDelegations-service-status'),
    ).toHaveAttribute('data-status', 'forbidden');
  });

  // ── TASK-MONO-809 — the «실적» strip ────────────────────────────────────

  it('performance: the strip is the first block, with the ERP labels and period links', () => {
    render(<ErpOverviewScreen state={baseState()} />);
    const section = screen.getByTestId('erp-overview');
    const strip = screen.getByTestId('erp-performance');
    expect(section.firstElementChild).toBe(strip);
    expect(screen.getByTestId('erp-performance-kpi-submitted')).toHaveTextContent('상신');
    expect(screen.getByTestId('erp-performance-kpi-submitted-value')).toHaveTextContent('42');
    expect(screen.getByTestId('erp-performance-kpi-approved')).toHaveTextContent('승인');
    expect(screen.getByTestId('erp-performance-kpi-rejected')).toHaveTextContent('반려');
    expect(screen.getByTestId('erp-performance-trend')).toHaveTextContent('상신 · 일별');
    expect(screen.getByTestId('erp-performance-period-week')).toHaveAttribute('href', '/erp?period=week');
    expect(screen.getByTestId('erp-performance-period-month')).toHaveAttribute('href', '/erp?period=month');
    expect(screen.getByTestId('erp-performance-period-month')).toHaveAttribute('aria-current', 'page');
    expect(screen.getByTestId('erp-performance-scope')).toHaveTextContent('운영자 권한이면 전체 결재');
  });

  it('performance: period links keep an existing asOf; the asOf note appears only with asOf', () => {
    const { unmount } = render(<ErpOverviewScreen state={baseState()} />);
    expect(screen.queryByTestId('erp-performance-asof-note')).toBeNull();
    unmount();

    render(<ErpOverviewScreen state={baseState({ period: 'week' })} asOf="2026-01-01" />);
    expect(screen.getByTestId('erp-performance-period-week')).toHaveAttribute(
      'href',
      '/erp?asOf=2026-01-01&period=week',
    );
    expect(screen.getByTestId('erp-performance-period-month')).toHaveAttribute(
      'href',
      '/erp?asOf=2026-01-01&period=month',
    );
    expect(screen.getByTestId('erp-performance-period-week')).toHaveAttribute('aria-current', 'page');
    expect(screen.getByTestId('erp-performance-asof-note')).toHaveTextContent(
      '실적 줄은 기준일(asOf)과 관계없이 오늘까지의 기간입니다.',
    );
  });

  it('performance: degraded / forbidden strip says so; the tiles below still render', () => {
    const { unmount } = render(
      <ErpOverviewScreen state={baseState({ performance: { status: 'degraded', value: null } })} />,
    );
    expect(screen.getByTestId('erp-performance-degraded')).toHaveTextContent('실적을 불러올 수 없습니다');
    expect(screen.getByTestId('erp-overview-departments-count')).toHaveTextContent('5');
    unmount();

    render(<ErpOverviewScreen state={baseState({ performance: { status: 'forbidden', value: null } })} />);
    expect(screen.getByTestId('erp-performance-forbidden')).toHaveTextContent('권한이 없습니다');
    expect(screen.queryByTestId('erp-performance-kpi-submitted')).toBeNull();
  });
});
