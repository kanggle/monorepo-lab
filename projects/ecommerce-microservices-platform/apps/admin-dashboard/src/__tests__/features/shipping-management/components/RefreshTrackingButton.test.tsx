import { render, screen, fireEvent } from '@testing-library/react';
import { RefreshTrackingButton } from '@/features/shipping-management/components/RefreshTrackingButton';
import type { ShippingSummary, ShippingStatus } from '@repo/types';

function makeShipping(overrides: Partial<ShippingSummary> = {}): ShippingSummary {
  return {
    shippingId: 's1',
    orderId: 'order-0001',
    status: 'SHIPPED',
    carrier: 'kr.lotte',
    trackingNumber: '259665739034',
    createdAt: '2026-06-05T10:00:00Z',
    ...overrides,
  } as ShippingSummary;
}

const LABEL = '택배사 동기화';

describe('RefreshTrackingButton', () => {
  it('SHIPPED + 운송장번호 있으면 버튼을 보여준다', () => {
    render(<RefreshTrackingButton shipping={makeShipping({ status: 'SHIPPED' })} isPending={false} onSync={vi.fn()} />);
    expect(screen.getByText(LABEL)).toBeInTheDocument();
  });

  it('IN_TRANSIT + 운송장번호 있으면 버튼을 보여준다', () => {
    render(<RefreshTrackingButton shipping={makeShipping({ status: 'IN_TRANSIT' })} isPending={false} onSync={vi.fn()} />);
    expect(screen.getByText(LABEL)).toBeInTheDocument();
  });

  it.each<ShippingStatus>(['PREPARING', 'DELIVERED'])(
    '%s 상태면 버튼을 숨긴다 (waybill 없음/terminal)',
    (status) => {
      render(<RefreshTrackingButton shipping={makeShipping({ status })} isPending={false} onSync={vi.fn()} />);
      expect(screen.queryByText(LABEL)).not.toBeInTheDocument();
    },
  );

  it('운송장번호가 없으면 (SHIPPED 이어도) 버튼을 숨긴다', () => {
    render(
      <RefreshTrackingButton
        shipping={makeShipping({ status: 'SHIPPED', trackingNumber: null as unknown as string })}
        isPending={false}
        onSync={vi.fn()}
      />,
    );
    expect(screen.queryByText(LABEL)).not.toBeInTheDocument();
  });

  it('운송장번호가 공백만이면 버튼을 숨긴다', () => {
    render(
      <RefreshTrackingButton shipping={makeShipping({ trackingNumber: '   ' })} isPending={false} onSync={vi.fn()} />,
    );
    expect(screen.queryByText(LABEL)).not.toBeInTheDocument();
  });

  it('클릭 시 onSync(shipping) 를 호출한다', () => {
    const onSync = vi.fn();
    const shipping = makeShipping();
    render(<RefreshTrackingButton shipping={shipping} isPending={false} onSync={onSync} />);
    fireEvent.click(screen.getByText(LABEL));
    expect(onSync).toHaveBeenCalledWith(shipping);
  });

  it('isPending 이면 비활성화된다', () => {
    render(<RefreshTrackingButton shipping={makeShipping()} isPending={true} onSync={vi.fn()} />);
    expect(screen.getByText(LABEL)).toBeDisabled();
  });
});
