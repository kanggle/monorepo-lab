/**
 * 상품 상세 리뷰 — 저장본에서 읽는다 (ADR-MONO-074 D3 · TASK-MONO-681).
 *
 * 판독자만 mock 하고 `productReviews`(질의)는 **진짜**를 쓴다 — 요약 계산이 이 시험의 대상이다.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { PublicReview, StorePublicData } from '@demo/public-data';

const mockRead = vi.hoisted(() => vi.fn());

vi.mock('@/shared/public-data/store-snapshot', () => ({
  readStoreSnapshot: mockRead,
}));

import { getSnapshotProductReviews } from '@/features/review/api/get-snapshot-reviews';

const review = (id: string, productId: string, rating: number, createdAt: string): PublicReview => ({
  id,
  productId,
  rating,
  title: `제목-${id}`,
  content: `본문-${id}`,
  createdAt,
});

const REVIEWS: PublicReview[] = [
  review('r1', 'p1', 5, '2026-02-01T00:00:00Z'),
  review('r2', 'p1', 3, '2026-02-03T00:00:00Z'),
  review('r3', 'p2', 1, '2026-02-02T00:00:00Z'),
  review('r4', 'p1', 4, '2026-02-02T00:00:00Z'),
];

const DATA: StorePublicData = { products: [], categories: [], reviews: REVIEWS };

function snapshot(source: 'backend' | 'bundled' | 'authored') {
  return {
    data: DATA,
    envelope: { source, generatedAt: '2026-09-07T00:00:00.000Z', dataset: 'store' },
    degraded: false,
    degradedReason: null,
  };
}

describe('getSnapshotProductReviews', () => {
  beforeEach(() => {
    mockRead.mockReset();
    mockRead.mockResolvedValue(snapshot('bundled'));
  });

  it('그 상품의 리뷰만 최신순으로 돌려준다', async () => {
    const r = await getSnapshotProductReviews('p1');
    expect(r.reviews.map((x) => x.id)).toEqual(['r2', 'r4', 'r1']);
  });

  it('요약을 저장본 안에서 계산한다 — 평균·개수·별점 다섯 칸 전부', async () => {
    const r = await getSnapshotProductReviews('p1');
    expect(r.summary.totalReviews).toBe(3);
    expect(r.summary.averageRating).toBeCloseTo(4);
    expect(r.summary.ratingDistribution).toEqual({ '1': 0, '2': 0, '3': 1, '4': 1, '5': 1 });
  });

  it('리뷰가 없는 상품은 빈 목록과 0 요약이다 (키 다섯은 그대로 있다)', async () => {
    const r = await getSnapshotProductReviews('p-none');
    expect(r.reviews).toEqual([]);
    expect(r.summary).toEqual({
      averageRating: 0,
      totalReviews: 0,
      ratingDistribution: { '1': 0, '2': 0, '3': 0, '4': 0, '5': 0 },
    });
  });

  it('저장본 캐시의 배열을 제자리 정렬하지 않는다', async () => {
    const before = REVIEWS.map((x) => x.id);
    await getSnapshotProductReviews('p1');
    expect(REVIEWS.map((x) => x.id)).toEqual(before);
  });

  describe('샘플 표시 판정 (D4)', () => {
    it.each([
      ['bundled', true],
      ['authored', true],
      ['backend', false],
    ] as const)('source=%s → isSample=%s', async (source, expected) => {
      mockRead.mockResolvedValue(snapshot(source));
      const r = await getSnapshotProductReviews('p1');
      expect(r.isSample).toBe(expected);
    });
  });
});
