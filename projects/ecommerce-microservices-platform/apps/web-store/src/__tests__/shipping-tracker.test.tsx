import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import type { ShippingResponse } from '@repo/types';
import { TestQueryProvider } from './test-utils';

vi.mock('@/features/order/api/shipping-api', () => ({
  getShippingByOrder: vi.fn(),
}));

import { getShippingByOrder } from '@/features/order/api/shipping-api';
import { ShippingTracker } from '@/features/order/ui/ShippingTracker';

const mockGetShippingByOrder = vi.mocked(getShippingByOrder);

const MOCK_SHIPPING_PREPARING: ShippingResponse = {
  shippingId: 'ship-1',
  orderId: 'order-1',
  status: 'PREPARING',
  trackingNumber: null,
  carrier: null,
  statusHistory: [
    { status: 'PREPARING', changedAt: '2026-04-01T10:00:00Z' },
  ],
  createdAt: '2026-04-01T10:00:00Z',
  updatedAt: '2026-04-01T10:00:00Z',
};

const MOCK_SHIPPING_SHIPPED: ShippingResponse = {
  shippingId: 'ship-1',
  orderId: 'order-1',
  status: 'SHIPPED',
  trackingNumber: '1234567890',
  carrier: 'CJ대한통운',
  statusHistory: [
    { status: 'PREPARING', changedAt: '2026-04-01T10:00:00Z' },
    { status: 'SHIPPED', changedAt: '2026-04-02T09:00:00Z' },
  ],
  createdAt: '2026-04-01T10:00:00Z',
  updatedAt: '2026-04-02T09:00:00Z',
};

const MOCK_SHIPPING_IN_TRANSIT: ShippingResponse = {
  shippingId: 'ship-1',
  orderId: 'order-1',
  status: 'IN_TRANSIT',
  trackingNumber: '1234567890',
  carrier: 'CJ대한통운',
  statusHistory: [
    { status: 'PREPARING', changedAt: '2026-04-01T10:00:00Z' },
    { status: 'SHIPPED', changedAt: '2026-04-02T09:00:00Z' },
    { status: 'IN_TRANSIT', changedAt: '2026-04-02T14:00:00Z' },
  ],
  createdAt: '2026-04-01T10:00:00Z',
  updatedAt: '2026-04-02T14:00:00Z',
};

const MOCK_SHIPPING_DELIVERED: ShippingResponse = {
  shippingId: 'ship-1',
  orderId: 'order-1',
  status: 'DELIVERED',
  trackingNumber: '1234567890',
  carrier: 'CJ대한통운',
  statusHistory: [
    { status: 'PREPARING', changedAt: '2026-04-01T10:00:00Z' },
    { status: 'SHIPPED', changedAt: '2026-04-02T09:00:00Z' },
    { status: 'IN_TRANSIT', changedAt: '2026-04-02T14:00:00Z' },
    { status: 'DELIVERED', changedAt: '2026-04-03T11:30:00Z' },
  ],
  createdAt: '2026-04-01T10:00:00Z',
  updatedAt: '2026-04-03T11:30:00Z',
};

describe('ShippingTracker', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('PREPARING 상태에서 스텝 인디케이터를 표시한다', async () => {
    mockGetShippingByOrder.mockResolvedValueOnce(MOCK_SHIPPING_PREPARING);

    render(
      <TestQueryProvider>
        <ShippingTracker orderId="order-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('배송 추적')).toBeInTheDocument();
    });

    expect(screen.getByText('상품 준비중')).toBeInTheDocument();
    expect(screen.getByText('배송 시작')).toBeInTheDocument();
    expect(screen.getByText('배송중')).toBeInTheDocument();
    expect(screen.getByText('배송 완료')).toBeInTheDocument();
  });

  it('PREPARING 상태에서 운송장 번호와 택배사가 표시되지 않는다', async () => {
    mockGetShippingByOrder.mockResolvedValueOnce(MOCK_SHIPPING_PREPARING);

    render(
      <TestQueryProvider>
        <ShippingTracker orderId="order-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('배송 추적')).toBeInTheDocument();
    });

    expect(screen.queryByText(/택배사:/)).not.toBeInTheDocument();
    expect(screen.queryByText(/운송장 번호:/)).not.toBeInTheDocument();
  });

  it('SHIPPED 상태에서 운송장 번호와 택배사가 표시된다', async () => {
    mockGetShippingByOrder.mockResolvedValueOnce(MOCK_SHIPPING_SHIPPED);

    render(
      <TestQueryProvider>
        <ShippingTracker orderId="order-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText(/택배사: CJ대한통운/)).toBeInTheDocument();
    });

    expect(screen.getByText(/운송장 번호: 1234567890/)).toBeInTheDocument();
  });

  it('IN_TRANSIT 상태에서 운송장 번호와 택배사가 표시된다', async () => {
    mockGetShippingByOrder.mockResolvedValueOnce(MOCK_SHIPPING_IN_TRANSIT);

    render(
      <TestQueryProvider>
        <ShippingTracker orderId="order-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText(/택배사: CJ대한통운/)).toBeInTheDocument();
    });

    expect(screen.getByText(/운송장 번호: 1234567890/)).toBeInTheDocument();
  });

  it('DELIVERED 상태에서 배송 완료일이 표시된다', async () => {
    mockGetShippingByOrder.mockResolvedValueOnce(MOCK_SHIPPING_DELIVERED);

    render(
      <TestQueryProvider>
        <ShippingTracker orderId="order-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText(/배송 완료일:/)).toBeInTheDocument();
    });

    expect(screen.getByText(/택배사: CJ대한통운/)).toBeInTheDocument();
    expect(screen.getByText(/운송장 번호: 1234567890/)).toBeInTheDocument();
  });

  it('DELIVERED 상태가 아니면 배송 완료일이 표시되지 않는다', async () => {
    mockGetShippingByOrder.mockResolvedValueOnce(MOCK_SHIPPING_IN_TRANSIT);

    render(
      <TestQueryProvider>
        <ShippingTracker orderId="order-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('배송 추적')).toBeInTheDocument();
    });

    expect(screen.queryByText(/배송 완료일:/)).not.toBeInTheDocument();
  });

  it('배송 정보가 없으면(404) 안내 메시지를 표시한다', async () => {
    mockGetShippingByOrder.mockRejectedValueOnce({
      code: 'SHIPPING_NOT_FOUND',
      message: 'Shipping record for given order does not exist',
      timestamp: new Date().toISOString(),
    });

    render(
      <TestQueryProvider>
        <ShippingTracker orderId="order-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('배송 추적')).toBeInTheDocument();
    });

    expect(screen.getByText(/배송 준비 중입니다/)).toBeInTheDocument();
  });

  it('API 에러 시 에러 메시지를 표시한다', async () => {
    mockGetShippingByOrder.mockRejectedValue(new Error('network error'));

    render(
      <TestQueryProvider>
        <ShippingTracker orderId="order-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('배송 정보를 불러오는데 실패했습니다.')).toBeInTheDocument();
    }, { timeout: 10000 });
  });

  it('권한 없는 접근(403) 시 접근 불가 메시지를 표시한다', async () => {
    mockGetShippingByOrder.mockRejectedValueOnce({
      code: 'ACCESS_DENIED',
      message: 'Not the order owner',
      timestamp: new Date().toISOString(),
    });

    render(
      <TestQueryProvider>
        <ShippingTracker orderId="order-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('배송 정보에 접근할 수 없습니다.')).toBeInTheDocument();
    });
  });

  it('로딩 중일 때 스켈레톤을 표시한다', () => {
    mockGetShippingByOrder.mockReturnValue(new Promise(() => {}));

    render(
      <TestQueryProvider>
        <ShippingTracker orderId="order-1" />
      </TestQueryProvider>,
    );

    expect(screen.queryByText('배송 추적')).not.toBeInTheDocument();
  });

  it('취소된 주문의 배송 정보 조회 시 안내 메시지를 표시한다', async () => {
    mockGetShippingByOrder.mockRejectedValueOnce({
      code: 'SHIPPING_NOT_FOUND',
      message: 'Shipping record for given order does not exist',
      timestamp: new Date().toISOString(),
    });

    render(
      <TestQueryProvider>
        <ShippingTracker orderId="cancelled-order-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('배송 추적')).toBeInTheDocument();
    });

    expect(screen.getByText(/배송 준비 중입니다/)).toBeInTheDocument();
    expect(screen.queryByText(/택배사:/)).not.toBeInTheDocument();
    expect(screen.queryByText(/운송장 번호:/)).not.toBeInTheDocument();
  });

  it('운송장 번호만 있고 택배사가 없으면 택배사에 "정보 없음"을 표시한다', async () => {
    const shippingWithoutCarrier: ShippingResponse = {
      ...MOCK_SHIPPING_SHIPPED,
      carrier: null,
    };
    mockGetShippingByOrder.mockResolvedValueOnce(shippingWithoutCarrier);

    render(
      <TestQueryProvider>
        <ShippingTracker orderId="order-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText(/운송장 번호: 1234567890/)).toBeInTheDocument();
    });

    expect(screen.getByText(/택배사: 정보 없음/)).toBeInTheDocument();
  });

  it('택배사만 있고 운송장 번호가 없으면 운송장 번호에 "정보 없음"을 표시한다', async () => {
    const shippingWithoutTracking: ShippingResponse = {
      ...MOCK_SHIPPING_SHIPPED,
      trackingNumber: null,
    };
    mockGetShippingByOrder.mockResolvedValueOnce(shippingWithoutTracking);

    render(
      <TestQueryProvider>
        <ShippingTracker orderId="order-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText(/택배사: CJ대한통운/)).toBeInTheDocument();
    });

    expect(screen.getByText(/운송장 번호: 정보 없음/)).toBeInTheDocument();
  });

  it('스텝 인디케이터에 role="list" 속성이 있다', async () => {
    mockGetShippingByOrder.mockResolvedValueOnce(MOCK_SHIPPING_PREPARING);

    render(
      <TestQueryProvider>
        <ShippingTracker orderId="order-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByRole('list', { name: '배송 진행 상태' })).toBeInTheDocument();
    });
  });
});
