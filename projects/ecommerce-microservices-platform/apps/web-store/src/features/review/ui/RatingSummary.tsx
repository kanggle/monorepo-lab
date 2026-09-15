/** 평점 요약에 필요한 값 — 저장본 `PublicReviewSummary` 와 백엔드 `ReviewSummary` 둘 다 이 모양을 만족한다. */
export interface RatingSummaryData {
  averageRating: number;
  totalReviews: number;
  ratingDistribution: Record<string, number>;
}

interface RatingSummaryProps {
  /**
   * 🔴 서버가 **저장본에서** 계산해 넘긴다(ADR-MONO-075 D3). 이 컴포넌트는 백엔드를 부르지 않는다 —
   *    예전 판은 `useReviewSummary` 로 게이트웨이를 불러서 데모가 꺼진 동안 요약이 사라졌다.
   */
  summary: RatingSummaryData;
}

const RATING_LABELS: Record<number, string> = {
  5: '5점',
  4: '4점',
  3: '3점',
  2: '2점',
  1: '1점',
};

export function RatingSummary({ summary }: RatingSummaryProps) {
  // 🔵 리뷰가 0개면 요약을 그리지 않는다 — «0.0 / 5.0» 은 «평점이 0점» 으로 읽힌다.
  //    «아직 리뷰가 없습니다» 는 목록 쪽 빈 상태가 말한다.
  if (summary.totalReviews === 0) {
    return null;
  }

  const maxCount = Math.max(...Object.values(summary.ratingDistribution), 1);

  return (
    <div
      data-testid="rating-summary"
      style={{
        padding: 'var(--space-6)',
        background: 'var(--color-bg-secondary, #f9fafb)',
        borderRadius: 'var(--radius-lg)',
        border: '1px solid var(--color-border-light)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 'var(--space-2)' }}>
        <span
          style={{
            fontSize: 'var(--font-size-2xl, 1.5rem)',
            fontWeight: 'var(--font-weight-bold)',
          }}
        >
          {summary.averageRating.toFixed(1)}
        </span>
        <span style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)' }}>
          / 5.0 ({summary.totalReviews}개 리뷰)
        </span>
      </div>

      <div
        style={{
          marginTop: 'var(--space-4)',
          display: 'flex',
          flexDirection: 'column',
          gap: 'var(--space-2)',
        }}
      >
        {[5, 4, 3, 2, 1].map((rating) => {
          const count = summary.ratingDistribution[String(rating)] ?? 0;
          const percentage = maxCount > 0 ? (count / maxCount) * 100 : 0;

          return (
            <div
              key={rating}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 'var(--space-2)',
              }}
            >
              <span
                style={{
                  width: '32px',
                  fontSize: 'var(--font-size-sm)',
                  color: 'var(--color-text-secondary)',
                  textAlign: 'right',
                }}
              >
                {RATING_LABELS[rating]}
              </span>
              <div
                style={{
                  flex: 1,
                  height: '8px',
                  background: 'var(--color-border-light)',
                  borderRadius: 'var(--radius-full, 9999px)',
                  overflow: 'hidden',
                }}
              >
                <div
                  data-testid={`rating-bar-${rating}`}
                  style={{
                    height: '100%',
                    width: `${percentage}%`,
                    background: 'var(--color-primary)',
                    borderRadius: 'var(--radius-full, 9999px)',
                    transition: 'width 0.3s ease',
                  }}
                />
              </div>
              <span
                style={{
                  width: '28px',
                  fontSize: 'var(--font-size-xs, 0.75rem)',
                  color: 'var(--color-text-secondary)',
                  textAlign: 'right',
                }}
              >
                {count}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
