import { renderHook, act, waitFor } from '@testing-library/react';
import { usePromotionForm } from '@/features/promotion-management/hooks/use-promotion-form';

const mockPush = vi.fn();
const mockCreateMutate = vi.fn().mockResolvedValue({ promotionId: 'new-1' });
const mockUpdateMutate = vi.fn().mockResolvedValue({ promotionId: 'p1' });

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}));

vi.mock('@/features/promotion-management/hooks/use-create-promotion', () => ({
  useCreatePromotion: () => ({ mutateAsync: mockCreateMutate }),
}));

vi.mock('@/features/promotion-management/hooks/use-update-promotion', () => ({
  useUpdatePromotion: () => ({ mutateAsync: mockUpdateMutate }),
}));

function createEvent() {
  return { preventDefault: vi.fn() } as unknown as React.FormEvent;
}

describe('usePromotionForm', () => {
  beforeEach(() => {
    mockPush.mockClear();
    mockCreateMutate.mockClear();
    mockUpdateMutate.mockClear();
  });

  describe('등록 모드', () => {
    it('기본값으로 초기화된다', () => {
      const { result } = renderHook(() => usePromotionForm());

      expect(result.current.name).toBe('');
      expect(result.current.description).toBe('');
      expect(result.current.discountType).toBe('FIXED');
      expect(result.current.discountValue).toBe(0);
      expect(result.current.maxIssuanceCount).toBe(0);
      expect(result.current.startDate).toBe('');
      expect(result.current.endDate).toBe('');
      expect(result.current.isEdit).toBe(false);
      expect(result.current.isValid).toBe(false);
    });

    it('모든 필수 값이 있고 startDate < endDate이면 isValid가 true이다', () => {
      const { result } = renderHook(() => usePromotionForm());

      act(() => {
        result.current.setName('여름 세일');
        result.current.setDiscountValue(5000);
        result.current.setMaxIssuanceCount(1000);
        result.current.setStartDate('2026-06-01');
        result.current.setEndDate('2026-06-30');
      });

      expect(result.current.isValid).toBe(true);
    });

    it('startDate가 endDate보다 크거나 같으면 isValid가 false이다', () => {
      const { result } = renderHook(() => usePromotionForm());

      act(() => {
        result.current.setName('세일');
        result.current.setDiscountValue(5000);
        result.current.setMaxIssuanceCount(1000);
        result.current.setStartDate('2026-06-30');
        result.current.setEndDate('2026-06-01');
      });

      expect(result.current.isValid).toBe(false);
    });

    it('discountValue가 0이면 isValid가 false이다', () => {
      const { result } = renderHook(() => usePromotionForm());

      act(() => {
        result.current.setName('세일');
        result.current.setDiscountValue(0);
        result.current.setMaxIssuanceCount(100);
        result.current.setStartDate('2026-06-01');
        result.current.setEndDate('2026-06-30');
      });

      expect(result.current.isValid).toBe(false);
    });

    it('handleSubmit 호출 시 createPromotion을 실행하고 상세로 이동한다', async () => {
      const { result } = renderHook(() => usePromotionForm());

      act(() => {
        result.current.setName(' 여름 세일 ');
        result.current.setDescription(' 여름 할인 ');
        result.current.setDiscountValue(5000);
        result.current.setMaxDiscountAmount(10000);
        result.current.setMaxIssuanceCount(1000);
        result.current.setStartDate('2026-06-01');
        result.current.setEndDate('2026-06-30');
      });

      await act(async () => {
        await result.current.handleSubmit(createEvent());
      });

      expect(mockCreateMutate).toHaveBeenCalledWith({
        name: '여름 세일',
        description: '여름 할인',
        discountType: 'FIXED',
        discountValue: 5000,
        maxDiscountAmount: 10000,
        maxIssuanceCount: 1000,
        startDate: '2026-06-01T00:00:00.000Z',
        endDate: '2026-06-30T23:59:59.999Z',
      });
      expect(mockPush).toHaveBeenCalledWith('/promotions/new-1');
    });

    it('유효하지 않은 상태에서는 mutation을 호출하지 않는다', async () => {
      const { result } = renderHook(() => usePromotionForm());

      await act(async () => {
        await result.current.handleSubmit(createEvent());
      });

      expect(mockCreateMutate).not.toHaveBeenCalled();
      expect(mockUpdateMutate).not.toHaveBeenCalled();
    });

    it('mutation 실패 시 error 상태를 설정한다', async () => {
      mockCreateMutate.mockRejectedValueOnce(new Error('생성 실패'));

      const { result } = renderHook(() => usePromotionForm());

      act(() => {
        result.current.setName('세일');
        result.current.setDiscountValue(5000);
        result.current.setMaxIssuanceCount(1000);
        result.current.setStartDate('2026-06-01');
        result.current.setEndDate('2026-06-30');
      });

      await act(async () => {
        await result.current.handleSubmit(createEvent());
      });

      await waitFor(() => {
        expect(result.current.error).toBe('생성 실패');
      });
    });
  });

  describe('수정 모드', () => {
    const promotion = {
      promotionId: 'p1',
      name: '기존 세일',
      description: '기존 설명',
      discountType: 'PERCENTAGE' as const,
      discountValue: 10,
      maxDiscountAmount: 20000,
      maxIssuanceCount: 500,
      issuedCount: 100,
      startDate: '2026-06-01T00:00:00Z',
      endDate: '2026-06-30T23:59:59Z',
      status: 'ACTIVE' as const,
      createdAt: '2026-05-01T00:00:00Z',
      updatedAt: '2026-05-15T00:00:00Z',
    };

    it('기존 프로모션 데이터로 초기화된다', () => {
      const { result } = renderHook(() => usePromotionForm(promotion));

      expect(result.current.name).toBe('기존 세일');
      expect(result.current.description).toBe('기존 설명');
      expect(result.current.discountType).toBe('PERCENTAGE');
      expect(result.current.discountValue).toBe(10);
      expect(result.current.startDate).toBe('2026-06-01');
      expect(result.current.endDate).toBe('2026-06-30');
      expect(result.current.isEdit).toBe(true);
      expect(result.current.isValid).toBe(true);
    });

    it('handleSubmit 호출 시 updatePromotion을 실행한다', async () => {
      const { result } = renderHook(() => usePromotionForm(promotion));

      act(() => {
        result.current.setName('수정된 세일');
      });

      await act(async () => {
        await result.current.handleSubmit(createEvent());
      });

      expect(mockUpdateMutate).toHaveBeenCalledWith({
        promotionId: 'p1',
        data: expect.objectContaining({ name: '수정된 세일' }),
      });
      expect(mockPush).toHaveBeenCalledWith('/promotions/p1');
    });
  });
});
