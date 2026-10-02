/**
 * 아티스트 굿즈 선택 (`ADR-MONO-079` D3 · `TASK-MONO-749`) — 순수 선택 함수.
 *
 * 🔵 실제 번들 시드 두 벌을 같이 읽는다: 팬 아티스트는 `fan.json`, 상품은 스토어의 `store.json`.
 *    한쪽만 읽으면 두 프로젝트가 따로 바뀔 때 못 잡는다(`TASK-MONO-739` AC-2 와 같은 규율).
 * 🔵 합성 칸의 「A 의 굿즈」·「B 의 굿즈」는 실제 저장본의 **굿즈가 아닌** 상품(`collectionRef === null`)을 복제해
 *    `collectionRef` 만 바꾼 것이다. 모양은 진짜 `PublicProduct` 그대로다. 실제 굿즈 시드(`TASK-MONO-739`)에 대한
 *    대조는 `store-links.test.ts`(AC-2)가 한다 — 여기 합성 칸은 그 시드와 독립으로 선택 함수의 성질만 잰다.
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
/** 어느 아티스트 컬렉션에도 속하지 않는 실제 상품 — 합성 굿즈의 바탕(실제 굿즈와 섞이지 않게). */
const plain = store.products.filter((p) => p.collectionRef === null);

const [artistA, artistB] = fan.artists;

function goodsOf(artistId: string, base: PublicProduct, suffix: string): PublicProduct {
  return { ...base, id: `${base.id}-${suffix}`, collectionRef: artistId };
}

describe('artistGoods — collectionRef 로 고른다', () => {
  it('전제: 대조군을 만들 아티스트 둘과 상품이 있다 (비공허성)', () => {
    expect(artistA).toBeDefined();
    expect(artistB).toBeDefined();
    expect(artistA.id).not.toBe(artistB.id);
    expect(plain.length).toBeGreaterThanOrEqual(3);
  });

  it('🔴🔴 AC-1: A 의 페이지에는 A 의 굿즈만, B 의 굿즈는 안 보인다 (대조군 — 양방향)', () => {
    const [p0, p1, p2] = plain;
    const aGoods = [goodsOf(artistA.id, p0, 'a1'), goodsOf(artistA.id, p1, 'a2')];
    const bGoods = [goodsOf(artistB.id, p2, 'b1')];
    const products = [...plain, ...aGoods, ...bGoods];

    expect(ids(artistGoods(fan, products, artistA.id))).toEqual(ids(aGoods));
    expect(ids(artistGoods(fan, products, artistB.id))).toEqual(ids(bGoods));
  });

  it('🔴 AC-1/AC-2: collectionRef 가 null 인 상품(굿즈가 아닌 기존 카탈로그)은 어느 아티스트 페이지에도 없다', () => {
    // 필드가 생기기 전의 상품은 전부 null 이다 — 생성기가 키를 싣고 값은 null.
    // 🔵 TASK-MONO-739 가 굿즈를 시드한 뒤로 저장본에는 null 과 non-null 이 **둘 다** 있다 — 대조군이 실물이 됐다.
    expect(plain.length).toBeGreaterThan(0); // 비공허성
    expect(store.products.some((p) => p.collectionRef !== null)).toBe(true);
    for (const a of fan.artists) {
      const picked = ids(artistGoods(fan, store.products, a.id));
      for (const p of plain) expect(picked).not.toContain(p.id);
    }
  });

  it('Edge: 팬 저장본에 없는 아티스트(보관 · 비공개)를 가리키는 상품은 팬에 안 보인다 — 상품은 그대로 남는다', () => {
    const archivedId = 'archived-artist-not-in-fan-snapshot';
    expect(fan.artists.some((a) => a.id === archivedId)).toBe(false);
    const orphan = goodsOf(archivedId, plain[0], 'orphan');
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
      ...plain[0],
      id: 'name-says-a-but-ref-says-b',
      name: `${artistA.stageName} 응원봉`,
      collectionRef: artistB.id,
    };
    expect(ids(artistGoods(fan, [misleading], artistA.id))).toEqual([]);
    expect(ids(artistGoods(fan, [misleading], artistB.id))).toEqual([misleading.id]);
  });
});
