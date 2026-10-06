import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

/**
 * TASK-FAN-FE-032 — `CommentPanel` unit coverage (list / compose / own-delete).
 * `memberPostDetail`/`commentSection`'s 404-safety is covered separately in
 * `comment-section-404.test.tsx` — this file exercises the client component
 * in isolation, same split `reaction-restore.test.tsx` uses between
 * `ReactionBar` (axis 1) and the call site (axis 2).
 */

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

const { addComment, deleteComment, loadMoreComments } = vi.hoisted(() => ({
  addComment: vi.fn(),
  deleteComment: vi.fn(),
  loadMoreComments: vi.fn(),
}));
vi.mock('@/features/post/api/comments', () => ({ addComment, deleteComment, loadMoreComments }));

import { CommentPanel } from '@/features/post/ui/CommentPanel';
import type { Comment } from '@/entities/post';

function comment(overrides: Partial<Comment> = {}): Comment {
  return {
    commentId: 'c-1',
    postId: 'p-1',
    tenantId: 'fan-platform',
    authorAccountId: 'acc-other',
    body: '댓글 본문',
    createdAt: '2026-10-01T00:00:00Z',
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.spyOn(window, 'confirm').mockReturnValue(true);
});

describe('CommentPanel — AC-1 목록 / 빈 목록', () => {
  it('빈 목록이면 "아직 댓글이 없습니다" 가 보인다 — 에러로 보이지 않는다', () => {
    render(
      <CommentPanel
        postId="p-1"
        initialComments={[]}
        initialPage={0}
        initialHasNext={false}
        viewerAccountId="acc-me"
      />,
    );
    expect(screen.getByTestId('comment-empty')).toHaveTextContent('아직 댓글이 없습니다');
    expect(screen.queryByTestId('comment-list')).not.toBeInTheDocument();
  });

  it('댓글이 있으면 작성 순서(배열 순서) 그대로 렌더된다', () => {
    const comments = [comment({ commentId: 'c-1', body: '첫' }), comment({ commentId: 'c-2', body: '둘째' })];
    render(
      <CommentPanel
        postId="p-1"
        initialComments={comments}
        initialPage={0}
        initialHasNext={false}
        viewerAccountId="acc-me"
      />,
    );
    const items = screen.getAllByTestId('comment-item');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent('첫');
    expect(items[1]).toHaveTextContent('둘째');
  });
});

describe('CommentPanel — AC-4 비로그인/뷰어 없음', () => {
  it('viewerAccountId=null 이면 composer 가 없다', () => {
    render(
      <CommentPanel
        postId="p-1"
        initialComments={[comment()]}
        initialPage={0}
        initialHasNext={false}
        viewerAccountId={null}
      />,
    );
    expect(screen.queryByTestId('comment-composer')).not.toBeInTheDocument();
    // 목록 자체는 보인다.
    expect(screen.getByTestId('comment-list')).toBeInTheDocument();
  });

  it('viewerAccountId=null 이면 어떤 댓글에도 삭제 버튼이 없다', () => {
    render(
      <CommentPanel
        postId="p-1"
        initialComments={[comment({ authorAccountId: 'acc-me' })]}
        initialPage={0}
        initialHasNext={false}
        viewerAccountId={null}
      />,
    );
    expect(screen.queryByTestId('comment-delete')).not.toBeInTheDocument();
  });
});

describe('CommentPanel — AC-3 본인 삭제', () => {
  it('🔴 bite: 본인이 쓴 댓글에만 삭제 버튼이 보이고, 남의 댓글에는 안 보인다', () => {
    render(
      <CommentPanel
        postId="p-1"
        initialComments={[
          comment({ commentId: 'c-mine', authorAccountId: 'acc-me' }),
          comment({ commentId: 'c-other', authorAccountId: 'acc-other' }),
        ]}
        initialPage={0}
        initialHasNext={false}
        viewerAccountId="acc-me"
      />,
    );
    const deleteButtons = screen.getAllByTestId('comment-delete');
    // 2개 댓글 중 1개만 본인 것 → 삭제 버튼도 1개뿐이어야 한다.
    expect(deleteButtons).toHaveLength(1);
  });

  it('삭제 확인 후 성공하면 목록에서 그 댓글이 사라진다', async () => {
    deleteComment.mockResolvedValue({ ok: true });
    const user = userEvent.setup();
    render(
      <CommentPanel
        postId="p-1"
        initialComments={[comment({ commentId: 'c-mine', authorAccountId: 'acc-me', body: '지울댓글' })]}
        initialPage={0}
        initialHasNext={false}
        viewerAccountId="acc-me"
      />,
    );
    await user.click(screen.getByTestId('comment-delete'));
    expect(deleteComment).toHaveBeenCalledWith('p-1', 'c-mine');
    expect(await screen.findByTestId('comment-empty')).toBeInTheDocument();
  });

  it('confirm 을 취소하면 삭제 호출이 일어나지 않는다', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();
    render(
      <CommentPanel
        postId="p-1"
        initialComments={[comment({ commentId: 'c-mine', authorAccountId: 'acc-me' })]}
        initialPage={0}
        initialHasNext={false}
        viewerAccountId="acc-me"
      />,
    );
    await user.click(screen.getByTestId('comment-delete'));
    expect(deleteComment).not.toHaveBeenCalled();
    expect(screen.getByTestId('comment-item')).toBeInTheDocument();
  });

  it('🔴 롤백: 서버가 403 등으로 거부하면(ok:false) 삭제한 댓글이 되돌아온다 — 프런트 숨김이 신뢰 경계가 아님', async () => {
    deleteComment.mockResolvedValue({ ok: false, message: '권한이 없습니다.' });
    const user = userEvent.setup();
    render(
      <CommentPanel
        postId="p-1"
        initialComments={[comment({ commentId: 'c-mine', authorAccountId: 'acc-me', body: '지울댓글' })]}
        initialPage={0}
        initialHasNext={false}
        viewerAccountId="acc-me"
      />,
    );
    await user.click(screen.getByTestId('comment-delete'));
    // optimistic 제거 후 서버가 거부하면 롤백되어 다시 보여야 한다.
    expect(await screen.findByText('지울댓글')).toBeInTheDocument();
  });
});

describe('CommentPanel — AC-2 작성', () => {
  it('🔴 bite: 서버 액션이 성공하면 같은 화면에서 새 댓글이 바로 보인다(브라우저 증거)', async () => {
    const created = comment({ commentId: 'c-new', authorAccountId: 'acc-me', body: '새 댓글' });
    addComment.mockResolvedValue({ ok: true, comment: created });

    const user = userEvent.setup();
    render(
      <CommentPanel
        postId="p-1"
        initialComments={[]}
        initialPage={0}
        initialHasNext={false}
        viewerAccountId="acc-me"
      />,
    );

    await user.type(screen.getByTestId('comment-input'), '새 댓글');
    await user.click(screen.getByTestId('comment-submit'));

    expect(addComment).toHaveBeenCalledWith('p-1', '새 댓글');
    expect(await screen.findByText('새 댓글')).toBeInTheDocument();
    // 입력창은 성공 후 비워진다.
    expect(screen.getByTestId('comment-input')).toHaveValue('');
  });

  it('빈 입력이면 제출 버튼이 disabled', () => {
    render(
      <CommentPanel
        postId="p-1"
        initialComments={[]}
        initialPage={0}
        initialHasNext={false}
        viewerAccountId="acc-me"
      />,
    );
    expect(screen.getByTestId('comment-submit')).toBeDisabled();
  });

  it('2000자를 넘으면 제출 버튼이 disabled 되고 에러 안내가 보인다', async () => {
    const user = userEvent.setup();
    render(
      <CommentPanel
        postId="p-1"
        initialComments={[]}
        initialPage={0}
        initialHasNext={false}
        viewerAccountId="acc-me"
      />,
    );
    const input = screen.getByTestId('comment-input');
    await user.click(input);
    // fireEvent 로 긴 문자열을 한 번에 넣어 user.type 의 성능 문제를 피한다.
    const longText = 'a'.repeat(2001);
    await user.paste(longText);
    expect(screen.getByTestId('comment-submit')).toBeDisabled();
    expect(addComment).not.toHaveBeenCalled();
  });

  it('서버 액션이 실패(ok:false)하면 에러 메시지가 보이고 목록은 바뀌지 않는다', async () => {
    addComment.mockResolvedValue({ ok: false, message: '댓글을 저장하지 못했습니다.' });
    const user = userEvent.setup();
    render(
      <CommentPanel
        postId="p-1"
        initialComments={[]}
        initialPage={0}
        initialHasNext={false}
        viewerAccountId="acc-me"
      />,
    );
    await user.type(screen.getByTestId('comment-input'), '실패할 댓글');
    await user.click(screen.getByTestId('comment-submit'));

    expect(await screen.findByTestId('comment-compose-error')).toHaveTextContent(
      '댓글을 저장하지 못했습니다.',
    );
    expect(screen.getByTestId('comment-empty')).toBeInTheDocument();
  });

  it('연타는 isSubmitting 동안 제출 버튼이 disabled 로 막힌다', async () => {
    const { promise } = deferred<{ ok: true; comment: Comment }>();
    addComment.mockReturnValue(promise);
    const user = userEvent.setup();
    render(
      <CommentPanel
        postId="p-1"
        initialComments={[]}
        initialPage={0}
        initialHasNext={false}
        viewerAccountId="acc-me"
      />,
    );
    await user.type(screen.getByTestId('comment-input'), '댓글');
    await user.click(screen.getByTestId('comment-submit'));
    expect(screen.getByTestId('comment-submit')).toBeDisabled();
    expect(addComment).toHaveBeenCalledTimes(1);
  });
});

describe('CommentPanel — 더 보기(페이지네이션)', () => {
  it('hasNext=false 면 "더 보기" 버튼이 없다', () => {
    render(
      <CommentPanel
        postId="p-1"
        initialComments={[comment()]}
        initialPage={0}
        initialHasNext={false}
        viewerAccountId="acc-me"
      />,
    );
    expect(screen.queryByTestId('comment-load-more')).not.toBeInTheDocument();
  });

  it('"더 보기" 클릭 시 다음 페이지가 기존 목록 뒤에 붙는다', async () => {
    loadMoreComments.mockResolvedValue({
      ok: true,
      page: {
        content: [comment({ commentId: 'c-2', body: '두번째 페이지' })],
        page: 1,
        size: 1,
        totalElements: 2,
        totalPages: 2,
        hasNext: false,
      },
    });
    const user = userEvent.setup();
    render(
      <CommentPanel
        postId="p-1"
        initialComments={[comment({ commentId: 'c-1', body: '첫 페이지' })]}
        initialPage={0}
        initialHasNext={true}
        viewerAccountId="acc-me"
      />,
    );
    await user.click(screen.getByTestId('comment-load-more'));
    expect(loadMoreComments).toHaveBeenCalledWith('p-1', 1);
    expect(await screen.findByText('두번째 페이지')).toBeInTheDocument();
    expect(screen.getByText('첫 페이지')).toBeInTheDocument();
    // hasNext 가 false 로 갱신됐으므로 버튼이 사라진다.
    expect(screen.queryByTestId('comment-load-more')).not.toBeInTheDocument();
  });

  it('"더 보기" 가 실패하면 에러 안내만 보이고 기존 목록은 그대로다', async () => {
    loadMoreComments.mockResolvedValue({ ok: false, message: '댓글을 더 불러오지 못했습니다.' });
    const user = userEvent.setup();
    render(
      <CommentPanel
        postId="p-1"
        initialComments={[comment({ commentId: 'c-1', body: '첫 페이지' })]}
        initialPage={0}
        initialHasNext={true}
        viewerAccountId="acc-me"
      />,
    );
    await user.click(screen.getByTestId('comment-load-more'));
    expect(await screen.findByTestId('comment-load-more-error')).toHaveTextContent(
      '댓글을 더 불러오지 못했습니다.',
    );
    expect(screen.getByText('첫 페이지')).toBeInTheDocument();
  });
});
