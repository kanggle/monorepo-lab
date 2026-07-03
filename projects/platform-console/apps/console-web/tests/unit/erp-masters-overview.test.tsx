import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ErpMastersOverview } from '@/features/erp-ops';
import type { ErpMastersOverviewState } from '@/features/erp-ops';

/**
 * TASK-PC-FE-161 — `ErpMastersOverview` presentation. 5 master count tiles
 * (부서/직원/직급/원가센터/거래처) as read-only stat tiles (NOT nav links —
 * single-route masters screen). No distribution / recent (thinnest of the 4).
 */

const baseState = (
  over: Partial<ErpMastersOverviewState> = {},
): ErpMastersOverviewState => ({
  notEligible: false,
  counts: [
    { key: 'departments', label: '부서', count: 5, status: 'ok' },
    { key: 'employees', label: '직원', count: 120, status: 'ok' },
    { key: 'jobGrades', label: '직급', count: 8, status: 'ok' },
    { key: 'costCenters', label: '원가센터', count: 14, status: 'ok' },
    { key: 'businessPartners', label: '거래처', count: 37, status: 'ok' },
  ],
  ...over,
});

describe('ErpMastersOverview (TASK-PC-FE-161)', () => {
  it('notEligible → renders nothing', () => {
    const { container } = render(
      <ErpMastersOverview state={baseState({ notEligible: true, counts: [] })} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('renders the 5 master count tiles (not links) with their totals', () => {
    render(<ErpMastersOverview state={baseState()} />);
    expect(screen.getByTestId('erp-departments-count')).toHaveTextContent('5');
    expect(screen.getByTestId('erp-employees-count')).toHaveTextContent('120');
    expect(screen.getByTestId('erp-jobGrades-count')).toHaveTextContent('8');
    expect(screen.getByTestId('erp-costCenters-count')).toHaveTextContent('14');
    expect(screen.getByTestId('erp-businessPartners-count')).toHaveTextContent(
      '37',
    );
    // Stat tiles, NOT nav links.
    expect(screen.getByTestId('erp-overview').querySelector('a')).toBeNull();
  });

  it('a non-ok count cell renders a placeholder + reflects its status indicator', () => {
    render(
      <ErpMastersOverview
        state={baseState({
          counts: [
            { key: 'departments', label: '부서', count: null, status: 'degraded' },
            { key: 'employees', label: '직원', count: null, status: 'forbidden' },
          ],
        })}
      />,
    );
    expect(
      screen.getByTestId('erp-departments-count-degraded'),
    ).toHaveTextContent('점검 필요');
    expect(screen.getByTestId('erp-employees-count-degraded')).toHaveTextContent(
      '권한 없음',
    );
    expect(screen.queryByTestId('erp-departments-count')).toBeNull();
    expect(
      screen.getByTestId('erp-departments-service-status'),
    ).toHaveAttribute('data-status', 'degraded');
  });
});
