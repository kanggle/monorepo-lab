import 'server-only';
import { gatewayFetch } from '@/shared/api/client';
import type { FeedPage } from '@/entities/post';

/**
 * Server-only feed fetcher. Errors propagate as `ApiError` — the page wraps
 * with `try/catch` and renders an `ErrorState` so the frontend doesn't crash
 * when the gateway is unreachable.
 */
export async function getFeed(
  accessToken: string | null,
  page = 0,
  size = 20,
): Promise<FeedPage> {
  const res = await gatewayFetch<FeedPage>('/api/v1/community/feed', {
    accessToken,
    query: { page, size },
    cache: 'no-store',
  });
  return res.data;
}
