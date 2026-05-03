'use client';
import { useState, useTransition } from 'react';
import { Button } from '@/shared/ui/Button';
import { followArtist, unfollowArtist } from '@/features/follow/api/actions';

export function FollowButton({
  artistAccountId,
  initialFollowing = false,
}: {
  artistAccountId: string;
  initialFollowing?: boolean;
}) {
  const [following, setFollowing] = useState(initialFollowing);
  const [isPending, startTransition] = useTransition();

  const onClick = () => {
    startTransition(async () => {
      try {
        if (following) {
          await unfollowArtist(artistAccountId);
          setFollowing(false);
        } else {
          await followArtist(artistAccountId);
          setFollowing(true);
        }
      } catch {
        // Server action error already returned to UI; toast layer can pick up
        // the rejected promise if added later.
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
