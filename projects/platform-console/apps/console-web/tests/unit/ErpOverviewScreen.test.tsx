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
});
