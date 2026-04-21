import { render, screen } from '@testing-library/react';
import { EditPromotion } from '@/features/promotion-management/components/EditPromotion';

const mockRefetch = vi.fn();
let mockUsePromotion: {
  data: unknown;
  isLoading: boolean;
  isError: boolean;
  refetch: () => void;
};

vi.mock('@/features/promotion-management/hooks/use-promotion', () => ({
  usePromotion: () => mockUsePromotion,
}));

vi.mock('@/shared/ui', () => ({
  PageLayout: Object.assign(
    ({ title, children }: { title: string; children: React.ReactNode }) => (
      <div data-testid="page-layout" data-title={title}>
        {children}
      </div>
    ),
    { Skeleton: () => <div data-testid="skeleton" /> },
  ),
}));

vi.mock('@repo/ui', () => ({
  ErrorMessage: ({ message, onRetry }: { message: string; onRetry: () => void }) => (
    <div data-testid="error-message">
      <span>{message}</span>
      <button onClick={onRetry}>다시 시도</button>
    </div>
  ),
}));

vi.mock('@/features/promotion-management/components/PromotionForm', () => ({
  PromotionForm: ({ promotion }: { promotion: unknown }) => (
    <div data-testid="promotion-form" data-promotion={JSON.stringify(promotion)} />
  ),
}));

describe('EditPromotion', () => {
  beforeEach(() => {
    mockRefetch.mockClear();
  });

  it('로딩 중이면 Skeleton을 표시한다', () => {
    mockUsePromotion = {
      data: undefined,
      isLoading: true,
      isError: false,
      refetch: mockRefetch,
    };

    render(<EditPromotion promotionId="p1" />);

    expect(screen.getByTestId('skeleton')).toBeInTheDocument();
  });

  it('data가 없으면 Skeleton을 표시한다', () => {
    mockUsePromotion = {
      data: undefined,
      isLoading: false,
      isError: false,
      refetch: mockRefetch,
    };

    render(<EditPromotion promotionId="p1" />);

    expect(screen.getByTestId('skeleton')).toBeInTheDocument();
  });

  it('데이터 로드 성공 시 PageLayout 제목에 프로모션 이름을 포함한다', () => {
    mockUsePromotion = {
      data: { promotionId: 'p1', name: '여름 세일' },
      isLoading: false,
      isError: false,
      refetch: mockRefetch,
    };

    render(<EditPromotion promotionId="p1" />);

    expect(screen.getByTestId('page-layout')).toHaveAttribute(
      'data-title',
      '여름 세일 수정',
    );
    expect(screen.getByTestId('promotion-form')).toBeInTheDocument();
  });
});
