/**
 * 평점 요약 — props 로 받은 요약을 그린다 (ADR-MONO-075 D3 · TASK-MONO-681).
 *
 * 🔴 예전 판은 `productId` 를 받아 `useReviewSummary` 로 게이트웨이를 불렀고, 그래서 로딩·에러 상태가
 *    있었다. 요약은 이제 서버가 **저장본에서** 계산해 넘기므로 그 두 상태는 존재하지 않는다 — 대신
 *    «백엔드를 부르지 않는다» 와 «0개면 안 그린다» 를 잰다.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';

vi.mock('@/features/review/api/review-api', () => ({
  getProductReviewSummary: vi.fn(),
}));

import { getProductReviewSummary } from '@/features/review/api/review-api';
import { RatingSummary, type RatingSummaryData } from '@/features/review/ui/RatingSummary';

const SUMMARY: RatingSummaryData = {
  averageRating: 4.2,
  totalReviews: 15,
  ratingDistribution: { '1': 1, '2': 0, '3': 2, '4': 5, '5': 7 },
};

describe('RatingSummary', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('평균 평점과 총 리뷰 수를 표시한다', () => {
    render(<RatingSummary summary={SUMMARY} />);

    expect(screen.getByText('4.2')).toBeInTheDocument();
    expect(screen.getByText(/15개 리뷰/)).toBeInTheDocument();
  });

  it('평균은 소수 첫째 자리로 표시한다 (저장본은 반올림하지 않은 값을 넘긴다)', () => {
    render(<RatingSummary summary={{ ...SUMMARY, averageRating: 13 / 3 }} />);

    expect(screen.getByText('4.3')).toBeInTheDocument();
  });

  it('별점 분포를 다섯 칸 전부 표시한다', () => {
    render(<RatingSummary summary={SUMMARY} />);

    for (const label of ['5점', '4점', '3점', '2점', '1점']) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }
    expect(screen.getByText('7')).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();
    expect(screen.getByText('0')).toBeInTheDocument();
  });

  it('가장 많은 별점의 막대가 가득 찬다', () => {
    render(<RatingSummary summary={SUMMARY} />);

    expect(screen.getByTestId('rating-bar-5')).toHaveStyle({ width: '100%' });
    expect(screen.getByTestId('rating-bar-2')).toHaveStyle({ width: '0%' });
  });

  it('리뷰가 0개면 아무것도 그리지 않는다 («0.0 / 5.0» 은 «평점 0점» 으로 읽힌다)', () => {
    const { container } = render(
      <RatingSummary
        summary={{
          averageRating: 0,
          totalReviews: 0,
          ratingDistribution: { '1': 0, '2': 0, '3': 0, '4': 0, '5': 0 },
        }}
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it('🔴 요약을 읽으러 백엔드를 부르지 않는다', () => {
    render(<RatingSummary summary={SUMMARY} />);

    expect(vi.mocked(getProductReviewSummary)).not.toHaveBeenCalled();
  });
});
