import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { BalancesTable } from '@/features/finance-ops/components/BalancesTable';
import { balanceBar } from '@/features/finance-ops/lib/balance-bars';
import { formatMoney } from '@/shared/lib/money';
import { TrialBalanceTable } from '@/features/ledger-ops/components/TrialBalanceTable';
import { trialBalanceBars } from '@/features/ledger-ops/lib/trial-balance-bars';
import { approvalStepProgress } from '@/features/erp-ops/lib/approval-steps';
import { ApprovalStepProgressView } from '@/features/erp-ops/components/ApprovalStepProgressView';
import { OutboundDrillSummary } from '@/features/wms-outbound-ops/components/OutboundDrillSummary';
import {
  OUTBOUND_FLOW,
  OUTBOUND_SIDE_BRANCHES,
  outboundProgress,
} from '@/features/wms-outbound-ops/components/outbound-ops-helpers';
import { StepBar, stepStates } from '@/shared/ui/InlineBar';
import type { Balance } from '@/features/finance-ops/api/types';
import type { TrialBalance } from '@/features/ledger-ops/api/types';
import type {
  OutboundOrderDetail,
  OutboundSaga,
} from '@/features/wms-outbound-ops/api/types';

/**
 * TASK-PC-FE-361 — finance balance bars, ledger trial-balance bar pairs,
 * approval step progress, outbound flow progress. Widths from money strings go
 * through `chartMagnitude` only (F5 geometry exception); every amount text is
 * still the producer string via `formatMoney`.
 */

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn() }),
  usePathname: () => '/x',
  useSearchParams: () => new URLSearchParams(),
}));

const bal = (currency: string, ledger: string, available: string, held: string): Balance => ({
  currency,
  ledger,
  available,
  held,
});

const pcts = (testid: string) =>
  Array.from(screen.getByTestId(testid).children).map((c) =>
    Number((c as HTMLElement).dataset.pct),
  );

const barPct = (testid: string) =>
  Number((screen.getByTestId(testid).firstElementChild as HTMLElement).dataset.pct);

describe('stepStates / StepBar (shared primitive)', () => {
  it('done / current / todo by zero-based position', () => {
    expect(stepStates(0, 3)).toEqual(['current', 'todo', 'todo']);
    expect(stepStates(1, 3)).toEqual(['done', 'current', 'todo']);
    expect(stepStates(2, 3)).toEqual(['done', 'done', 'current']);
  });

  it('unusable numbers draw nothing (0 / 1-based overflow / negative / fraction / null)', () => {
    expect(stepStates(0, 0)).toBeNull();
    expect(stepStates(0, null)).toBeNull();
    expect(stepStates(0, undefined)).toBeNull();
    expect(stepStates(3, 3)).toBeNull(); // a 1-based reading of the 0-based field
    expect(stepStates(-1, 3)).toBeNull();
    expect(stepStates(0.5, 3)).toBeNull();
    expect(stepStates(undefined, 3)).toBeNull();
    expect(stepStates(Number.NaN, 3)).toBeNull();
  });

  it('finished marks every step done, even without a position', () => {
    expect(stepStates(null, 2, true)).toEqual(['done', 'done']);
    expect(stepStates(null, 0, true)).toBeNull();
  });

  it('StepBar is aria-hidden', () => {
    render(<StepBar states={['done', 'current']} labels={['a', 'b']} testid="sb" />);
    expect(screen.getByTestId('sb').getAttribute('aria-hidden')).toBe('true');
  });
});

describe('finance balances — balanceBar helper', () => {
  it('available + held against the ledger', () => {
    const b = balanceBar(bal('KRW', '100000', '80000', '20000'))!;
    expect(b.expected).toBe(100000);
    expect(b.segments.map((s) => [s.key, s.value])).toEqual([
      ['available', 80000],
      ['held', 20000],
    ]);
  });

  it('ledger 0 and nothing available / held → no bar', () => {
    expect(balanceBar(bal('KRW', '0', '0', '0'))).toBeNull();
  });

  it('negative and unparsable amounts count as 0', () => {
    const b = balanceBar(bal('KRW', '100', '-50', 'abc'))!;
    expect(b.segments.map((s) => s.value)).toEqual([0, 0]);
    expect(balanceBar(bal('KRW', 'x', '-5', ''))).toBeNull();
  });
});

describe('BalancesTable — bars', () => {
  it('one bar per currency row, scaled by that row\'s own ledger; text unchanged', () => {
    render(
      <BalancesTable
        balances={[bal('KRW', '100000', '80000', '20000'), bal('USD', '10000', '2500', '0')]}
      />,
    );
    expect(pcts('finance-balance-bar-0')).toEqual([80, 20]);
    expect(pcts('finance-balance-bar-1')).toEqual([25]);
    expect(screen.getByTestId('finance-balance-bar-0').getAttribute('aria-hidden')).toBe('true');
    // the number stays the producer string through formatMoney
    expect(screen.getByTestId('finance-balance-available-0').textContent).toBe(
      formatMoney({ amount: '80000', currency: 'KRW' }),
    );
    expect(screen.getByTestId('finance-balance-held-1').textContent).toBe(
      formatMoney({ amount: '0', currency: 'USD' }),
    );
    expect(screen.getByTestId('finance-balance-bar-scope')).toBeDefined();
  });

  it('held + available above the ledger is shown against the sum, not hidden', () => {
    render(<BalancesTable balances={[bal('KRW', '100', '90', '60')]} />);
    const [a, h] = pcts('finance-balance-bar-0');
    expect(a).toBeCloseTo(60, 5); // 90 / 150
    expect(h).toBeCloseTo(40, 5); // 60 / 150
    expect(screen.getByTestId('finance-balance-bar-0').getAttribute('data-overflow')).toBe('true');
  });

  it('a zero-ledger currency row has no bar; with no bar anywhere there is no scope note', () => {
    render(<BalancesTable balances={[bal('KRW', '0', '0', '0')]} />);
    expect(screen.queryByTestId('finance-balance-bar-0')).toBeNull();
    expect(screen.queryByTestId('finance-balance-bar-scope')).toBeNull();
    // existing testids kept
    expect(screen.getByTestId('finance-balance-row-0')).toBeDefined();
    expect(screen.getByTestId('finance-balance-ledger-0')).toBeDefined();
  });

  it('an unparsable amount keeps its text and draws nothing wrong', () => {
    render(<BalancesTable balances={[bal('KRW', '100000', 'oops', '25000')]} />);
    expect(pcts('finance-balance-bar-0')).toEqual([25]);
  });
});

const money = (amount: string, currency: string) => ({ amount, currency });

function tb(
  accounts: { code: string; d: string; c: string; dc?: string; cc?: string }[],
  inBalance = true,
): TrialBalance {
  return {
    accounts: accounts.map((a) => ({
      ledgerAccountCode: a.code,
      debitTotal: money(a.d, a.dc ?? 'KRW'),
      creditTotal: money(a.c, a.cc ?? a.dc ?? 'KRW'),
      baseDebitTotal: money(a.d, 'KRW'),
      baseCreditTotal: money(a.c, 'KRW'),
    })),
    grandDebitTotal: money('0', 'KRW'),
    grandCreditTotal: money('0', 'KRW'),
    grandBaseDebitTotal: money('0', 'KRW'),
    grandBaseCreditTotal: money('0', 'KRW'),
    inBalance,
  };
}

describe('trialBalanceBars — currency grouping', () => {
  it('same-currency accounts share one scale (largest debit-or-credit)', () => {
    const pairs = trialBalanceBars(
      tb([
        { code: 'CASH', d: '500000', c: '0' },
        { code: 'REVENUE', d: '0', c: '1000000' },
      ]).accounts,
    );
    expect(pairs.map((p) => p?.max)).toEqual([1000000, 1000000]);
  });

  it('different currencies never share a scale', () => {
    const pairs = trialBalanceBars(
      tb([
        { code: 'CASH', d: '500000', c: '0', dc: 'KRW' },
        { code: 'FX_USD', d: '10000', c: '0', dc: 'USD' },
      ]).accounts,
    );
    expect(pairs.map((p) => p?.max)).toEqual([500000, 10000]);
    expect(pairs.map((p) => p?.currency)).toEqual(['KRW', 'USD']);
  });

  it('debit / credit currency mismatch → no bars for that account only', () => {
    const pairs = trialBalanceBars(
      tb([
        { code: 'A', d: '10', c: '20', dc: 'KRW', cc: 'USD' },
        { code: 'B', d: '10', c: '20', dc: 'KRW' },
      ]).accounts,
    );
    expect(pairs[0]).toBeNull();
    expect(pairs[1]?.max).toBe(20);
  });

  it('negative / unparsable → 0; an all-zero group has no bars', () => {
    const pairs = trialBalanceBars(
      tb([
        { code: 'A', d: '-10', c: 'abc' },
        { code: 'B', d: '0', c: '0', dc: 'USD' },
      ]).accounts,
    );
    expect(pairs).toEqual([null, null]);
  });
});

describe('TrialBalanceTable — bars', () => {
  it('a debit / credit pair per account; balanced → same length; text unchanged', () => {
    render(
      <TrialBalanceTable
        trialBalance={tb([
          { code: 'CASH', d: '500000', c: '0' },
          { code: 'REVENUE', d: '0', c: '500000' },
          { code: 'EQUITY', d: '250000', c: '250000' },
        ])}
      />,
    );
    expect(barPct('ledger-tb-bar-debit-0')).toBe(100);
    expect(barPct('ledger-tb-bar-credit-0')).toBe(0);
    expect(barPct('ledger-tb-bar-credit-1')).toBe(100);
    expect(barPct('ledger-tb-bar-debit-2')).toBe(50);
    expect(barPct('ledger-tb-bar-credit-2')).toBe(50);
    expect(screen.getByTestId('ledger-tb-bar-0').children.length).toBe(2);
    expect(screen.getByTestId('ledger-tb-debit-0').textContent).toBe(
      formatMoney(money('500000', 'KRW')),
    );
  });

  it('a single account fills to 100%', () => {
    render(<TrialBalanceTable trialBalance={tb([{ code: 'CASH', d: '70', c: '70' }])} />);
    expect(barPct('ledger-tb-bar-debit-0')).toBe(100);
    expect(barPct('ledger-tb-bar-credit-0')).toBe(100);
  });

  it('the in-balance verdict stays the producer boolean, never the bar widths', () => {
    render(
      <TrialBalanceTable
        trialBalance={tb([{ code: 'CASH', d: '100', c: '40' }], true)}
      />,
    );
    expect(screen.getByTestId('ledger-tb-inbalance').textContent).toContain('대차 일치');
    expect(barPct('ledger-tb-bar-debit-0')).toBe(100);
    expect(barPct('ledger-tb-bar-credit-0')).toBe(40);
  });

  it('scope note names the currency grouping only when more than one currency is drawn', () => {
    const { unmount } = render(
      <TrialBalanceTable trialBalance={tb([{ code: 'A', d: '1', c: '1' }])} />,
    );
    expect(screen.getByTestId('ledger-tb-bar-scope').textContent).toBe(
      '막대는 이 시산표에서 가장 큰 차변 · 대변 합계 대비 길이입니다.',
    );
    unmount();
    render(
      <TrialBalanceTable
        trialBalance={tb([
          { code: 'A', d: '1', c: '1' },
          { code: 'B', d: '1', c: '1', dc: 'USD' },
        ])}
      />,
    );
    expect(screen.getByTestId('ledger-tb-bar-scope').textContent).toBe(
      '막대는 같은 통화 계정끼리, 그 통화에서 가장 큰 차변 · 대변 합계 대비 길이입니다.',
    );
  });

  it('an empty trial balance keeps its empty note and no bars', () => {
    render(<TrialBalanceTable trialBalance={tb([])} />);
    expect(screen.getByTestId('ledger-tb-empty')).toBeDefined();
    expect(screen.queryByTestId('ledger-tb-bar-scope')).toBeNull();
  });
});

describe('approvalStepProgress — contract: currentStage is 0-based, absent once finalized', () => {
  it('in-flight rows: n/N with the 0-based index shifted for the text', () => {
    const p = approvalStepProgress({ status: 'IN_REVIEW', currentStage: 1, totalStages: 3 })!;
    expect(p.states).toEqual(['done', 'current', 'todo']);
    expect(p.text).toBe('2/3 단계');
    expect(
      approvalStepProgress({ status: 'SUBMITTED', currentStage: 0, totalStages: 2 })!.text,
    ).toBe('1/2 단계');
  });

  it('boundary: currentStage === totalStages (a 1-based reading) draws nothing', () => {
    expect(
      approvalStepProgress({ status: 'IN_REVIEW', currentStage: 3, totalStages: 3 }),
    ).toBeNull();
  });

  it('APPROVED → all done, no text (stage index is absent once finalized)', () => {
    const p = approvalStepProgress({ status: 'APPROVED', totalStages: 2 })!;
    expect(p.states).toEqual(['done', 'done']);
    expect(p.text).toBeNull();
  });

  it('REJECTED / WITHDRAWN / DRAFT / unknown → no position invented', () => {
    for (const status of ['REJECTED', 'WITHDRAWN', 'DRAFT', 'FUTURE_STATE']) {
      expect(approvalStepProgress({ status, currentStage: 0, totalStages: 2 })).toBeNull();
    }
  });

  it('totalStages 0 / missing → nothing', () => {
    expect(approvalStepProgress({ status: 'SUBMITTED', currentStage: 0, totalStages: 0 })).toBeNull();
    expect(approvalStepProgress({ status: 'SUBMITTED', currentStage: 0 })).toBeNull();
    expect(approvalStepProgress({ status: 'APPROVED' })).toBeNull();
    expect(approvalStepProgress({ status: 'SUBMITTED', totalStages: 2 })).toBeNull();
  });
});

describe('ApprovalStepProgressView', () => {
  it('renders an aria-hidden bar plus the position text', () => {
    render(
      <ApprovalStepProgressView
        row={{ status: 'IN_REVIEW', currentStage: 1, totalStages: 2 }}
        index={4}
      />,
    );
    expect(screen.getByTestId('approval-steps-bar-4').getAttribute('aria-hidden')).toBe('true');
    expect(screen.getByTestId('approval-steps-text-4').textContent).toBe('2/2 단계');
  });

  it('renders nothing for a row without an honest position', () => {
    const { container } = render(
      <ApprovalStepProgressView row={{ status: 'REJECTED', totalStages: 2 }} index={0} />,
    );
    expect(container.firstChild).toBeNull();
  });
});

describe('outboundProgress — order is taken from order-status.md', () => {
  it('the normal flow is the contract order', () => {
    expect([...OUTBOUND_FLOW]).toEqual(['PICKING', 'PICKED', 'PACKING', 'PACKED', 'SHIPPED']);
    expect([...OUTBOUND_SIDE_BRANCHES]).toEqual(['CANCELLED', 'BACKORDERED']);
  });

  it('every flow status marks its own position', () => {
    OUTBOUND_FLOW.forEach((s, i) => {
      const p = outboundProgress(s);
      expect(p?.kind).toBe('flow');
      if (p?.kind === 'flow') {
        expect(p.states).toHaveLength(OUTBOUND_FLOW.length);
        if (s === 'SHIPPED') expect(p.states.every((x) => x === 'done')).toBe(true);
        else expect(p.states[i]).toBe('current');
      }
    });
  });

  it('side branches get no steps', () => {
    expect(outboundProgress('CANCELLED')).toEqual({ kind: 'side', status: 'CANCELLED' });
    expect(outboundProgress('BACKORDERED')).toEqual({ kind: 'side', status: 'BACKORDERED' });
  });

  it('unknown / transient RECEIVED / absent → null', () => {
    expect(outboundProgress('RECEIVED')).toBeNull();
    expect(outboundProgress('FUTURE')).toBeNull();
    expect(outboundProgress(undefined)).toBeNull();
    expect(outboundProgress('')).toBeNull();
  });
});

describe('OutboundDrillSummary — progress', () => {
  const detail = (status: string): OutboundOrderDetail =>
    ({
      orderId: 'o-1',
      orderNo: 'ORD-1',
      status,
      sagaState: 'RESERVED',
      lines: [],
      version: 3,
    }) as OutboundOrderDetail;
  const saga = { sagaId: 's', orderId: 'o-1', state: 'RESERVED', version: 1 } as unknown as OutboundSaga;

  it('flow status: aria-hidden step bar, existing fields kept', () => {
    render(<OutboundDrillSummary detail={detail('PACKING')} saga={saga} />);
    const bar = screen.getByTestId('outbound-drill-progress-bar');
    expect(bar.getAttribute('aria-hidden')).toBe('true');
    expect(Array.from(bar.children).map((c) => (c as HTMLElement).dataset.step)).toEqual([
      'done',
      'done',
      'current',
      'todo',
      'todo',
    ]);
    expect(screen.getByTestId('outbound-drill-status').textContent).toBe('PACKING');
    expect(screen.getByTestId('outbound-drill-saga').textContent).toBe('RESERVED');
    expect(screen.getByTestId('outbound-drill-orderno').textContent).toBe('ORD-1');
  });

  it('side branch: label, no step bar', () => {
    render(<OutboundDrillSummary detail={detail('CANCELLED')} saga={saga} />);
    expect(screen.queryByTestId('outbound-drill-progress-bar')).toBeNull();
    expect(screen.getByTestId('outbound-drill-progress-side').textContent).toBe(
      '정상 흐름 밖 상태: CANCELLED',
    );
  });

  it('unknown status: badge text only', () => {
    render(<OutboundDrillSummary detail={detail('SOMETHING_NEW')} saga={saga} />);
    expect(screen.queryByTestId('outbound-drill-progress')).toBeNull();
    expect(screen.queryByTestId('outbound-drill-progress-side')).toBeNull();
    expect(screen.getByTestId('outbound-drill-status').textContent).toBe('SOMETHING_NEW');
  });
});

describe('F5 — the new helpers add no coercion call of their own', () => {
  const SRC = path.resolve(__dirname, '../../src');
  const files = [
    'features/finance-ops/lib/balance-bars.ts',
    'features/finance-ops/components/BalancesTable.tsx',
    'features/ledger-ops/lib/trial-balance-bars.ts',
    'features/ledger-ops/components/TrialBalanceTable.tsx',
  ];
  it.each(files)('%s has no Number( / parseFloat( / parseInt(', (rel) => {
    const text = readFileSync(path.join(SRC, rel), 'utf8');
    const code = text
      .split(/\r?\n/)
      .filter((l) => !/^\s*(\*|\/\/|\/\*)/.test(l))
      .join('\n');
    expect(code).not.toMatch(/\b(?:Number|parseFloat|parseInt)\s*\(/);
  });

  it('the bar helpers take their magnitude from chartMagnitude', () => {
    for (const rel of [
      'features/finance-ops/lib/balance-bars.ts',
      'features/ledger-ops/lib/trial-balance-bars.ts',
    ]) {
      expect(readFileSync(path.join(SRC, rel), 'utf8')).toContain(
        "from '@/shared/lib/chart-scale'",
      );
    }
  });
});
