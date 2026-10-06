import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';

/**
 * TASK-FAN-FE-032 § 🔴 "데모 백엔드가 아직 이 경로를 서빙하지 않는 동안에도 화면이 깨지면
 * 안 된다" — AC-5.
 *
 * Deployment-order axis (same class as `reaction-restore.test.tsx`'s
 * field-absence cell, `TASK-FAN-BE-051`/`-052`): the currently-deployed demo
 * backend predates `TASK-FAN-BE-052` and answers `GET …/comments` with a
 * plain 404 (not even the structured envelope). `getComments` is mocked to
 * reject with an `ApiError(404, …)` to reproduce exactly that — the
 * assertion is that `memberPostDetail` still renders the article (body +
 * ReactionBar) and simply omits `comment-section`, never throwing up to the
 * page's error boundary.
 */

const { getPost, getComments, setReaction, removeReaction } = vi.hoisted(() => ({
  getPost: vi.fn(),
  getComments: vi.fn(),
  setReaction: vi.fn(),
  removeReaction: vi.fn(),
}));

vi.mock('@/features/post/api/getPost', () => ({ getPost }));
vi.mock('@/features/post/api/getComments', () => ({ getComments }));
vi.mock('@/features/post/api/reactions', () => ({ setReaction, removeReaction }));
vi.mock('@/shared/auth/session', () => ({
  getFanSession: async () => ({ accessToken: 'token-abc', accountId: 'acc-me' }),
}));

import { memberPostDetail } from '@/features/post/ui/memberPostDetail';
import { ApiError } from '@/shared/api/errors';
import type { Post, CommentPage } from '@/entities/post';

function basePost(overrides: Partial<Post> = {}): Post {
  return {
    postId: 'p-1',
    tenantId: 'fan-platform',
    postType: 'ARTIST_POST',
    visibility: 'PUBLIC',
    status: 'PUBLISHED',
    authorAccountId: 'acc-artist-1',
    title: '제목',
    body: '본문입니다',
    mediaRefs: [],
    commentCount: 2,
    reactionCount: 3,
    myReaction: null,
    publishedAt: '2026-05-03T00:00:00Z',
    createdAt: '2026-05-03T00:00:00Z',
    updatedAt: '2026-05-03T00:00:00Z',
    ...overrides,
  };
}

function commentPage(overrides: Partial<CommentPage> = {}): CommentPage {
  return {
    content: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('memberPostDetail — AC-5 댓글 404/부재 허용', () => {
  it('🔴 bite: 댓글 목록 호출이 404 ApiError 를 던지면, 댓글 섹션은 없지만 본문·반응은 정상 렌더된다', async () => {
    getPost.mockResolvedValue(basePost());
    getComments.mockRejectedValue(new ApiError(404, { code: 'NOT_FOUND', message: 'no route' }));

    const element = await memberPostDetail('p-1');
    expect(() => render(<>{element}</>)).not.toThrow();

    // 본문 · 반응은 그대로 있다 — 전체 페이지가 깨지지 않았다는 증거.
    expect(screen.getByTestId('member-post-detail')).toBeInTheDocument();
    expect(screen.getByText('본문입니다')).toBeInTheDocument();
    expect(screen.getByLabelText('좋아요')).toBeInTheDocument();

    // 댓글 섹션만 숨겨진다.
    expect(screen.queryByTestId('comment-section')).not.toBeInTheDocument();
  });

  it('네트워크 에러(ApiError status=0)에도 같은 방식으로 섹션만 숨는다', async () => {
    getPost.mockResolvedValue(basePost());
    getComments.mockRejectedValue(new ApiError(0, { code: 'NETWORK_ERROR', message: 'fetch failed' }));

    const element = await memberPostDetail('p-1');
    render(<>{element}</>);

    expect(screen.getByTestId('member-post-detail')).toBeInTheDocument();
    expect(screen.queryByTestId('comment-section')).not.toBeInTheDocument();
  });

  it('대조군 — 댓글 호출이 성공하면 섹션이 보이고 본문도 그대로 있다(같은 렌더)', async () => {
    getPost.mockResolvedValue(basePost());
    getComments.mockResolvedValue(
      commentPage({
        content: [
          {
            commentId: 'c-1',
            postId: 'p-1',
            tenantId: 'fan-platform',
            authorAccountId: 'acc-other',
            body: '댓글 본문',
            createdAt: '2026-05-04T00:00:00Z',
          },
        ],
        totalElements: 1,
      }),
    );

    const element = await memberPostDetail('p-1');
    render(<>{element}</>);

    expect(screen.getByTestId('member-post-detail')).toBeInTheDocument();
    expect(screen.getByTestId('comment-section')).toBeInTheDocument();
    expect(screen.getByText('댓글 본문')).toBeInTheDocument();
  });

  it('AC-1 짝: 성공했지만 빈 목록이면 "아직 댓글이 없습니다" — 깨진 것처럼 보이지 않는다', async () => {
    getPost.mockResolvedValue(basePost());
    getComments.mockResolvedValue(commentPage());

    const element = await memberPostDetail('p-1');
    render(<>{element}</>);

    expect(screen.getByTestId('comment-section')).toBeInTheDocument();
    expect(screen.getByTestId('comment-empty')).toBeInTheDocument();
  });
});
