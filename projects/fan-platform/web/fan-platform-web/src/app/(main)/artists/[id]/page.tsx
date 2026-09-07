import { notFound } from 'next/navigation';
import { isAuthenticated } from '@/shared/auth/session';
import { ArtistFollowPanel } from '@/features/artist';
import {
  readFanPublicData,
  findArtist,
  artistPosts,
  PublicArtistProfile,
  PublicFeedList,
  ProvenanceBanner,
} from '@/features/public-browse';

/**
 * 아티스트 프로필 — **공개**. 프로필과 그 아티스트의 공개 글을 저장본에서 읽는다.
 *
 * 🔴 팔로우 버튼은 로그인한 방문자에게만 붙는다. 공개 저장본에 `accountId` 가 없어서
 *    저장본만으로는 **그릴 수단이 없기 때문**이고, 그 사정은 `ArtistFollowPanel` 헤더에
 *    적혀 있다. 인가는 예전 그대로 community-service 가 한다.
 *
 * 🔴 저장본에 없는 id 는 `notFound()` 다. 게이트웨이로 «혹시 있나» 를 물어보지 않는다 —
 *    그것이 바로 이 ADR 이 금지한 «백엔드 먼저, 실패하면 저장본» 의 거울상이다.
 */
export default async function ArtistProfilePage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;

  const result = await readFanPublicData();
  const artist = findArtist(result.data, id);
  if (!artist) notFound();

  const posts = artistPosts(result.data, id);
  const authed = await isAuthenticated();

  return (
    <section className="flex flex-col gap-8">
      <div>
        <PublicArtistProfile artist={artist} />
        {authed ? <ArtistFollowPanel artistId={id} /> : null}
      </div>

      <div>
        <h2 className="mb-4 text-lg font-semibold text-ink-900">포스트</h2>
        {posts.length === 0 ? (
          <p className="text-sm text-ink-600" data-testid="artist-posts-empty">
            저장본에 이 아티스트의 공개 포스트가 없습니다.
          </p>
        ) : (
          <PublicFeedList posts={posts} />
        )}
      </div>

      <ProvenanceBanner
        source={result.envelope.source}
        generatedAt={result.envelope.generatedAt}
        degraded={result.degraded}
      />
    </section>
  );
}
