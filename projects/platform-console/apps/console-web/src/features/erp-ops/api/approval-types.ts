import { z } from 'zod';
import type { StatusTone } from '@/shared/ui/StatusBadge';

/**
 * Feature-local types for the erp `approval-service` workflow surface
 * (TASK-PC-FE-051 — ADR-MONO-016 § D3.1 parity slice; erp's FIRST real
 * domain-logic — the approval state machine — exposed in the console).
 * Extended by TASK-PC-FE-053 (multi-stage approval + IN_REVIEW + delegation
 * read-only display).
 *
 * Authoritative producer contract (do NOT redefine — consume):
 *   `erp-platform/specs/contracts/http/approval-api.md`
 *     base path `/api/erp/approval`
 *     GET/POST  /requests              (list / create DRAFT)
 *     GET       /requests/{id}         (detail incl. immutable history)
 *     POST      /requests/{id}/{submit|approve|reject|withdraw}
 *     GET       /inbox                 (the caller's pending SUBMITTED)
 *
 * v2.0 AMENDMENT — multi-stage routing + `IN_REVIEW` (TASK-ERP-BE-012):
 *   `DRAFT → SUBMITTED → IN_REVIEW (2~N stages) → APPROVED | REJECTED | WITHDRAWN`.
 * v2.1 AMENDMENT — delegation/대결 (TASK-ERP-BE-013):
 *   history entry gains `actingForApproverId` when a delegate acted.
 *
 * NON_NULL absent-field convention (producer § `@JsonInclude(NON_NULL)`):
 * nullable response fields (`reason`, `submittedAt`, `finalizedAt`,
 * history-entry `reason`, `stages`, `currentStage`, `actingForApproverId`)
 * are ABSENT from the JSON when unset, never serialized as `null`. These
 * parse to optional/undefined here (zod `.optional()`); the UI renders an
 * absent field as "—" / hidden, never a crash (same convention as the
 * masterdata read surface).
 *
 * TOLERANCE: `status` / `subjectType` / history `transition` are parsed
 * as free strings (a future / unknown enum value renders generically and
 * NEVER throws — the producer is the authority for the enum vocabulary).
 */

// ---------------------------------------------------------------------------
// known enums — surfaced for honest labelling; stored as free strings for
// tolerance (an unknown / future value renders with a generic label).
// ---------------------------------------------------------------------------

export const APPROVAL_STATUSES = [
  'DRAFT',
  'SUBMITTED',
  'IN_REVIEW',
  'APPROVED',
  'REJECTED',
  'WITHDRAWN',
] as const;
export type ApprovalStatus = (typeof APPROVAL_STATUSES)[number];

/**
 * Approval status → shared semantic {@link StatusTone} (the palette lives in
 * `shared/ui/StatusBadge`; the erp badge keeps its own `<span>` for the
 * `data-status` / `data-terminal` attributes but styles it via
 * `statusToneClass` — TASK-PC-FE-159). DRAFT and WITHDRAWN are inactive
 * (neutral — a withdrawn request is recalled, NOT a failure, so it is not
 * danger); SUBMITTED awaits first action (warning); IN_REVIEW is mid-routing
 * (progress); APPROVED is the happy terminal (success); REJECTED is
 * terminal-bad (danger). An unknown/future status → `neutral` (tolerant).
 */
const APPROVAL_STATUS_TONE: Record<ApprovalStatus, StatusTone> = {
  DRAFT: 'neutral',
  SUBMITTED: 'warning',
  IN_REVIEW: 'progress',
  APPROVED: 'success',
  REJECTED: 'danger',
  WITHDRAWN: 'neutral',
};

export function approvalStatusTone(status: string): StatusTone {
  return APPROVAL_STATUS_TONE[status as ApprovalStatus] ?? 'neutral';
}

export const APPROVAL_SUBJECT_TYPES = ['DEPARTMENT', 'EMPLOYEE'] as const;
export type ApprovalSubjectType = (typeof APPROVAL_SUBJECT_TYPES)[number];

/** Terminal (immutable) states — no transition is offered by the console
 *  (the producer rejects any attempt with 409 `APPROVAL_ALREADY_FINALIZED`). */
export const TERMINAL_APPROVAL_STATUSES: readonly string[] = [
  'APPROVED',
  'REJECTED',
  'WITHDRAWN',
];

/** The four producer transition path segments (allow-list). */
export const APPROVAL_TRANSITIONS = [
  'submit',
  'approve',
  'reject',
  'withdraw',
] as const;
export type ApprovalTransition = (typeof APPROVAL_TRANSITIONS)[number];

// ---------------------------------------------------------------------------
// envelope meta — flat erp shape (same wire as the masterdata surface).
// ---------------------------------------------------------------------------

export const ApprovalMetaSchema = z
  .object({
    timestamp: z.string().optional(),
    page: z.number().int().nonnegative().optional(),
    size: z.number().int().positive().optional(),
    totalElements: z.number().int().nonnegative().optional(),
  })
  .passthrough();
export type ApprovalMeta = z.infer<typeof ApprovalMetaSchema>;

// ---------------------------------------------------------------------------
// ApprovalHistoryEntry — one immutable audit row per transition (E4).
//   `reason` ABSENT when none (NON_NULL) → optional.
// ---------------------------------------------------------------------------

export const ApprovalHistoryEntrySchema = z
  .object({
    transition: z.string(),
    actor: z.string(),
    at: z.string(),
    // ABSENT when the transition recorded no reason (submit / optional
    // approve) — NON_NULL convention.
    reason: z.string().optional(),
    // v2.0 — stage index (0-based) for the transition (ABSENT if not a
    // multi-stage request or if the stage is not tracked — NON_NULL).
    stage: z.number().int().optional(),
    // v2.1 — when a delegate acted on behalf of the stage's approver,
    // this carries the approver's id (ABSENT when the approver acted
    // themselves — NON_NULL).
    actingForApproverId: z.string().optional(),
  })
  .passthrough();
export type ApprovalHistoryEntry = z.infer<typeof ApprovalHistoryEntrySchema>;

// ---------------------------------------------------------------------------
// ApprovalStage — one ordered stage in a multi-stage route (v2.0).
//   `status` is free-string tolerant for future values.
// ---------------------------------------------------------------------------

export const ApprovalStageSchema = z
  .object({
    stageIndex: z.number().int(),
    approverId: z.string(),
    // Free-string tolerant: known values are "PENDING" / "APPROVED"
    // (a future value renders generically — never throws).
    status: z.string(),
  })
  .passthrough();
export type ApprovalStage = z.infer<typeof ApprovalStageSchema>;

// ---------------------------------------------------------------------------
// ApprovalSummary — list / inbox item (trimmed, no `history`).
//   `submittedAt` ABSENT until SUBMITTED → optional.
// ---------------------------------------------------------------------------

export const ApprovalSummarySchema = z
  .object({
    id: z.string(),
    status: z.string(),
    subjectType: z.string(),
    subjectId: z.string(),
    title: z.string(),
    approverId: z.string(),
    submitterId: z.string(),
    createdAt: z.string(),
    // ABSENT until SUBMITTED (NON_NULL).
    submittedAt: z.string().optional(),
    // v2.0 — multi-stage fields (ABSENT for single-stage or legacy —
    // NON_NULL; all optional for backward-compat).
    stages: z.array(ApprovalStageSchema).optional(),
    currentStage: z.number().int().optional(),
    totalStages: z.number().int().optional(),
  })
  .passthrough();
export type ApprovalSummary = z.infer<typeof ApprovalSummarySchema>;

// ---------------------------------------------------------------------------
// ApprovalRequest — detail payload (incl. immutable history).
//   `reason` / `submittedAt` / `finalizedAt` ABSENT when unset → optional.
// ---------------------------------------------------------------------------

export const ApprovalRequestSchema = z
  .object({
    id: z.string(),
    status: z.string(),
    subjectType: z.string(),
    subjectId: z.string(),
    title: z.string(),
    approverId: z.string(),
    submitterId: z.string(),
    // ABSENT when no creation reason (NON_NULL).
    reason: z.string().optional(),
    history: z.array(ApprovalHistoryEntrySchema),
    createdAt: z.string(),
    // ABSENT until SUBMITTED (NON_NULL).
    submittedAt: z.string().optional(),
    // ABSENT until APPROVED/REJECTED/WITHDRAWN (NON_NULL).
    finalizedAt: z.string().optional(),
    // v2.0 — multi-stage fields (ABSENT for single-stage or legacy —
    // NON_NULL; all optional for backward-compat).
    stages: z.array(ApprovalStageSchema).optional(),
    // 0-based index of the currently pending stage; ABSENT once the
    // request is finalized (NON_NULL).
    currentStage: z.number().int().optional(),
    totalStages: z.number().int().optional(),
  })
  .passthrough();
export type ApprovalRequest = z.infer<typeof ApprovalRequestSchema>;

// ---------------------------------------------------------------------------
// envelope response schemas.
// ---------------------------------------------------------------------------

export const ApprovalListResponseSchema = z.object({
  data: z.array(ApprovalSummarySchema),
  meta: ApprovalMetaSchema,
});
export type ApprovalListResponse = z.infer<typeof ApprovalListResponseSchema>;

export const ApprovalDetailResponseSchema = z.object({
  data: ApprovalRequestSchema,
  meta: ApprovalMetaSchema,
});
export type ApprovalDetailResponse = z.infer<
  typeof ApprovalDetailResponseSchema
>;

// ---------------------------------------------------------------------------
// query params + inputs.
// ---------------------------------------------------------------------------

export const APPROVAL_DEFAULT_PAGE_SIZE = 20;
export const APPROVAL_MAX_PAGE_SIZE = 100;

/** List query — `status` / `role` are producer filters (absent = both). */
export interface ApprovalListQueryParams {
  status?: string;
  /** `SUBMITTER | APPROVER` — filter to requests where the caller plays
   *  that role (absent = both). */
  role?: string;
  page?: number;
  size?: number;
}

/** Inbox query — minimal first increment (page / size only). */
export interface ApprovalInboxQueryParams {
  page?: number;
  size?: number;
}

/** `POST /requests` create body (DRAFT). `reason` optional.
 * v2.0: `approverIds` (ordered 1~N stage list) OR legacy `approverId`
 * (single stage). The api client guarantees exactly one is set.
 * Both are optional in the TS interface so callers can decide which to
 * use; the proxy validator enforces the XOR at the wire boundary. */
export interface CreateApprovalInput {
  subjectType: string;
  subjectId: string;
  title: string;
  /** Legacy single-approver (1-stage route, backward-compat). Exactly
   *  one of `approverId` / `approverIds` must be provided. */
  approverId?: string;
  /** Ordered multi-stage approver list (v2.0). Exactly one of
   *  `approverId` / `approverIds` must be provided. */
  approverIds?: string[];
  reason?: string;
}

// ---------------------------------------------------------------------------
// proxy-route body parsers — the route handlers validate the incoming
// client body before forwarding to the producer.
// ---------------------------------------------------------------------------

/** `POST /api/erp/approval/requests` create proxy body.
 * v2.0: accepts EITHER `approverId` (legacy, non-blank) OR `approverIds`
 * (non-empty array of non-blank strings). Exactly one must be present
 * (XOR, validated by the `.refine()` guard). */
export const CreateApprovalBodySchema = z
  .object({
    subjectType: z.enum(APPROVAL_SUBJECT_TYPES),
    subjectId: z.string().min(1),
    title: z.string().min(1).max(256),
    // One of these two is required — see .refine() below.
    approverId: z.string().min(1).optional(),
    approverIds: z
      .array(z.string().min(1))
      .min(1)
      .optional(),
    reason: z.string().max(512).optional(),
    idempotencyKey: z.string().min(1),
  })
  .refine(
    (b) => {
      const hasLegacy = typeof b.approverId === 'string' && b.approverId.length > 0;
      const hasMulti =
        Array.isArray(b.approverIds) && b.approverIds.length > 0;
      // Exactly one of the two must be present (XOR).
      return hasLegacy !== hasMulti;
    },
    { message: 'exactly one of approverId / approverIds must be provided' },
  );

/** Transition proxy body — `submit` / `approve` carry no required reason;
 *  `reject` / `withdraw` require a non-blank reason (E4 — 반려/회수 사유
 *  필수). The route validates the reason-required rule per transition. */
export const ApprovalTransitionBodySchema = z.object({
  reason: z.string().max(512).optional(),
  idempotencyKey: z.string().min(1),
});

// ---------------------------------------------------------------------------
// helpers.
// ---------------------------------------------------------------------------

/** True if `status` is a terminal (immutable) state — no transition action
 *  is offered for it (the producer rejects any attempt with 409
 *  `APPROVAL_ALREADY_FINALIZED`). */
export function isTerminalApprovalStatus(status: string): boolean {
  return TERMINAL_APPROVAL_STATUSES.includes(status);
}

/** The transition path segments offered for a given (non-terminal) status,
 *  per the producer state machine:
 *    DRAFT      → submit, withdraw   (submitter)
 *    SUBMITTED  → approve, reject, withdraw
 *    IN_REVIEW  → approve, reject, withdraw  (current stage's approver)
 *    terminal   → (none)
 *  The producer is the authority — the console pre-filters to avoid
 *  obviously-illegal calls, but a 409 from the producer is still surfaced
 *  inline (the console never assumes it owns the truth). */
export function allowedTransitionsFor(status: string): ApprovalTransition[] {
  if (status === 'DRAFT') return ['submit', 'withdraw'];
  if (status === 'SUBMITTED') return ['approve', 'reject', 'withdraw'];
  if (status === 'IN_REVIEW') return ['approve', 'reject', 'withdraw'];
  return [];
}

/** True if the transition requires a non-blank reason (reject / withdraw). */
export function transitionRequiresReason(t: string): boolean {
  return t === 'reject' || t === 'withdraw';
}
