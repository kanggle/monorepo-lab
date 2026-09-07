/**
 * `searchProducts` — 공개 저장본 안에서의 검색 (ADR-MONO-070 § D3.2).
 *
 * 🔴 요구가 둘이고 둘 다 여기서 재진다:
 *    ① 실제 스키마의 검색·필터·정렬 **의미를 보존**한다(`sort` 값 집합, 패싯 규칙).
 *    ② 저장 범위 밖 결과를 «전체 데이터에 없음» 으로 오인시키지 않는다(`corpusSize`).
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { PublicProduct, StorePublicData } from '@demo/public-data';

const mockRead = vi.hoisted(() => vi.fn());

vi.mock('@/shared/public-data/store-snapshot', () => ({
  readStoreSnapshot: mockRead,
}));

import { searchProducts } from '@/features/search/api/search-products';

function p(
  id: string,
  name: string,
  price: number,
  categoryId: string,
  createdAt: string,
): PublicProduct {
  return {
    id,
    name,
    description: null,
    status: 'ON_SALE',
    price,
    thumbnailUrl: `https://example.test/${id}.jpg`,
    images: [],
    categoryId,
    options: [],
    searchText: `${name} ${categoryId}`.toLowerCase(),
    createdAt,
  };
}

const PRODUCTS = [
  p('p1', '노트북 프로', 2_000_000, 'cat-pc', '2026-01-01T00:00:00Z'),
  p('p2', '게이밍 노트북', 1_500_000, 'cat-pc', '2026-02-01T00:00:00Z'),
  p('p3', '무선 마우스', 30_000, 'cat-acc', '2026-03-01T00:00:00Z'),
];

const DATA: StorePublicData = {
  products: PRODUCTS,
  categories: [
    { id: 'cat-pc', name: '노트북', productCount: 2 },
    { id: 'cat-acc', name: '주변기기', productCount: 1 },
  ],
};

function snapshot(data: StorePublicData = DATA) {
  return {
    data,
    envelope: {
      schemaVersion: 1,
      dataset: 'store' as const,
      dataVersion: 'v1',
      generatedAt: '2026-09-01T00:00:00.000Z',
      source: 'bundled' as const,
      origin: 'test',
      coverage: {},
      collectionStatus: {},
      data,
    },
    degraded: false,
    degradedReason: null,
  };
}

describe('searchProducts — 공개 저장본', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockRead.mockResolvedValue(snapshot());
  });

  it('백엔드 없이 검색어로 좁힌다', async () => {
    const r = await searchProducts({ q: '노트북', page: 0, size: 10 });

    expect(r.query).toBe('노트북');
    expect(r.content.map((i) => i.name)).toEqual(['노트북 프로', '게이밍 노트북']);
    expect(r.totalElements).toBe(2);
    expect(r.corpusSize).toBe(3);
  });

  it('relevance 는 질의어가 이름 앞쪽일수록 먼저다 (점수를 꾸며내지 않는다)', async () => {
    const r = await searchProducts({ q: '노트북', sort: 'relevance' });

    expect(r.content[0].name).toBe('노트북 프로');
    // 🔴 저장본은 점수를 못 매긴다 — 0 은 «점수 없음» 이고, 그리는 화면도 없다.
    expect(r.content.every((i) => i.score === 0)).toBe(true);
  });

  it.each([
    ['price_asc', [1_500_000, 2_000_000]],
    ['price_desc', [2_000_000, 1_500_000]],
  ] as const)('%s 정렬', async (sort, expected) => {
    const r = await searchProducts({ q: '노트북', sort });
    expect(r.content.map((i) => i.price)).toEqual(expected);
  });

  it('newest 정렬', async () => {
    const r = await searchProducts({ q: '노트북', sort: 'newest' });
    expect(r.content.map((i) => i.name)).toEqual(['게이밍 노트북', '노트북 프로']);
  });

  it('카테고리 필터를 적용한다', async () => {
    const r = await searchProducts({ q: '', categoryId: 'cat-acc' });
    expect(r.content.map((i) => i.name)).toEqual(['무선 마우스']);
  });

  it('가격 범위 필터를 적용한다', async () => {
    const r = await searchProducts({ q: '', minPrice: 100_000, maxPrice: 1_600_000 });
    expect(r.content.map((i) => i.name)).toEqual(['게이밍 노트북']);
  });

  it('패싯은 카테고리 조건을 뺀 채로 세고 표시명을 붙인다', async () => {
    const r = await searchProducts({ q: '', categoryId: 'cat-acc' });

    expect(r.facets.categories).toEqual([
      { id: 'cat-pc', name: '노트북', count: 2 },
      { id: 'cat-acc', name: '주변기기', count: 1 },
    ]);
  });

  it('가격 버킷은 비운다 — 백엔드와 다른 규칙을 지어내지 않는다', async () => {
    const r = await searchProducts({ q: '노트북' });
    expect(r.facets.priceRanges).toEqual([]);
  });

  describe('페이지 경계', () => {
    it('size 만큼 자르고 totalElements 는 전체를 말한다', async () => {
      const r = await searchProducts({ q: '', page: 0, size: 2 });
      expect(r.content).toHaveLength(2);
      expect(r.totalElements).toBe(3);
    });

    it('마지막 페이지', async () => {
      const r = await searchProducts({ q: '', page: 1, size: 2 });
      expect(r.content).toHaveLength(1);
    });

    it('범위 밖 페이지는 비지만 모집단 정보는 남는다', async () => {
      const r = await searchProducts({ q: '', page: 50, size: 2 });
      expect(r.content).toHaveLength(0);
      expect(r.totalElements).toBe(3);
      expect(r.corpusSize).toBe(3);
    });
  });

  describe('0건의 두 얼굴', () => {
    it('저장본은 찼는데 못 찾은 경우 — corpusSize > 0', async () => {
      const r = await searchProducts({ q: '존재하지않는상품' });
      expect(r.totalElements).toBe(0);
      expect(r.corpusSize).toBe(3);
    });

    it('저장본 자체가 빈 경우 — corpusSize === 0', async () => {
      mockRead.mockResolvedValue(snapshot({ products: [], categories: [] }));
      const r = await searchProducts({ q: '노트북' });
      expect(r.totalElements).toBe(0);
      expect(r.corpusSize).toBe(0);
    });
  });

  it('모르는 status 값은 «필터 없음» 이지 «0건» 이 아니다', async () => {
    const r = await searchProducts({ q: '', status: 'ACTIVE' });
    expect(r.totalElements).toBe(3);
  });

  it('판독 실패를 삼키지 않고 그대로 전파한다', async () => {
    mockRead.mockRejectedValueOnce(new Error('snapshot boom'));
    await expect(searchProducts({ q: '노트북' })).rejects.toThrow('snapshot boom');
  });
});
