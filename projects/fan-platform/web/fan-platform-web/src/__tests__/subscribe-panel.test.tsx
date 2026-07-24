import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SubscribePanel } from '@/features/membership/ui/SubscribePanel';

const { subscribe, getUpgradeQuote } = vi.hoisted(() => ({
  subscribe: vi.fn(),
  getUpgradeQuote: vi.fn(),
}));
vi.mock('@/features/membership/api/actions', () => ({ subscribe, getUpgradeQuote }));

// PortOne window + tier prices mocked — no real SDK / storeId in unit tests.
const { requestPortOnePayment } = vi.hoisted(() => ({ requestPortOnePayment: vi.fn() }));
vi.mock('@/features/membership/lib/portone-checkout', () => ({
  requestPortOnePayment,
  TIER_MONTHLY_KRW: { MEMBERS_ONLY: 7900, PREMIUM: 17900 },
}));

describe('SubscribePanel', () => {
  beforeEach(() => {
    subscribe.mockReset();
    getUpgradeQuote.mockReset();
    requestPortOnePayment.mockReset();
  });

  it('renders both tiers with a pay control', () => {
    render(<SubscribePanel heldActiveTiers={[]} />);
    expect(screen.getByText('멤버스 전용')).toBeInTheDocument();
    expect(screen.getByText('프리미엄')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: '카드로 결제' })).toHaveLength(2);
  });

  it('suppresses the MEMBERS_ONLY pay button when PREMIUM is held (FE-009 superset)', () => {
    render(<SubscribePanel heldActiveTiers={['PREMIUM']} />);
    expect(screen.getByText('이용 중인 멤버십')).toBeInTheDocument();
    expect(screen.getByText('프리미엄에 포함됨')).toBeInTheDocument();
    expect(screen.queryAllByRole('button', { name: '카드로 결제' })).toHaveLength(0);
    expect(getUpgradeQuote).not.toHaveBeenCalled();
  });

  it('plain PREMIUM subscribe charges the tier list price (17,900×N)', async () => {
    requestPortOnePayment.mockResolvedValue({ ok: true, paymentId: 'pay-x' });
    subscribe.mockResolvedValue({ ok: true, membership: {} });
    const user = userEvent.setup();
    render(<SubscribePanel heldActiveTiers={[]} />);

    await user.click(screen.getAllByRole('button', { name: '카드로 결제' })[1]); // PREMIUM card

    expect(requestPortOnePayment).toHaveBeenCalledWith(
      expect.any(String),
      17900,
      expect.objectContaining({}),
    );
    expect(subscribe).toHaveBeenCalledWith('PREMIUM', 1, 'pay-x');
  });

  it('MEMBERS_ONLY subscribe charges 7,900×N', async () => {
    requestPortOnePayment.mockResolvedValue({ ok: true, paymentId: 'pay-m' });
    subscribe.mockResolvedValue({ ok: true, membership: {} });
    const user = userEvent.setup();
    render(<SubscribePanel heldActiveTiers={[]} />);

    await user.click(screen.getAllByRole('button', { name: '카드로 결제' })[0]); // MEMBERS_ONLY card

    expect(requestPortOnePayment).toHaveBeenCalledWith(
      expect.any(String),
      7900,
      expect.objectContaining({}),
    );
    expect(subscribe).toHaveBeenCalledWith('MEMBERS_ONLY', 1, 'pay-m');
  });

  it('forwards the signed-in fan email to the PG (KG이니시스 requires buyer email)', async () => {
    requestPortOnePayment.mockResolvedValue({ ok: true, paymentId: 'pay-e' });
    subscribe.mockResolvedValue({ ok: true, membership: {} });
    const user = userEvent.setup();
    render(
      <SubscribePanel heldActiveTiers={[]} buyerEmail="fan@example.com" buyerName="테스트 팬" />,
    );

    await user.click(screen.getAllByRole('button', { name: '카드로 결제' })[0]);

    expect(requestPortOnePayment).toHaveBeenCalledWith(
      expect.any(String),
      7900,
      expect.objectContaining({ email: 'fan@example.com', fullName: '테스트 팬' }),
    );
  });

  it('upgrade (holds MEMBERS_ONLY) → PREMIUM shows the credit + charges the prorated quote', async () => {
    getUpgradeQuote.mockResolvedValue({
      tier: 'PREMIUM', planMonths: 1, listPriceMinor: 17900,
      creditMinor: 3950, chargeMinor: 13950, supersedesMembershipId: 'm-1',
    });
    requestPortOnePayment.mockResolvedValue({ ok: true, paymentId: 'pay-up' });
    subscribe.mockResolvedValue({ ok: true, membership: {} });
    const user = userEvent.setup();
    render(<SubscribePanel heldActiveTiers={['MEMBERS_ONLY']} />);

    // MEMBERS_ONLY is in-use; PREMIUM becomes an upgrade once the async quote resolves.
    const upgradeBtn = await screen.findByRole('button', { name: '프리미엄으로 업그레이드' });
    expect(screen.getByText(/크레딧/)).toBeInTheDocument();

    await user.click(upgradeBtn);

    // The PRORATED charge (13,950) — not the list price — reaches PortOne.
    expect(requestPortOnePayment).toHaveBeenCalledWith(
      expect.any(String),
      13950,
      expect.objectContaining({}),
    );
    expect(subscribe).toHaveBeenCalledWith('PREMIUM', 1, 'pay-up');
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
    requestPortOnePayment.mockResolvedValue({ ok: true, paymentId: 'pay-x' });
    subscribe.mockResolvedValue({ ok: false, code: 'PAYMENT_DECLINED', message: 'declined' });
    const user = userEvent.setup();
    render(<SubscribePanel heldActiveTiers={[]} />);

    await user.click(screen.getAllByRole('button', { name: '카드로 결제' })[1]);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('결제가 거절되었습니다');
  });
});
