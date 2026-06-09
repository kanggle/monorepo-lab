/** Membership tier — the two paid tiers gated by membership-service. */
export type MembershipTier = 'MEMBERS_ONLY' | 'PREMIUM';

/** Stored lifecycle status. Expiry is read-time (no stored EXPIRED). */
export type MembershipStatus = 'ACTIVE' | 'CANCELED';

/**
 * Full membership payload (subscribe / cancel / detail responses).
 * Mirrors `membership-api.md`.
 */
export interface Membership {
  membershipId: string;
  tenantId: string;
  accountId: string;
  tier: MembershipTier;
  status: MembershipStatus;
  validFrom: string;
  validTo: string;
  planMonths: number;
  paymentRef: string | null;
  createdAt: string;
  canceledAt: string | null;
}

/**
 * List item (`GET /api/v1/memberships`). Adds the **read-time** `active` flag
 * (`status == ACTIVE && now ∈ [validFrom, validTo]`) — a stored-ACTIVE row past
 * its window reads `active=false`.
 */
export interface MembershipListItem {
  membershipId: string;
  tier: MembershipTier;
  status: MembershipStatus;
  validFrom: string;
  validTo: string;
  planMonths: number;
  active: boolean;
  createdAt: string;
  canceledAt: string | null;
}

export interface MembershipList {
  content: MembershipListItem[];
}
