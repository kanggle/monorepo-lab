import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import type { NotificationSummary, NotificationDetail, PaginatedResponse } from '@repo/types';
import { TestQueryProvider } from './test-utils';

const mockSearchParams = new URLSearchParams();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => mockSearchParams,
  usePathname: () => '/my/notifications',
}));

vi.mock('next/link', () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) => (
    <a href={href} {...props}>{children}</a>
  ),
}));

vi.mock('@/features/notification/api/notification-api');

vi.mock('@repo/ui', () => ({
  LoadingSpinner: () => <div data-testid="loading-spinner">로딩 중...</div>,
  ErrorMessage: ({ message, onRetry }: { message: string; onRetry: () => void }) => (
    <div data-testid="error-message">
      {message}
      <button onClick={onRetry}>재시도</button>
    </div>
  ),
  EmptyState: ({ message }: { message: string }) => (
    <div data-testid="empty-state">{message}</div>
  ),
}));

import { getMyNotifications, getMyNotification } from '@/features/notification/api/notification-api';
import NotificationsPage from '@/app/(store)/my/notifications/page';

const mockGetMyNotifications = vi.mocked(getMyNotifications);
const mockGetMyNotification = vi.mocked(getMyNotification);

const MOCK_NOTIFICATIONS: NotificationSummary[] = [
  {
    notificationId: 'notif-1',
    channel: 'EMAIL',
    subject: '주문이 접수되었습니다',
    status: 'SENT',
    sentAt: '2026-04-01T10:00:00Z',
    createdAt: '2026-04-01T10:00:00Z',
  },
];

const MOCK_DETAIL: NotificationDetail = {
  notificationId: 'notif-1',
  channel: 'EMAIL',
  subject: '주문이 접수되었습니다',
  body: '주문번호 ORDER-001이 접수되었습니다.',
  status: 'SENT',
  sentAt: '2026-04-01T10:00:00Z',
  createdAt: '2026-04-01T10:00:00Z',
};

describe('NotificationsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Reset search params
    mockSearchParams.delete('id');
  });

  it('알림 목록을 기본으로 표시한다', async () => {
    mockGetMyNotifications.mockResolvedValueOnce({
      content: MOCK_NOTIFICATIONS,
      page: 0,
      size: 20,
      totalElements: 1,
    });

    render(<TestQueryProvider><NotificationsPage /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByText('주문이 접수되었습니다')).toBeInTheDocument();
    });
  });

  it('id 파라미터가 있으면 알림 상세를 표시한다', async () => {
    mockSearchParams.set('id', 'notif-1');
    mockGetMyNotification.mockResolvedValueOnce(MOCK_DETAIL);

    render(<TestQueryProvider><NotificationsPage /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByText(/주문번호 ORDER-001/)).toBeInTheDocument();
    });
  });
});
