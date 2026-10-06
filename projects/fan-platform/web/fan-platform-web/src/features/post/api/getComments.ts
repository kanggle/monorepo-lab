import 'server-only';
import { gatewayFetch } from '@/shared/api/client';
import type { CommentPage } from '@/entities/post';

/**
 * Comment list, oldest-first (TASK-FAN-BE-052 / `community-api.md` § Comments
 * — List). Same visibility gate as `getPost`/`setReaction` — a 403
 * `MEMBERSHIP_REQUIRED` here never actually fires in practice because the
 * caller (`memberPostDetail`) only reaches this call after `getPost` already
 * passed the identical gate for the same post.
 *
 * 🔴 Deployment-order gap (TASK-FAN-FE-032 § AMI 재굽기 간극) — the currently
 * deployed demo backend predates `TASK-FAN-BE-052` and does not serve this
 * route at all (plain 404, not even the structured `POST_NOT_FOUND` envelope
 * once the route exists). This function does NOT swallow that — it throws
 * `ApiError` same as `getPost` does, same as every other read in this
 * codebase. The caller (`commentSection` in `memberPostDetail.tsx`) is the
 * one that decides to catch and hide the section; keeping this module's
 * contract "throws on failure" matches `getPost`/`getMyPosts` and avoids a
 * special "returns empty page on error" shape that every other reader here
 * does not have.
 */
export async function getComments(
  accessToken: string | null,
  postId: string,
  page = 0,
  size = 20,
): Promise<CommentPage> {
  const res = await gatewayFetch<CommentPage>(
    `/api/v1/community/posts/${encodeURIComponent(postId)}/comments`,
    {
      accessToken,
      query: { page, size },
      cache: 'no-store',
    },
  );
  return res.data;
}
