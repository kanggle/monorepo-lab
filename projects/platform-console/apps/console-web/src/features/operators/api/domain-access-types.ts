import { z } from 'zod';

/**
 * Feature-local types for the per-operator ADR-MONO-083 「업무 권한」 surface
 * (TASK-PC-FE-338 slice b — contract § 2.4.3.4 rows 6-8).
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   `iam-platform/specs/contracts/http/admin-api.md` § 도메인 업무 권한 세트 —
 *     `GET /api/admin/operators/{operatorId}/domain-access`,
 *     `POST /api/admin/operators/{operatorId}/domain-permission-sets`,
 *     `DELETE …/domain-permission-sets/{setId}`.
 *
 * ── NO CROSS-FEATURE IMPORT (architecture.md § Forbidden Dependencies) ──
 * `features/domain-permission-sets` already defines `DomainPermissionSet`
 * (rows 1-5, the set CRUD screen) and `DomainRole`, but a
 * `features/operators → features/domain-permission-sets` import is barred
 * ("같은 계층 features/A → features/B 상호 참조 금지"). This module owns its
 * OWN small view-models instead — the aggregator-borrows-a-producer-wire-type
 * remedy (precedent: `IamOverviewAuditRow` in `features/iam-overview`):
 *   - the full `DomainAccessResponse` shape (this endpoint's own producer
 *     response — not shared with any other feature), and
 *   - a trimmed `GrantableSetOption` (setId/name/systemKey/domains) for the
 *     grant-picker, parsed from the EXISTING `/api/domain-permission-sets`
 *     BFF route (an HTTP call, not a TS import — the route itself already
 *     exists from slice a).
 */

// --- GET .../domain-access --------------------------------------------------

export const DOMAIN_ACCESS_SOURCES = ['DIRECT', 'MIGRATION', 'GROUP'] as const;
export type DomainAccessSourceKind = (typeof DOMAIN_ACCESS_SOURCES)[number];

/** One assignment row behind a set — `groupId`/`groupName` present only on a
 *  `GROUP` source; `grantedBy` is `null` for the system `MIGRATION` backfill. */
export const DomainAccessSourceSchema = z.object({
  source: z.string(), // tolerant (z.string(), not the enum) — forward-compat.
  groupId: z.string().nullable().optional(),
  groupName: z.string().nullable().optional(),
  tenantId: z.string(),
  grantedAt: z.string(),
  grantedBy: z.string().nullable(),
});
export type DomainAccessSource = z.infer<typeof DomainAccessSourceSchema>;

export const DomainAccessSetSchema = z.object({
  setId: z.string(),
  name: z.string(),
  systemKey: z.string().nullable(),
  sources: z.array(DomainAccessSourceSchema),
});
export type DomainAccessSet = z.infer<typeof DomainAccessSetSchema>;

/** `roles` is the SUM of every `sets[].roles` — 🔴 pre-subscription-filter
 *  (admin-api.md § 도메인 업무 권한 세트 "실효 업무 권한"); the screen marks a
 *  role whose domain the active tenant has not subscribed to as «구독 없음». */
export const DomainAccessResponseSchema = z.object({
  operatorId: z.string(),
  tenantId: z.string(),
  roles: z.array(z.object({ domainKey: z.string(), role: z.string() })),
  sets: z.array(DomainAccessSetSchema),
});
export type DomainAccessResponse = z.infer<typeof DomainAccessResponseSchema>;

// --- POST .../domain-permission-sets (grant, DIRECT) ------------------------

export const GrantDomainSetResultSchema = z.object({
  operatorId: z.string(),
  tenantId: z.string(),
  setId: z.string(),
  source: z.string(),
  grantedAt: z.string(),
  grantedBy: z.string().nullable(),
  selfGrant: z.boolean(),
});
export type GrantDomainSetResult = z.infer<typeof GrantDomainSetResultSchema>;

// --- grant-picker view-model (own copy — see file header) ------------------

/** Trimmed from the `/api/domain-permission-sets` (slice a) response — this
 *  module intentionally does NOT import `DomainPermissionSet`. */
export const GrantableSetOptionSchema = z
  .object({
    setId: z.string(),
    name: z.string(),
    systemKey: z.string().nullable(),
    readOnly: z.boolean(),
    domains: z.array(z.string()),
  })
  .passthrough();
export type GrantableSetOption = z.infer<typeof GrantableSetOptionSchema>;

export const GrantableSetOptionListSchema = z.object({
  items: z.array(GrantableSetOptionSchema),
});
