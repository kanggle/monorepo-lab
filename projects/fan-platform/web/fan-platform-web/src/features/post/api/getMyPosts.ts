import 'server-only';
import { gatewayFetch } from '@/shared/api/client';
import type { MyPostsPage } from '@/entities/post';

/**
 * The signed-in fan's own posts (TASK-FAN-FE-016).
 *
 * <p>This is the only read path that surfaces them: the feed is follow-based, so a fan's own
 * post never appears in their own feed, and `getPost` needs an id the fan has no way to know.
 */
export async function getMyPosts(
  accessToken: string | null,
  page = 0,
  size = 20,
): Promise<MyPostsPage> {
  const res = await gatewayFetch<MyPostsPage>('/api/v1/community/posts/mine', {
    accessToken,
    query: { page, size },
    cache: 'no-store',
  });
  return res.data;
}
