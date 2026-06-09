import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SubscribePanel } from '@/features/membership/ui/SubscribePanel';

const { subscribe } = vi.hoisted(() => ({ subscribe: vi.fn() }));
vi.mock('@/features/membership/api/actions', () => ({ subscribe }));

describe('SubscribePanel', () => {
  beforeEach(() => subscribe.mockReset());

  it('renders both tiers with a subscribe control', () => {
    render(<SubscribePanel heldActiveTiers={[]} />);
    expect(screen.getByText('멤버스 전용')).toBeInTheDocument();
    expect(screen.getByText('프리미엄')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: '구독하기' })).toHaveLength(2);
  });

  it('marks a held tier as in-use instead of offering subscribe', () => {
    render(<SubscribePanel heldActiveTiers={['PREMIUM']} />);
    expect(screen.getByText('이용 중인 멤버십')).toBeInTheDocument();
    // Only the un-held tier keeps its subscribe button.
    expect(screen.getAllByRole('button', { name: '구독하기' })).toHaveLength(1);
  });

  it('subscribes the chosen tier with the entered plan + token', async () => {
    subscribe.mockResolvedValue({ ok: true, membership: {} });
    const user = userEvent.setup();
    render(<SubscribePanel heldActiveTiers={[]} />);

    await user.click(screen.getAllByRole('button', { name: '구독하기' })[0]);

    expect(subscribe).toHaveBeenCalledWith('MEMBERS_ONLY', 1, 'tok_visa_demo');
  });

  it('surfaces a PG decline inline (no throw)', async () => {
    subscribe.mockResolvedValue({ ok: false, code: 'PAYMENT_DECLINED', message: 'declined' });
    const user = userEvent.setup();
    render(<SubscribePanel heldActiveTiers={[]} />);

    await user.click(screen.getAllByRole('button', { name: '구독하기' })[1]);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('결제가 거절되었습니다');
  });
});
