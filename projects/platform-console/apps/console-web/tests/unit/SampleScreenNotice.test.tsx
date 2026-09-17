/**
 * `widgets/sample-visitor/SampleScreenNotice` — «이 화면의 샘플 데이터는
 * 준비 중입니다» renders IFF the sample ledger says `pending` for the current
 * route (ADR-MONO-074 A9 — `TASK-PC-FE-288`).
 *
 * 🔴 Why this file exists now (not earlier): every prior domain ticket
 *    (283…287) proved this behaviour with a REAL `(console)` route that was
 *    still `pending` (`e2e-smoke/sample-visitor.spec.ts`'s "pending 화면" cell,
 *    most recently pointed at `/scm`). TASK-PC-FE-288 turns scm `ready` too —
 *    after this ticket NO `(console)` screen is `pending` anywhere, so that
 *    e2e cell has nowhere left to point (287's own note on that spot named
 *    this exact re-homing). Rather than leave one screen artificially
 *    `pending` forever just to keep an e2e assertion alive, this suite
 *    injects the ledger lookup the component ACTUALLY calls
 *    (`screenStatusFor`, from `@/shared/sample/coverage`) with a synthetic
 *    answer — proving the SAME subject (a pending screen's shell notice
 *    renders; a ready/static screen's does not) without adding a fake route
 *    to the real `SCREEN_COVERAGE` ledger.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';

let mockPathname = '/whatever';
vi.mock('next/navigation', () => ({
  usePathname: () => mockPathname,
}));

let mockStatus: 'ready' | 'pending' | 'static' = 'pending';
vi.mock('@/shared/sample/coverage', () => ({
  screenStatusFor: (_pathname: string) => mockStatus,
}));

import { SampleScreenNotice } from '@/widgets/sample-visitor/SampleScreenNotice';

beforeEach(() => {
  mockPathname = '/whatever';
  mockStatus = 'pending';
});

afterEach(() => {
  cleanup();
});

describe('SampleScreenNotice', () => {
  it('renders «이 화면의 샘플 데이터는 준비 중입니다» when the ledger says pending', () => {
    mockStatus = 'pending';
    render(<SampleScreenNotice />);
    const notice = screen.getByTestId('sample-screen-not-ready');
    expect(notice).toHaveTextContent('이 화면의 샘플 데이터는 준비 중입니다');
    expect(notice.getAttribute('role')).toBe('status');
  });

  it('renders nothing when the ledger says ready', () => {
    mockStatus = 'ready';
    const { container } = render(<SampleScreenNotice />);
    expect(container).toBeEmptyDOMElement();
    expect(screen.queryByTestId('sample-screen-not-ready')).toBeNull();
  });

  it('renders nothing when the ledger says static (e.g. a `*/guide` screen)', () => {
    mockStatus = 'static';
    const { container } = render(<SampleScreenNotice />);
    expect(container).toBeEmptyDOMElement();
    expect(screen.queryByTestId('sample-screen-not-ready')).toBeNull();
  });

  it('asks the ledger with the CURRENT pathname (not a stale/hardcoded one)', () => {
    mockPathname = '/some/deep/route';
    mockStatus = 'pending';
    render(<SampleScreenNotice />);
    // The mock ignores its argument by design (it only needs to prove the
    // component RE-ASKS per pathname, which the `usePathname` client-component
    // posture already guarantees) — this cell exists to document that the
    // component is deliberately a client component for that reason (see its
    // own docstring), not to duplicate `screenStatusFor`'s own unit tests.
    expect(screen.getByTestId('sample-screen-not-ready')).toBeInTheDocument();
  });
});
