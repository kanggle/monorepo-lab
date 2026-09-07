/**
 * `getProduct` — 공개 저장본에서 오는 상품 상세 (ADR-MONO-070).
 *
 * 🔴🔴 이 스위트의 **핵심 단언은 «재고가 없다»** 이다. 저장본의 옵션에는 `stock` 필드가
 *    아예 없고(`PublicProductOption`), 그래서 화면 타입은 `null` 을 받는다. `null` 은
 *    "0" 이 아니라 **"모른다"** 이고, 0 으로 번역하면 화면이 «품절» 이라는 없는 사실을
 *    말한다. 여기서 그것을 지키는 단언이 두 개다: 값이 null 인가, 그리고 숫자가 아닌가.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { PublicProduct, StorePublicData } from '@demo/public-data';

const mockRead = vi.hoisted(() => vi.fn());

vi.mock('@/shared/public-data/store-snapshot', () => ({
  readStoreSnapshot: mockRead,
}));

import { getProduct } from '@/entities/product/api/get-product';

const PRODUCT: PublicProduct = {
  id: 'b0000000-0000-0000-0000-000000000001',
  name: '베이직 코튼 티셔츠',
  description: '부드러운 코튼 100%.',
  status: 'ON_SALE',
  price: 29000,
  thumbnailUrl: 'https://example.test/thumb.jpg',
  images: [
    { url: 'https://example.test/2.jpg', sortOrder: 1, isPrimary: false },
    { url: 'https://example.test/1.jpg', sortOrder: 0, isPrimary: true },
  ],
  categoryId: 'cat-a',
  options: [
    { id: 'c1', optionName: 'S', additionalPrice: 0 },
    { id: 'c2', optionName: 'XL', additionalPrice: 2000 },
  ],
  searchText: '베이직 코튼 티셔츠',
  createdAt: '2026-01-02T00:00:00Z',
};

const DATA: StorePublicData = {
  products: [PRODUCT],
  categories: [{ id: 'cat-a', name: '상의', productCount: 1 }],
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

describe('getProduct — 공개 저장본', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockRead.mockResolvedValue(snapshot());
  });

  it('백엔드 없이 상세를 만든다', async () => {
    const p = await getProduct(PRODUCT.id);

    expect(p).not.toBeNull();
    expect(p!.name).toBe('베이직 코튼 티셔츠');
    expect(p!.price).toBe(29000);
    expect(p!.description).toBe('부드러운 코튼 100%.');
    expect(p!.categoryId).toBe('cat-a');
    expect(p!.categoryName).toBe('상의');
    expect(p!.fromSnapshot).toBe(true);
    expect(p!.id).not.toMatch(/^mock-/);
  });

  it('옵션에는 재고가 없다 — null 이며, 어떤 옵션도 숫자 재고를 갖지 않는다', async () => {
    const p = await getProduct(PRODUCT.id);

    expect(p!.variants.map((v) => v.optionName)).toEqual(['S', 'XL']);
    expect(p!.variants.every((v) => v.stock === null)).toBe(true);
    expect(p!.variants.some((v) => typeof v.stock === 'number')).toBe(false);
  });

  it('옵션 추가금은 그대로 온다 (가격 축은 저장본이 안다)', async () => {
    const p = await getProduct(PRODUCT.id);
    expect(p!.variants.map((v) => v.additionalPrice)).toEqual([0, 2000]);
  });

  it('이미지를 sortOrder 순으로 정렬한다', async () => {
    const p = await getProduct(PRODUCT.id);
    expect(p!.images!.map((i) => i.url)).toEqual([
      'https://example.test/1.jpg',
      'https://example.test/2.jpg',
    ]);
    expect(p!.images![0].isPrimary).toBe(true);
  });

  it('이미지가 없으면 썸네일을, 그마저 없으면 폴백 URL 을 쓴다', async () => {
    mockRead.mockResolvedValue(
      snapshot({ ...DATA, products: [{ ...PRODUCT, images: [], thumbnailUrl: null }] }),
    );

    const p = await getProduct(PRODUCT.id);

    expect(p!.images!.length).toBeGreaterThan(0);
    expect(p!.images![0].url).toContain('placehold.co');
  });

  it('description 이 null 이면 빈 문자열이 된다 (필수 string 필드)', async () => {
    mockRead.mockResolvedValue(snapshot({ ...DATA, products: [{ ...PRODUCT, description: null }] }));
    const p = await getProduct(PRODUCT.id);
    expect(p!.description).toBe('');
  });

  it('카테고리 이름을 못 찾으면 null 로 남긴다 — id 를 이름 자리에 넣지 않는다', async () => {
    mockRead.mockResolvedValue(snapshot({ products: [PRODUCT], categories: [] }));
    const p = await getProduct(PRODUCT.id);
    expect(p!.categoryName).toBeNull();
  });

  it('저장본에 없는 id 는 null 이다', async () => {
    expect(await getProduct('없는-아이디')).toBeNull();
  });

  it('판독 실패를 삼키지 않고 그대로 전파한다', async () => {
    mockRead.mockRejectedValueOnce(new Error('snapshot boom'));
    await expect(getProduct(PRODUCT.id)).rejects.toThrow('snapshot boom');
  });
});
