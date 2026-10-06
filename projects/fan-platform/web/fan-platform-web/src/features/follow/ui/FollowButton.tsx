'use client';
import { useState, useTransition } from 'react';
import { Button } from '@/shared/ui/Button';
import { followArtist, unfollowArtist } from '@/features/follow/api/actions';

export function FollowButton({
  artistAccountId,
  artistId,
  initialFollowing = false,
}: {
  /** The follow target — `artists.account_id`, what the API validates against. */
  artistAccountId: string;
  /** The artist entity id — only for revalidating `/artists/[id]`. Not the same value. */
  artistId: string;
  initialFollowing?: boolean;
}) {
  const [following, setFollowing] = useState(initialFollowing);
  const [isPending, startTransition] = useTransition();

  /**
   * TASK-FAN-FE-028 — optimistic update. The label used to flip only after the
   * server action's `await` resolved, so a slow round trip (cold start /
   * gateway latency — measured >=3s in the 23차 창 live run) left the button
   * silently frozen on the pre-click label even though the click had already
   * been accepted. `setFollowing` now runs synchronously on click, before the
   * network call starts, so the label always reacts within the same tick.
   *
   * Rollback: `wasFollowing` is captured before the optimistic flip so a real
   * failure (one that reaches this `catch` — `actions.ts` already swallows the
   * idempotent 409/404 cases by returning normally, so those never get here)
   * restores the pre-click label instead of leaving the UI claiming a state the
   * server never reached. This mirrors `ReactionBar`'s existing optimistic
   * pattern in this codebase (`features/post/ui/ReactionBar.tsx`).
   */
  const onClick = () => {
    const wasFollowing = following;
    setFollowing(!wasFollowing);
    startTransition(async () => {
      try {
        if (wasFollowing) {
          await unfollowArtist(artistAccountId, artistId);
        } else {
          await followArtist(artistAccountId, artistId);
        }
      } catch {
        setFollowing(wasFollowing);
      }
    });
  };

  return (
    <Button
      variant={following ? 'secondary' : 'primary'}
      onClick={onClick}
      disabled={isPending}
      aria-pressed={following}
      data-testid="follow-button"
    >
      {following ? '팔로잉' : '팔로우'}
    </Button>
  );
}
