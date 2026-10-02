'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import {
  AgencySchema,
  AgencyPageSchema,
  ArtistSchema,
  ArtistPageSchema,
  ArtistGroupSchema,
  type Agency,
  type Artist,
  type ArtistGroup,
  type Paged,
  type ArtistListParams,
  type FanListParams,
  type CreateAgencyBody,
  type CreateArtistBody,
  type UpdateArtistBody,
  type ArtistStatusBody,
  type CreateGroupBody,
} from '../api/fan-types';

/**
 * Client-side fan-directory hooks (TASK-MONO-751). Every call goes to the same-origin
 * `/api/fan/**` routes, which attach the HttpOnly domain-facing token server-side — the
 * browser never holds a token and never calls the fan gateway (ADR-MONO-081: console
 * proxies live in console-web's own server).
 */

const FAN_KEY = 'fan-directory';

function qs(params: FanListParams & { q?: string }): string {
  const s = new URLSearchParams();
  s.set('page', String(Math.max(0, params.page ?? 0)));
  if (params.size) s.set('size', String(params.size));
  if (params.q && params.q.trim() !== '') s.set('q', params.q.trim());
  return s.toString();
}

const enc = (v: string) => encodeURIComponent(v);

// --- agencies -----------------------------------------------------------------

export function useAgencies(params: FanListParams, initial?: Paged<Agency>) {
  const seeded = initial !== undefined && (params.page ?? 0) === 0;
  return useQuery({
    queryKey: [FAN_KEY, 'agencies', params.page ?? 0, params.size ?? 0] as const,
    queryFn: async () =>
      AgencyPageSchema.parse(await apiClient.get<unknown>(`/api/fan/agencies?${qs(params)}`)),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
  });
}

/** All ACTIVE agencies (first 100) — the affiliation picker's options. */
export function useActiveAgencyOptions() {
  return useQuery({
    queryKey: [FAN_KEY, 'agency-options'] as const,
    queryFn: async () => {
      const page = AgencyPageSchema.parse(
        await apiClient.get<unknown>('/api/fan/agencies?page=0&size=100'),
      );
      return page.content.filter((a) => a.status === 'ACTIVE');
    },
    staleTime: 30_000,
  });
}

export function useAgency(agencyId: string, initial: Agency) {
  return useQuery({
    queryKey: [FAN_KEY, 'agency', agencyId] as const,
    queryFn: async () =>
      AgencySchema.parse(await apiClient.get<unknown>(`/api/fan/agencies/${enc(agencyId)}`)),
    initialData: initial,
    staleTime: 0,
    ...READ_QUERY_REFETCH,
  });
}

function invalidateAgencies(qc: ReturnType<typeof useQueryClient>, agencyId?: string) {
  qc.invalidateQueries({ queryKey: [FAN_KEY, 'agencies'] });
  qc.invalidateQueries({ queryKey: [FAN_KEY, 'agency-options'] });
  if (agencyId) qc.invalidateQueries({ queryKey: [FAN_KEY, 'agency', agencyId] });
}

export function useCreateAgency() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateAgencyBody): Promise<Agency> =>
      AgencySchema.parse(await apiClient.post<unknown>('/api/fan/agencies', body)),
    onSuccess: () => invalidateAgencies(qc),
  });
}

export function useRenameAgency(agencyId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (name: string): Promise<Agency> =>
      AgencySchema.parse(
        await apiClient.patch<unknown>(`/api/fan/agencies/${enc(agencyId)}`, { name }),
      ),
    onSuccess: () => invalidateAgencies(qc, agencyId),
  });
}

export function useArchiveAgency(agencyId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (): Promise<Agency> =>
      AgencySchema.parse(
        await apiClient.patch<unknown>(`/api/fan/agencies/${enc(agencyId)}/status`, {
          status: 'ARCHIVED',
        }),
      ),
    onSuccess: () => invalidateAgencies(qc, agencyId),
  });
}

/** `null` clears the link (no store lookup); a value is verified by the store first. */
export function useLinkAgencyStoreSeller(agencyId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (storeSellerId: string | null): Promise<Agency> =>
      AgencySchema.parse(
        await apiClient.patch<unknown>(`/api/fan/agencies/${enc(agencyId)}/store-seller`, {
          storeSellerId,
        }),
      ),
    onSuccess: () => invalidateAgencies(qc, agencyId),
  });
}

// --- artists ------------------------------------------------------------------

export function useArtists(params: ArtistListParams, initial?: Paged<Artist>) {
  const seeded = initial !== undefined && (params.page ?? 0) === 0 && !params.q;
  return useQuery({
    queryKey: [FAN_KEY, 'artists', params.page ?? 0, params.q ?? ''] as const,
    queryFn: async () =>
      ArtistPageSchema.parse(await apiClient.get<unknown>(`/api/fan/artists?${qs(params)}`)),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
  });
}

export function useArtist(artistId: string, initial: Artist) {
  return useQuery({
    queryKey: [FAN_KEY, 'artist', artistId] as const,
    queryFn: async () =>
      ArtistSchema.parse(await apiClient.get<unknown>(`/api/fan/artists/${enc(artistId)}`)),
    initialData: initial,
    staleTime: 0,
    ...READ_QUERY_REFETCH,
  });
}

function invalidateArtist(qc: ReturnType<typeof useQueryClient>, artistId?: string) {
  qc.invalidateQueries({ queryKey: [FAN_KEY, 'artists'] });
  if (artistId) qc.invalidateQueries({ queryKey: [FAN_KEY, 'artist', artistId] });
}

export function useCreateArtist() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateArtistBody): Promise<Artist> =>
      ArtistSchema.parse(await apiClient.post<unknown>('/api/fan/artists', body)),
    onSuccess: () => invalidateArtist(qc),
  });
}

export function useUpdateArtist(artistId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: UpdateArtistBody): Promise<Artist> =>
      ArtistSchema.parse(await apiClient.patch<unknown>(`/api/fan/artists/${enc(artistId)}`, body)),
    onSuccess: () => invalidateArtist(qc, artistId),
  });
}

export function useChangeArtistStatus(artistId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: ArtistStatusBody): Promise<Artist> =>
      ArtistSchema.parse(
        await apiClient.patch<unknown>(`/api/fan/artists/${enc(artistId)}/status`, body),
      ),
    onSuccess: () => invalidateArtist(qc, artistId),
  });
}

export function useChangeArtistAgency(artistId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (agencyId: string | null): Promise<Artist> =>
      ArtistSchema.parse(
        await apiClient.patch<unknown>(`/api/fan/artists/${enc(artistId)}/agency`, { agencyId }),
      ),
    onSuccess: () => invalidateArtist(qc, artistId),
  });
}

// --- artist groups ------------------------------------------------------------

export function useArtistGroup(groupId: string, initial: ArtistGroup) {
  return useQuery({
    queryKey: [FAN_KEY, 'group', groupId] as const,
    queryFn: async () =>
      ArtistGroupSchema.parse(
        await apiClient.get<unknown>(`/api/fan/artist-groups/${enc(groupId)}`),
      ),
    initialData: initial,
    staleTime: 0,
    ...READ_QUERY_REFETCH,
  });
}

export function useCreateArtistGroup() {
  return useMutation({
    mutationFn: async (body: CreateGroupBody): Promise<ArtistGroup> =>
      ArtistGroupSchema.parse(await apiClient.post<unknown>('/api/fan/artist-groups', body)),
  });
}

export function useChangeGroupAgency(groupId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (agencyId: string | null): Promise<ArtistGroup> =>
      ArtistGroupSchema.parse(
        await apiClient.patch<unknown>(`/api/fan/artist-groups/${enc(groupId)}/agency`, {
          agencyId,
        }),
      ),
    onSuccess: () => qc.invalidateQueries({ queryKey: [FAN_KEY, 'group', groupId] }),
  });
}
