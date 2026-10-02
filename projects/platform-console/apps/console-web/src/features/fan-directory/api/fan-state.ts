import { redirect } from 'next/navigation';
import { ApiError } from '@/shared/api/errors';
import {
  listAgencies,
  getAgency,
  listArtists,
  getArtist,
  getArtistGroup,
} from './fan-api';
import type { Agency, Artist, ArtistGroup, Paged } from './fan-types';

/**
 * Server-side section state for the `(console)/fan/**` routes (TASK-MONO-751). Mirrors the
 * ecommerce `*-state.ts` waterfall: eligibility → read → { forbidden | notFound | degraded }.
 *
 *   - `401` → whole-session re-login (`redirect` throws).
 *   - `403` → inline «not permitted» (e.g. a token that is not `fan-platform`).
 *   - `404` on a detail read → `notFound`.
 *   - `503` / timeout / network / anything else → `degraded` (ONLY this section).
 */

export interface FanSectionFlags {
  notEligible: boolean;
  forbidden: boolean;
  notFound: boolean;
  degraded: boolean;
}

const FLAGS: FanSectionFlags = {
  notEligible: false,
  forbidden: false,
  notFound: false,
  degraded: false,
};

/** Maps a server-read error to the section flags. `401` redirects (throws). */
export function mapFanError(err: unknown, detail: boolean): FanSectionFlags {
  if (err instanceof ApiError && err.status === 401) {
    redirect('/login?error=session_expired');
  }
  if (err instanceof ApiError && err.status === 403) return { ...FLAGS, forbidden: true };
  if (detail && err instanceof ApiError && err.status === 404) return { ...FLAGS, notFound: true };
  return { ...FLAGS, degraded: true };
}

export interface FanSectionState<T> extends FanSectionFlags {
  data: T | null;
}

async function read<T>(
  eligible: boolean,
  detail: boolean,
  fn: () => Promise<T>,
): Promise<FanSectionState<T>> {
  if (!eligible) return { ...FLAGS, notEligible: true, data: null };
  try {
    return { ...FLAGS, data: await fn() };
  } catch (err) {
    return { ...mapFanError(err, detail), data: null };
  }
}

export function getAgenciesSectionState(eligible: boolean): Promise<FanSectionState<Paged<Agency>>> {
  return read(eligible, false, () => listAgencies({ page: 0 }));
}

export function getAgencyDetailSectionState(
  eligible: boolean,
  agencyId: string,
): Promise<FanSectionState<Agency>> {
  return read(eligible, true, () => getAgency(agencyId));
}

export function getArtistsSectionState(eligible: boolean): Promise<FanSectionState<Paged<Artist>>> {
  return read(eligible, false, () => listArtists({ page: 0 }));
}

export function getArtistDetailSectionState(
  eligible: boolean,
  artistId: string,
): Promise<FanSectionState<Artist>> {
  return read(eligible, true, () => getArtist(artistId));
}

export function getGroupDetailSectionState(
  eligible: boolean,
  groupId: string,
): Promise<FanSectionState<ArtistGroup>> {
  return read(eligible, true, () => getArtistGroup(groupId));
}
