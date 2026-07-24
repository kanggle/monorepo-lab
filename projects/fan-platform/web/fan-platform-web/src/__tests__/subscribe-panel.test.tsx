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

  it('suppresses the MEMBERS_ONLY subscribe when PREMIUM is held (superset)', () => {
    render(<SubscribePanel heldActiveTiers={['PREMIUM']} />);
    // PREMIUM card → in-use; MEMBERS_ONLY card → already covered by premium, so
    // it offers NO subscribe (PREMIUM ⊇ MEMBERS_ONLY). Both actions are non-buttons.
    expect(screen.getByText('이용 중인 멤버십')).toBeInTheDocument();
    expect(screen.getByText('프리미엄에 포함됨')).toBeInTheDocument();
    expect(screen.queryAllByRole('button', { name: '구독하기' })).toHaveLength(0);
  });

  it('still offers PREMIUM when only MEMBERS_ONLY is held (upgrade path stays open)', () => {
    render(<SubscribePanel heldActiveTiers={['MEMBERS_ONLY']} />);
    expect(screen.getByText('이용 중인 멤버십')).toBeInTheDocument();
    expect(screen.queryByText('프리미엄에 포함됨')).not.toBeInTheDocument();
    // PREMIUM is a genuine upgrade → its subscribe button remains.
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
