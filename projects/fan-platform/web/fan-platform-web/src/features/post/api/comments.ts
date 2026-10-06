'use server';
import { revalidatePath } from 'next/cache';
import { gatewayFetch } from '@/shared/api/client';
import { getFanSession } from '@/shared/auth/session';
import { getComments } from '@/features/post/api/getComments';
import { ApiError } from '@/shared/api/errors';
import { FAN_COMMENT_BODY_MAX } from '@/features/post/lib/post-limits';
import type { Comment, CommentPage } from '@/entities/post';

/**
 * Comment write/read-more actions (TASK-FAN-FE-032).
 *
 * <p>All three return a result object rather than throwing — same reasoning
 * as `publishFanPost` (`features/post/api/actions.ts`): a thrown `ApiError`
 * crossing the Server Action boundary into a Client Component gets its
 * message stripped by Next.js in a production build, so the caller would
 * only ever see a generic "something went wrong". Returning `{ ok, message }`
 * keeps the backend's actual message (e.g. the 1..2000 bound, or
 * `MEMBERSHIP_REQUIRED`) visible to the UI.
 */

export type AddCommentResult = { ok: true; comment: Comment } | { ok: false; message: string };
export type DeleteCommentResult = { ok: true } | { ok: false; message: string };
export type LoadCommentsResult = { ok: true; page: CommentPage } | { ok: false; message: string };

export async function addComment(postId: string, body: string): Promise<AddCommentResult> {
  const trimmed = body.trim();
  if (trimmed.length === 0) {
    return { ok: false, message: '댓글 내용을 입력해 주세요.' };
  }
  if (trimmed.length > FAN_COMMENT_BODY_MAX) {
    return { ok: false, message: `댓글은 ${FAN_COMMENT_BODY_MAX.toLocaleString()}자를 넘을 수 없습니다.` };
  }

  const session = await getFanSession();
  try {
    const res = await gatewayFetch<Comment>(
      `/api/v1/community/posts/${encodeURIComponent(postId)}/comments`,
      {
        accessToken: session.accessToken,
        method: 'POST',
        body: { body: trimmed },
      },
    );
    revalidatePath(`/posts/${postId}`);
    return { ok: true, comment: res.data };
  } catch (err) {
    const message =
      err instanceof ApiError ? err.message : '댓글을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.';
    return { ok: false, message };
  }
}

/**
 * Delete the caller's own comment. `authorAccountId === viewer` is already
 * checked client-side to decide whether to show the delete button at all
 * (UX convenience only) — the actual trust boundary is this call reaching
 * community-service, which re-checks author-or-operator and answers 403
 * `PERMISSION_DENIED` for anyone else (`community-api.md` § Comments —
 * `DELETE`). A client that bypassed the hidden button and called this action
 * directly for someone else's comment would still get rejected here.
 */
export async function deleteComment(postId: string, commentId: string): Promise<DeleteCommentResult> {
  const session = await getFanSession();
  try {
    await gatewayFetch(
      `/api/v1/community/posts/${encodeURIComponent(postId)}/comments/${encodeURIComponent(commentId)}`,
      {
        accessToken: session.accessToken,
        method: 'DELETE',
      },
    );
    revalidatePath(`/posts/${postId}`);
    return { ok: true };
  } catch (err) {
    const message =
      err instanceof ApiError ? err.message : '댓글을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.';
    return { ok: false, message };
  }
}

/**
 * "더 보기" — the next comment page. A Server Action rather than a plain
 * client fetch: `shared/api/client.ts`'s `gatewayFetch` is the single fetch
 * boundary and is `server-only`-adjacent (no browser implementation), so a
 * Client Component cannot call it directly. Reuses `getComments` rather than
 * re-deriving the call.
 */
export async function loadMoreComments(
  postId: string,
  page: number,
  size = 20,
): Promise<LoadCommentsResult> {
  const session = await getFanSession();
  try {
    const nextPage = await getComments(session.accessToken, postId, page, size);
    return { ok: true, page: nextPage };
  } catch (err) {
    const message =
      err instanceof ApiError ? err.message : '댓글을 더 불러오지 못했습니다.';
    return { ok: false, message };
  }
}
