/**
 * `/posts/[id]` 네 갈래 **전부**에 «← 뒤로» 가 정확히 하나 (TASK-FAN-FE-023 AC-2 · AC-3).
 *
 * 네 갈래: 회원 성공 · 회원 `MEMBERSHIP_REQUIRED` · 공개 열림 · 공개 잠김. 회원 두 갈래는
 * `memberPostDetail` 이 만든 엘리먼트를 페이지가 그대로 싸서 그리므로, 여기서는 그 함수를 스텁으로
 * 갈아 끼워 두 모양의 엘리먼트를 돌려준다. 공개 두 갈래는 **진짜 번들 저장본**을 읽는다
 * (`public-pages.test.tsx` 와 같은 방식 — `server-only` 한 줄만 우회).
 *
 * 🔴 회원 판이 `null`(판정 못 함)이면 공개 판으로 내려간다 — 그 칸에서도 뒤로가기가 **하나**여야 한다
 *    (두 겹으로 싸면 둘이 된다).
 */
import type { ReactNode } from 'react';
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

const state = vi.hoisted(() => ({
  result: null as unknown,
  authed: false,
  member: null as ReactNode | null,
}));

vi.mock('@/features/public-browse/api/read', () => ({
  readFanPublicData: async () => state.result,
}));
vi.mock('@/shared/auth/session', () => ({
  isAuthenticated: async () => state.authed,
}));
vi.mock('@/features/post', () => ({
  memberPostDetail: async () => state.member,
}));

import PostDetailPage from '@/app/(main)/posts/[id]/page';

let fetchSpy: ReturnType<typeof vi.fn>;
let data: FanPublicData;

beforeEach(async () => {
  __resetPublicDataCache();
  const reader = createPublicDataReader('fan', bundledEnvelope('fan', bundledJson), { env: {} });
  const result: PublicDataResult<FanPublicData> = await reader.readPublicData();
  state.result = result;
  data = result.data;
  state.authed = false;
  state.member = null;
  fetchSpy = vi.fn().mockRejectedValue(new Error('상세 렌더에서 네트워크 요청이 발생했다'));
  vi.stubGlobal('fetch', fetchSpy);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

async function renderDetail(id: string) {
  render(<div>{await PostDetailPage({ params: Promise.resolve({ id }) })}</div>);
}

function postId(locked: boolean): string {
  const post = data.posts.find((p) => p.locked === locked);
  if (!post) throw new Error(`번들 저장본에 locked=${locked} 글이 없다 — 이 칸의 전제가 깨졌다`);
  return post.id;
}

describe('/posts/[id] — 뒤로가기가 네 갈래 전부에 하나씩', () => {
  it('공개 · 열림', async () => {
    await renderDetail(postId(false));
    expect(screen.getByTestId('public-post-detail').getAttribute('data-locked')).toBe('false');
    expect(screen.getAllByTestId('back-link')).toHaveLength(1);
    expect(screen.getByTestId('back-link').getAttribute('href')).toBe('/');
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('공개 · 잠김', async () => {
    await renderDetail(postId(true));
    expect(screen.getByTestId('public-post-detail').getAttribute('data-locked')).toBe('true');
    expect(screen.getAllByTestId('back-link')).toHaveLength(1);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('회원 · 성공', async () => {
    state.authed = true;
    state.member = <article data-testid="member-post-detail" />;
    await renderDetail('any');
    expect(screen.getByTestId('member-post-detail')).toBeTruthy();
    expect(screen.getAllByTestId('back-link')).toHaveLength(1);
  });

  it('회원 · MEMBERSHIP_REQUIRED', async () => {
    state.authed = true;
    state.member = <div data-testid="membership-required">멤버십이 필요합니다</div>;
    await renderDetail('any');
    expect(screen.getByTestId('membership-required')).toBeTruthy();
    expect(screen.getAllByTestId('back-link')).toHaveLength(1);
  });

  it('🔴 회원 판이 판정 못 함(null) → 공개 판으로 내려가고, 뒤로가기는 여전히 하나', async () => {
    state.authed = true;
    state.member = null;
    await renderDetail(postId(false));
    expect(screen.getByTestId('public-post-detail')).toBeTruthy();
    expect(screen.queryByTestId('member-post-detail')).toBeNull();
    expect(screen.getAllByTestId('back-link')).toHaveLength(1);
  });
});
