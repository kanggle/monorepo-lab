/**
 * 아티스트 사진이 **실제로 그려지는가** — TASK-MONO-641
 *
 * =============================================================================
 * 🔴🔴 이 시험이 재는 축, 그리고 재지 **않는** 축
 * =============================================================================
 * `TASK-MONO-638` 은 여섯 아티스트의 `profileImageUrl` 을 채웠고 그 AC 는 닫혔다. 그런데
 * 배포된 화면에는 사진이 **한 장도 없었다** — 그 값을 읽는 컴포넌트가 없었기 때문이다.
 *
 * ⇒ 그래서 이 시험은 «컴포넌트가 그 prop 을 받는가» 를 묻지 않는다. **받아 놓고 안 그리는
 *   것**이 정확히 그 결함의 모양이고, prop 검사는 그 상태에서도 초록이다.
 *   묻는 것은 **그려진 결과에 그 주소가 있는가** 다.
 *
 * 🔴 그리고 **양방향**이다. 한쪽만 재면 다음이 통과한다:
 *      · 값→사진만 재면  → «항상 사진» 이 통과한다(null 인데 깨진 이미지를 그려도 모른다)
 *      · null→이니셜만 재면 → 638 이전 상태(아무도 안 그림)가 그대로 통과한다
 *   `null` 은 638 이전 여섯 명 전원의 상태였고 지금도 저장본이 비면 그 값이 온다.
 */
import { describe, it, expect } from 'vitest';
import { render, fireEvent } from '@testing-library/react';
import type { PublicArtist } from '@demo/public-data';
import bundled from '@demo/public-data/snapshots/fan.json';
import { PublicArtistCard } from '../ui/PublicArtistCard';
import { PublicArtistProfile } from '../ui/PublicArtistProfile';

const PHOTO = 'https://images.unsplash.com/photo-test-641?w=400&h=400';

const withPhoto: PublicArtist = {
  id: '0199de80-0000-7000-8000-0000000641a1',
  stageName: '사진있음',
  artistType: 'SOLO',
  agency: 'Aurora Entertainment',
  debutDate: '2024-01-01',
  bio: '사진이 있는 아티스트',
  profileImageUrl: PHOTO,
  searchText: '사진있음',
} as PublicArtist;

const withoutPhoto: PublicArtist = { ...withPhoto, stageName: '사진없음', profileImageUrl: null };

describe('공개 아티스트의 얼굴 자리 (TASK-MONO-641)', () => {
  describe('방향 ① — 값이 있으면 **사진이 그려진다**', () => {
    it('카드', () => {
      const { container } = render(<PublicArtistCard artist={withPhoto} />);
      const img = container.querySelector('img');
      expect(img).not.toBeNull();
      expect(img?.getAttribute('src')).toBe(PHOTO);
      // 🔴 스크린리더가 «이미지» 라고만 읽지 않는다.
      expect(img?.getAttribute('alt') ?? '').toContain('사진있음');
    });

    it('프로필', () => {
      const { container } = render(<PublicArtistProfile artist={withPhoto} />);
      expect(container.querySelector('img')?.getAttribute('src')).toBe(PHOTO);
    });
  });

  describe('방향 ② — 값이 없으면 **이니셜이 그대로 나온다**', () => {
    // 🔴 이 방향이 없으면 «항상 사진» 도 통과한다. 그리고 null 은 저장본이 비는 날의
    //    정상 경로이므로, 깨지면 그날 얼굴 자리가 통째로 사라진다.
    it('카드 — img 를 안 그리고 이니셜을 그린다', () => {
      const { container, getByTestId } = render(<PublicArtistCard artist={withoutPhoto} />);
      expect(container.querySelector('img')).toBeNull();
      // 🔵 이니셜은 **두 글자**다(`slice(0, 2)`) — '사진없음' → '사진'.
      expect(getByTestId('public-artist-avatar-fallback').textContent).toBe('사진');
    });

    it('프로필 — img 를 안 그리고 이니셜을 그린다', () => {
      const { container } = render(<PublicArtistProfile artist={withoutPhoto} />);
      expect(container.querySelector('img')).toBeNull();
    });
  });

  it('🔴 사진이 404 여도 그 칸만 이니셜로 되돌아간다 — 카드는 안 깨진다', () => {
    const { container, getByTestId, queryByTestId } = render(<PublicArtistCard artist={withPhoto} />);
    const img = getByTestId('public-artist-avatar');
    // 🔴 `dispatchEvent(new Event('error'))` 로는 React 의 합성 핸들러가 안 불린다
    //    (React 는 루트에 위임하는데 error 는 버블하지 않는다). testing-library 의
    //    fireEvent 는 그 경로를 알고 있으므로 그것을 쓴다 — 안 그러면 이 칸은
    //    «폴백이 안 돌았다» 를 컴포넌트 결함으로 오독한다(실제로 한 번 그랬다).
    fireEvent.error(img);
    expect(container.querySelector('img')).toBeNull();
    expect(queryByTestId('public-artist-avatar-fallback')).not.toBeNull();
    // 카드 자체는 여전히 있다(그 칸만 바뀌었다).
    expect(getByTestId('public-artist-card')).not.toBeNull();
  });

  it('🔵 저장본의 아티스트가 실제로 사진을 들고 있다 — 이 시험이 합성 픽스처만 재지 않도록', () => {
    // 🔴 위 칸들은 전부 내가 만든 객체를 그린다. 그것만으로는 «저장본에는 사진이 없는데
    //    컴포넌트만 준비된» 상태가 통과한다 — 638 직후가 정확히 그 반대 모양이었다.
    const artists = (bundled as { data: { artists: PublicArtist[] } }).data.artists;
    expect(artists.length).toBeGreaterThan(0); // 비공허성
    const withUrl = artists.filter((a) => a.profileImageUrl);
    expect(withUrl.length).toBe(artists.length);

    const { container } = render(<PublicArtistCard artist={artists[0]} />);
    expect(container.querySelector('img')?.getAttribute('src')).toBe(artists[0].profileImageUrl);
  });
});
