/**
 * 아티스트 굿즈 선택 (`ADR-MONO-079` D3 · `TASK-MONO-749`) — 순수 선택 함수.
 *
 * 🔵 실제 번들 시드 두 벌을 같이 읽는다: 팬 아티스트는 `fan.json`, 상품은 스토어의 `store.json`.
 *    한쪽만 읽으면 두 프로젝트가 따로 바뀔 때 못 잡는다(`TASK-MONO-739` AC-2 와 같은 규율).
 * 🔵 굿즈 시드는 아직 없다(`TASK-MONO-739` 가 이 필드 위에서 시드한다) — 그래서 「A 의 굿즈」·「B 의 굿즈」는
 *    실제 저장본 상품을 복제해 `collectionRef` 만 바꾼 것이다. 모양은 진짜 `PublicProduct` 그대로다.
 */
import { describe, it, expect } from 'vitest';
import {
  bundledEnvelope,
  type FanPublicData,
  type PublicProduct,
  type StorePublicData,
} from '@demo/public-data';
import fanJson from '@demo/public-data/snapshots/fan.json';
import storeJson from '@demo/public-data/snapshots/store.json';
import { artistGoods } from '../lib/select';

const fan = bundledEnvelope('fan', fanJson).data as FanPublicData;
const store = bundledEnvelope('store', storeJson).data as StorePublicData;
const ids = (ps: { id: string }[]) => ps.map((p) => p.id);

const [artistA, artistB] = fan.artists;

function goodsOf(artistId: string, base: PublicProduct, suffix: string): PublicProduct {
  return { ...base, id: `${base.id}-${suffix}`, collectionRef: artistId };
}

describe('artistGoods — collectionRef 로 고른다', () => {
  it('전제: 대조군을 만들 아티스트 둘과 상품이 있다 (비공허성)', () => {
    expect(artistA).toBeDefined();
    expect(artistB).toBeDefined();
    expect(artistA.id).not.toBe(artistB.id);
    expect(store.products.length).toBeGreaterThanOrEqual(3);
  });

  it('🔴🔴 AC-1: A 의 페이지에는 A 의 굿즈만, B 의 굿즈는 안 보인다 (대조군 — 양방향)', () => {
    const [p0, p1, p2] = store.products;
    const aGoods = [goodsOf(artistA.id, p0, 'a1'), goodsOf(artistA.id, p1, 'a2')];
    const bGoods = [goodsOf(artistB.id, p2, 'b1')];
    const products = [...store.products, ...aGoods, ...bGoods];

    expect(ids(artistGoods(fan, products, artistA.id))).toEqual(ids(aGoods));
    expect(ids(artistGoods(fan, products, artistB.id))).toEqual(ids(bGoods));
  });

  it('🔴 AC-1/AC-2: collectionRef 가 null 인 상품(지금의 저장본 전부)은 어느 아티스트 페이지에도 없다', () => {
    // 필드가 생기기 전의 상품은 전부 null 이다 — 생성기가 키를 싣고 값은 null.
    expect(store.products.every((p) => p.collectionRef === null)).toBe(true);
    for (const a of fan.artists) {
      expect(artistGoods(fan, store.products, a.id)).toEqual([]);
    }
  });

  it('Edge: 팬 저장본에 없는 아티스트(보관 · 비공개)를 가리키는 상품은 팬에 안 보인다 — 상품은 그대로 남는다', () => {
    const archivedId = 'archived-artist-not-in-fan-snapshot';
    expect(fan.artists.some((a) => a.id === archivedId)).toBe(false);
    const orphan = goodsOf(archivedId, store.products[0], 'orphan');
    const products = [...store.products, orphan];

    expect(artistGoods(fan, products, archivedId)).toEqual([]);
    for (const a of fan.artists) {
      expect(ids(artistGoods(fan, products, a.id))).not.toContain(orphan.id);
    }
    // 스토어 쪽 데이터는 건드리지 않는다(선택은 읽기뿐이다).
    expect(products).toContain(orphan);
  });

  it('예명이 아니라 id 로 맞춘다 — 이름에 예명이 들어 있어도 collectionRef 가 다르면 안 고른다', () => {
    const misleading: PublicProduct = {
      ...store.products[0],
      id: 'name-says-a-but-ref-says-b',
      name: `${artistA.stageName} 응원봉`,
      collectionRef: artistB.id,
    };
    expect(ids(artistGoods(fan, [misleading], artistA.id))).toEqual([]);
    expect(ids(artistGoods(fan, [misleading], artistB.id))).toEqual([misleading.id]);
  });
});
