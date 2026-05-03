import 'server-only';
import { gatewayFetch } from '@/shared/api/client';
import type { Post } from '@/entities/post';

export async function getPost(accessToken: string | null, postId: string): Promise<Post> {
  const res = await gatewayFetch<Post>(`/api/v1/community/posts/${encodeURIComponent(postId)}`, {
    accessToken,
    cache: 'no-store',
  });
  return res.data;
}
