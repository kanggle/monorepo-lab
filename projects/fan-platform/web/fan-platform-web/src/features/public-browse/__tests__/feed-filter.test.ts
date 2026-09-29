/**
 * 공개 피드 필터 (TASK-FAN-FE-025) — 순수 선택 함수.
 *
 * 🔴 기대값을 숫자로 얼리지 않는다. 시드가 재발행되면 숫자는 바뀌고, 그때 이 파일이
 *    «제품이 고장났다» 가 아니라 «내가 수치를 얼려 두었다» 로 빨개진다(TASK-MONO-638 의 교훈).
 *    대신 **실제 번들 시드**에서 기대값을 계산하고, 각 필터가 실제로 좁히는지(0 < n < 전체)를
 *    비공허성 단언으로 따로 묻는다.
 */
import { describe, it, expect } from 'vitest';
import { bundledEnvelope, type FanPublicData } from '@demo/public-data';
import bundledJson from '@demo/public-data/snapshots/fan.json';
import {
  feedPosts,
  filterFeedPosts,
  resolveFeedFilter,
  isFeedFiltered,
  feedHref,
  FEED_VISIBILITIES,
  NO_FEED_FILTER,
} from '../lib/select';

const data = bundledEnvelope('fan', bundledJson).data as FanPublicData;
const all = feedPosts(data);
const ids = (posts: { id: string }[]) => posts.map((p) => p.id);

describe('filterFeedPosts — 기본값', () => {
  it('🔴🔴 필터가 없으면 feedPosts 와 원소·순서가 같다 (잠긴 글 포함)', () => {
    expect(all.length).toBeGreaterThan(0);
    expect(all.some((p) => p.locked)).toBe(true); // 비공허성 — 잠긴 글이 있어야 이 칸이 뜻을 갖는다
    expect(ids(filterFeedPosts(data, NO_FEED_FILTER))).toEqual(ids(all));
  });
});

describe('filterFeedPosts — 축별', () => {
  it('아티스트: 그 아티스트의 글만, 순서는 피드와 같다', () => {
    const artist = data.artists.find((a) => all.some((p) => p.artistId === a.id))!;
    const got = filterFeedPosts(data, { ...NO_FEED_FILTER, artistId: artist.id });
    const expected = all.filter((p) => p.artistId === artist.id);
    expect(got.length).toBeGreaterThan(0);
    expect(got.length).toBeLessThan(all.length);
    expect(ids(got)).toEqual(ids(expected));
  });

  it.each(FEED_VISIBILITIES)('공개 범위 %s: 그 범위의 글만', (visibility) => {
    const got = filterFeedPosts(data, { ...NO_FEED_FILTER, visibility });
    expect(ids(got)).toEqual(ids(all.filter((p) => p.visibility === visibility)));
    expect(got.every((p) => p.visibility === visibility)).toBe(true);
  });

  it('🔴 «공개» 를 직접 고르면 잠긴 글이 빠진다 — 그리고 실제로 좁혀진다', () => {
    const got = filterFeedPosts(data, { ...NO_FEED_FILTER, visibility: 'PUBLIC' });
    expect(got.length).toBeGreaterThan(0);
    expect(got.length).toBeLessThan(all.length);
    expect(got.some((p) => p.locked)).toBe(false);
  });

  it('검색: 제목의 일부로 찾는다 (대소문자 무시)', () => {
    const target = all[0];
    const needle = target.title.slice(0, Math.min(3, target.title.length)).toUpperCase();
    const got = filterFeedPosts(data, { ...NO_FEED_FILTER, q: needle });
    expect(ids(got)).toContain(target.id);
    const lower = needle.toLowerCase();
    expect(
      got.every((p) => `${p.title} ${p.artistStageName}`.toLowerCase().includes(lower)),
    ).toBe(true);
  });

  it('검색: 아티스트 이름으로도 찾는다', () => {
    const name = all[0].artistStageName;
    const got = filterFeedPosts(data, { ...NO_FEED_FILTER, q: name });
    expect(ids(got)).toEqual(
      ids(all.filter((p) => `${p.title} ${p.artistStageName}`.toLowerCase().includes(name.toLowerCase()))),
    );
    expect(got.length).toBeGreaterThan(0);
  });

  it('검색: 토큰은 AND — 두 토큰이 모두 있어야 걸린다', () => {
    const target = all[0];
    const got = filterFeedPosts(data, { ...NO_FEED_FILTER, q: `${target.artistStageName} zzz없는토큰zzz` });
    expect(got).toEqual([]);
  });
});

describe('filterFeedPosts — 조합', () => {
  it('아티스트 × 공개 범위는 AND 다', () => {
    const artistId = all[0].artistId;
    const got = filterFeedPosts(data, { ...NO_FEED_FILTER, artistId, visibility: 'PUBLIC' });
    expect(ids(got)).toEqual(
      ids(all.filter((p) => p.artistId === artistId && p.visibility === 'PUBLIC')),
    );
  });
});

describe('resolveFeedFilter — 적용되는 값만 남긴다', () => {
  it('유효한 값은 그대로', () => {
    const artistId = data.artists[0].id;
    expect(resolveFeedFilter(data, { artist: artistId, visibility: 'PREMIUM', q: '  루미 ' })).toEqual({
      artistId,
      visibility: 'PREMIUM',
      q: '루미',
    });
  });

  it('🔴 저장본에 없는 아티스트·모르는 공개 범위·공백뿐인 검색어는 무시', () => {
    const f = resolveFeedFilter(data, { artist: 'no-such-artist', visibility: 'SECRET', q: '   ' });
    expect(f).toEqual(NO_FEED_FILTER);
    expect(isFeedFiltered(f)).toBe(false);
  });
});

describe('feedHref — 필터를 보존한 링크', () => {
  it('필터 없는 첫 페이지는 정확히 «/»', () => {
    expect(feedHref(NO_FEED_FILTER, 0)).toBe('/');
    expect(feedHref(NO_FEED_FILTER, 2)).toBe('/?page=2');
  });

  it('🔴 걸린 필터를 모두 싣고, 첫 페이지면 page 를 뺀다', () => {
    const filter = { artistId: 'a1', visibility: 'MEMBERS_ONLY' as const, q: '새 앨범' };
    const url = new URL(feedHref(filter, 1), 'http://x');
    expect(url.pathname).toBe('/');
    expect(url.searchParams.get('artist')).toBe('a1');
    expect(url.searchParams.get('visibility')).toBe('MEMBERS_ONLY');
    expect(url.searchParams.get('q')).toBe('새 앨범');
    expect(url.searchParams.get('page')).toBe('1');
    expect(new URL(feedHref(filter, 0), 'http://x').searchParams.has('page')).toBe(false);
  });
});
