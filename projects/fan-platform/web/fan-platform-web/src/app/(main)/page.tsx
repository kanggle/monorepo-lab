import { paginate } from '@demo/public-data';
import { isAuthenticated } from '@/shared/auth/session';
import { FollowingFeedSection } from '@/features/feed';
import {
  readFanPublicData,
  feedPosts,
  emptyKind,
  totalPagesOf,
  PublicFeedList,
  PublicEmptyState,
  ProvenanceBanner,
} from '@/features/public-browse';
import { Pagination } from '@/shared/ui/Pagination';

const PAGE_SIZE = 10;

/**
 * 홈 — **공개 피드**. 로그인도 백엔드도 없이 뜬다.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 익명 방문은 백엔드로 요청을 **하나도** 보내지 않는다
 * ─────────────────────────────────────────────────────────────────────────
 * 이 함수가 익명 경로에서 하는 일은 둘뿐이다: 저장본 읽기(`readFanPublicData`)와
 * 세션 확인(`isAuthenticated` → 쿠키를 읽을 뿐 네트워크가 없다). 게이트웨이로 가는
 * 코드는 `FollowingFeedSection` 안에 있고, 그 엘리먼트는 **`authed` 일 때만 만들어진다.**
 * 조건을 컴포넌트 안이 아니라 여기 둔 이유는 §`FollowingFeedSection` 에 적혀 있다.
 *
 * 🔴 `<Suspense>` 를 쓰지 않는다. 예전 판은 게이트웨이 왕복을 스트리밍하려고 감쌌지만,
 *    저장본 읽기는 프로세스 안 캐시라 감쌀 지연이 없다. 그리고 감싸면 유닛 테스트가
 *    **fallback 만** 렌더하게 된다 — 이 저장소가 `ArtistProfile` 에서 이미 밟은 함정이고
 *    (TASK-FAN-FE-017), 그때 결함이 유닛 스위트에 안 보인 이유가 정확히 그것이었다.
 * ─────────────────────────────────────────────────────────────────────────
 */
export default async function HomePage({
  searchParams,
}: {
  searchParams: Promise<{ page?: string }>;
}) {
  const params = await searchParams;
  const page = Number.parseInt(params.page ?? '0', 10) || 0;

  const result = await readFanPublicData();
  const posts = feedPosts(result.data);
  // 🔵 `corpusSize` 는 **질의 이전의** 모집단이다. 공개 피드에는 필터가 없으므로 지금은
  //    `totalElements` 와 같지만, 그래도 따로 넘긴다 — 0건 판정이 두 수의 «차이» 를 읽는
  //    구조이고, 필터가 생기는 날 여기만 고치면 되게 한다.
  const paged = paginate(posts, { page, size: PAGE_SIZE }, result.data.posts.length);
  const empty = emptyKind(paged);

  const authed = await isAuthenticated();

  return (
    <section>
      <header className="mb-6">
        <h1 className="text-2xl font-bold text-ink-900">피드</h1>
        <p className="text-sm text-ink-600">아티스트들이 공개한 최신 포스트입니다.</p>
      </header>

      {authed ? <FollowingFeedSection size={PAGE_SIZE} /> : null}

      {empty ? (
        <PublicEmptyState kind={empty} noun="포스트" />
      ) : (
        <>
          <PublicFeedList posts={paged.content} />
          <Pagination
            page={paged.page}
            totalPages={totalPagesOf(paged.totalElements, paged.size)}
            hrefFor={(p) => (p === 0 ? '/' : `/?page=${p}`)}
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
