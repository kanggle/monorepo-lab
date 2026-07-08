import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

/**
 * TASK-PC-FE-052 — `<NotificationBell>` component assertions:
 *   - unread badge shows count when items are unread (AC-1);
 *   - badge hidden when all items read / no notifications / zero count;
 *   - dropdown opens on bell click; lists notifications with type/title +
 *     unread dot visible on unread rows;
 *   - clicking a row triggers mark-read mutation + router.push('/erp/approval?request=<sourceId>')
 *     for APPROVAL source notifications (AC-2; PC-FE-230 — the real 결재함
 *     route, NOT the masters `/erp` slice);
 *   - a §1 `deepLink`, when present, is preferred over the approval fallback;
 *   - non-APPROVAL source type: no router push (generic label only);
 *   - empty state shows "새 알림이 없습니다" (AC-1);
 *   - DEGRADE: when inbox query errors (403/503/network), the bell renders
 *     without crashing — no badge, quiet unavailable message, console intact
 *     (AC-4);
 *   - outside-click closes the dropdown;
 *   - Escape closes the dropdown.
 *
 * Same-origin `/api/console/notifications` (console-bff aggregator) fetch
 * mocked. QueryClientProvider wraps each test (mirrors ApprovalScreen.test.tsx).
 */

const mockPush = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  usePathname: () => '/dashboards',
  useSearchParams: () => new URLSearchParams(),
}));

import { NotificationBell } from '@/features/notifications';

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
function errorResponse(code: string, status: number) {
  return new Response(JSON.stringify({ code, message: 'e' }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const UNREAD_NOTIFICATION = {
  id: 'ntf-1',
  sourceDomain: 'erp',
  type: 'APPROVAL_SUBMITTED',
  title: '결재 상신 통지',
  body: '상신됨',
  sourceType: 'APPROVAL',
  sourceId: 'appr-1',
  read: false,
  createdAt: '2026-06-05T00:00:00Z',
  // readAt ABSENT (NON_NULL)
};

const READ_NOTIFICATION = {
  id: 'ntf-2',
  sourceDomain: 'erp',
  type: 'APPROVAL_APPROVED',
  title: '결재 승인 통지',
  body: '승인됨',
  sourceType: 'APPROVAL',
  sourceId: 'appr-2',
  read: true,
  createdAt: '2026-06-05T01:00:00Z',
  readAt: '2026-06-05T02:00:00Z',
};

const UNKNOWN_SOURCE_NOTIFICATION = {
  id: 'ntf-3',
  sourceDomain: 'erp',
  type: 'FUTURE_EVENT',
  title: '미래 알림',
  body: '...',
  sourceType: 'MASTERDATA',
  sourceId: 'some-id',
  read: false,
  createdAt: '2026-06-05T03:00:00Z',
};

// The console-bff aggregator response shape (P3b): { items, meta, degradedDomains }.
const LIST_WITH_UNREAD = {
  items: [UNREAD_NOTIFICATION, READ_NOTIFICATION],
  meta: { page: 0, size: 20, totalElements: 2, timestamp: 'x' },
  degradedDomains: [],
};

const LIST_ALL_READ = {
  items: [READ_NOTIFICATION],
  meta: { page: 0, size: 20, totalElements: 1, timestamp: 'x' },
  degradedDomains: [],
};

const EMPTY_LIST = {
  items: [],
  meta: { page: 0, size: 20, totalElements: 0, timestamp: 'x' },
  degradedDomains: [],
};

// Mark-read returns the owning domain's `{ data, meta }` envelope (passed
// through by the aggregator + proxy).
const MARK_READ_RESPONSE = {
  data: { ...UNREAD_NOTIFICATION, read: true, readAt: '2026-06-05T10:00:00Z' },
  meta: { timestamp: 'x' },
};

beforeEach(() => {
  vi.unstubAllGlobals();
  mockPush.mockClear();
});

// ===========================================================================
// badge visibility.
// ===========================================================================

describe('NotificationBell — unread badge', () => {
  it('shows unread badge with count PASSIVELY (no click needed — on-mount fetch)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(LIST_WITH_UNREAD)));
    render(<NotificationBell />, { wrapper: wrapper() });

    // Badge appears from the on-mount fetch WITHOUT opening the dropdown
    // (the bell's whole purpose — a passive unread indicator).
    await waitFor(() =>
      expect(screen.getByTestId('notification-badge')).toBeInTheDocument(),
    );
    // 1 unread item in LIST_WITH_UNREAD.
    expect(screen.getByTestId('notification-badge').textContent).toBe('1');
    // The dropdown was never opened.
    expect(screen.queryByTestId('notification-dropdown')).not.toBeInTheDocument();
  });

  it('badge hidden when all items are read (unread count === 0)', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(LIST_ALL_READ)));
    render(<NotificationBell />, { wrapper: wrapper() });
    await user.click(screen.getByTestId('notification-bell-trigger'));
    await waitFor(() =>
      expect(screen.getByTestId('notification-dropdown')).toBeInTheDocument(),
    );
    expect(screen.queryByTestId('notification-badge')).not.toBeInTheDocument();
  });

  it('badge hidden when list is empty', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(EMPTY_LIST)));
    render(<NotificationBell />, { wrapper: wrapper() });
    await user.click(screen.getByTestId('notification-bell-trigger'));
    await waitFor(() =>
      expect(screen.getByTestId('notification-empty')).toBeInTheDocument(),
    );
    expect(screen.queryByTestId('notification-badge')).not.toBeInTheDocument();
  });
});

// ===========================================================================
// dropdown opens and lists notifications.
// ===========================================================================

describe('NotificationBell — dropdown', () => {
  it('opens on bell click; lists notifications with title', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(LIST_WITH_UNREAD)));
    render(<NotificationBell />, { wrapper: wrapper() });

    // Dropdown not visible before click.
    expect(screen.queryByTestId('notification-dropdown')).not.toBeInTheDocument();

    await user.click(screen.getByTestId('notification-bell-trigger'));
    await waitFor(() =>
      expect(screen.getByTestId('notification-dropdown')).toBeInTheDocument(),
    );
    // Both notifications appear.
    expect(screen.getByTestId('notification-row-ntf-1')).toBeInTheDocument();
    expect(screen.getByTestId('notification-row-ntf-2')).toBeInTheDocument();
    // Title text is rendered.
    expect(screen.getByText('결재 상신 통지')).toBeInTheDocument();
    expect(screen.getByText('결재 승인 통지')).toBeInTheDocument();
  });

  it('empty state shows "새 알림이 없습니다"', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(EMPTY_LIST)));
    render(<NotificationBell />, { wrapper: wrapper() });
    await user.click(screen.getByTestId('notification-bell-trigger'));
    await waitFor(() =>
      expect(screen.getByTestId('notification-empty')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('notification-empty').textContent).toContain(
      '새 알림이 없습니다',
    );
  });
});

// ===========================================================================
// row click — mark-read + deep-link.
// ===========================================================================

describe('NotificationBell — row click (mark-read + deep-link)', () => {
  it('clicking an APPROVAL row triggers mark-read then router.push with sourceId', async () => {
    const fetchMock = vi.fn((url: string) => {
      if (String(url).includes('/read')) {
        return Promise.resolve(jsonResponse(MARK_READ_RESPONSE));
      }
      return Promise.resolve(jsonResponse(LIST_WITH_UNREAD));
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<NotificationBell />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('notification-bell-trigger'));
    await waitFor(() =>
      expect(screen.getByTestId('notification-row-ntf-1')).toBeInTheDocument(),
    );
    await user.click(screen.getByTestId('notification-row-ntf-1'));

    // mark-read was called.
    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some((c) =>
          String(c[0]).includes('/api/console/notifications/erp/ntf-1/read'),
        ),
      ).toBe(true),
    );
    // router.push was called with the approval deep-link (real 결재함 route).
    expect(mockPush).toHaveBeenCalledWith('/erp/approval?request=appr-1');
    // Dropdown is closed.
    await waitFor(() =>
      expect(screen.queryByTestId('notification-dropdown')).not.toBeInTheDocument(),
    );
  });

  it('clicking a non-APPROVAL source row: mark-read fires but NO router.push', async () => {
    const listWithUnknown = {
      items: [UNKNOWN_SOURCE_NOTIFICATION],
      meta: { page: 0, size: 20, totalElements: 1, timestamp: 'x' },
      degradedDomains: [],
    };
    const readResponse = {
      data: { ...UNKNOWN_SOURCE_NOTIFICATION, read: true, readAt: '2026-06-05T10:00:00Z' },
      meta: { timestamp: 'x' },
    };
    const fetchMock = vi.fn((url: string) => {
      if (String(url).includes('/read')) {
        return Promise.resolve(jsonResponse(readResponse));
      }
      return Promise.resolve(jsonResponse(listWithUnknown));
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<NotificationBell />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('notification-bell-trigger'));
    await waitFor(() =>
      expect(screen.getByTestId('notification-row-ntf-3')).toBeInTheDocument(),
    );
    await user.click(screen.getByTestId('notification-row-ntf-3'));

    // mark-read should be fired.
    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some((c) =>
          String(c[0]).includes('/api/console/notifications/erp/ntf-3/read'),
        ),
      ).toBe(true),
    );
    // NO router.push for non-APPROVAL source.
    expect(mockPush).not.toHaveBeenCalled();
  });

  it('mark-read failure does NOT block navigation (graceful failure)', async () => {
    const fetchMock = vi.fn((url: string) => {
      if (String(url).includes('/read')) {
        return Promise.resolve(errorResponse('NOTIFICATION_NOT_FOUND', 404));
      }
      return Promise.resolve(jsonResponse(LIST_WITH_UNREAD));
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<NotificationBell />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('notification-bell-trigger'));
    await waitFor(() =>
      expect(screen.getByTestId('notification-row-ntf-1')).toBeInTheDocument(),
    );
    // Click the APPROVAL notification row — mark-read will fail (404), but
    // navigation should still fire.
    await user.click(screen.getByTestId('notification-row-ntf-1'));

    // router.push still fired despite mark-read failure.
    expect(mockPush).toHaveBeenCalledWith('/erp/approval?request=appr-1');
  });

  it('prefers the §1 deepLink over the approval fallback when present', async () => {
    const withDeepLink = {
      ...UNREAD_NOTIFICATION,
      deepLink: '/erp/approval?request=appr-canonical',
    };
    const listWithDeepLink = {
      items: [withDeepLink],
      meta: { page: 0, size: 20, totalElements: 1, timestamp: 'x' },
      degradedDomains: [],
    };
    const fetchMock = vi.fn((url: string) => {
      if (String(url).includes('/read')) {
        return Promise.resolve(jsonResponse(MARK_READ_RESPONSE));
      }
      return Promise.resolve(jsonResponse(listWithDeepLink));
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<NotificationBell />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('notification-bell-trigger'));
    await waitFor(() =>
      expect(screen.getByTestId('notification-row-ntf-1')).toBeInTheDocument(),
    );
    await user.click(screen.getByTestId('notification-row-ntf-1'));

    // deepLink wins over the sourceId-derived approval fallback.
    expect(mockPush).toHaveBeenCalledWith('/erp/approval?request=appr-canonical');
  });
});

// ===========================================================================
// degrade graceful — AC-4.
// ===========================================================================

describe('NotificationBell — degrade graceful (403/503/error)', () => {
  it('403 inbox error: bell renders without crash; no badge; quiet unavailable message on open', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(errorResponse('TENANT_FORBIDDEN', 403)),
    );
    const user = userEvent.setup();
    render(<NotificationBell />, { wrapper: wrapper() });

    // Bell trigger should render (no crash before open).
    expect(screen.getByTestId('notification-bell-trigger')).toBeInTheDocument();

    // Open the dropdown — fetch fires, gets 403.
    await user.click(screen.getByTestId('notification-bell-trigger'));
    await waitFor(() =>
      expect(screen.getByTestId('notification-dropdown')).toBeInTheDocument(),
    );

    // Quiet unavailable message instead of error boundary.
    expect(screen.getByTestId('notification-unavailable')).toBeInTheDocument();
    expect(screen.getByTestId('notification-unavailable').textContent).toContain(
      '알림을 불러올 수 없습니다',
    );
    // No badge.
    expect(screen.queryByTestId('notification-badge')).not.toBeInTheDocument();
  });

  it('503 inbox error: bell degrades gracefully (no crash, shell intact)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(errorResponse('SERVICE_UNAVAILABLE', 503)),
    );
    const user = userEvent.setup();
    render(<NotificationBell />, { wrapper: wrapper() });
    expect(screen.getByTestId('notification-bell-trigger')).toBeInTheDocument();
    await user.click(screen.getByTestId('notification-bell-trigger'));
    await waitFor(() =>
      expect(screen.getByTestId('notification-dropdown')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('notification-unavailable')).toBeInTheDocument();
    expect(screen.queryByTestId('notification-badge')).not.toBeInTheDocument();
  });

  it('network error: bell degrades gracefully', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockRejectedValue(new TypeError('Network error')),
    );
    const user = userEvent.setup();
    render(<NotificationBell />, { wrapper: wrapper() });
    expect(screen.getByTestId('notification-bell-trigger')).toBeInTheDocument();
    await user.click(screen.getByTestId('notification-bell-trigger'));
    await waitFor(() =>
      expect(screen.getByTestId('notification-dropdown')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('notification-unavailable')).toBeInTheDocument();
  });
});

// ===========================================================================
// outside-click + Escape close.
// ===========================================================================

describe('NotificationBell — outside-click + Escape close', () => {
  it('outside-click closes the dropdown', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(EMPTY_LIST)));
    const user = userEvent.setup();
    render(
      <div>
        <NotificationBell />
        <button data-testid="outside-button">Outside</button>
      </div>,
      { wrapper: wrapper() },
    );
    await user.click(screen.getByTestId('notification-bell-trigger'));
    await waitFor(() =>
      expect(screen.getByTestId('notification-dropdown')).toBeInTheDocument(),
    );
    await user.click(screen.getByTestId('outside-button'));
    await waitFor(() =>
      expect(screen.queryByTestId('notification-dropdown')).not.toBeInTheDocument(),
    );
  });

  it('Escape closes the dropdown', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(EMPTY_LIST)));
    const user = userEvent.setup();
    render(<NotificationBell />, { wrapper: wrapper() });
    await user.click(screen.getByTestId('notification-bell-trigger'));
    await waitFor(() =>
      expect(screen.getByTestId('notification-dropdown')).toBeInTheDocument(),
    );
    await user.keyboard('{Escape}');
    await waitFor(() =>
      expect(screen.queryByTestId('notification-dropdown')).not.toBeInTheDocument(),
    );
  });
});
