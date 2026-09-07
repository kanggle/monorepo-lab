/**
 * 공개 페이지가 **세션 없이** 실물로 서는가 — 그리고 그러는 동안 **요청을 하나도 안 보내는가**.
 *
 * ═════════════════════════════════════════════════════════════════════════
 * 이 파일이 재는 두 명제
 * ═════════════════════════════════════════════════════════════════════════
 *   ① 렌더된다 — 로그인도, 게이트웨이도, env 도 없이 화면에 내용이 들어찬다.
 *   ② 조용하다 — `fetch` 가 **한 번도** 안 불린다.
 *
 * ②가 ①보다 약해 보이지만 실제로는 그 반대다. 「백엔드를 안 부른다」는 코드를 읽어서
 * 확인할 수도 있지만, 그 확인은 **다음 커밋에서 낡는다**. 누가 헤더나 페이지에 조회를
 * 하나 붙이면 화면은 여전히 잘 뜨고(백엔드가 죽어 있어도 `.catch(() => null)` 이 삼킨다)
 * 아무 칸도 안 빨개진다 — 익명 방문이 EC2 를 깨우기 시작한 사실만 조용히 참이 된다.
 * 그래서 여기서는 `fetch` 를 스파이로 갈아 끼우고 **호출 0건**을 단언한다.
 *
 * 🔴 `readFanPublicData` 만 `vi.mock` 으로 갈아 끼운다 — 그 모듈이 `server-only` 를
 *    임포트해서 jsdom 에서 로드가 안 되기 때문이다. 갈아 끼운 자리에서는 **진짜
 *    `@demo/public-data` 판독자**를 부르므로, 데이터 경로(봉투 검증·번들 시드·질의)는
 *    그대로 시험된다. 우회되는 것은 `server-only` 한 줄뿐이다.
 *
 * 🔴 게이트웨이를 만지는 회원 전용 조각들(`FollowingFeedSection`, `MembershipMemberPanel`,
 *    `ArtistFollowPanel`, `memberPostDetail`)은 **스텁으로 갈아 끼운다.** 이유가 둘이다:
 *    ⓐ 그 모듈들도 `server-only` 를 지나 로드가 안 된다. ⓑ 더 중요하게 — 스텁이 «불렸다»
 *    를 기록하므로, 익명 경로에서 그것들이 **호출조차 되지 않는다**를 직접 단언할 수 있다.
 *    `fetch` 0건만 보면 "안 불렸다" 와 "불렸는데 마침 네트워크를 안 탔다" 가 안 갈린다.
 * ═════════════════════════════════════════════════════════════════════════
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import {
  createPublicDataReader,
  bundledEnvelope,
  __resetPublicDataCache,
  type FanPublicData,
  type PublicDataResult,
} from '@demo/public-data';
import bundledJson from '@demo/public-data/snapshots/fan.json';

/** 판독 결과를 칸마다 갈아 끼울 수 있게 hoisted 홀더에 둔다. */
const state = vi.hoisted(() => ({
  result: null as unknown,
  memberCalls: [] as string[],
}));

vi.mock('@/features/public-browse/api/read', () => ({
  readFanPublicData: async () => state.result,
}));

vi.mock('@/shared/auth/session', () => ({
  // 이 파일의 모든 칸은 **익명**이다. 회원 경로는 별도 관심사다.
  isAuthenticated: async () => false,
  getFanSession: async () => {
    state.memberCalls.push('getFanSession');
    return { accessToken: null, accountId: null, tenantId: null, roles: [], email: null, displayName: null };
  },
}));

vi.mock('@/features/feed', () => ({
  FollowingFeedSection: () => {
    state.memberCalls.push('FollowingFeedSection');
    return null;
  },
}));
vi.mock('@/features/membership', () => ({
  MembershipMemberPanel: () => {
    state.memberCalls.push('MembershipMemberPanel');
    return null;
  },
}));
vi.mock('@/features/artist', () => ({
  ArtistFollowPanel: () => {
    state.memberCalls.push('ArtistFollowPanel');
    return null;
  },
}));
vi.mock('@/features/post', () => ({
  memberPostDetail: async () => {
    state.memberCalls.push('memberPostDetail');
    return null;
  },
}));

/**
 * 🔵 페이지는 **정적 임포트**로 가져온다. `vi.mock` 은 임포트보다 위로 끌어올려지므로 위
 *    스텁들은 그대로 먹고, 대신 모듈 로딩 비용이 수집 단계로 빠진다. 칸 안에서
 *    `await import(...)` 하면 그 비용이 **첫 칸의 5초 타임아웃에 잡히고**, 그러면 «느린
 *    변환» 이 «칸이 멈췄다» 로 보인다(이 호스트에서 실제로 그랬다 — 첫 칸만 빨갛고 같은
 *    모듈을 쓰는 뒷 칸들은 전부 초록이었다. 그 모양이 곧 지문이다).
 */
import HomePage from '@/app/(main)/page';
import ArtistsPage from '@/app/(main)/artists/page';
import ArtistProfilePage from '@/app/(main)/artists/[id]/page';
import PostDetailPage from '@/app/(main)/posts/[id]/page';
import MembershipPage from '@/app/(main)/membership/page';

/** 진짜 판독자로 봉투를 읽는다 — 번들 시드가 계약을 어기면 여기서 죽는다. */
async function realBundledResult(): Promise<PublicDataResult<FanPublicData>> {
  __resetPublicDataCache();
  const reader = createPublicDataReader('fan', bundledEnvelope('fan', bundledJson), { env: {} });
  return reader.readPublicData();
}

/** 저장본이 **비어 있는** 봉투 — 0건 판정의 둘째 칸을 만들기 위한 것. */
function emptyResult(): PublicDataResult<FanPublicData> {
  const envelope = {
    ...(bundledJson as unknown as { schemaVersion: number }),
    ...bundledJson,
    coverage: { artists: 0, posts: 0, membershipPlans: 0 },
    collectionStatus: { artists: 'empty', posts: 'empty', membershipPlans: 'empty' },
    data: { artists: [], posts: [], membershipPlans: [] },
  } as unknown as PublicDataResult<FanPublicData>['envelope'];
  return { data: envelope.data, envelope, degraded: false, degradedReason: null };
}

let fetchSpy: ReturnType<typeof vi.fn>;

beforeEach(async () => {
  state.memberCalls = [];
  state.result = await realBundledResult();
  // 🔴 이 스파이가 이 파일의 핵심 계측기다. 익명 렌더 동안 **한 번도** 안 불려야 한다.
  fetchSpy = vi.fn().mockRejectedValue(new Error('공개 경로에서 네트워크 요청이 발생했다'));
  vi.stubGlobal('fetch', fetchSpy);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

/** 서버 컴포넌트를 함수로 불러 엘리먼트를 얻은 뒤 렌더한다. */
async function renderPage(el: Promise<React.ReactNode>) {
  render(<div data-testid="host">{await el}</div>);
}

describe('/ (공개 피드) — 세션 없이', () => {
  it('🔴 공개 글이 그려지고, 잠긴 글은 티저로 나온다', async () => {
    await renderPage(HomePage({ searchParams: Promise.resolve({}) }));

    expect(screen.getByTestId('public-feed')).toBeInTheDocument();
    // 시드의 공개 글 제목
    expect(screen.getByText('STELLAR 컴백 준비 현장')).toBeInTheDocument();
    // 시드의 잠긴 글 — 제목은 나오고 상태는 «멤버십 전용»
    expect(screen.getByText('[멤버십] 미공개 데모 트랙 이야기')).toBeInTheDocument();
    expect(screen.getAllByText('멤버십 전용').length).toBeGreaterThan(0);
  });

  it('🔴🔴 fetch 가 **한 번도** 안 불린다', async () => {
    await renderPage(HomePage({ searchParams: Promise.resolve({}) }));
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('🔴 회원 전용 조각이 **만들어지지도** 않는다', async () => {
    await renderPage(HomePage({ searchParams: Promise.resolve({}) }));
    expect(state.memberCalls).not.toContain('FollowingFeedSection');
  });

  it('출처 배너가 «샘플 데이터» 를 말한다 (live 를 주장하지 않는다)', async () => {
    await renderPage(HomePage({ searchParams: Promise.resolve({}) }));
    expect(screen.getByTestId('provenance-banner')).toHaveTextContent('샘플 데이터');
  });

  it('🔵 저장본이 비면 «저장본이 비어 있습니다» — «검색 결과 없음» 이 아니다', async () => {
    state.result = emptyResult();
    await renderPage(HomePage({ searchParams: Promise.resolve({}) }));
    expect(screen.getByText('저장본이 비어 있습니다')).toBeInTheDocument();
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});

describe('/artists (공개 목록 + 검색) — 세션 없이', () => {
  it('목록이 그려진다', async () => {
    await renderPage(ArtistsPage({ searchParams: Promise.resolve({}) }));

    expect(screen.getByTestId('artist-grid')).toBeInTheDocument();
    expect(screen.getAllByTestId('public-artist-card')).toHaveLength(3);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('🔴 `q` 가 계속 동작한다 — 걸리는 질의는 좁혀진다', async () => {
    await renderPage(ArtistsPage({ searchParams: Promise.resolve({ q: '루미' }) }));

    const cards = screen.getAllByTestId('public-artist-card');
    expect(cards).toHaveLength(1);
    // 🔵 `getByText('루미')` 는 쓰지 않는다 — 카드가 아바타 이니셜에도 같은 두 글자를
    //    그려서 매치가 둘이 된다(한글 활동명에서는 `slice(0,2)` 가 이름 전체다).
    //    남은 카드가 **누구인지**를 묻는 것이 이 칸의 명제이므로 카드 안에서 확인한다.
    expect(cards[0]).toHaveTextContent('루미');
    // 음성 대조군 — 걸러진 아티스트는 화면에 없다.
    expect(screen.queryByText('노아')).toBeNull();
    expect(screen.queryByText('세아')).toBeNull();
  });

  it('🔴 안 걸리는 질의 → «검색 결과가 없습니다» (저장본이 빈 것과 다르다)', async () => {
    await renderPage(ArtistsPage({ searchParams: Promise.resolve({ q: 'zzz없는이름zzz' }) }));

    expect(screen.getByText('검색 결과가 없습니다')).toBeInTheDocument();
    expect(screen.queryByText('저장본이 비어 있습니다')).toBeNull();
  });

  it('🔴 저장본이 비었을 때는 **반대 문구** — 같은 0건인데 다른 사실이다', async () => {
    state.result = emptyResult();
    await renderPage(ArtistsPage({ searchParams: Promise.resolve({ q: 'zzz없는이름zzz' }) }));

    expect(screen.getByText('저장본이 비어 있습니다')).toBeInTheDocument();
    expect(screen.queryByText('검색 결과가 없습니다')).toBeNull();
  });
});

describe('/artists/[id] (공개 프로필) — 세션 없이', () => {
  it('프로필과 그 아티스트의 글이 그려지고, 팔로우 패널은 없다', async () => {
    await renderPage(ArtistProfilePage({ params: Promise.resolve({ id: '0199de80-0000-7000-8000-00000000a001' }) }));

    expect(screen.getByTestId('public-artist-profile')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '루미', level: 1 })).toBeInTheDocument();
    expect(screen.getByTestId('public-feed')).toBeInTheDocument();
    expect(state.memberCalls).not.toContain('ArtistFollowPanel');
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('🔴 공개 프로필에 본명(realName)을 그릴 자리가 없다 — 공개 계약에 그 필드가 없다', async () => {
    await renderPage(ArtistProfilePage({ params: Promise.resolve({ id: '0199de80-0000-7000-8000-00000000a001' }) }));
    const artists = (state.result as PublicDataResult<FanPublicData>).data.artists;
    for (const a of artists) {
      expect('realName' in a).toBe(false);
      expect('accountId' in a).toBe(false);
    }
  });

  it('저장본에 없는 id → notFound (게이트웨이에 되묻지 않는다)', async () => {
    await expect(ArtistProfilePage({ params: Promise.resolve({ id: 'no-such-artist' }) })).rejects.toThrow();
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});

describe('/posts/[id] (공개 상세) — 세션 없이', () => {
  it('공개 글은 본문이 나온다', async () => {
    await renderPage(PostDetailPage({ params: Promise.resolve({ id: '0199de80-0000-7000-8000-00000000b001' }) }));

    expect(screen.getByTestId('public-post-detail')).toHaveAttribute('data-locked', 'false');
    expect(screen.getByText(/첫 정규 앨범 작업을 시작했습니다/)).toBeInTheDocument();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('🔴🔴 잠긴 글의 상세는 «멤버십 전용» 이고 본문이 없다', async () => {
    await renderPage(PostDetailPage({ params: Promise.resolve({ id: '0199de80-0000-7000-8000-00000000b004' }) }));

    const detail = screen.getByTestId('public-post-detail');
    expect(detail).toHaveAttribute('data-locked', 'true');
    expect(detail).toHaveTextContent('멤버십 전용');
    // 잠긴 글의 body 는 시드에서 null 이다 — 화면이 "null" 을 문자로 흘리지도 않는다.
    expect(detail.textContent ?? '').not.toContain('null');
  });

  it('🔴 익명이면 회원 판을 **시도조차 안 한다**', async () => {
    await renderPage(PostDetailPage({ params: Promise.resolve({ id: '0199de80-0000-7000-8000-00000000b001' }) }));
    expect(state.memberCalls).not.toContain('memberPostDetail');
  });
});

describe('/membership (공개 소개) — 세션 없이', () => {
  it('🔴 요금제·혜택·가격과 note 가 전부 나온다', async () => {
    await renderPage(MembershipPage({ searchParams: Promise.resolve({}) }));

    expect(screen.getAllByTestId('public-membership-plan')).toHaveLength(2);
    expect(screen.getByText('멤버스')).toBeInTheDocument();
    expect(screen.getByText('프리미엄')).toBeInTheDocument();
    expect(screen.getByText(/4,900원/)).toBeInTheDocument();
    expect(screen.getByText(/멤버십 전용 게시물 열람/)).toBeInTheDocument();
    // 🔴 `note` — 이 문장이 없으면 화면이 «지금 이 가격에 가입된다» 를 주장하게 된다.
    expect(screen.getAllByText(/실제 가입·결제는 로그인 후 진행되며/).length).toBe(2);
  });

  it('🔴 «가입하려면 로그인» CTA 가 있고, 회원 패널은 **없다**', async () => {
    await renderPage(MembershipPage({ searchParams: Promise.resolve({}) }));

    expect(screen.getByTestId('membership-login-cta')).toBeInTheDocument();
    expect(screen.queryByTestId('membership-member-panel')).toBeNull();
    expect(state.memberCalls).not.toContain('MembershipMemberPanel');
  });

  it('🔴🔴 공개 경로에서 개인 구독 데이터를 **읽지 않는다** — 요청 0건', async () => {
    await renderPage(MembershipPage({ searchParams: Promise.resolve({}) }));
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(state.memberCalls).toEqual([]);
  });
});

describe('전체 — 새 세션·직접 진입에서 같은 결과가 나온다', () => {
  it('🔵 같은 페이지를 두 번 렌더하면 같은 내용이 나온다 (세션 상태에 안 묶여 있다)', async () => {

    const first = render(<div>{await HomePage({ searchParams: Promise.resolve({}) })}</div>);
    const firstText = first.container.textContent;
    first.unmount();

    const second = render(<div>{await HomePage({ searchParams: Promise.resolve({}) })}</div>);
    expect(second.container.textContent).toBe(firstText);
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});
