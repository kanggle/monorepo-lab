/**
 * 상품 상세 리뷰 영역 — props 로 받은 **저장본** 리뷰를 그린다 (ADR-MONO-075 · TASK-MONO-681).
 *
 * 🔴 «리뷰를 읽으러 백엔드에 가지 않는다» 를 말로 하지 않고 **잰다**: 리뷰 API 모듈을 mock 하고 조회
 *    함수가 한 번도 안 불렸음을 단언한다(AC-6). 예전 판은 그 조회가 실패하면 «리뷰를 불러오는데
 *    실패했습니다» 를 그렸고, 데모가 꺼진 동안 방문자가 본 것이 정확히 그것이었다.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TestQueryProvider } from './test-utils';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

vi.mock('@/shared/lib/auth-context', () => ({
  useAuth: vi.fn(),
}));

vi.mock('@/features/review/api/review-api', () => ({
  getProductReviews: vi.fn(),
  getProductReviewSummary: vi.fn(),
  createReview: vi.fn(),
  updateReview: vi.fn(),
  deleteReview: vi.fn(),
}));

vi.mock('@repo/ui', () => ({
  EmptyState: ({ message }: { message: string }) => (
    <div data-testid="empty-state">{message}</div>
  ),
}));

import { useAuth } from '@/shared/lib/auth-context';
import {
  getProductReviews,
  getProductReviewSummary,
} from '@/features/review/api/review-api';
import { ReviewList, type ReviewListItem } from '@/features/review/ui/ReviewList';

const mockUseAuth = vi.mocked(useAuth);

const REVIEWS: ReviewListItem[] = [
  {
    id: 'review-1',
    rating: 5,
    title: '아주 좋아요',
    content: '정말 만족스러운 상품입니다.',
    createdAt: '2026-04-01T10:00:00Z',
  },
  {
    id: 'review-2',
    rating: 3,
    title: '보통이에요',
    content: '가격 대비 평범합니다.',
    createdAt: '2026-03-30T10:00:00Z',
  },
];

const SUMMARY = {
  averageRating: 4,
  totalReviews: 2,
  ratingDistribution: { '1': 0, '2': 0, '3': 1, '4': 0, '5': 1 },
};

const EMPTY_SUMMARY = {
  averageRating: 0,
  totalReviews: 0,
  ratingDistribution: { '1': 0, '2': 0, '3': 0, '4': 0, '5': 0 },
};

function authAs(isAuthenticated: boolean) {
  mockUseAuth.mockReturnValue({
    isAuthenticated,
    isLoading: false,
    user: isAuthenticated ? ({ userId: 'user-1' } as never) : null,
    login: vi.fn(),
    signup: vi.fn(),
    logout: vi.fn(),
  });
}

function renderList(overrides: Partial<Parameters<typeof ReviewList>[0]> = {}) {
  return render(
    <TestQueryProvider>
      <ReviewList
        productId="product-1"
        reviews={REVIEWS}
        summary={SUMMARY}
        isSample={false}
        {...overrides}
      />
    </TestQueryProvider>,
  );
}

function manyReviews(n: number): ReviewListItem[] {
  return Array.from({ length: n }, (_, i) => ({
    id: `r-${i + 1}`,
    rating: (i % 5) + 1,
    title: `리뷰 제목 ${i + 1}`,
    content: `리뷰 본문 ${i + 1}`,
    createdAt: `2026-02-${String(28 - i).padStart(2, '0')}T00:00:00Z`,
  }));
}

describe('ReviewList — 저장본 리뷰 (ADR-MONO-075)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authAs(false);
  });

  it('받은 리뷰와 요약을 곧바로 그린다 (로딩 단계가 없다)', () => {
    renderList();

    expect(screen.getByText('상품 리뷰')).toBeInTheDocument();
    expect(screen.getByText('아주 좋아요')).toBeInTheDocument();
    expect(screen.getByText('보통이에요')).toBeInTheDocument();
    expect(screen.getByText('정말 만족스러운 상품입니다.')).toBeInTheDocument();
    expect(screen.getByTestId('rating-summary')).toHaveTextContent('(2개 리뷰)');
  });

  it('🔴 리뷰·요약을 읽으러 백엔드를 부르지 않는다 (AC-6)', () => {
    renderList();

    expect(vi.mocked(getProductReviews)).not.toHaveBeenCalled();
    expect(vi.mocked(getProductReviewSummary)).not.toHaveBeenCalled();
    // 🔵 예전 판의 실패 문구가 어떤 경로로도 나타나지 않는다.
    expect(screen.queryByText('리뷰를 불러오는데 실패했습니다.')).not.toBeInTheDocument();
  });

  it('리뷰가 없으면 빈 상태를 보이고 요약은 그리지 않는다 («0.0 / 5.0» 을 안 그린다)', () => {
    renderList({ reviews: [], summary: EMPTY_SUMMARY });

    expect(screen.getByTestId('empty-state')).toHaveTextContent('아직 리뷰가 없습니다.');
    expect(screen.queryByTestId('rating-summary')).not.toBeInTheDocument();
  });

  describe('샘플 표시 (D4)', () => {
    it('isSample 이면 「샘플 리뷰」 를 말한다', () => {
      renderList({ isSample: true });
      expect(screen.getByTestId('sample-review-notice')).toHaveTextContent(
        '샘플 리뷰 — 실제 구매자가 쓴 리뷰가 아닙니다.',
      );
    });

    it('isSample 이 아니면 말하지 않는다 (발행된 실데이터에 샘플 딱지를 붙이지 않는다)', () => {
      renderList({ isSample: false });
      expect(screen.queryByTestId('sample-review-notice')).not.toBeInTheDocument();
    });
  });

  it('비로그인 사용자에게 리뷰 작성 버튼을 표시하지 않는다', () => {
    renderList();
    expect(screen.queryByRole('button', { name: '리뷰 작성' })).not.toBeInTheDocument();
  });

  it('로그인한 사용자에게 리뷰 작성 버튼을 표시하고, 누르면 폼이 열린다 (R2)', async () => {
    authAs(true);
    const user = userEvent.setup();
    renderList();

    await user.click(screen.getByRole('button', { name: '리뷰 작성' }));

    expect(screen.getByLabelText('제목')).toBeInTheDocument();
    expect(screen.getByLabelText('내용')).toBeInTheDocument();
  });

  it('🔴 로그인해도 수정·삭제 버튼이 없다 — 저장본에 작성자가 없어 «본인» 을 판정할 수 없다 (R3)', () => {
    authAs(true);
    renderList();

    expect(screen.getByText('아주 좋아요')).toBeInTheDocument();
    expect(screen.queryByText('수정')).not.toBeInTheDocument();
    expect(screen.queryByText('삭제')).not.toBeInTheDocument();
  });

  it('작성에 성공하면 «다음 발행 때 반영» 을 알린다 — 목록에 바로 안 나오는 이유를 말한다 (R2)', async () => {
    const { createReview } = await import('@/features/review/api/review-api');
    vi.mocked(createReview).mockResolvedValueOnce({ reviewId: 'new-1' });
    authAs(true);
    const user = userEvent.setup();
    renderList();

    await user.click(screen.getByRole('button', { name: '리뷰 작성' }));
    await user.click(screen.getByRole('radio', { name: '4점' }));
    await user.type(screen.getByLabelText('제목'), '새로 쓴 리뷰');
    await user.type(screen.getByLabelText('내용'), '배송이 빨랐어요');
    // 폼이 열리면 토글 버튼은 사라지고, 같은 이름의 **제출** 버튼만 남는다.
    await user.click(screen.getByRole('button', { name: '리뷰 작성' }));

    expect(await screen.findByTestId('review-submitted-notice')).toHaveTextContent(
      '공개 목록에는 다음 발행 때 반영됩니다',
    );
    expect(vi.mocked(createReview).mock.calls[0][0]).toEqual({
      productId: 'product-1',
      rating: 4,
      title: '새로 쓴 리뷰',
      content: '배송이 빨랐어요',
    });
    // 🔴 방금 쓴 리뷰는 목록에 **없다** — 목록은 저장본이고 아직 발행 전이다. 그래서 위 알림이 필요하다.
    expect(screen.queryByText('새로 쓴 리뷰')).not.toBeInTheDocument();
  });

  describe('페이지네이션 — 저장본 배열 안에서 돈다', () => {
    it('10개 이하면 페이지네이션을 그리지 않는다', () => {
      renderList({ reviews: manyReviews(10) });
      expect(screen.queryByLabelText('다음 페이지')).not.toBeInTheDocument();
    });

    it('11개 이상이면 페이지를 나누고, 다음을 누르면 다음 묶음을 그린다', async () => {
      const user = userEvent.setup();
      renderList({ reviews: manyReviews(12) });

      expect(screen.getByText('1 / 2')).toBeInTheDocument();
      expect(screen.getByLabelText('이전 페이지')).toBeDisabled();
      expect(screen.getByText('리뷰 제목 1')).toBeInTheDocument();
      expect(screen.queryByText('리뷰 제목 11')).not.toBeInTheDocument();

      await user.click(screen.getByLabelText('다음 페이지'));

      expect(screen.getByText('2 / 2')).toBeInTheDocument();
      expect(screen.getByText('리뷰 제목 11')).toBeInTheDocument();
      expect(screen.queryByText('리뷰 제목 1')).not.toBeInTheDocument();
      // 🔵 페이지를 넘겨도 조회는 없다 — 넘기는 것은 배열 자르기다.
      expect(vi.mocked(getProductReviews)).not.toHaveBeenCalled();
    });
  });
});
