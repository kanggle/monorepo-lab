import { z } from 'zod';

/**
 * Feature-local types for the ADR-MONO-083 「업무 권한 세트」 surface
 * (TASK-PC-FE-338 console slice P4 — producer P2 `TASK-BE-631`).
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   `iam-platform/specs/contracts/http/admin-api.md`
 *   § "도메인 업무 권한 세트" —
 *     `GET /api/admin/domain-roles`,
 *     `GET/POST /api/admin/domain-permission-sets`,
 *     `GET/PATCH/DELETE /api/admin/domain-permission-sets/{setId}`.
 *
 * These zod schemas are the runtime parsers the api-client / tests assert
 * against. Feature-local (not cross-feature) per architecture.md
 * § Allowed Dependencies.
 *
 * ── NAMESPACE (ADR-MONO-035 O1-C / ADR-083 D1): these roles/sets are a
 *   SEPARATE entity from `admin_roles` (「IAM 관리 역할」, `features/permission-sets`).
 *   Holding an admin role never grants a domain business permission, and a
 *   domain permission set never grants an admin (`/api/admin/**`) permission.
 *
 * 🔴 STATUS HONESTY (ADR-083 D9): P3 (`TASK-BE-632`, the token-issuance
 *   change) is not deployed. A set recorded here does not yet narrow any
 *   assume-tenant token — every operator of the tenant still gets the full
 *   subscription-derived role set. Screens consuming these types must say
 *   "적용 예정", never that a set currently limits access.
 */

// --- domain role catalog (row 1) --------------------------------------------

export const DomainRoleSchema = z.object({
  domainKey: z.string(),
  role: z.string(),
  access: z.enum(['READ', 'WRITE', 'OPERATE']),
  /** `false` = the domain server does not yet check fine-grained roles — this
   *  ONE role opens the domain's entire business API. Render `label` verbatim
   *  ("… 전체 업무(세분 미지원)") — never split or described as narrower. */
  fineGrained: z.boolean(),
  label: z.string(),
});
export type DomainRole = z.infer<typeof DomainRoleSchema>;

export const DomainRoleListSchema = z.object({
  items: z.array(DomainRoleSchema),
});
export type DomainRoleList = z.infer<typeof DomainRoleListSchema>;

// --- domain permission set (rows 2-5) ---------------------------------------

export const DomainSetRoleRefSchema = z.object({
  domainKey: z.string(),
  role: z.string(),
});
export type DomainSetRoleRef = z.infer<typeof DomainSetRoleRefSchema>;

export const DomainPermissionSetSchema = z.object({
  setId: z.string(),
  /** `null` for a system preset (tenant-independent). */
  tenantId: z.string().nullable(),
  /** Preset key (e.g. `wms-inventory-read`); `null` for a tenant set. */
  systemKey: z.string().nullable(),
  /** Preset = `true` (read-only, no edit/delete); tenant set = `false`. */
  readOnly: z.boolean(),
  name: z.string(),
  description: z.string().nullable(),
  roles: z.array(DomainSetRoleRefSchema),
  /** The `domainKey` set of `roles`, ascending — the subscription-gate unit. */
  domains: z.array(z.string()),
  /** Operator assignment rows pointing at this set (any source), in the
   *  ACTING tenant. */
  grantCount: z.number().int(),
  /** Groups that have assigned this set, in the ACTING tenant. */
  groupCount: z.number().int(),
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type DomainPermissionSet = z.infer<typeof DomainPermissionSetSchema>;

export const DomainPermissionSetListSchema = z.object({
  items: z.array(DomainPermissionSetSchema),
});
export type DomainPermissionSetList = z.infer<typeof DomainPermissionSetListSchema>;

// --- inputs ------------------------------------------------------------------

export interface CreateDomainPermissionSetInput {
  name: string;
  description?: string;
  roles: DomainSetRoleRef[];
}

/** All fields optional, at least one required (producer + proxy refine).
 *  Sending `roles` is a FULL REPLACE, never a merge. */
export interface UpdateDomainPermissionSetInput {
  name?: string;
  description?: string;
  roles?: DomainSetRoleRef[];
}
