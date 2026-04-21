import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StockAdjustmentForm } from '@/features/product-management/components/StockAdjustmentForm';

const mockMutateAsync = vi.fn().mockResolvedValue({ variantId: 'v1', stock: 15 });

vi.mock('@/features/product-management/hooks/use-adjust-stock', () => ({
  useAdjustStock: () => ({
    mutateAsync: mockMutateAsync,
    isPending: false,
  }),
}));

const mockVariant = { id: 'v1', optionName: '기본', stock: 10, additionalPrice: 0 };
const mockOnClose = vi.fn();

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe('StockAdjustmentForm', () => {
  beforeEach(() => {
    mockMutateAsync.mockClear();
    mockOnClose.mockClear();
  });

  it('현재 재고를 표시한다', () => {
    render(
      <StockAdjustmentForm productId="p1" variant={mockVariant} onClose={mockOnClose} />,
      { wrapper: createWrapper() },
    );

    expect(screen.getByText('현재 재고: 10')).toBeInTheDocument();
    expect(screen.getByText(/재고 조정 — 기본/)).toBeInTheDocument();
  });

  it('수량이 0이고 사유가 비어있으면 제출 버튼이 비활성화된다', () => {
    render(
      <StockAdjustmentForm productId="p1" variant={mockVariant} onClose={mockOnClose} />,
      { wrapper: createWrapper() },
    );

    const submitButton = screen.getByRole('button', { name: '조정' });
    expect(submitButton).toBeDisabled();
  });

  it('유효한 입력 후 제출하면 mutation을 호출한다', async () => {
    const user = userEvent.setup();
    render(
      <StockAdjustmentForm productId="p1" variant={mockVariant} onClose={mockOnClose} />,
      { wrapper: createWrapper() },
    );

    const quantityInput = screen.getByLabelText(/조정 수량/);
    const reasonInput = screen.getByLabelText('사유');

    await user.clear(quantityInput);
    await user.type(quantityInput, '5');
    await user.type(reasonInput, '입고');

    const submitButton = screen.getByRole('button', { name: '조정' });
    await user.click(submitButton);

    expect(mockMutateAsync).toHaveBeenCalledWith({
      productId: 'p1',
      data: { variantId: 'v1', quantity: 5, reason: '입고' },
    });
    expect(mockOnClose).toHaveBeenCalled();
  });

  it('취소 버튼 클릭 시 onClose를 호출한다', async () => {
    const user = userEvent.setup();
    render(
      <StockAdjustmentForm productId="p1" variant={mockVariant} onClose={mockOnClose} />,
      { wrapper: createWrapper() },
    );

    await user.click(screen.getByRole('button', { name: '취소' }));
    expect(mockOnClose).toHaveBeenCalled();
  });

  it('API 에러 시 에러 메시지를 표시한다', async () => {
    mockMutateAsync.mockRejectedValueOnce(new Error('재고 부족'));
    const user = userEvent.setup();
    render(
      <StockAdjustmentForm productId="p1" variant={mockVariant} onClose={mockOnClose} />,
      { wrapper: createWrapper() },
    );

    const quantityInput = screen.getByLabelText(/조정 수량/);
    const reasonInput = screen.getByLabelText('사유');

    await user.clear(quantityInput);
    await user.type(quantityInput, '-20');
    await user.type(reasonInput, '출고');

    await user.click(screen.getByRole('button', { name: '조정' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('재고 부족');
    expect(mockOnClose).not.toHaveBeenCalled();
  });
});
