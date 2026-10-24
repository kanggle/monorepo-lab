import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { render, screen, within, cleanup } from '@testing-library/react';
import {
  IamOverviewScreen,
  IAM_PERFORMANCE_SCOPE_NOTE,
} from '@/features/iam-overview';
import type { IamOverviewState } from '@/features/iam-overview';
import type { AuditRow } from '@/features/audit/api/types';
import type { PerformanceCell } from '@/shared/api/performance-metrics';
import { runAxe } from '../a11y/axe-helper';

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

/** `GET /api/admin/metrics` (TASK-MONO-811) — four counts, in the producer's order. */
const PERF_OK: PerformanceCell = {
  status: 'ok',
  value: {
    period: 'month',
    timezone: 'Asia/Seoul',
    current: { from: '2026-10-01T00:00:00+09:00', to: '2026-10-24T14:05:00+09:00' },
    previous: { from: '2026-09-01T00:00:00+09:00', to: '2026-09-24T14:05:00+09:00' },
    metrics: [
      { key: 'loginSuccess', unit: 'count', currency: null, current: '4120', previous: '3980' },
      { key: 'loginFailure', unit: 'count', currency: null, current: '37', previous: '41' },
      { key: 'accountLocked', unit: 'count', currency: null, current: '3', previous: '5' },
      { key: 'newAccount', unit: 'count', currency: null, current: '18', previous: '12' },
    ],
    series: [
      {
        date: '2026-10-01',
        values: { loginSuccess: '180', loginFailure: '2', accountLocked: '0', newAccount: '1' },
      },
      {
        date: '2026-10-02',
        values: { loginSuccess: '0', loginFailure: '0', accountLocked: '0', newAccount: '0' },
      },
    ],
  },
};

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
    period: 'month',
    performance: PERF_OK,
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
    render(
      <IamOverviewScreen
        state={state({ noActiveTenant: true, tenantNoticeKind: 'select' })}
      />,
    );
    const el = screen.getByTestId('iam-overview-no-tenant');
    expect(el).toBeInTheDocument();
    expect(el).toHaveTextContent('테넌트를 먼저 선택하세요');
    expect(screen.queryByTestId('iam-overview')).not.toBeInTheDocument();
    expect(screen.queryByTestId('iam-overview-operators')).not.toBeInTheDocument();
  });

  it('🔴🔴 TASK-PC-FE-301 — 0 selectable tenants (tenantNoticeKind=zero) → the no-reachable-tenant notice, same shared body every other gated screen uses', () => {
    cleanup();
    render(
      <IamOverviewScreen
        state={state({ noActiveTenant: true, tenantNoticeKind: 'zero' })}
      />,
    );
    const el = screen.getByTestId('iam-overview-no-tenant');
    expect(el).toHaveTextContent('이 계정에는 접근 가능한 테넌트가 없습니다');
    expect(el).not.toHaveTextContent('테넌트를 먼저 선택하세요');
  });

  it('tenantNoticeKind missing (defensive default) → falls back to select, never crashes', () => {
    cleanup();
    render(<IamOverviewScreen state={state({ noActiveTenant: true })} />);
    expect(screen.getByTestId('iam-overview-no-tenant')).toHaveTextContent(
      '테넌트를 먼저 선택하세요',
    );
  });
});

describe('IamOverviewScreen — «실적» strip (TASK-MONO-811)', () => {
  it('is the first block of the overview, before the cards', () => {
    render(<IamOverviewScreen state={state()} />);
    const overview = screen.getByTestId('iam-overview');
    const strip = screen.getByTestId('iam-performance');
    expect(overview.firstElementChild).toBe(strip);
    expect(within(strip).getByRole('heading', { name: '실적' })).toBeInTheDocument();
  });

  it('renders four cells in order — 로그인 성공 · 로그인 실패 · 계정 잠금 · 신규 계정 — with their values', () => {
    render(<IamOverviewScreen state={state()} />);
    const strip = screen.getByTestId('iam-performance');
    const cells = within(strip)
      .getAllByRole('listitem')
      .filter((li) => /^iam-performance-kpi-[A-Za-z]+$/.test(li.getAttribute('data-testid') ?? ''));
    expect(cells.map((li) => li.getAttribute('data-testid'))).toEqual([
      'iam-performance-kpi-loginSuccess',
      'iam-performance-kpi-loginFailure',
      'iam-performance-kpi-accountLocked',
      'iam-performance-kpi-newAccount',
    ]);
    const labels = ['로그인 성공', '로그인 실패', '계정 잠금', '신규 계정'];
    cells.forEach((li, i) => expect(within(li).getByText(labels[i])).toBeInTheDocument());
    expect(screen.getByTestId('iam-performance-kpi-loginSuccess-value')).toHaveTextContent('4,120');
    expect(screen.getByTestId('iam-performance-kpi-loginFailure-value')).toHaveTextContent('37');
    expect(screen.getByTestId('iam-performance-kpi-accountLocked-value')).toHaveTextContent('3');
    expect(screen.getByTestId('iam-performance-kpi-newAccount-value')).toHaveTextContent('18');
    // The trend chart draws 로그인 성공; the cards are not links.
    expect(screen.getByTestId('iam-performance-trend')).toHaveTextContent('로그인 성공 · 일별');
    expect(screen.queryByTestId('iam-performance-kpi-loginSuccess-link')).toBeNull();
  });

  it('carries the consumer-pool scope note — and the contract quotes it verbatim', () => {
    render(<IamOverviewScreen state={state()} />);
    expect(screen.getByTestId('iam-performance-scope')).toHaveTextContent(IAM_PERFORMANCE_SCOPE_NOTE);
    expect(IAM_PERFORMANCE_SCOPE_NOTE).toContain('소비자 풀 계정이 빠집니다');
    expect(IAM_PERFORMANCE_SCOPE_NOTE).toContain('이 사이트로 가입한 소비자가 포함됩니다');
    const contract = readFileSync(
      path.resolve(__dirname, '..', '..', '..', '..', 'specs', 'contracts', 'console-integration-contract.md'),
      'utf8',
    );
    expect(contract).toContain(`«${IAM_PERFORMANCE_SCOPE_NOTE}»`);
  });

  it('period switch links to /iam?period=…, the selected one marked', () => {
    render(<IamOverviewScreen state={state({ period: 'week' })} />);
    expect(screen.getByTestId('iam-performance-period-week')).toHaveAttribute('href', '/iam?period=week');
    expect(screen.getByTestId('iam-performance-period-week')).toHaveAttribute('aria-current', 'page');
    expect(screen.getByTestId('iam-performance-period-month')).toHaveAttribute('href', '/iam?period=month');
  });

  it('a failed strip says «불러올 수 없습니다» in words, shows no number (never 0) — the cards stay', () => {
    render(<IamOverviewScreen state={state({ performance: { status: 'degraded', value: null } })} />);
    expect(screen.getByTestId('iam-performance-degraded')).toHaveTextContent('실적을 불러올 수 없습니다');
    expect(screen.getByTestId('iam-performance').textContent).not.toMatch(/\d/);
    expect(screen.queryByTestId('iam-performance-kpi-loginSuccess')).toBeNull();
    expect(screen.getByTestId('iam-overview-operators-total')).toHaveTextContent('10');
    expect(screen.getByTestId('iam-overview-audit-total')).toHaveTextContent('57');
  });

  it('forbidden strip → «권한이 없습니다», the rest of the overview renders', () => {
    render(<IamOverviewScreen state={state({ performance: { status: 'forbidden', value: null } })} />);
    expect(screen.getByTestId('iam-performance-forbidden')).toHaveTextContent('실적을 볼 권한이 없습니다');
    expect(screen.getByTestId('iam-performance').textContent).not.toMatch(/\d/);
    expect(screen.getByTestId('iam-overview-accounts-total')).toHaveTextContent('123');
    expect(screen.getByTestId('iam-overview-audit-recent')).toBeInTheDocument();
  });

  it('no active tenant → no strip (the page-level gate only)', () => {
    cleanup();
    render(<IamOverviewScreen state={state({ noActiveTenant: true, tenantNoticeKind: 'select' })} />);
    expect(screen.queryByTestId('iam-performance')).toBeNull();
  });

  it('is axe-clean', async () => {
    const { container } = render(<IamOverviewScreen state={state()} />);
    expect(await runAxe(container)).toEqual([]);
  });
});
