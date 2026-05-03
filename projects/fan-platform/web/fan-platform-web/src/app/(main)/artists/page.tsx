import { Suspense } from 'react';
import { getFanSession } from '@/shared/auth/session';
import { getArtists, ArtistCard } from '@/features/artist';
import { LoadingState } from '@/shared/ui/LoadingState';
import { EmptyState } from '@/shared/ui/EmptyState';
import { ErrorState } from '@/shared/ui/ErrorState';
import { Pagination } from '@/shared/ui/Pagination';
import type { ArtistPage } from '@/entities/artist';

const PAGE_SIZE = 12;

async function ArtistGrid({ q, page }: { q?: string; page: number }) {
  const session = await getFanSession();
  let result: ArtistPage | null = null;
  try {
    result = await getArtists(session.accessToken, { q, page, size: PAGE_SIZE });
  } catch {
    result = null;
  }

  if (!result) {
    return <ErrorState title="디렉토리를 불러올 수 없습니다" />;
  }

  if (result.content.length === 0) {
    return (
      <EmptyState
        title="아티스트를 찾을 수 없습니다"
        description={q ? `"${q}" 와 일치하는 아티스트가 없습니다.` : undefined}
      />
    );
  }

  return (
    <>
      <ul
        className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4"
        data-testid="artist-grid"
      >
        {result.content.map((artist) => (
          <li key={artist.id}>
            <ArtistCard artist={artist} />
          </li>
        ))}
      </ul>
      <Pagination
        page={result.page}
        totalPages={result.totalPages}
        hrefFor={(p) => (q ? `/artists?q=${encodeURIComponent(q)}&page=${p}` : `/artists?page=${p}`)}
      />
    </>
  );
}

export default async function ArtistsPage({
  searchParams,
}: {
  searchParams: Promise<{ q?: string; page?: string }>;
}) {
  const params = await searchParams;
  const q = params.q?.trim() || undefined;
  const page = Number.parseInt(params.page ?? '0', 10) || 0;
  return (
    <section>
      <header className="mb-6 flex items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-ink-900">아티스트 디렉토리</h1>
          <p className="text-sm text-ink-600">팬덤을 쌓아갈 아티스트를 찾아보세요.</p>
        </div>
        <form className="flex items-center gap-2" action="/artists">
          <input
            name="q"
            defaultValue={q ?? ''}
            placeholder="아티스트 이름..."
            className="rounded-md border border-ink-200 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-200"
          />
          <button
            type="submit"
            className="rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700"
          >
            검색
          </button>
        </form>
      </header>
      <Suspense fallback={<LoadingState label="아티스트를 불러오는 중..." />}>
        <ArtistGrid q={q} page={page} />
      </Suspense>
    </section>
  );
}
