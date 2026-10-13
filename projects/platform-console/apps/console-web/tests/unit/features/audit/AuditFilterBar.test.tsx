import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { AuditFilterBar } from '@/features/audit/components/AuditFilterBar';
import { EMPTY_FILTERS, type FilterState } from '@/features/audit/hooks/use-audit-screen';

/**
 * TASK-PC-FE-334 AC-4 — the 계정 ID input stays; the new email-lookup ASSIST
 * (shared `<AccountEmailLookup>`) fills `filters.accountId` the same way
 * typing the id directly would.
 */

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function Harness() {
  const [filters, setFilters] = useState<FilterState>(EMPTY_FILTERS);
  return (
    <AuditFilterBar
      accountFid="account-fid"
      actionFid="action-fid"
      fromFid="from-fid"
      toFid="to-fid"
      sourceFid="source-fid"
      tenantFid="tenant-fid"
      filters={filters}
      setFilters={setFilters}
      submitFilters={(e) => e.preventDefault()}
      resetFilters={() => setFilters(EMPTY_FILTERS)}
      securityKnownDenied={false}
      superAdminTenants={[]}
    />
  );
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('AuditFilterBar — email lookup fills filters.accountId (AC-4)', () => {
  it('picking a found account fills the existing 계정 ID input', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          content: [{ id: 'acc-1', email: 'a@x.co', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' }],
          totalElements: 1,
          page: 0,
          size: 20,
          totalPages: 1,
        }),
      ),
    );
    const user = userEvent.setup();
    render(<Harness />);

    await user.type(screen.getByTestId('audit-filter-account-lookup-email-input'), 'a@x.co');
    await user.click(screen.getByTestId('audit-filter-account-lookup-search'));
    await user.click(await screen.findByTestId('audit-filter-account-lookup-result-acc-1'));

    expect(screen.getByTestId('audit-filter-accountId')).toHaveValue('acc-1');
  });
});
