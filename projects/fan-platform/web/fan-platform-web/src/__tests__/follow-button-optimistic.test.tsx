import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FollowButton } from '@/features/follow/ui/FollowButton';

/**
 * TASK-FAN-FE-028 — the follow/unfollow server action succeeds (200), but the
 * button label used to stay on the pre-click text until the action's `await`
 * resolved. The 23차 창 live run measured >=3s of silent frozen label before a
 * cold-start / gateway-latency response landed.
 *
 * <h2>Why the pending-promise fixture is load-bearing</h2>
 *
 * `followArtist`/`unfollowArtist` are mocked with a **deferred** promise that
 * the test resolves (or rejects) by hand, so the assertion right after the
 * click happens strictly *before* the server action settles. Against the
 * pre-fix code (`setFollowing` called only after `await`), that assertion
 * fails — the label is still on the pre-click text at that point. That is the
 * bite this suite is checking for AC-3 ("라벨이 클릭 한 틱 안에 바뀐다" — not
 * "결국 200 이 온다").
 */

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

const { followArtist, unfollowArtist } = vi.hoisted(() => ({
  followArtist: vi.fn(),
  unfollowArtist: vi.fn(),
}));
vi.mock('@/features/follow/api/actions', () => ({ followArtist, unfollowArtist }));

describe('FollowButton — 낙관적 갱신 (TASK-FAN-FE-028)', () => {
  beforeEach(() => {
    followArtist.mockReset();
    unfollowArtist.mockReset();
  });

  it('🔴 AC-3 bite: 서버 액션이 아직 끝나지 않았어도 클릭 직후 라벨이 팔로잉으로 바뀐다', async () => {
    const { promise } = deferred<void>();
    followArtist.mockReturnValue(promise); // never resolves within this test

    const user = userEvent.setup();
    render(<FollowButton artistAccountId="acc-1" artistId="artist-1" initialFollowing={false} />);

    const button = screen.getByTestId('follow-button');
    expect(button.textContent).toContain('팔로우');

    await user.click(button);

    // The server action is still pending at this point — followArtist's
    // promise has not been resolved. The label must already have flipped.
    expect(followArtist).toHaveBeenCalledWith('acc-1', 'artist-1');
    expect(button.textContent).toContain('팔로잉');
    expect(button.getAttribute('aria-pressed')).toBe('true');
  });

  it('🔴 AC-3 짝 (언팔로우 방향): 클릭 직후 라벨이 팔로우로 바뀐다', async () => {
    const { promise } = deferred<void>();
    unfollowArtist.mockReturnValue(promise);

    const user = userEvent.setup();
    render(<FollowButton artistAccountId="acc-1" artistId="artist-1" initialFollowing={true} />);

    const button = screen.getByTestId('follow-button');
    expect(button.textContent).toContain('팔로잉');

    await user.click(button);

    expect(unfollowArtist).toHaveBeenCalledWith('acc-1', 'artist-1');
    expect(button.textContent).toContain('팔로우');
    expect(button.getAttribute('aria-pressed')).toBe('false');
  });

  it('🔴 AC-2 롤백: 서버 액션이 진짜로 실패하면 라벨이 클릭 전 상태로 되돌아온다', async () => {
    const { promise, reject } = deferred<void>();
    followArtist.mockReturnValue(promise);

    const user = userEvent.setup();
    render(<FollowButton artistAccountId="acc-1" artistId="artist-1" initialFollowing={false} />);
    const button = screen.getByTestId('follow-button');

    await user.click(button);
    expect(button.textContent).toContain('팔로잉'); // optimistic flip happened

    reject(new Error('network error'));

    // Rollback happens once the rejected transition settles.
    expect(await screen.findByText('팔로우')).toBeInTheDocument();
    expect(button.getAttribute('aria-pressed')).toBe('false');
  });

  it('회귀: 연타는 isPending 동안 disabled 로 계속 막힌다', async () => {
    const { promise } = deferred<void>();
    followArtist.mockReturnValue(promise);

    const user = userEvent.setup();
    render(<FollowButton artistAccountId="acc-1" artistId="artist-1" initialFollowing={false} />);
    const button = screen.getByTestId('follow-button');

    await user.click(button);

    expect(button).toBeDisabled();
    expect(followArtist).toHaveBeenCalledTimes(1);
  });
});
