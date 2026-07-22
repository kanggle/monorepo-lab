import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import {
  NotificationsScreen,
  type NotificationTemplateList,
} from '@/features/ecommerce-ops';

/**
 * TASK-PC-FE-249 — render regression guard for the
 * `/ecommerce/notifications/templates` screen.
 *
 * The notifications facet already had state / api / proxy / nav unit tests, but
 * the screen component was never mounted — a crash-on-mount would have shipped
 * green. This mounts the REAL screen with a seeded (page-0) template list and
 * asserts its primary rendered structure (heading + register link + table +
 * row), plus the empty-state branch.
 *
 * NotificationsScreen reads via react-query (`useNotificationTemplates`), so it
 * needs a QueryClientProvider. `fetch` is stubbed to return the seed so any
 * background refetch resolves cleanly (an errored refetch would flip the
 * section to its degraded state and defeat the render assertion).
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

const LIST: NotificationTemplateList = {
  content: [
    {
      templateId: 't-1',
      type: 'ORDER_PLACED',
      channel: 'EMAIL',
      subject: '주문이 접수되었습니다',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-02T00:00:00Z',
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

describe('NotificationsScreen — mount render', () => {
  it('renders the heading + register link + template table with the seeded row', async () => {
    render(<NotificationsScreen templates={LIST} />, { wrapper: wrapper() });

    expect(
      screen.getByRole('heading', {
        level: 1,
        name: 'E-Commerce 알림 템플릿',
      }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('notification-new-link')).toHaveAttribute(
      'href',
      '/ecommerce/notifications/templates/new',
    );
    expect(await screen.findByTestId('notification-table')).toBeInTheDocument();
    expect(screen.getByTestId('notification-row-0')).toBeInTheDocument();
    expect(screen.getByText('주문이 접수되었습니다')).toBeInTheDocument();
  });

  it('renders the empty-state when the list is empty', async () => {
    const empty: NotificationTemplateList = {
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
    };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(empty)));
    render(<NotificationsScreen templates={empty} />, { wrapper: wrapper() });

    expect(await screen.findByTestId('notification-empty')).toBeInTheDocument();
  });
});
