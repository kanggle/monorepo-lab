import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { CouponIssueDialog } from '@/features/ecommerce-ops/components/CouponIssueDialog';

/**
 * TASK-PC-FE-334 AC-5 — «이메일로 찾아 추가»: a partial-email search against
 * `/api/ecommerce/users` appends the picked row's userId to the existing
 * textarea list (de-duped), and the issue request body carries it.
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

function ok(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function usersPage(rows: { userId: string; email: string | null }[]) {
  return {
    content: rows.map((r) => ({
      userId: r.userId,
      email: r.email,
      status: 'ACTIVE',
      createdAt: '2026-01-01T00:00:00Z',
    })),
    page: 0,
    size: 20,
    totalElements: rows.length,
  };
}

function renderDialog() {
  return render(
    <CouponIssueDialog open promotionId="promo-1" onClose={vi.fn()} onIssued={vi.fn()} />,
    { wrapper: wrapper() },
  );
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('CouponIssueDialog — email search assist (AC-5)', () => {
  it('a found user appended via 추가 reaches the issue request body', async () => {
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (String(url).includes('/api/ecommerce/users')) {
        return Promise.resolve(
          ok(usersPage([{ userId: 'user-1', email: 'fan@x.co' }])),
        );
      }
      return Promise.resolve(ok({ issuedCount: 1 }, 201));
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderDialog();

    await user.type(screen.getByTestId('coupon-issue-email-search-input'), 'fan');
    await user.click(screen.getByTestId('coupon-issue-email-search'));
    await user.click(await screen.findByTestId('coupon-issue-email-search-add-user-1'));

    expect(screen.getByTestId('coupon-issue-userids')).toHaveValue('user-1');

    await user.click(screen.getByTestId('ecommerce-confirm-confirm'));

    await waitFor(() => {
      const postCall = fetchMock.mock.calls.find(
        (c) => (c[1] as RequestInit | undefined)?.method === 'POST',
      );
      expect(postCall).toBeTruthy();
      const body = JSON.parse((postCall![1] as RequestInit).body as string);
      expect(body.userIds).toEqual(['user-1']);
    });
  });

  it('adding the SAME user twice de-dupes (only one entry)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      ok(usersPage([{ userId: 'user-2', email: 'dup@x.co' }])),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderDialog();

    await user.type(screen.getByTestId('coupon-issue-email-search-input'), 'dup');
    await user.click(screen.getByTestId('coupon-issue-email-search'));
    const addButton = await screen.findByTestId('coupon-issue-email-search-add-user-2');
    await user.click(addButton);
    await user.click(addButton);

    expect(screen.getByTestId('coupon-issue-userids')).toHaveValue('user-2');
  });

  it('a user row with a null email displays the id instead', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      ok(usersPage([{ userId: 'user-3', email: null }])),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderDialog();

    await user.type(screen.getByTestId('coupon-issue-email-search-input'), 'x');
    await user.click(screen.getByTestId('coupon-issue-email-search'));

    const row = await screen.findByTestId('coupon-issue-email-search-add-user-3');
    expect(row.closest('li')).toHaveTextContent('user-3');
  });

  it('a search failure shows an error only in the search area (list input unaffected)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'SERVICE_UNAVAILABLE', message: 'x' }), {
          status: 503,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    const user = userEvent.setup();
    renderDialog();

    await user.type(screen.getByTestId('coupon-issue-email-search-input'), 'x');
    await user.click(screen.getByTestId('coupon-issue-email-search'));

    await screen.findByTestId('coupon-issue-email-search-error');
    expect(screen.getByTestId('coupon-issue-userids')).toHaveValue('');
  });
});
