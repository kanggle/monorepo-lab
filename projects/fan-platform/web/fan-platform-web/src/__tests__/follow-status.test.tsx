import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';

/**
 * TASK-FAN-FE-017 — the artist detail page must render the follow relationship
 * the server knows about.
 *
 * <h2>🔴 Why there are two axes here and why the second one is load-bearing</h2>
 *
 * The original defect was NOT in {@code FollowButton}: the component already
 * accepted {@code initialFollowing} and rendered it correctly. The defect was in
 * the **call site** — the page passed the literal {@code false}. So a test that
 * only exercises the component passes against the bug, unchanged. That is why
 * the second describe block asserts what {@code getFollowStatus} is *called with*
 * and that its answer *reaches* the button, rather than re-testing the component.
 *
 * <h2>🔴 The axis trap</h2>
 *
 * Follow is keyed on {@code artists.account_id}, while the detail route is keyed
 * on the artist entity id ({@code artists.id}) — TASK-FAN-BE-045. In the demo's
 * backfilled rows the two values coincide, so a page that queried the wrong one
 * would look perfectly correct in the demo and answer about the wrong subject for
 * an artist registered against a real IAM account. The fixture below therefore
 * gives the artist an id and an accountId that **differ**, and the assertion names
 * the accountId explicitly.
 */

const { getFollowStatus, getArtist } = vi.hoisted(() => ({
  getFollowStatus: vi.fn(),
  getArtist: vi.fn(),
}));

vi.mock('@/features/artist/api/getArtists', () => ({ getArtist }));
vi.mock('@/shared/auth/session', () => ({
  getFanSession: async () => ({ accessToken: 'token-abc' }),
}));
// The follow barrel is mocked wholesale: the real getFollowStatus reaches
// `server-only` through the gateway client, which jsdom cannot load. FollowButton
// is re-exported from the real module so the assertions below still render the
// production component.
vi.mock('@/features/follow', async () => {
  const real = await vi.importActual<typeof import('@/features/follow/ui/FollowButton')>(
    '@/features/follow/ui/FollowButton',
  );
  return { getFollowStatus, FollowButton: real.FollowButton };
});

import { FollowButton } from '@/features/follow/ui/FollowButton';

vi.mock('@/features/follow/api/actions', () => ({
  followArtist: vi.fn(),
  unfollowArtist: vi.fn(),
}));

// 🔴 id !== accountId on purpose — see the class comment.
const ARTIST = {
  id: '0199de80-0000-7000-8000-0000000000e1',
  accountId: '0199de70-0000-7000-8000-0000000000a1',
  stageName: '루미',
  artistType: 'SOLO',
  agency: 'Aurora Entertainment',
  realName: null,
  debutDate: null,
  bio: null,
};

beforeEach(() => {
  vi.clearAllMocks();
  getArtist.mockResolvedValue(ARTIST);
});

// ---- axis 1: the component honours the value it is handed --------------------

describe('FollowButton — initialFollowing 을 그대로 그린다', () => {
  it('🔴 AC-2 대조군: true 는 팔로잉/aria-pressed=true, false 는 팔로우/false', () => {
    const { unmount } = render(
      <FollowButton artistAccountId="a" artistId="b" initialFollowing={true} />,
    );
    const followingBtn = screen.getByTestId('follow-button');
    expect(followingBtn.textContent).toContain('팔로잉');
    expect(followingBtn.getAttribute('aria-pressed')).toBe('true');
    unmount();

    render(<FollowButton artistAccountId="a" artistId="b" initialFollowing={false} />);
    const notFollowingBtn = screen.getByTestId('follow-button');
    expect(notFollowingBtn.textContent).toContain('팔로우');
    expect(notFollowingBtn.getAttribute('aria-pressed')).toBe('false');

    // The two cells must DIFFER. Asserting only one of them is satisfied by a
    // constant, and a constant is exactly what this ticket is fixing.
    expect(followingBtn.textContent).not.toBe(notFollowingBtn.textContent);
  });
});

// ---- axis 2: the CALL SITE actually asks, and passes the answer through ------

describe('아티스트 상세 — 서버가 아는 팔로우 여부가 버튼까지 도달한다', () => {
  /**
   * 🔴 Renders ArtistProfile, NOT the route.
   *
   * The route wraps this component in {@code <Suspense>} and RTL resolves the
   * fallback, never the async child — the first version of this test rendered the
   * route, saw "아티스트 프로필을 불러오는 중...", and reported
   * {@code getFollowStatus} called 0 times. That is the same shape as a green test
   * that measures nothing, so the component is awaited directly here.
   */
  async function renderProfile() {
    const mod = await import('@/features/artist/ui/ArtistProfile');
    const element = await mod.ArtistProfile({ id: ARTIST.id });
    render(element);
  }

  it('🔴 AC-1/AC-2: 팔로우 중이면 버튼이 팔로잉으로 뜬다 (호출부가 상수를 넘기면 실패한다)', async () => {
    getFollowStatus.mockResolvedValue(true);
    await renderProfile();
    expect(screen.getByText('팔로잉')).toBeTruthy();
  });

  it('🔴 AC-2 짝: 팔로우하지 않으면 팔로우로 뜬다 — 두 칸이 갈라져야 잰 것이다', async () => {
    getFollowStatus.mockResolvedValue(false);
    await renderProfile();
    expect(screen.getByText('팔로우')).toBeTruthy();
  });

  it('🔴 축: 조회는 artist.accountId 로 한다 (artist.id 가 아니다)', async () => {
    getFollowStatus.mockResolvedValue(true);
    await renderProfile();

    expect(getFollowStatus).toHaveBeenCalledTimes(1);
    const [, askedFor] = getFollowStatus.mock.calls[0] as [unknown, string];
    // In the demo these two values are equal, so `toBe(ARTIST.accountId)` alone
    // would pass on the wrong axis there. The fixture keeps them different AND
    // the wrong value is named, so the failure message says which axis was used.
    expect(askedFor).toBe(ARTIST.accountId);
    expect(askedFor).not.toBe(ARTIST.id);
  });
});
