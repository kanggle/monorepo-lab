import 'server-only';
import { gatewayFetch } from '@/shared/api/client';

/**
 * "Am I following this artist?" — TASK-FAN-FE-017 / TASK-FAN-BE-049.
 *
 * <p>🔴 Takes the artist's **account id**, not the artist entity id. The two are
 * different identifiers and community validates the account one (TASK-FAN-BE-045);
 * they coincide only for the backfilled demo rows, so passing `artist.id` here
 * looks correct in the demo and answers about the wrong subject for an artist
 * registered against a real IAM account.
 *
 * <p>The endpoint answers 200 with a boolean and never 404 — a 404 would collide
 * with "no such artist" (community-api.md § "Why this is 200 + a boolean and never
 * 404"). We still fall back to `false` if the call throws, because a profile page
 * that renders is better than one that errors; the cost is that a transient
 * failure looks like "not following", which is precisely the defect this ticket
 * fixes. That is acceptable for a blip and unacceptable as a steady state, which
 * is why the fallback is here and not in place of the call.
 */
export async function getFollowStatus(
  accessToken: string | null,
  artistAccountId: string,
): Promise<boolean> {
  try {
    const res = await gatewayFetch<{ artistAccountId: string; following: boolean }>(
      `/api/v1/community/follows/${encodeURIComponent(artistAccountId)}`,
      { accessToken, cache: 'no-store' },
    );
    return res.data.following === true;
  } catch {
    return false;
  }
}
