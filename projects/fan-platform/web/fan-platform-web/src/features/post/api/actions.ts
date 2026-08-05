'use server';
import { revalidatePath } from 'next/cache';
import { gatewayFetch } from '@/shared/api/client';
import { getFanSession } from '@/shared/auth/session';
import { ApiError } from '@/shared/api/errors';
import { FAN_POST_BODY_MAX, FAN_POST_TITLE_MAX } from '@/features/post/lib/post-limits';
import type { Post } from '@/entities/post';

export type PublishFanPostResult =
  | { ok: true; postId: string }
  | { ok: false; message: string };

/**
 * Publish a fan post (TASK-FAN-FE-016).
 *
 * <p><strong>`visibility` is fixed to PUBLIC and is not a parameter.</strong> The API accepts
 * all three tiers for either post type — `PublishPostUseCase` checks `postType` and never
 * looks at `visibility` — so a fan *can* publish a gated post today. Offering that here would
 * produce a post almost nobody can read and that earns its author nothing: membership is
 * platform-scoped, so a fan's MEMBERS_ONLY post is hidden from every non-subscriber while the
 * subscription revenue it gates belongs to the platform, not to them. The tier exists for
 * artist monetization. Not exposing the control is this ticket's decision (AC-2); the API-side
 * gap is recorded separately rather than silently narrowed here, because the contract
 * currently documents all three values as accepted.
 */
export async function publishFanPost(
  title: string,
  body: string,
): Promise<PublishFanPostResult> {
  const trimmedBody = body.trim();
  const trimmedTitle = title.trim();
  if (trimmedBody.length === 0) {
    return { ok: false, message: '내용을 입력해 주세요.' };
  }
  if (trimmedBody.length > FAN_POST_BODY_MAX) {
    return { ok: false, message: `내용은 ${FAN_POST_BODY_MAX.toLocaleString()}자를 넘을 수 없습니다.` };
  }
  if (trimmedTitle.length > FAN_POST_TITLE_MAX) {
    return { ok: false, message: `제목은 ${FAN_POST_TITLE_MAX}자를 넘을 수 없습니다.` };
  }

  const session = await getFanSession();
  let created: Post;
  try {
    const res = await gatewayFetch<Post>('/api/v1/community/posts', {
      accessToken: session.accessToken,
      method: 'POST',
      body: {
        postType: 'FAN_POST',
        visibility: 'PUBLIC',
        // The DTO allows an absent title; send undefined rather than '' so an empty
        // field does not become a zero-length title on the row.
        title: trimmedTitle.length > 0 ? trimmedTitle : undefined,
        body: trimmedBody,
      },
    });
    created = res.data;
  } catch (err) {
    const message =
      err instanceof ApiError ? err.message : '글을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.';
    return { ok: false, message };
  }

  // The new post does NOT appear in the author's own feed — the feed is follow-based and
  // nobody follows themselves. `/me/posts` is the only place it becomes visible, so it is
  // the one that must be revalidated for the redirect target to show it.
  revalidatePath('/me/posts');
  return { ok: true, postId: created.postId };
}
