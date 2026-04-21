import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ShippingList } from '@/features/shipping-management/components/ShippingList';

const mockPush = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  useSearchParams: () => new URLSearchParams(),
}));

const mockGetShippings = vi.fn();
const mockUpdateShippingStatus = vi.fn();

vi.mock('@/features/shipping-management/api/shipping-api', () => ({
  getShippings: (...args: unknown[]) => mockGetShippings(...args),
  updateShippingStatus: (...args: unknown[]) => mockUpdateShippingStatus(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

const mockShippings = {
  content: [
    {
      shippingId: 's1',
      orderId: 'order-1234-5678-abcd',
      status: 'PREPARING' as const,
      trackingNumber: null,
      carrier: null,
      createdAt: '2026-04-01T10:00:00Z',
      updatedAt: '2026-04-01T10:00:00Z',
    },
    {
      shippingId: 's2',
      orderId: 'order-9999-aaaa-bbbb',
      status: 'SHIPPED' as const,
      trackingNumber: '1234567890',
      carrier: 'CJ대한통운',
      createdAt: '2026-04-02T10:00:00Z',
      updatedAt: '2026-04-02T12:00:00Z',
    },
    {
      shippingId: 's3',
      orderId: 'order-dddd-eeee-ffff',
      status: 'IN_TRANSIT' as const,
      trackingNumber: '9876543210',
      carrier: '한진택배',
      createdAt: '2026-04-03T10:00:00Z',
      updatedAt: '2026-04-03T14:00:00Z',
    },
    {
      shippingId: 's4',
      orderId: 'order-1111-2222-3333',
      status: 'DELIVERED' as const,
      trackingNumber: '5555555555',
      carrier: '우체국택배',
      createdAt: '2026-04-04T10:00:00Z',
      updatedAt: '2026-04-04T16:00:00Z',
    },
  ],
  totalElements: 4,
  page: 0,
  size: 20,
};

describe('ShippingList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetShippings.mockResolvedValue(mockShippings);
    mockUpdateShippingStatus.mockResolvedValue({ shippingId: 's1', status: 'SHIPPED', updatedAt: '2026-04-01T12:00:00Z' });
  });

  it('배송 목록을 테이블에 표시한다', async () => {
    render(<ShippingList />, { wrapper: createWrapper() });

    expect(await screen.findByText('order-12...')).toBeInTheDocument();
    expect(screen.getByText('order-99...')).toBeInTheDocument();
    expect(screen.getByText('CJ대한통운')).toBeInTheDocument();
    expect(screen.getByText('1234567890')).toBeInTheDocument();
  });

  it('로딩 중일 때 스피너를 표시한다', () => {
    render(<ShippingList />, { wrapper: createWrapper() });
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('상태 필터를 표시한다', async () => {
    render(<ShippingList />, { wrapper: createWrapper() });

    await screen.findByText('order-12...');
    expect(screen.getByRole('combobox')).toBeInTheDocument();
    expect(screen.getByText('전체 상태')).toBeInTheDocument();
  });

  it('배송 상태 뱃지를 표시한다', async () => {
    render(<ShippingList />, { wrapper: createWrapper() });

    await screen.findByText('order-12...');
    const preparingBadges = screen.getAllByText('준비중');
    expect(preparingBadges.length).toBeGreaterThanOrEqual(2); // FilterBar option + StatusBadge
  });

  it('PREPARING 상태에 발송 처리 버튼을 표시한다', async () => {
    render(<ShippingList />, { wrapper: createWrapper() });

    await screen.findByText('order-12...');
    expect(screen.getByText('발송 처리')).toBeInTheDocument();
  });

  it('SHIPPED 상태에 배송중 전환 버튼을 표시한다', async () => {
    render(<ShippingList />, { wrapper: createWrapper() });

    await screen.findByText('order-12...');
    expect(screen.getByText('배송중 전환')).toBeInTheDocument();
  });

  it('IN_TRANSIT 상태에 배송완료 처리 버튼을 표시한다', async () => {
    render(<ShippingList />, { wrapper: createWrapper() });

    await screen.findByText('order-12...');
    expect(screen.getByText('배송완료 처리')).toBeInTheDocument();
  });

  it('DELIVERED 상태에는 상태 변경 버튼이 없다', async () => {
    mockGetShippings.mockResolvedValue({
      content: [mockShippings.content[3]],
      totalElements: 1,
      page: 0,
      size: 20,
    });

    render(<ShippingList />, { wrapper: createWrapper() });

    await screen.findByText('order-11...');
    expect(screen.queryByText('발송 처리')).not.toBeInTheDocument();
    expect(screen.queryByText('배송중 전환')).not.toBeInTheDocument();
    expect(screen.queryByText('배송완료 처리')).not.toBeInTheDocument();
  });

  it('발송 처리 버튼 클릭 시 운송장 입력 모달이 표시된다', async () => {
    const user = userEvent.setup();
    render(<ShippingList />, { wrapper: createWrapper() });

    await screen.findByText('order-12...');
    await user.click(screen.getByText('발송 처리'));

    expect(screen.getByRole('dialog', { name: '발송 처리' })).toBeInTheDocument();
    expect(screen.getByLabelText('택배사')).toBeInTheDocument();
    expect(screen.getByLabelText('운송장 번호')).toBeInTheDocument();
  });

  it('배송중 전환 버튼 클릭 시 확인 다이얼로그가 표시된다', async () => {
    const user = userEvent.setup();
    render(<ShippingList />, { wrapper: createWrapper() });

    await screen.findByText('order-12...');
    await user.click(screen.getByText('배송중 전환'));

    expect(screen.getByRole('dialog', { name: '배송 상태 변경' })).toBeInTheDocument();
  });

  it('배송 건이 없을 때 빈 상태 메시지를 표시한다', async () => {
    mockGetShippings.mockResolvedValue({
      content: [],
      totalElements: 0,
      page: 0,
      size: 20,
    });

    render(<ShippingList />, { wrapper: createWrapper() });

    expect(await screen.findByText('배송 건이 없습니다.')).toBeInTheDocument();
  });

  it('API 실패 시 에러 메시지와 재시도 버튼을 표시한다', async () => {
    mockGetShippings.mockRejectedValue(new Error('Network error'));

    render(<ShippingList />, { wrapper: createWrapper() });

    expect(await screen.findByText('배송 목록을 불러오는데 실패했습니다.')).toBeInTheDocument();
    expect(screen.getByText('다시 시도')).toBeInTheDocument();
  });

  it('운송장 번호/택배사 없는 항목은 -로 표시한다', async () => {
    mockGetShippings.mockResolvedValue({
      content: [mockShippings.content[0]],
      totalElements: 1,
      page: 0,
      size: 20,
    });

    render(<ShippingList />, { wrapper: createWrapper() });

    await screen.findByText('order-12...');
    const dashes = screen.getAllByText('-');
    expect(dashes.length).toBeGreaterThanOrEqual(2);
  });
});
