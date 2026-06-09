import 'server-only';
import { gatewayFetch } from '@/shared/api/client';
import type { MembershipList, MembershipListItem } from '@/entities/membership';

/**
 * The caller's memberships (newest window first). Returns `[]` on any error so
 * the page degrades to "no membership" rather than an error boundary — the
 * subscribe panel is still useful when the read fails.
 */
export async function getMemberships(
  accessToken: string | null,
): Promise<MembershipListItem[]> {
  try {
    const res = await gatewayFetch<MembershipList>('/api/v1/memberships', {
      accessToken,
      cache: 'no-store',
    });
    return res.data?.content ?? [];
  } catch {
    return [];
  }
}

/**
 * The single currently-active membership (read-time `active`), or `null`. A fan
 * holds at most one active membership in this slice; if multiple ever read
 * active, the newest (first) wins.
 */
export function currentActive(
  memberships: MembershipListItem[],
): MembershipListItem | null {
  return memberships.find((m) => m.active) ?? null;
}
