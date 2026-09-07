import { getFanSession } from '@/shared/auth/session';
import { getArtist } from '@/features/artist/api/getArtists';
import { FollowButton, getFollowStatus } from '@/features/follow';

/**
 * `/artists/[id]` 의 **회원 전용 조각** — 팔로우 버튼.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 왜 프로필 본문과 분리돼 있는가 — 공개 저장본에 `accountId` 가 **없기 때문**이다
 * ─────────────────────────────────────────────────────────────────────────
 * 팔로우는 `artist.accountId` 를 대상으로 한다(TASK-FAN-BE-045: `artist.id` 와 같은
 * 값이 아니고, community-service 는 살아 있는 `artists.account_id` 가 아니면 거절한다).
 * 그런데 `PublicArtist` 에는 `accountId` 가 **없다** — 테넌트 축 전체가 공개 계약에서
 * 빠져 있다. 즉 저장본만으로는 팔로우 버튼을 그릴 **수단이 없고**, 그것은 결함이 아니라
 * 이 설계가 의도한 결과다.
 *
 * ⇒ 그래서 팔로우는 로그인한 방문자에게만, **게이트웨이에서 `accountId` 를 받아온 뒤**
 *   붙는다. 익명 방문자는 이 컴포넌트를 애초에 만들지 않으므로 여기서 나가는 요청이 없다.
 *
 * 🔴 게이트웨이가 답을 못 주면 **버튼을 안 그린다**(`null`). 공개 프로필은 이미 저장본으로
 *    떠 있으므로, 여기서 에러 상태를 그리면 «백엔드가 필요 없는 화면» 위에 백엔드 장애를
 *    다시 얹는 꼴이 된다. 못 하는 일을 조용히 안 보여 주는 쪽이 맞다.
 */
export async function ArtistFollowPanel({ artistId }: { artistId: string }) {
  const session = await getFanSession();
  if (!session.accessToken) return null;

  try {
    const artist = await getArtist(session.accessToken, artistId);
    const following = await getFollowStatus(session.accessToken, artist.accountId);
    return (
      <div data-testid="artist-follow-panel" className="mt-4">
        <FollowButton
          artistAccountId={artist.accountId}
          artistId={artist.id}
          initialFollowing={following}
        />
      </div>
    );
  } catch {
    return null;
  }
}
