import { notFound } from 'next/navigation';
import { Suspense } from 'react';
import { getFanSession } from '@/shared/auth/session';
import { getArtist } from '@/features/artist';
import { FollowButton } from '@/features/follow';
import { ApiError } from '@/shared/api/errors';
import { LoadingState } from '@/shared/ui/LoadingState';

async function ArtistProfile({ id }: { id: string }) {
  const session = await getFanSession();
  try {
    const artist = await getArtist(session.accessToken, id);
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
              <FollowButton artistAccountId={artist.id} initialFollowing={false} />
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

export default async function ArtistProfilePage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return (
    <Suspense fallback={<LoadingState label="아티스트 프로필을 불러오는 중..." />}>
      <ArtistProfile id={id} />
    </Suspense>
  );
}
