import { queryArtists } from '@demo/public-data';
import {
  readFanPublicData,
  emptyKind,
  totalPagesOf,
  PublicArtistCard,
  PublicEmptyState,
  ProvenanceBanner,
} from '@/features/public-browse';
import { Pagination } from '@/shared/ui/Pagination';

const PAGE_SIZE = 12;

/**
 * 아티스트 디렉토리 — **공개**. 검색(`?q=`)도 저장본 안에서 돈다.
 *
 * 🔵 검색을 화면이 직접 구현하지 않고 `queryArtists` 를 부른다. 그 함수는 백엔드가 하던
 *    일과 **같은 뜻**을 유지하려고 `@demo/public-data` 에 있다(부분 문자열 포함이라는
 *    약한 규칙 + 안정 정렬). 여기서 다른 규칙을 새로 쓰면 같은 질의에 다른 답을 내면서
 *    아무도 그 사실을 모른다.
 *
 * 🔴 0건에는 **두 뜻**이 있고 문구가 갈린다 — `corpusSize` 가 그 축이다
 *    (§ `features/public-browse/lib/empty-state.ts`). 예전 판은 둘 다 "아티스트를 찾을 수
 *    없습니다" 였고, 그래서 저장본이 비었을 때 방문자가 검색어만 바꿔 가며 헤맸다.
 */
export default async function ArtistsPage({
  searchParams,
}: {
  searchParams: Promise<{ q?: string; page?: string }>;
}) {
  const params = await searchParams;
  const q = params.q?.trim() || undefined;
  const page = Number.parseInt(params.page ?? '0', 10) || 0;

  const result = await readFanPublicData();
  const paged = queryArtists(result.data.artists, { q, page, size: PAGE_SIZE });
  const empty = emptyKind(paged);

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

      {empty ? (
        <PublicEmptyState kind={empty} query={q} noun="아티스트" />
      ) : (
        <>
          <ul
            className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4"
            data-testid="artist-grid"
          >
            {paged.content.map((artist) => (
              <li key={artist.id}>
                <PublicArtistCard artist={artist} />
              </li>
            ))}
          </ul>
          <Pagination
            page={paged.page}
            totalPages={totalPagesOf(paged.totalElements, paged.size)}
            hrefFor={(p) =>
              q ? `/artists?q=${encodeURIComponent(q)}&page=${p}` : `/artists?page=${p}`
            }
          />
        </>
      )}

      <ProvenanceBanner
        source={result.envelope.source}
        generatedAt={result.envelope.generatedAt}
        degraded={result.degraded}
      />
    </section>
  );
}
