/**
 * Artist types — shape from `specs/contracts/http/artist-api.md`.
 */

export type ArtistType = 'SOLO' | 'GROUP_MEMBER';
export type ArtistStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export interface Artist {
  id: string;
  tenantId: string;
  artistType: ArtistType;
  status: ArtistStatus;
  stageName: string;
  realName: string | null;
  debutDate: string | null;
  agency: string | null;
  bio: string | null;
  profileImageRef: string | null;
  createdAt: string;
  updatedAt: string;
  publishedAt: string | null;
  archivedAt: string | null;
}

export interface Fandom {
  artistId: string;
  tenantId: string;
  fandomName: string;
  colorHex: string | null;
  foundedAt: string | null;
  slogan: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ArtistPage {
  content: Artist[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
