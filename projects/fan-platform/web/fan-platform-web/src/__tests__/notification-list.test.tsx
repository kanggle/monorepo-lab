import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NotificationList } from '@/features/notification/ui/NotificationList';
import type { Notification } from '@/entities/notification';

const { markNotificationRead, markAllNotificationsRead } = vi.hoisted(() => ({
  markNotificationRead: vi.fn(async () => {}),
  markAllNotificationsRead: vi.fn(async () => {}),
}));
vi.mock('@/features/notification/api/actions', () => ({
  markNotificationRead,
  markAllNotificationsRead,
}));

function note(over: Partial<Notification> = {}): Notification {
  return {
    id: 'n-1',
    type: 'WELCOME',
    title: '멤버십이 시작되었어요',
    body: '프리미엄 멤버십이 활성화되었습니다.',
    status: 'UNREAD',
    read: false,
    createdAt: '2026-06-11T00:00:00Z',
    ...over,
  };
}

describe('NotificationList', () => {
  beforeEach(() => {
    markNotificationRead.mockClear();
    markAllNotificationsRead.mockClear();
  });

  it('renders a row per notification with its type label', () => {
    render(
      <NotificationList
        initial={[
          note({ id: 'a', type: 'WELCOME' }),
          note({ id: 'b', type: 'CANCELLATION', title: '해지되었습니다' }),
        ]}
      />,
    );
    expect(screen.getAllByTestId('notification-row')).toHaveLength(2);
    expect(screen.getByText('멤버십 시작')).toBeInTheDocument();
    expect(screen.getByText('멤버십 해지')).toBeInTheDocument();
  });

  it('marks a single unread row read on click', async () => {
    const user = userEvent.setup();
    render(<NotificationList initial={[note({ id: 'n-9' })]} />);
    await user.click(screen.getByTestId('notification-row'));
    expect(markNotificationRead).toHaveBeenCalledWith('n-9');
  });

  it('does not offer "모두 읽음" when everything is read', () => {
    render(<NotificationList initial={[note({ read: true, status: 'READ' })]} />);
    expect(screen.queryByRole('button', { name: /모두 읽음/ })).not.toBeInTheDocument();
  });

  it('marks all unread rows read via the bulk action', async () => {
    const user = userEvent.setup();
    render(
      <NotificationList
        initial={[
          note({ id: 'a' }),
          note({ id: 'b', read: true, status: 'READ' }),
          note({ id: 'c' }),
        ]}
      />,
    );
    await user.click(screen.getByRole('button', { name: /모두 읽음/ }));
    expect(markAllNotificationsRead).toHaveBeenCalledWith(['a', 'c']);
  });
});
