import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';

/**
 * TASK-FAN-FE-029 — `/posts/[id]` must restore the viewer's own reaction after
 * reload. Same class as TASK-FAN-FE-017 (follow button): the component already
 * accepts an initial-value prop correctly — the defect is the **call site**
 * (`memberPostDetail.tsx`) never asking the server and never passing the
 * answer through. The two-axis structure below is copied from
 * `follow-status.test.tsx` per this ticket's Dependency Markers.
 *
 * 🔴 Deployment-order axis (field-absence tolerance): the contract
 * (`community-api.md` § `myReaction`, TASK-FAN-BE-051) ships with this ticket,
 * but the currently-deployed demo backend predates it and will not send the
 * key at all until the next AMI rebake — not even as `null`. A fixture that
 * always includes `myReaction` would never exercise that gap, so one cell
 * below deletes the key entirely rather than setting it to `null`.
 */

const { getPost, getComments, setReaction, removeReaction } = vi.hoisted(() => ({
  getPost: vi.fn(),
  // TASK-FAN-FE-032 — `memberPostDetail` now also calls `getComments`
  // (`server-only`, same reason as `getPost` below). Default rejection is
  // the realistic deployment-order case (`comment-section-404.test.tsx`
  // covers that behaviour directly); this file only cares about ReactionBar.
  getComments: vi.fn().mockRejectedValue(new Error('not relevant to this suite')),
  setReaction: vi.fn(),
  removeReaction: vi.fn(),
}));

// Mocked wholesale: the real getPost carries `server-only`, and reactions.ts
// reaches `@/shared/auth/session` (also `server-only`) — neither loads in
// jsdom. ReactionBar itself is exercised directly in axis 1 below.
vi.mock('@/features/post/api/getPost', () => ({ getPost }));
vi.mock('@/features/post/api/getComments', () => ({ getComments }));
vi.mock('@/features/post/api/reactions', () => ({ setReaction, removeReaction }));
vi.mock('@/shared/auth/session', () => ({
  getFanSession: async () => ({ accessToken: 'token-abc' }),
}));

import { ReactionBar } from '@/features/post/ui/ReactionBar';
import { memberPostDetail } from '@/features/post/ui/memberPostDetail';
import type { Post } from '@/entities/post';

function basePost(overrides: Partial<Post> = {}): Post {
  return {
    postId: 'p-1',
    tenantId: 'fan-platform',
    postType: 'ARTIST_POST',
    visibility: 'PUBLIC',
    status: 'PUBLISHED',
    authorAccountId: 'acc-artist-1',
    title: '제목',
    body: '본문',
    mediaRefs: [],
    commentCount: 0,
    reactionCount: 3,
    myReaction: null,
    publishedAt: '2026-05-03T00:00:00Z',
    createdAt: '2026-05-03T00:00:00Z',
    updatedAt: '2026-05-03T00:00:00Z',
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
});

// ---- axis 1: the component honours the value it is handed --------------------

describe('ReactionBar — initialReaction 을 그대로 그린다', () => {
  it('🔴 AC-2/AC-3 대조군: initialReaction=LIKE 면 LIKE 만 aria-pressed=true', () => {
    render(<ReactionBar postId="p-1" totalReactions={3} initialReaction="LIKE" />);
    expect(screen.getByLabelText('좋아요').getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByLabelText('사랑해요').getAttribute('aria-pressed')).toBe('false');
    expect(screen.getByLabelText('대박').getAttribute('aria-pressed')).toBe('false');
    expect(screen.getByLabelText('슬퍼요').getAttribute('aria-pressed')).toBe('false');
  });

  it('initialReaction=null (또는 생략) 이면 전부 false', () => {
    render(<ReactionBar postId="p-1" totalReactions={0} initialReaction={null} />);
    for (const label of ['좋아요', '사랑해요', '대박', '슬퍼요']) {
      expect(screen.getByLabelText(label).getAttribute('aria-pressed')).toBe('false');
    }

    render(<ReactionBar postId="p-2" totalReactions={0} />);
    expect(screen.getAllByLabelText('좋아요')[1].getAttribute('aria-pressed')).toBe('false');
  });
});

// ---- axis 2: the CALL SITE actually asks, and passes the answer through ------

describe('/posts/[id] 회원 판 — 서버가 아는 내 반응이 버튼까지 도달한다', () => {
  it('🔴 AC-1/AC-2: 반응한 글은 새로고침 뒤에도 해당 버튼이 aria-pressed=true', async () => {
    getPost.mockResolvedValue(basePost({ myReaction: 'LOVE' }));
    const element = await memberPostDetail('p-1');
    render(<>{element}</>);

    expect(screen.getByLabelText('사랑해요').getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByLabelText('좋아요').getAttribute('aria-pressed')).toBe('false');
  });

  it('🔴 AC-2 짝: 반응하지 않은 글은 전부 aria-pressed=false — 두 칸이 갈라져야 잰 것이다', async () => {
    getPost.mockResolvedValue(basePost({ myReaction: null }));
    const element = await memberPostDetail('p-2');
    render(<>{element}</>);

    for (const label of ['좋아요', '사랑해요', '대박', '슬퍼요']) {
      expect(screen.getByLabelText(label).getAttribute('aria-pressed')).toBe('false');
    }
  });

  it('🔴 AC-3: 반응을 바꾼 뒤(LIKE→LOVE) 새 반응만 true, 이전 반응은 false', async () => {
    getPost.mockResolvedValue(basePost({ myReaction: 'LOVE' }));
    const element = await memberPostDetail('p-1');
    render(<>{element}</>);

    expect(screen.getByLabelText('사랑해요').getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByLabelText('좋아요').getAttribute('aria-pressed')).toBe('false');
  });

  it('🔴 배포 순서 안전망: myReaction 키가 응답에 아예 없어도(구버전 데모 백엔드) 크래시 없이 전부 false', async () => {
    const post = basePost();
    delete (post as Partial<Post>).myReaction; // key absent, not even `null`
    getPost.mockResolvedValue(post);

    const element = await memberPostDetail('p-3');
    expect(() => render(<>{element}</>)).not.toThrow();

    for (const label of ['좋아요', '사랑해요', '대박', '슬퍼요']) {
      const btn = screen.getByLabelText(label);
      expect(btn.getAttribute('aria-pressed')).toBe('false');
      expect(btn.textContent).not.toContain('undefined');
    }
  });
});
