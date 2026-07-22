import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { UsersScreen, type UserList } from '@/features/ecommerce-ops';

/**
 * TASK-PC-FE-249 — render regression guard for the `/ecommerce/users` screen.
 *
 * The users facet already had state / api / proxy / nav unit tests, but the
 * screen component was never mounted — a crash-on-mount would have shipped
 * green. This mounts the REAL screen with a seeded (page-0, unfiltered) user
 * list and asserts its primary rendered structure (heading + table + a row),
 * plus the empty-state branch.
 *
 * UsersScreen reads via react-query (`useUsers`), so it needs a
 * QueryClientProvider. `fetch` is stubbed to return the seed so any background
 * refetch resolves cleanly (an errored refetch would flip the section to its
 * degraded state and defeat the render assertion).
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const LIST: UserList = {
  content: [
    {
      userId: 'u-1',
      email: 'u1@example.com',
      name: '홍길동',
      nickname: 'gildong',
      status: 'ACTIVE',
      createdAt: '2026-01-01T00:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
};

beforeEach(() => {
  vi.unstubAllGlobals();
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(LIST)));
});

describe('UsersScreen — mount render', () => {
  it('renders the heading + user table with the seeded row', async () => {
    render(<UsersScreen users={LIST} />, { wrapper: wrapper() });

    expect(
      screen.getByRole('heading', { level: 1, name: 'E-Commerce 사용자' }),
    ).toBeInTheDocument();
    expect(await screen.findByTestId('user-table')).toBeInTheDocument();
    expect(screen.getByTestId('user-row-0')).toBeInTheDocument();
    expect(screen.getByText('u1@example.com')).toBeInTheDocument();
  });

  it('renders the empty-state when the list is empty', async () => {
    const empty: UserList = { content: [], page: 0, size: 20, totalElements: 0 };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(empty)));
    render(<UsersScreen users={empty} />, { wrapper: wrapper() });

    expect(await screen.findByTestId('user-empty')).toBeInTheDocument();
  });
});
