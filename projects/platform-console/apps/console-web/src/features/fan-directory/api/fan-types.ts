import { z } from 'zod';

/**
 * fan-directory wire types (TASK-MONO-751 — ADR-MONO-079 D4-A).
 *
 * Authoritative producer contract: `projects/fan-platform/specs/contracts/http/artist-api.md`
 * (§ Artists · § Artist groups · § Agencies). Consumed only — the console never redefines a
 * field. The producer answers every success as `{ data, meta }` (`meta.timestamp`, plus
 * `page/size/totalElements/totalPages` on a list); the console's same-origin routes unwrap
 * `data` and hand the screens the plain objects below (a list becomes {@link Paged}).
 *
 * Nullable producer fields are `nullable().optional()` on purpose: the producer serialises
 * Java nulls as `null`, and an absent key must not crash a screen (the console parses
 * defensively; the producer is the authority).
 */

const nullableString = z.string().nullable().optional();

export const AgencySchema = z.object({
  id: z.string().min(1),
  tenantId: nullableString,
  name: z.string(),
  /** `ACTIVE` | `ARCHIVED` — kept as a string so a future status does not crash the list. */
  status: z.string(),
  /** ecommerce `seller_id` that sells this agency's goods (ADR-MONO-079 D2, 0..1). */
  storeSellerId: nullableString,
  createdAt: nullableString,
  updatedAt: nullableString,
});
export type Agency = z.infer<typeof AgencySchema>;

export const ArtistSchema = z.object({
  id: z.string().min(1),
  tenantId: nullableString,
  accountId: nullableString,
  artistType: z.string(),
  status: z.string(),
  stageName: z.string(),
  realName: nullableString,
  debutDate: nullableString,
  /** Display name — the agency entity's name when `agencyId` is set (artist-api § Agencies). */
  agency: nullableString,
  agencyId: nullableString,
  bio: nullableString,
  profileImageRef: nullableString,
  createdAt: nullableString,
  updatedAt: nullableString,
  publishedAt: nullableString,
  archivedAt: nullableString,
});
export type Artist = z.infer<typeof ArtistSchema>;

export const GroupMemberSchema = z.object({
  artistId: z.string(),
  role: z.string(),
  joinedAt: nullableString,
  leftAt: nullableString,
});
export type GroupMember = z.infer<typeof GroupMemberSchema>;

export const ArtistGroupSchema = z.object({
  id: z.string().min(1),
  tenantId: nullableString,
  name: z.string(),
  debutDate: nullableString,
  agency: nullableString,
  agencyId: nullableString,
  profileImageRef: nullableString,
  status: z.string(),
  createdAt: nullableString,
  updatedAt: nullableString,
  members: z.array(GroupMemberSchema).default([]),
});
export type ArtistGroup = z.infer<typeof ArtistGroupSchema>;

/** A page of items as the console's routes hand it to the screens. */
export interface Paged<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function pagedSchema<T extends z.ZodTypeAny>(item: T) {
  return z.object({
    content: z.array(item),
    page: z.number().int().nonnegative(),
    size: z.number().int().positive(),
    totalElements: z.number().int().nonnegative(),
    totalPages: z.number().int().nonnegative(),
  });
}

export const AgencyPageSchema = pagedSchema(AgencySchema);
export const ArtistPageSchema = pagedSchema(ArtistSchema);

/** The producer's `{ data, meta }` success envelope. */
export function envelopeSchema<T extends z.ZodTypeAny>(data: T) {
  return z.object({
    data,
    meta: z.record(z.unknown()).optional(),
  });
}

/** The producer's paginated list envelope: `data` is the array, `meta` carries the page. */
export function listEnvelopeSchema<T extends z.ZodTypeAny>(item: T) {
  return z.object({
    data: z.array(item),
    meta: z.object({
      page: z.number().int().nonnegative(),
      size: z.number().int().positive(),
      totalElements: z.number().int().nonnegative(),
      totalPages: z.number().int().nonnegative(),
    }),
  });
}

export const FAN_DEFAULT_PAGE_SIZE = 20;
export const FAN_MAX_PAGE_SIZE = 100;

export interface FanListParams {
  page?: number;
  size?: number;
}

export interface ArtistListParams extends FanListParams {
  /** Case-insensitive substring on `stageName`. */
  q?: string;
}

// ===========================================================================
// Request bodies — validated by the console's routes before the upstream
// (defence-in-depth; the producer validates too). Limits copied from the
// producer's request records (artist-service `dto/request/*`).
// ===========================================================================

const name120 = z.string().trim().min(1).max(120);
const optional120 = z.string().trim().max(120).optional();
const isoDate = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/)
  .optional();
/** An agency id (1..36) or `null` = unaffiliated (artist-api § Change affiliation). */
const agencyIdOrNull = z.string().trim().min(1).max(36).nullable();

export const CreateAgencyBodySchema = z.object({ name: name120 });
export type CreateAgencyBody = z.infer<typeof CreateAgencyBodySchema>;

export const RenameAgencyBodySchema = z.object({ name: name120 });
export type RenameAgencyBody = z.infer<typeof RenameAgencyBodySchema>;

/** `ARCHIVED` is the only transition target (artist-api § Archive). */
export const ArchiveAgencyBodySchema = z.object({ status: z.literal('ARCHIVED') });
export type ArchiveAgencyBody = z.infer<typeof ArchiveAgencyBodySchema>;

/** `null` clears the link without asking the store (artist-api § store-seller). */
export const LinkStoreSellerBodySchema = z.object({
  storeSellerId: z.string().trim().min(1).max(64).nullable(),
});
export type LinkStoreSellerBody = z.infer<typeof LinkStoreSellerBodySchema>;

export const ARTIST_TYPES = ['SOLO', 'GROUP_MEMBER'] as const;

export const CreateArtistBodySchema = z.object({
  accountId: z.string().trim().min(1).max(36),
  artistType: z.enum(ARTIST_TYPES),
  stageName: name120,
  realName: optional120,
  debutDate: isoDate,
  bio: z.string().max(4000).optional(),
  agencyId: z.string().trim().min(1).max(36).optional(),
});
export type CreateArtistBody = z.infer<typeof CreateArtistBodySchema>;

/** PATCH semantics: every field optional (artist-api § Update profile). `accountId` is
 *  deliberately absent — immutable by contract. The free-text `agency` is absent too: the
 *  affiliation goes through its own endpoint. */
export const UpdateArtistBodySchema = z.object({
  stageName: name120.optional(),
  realName: optional120,
  debutDate: isoDate,
  bio: z.string().max(4000).optional(),
});
export type UpdateArtistBody = z.infer<typeof UpdateArtistBodySchema>;

export const ArtistStatusBodySchema = z.object({
  status: z.enum(['PUBLISHED', 'ARCHIVED']),
  reason: z.string().max(200).optional(),
});
export type ArtistStatusBody = z.infer<typeof ArtistStatusBodySchema>;

export const ChangeAffiliationBodySchema = z.object({ agencyId: agencyIdOrNull });
export type ChangeAffiliationBody = z.infer<typeof ChangeAffiliationBodySchema>;

export const CreateGroupBodySchema = z.object({
  name: name120,
  debutDate: isoDate,
  agencyId: z.string().trim().min(1).max(36).optional(),
});
export type CreateGroupBody = z.infer<typeof CreateGroupBodySchema>;
