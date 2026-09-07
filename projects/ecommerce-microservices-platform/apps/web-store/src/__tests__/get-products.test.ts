/**
 * `getProducts` — 공개 저장본에서 오는 상품 목록 (ADR-MONO-070).
 *
 * 🔴 이 스위트는 **백엔드를 한 번도 안 부른다.** 그것이 곧 판정이다: `@/shared/config/api`
 *    나 `@repo/api-client` 를 mock 조차 하지 않는데 전부 초록이면, 그 경로가 애초에 안 도는
 *    것이다(부르는 코드가 남아 있었다면 axios 가 fetch 를 때리고 그 자리에서 죽는다).
 *
 * 🔴 TASK-FE-061 이 지운 «조용한 목 폴백» 과 헷갈리지 마라 — 근거는
 *    `entities/product/api/get-products.ts` 머리 주석. 여기서 그 축을 지키는 단언은
 *    **"id 가 mock-* 가 아니다"** 와 **"에러를 삼키지 않는다"** 두 개다.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import type { PublicProduct, StorePublicData } from '@demo/public-data';

const mockRead = vi.hoisted(() => vi.fn());

vi.mock('@/shared/public-data/store-snapshot', () => ({
  readStoreSnapshot: mockRead,
}));

import { getProducts } from '@/entities/product/api/get-products';

function product(over: Partial<PublicProduct> & Pick<PublicProduct, 'id' | 'name'>): PublicProduct {
  return {
    description: null,
    status: 'ON_SALE',
    price: 10000,
    thumbnailUrl: 'https://example.test/t.jpg',
    images: [],
    categoryId: 'cat-a',
    options: [],
    searchText: `${over.name} ${over.categoryId ?? 'cat-a'}`.toLowerCase(),
    createdAt: '2026-01-01T00:00:00Z',
    ...over,
  };
}

const PRODUCTS: PublicProduct[] = [
  product({ id: 'b0000000-0000-0000-0000-000000000001', name: '알파 티셔츠', price: 30000, categoryId: 'cat-a', createdAt: '2026-01-01T00:00:00Z', searchText: '알파 티셔츠 cat-a' }),
  product({ id: 'b0000000-0000-0000-0000-000000000002', name: '베타 청바지', price: 10000, categoryId: 'cat-b', createdAt: '2026-03-01T00:00:00Z', searchText: '베타 청바지 cat-b' }),
  product({ id: 'b0000000-0000-0000-0000-000000000003', name: '감마 자켓', price: 50000, categoryId: 'cat-a', createdAt: '2026-02-01T00:00:00Z', searchText: '감마 자켓 cat-a' }),
];

const DATA: StorePublicData = {
  products: PRODUCTS,
  categories: [
    { id: 'cat-a', name: '상의', productCount: 2 },
    { id: 'cat-b', name: '하의', productCount: 1 },
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
      coverage: { products: data.products.length },
      collectionStatus: { products: 'ok' as const },
      data,
    },
    degraded: false,
    degradedReason: null,
  };
}

describe('getProducts — 공개 저장본', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockRead.mockResolvedValue(snapshot());
  });
  afterEach(() => vi.restoreAllMocks());

  it('백엔드 없이 저장본에서 목록을 만든다', async () => {
    const result = await getProducts({ page: 0, size: 10 });

    expect(result.totalElements).toBe(3);
    expect(result.corpusSize).toBe(3);
    expect(result.content.map((p) => p.name)).toEqual(['감마 자켓', '베타 청바지', '알파 티셔츠']);
  });

  it('썸네일이 없으면 폴백 이미지 URL 을 채운다 (필수 string 필드)', async () => {
    mockRead.mockResolvedValue(
      snapshot({ ...DATA, products: [product({ id: 'x', name: '썸네일없음', thumbnailUrl: null })] }),
    );

    const result = await getProducts();

    expect(result.content[0].thumbnailUrl).toContain('placehold.co');
  });

  it('저장본의 id 를 그대로 보존한다 — mock-* 로 대체하지 않는다', async () => {
    const result = await getProducts();

    for (const item of result.content) {
      expect(item.id).not.toMatch(/^mock-/);
    }
    expect(result.content.map((p) => p.id).sort()).toEqual(PRODUCTS.map((p) => p.id).sort());
  });

  describe('정렬 — 백엔드 SearchSortOrder 와 같은 값 집합', () => {
    it('price_asc 는 가격 오름차순', async () => {
      const r = await getProducts({ sort: 'price_asc' });
      expect(r.content.map((p) => p.price)).toEqual([10000, 30000, 50000]);
    });

    it('price_desc 는 가격 내림차순', async () => {
      const r = await getProducts({ sort: 'price_desc' });
      expect(r.content.map((p) => p.price)).toEqual([50000, 30000, 10000]);
    });

    it('newest 는 createdAt 내림차순', async () => {
      const r = await getProducts({ sort: 'newest' });
      expect(r.content.map((p) => p.name)).toEqual(['베타 청바지', '감마 자켓', '알파 티셔츠']);
    });

    it('relevance 는 질의어가 이름 앞쪽일수록 먼저 (동점은 이름순 안정 정렬)', async () => {
      const r = await getProducts({ q: '티셔츠', sort: 'relevance' });
      expect(r.content.map((p) => p.name)).toEqual(['알파 티셔츠']);
    });

    it('알 수 없는 sort 값은 기본(relevance)으로 떨어지고 0건이 되지 않는다', async () => {
      const r = await getProducts({ sort: undefined });
      expect(r.totalElements).toBe(3);
    });
  });

  describe('카테고리 필터', () => {
    it('categoryId 로 좁힌다', async () => {
      const r = await getProducts({ categoryId: 'cat-b' });
      expect(r.content.map((p) => p.name)).toEqual(['베타 청바지']);
      expect(r.totalElements).toBe(1);
      // 🔴 corpusSize 는 «질의 이전» 이라 필터와 무관하게 3 이다.
      expect(r.corpusSize).toBe(3);
    });

    it('카테고리 목록은 **현재 카테고리 조건을 뺀 채로** 센다 — 다른 카테고리로 옮겨갈 수 있어야 한다', async () => {
      const r = await getProducts({ categoryId: 'cat-b' });
      expect(r.categories).toEqual(
        expect.arrayContaining([
          { id: 'cat-a', name: '상의', count: 2 },
          { id: 'cat-b', name: '하의', count: 1 },
        ]),
      );
    });

    it('저장본에 이름이 없는 카테고리는 name 을 null 로 남긴다 (id 를 이름 자리에 넣지 않는다)', async () => {
      mockRead.mockResolvedValue(snapshot({ products: PRODUCTS, categories: [] }));
      const r = await getProducts();
      expect(r.categories.every((c) => c.name === null)).toBe(true);
    });
  });

  describe('페이지 경계', () => {
    it('첫 페이지는 size 만큼만 담고 totalElements 는 전체를 말한다', async () => {
      const r = await getProducts({ page: 0, size: 2 });
      expect(r.content).toHaveLength(2);
      expect(r.totalElements).toBe(3);
      expect(r.page).toBe(0);
      expect(r.size).toBe(2);
    });

    it('마지막 페이지는 남은 건수만 담는다', async () => {
      const r = await getProducts({ page: 1, size: 2 });
      expect(r.content).toHaveLength(1);
      expect(r.totalElements).toBe(3);
    });

    it('범위를 넘어선 페이지는 빈 content 이지만 totalElements 는 그대로다', async () => {
      const r = await getProducts({ page: 99, size: 2 });
      expect(r.content).toHaveLength(0);
      expect(r.totalElements).toBe(3);
      // 🔴 «검색 결과 없음» 으로 오인시키지 않기 위한 축이 살아 있어야 한다.
      expect(r.corpusSize).toBe(3);
    });

    it('음수 페이지는 0 으로 정규화된다', async () => {
      const r = await getProducts({ page: -3, size: 2 });
      expect(r.page).toBe(0);
      expect(r.content).toHaveLength(2);
    });
  });

  describe('빈 상태의 두 얼굴', () => {
    it('저장본이 비면 corpusSize 가 0 이다', async () => {
      mockRead.mockResolvedValue(snapshot({ products: [], categories: [] }));
      const r = await getProducts();
      expect(r.totalElements).toBe(0);
      expect(r.corpusSize).toBe(0);
    });

    it('저장본은 찼는데 조건에 안 맞으면 corpusSize 는 0 이 아니다', async () => {
      const r = await getProducts({ q: '존재하지않는상품명' });
      expect(r.totalElements).toBe(0);
      expect(r.corpusSize).toBe(3);
    });
  });

  it('status=HIDDEN 요청은 항상 0건이다 — 저장본에 HIDDEN 은 없다', async () => {
    const r = await getProducts({ status: 'HIDDEN' });
    expect(r.content).toHaveLength(0);
    expect(r.totalElements).toBe(0);
    expect(r.corpusSize).toBe(3);
  });

  it('판독 실패를 삼키지 않고 그대로 전파한다 (조용한 폴백 금지 — TASK-FE-061 축 유지)', async () => {
    mockRead.mockRejectedValueOnce(new Error('snapshot boom'));
    await expect(getProducts()).rejects.toThrow('snapshot boom');
  });
});
