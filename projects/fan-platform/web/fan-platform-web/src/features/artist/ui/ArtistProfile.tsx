import { notFound } from 'next/navigation';
import { getFanSession } from '@/shared/auth/session';
import { getArtist } from '@/features/artist/api/getArtists';
import { FollowButton, getFollowStatus } from '@/features/follow';
import { ApiError } from '@/shared/api/errors';

/**
 * Artist profile card — the body of `/artists/[id]`.
 *
 * <p>Lives here rather than inside the route file so it can be rendered directly
 * in a test. The route wraps it in {@code <Suspense>}, and a test that renders the
 * route gets the fallback, never this component — which is how the defect below
 * stayed invisible to the unit suite (TASK-FAN-FE-017).
 */
export async function ArtistProfile({ id }: { id: string }) {
  const session = await getFanSession();
  try {
    const artist = await getArtist(session.accessToken, id);
    // TASK-FAN-FE-017. This used to be the literal `false` handed to
    // <FollowButton> below, so the page could act on the follow relationship but
    // never show it — an already-following fan saw "팔로우" and had to press it
    // once (a no-op re-follow) before unfollow was reachable.
    //
    // 🔴 Queried on artist.accountId, the same axis the follow API validates.
    const following = await getFollowStatus(session.accessToken, artist.accountId);
    return (
      <article className="rounded-2xl border border-ink-200 bg-white p-8 shadow-sm">
        <div className="flex items-start gap-6">
          <div className="flex h-32 w-32 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-100 to-accent-100 text-4xl font-bold text-brand-700">
            {artist.stageName.slice(0, 2).toUpperCase()}
          </div>
          <div className="flex-1">
            <h1 className="text-3xl font-bold text-ink-900">{artist.stageName}</h1>
            {artist.realName ? (
              <p className="text-sm text-ink-500">{artist.realName}</p>
            ) : null}
            <p className="mt-2 text-sm text-ink-600">
              {artist.artistType === 'SOLO' ? '솔로' : '그룹 멤버'}
              {artist.agency ? ` · ${artist.agency}` : ''}
              {artist.debutDate ? ` · 데뷔 ${artist.debutDate}` : ''}
            </p>
            <div className="mt-4">
              {/* artist.accountId, NOT artist.id — TASK-FAN-BE-045. The two
                  coincide only for the backfilled demo rows; for an artist
                  registered against a real IAM subject, artist.id is the wrong
                  value and community-service refuses the follow. The read above
                  must use the same axis for the same reason. */}
              <FollowButton
                artistAccountId={artist.accountId}
                artistId={artist.id}
                initialFollowing={following}
              />
            </div>
          </div>
        </div>
        {artist.bio ? (
          <p className="mt-6 whitespace-pre-line text-sm leading-relaxed text-ink-700">
            {artist.bio}
          </p>
        ) : null}
      </article>
    );
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) {
      notFound();
    }
    throw err;
  }
}
