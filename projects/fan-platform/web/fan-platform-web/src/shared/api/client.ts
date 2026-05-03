import { env } from '@/shared/config/env';
import { ApiError, toApiError } from '@/shared/api/errors';

/**
 * Server-side gateway client.
 *
 * Browser-side fetches are intentionally not implemented in this module — all
 * read paths go through Server Components (RSC fetch) and write paths through
 * Server Actions (`'use server'`). This keeps the access_token on the server
 * and out of the client bundle. See § "Server vs Client Boundary" in the
 * frontend/auth-client SKILL.
 */

/** Shape of `{ data, meta }` envelope. Meta varies (timestamp / page info). */
export interface Envelope<T> {
  data: T;
  meta?: Record<string, unknown>;
}

export interface RequestOptions {
  /** Bearer access token — pass `null` for unauthenticated calls (rare). */
  accessToken: string | null;
  /** HTTP method (default GET). */
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  /** Optional JSON body (auto-serialized). */
  body?: unknown;
  /** Optional query string params. */
  query?: Record<string, string | number | undefined>;
  /** Pass to RSC fetch for incremental cache (default 'no-store'). */
  cache?: RequestCache;
  /** Next.js fetch revalidation seconds (overrides cache when set). */
  revalidate?: number;
  /** Tag for selective revalidation. */
  tags?: string[];
  /** AbortController signal. */
  signal?: AbortSignal;
}

function buildUrl(path: string, query?: RequestOptions['query']): string {
  const base = env.gatewayInternalUrl.replace(/\/+$/, '');
  const cleanPath = path.startsWith('/') ? path : `/${path}`;
  if (!query) return `${base}${cleanPath}`;
  const search = new URLSearchParams();
  for (const [k, v] of Object.entries(query)) {
    if (v !== undefined && v !== null) search.set(k, String(v));
  }
  const qs = search.toString();
  return qs ? `${base}${cleanPath}?${qs}` : `${base}${cleanPath}`;
}

/**
 * Low-level request used by feature-specific API modules. Throws `ApiError`
 * on non-2xx; the caller decides how to surface to the UI (Loading/Error
 * states, redirect to /login, etc.).
 */
export async function gatewayFetch<TBody = unknown>(
  path: string,
  opts: RequestOptions,
): Promise<Envelope<TBody>> {
  const url = buildUrl(path, opts.query);
  const headers: Record<string, string> = {
    Accept: 'application/json',
  };
  if (opts.accessToken) headers.Authorization = `Bearer ${opts.accessToken}`;
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';

  const init: RequestInit & { next?: { revalidate?: number; tags?: string[] } } = {
    method: opts.method ?? 'GET',
    headers,
    signal: opts.signal,
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
    cache: opts.revalidate !== undefined ? undefined : (opts.cache ?? 'no-store'),
  };
  if (opts.revalidate !== undefined || opts.tags) {
    init.next = { revalidate: opts.revalidate, tags: opts.tags };
  }

  let response: Response;
  try {
    response = await fetch(url, init);
  } catch (cause) {
    // Network error / DNS / ECONNREFUSED. Surface as ApiError(0) so callers
    // can apply the same `.catch(() => null)` fallback as for HTTP errors.
    throw new ApiError(0, {
      code: 'NETWORK_ERROR',
      message: cause instanceof Error ? cause.message : 'network error',
    });
  }

  if (response.status === 204) {
    return { data: undefined as unknown as TBody };
  }

  if (!response.ok) {
    throw await toApiError(response);
  }

  // Success — community-api / artist-api wrap successful payloads in
  // `{ data, meta }`. Defensive: if a backend returns a bare object we still
  // wrap it so callers always read `.data`.
  const json = (await response.json()) as Envelope<TBody> | TBody;
  if (json && typeof json === 'object' && 'data' in (json as Record<string, unknown>)) {
    return json as Envelope<TBody>;
  }
  return { data: json as TBody };
}
