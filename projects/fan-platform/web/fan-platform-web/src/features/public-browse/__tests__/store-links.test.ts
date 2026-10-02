/**
 * 🔴🔴 `TASK-MONO-739` AC-2 — 팬이 만드는 스토어 링크가 **0건 목록으로 떨어지지 않는다**.
 *
 * 이 시험은 두 프로젝트의 데이터를 **같이** 읽는다 — 그것이 이 시험의 존재 이유다:
 *   · 팬 쪽: URL 생성 함수(`shared/config/store-links.ts`)의 **출력 문자열**과 팬 저장본(`fan.json`)
 *   · 스토어 쪽: 스토어 저장본(`store.json`)과 스토어가 실제로 쓰는 질의 함수 `queryProducts`
 * 한쪽만 읽으면(예: 팬 상수만 대조) 카테고리 id 가 스토어에서 바뀌거나 예명이 바뀌는 날, 두 프로젝트가
 * 따로 초록인 채 방문자는 «상품이 없습니다» 를 본다(`ADR-MONO-077` D5 의 «대가» 부류).
 *
 * 🔵 URL 을 **스토어 페이지처럼** 푼다 — `web-store/src/app/(store)/products/page.tsx`: `q` 가 있으면
 *    `searchProducts`(sort `relevance`), 없으면 `getProducts`. 둘 다 `queryProducts` 한 곳으로 간다.
 *    page 기본 0 · size 기본 20 도 그 파일의 기본값이다.
 */
import { describe, it, expect } from 'vitest';
import {
  bundledEnvelope,
  queryProducts,
  type FanPublicData,
  type PublicProduct,
  type StorePublicData,
} from '@demo/public-data';
import fanJson from '@demo/public-data/snapshots/fan.json';
import storeJson from '@demo/public-data/snapshots/store.json';
import { artistGoods } from '../lib/select';
import {
  DEFAULT_STORE_URL,
  STORE_GOODS_CATEGORY_ID,
  storeArtistGoodsHref,
  storeBaseUrl,
  storeGoodsHref,
  storeProductHref,
} from '@/shared/config/store-links';

const fan = bundledEnvelope('fan', fanJson).data as FanPublicData;
const store = bundledEnvelope('store', storeJson).data as StorePublicData;

/** 스토어 목록 페이지가 이 URL 을 받았을 때 보여 줄 **첫 페이지**와 총 건수. */
function resolveStoreList(href: string): { content: PublicProduct[]; total: number } {
  const url = new URL(href);
  expect(url.origin).toBe(new URL(storeBaseUrl()).origin);
  expect(url.pathname).toBe('/products');
  const p = url.searchParams;
  const q = p.get('q')?.trim();
  const page = Number(p.get('page') ?? '0');
  const size = Number(p.get('size') ?? '20');
  const categoryId = p.get('categoryId') ?? undefined;
  const paged = q
    ? queryProducts(store.products, { q, categoryId, sort: 'relevance', page, size })
    : queryProducts(store.products, { categoryId, page, size });
  return { content: paged.content, total: paged.totalElements };
}

/** 스토어 상품 상세가 이 URL 을 받았을 때 그릴 상품(없으면 `notFound`). */
function resolveStoreDetail(href: string): PublicProduct | undefined {
  const url = new URL(href);
  expect(url.origin).toBe(new URL(storeBaseUrl()).origin);
  const m = /^\/products\/([^/]+)$/.exec(url.pathname);
  expect(m).not.toBeNull();
  return store.products.find((x) => x.id === decodeURIComponent(m![1]));
}

const ids = (ps: readonly { id: string }[]) => ps.map((p) => p.id).sort();

describe('AC-2 — 팬 → 스토어 링크를 스토어 저장본으로 푼다', () => {
  it('전제(비공허성): 팬 공개 아티스트 6명, 스토어 저장본에 굿즈 카테고리가 있다', () => {
    expect(fan.artists).toHaveLength(6);
    expect(store.categories.some((c) => c.id === STORE_GOODS_CATEGORY_ID)).toBe(true);
  });

  it('🔴 헤더 「굿즈샵」: 굿즈만, 1건 이상 — 그리고 굿즈 **전부**가 한 페이지에 있다', () => {
    const { content, total } = resolveStoreList(storeGoodsHref());
    expect(total).toBeGreaterThanOrEqual(1);
    expect(content.every((p) => p.categoryId === STORE_GOODS_CATEGORY_ID)).toBe(true);
    const allGoods = store.products.filter((p) => p.categoryId === STORE_GOODS_CATEGORY_ID);
    expect(ids(content)).toEqual(ids(allGoods));
  });

  for (const artist of fan.artists) {
    describe(`${artist.stageName} (${artist.id})`, () => {
      const cards = artistGoods(fan, store.products, artist.id);

      it('🔴 카드: 1장 이상이고, 카드마다 스토어 상세가 그 상품을 판매 중/품절로 그린다', () => {
        expect(cards.length).toBeGreaterThanOrEqual(1);
        for (const c of cards) {
          const detail = resolveStoreDetail(storeProductHref(c.id));
          expect(detail, `카드 ${c.id} 가 스토어 저장본에 없다`).toBeDefined();
          expect(['ON_SALE', 'SOLD_OUT']).toContain(detail!.status);
          expect(detail!.categoryId).toBe(STORE_GOODS_CATEGORY_ID);
          expect(detail!.collectionRef).toBe(artist.id);
        }
      });

      it('🔴🔴 「전체 보기」: 굿즈만, 1건 이상, 그 아티스트 굿즈만 — 카드가 고른 집합과 **같다**', () => {
        const { content, total } = resolveStoreList(storeArtistGoodsHref(artist.stageName));
        expect(total).toBeGreaterThanOrEqual(1);
        expect(content.every((p) => p.categoryId === STORE_GOODS_CATEGORY_ID)).toBe(true);
        // 🔴 예명 검색이 남의 굿즈를 섞으면(예명이 다른 굿즈 이름·설명 안에 있으면) 여기서 빨개진다.
        expect(content.every((p) => p.collectionRef === artist.id)).toBe(true);
        // 🔴 반대 방향도 — 카드에는 있는데 목록에서 빠지는 굿즈가 없다(이름이 예명을 잃으면 여기서 빨개진다).
        expect(ids(content)).toEqual(ids(cards));
      });
    });
  }
});

describe('스토어 주소 — `NEXT_PUBLIC_STORE_URL` (ADR-MONO-077 D3)', () => {
  it('미설정·빈 값 → 정본 공개 호스트', () => {
    expect(storeBaseUrl(undefined)).toBe(DEFAULT_STORE_URL);
    expect(storeBaseUrl('   ')).toBe(DEFAULT_STORE_URL);
    expect(DEFAULT_STORE_URL).toBe('https://store.hubwang.com');
  });

  it('설정값을 쓰고 끝의 `/` 를 뗀다 — 링크에 `//products` 가 생기지 않는다', () => {
    expect(storeBaseUrl('https://store.example.test/')).toBe('https://store.example.test');
    expect(storeGoodsHref('https://store.example.test')).toBe(
      `https://store.example.test/products?categoryId=${STORE_GOODS_CATEGORY_ID}`,
    );
  });

  it('예명은 URL 인코딩된다 — 스토어가 받는 `q` 는 원래 예명이다', () => {
    const href = storeArtistGoodsHref('루미', DEFAULT_STORE_URL);
    expect(href).not.toContain('루미');
    expect(new URL(href).searchParams.get('q')).toBe('루미');
  });
});
