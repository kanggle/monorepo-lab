import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PromotionForm } from '@/features/promotion-management/components/PromotionForm';

const mockPush = vi.fn();
const mockBack = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, back: mockBack }),
}));

const mockCreatePromotion = vi.fn().mockResolvedValue({ promotionId: 'new-1' });
const mockUpdatePromotion = vi.fn().mockResolvedValue({ promotionId: 'p1' });

vi.mock('@/features/promotion-management/hooks/use-create-promotion', () => ({
  useCreatePromotion: () => ({
    mutateAsync: mockCreatePromotion,
    isPending: false,
  }),
}));

vi.mock('@/features/promotion-management/hooks/use-update-promotion', () => ({
  useUpdatePromotion: () => ({
    mutateAsync: mockUpdatePromotion,
    isPending: false,
  }),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe('PromotionForm', () => {
  beforeEach(() => {
    mockCreatePromotion.mockClear();
    mockUpdatePromotion.mockClear();
    mockPush.mockClear();
    mockBack.mockClear();
  });

  describe('등록 모드', () => {
    it('빈 폼을 렌더링한다', () => {
      render(<PromotionForm />, { wrapper: createWrapper() });

      expect(screen.getByLabelText('프로모션명 *')).toHaveValue('');
      expect(screen.getByLabelText('할인값 *')).toHaveValue(0);
      expect(screen.getByLabelText('최대 발급 수량 *')).toHaveValue(0);
    });

    it('필수 필드가 비어있으면 등록 버튼이 비활성화된다', () => {
      render(<PromotionForm />, { wrapper: createWrapper() });

      const submitButton = screen.getByText('등록');
      expect(submitButton).toBeDisabled();
    });

    it('필수 필드 입력 후 등록 버튼이 활성화된다', async () => {
      render(<PromotionForm />, { wrapper: createWrapper() });

      await userEvent.type(screen.getByLabelText('프로모션명 *'), '테스트 프로모션');
      await userEvent.clear(screen.getByLabelText('할인값 *'));
      await userEvent.type(screen.getByLabelText('할인값 *'), '5000');
      await userEvent.clear(screen.getByLabelText('최대 발급 수량 *'));
      await userEvent.type(screen.getByLabelText('최대 발급 수량 *'), '100');
      fireEvent.change(screen.getByLabelText('시작일 *'), { target: { value: '2026-06-01' } });
      fireEvent.change(screen.getByLabelText('종료일 *'), { target: { value: '2026-06-30' } });

      expect(screen.getByText('등록')).not.toBeDisabled();
    });

    it('종료일이 시작일 이전이면 검증 메시지를 표시한다', async () => {
      render(<PromotionForm />, { wrapper: createWrapper() });

      fireEvent.change(screen.getByLabelText('시작일 *'), { target: { value: '2026-06-30' } });
      fireEvent.change(screen.getByLabelText('종료일 *'), { target: { value: '2026-06-01' } });

      expect(screen.getByText('종료일은 시작일 이후여야 합니다.')).toBeInTheDocument();
    });

    it('할인 유형 선택이 가능하다', () => {
      render(<PromotionForm />, { wrapper: createWrapper() });

      const select = screen.getByLabelText('할인 유형 *') as HTMLSelectElement;
      expect(select).toBeInTheDocument();
      expect(select.options).toHaveLength(2);
      expect(select.options[0].text).toBe('정액');
      expect(select.options[1].text).toBe('정률 (%)');
    });
  });

  describe('수정 모드', () => {
    const promotion = {
      promotionId: 'p1',
      name: '기존 프로모션',
      description: '기존 설명',
      discountType: 'FIXED' as const,
      discountValue: 5000,
      maxDiscountAmount: 10000,
      maxIssuanceCount: 1000,
      issuedCount: 500,
      startDate: '2026-06-01T00:00:00Z',
      endDate: '2026-06-30T00:00:00Z',
      status: 'ACTIVE' as const,
      createdAt: '2026-05-01T00:00:00Z',
      updatedAt: '2026-05-15T00:00:00Z',
    };

    it('기존 데이터로 폼을 채운다', () => {
      render(<PromotionForm promotion={promotion} />, { wrapper: createWrapper() });

      expect(screen.getByLabelText('프로모션명 *')).toHaveValue('기존 프로모션');
      expect(screen.getByLabelText('할인값 *')).toHaveValue(5000);
      expect(screen.getByLabelText('최대 발급 수량 *')).toHaveValue(1000);
    });

    it('수정 모드에서 수정 버튼이 표시된다', () => {
      render(<PromotionForm promotion={promotion} />, { wrapper: createWrapper() });
      expect(screen.getByText('수정')).toBeInTheDocument();
    });

    it('취소 버튼 클릭 시 뒤로 이동한다', async () => {
      render(<PromotionForm promotion={promotion} />, { wrapper: createWrapper() });

      await userEvent.click(screen.getByText('취소'));
      expect(mockBack).toHaveBeenCalledTimes(1);
    });
  });
});
