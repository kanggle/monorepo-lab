/**
 * `public-browse` 의 공개 표면 — **로그인 없이 도는 화면이 쓰는 것 전부**.
 *
 * 🔴 이 배럴에는 게이트웨이로 가는 것이 하나도 없다. `@/shared/api/client` 를 임포트하는
 *    모듈이 이 슬라이스 안에 **없고**, 그래서 공개 페이지가 이 배럴만 쓰는 한 익명 방문이
 *    백엔드로 요청을 보낼 경로가 존재하지 않는다. 여기에 게이트웨이 호출을 재수출하는
 *    변경은 그 성질을 조용히 없애므로, 하려면 그 사실을 아는 상태로 해야 한다.
 */

export { readFanPublicData, type FanPublicDataResult } from './api/read';

export { feedPosts, findArtist, findPost, artistPosts, totalPagesOf } from './lib/select';
export { provenanceOf, type Provenance, type ProvenanceKind } from './lib/provenance';
export { emptyKind, type EmptyKind } from './lib/empty-state';

export { ProvenanceBanner } from './ui/ProvenanceBanner';
export { PublicFeedList } from './ui/PublicFeedList';
export { PublicPostCard } from './ui/PublicPostCard';
export { PublicPostDetail } from './ui/PublicPostDetail';
export { PublicArtistCard } from './ui/PublicArtistCard';
export { PublicArtistProfile } from './ui/PublicArtistProfile';
export { PublicMembershipPlans } from './ui/PublicMembershipPlans';
export { PublicEmptyState } from './ui/PublicEmptyState';
