import 'server-only';
import { gatewayFetch } from '@/shared/api/client';
import type { Artist, ArtistPage } from '@/entities/artist';

export async function getArtists(
  accessToken: string | null,
  query: { q?: string; page?: number; size?: number } = {},
): Promise<ArtistPage> {
  const res = await gatewayFetch<Artist[]>('/api/v1/artists', {
    accessToken,
    query: {
      q: query.q,
      page: query.page ?? 0,
      size: query.size ?? 20,
    },
    cache: 'no-store',
  });
  // artist-api returns `data: Artist[]` with paging in `meta`. Normalize to
  // ArtistPage so consumers get the same shape regardless of envelope.
  const meta = (res.meta ?? {}) as Record<string, number | undefined>;
  return {
    content: res.data,
    page: meta.page ?? 0,
    size: meta.size ?? 20,
    totalElements: meta.totalElements ?? res.data.length,
    totalPages: meta.totalPages ?? 1,
  };
}

export async function getArtist(accessToken: string | null, id: string): Promise<Artist> {
  const res = await gatewayFetch<Artist>(`/api/v1/artists/${encodeURIComponent(id)}`, {
    accessToken,
    cache: 'no-store',
  });
  return res.data;
}
