import { describe, it, expect } from 'vitest';
import { render, screen, within, cleanup } from '@testing-library/react';
import { IamOverviewScreen } from '@/features/iam-overview';
import type { IamOverviewState } from '@/features/iam-overview';
import type { AuditRow } from '@/features/audit/api/types';

/**
 * TASK-PC-FE-180 — the live IAM overview presentation. Covers: the count cards
 * (each a quick-launch Link), the operator ACTIVE/SUSPENDED split, the recent
 * audit mini-list, per-cell degrade/forbidden placeholders, and the page-level
 * no-active-tenant gate.
 */

const adminRow = (auditId: string, actionCode: string): AuditRow => ({
  source: 'admin',
  auditId,
  actionCode,
  operatorId: 'op1',
  outcome: 'SUCCESS',
  occurredAt: '2026-07-01T00:00:00Z',
});

function state(overrides: Partial<IamOverviewState> = {}): IamOverviewState {
  return {
    noActiveTenant: false,
    operators: { total: 10, active: 8, suspended: 2, status: 'ok' },
    accounts: { total: 123, locked: 4, status: 'ok' },
    audit: {
      total: 57,
      recent: [adminRow('a1', 'ACCOUNT_LOCK'), adminRow('a2', 'ROLE_GRANT')],
      status: 'ok',
    },
    ...overrides,
  };
}

describe('IamOverviewScreen (TASK-PC-FE-180)', () => {
  it('renders operator/account counts + operator split, each card linking to its screen', () => {
    render(<IamOverviewScreen state={state()} />);

    const ops = screen.getByTestId('iam-overview-operators');
    expect(ops).toHaveAttribute('href', '/operators');
    expect(within(ops).getByTestId('iam-overview-operators-total')).toHaveTextContent(
      '10',
    );
    expect(within(ops).getByTestId('iam-overview-operators-active')).toHaveTextContent(
      '8',
    );
    expect(
      within(ops).getByTestId('iam-overview-operators-suspended'),
    ).toHaveTextContent('2');

    const acc = screen.getByTestId('iam-overview-accounts');
    expect(acc).toHaveAttribute('href', '/accounts');
    expect(within(acc).getByTestId('iam-overview-accounts-total')).toHaveTextContent(
      '123',
    );
    // TASK-PC-FE-181: the LOCKED sub-count (via the BE-475 status filter).
    expect(within(acc).getByTestId('iam-overview-accounts-locked')).toHaveTextContent(
      '4',
    );
  });

  it('renders the audit total + recent rows linking to /audit', () => {
    render(<IamOverviewScreen state={state()} />);
    expect(screen.getByTestId('iam-overview-audit-link')).toHaveAttribute(
      'href',
      '/audit',
    );
    expect(screen.getByTestId('iam-overview-audit-total')).toHaveTextContent('57');
    const recent = screen.getByTestId('iam-overview-audit-recent');
    expect(within(recent).getByText('ACCOUNT_LOCK')).toBeInTheDocument();
    expect(within(recent).getByText('ROLE_GRANT')).toBeInTheDocument();
  });

  it('forbidden operators cell → 권한 없음 placeholder (not a number)', () => {
    render(
      <IamOverviewScreen
        state={state({
          operators: { total: null, active: null, suspended: null, status: 'forbidden' },
        })}
      />,
    );
    expect(
      screen.getByTestId('iam-overview-operators-degraded'),
    ).toHaveTextContent('권한 없음');
    expect(
      screen.queryByTestId('iam-overview-operators-total'),
    ).not.toBeInTheDocument();
  });

  it('degraded accounts cell → 점검 필요 placeholder', () => {
    render(
      <IamOverviewScreen
        state={state({ accounts: { total: null, locked: null, status: 'degraded' } })}
      />,
    );
    expect(
      screen.getByTestId('iam-overview-accounts-degraded'),
    ).toHaveTextContent('점검 필요');
  });

  it('degraded audit cell → placeholder, no recent list', () => {
    render(
      <IamOverviewScreen
        state={state({ audit: { total: null, recent: null, status: 'degraded' } })}
      />,
    );
    expect(screen.getByTestId('iam-overview-audit-degraded')).toHaveTextContent(
      '점검 필요',
    );
    expect(
      screen.queryByTestId('iam-overview-audit-recent'),
    ).not.toBeInTheDocument();
  });

  it('audit ok but empty recent → empty note, no crash', () => {
    render(
      <IamOverviewScreen
        state={state({ audit: { total: 0, recent: [], status: 'ok' } })}
      />,
    );
    expect(screen.getByText('최근 이벤트가 없습니다.')).toBeInTheDocument();
  });

  it('no active tenant → page-level tenant gate, no cards', () => {
    cleanup();
    render(<IamOverviewScreen state={state({ noActiveTenant: true })} />);
    expect(screen.getByTestId('iam-overview-no-tenant')).toBeInTheDocument();
    expect(screen.queryByTestId('iam-overview')).not.toBeInTheDocument();
    expect(screen.queryByTestId('iam-overview-operators')).not.toBeInTheDocument();
  });
});
