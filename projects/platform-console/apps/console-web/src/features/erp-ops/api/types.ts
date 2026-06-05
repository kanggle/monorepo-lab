import { z } from 'zod';

/**
 * Feature-local types for the erp `masterdata-service`'s read-only
 * 5-master × {list, detail} = 10 GET surface (TASK-PC-FE-010 —
 * ADR-MONO-013 Phase 6, the FOURTH non-GAP federated domain and the
 * FIRST internal-system-primary confirmation: wms transactional →
 * scm integration-heavy → finance regulated/transactional → erp
 * internal-system + transactional + audit-heavy).
 *
 * Authoritative producer contract (do NOT redefine — consume read-only):
 *   `erp-platform/specs/contracts/http/masterdata-api.md`
 *     § Department / Employee / JobGrade / CostCenter / BusinessPartner
 *     each exposing `GET .../{master}` (list) and `GET .../{master}/{id}`
 *     (detail), all with `?asOf=<ISO-8601>` query (E3 point-in-time).
 *
 * Consumer obligation: `console-integration-contract.md` § 2.4.8
 * (reuses the § 2.4.5 per-domain credential rule — NOT re-derived;
 * same outcome as § 2.4.6 scm / § 2.4.7 finance).
 *
 * erp-side spec-first basis:
 * `erp-platform/specs/integration/gap-integration.md` § *platform-
 * console Operator Read Consumer* (TASK-ERP-BE-002, merged
 * 2026-05-20).
 *
 * These zod schemas are the runtime parsers the api-client / tests
 * assert against. They are feature-local (not cross-feature) per
 * architecture.md § Allowed Dependencies.
 *
 * E2 EFFECTIVE-DATING invariant (§ 2.4.8 / E2 architecture
 * obligation): every master detail surfaces `effectivePeriod` as a
 * REQUIRED, first-class field. `effectiveTo: null` (open-ended /
 * active) vs `effectiveTo: <past>` (retired) MUST both render —
 * retired rows visually distinct but NEVER hidden / filtered (a
 * test asserts this).
 *
 * TOLERANCE invariant (§ 2.4.8 / task Edge Case "Unknown/future
 * enum"): every read shape is permissive — unknown / future master
 * `status` (`RETIRED` and any future addition) and employee
 * `employmentStatus` (`SEPARATED` and any future addition) parse
 * to a generic string value and NEVER throw. Only the fields the
 * UI strictly needs are required; everything else is passthrough.
 */

// ---------------------------------------------------------------------------
// EffectivePeriod — E2 first-class field on every master detail.
// ---------------------------------------------------------------------------

/**
 * `EffectivePeriod` — `{ effectiveFrom, effectiveTo }`. `effectiveTo`
 * may be `null` (open-ended / active). Both fields are ISO-8601
 * DATE strings (the producer wire shape from `masterdata-api.md` §
 * Common shapes). The consumer surfaces them HONESTLY — retired
 * rows (`effectiveTo` in the past) are rendered visually distinct
 * but NOT hidden (E2 honesty).
 */
export const EffectivePeriodSchema = z.object({
  effectiveFrom: z.string(),
  effectiveTo: z.string().nullable(),
});
export type EffectivePeriod = z.infer<typeof EffectivePeriodSchema>;

/** Producer master `status` enum surfaced HONESTLY (a `RETIRED`
 *  master is shown as such, never hidden — § 2.4.8). Stored as a
 *  free string so unknown / future values render generically (no
 *  parser throw, tolerant-parser discipline). */
export const KNOWN_MASTER_STATUSES = ['ACTIVE', 'RETIRED'] as const;
export type KnownMasterStatus = (typeof KNOWN_MASTER_STATUSES)[number];

/** Producer employee `employmentStatus` enum surfaced HONESTLY (a
 *  `SEPARATED` employee is shown as such, never filtered out —
 *  § 2.4.8). Free string for tolerance. */
export const KNOWN_EMPLOYMENT_STATUSES = [
  'EMPLOYED',
  'ON_LEAVE',
  'SEPARATED',
] as const;
export type KnownEmploymentStatus =
  (typeof KNOWN_EMPLOYMENT_STATUSES)[number];

/** Producer business-partner `partnerType` enum. Free string for
 *  tolerance — unknown / future values render with a generic
 *  label. */
export const KNOWN_PARTNER_TYPES = [
  'CUSTOMER',
  'SUPPLIER',
  'BOTH',
] as const;
export type KnownPartnerType = (typeof KNOWN_PARTNER_TYPES)[number];

// ---------------------------------------------------------------------------
// Audit — append-only audit (E8) surfaced on detail responses.
// ---------------------------------------------------------------------------

export const AuditSchema = z
  .object({
    createdAt: z.string().optional(),
    createdBy: z.string().optional(),
    updatedAt: z.string().optional(),
    updatedBy: z.string().optional(),
  })
  .partial()
  .passthrough();
export type Audit = z.infer<typeof AuditSchema>;

// ---------------------------------------------------------------------------
// erp success envelope shapes — flat (same wire as scm/finance,
// distinct producer / own parser).
// ---------------------------------------------------------------------------

/** erp success-meta: `{ timestamp, page?, size?, totalElements? }`.
 *  Producer-specific — kept distinct from finance / scm meta even
 *  though byte-identical (each domain owns its own parser). */
export const ErpMetaSchema = z
  .object({
    timestamp: z.string().optional(),
    page: z.number().int().nonnegative().optional(),
    size: z.number().int().positive().optional(),
    totalElements: z.number().int().nonnegative().optional(),
  })
  .passthrough();
export type ErpMeta = z.infer<typeof ErpMetaSchema>;

// ---------------------------------------------------------------------------
// read-model meta — extends the base meta with `warning` (required,
// always present) and optional `unresolved` (only when ≥ 1 reference
// is not yet projected). Both fields MUST be tolerated by the consumer
// regardless of presence (eventually-consistent, E5).
// ---------------------------------------------------------------------------

/** erp read-model success-meta (TASK-PC-FE-049 — `read-model-api.md`):
 *  extends `ErpMetaSchema` with the required `warning` (always
 *  "Eventually-consistent read-model") and the optional `unresolved`
 *  array (field names whose master event has not yet been consumed).
 *  Schema is TOLERANT: unknown fields pass through; `warning` is
 *  optional at the schema level so an absent value does not throw. */
export const ReadModelMetaSchema = z
  .object({
    timestamp: z.string().optional(),
    page: z.number().int().nonnegative().optional(),
    size: z.number().int().positive().optional(),
    totalElements: z.number().int().nonnegative().optional(),
    warning: z.string().optional(),
    unresolved: z.array(z.string()).optional(),
  })
  .passthrough();
export type ReadModelMeta = z.infer<typeof ReadModelMetaSchema>;

// ---------------------------------------------------------------------------
// EmployeeOrgView — read-model projection (TASK-PC-FE-049).
//   GET /api/erp/read-model/employees (?page=&size=&asOf=&departmentId=&status=)
//   GET /api/erp/read-model/employees/{id} (?asOf=)
// Fields derived verbatim from `read-model-api.md` § EmployeeOrgView.
// A `null` on department / costCenter / jobGrade means the projected
// master event has not yet been consumed (eventually-consistent, E5).
// NEVER fabricate a non-null value for an unresolved reference.
// ---------------------------------------------------------------------------

export const DepartmentPathNodeSchema = z
  .object({
    id: z.string(),
    code: z.string(),
    name: z.string(),
  })
  .passthrough();
export type DepartmentPathNode = z.infer<typeof DepartmentPathNodeSchema>;

export const DepartmentRefSchema = z
  .object({
    id: z.string(),
    code: z.string(),
    name: z.string(),
    path: z.array(DepartmentPathNodeSchema),
  })
  .passthrough();
export type DepartmentRef = z.infer<typeof DepartmentRefSchema>;

export const CostCenterRefSchema = z
  .object({
    id: z.string(),
    code: z.string(),
    name: z.string(),
  })
  .passthrough();
export type CostCenterRef = z.infer<typeof CostCenterRefSchema>;

export const JobGradeRefSchema = z
  .object({
    id: z.string(),
    code: z.string(),
    name: z.string(),
    displayOrder: z.number().int().optional(),
  })
  .passthrough();
export type JobGradeRef = z.infer<typeof JobGradeRefSchema>;

export const EmployeeOrgViewSchema = z
  .object({
    id: z.string(),
    employeeNumber: z.string(),
    name: z.string(),
    // free-string for tolerance (ACTIVE / RETIRED / future)
    status: z.string(),
    effectivePeriod: EffectivePeriodSchema,
    department: DepartmentRefSchema.nullable(),
    costCenter: CostCenterRefSchema.nullable(),
    jobGrade: JobGradeRefSchema.nullable(),
  })
  .passthrough();
export type EmployeeOrgView = z.infer<typeof EmployeeOrgViewSchema>;

export const EmployeeOrgViewListResponseSchema = z.object({
  data: z.array(EmployeeOrgViewSchema),
  meta: ReadModelMetaSchema,
});
export type EmployeeOrgViewListResponse = z.infer<
  typeof EmployeeOrgViewListResponseSchema
>;

export const EmployeeOrgViewDetailResponseSchema = z.object({
  data: EmployeeOrgViewSchema,
  meta: ReadModelMetaSchema,
});
export type EmployeeOrgViewDetailResponse = z.infer<
  typeof EmployeeOrgViewDetailResponseSchema
>;

/** Query params for the read-model employee org-view list.
 *  Extends `ErpListQueryParams` with `departmentId` (subtree filter)
 *  and `status` (`ACTIVE | RETIRED`; default `ACTIVE`). `asOf` (E3)
 *  threads through verbatim. */
export interface OrgViewListQueryParams {
  asOf?: string;
  page?: number;
  size?: number;
  departmentId?: string;
  status?: string;
}

// ---------------------------------------------------------------------------
// Department — list + detail (E3 point-in-time read; parentId for
// hierarchical structure).
//   GET /api/erp/masterdata/departments (?asOf=&active=&parentId=&page=&size=)
//   GET /api/erp/masterdata/departments/{id} (?asOf=)
// ---------------------------------------------------------------------------

export const DepartmentSchema = z
  .object({
    id: z.string(),
    code: z.string(),
    name: z.string(),
    parentId: z.string().nullable().optional(),
    // tolerated as free string (unknown → generic label).
    status: z.string(),
    effectivePeriod: EffectivePeriodSchema,
    audit: AuditSchema.optional(),
  })
  .passthrough();
export type Department = z.infer<typeof DepartmentSchema>;

export const DepartmentListResponseSchema = z.object({
  data: z.array(DepartmentSchema),
  meta: ErpMetaSchema,
});
export type DepartmentListResponse = z.infer<typeof DepartmentListResponseSchema>;

export const DepartmentDetailResponseSchema = z.object({
  data: DepartmentSchema,
  meta: ErpMetaSchema,
});
export type DepartmentDetailResponse = z.infer<typeof DepartmentDetailResponseSchema>;

// ---------------------------------------------------------------------------
// Department WRITE inputs (TASK-PC-FE-046 — the department write PILOT;
// console-integration-contract § 2.4.8 *Department write binding (PILOT)*).
// These mirror the UNCHANGED producer `masterdata-api.md` § Department
// mutation request bodies — the console does NOT redefine them, it
// consumes them. Only the department master is writable at the pilot;
// the other four masters remain read-only (no write-input types exist
// for them — a test pins that absence).
//
// `reason` lives on the body ONLY where the producer has a slot
// (`retire` required, `move-parent` optional). `create`/`update` have
// NO reason slot — the console MUST NOT fabricate `X-Operator-Reason`
// (erp does not read it). Every mutation carries an `Idempotency-Key`
// (set as a header by the api client, supplied console-side per attempt).
// ---------------------------------------------------------------------------

/** `POST /api/erp/masterdata/departments` body — create. `code` +
 *  `name` required; `parentId` optional (root when absent);
 *  `effectiveFrom` optional ISO-8601 DATE (producer defaults today). */
export interface CreateDepartmentInput {
  code: string;
  name: string;
  parentId?: string | null;
  effectiveFrom?: string;
}

/** `PATCH /api/erp/masterdata/departments/{id}` body — update. Partial;
 *  a new revision is created producer-side (NOT an in-place overwrite). */
export interface UpdateDepartmentInput {
  name?: string;
  effectiveFrom?: string;
}

/** `POST /api/erp/masterdata/departments/{id}/retire` body. `reason`
 *  REQUIRED (≤256) — the producer has a slot here (maps to E8 audit). */
export interface RetireDepartmentInput {
  reason: string;
}

/** `POST /api/erp/masterdata/departments/{id}/move-parent` body.
 *  `newParentId` may be `null` (promote to root). `effectiveFrom`
 *  required ISO-8601 DATE. `reason` optional (≤256, producer slot). */
export interface MoveDepartmentParentInput {
  newParentId: string | null;
  effectiveFrom: string;
  reason?: string;
}

/** Zod parsers for the same-origin proxy request bodies (the route
 *  handlers validate the incoming client body before forwarding). */
export const CreateDepartmentBodySchema = z.object({
  code: z.string().min(1).max(64),
  name: z.string().min(1).max(256),
  parentId: z.string().min(1).nullable().optional(),
  effectiveFrom: z.string().min(1).optional(),
  idempotencyKey: z.string().min(1),
});

export const UpdateDepartmentBodySchema = z.object({
  name: z.string().min(1).max(256).optional(),
  effectiveFrom: z.string().min(1).optional(),
  idempotencyKey: z.string().min(1),
});

export const RetireDepartmentBodySchema = z.object({
  reason: z.string().min(1).max(256),
  idempotencyKey: z.string().min(1),
});

export const MoveDepartmentParentBodySchema = z.object({
  newParentId: z.string().min(1).nullable(),
  effectiveFrom: z.string().min(1),
  reason: z.string().max(256).optional(),
  idempotencyKey: z.string().min(1),
});

// ---------------------------------------------------------------------------
// Employee — list + detail (cross-refs department / jobGrade /
// costCenter; employment status).
//   GET /api/erp/masterdata/employees (?asOf=&active=&departmentId=&costCenterId=&page=&size=)
//   GET /api/erp/masterdata/employees/{id} (?asOf=)
// ---------------------------------------------------------------------------

export const EmployeeSchema = z
  .object({
    id: z.string(),
    employeeNumber: z.string(),
    // confidential / E7 — surfaced to the operator UI but never
    // logged by the api module.
    name: z.string(),
    departmentId: z.string().nullable().optional(),
    jobGradeId: z.string().nullable().optional(),
    costCenterId: z.string().nullable().optional(),
    // master `status` (`ACTIVE`/`RETIRED`) — honest tolerant.
    status: z.string(),
    // separate from master status: employment lifecycle
    // (`EMPLOYED`/`ON_LEAVE`/`SEPARATED`/...) — honest tolerant.
    employmentStatus: z.string().optional(),
    effectivePeriod: EffectivePeriodSchema,
    audit: AuditSchema.optional(),
  })
  .passthrough();
export type Employee = z.infer<typeof EmployeeSchema>;

export const EmployeeListResponseSchema = z.object({
  data: z.array(EmployeeSchema),
  meta: ErpMetaSchema,
});
export type EmployeeListResponse = z.infer<typeof EmployeeListResponseSchema>;

export const EmployeeDetailResponseSchema = z.object({
  data: EmployeeSchema,
  meta: ErpMetaSchema,
});
export type EmployeeDetailResponse = z.infer<typeof EmployeeDetailResponseSchema>;

// ---------------------------------------------------------------------------
// JobGrade — list (ordered by `displayOrder` asc producer-side) + detail.
//   GET /api/erp/masterdata/job-grades (?asOf=&active=&page=&size=)
//   GET /api/erp/masterdata/job-grades/{id} (?asOf=)
// ---------------------------------------------------------------------------

export const JobGradeSchema = z
  .object({
    id: z.string(),
    code: z.string(),
    name: z.string(),
    displayOrder: z.number().int().optional(),
    status: z.string(),
    effectivePeriod: EffectivePeriodSchema,
    audit: AuditSchema.optional(),
  })
  .passthrough();
export type JobGrade = z.infer<typeof JobGradeSchema>;

export const JobGradeListResponseSchema = z.object({
  data: z.array(JobGradeSchema),
  meta: ErpMetaSchema,
});
export type JobGradeListResponse = z.infer<typeof JobGradeListResponseSchema>;

export const JobGradeDetailResponseSchema = z.object({
  data: JobGradeSchema,
  meta: ErpMetaSchema,
});
export type JobGradeDetailResponse = z.infer<typeof JobGradeDetailResponseSchema>;

// ---------------------------------------------------------------------------
// CostCenter — list + detail (references department).
//   GET /api/erp/masterdata/cost-centers (?asOf=&active=&departmentId=&page=&size=)
//   GET /api/erp/masterdata/cost-centers/{id} (?asOf=)
// ---------------------------------------------------------------------------

export const CostCenterSchema = z
  .object({
    id: z.string(),
    code: z.string(),
    name: z.string(),
    departmentId: z.string().nullable().optional(),
    status: z.string(),
    effectivePeriod: EffectivePeriodSchema,
    audit: AuditSchema.optional(),
  })
  .passthrough();
export type CostCenter = z.infer<typeof CostCenterSchema>;

export const CostCenterListResponseSchema = z.object({
  data: z.array(CostCenterSchema),
  meta: ErpMetaSchema,
});
export type CostCenterListResponse = z.infer<typeof CostCenterListResponseSchema>;

export const CostCenterDetailResponseSchema = z.object({
  data: CostCenterSchema,
  meta: ErpMetaSchema,
});
export type CostCenterDetailResponse = z.infer<typeof CostCenterDetailResponseSchema>;

// ---------------------------------------------------------------------------
// BusinessPartner — list + detail (CUSTOMER / SUPPLIER / BOTH;
// confidential financial details — paymentTerms).
//   GET /api/erp/masterdata/business-partners (?asOf=&active=&partnerType=&page=&size=)
//   GET /api/erp/masterdata/business-partners/{id} (?asOf=)
// ---------------------------------------------------------------------------

export const BusinessPartnerSchema = z
  .object({
    id: z.string(),
    code: z.string(),
    name: z.string(),
    partnerType: z.string(),
    // confidential — paymentTerms surfaced to the operator UI but
    // NEVER logged. Tolerant-parser passthrough — the inner shape
    // is producer-owned and may grow.
    paymentTerms: z.unknown().optional(),
    status: z.string(),
    effectivePeriod: EffectivePeriodSchema,
    audit: AuditSchema.optional(),
  })
  .passthrough();
export type BusinessPartner = z.infer<typeof BusinessPartnerSchema>;

export const BusinessPartnerListResponseSchema = z.object({
  data: z.array(BusinessPartnerSchema),
  meta: ErpMetaSchema,
});
export type BusinessPartnerListResponse = z.infer<
  typeof BusinessPartnerListResponseSchema
>;

export const BusinessPartnerDetailResponseSchema = z.object({
  data: BusinessPartnerSchema,
  meta: ErpMetaSchema,
});
export type BusinessPartnerDetailResponse = z.infer<
  typeof BusinessPartnerDetailResponseSchema
>;

// ---------------------------------------------------------------------------
// query params
// ---------------------------------------------------------------------------

export const ERP_DEFAULT_PAGE_SIZE = 20;
export const ERP_MAX_PAGE_SIZE = 100;

/** Common query params for every erp list endpoint. `asOf` is the
 *  E3 first-class point-in-time read — when supplied it threads
 *  through to the producer verbatim and the producer returns the
 *  state-at-that-instant (NOT the current state). */
export interface ErpListQueryParams {
  /** E3 — ISO-8601 DATE for point-in-time read. */
  asOf?: string;
  /** Optional producer filter — when omitted producer default
   *  applies (typically active = true). The console exposes this
   *  honestly so retired masters can be browsed. */
  active?: boolean;
  page?: number;
  size?: number;
  /** Master-specific filters — tolerated as a passthrough record
   *  so per-master query params (`parentId` / `departmentId` /
   *  `costCenterId` / `partnerType`) can be supplied without
   *  proliferating per-master interfaces. */
  filters?: Record<string, string>;
}

/** Detail query — only `asOf` is producer-defined. */
export interface ErpDetailQueryParams {
  asOf?: string;
}

// ---------------------------------------------------------------------------
// labelForUnknown — tolerant rendering helper for master / employment
// status enums (used by the components; co-located with the schemas
// because the known-enum sets live here).
// ---------------------------------------------------------------------------

export function labelForUnknownEnum<T extends string>(
  value: string | undefined | null,
  known: readonly T[],
): string {
  if (!value) return '—';
  return (known as readonly string[]).includes(value)
    ? value
    : `${value} (unknown)`;
}

// ---------------------------------------------------------------------------
// DelegationFact — read-model projection (TASK-PC-FE-055).
//   GET /api/erp/read-model/delegations (?delegatorId=&delegateId=&status=&activeAt=&page=&size=)
//   GET /api/erp/read-model/delegations/{grantId}
// Fields derived verbatim from `read-model-api.md` § Delegation facts (v1.2).
// All optional fields are NON_NULL-absent — parse tolerant (.optional() NOT
// .nullable()). A `validTo` absent means open-ended / 무기한.
// ---------------------------------------------------------------------------

export const DelegationFactSchema = z
  .object({
    grantId: z.string(),
    // free-string for tolerance (ACTIVE | REVOKED | future)
    status: z.string(),
    delegatorId: z.string(),
    delegateId: z.string(),
    // NON_NULL-absent: out-of-order revoke-before-grant → ABSENT.
    validFrom: z.string().optional(),
    // NON_NULL-absent: ABSENT = open-ended (무기한) or revoke-before-grant.
    validTo: z.string().optional(),
    // NON_NULL-absent: ABSENT when no reason was provided.
    reason: z.string().optional(),
    // NON_NULL-absent: ABSENT while ACTIVE.
    revokedAt: z.string().optional(),
  })
  .passthrough();
export type DelegationFact = z.infer<typeof DelegationFactSchema>;

export const DelegationFactListResponseSchema = z.object({
  data: z.array(DelegationFactSchema),
  meta: ReadModelMetaSchema,
});
export type DelegationFactListResponse = z.infer<
  typeof DelegationFactListResponseSchema
>;

export const DelegationFactDetailResponseSchema = z.object({
  data: DelegationFactSchema,
  meta: ReadModelMetaSchema,
});
export type DelegationFactDetailResponse = z.infer<
  typeof DelegationFactDetailResponseSchema
>;

/** Query params for the read-model delegation-fact list.
 *  `delegatorId` / `delegateId` / `status` / `activeAt` are
 *  producer-defined filters — the console passes them verbatim. */
export interface DelegationFactListQueryParams {
  delegatorId?: string;
  delegateId?: string;
  status?: string;
  activeAt?: string;
  page?: number;
  size?: number;
}

/** True if `effectiveTo` is in the past relative to `now` (default
 *  = `new Date()`). Used by E2 rendering to mark retired rows
 *  visually distinct without HIDING them. */
export function isRetired(
  period: EffectivePeriod,
  now: Date = new Date(),
): boolean {
  if (!period.effectiveTo) return false;
  // String comparison on ISO-8601 DATEs is monotonic — no Date()
  // parse needed when both sides are ISO-8601, but we keep Date()
  // to be robust against partial-precision producer values.
  try {
    return new Date(period.effectiveTo).getTime() < now.getTime();
  } catch {
    return false;
  }
}

// ---------------------------------------------------------------------------
// Additional masters WRITE inputs (TASK-PC-FE-048 — employees / job-grades /
// cost-centers / business-partners write parity, generalising the
// TASK-PC-FE-046 department pilot to all 5 masters). Consume the UNCHANGED
// producer `masterdata-api.md` § <master> create/update/retire bodies. retire
// is uniform across every master (`{ reason }`). `reason` rides in the body
// only on retire (the producer's only reason slot for these masters); create/
// update never send `X-Operator-Reason` (erp does not read it). Each mutation
// carries an `Idempotency-Key` (E1 / T1).
// ---------------------------------------------------------------------------

/** Shared retire body — identical across every master. */
export const ErpRetireBodySchema = z.object({
  reason: z.string().min(1).max(256),
  idempotencyKey: z.string().min(1),
});

/** Optional payment-terms sub-object (BusinessPartner — confidential). */
const PaymentTermsSchema = z
  .object({
    termDays: z.number().int().nonnegative().optional(),
    method: z.string().optional(),
  })
  .passthrough();

// Employee -------------------------------------------------------------------
export interface CreateEmployeeInput {
  employeeNumber: string;
  name: string;
  departmentId?: string | null;
  costCenterId?: string | null;
  jobGradeId?: string | null;
  effectiveFrom?: string;
}
export interface UpdateEmployeeInput {
  name?: string;
  departmentId?: string | null;
  costCenterId?: string | null;
  jobGradeId?: string | null;
  effectiveFrom?: string;
}
export const CreateEmployeeBodySchema = z.object({
  employeeNumber: z.string().min(1).max(64),
  name: z.string().min(1).max(256),
  departmentId: z.string().min(1).nullable().optional(),
  costCenterId: z.string().min(1).nullable().optional(),
  jobGradeId: z.string().min(1).nullable().optional(),
  effectiveFrom: z.string().min(1).optional(),
  idempotencyKey: z.string().min(1),
});
export const UpdateEmployeeBodySchema = z.object({
  name: z.string().min(1).max(256).optional(),
  departmentId: z.string().min(1).nullable().optional(),
  costCenterId: z.string().min(1).nullable().optional(),
  jobGradeId: z.string().min(1).nullable().optional(),
  effectiveFrom: z.string().min(1).optional(),
  idempotencyKey: z.string().min(1),
});

// JobGrade -------------------------------------------------------------------
export interface CreateJobGradeInput {
  code: string;
  name: string;
  displayOrder?: number;
  effectiveFrom?: string;
}
export interface UpdateJobGradeInput {
  name?: string;
  displayOrder?: number;
  effectiveFrom?: string;
}
export const CreateJobGradeBodySchema = z.object({
  code: z.string().min(1).max(64),
  name: z.string().min(1).max(256),
  displayOrder: z.number().int().optional(),
  effectiveFrom: z.string().min(1).optional(),
  idempotencyKey: z.string().min(1),
});
export const UpdateJobGradeBodySchema = z.object({
  name: z.string().min(1).max(256).optional(),
  displayOrder: z.number().int().optional(),
  effectiveFrom: z.string().min(1).optional(),
  idempotencyKey: z.string().min(1),
});

// CostCenter -----------------------------------------------------------------
export interface CreateCostCenterInput {
  code: string;
  name: string;
  departmentId?: string | null;
  effectiveFrom?: string;
}
export interface UpdateCostCenterInput {
  name?: string;
  departmentId?: string | null;
  effectiveFrom?: string;
}
export const CreateCostCenterBodySchema = z.object({
  code: z.string().min(1).max(64),
  name: z.string().min(1).max(256),
  departmentId: z.string().min(1).nullable().optional(),
  effectiveFrom: z.string().min(1).optional(),
  idempotencyKey: z.string().min(1),
});
export const UpdateCostCenterBodySchema = z.object({
  name: z.string().min(1).max(256).optional(),
  departmentId: z.string().min(1).nullable().optional(),
  effectiveFrom: z.string().min(1).optional(),
  idempotencyKey: z.string().min(1),
});

// BusinessPartner ------------------------------------------------------------
export interface CreateBusinessPartnerInput {
  code: string;
  name: string;
  partnerType: string;
  paymentTerms?: { termDays?: number; method?: string } | null;
  effectiveFrom?: string;
}
export interface UpdateBusinessPartnerInput {
  name?: string;
  partnerType?: string;
  paymentTerms?: { termDays?: number; method?: string } | null;
  effectiveFrom?: string;
}
export const CreateBusinessPartnerBodySchema = z.object({
  code: z.string().min(1).max(64),
  name: z.string().min(1).max(256),
  partnerType: z.string().min(1),
  paymentTerms: PaymentTermsSchema.nullable().optional(),
  effectiveFrom: z.string().min(1).optional(),
  idempotencyKey: z.string().min(1),
});
export const UpdateBusinessPartnerBodySchema = z.object({
  name: z.string().min(1).max(256).optional(),
  partnerType: z.string().min(1).optional(),
  paymentTerms: PaymentTermsSchema.nullable().optional(),
  effectiveFrom: z.string().min(1).optional(),
  idempotencyKey: z.string().min(1),
});
