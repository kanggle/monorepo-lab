import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SubscribePanel } from '@/features/membership/ui/SubscribePanel';

const { subscribe } = vi.hoisted(() => ({ subscribe: vi.fn() }));
vi.mock('@/features/membership/api/actions', () => ({ subscribe }));

// The PortOne payment window is mocked — no real SDK / storeId in unit tests.
const { requestPortOnePayment } = vi.hoisted(() => ({ requestPortOnePayment: vi.fn() }));
vi.mock('@/features/membership/lib/portone-checkout', () => ({ requestPortOnePayment }));

describe('SubscribePanel', () => {
  beforeEach(() => {
    subscribe.mockReset();
    requestPortOnePayment.mockReset();
  });

  it('renders both tiers with a pay control', () => {
    render(<SubscribePanel heldActiveTiers={[]} />);
    expect(screen.getByText('멤버스 전용')).toBeInTheDocument();
    expect(screen.getByText('프리미엄')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: '카드로 결제' })).toHaveLength(2);
  });

  it('suppresses the MEMBERS_ONLY pay button when PREMIUM is held (superset)', () => {
    render(<SubscribePanel heldActiveTiers={['PREMIUM']} />);
    // PREMIUM card → in-use; MEMBERS_ONLY card → already covered by premium, so
    // it offers NO pay button (PREMIUM ⊇ MEMBERS_ONLY). (FE-009 behavior preserved.)
    expect(screen.getByText('이용 중인 멤버십')).toBeInTheDocument();
    expect(screen.getByText('프리미엄에 포함됨')).toBeInTheDocument();
    expect(screen.queryAllByRole('button', { name: '카드로 결제' })).toHaveLength(0);
  });

  it('still offers PREMIUM when only MEMBERS_ONLY is held (upgrade path stays open)', () => {
    render(<SubscribePanel heldActiveTiers={['MEMBERS_ONLY']} />);
    expect(screen.getByText('이용 중인 멤버십')).toBeInTheDocument();
    expect(screen.queryByText('프리미엄에 포함됨')).not.toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: '카드로 결제' })).toHaveLength(1);
  });

  it('opens the payment window then subscribes with the returned paymentId', async () => {
    requestPortOnePayment.mockResolvedValue({ ok: true, paymentId: 'pay-xyz' });
    subscribe.mockResolvedValue({ ok: true, membership: {} });
    const user = userEvent.setup();
    render(<SubscribePanel heldActiveTiers={[]} />);

    await user.click(screen.getAllByRole('button', { name: '카드로 결제' })[0]);

    expect(requestPortOnePayment).toHaveBeenCalled();
    // The verified paymentId — not a client-typed token — is what reaches the action.
    expect(subscribe).toHaveBeenCalledWith('MEMBERS_ONLY', 1, 'pay-xyz');
  });

  it('does not subscribe when the payment window is canceled (inline notice)', async () => {
    requestPortOnePayment.mockResolvedValue({ ok: false, message: '결제가 취소되었습니다.' });
    const user = userEvent.setup();
    render(<SubscribePanel heldActiveTiers={[]} />);

    await user.click(screen.getAllByRole('button', { name: '카드로 결제' })[0]);

    expect(subscribe).not.toHaveBeenCalled();
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('결제가 취소되었습니다');
  });

  it('surfaces a backend PG decline inline (no throw)', async () => {
    requestPortOnePayment.mockResolvedValue({ ok: true, paymentId: 'pay-xyz' });
    subscribe.mockResolvedValue({ ok: false, code: 'PAYMENT_DECLINED', message: 'declined' });
    const user = userEvent.setup();
    render(<SubscribePanel heldActiveTiers={[]} />);

    await user.click(screen.getAllByRole('button', { name: '카드로 결제' })[1]);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('결제가 거절되었습니다');
  });
});
