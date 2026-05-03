import Link from 'next/link';
import type { Artist } from '@/entities/artist';

export function ArtistCard({ artist }: { artist: Artist }) {
  return (
    <Link
      href={`/artists/${artist.id}`}
      className="group flex flex-col gap-2 rounded-xl border border-ink-200 bg-white p-4 shadow-sm transition-shadow hover:shadow-md dark:bg-ink-900 dark:border-ink-800"
      data-testid="artist-card"
    >
      <div className="flex h-32 items-center justify-center rounded-lg bg-gradient-to-br from-brand-100 to-accent-100 text-3xl font-bold text-brand-700">
        {artist.stageName.slice(0, 2).toUpperCase()}
      </div>
      <div>
        <p className="font-semibold text-ink-900 dark:text-ink-100">{artist.stageName}</p>
        <p className="text-xs text-ink-500">
          {artist.artistType === 'SOLO' ? '솔로' : '그룹 멤버'}
          {artist.agency ? ` · ${artist.agency}` : ''}
        </p>
      </div>
    </Link>
  );
}
