import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AccountEmailLookup } from '@/shared/ui/AccountEmailLookup';

/**
 * TASK-PC-FE-334 — the shared "이메일로 계정 찾기" ASSIST used by the audit
 * filter and artist-registration screens. Queries the same-origin
 * `GET /api/accounts?email=` proxy (mocked via global `fetch`).
 */

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function accountsPage(
  rows: { id: string; email: string; status?: string; tenantId?: string }[],
) {
  return {
    content: rows.map((r) => ({
      id: r.id,
      email: r.email,
      status: r.status ?? 'ACTIVE',
      createdAt: '2026-01-01T00:00:00Z',
      ...(r.tenantId ? { tenantId: r.tenantId } : {}),
    })),
    totalElements: rows.length,
    page: 0,
    size: 20,
    totalPages: 1,
  };
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('AccountEmailLookup — AC-4 pick fills', () => {
  it('a single-row result renders a pick button that calls onPick with the account id', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(accountsPage([{ id: 'acc-1', email: 'a@x.co' }])));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    const onPick = vi.fn();
    render(<AccountEmailLookup id="lookup" testid="lookup" onPick={onPick} />);

    await user.type(screen.getByTestId('lookup-email-input'), 'a@x.co');
    await user.click(screen.getByTestId('lookup-search'));

    const result = await screen.findByTestId('lookup-result-acc-1');
    expect(result).toHaveTextContent('a@x.co');
    await user.click(result);
    expect(onPick).toHaveBeenCalledWith('acc-1', expect.objectContaining({ id: 'acc-1' }));
  });

  it('shows a tenant per row when the producer sends one (SUPER_ADMIN `*` multi-row edge case)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(
        accountsPage([
          { id: 'acc-1', email: 'a@x.co', tenantId: 'tenant-a' },
          { id: 'acc-2', email: 'a@x.co', tenantId: 'tenant-b' },
        ]),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<AccountEmailLookup id="lookup" testid="lookup" onPick={vi.fn()} />);

    await user.type(screen.getByTestId('lookup-email-input'), 'a@x.co');
    await user.click(screen.getByTestId('lookup-search'));

    const row1 = await screen.findByTestId('lookup-result-acc-1');
    const row2 = await screen.findByTestId('lookup-result-acc-2');
    expect(row1).toHaveTextContent('tenant-a');
    expect(row2).toHaveTextContent('tenant-b');
  });
});

describe('AccountEmailLookup — 0 rows vs failure (AC-4)', () => {
  it('0 rows shows the "없습니다" notice (not an error)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(accountsPage([])));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<AccountEmailLookup id="lookup" testid="lookup" onPick={vi.fn()} />);

    await user.type(screen.getByTestId('lookup-email-input'), 'nobody@x.co');
    await user.click(screen.getByTestId('lookup-search'));

    await waitFor(() => expect(screen.getByTestId('lookup-empty')).toBeInTheDocument());
    expect(screen.getByTestId('lookup-empty')).toHaveTextContent(
      '현재 테넌트에 이 이메일의 계정이 없습니다',
    );
    expect(screen.queryByTestId('lookup-error')).not.toBeInTheDocument();
  });

  it('a 403 renders a role="alert" with the TENANT_SCOPE_DENIED copy', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'TENANT_SCOPE_DENIED', message: 'x' }), {
          status: 403,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    const user = userEvent.setup();
    render(<AccountEmailLookup id="lookup" testid="lookup" onPick={vi.fn()} />);

    await user.type(screen.getByTestId('lookup-email-input'), 'a@x.co');
    await user.click(screen.getByTestId('lookup-search'));

    const error = await screen.findByTestId('lookup-error');
    expect(error).toHaveAttribute('role', 'alert');
    expect(error).toHaveTextContent('이 테넌트의 계정을 조회할 권한이 없습니다');
  });

  it('a 503/other failure renders a generic role="alert"', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'DOWNSTREAM_ERROR', message: 'x' }), {
          status: 503,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    const user = userEvent.setup();
    render(<AccountEmailLookup id="lookup" testid="lookup" onPick={vi.fn()} />);

    await user.type(screen.getByTestId('lookup-email-input'), 'a@x.co');
    await user.click(screen.getByTestId('lookup-search'));

    const error = await screen.findByTestId('lookup-error');
    expect(error).toHaveAttribute('role', 'alert');
  });
});
