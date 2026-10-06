'use client';
import { useState, useTransition } from 'react';
import type { Comment } from '@/entities/post';
import { addComment, deleteComment, loadMoreComments } from '@/features/post/api/comments';
import { FAN_COMMENT_BODY_MAX } from '@/features/post/lib/post-limits';
import { Button } from '@/shared/ui/Button';

/**
 * Comment list + composer + own-comment delete (TASK-FAN-FE-032).
 *
 * <p>Owns its own `comments` array as client state rather than relying solely
 * on `revalidatePath` + an RSC re-render, so AC-2 ("같은 화면에서 다시
 * 보인다") is satisfied by a synchronous state update the moment `addComment`
 * resolves — the same reasoning `FollowButton` (TASK-FAN-FE-028) used for its
 * optimistic label flip, just applied one step later (after the server
 * confirms, not before) because unlike a follow toggle there is no local
 * value to flip to in advance — the server assigns `commentId`/`createdAt`.
 *
 * <p>Delete IS optimistic (removes immediately, rolls back on failure) —
 * mirrors `FollowButton`'s rollback pattern 1:1.
 */
export function CommentPanel({
  postId,
  initialComments,
  initialPage,
  initialHasNext,
  viewerAccountId,
}: {
  postId: string;
  initialComments: Comment[];
  initialPage: number;
  initialHasNext: boolean;
  /** `null` for a viewer with no session accountId — hides composer + delete. */
  viewerAccountId: string | null;
}) {
  const [comments, setComments] = useState<Comment[]>(initialComments);
  const [page, setPage] = useState(initialPage);
  const [hasNext, setHasNext] = useState(initialHasNext);
  const [body, setBody] = useState('');
  const [composeError, setComposeError] = useState<string | null>(null);
  const [loadMoreError, setLoadMoreError] = useState<string | null>(null);
  const [isSubmitting, startSubmit] = useTransition();
  const [isLoadingMore, startLoadMore] = useTransition();

  const trimmedLength = body.trim().length;
  const tooLong = trimmedLength > FAN_COMMENT_BODY_MAX;
  const canSubmit = trimmedLength > 0 && !tooLong && !isSubmitting;

  function onSubmit() {
    setComposeError(null);
    startSubmit(async () => {
      const result = await addComment(postId, body);
      if (!result.ok) {
        setComposeError(result.message);
        return;
      }
      setComments((prev) => [...prev, result.comment]);
      setBody('');
    });
  }

  function onDelete(commentId: string) {
    if (!window.confirm('이 댓글을 삭제할까요?')) return;
    const before = comments;
    setComments((prev) => prev.filter((c) => c.commentId !== commentId));
    startSubmit(async () => {
      const result = await deleteComment(postId, commentId);
      if (!result.ok) {
        setComments(before); // rollback — delete failed server-side (e.g. 403)
      }
    });
  }

  function onLoadMore() {
    setLoadMoreError(null);
    startLoadMore(async () => {
      const result = await loadMoreComments(postId, page + 1);
      if (!result.ok) {
        setLoadMoreError(result.message);
        return;
      }
      setComments((prev) => [...prev, ...result.page.content]);
      setPage(result.page.page);
      setHasNext(result.page.hasNext);
    });
  }

  return (
    <div data-testid="comment-panel" className="flex flex-col gap-4">
      {viewerAccountId ? (
        <form
          data-testid="comment-composer"
          onSubmit={(e) => {
            e.preventDefault();
            if (canSubmit) onSubmit();
          }}
          className="flex flex-col gap-2"
        >
          <textarea
            data-testid="comment-input"
            value={body}
            rows={3}
            onChange={(e) => setBody(e.target.value)}
            placeholder="댓글을 남겨보세요"
            className="rounded-md border border-ink-200 px-3 py-2 text-sm text-ink-900 dark:bg-ink-800 dark:text-ink-100 dark:border-ink-700"
          />
          <div className="flex items-center justify-between">
            <span className={['text-xs', tooLong ? 'text-red-600' : 'text-ink-500'].join(' ')}>
              {trimmedLength.toLocaleString()} / {FAN_COMMENT_BODY_MAX.toLocaleString()}
            </span>
            <Button type="submit" size="sm" disabled={!canSubmit} data-testid="comment-submit">
              {isSubmitting ? '등록하는 중…' : '댓글 등록'}
            </Button>
          </div>
          {composeError ? (
            <p data-testid="comment-compose-error" className="text-sm text-red-600">
              {composeError}
            </p>
          ) : null}
        </form>
      ) : null}

      {comments.length === 0 ? (
        <p data-testid="comment-empty" className="text-sm text-ink-500">
          아직 댓글이 없습니다.
        </p>
      ) : (
        <ul data-testid="comment-list" className="flex flex-col gap-3">
          {comments.map((c) => (
            <li
              key={c.commentId}
              data-testid="comment-item"
              className="rounded-md border border-ink-100 p-3"
            >
              <div className="flex items-start justify-between gap-2">
                <p className="whitespace-pre-line text-sm text-ink-800">{c.body}</p>
                {viewerAccountId && c.authorAccountId === viewerAccountId ? (
                  <button
                    type="button"
                    data-testid="comment-delete"
                    aria-label="댓글 삭제"
                    onClick={() => onDelete(c.commentId)}
                    className="shrink-0 text-xs text-ink-400 hover:text-red-600"
                  >
                    삭제
                  </button>
                ) : null}
              </div>
              <time className="mt-1 block text-xs text-ink-400" dateTime={c.createdAt}>
                {new Date(c.createdAt).toLocaleString('ko-KR')}
              </time>
            </li>
          ))}
        </ul>
      )}

      {hasNext ? (
        <button
          type="button"
          data-testid="comment-load-more"
          onClick={onLoadMore}
          disabled={isLoadingMore}
          className="self-center text-sm text-brand-600 hover:underline disabled:opacity-50"
        >
          {isLoadingMore ? '불러오는 중…' : '더 보기'}
        </button>
      ) : null}
      {loadMoreError ? (
        <p data-testid="comment-load-more-error" className="self-center text-sm text-red-600">
          {loadMoreError}
        </p>
      ) : null}
    </div>
  );
}
