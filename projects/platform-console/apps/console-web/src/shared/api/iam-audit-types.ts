import { z } from 'zod';

/**
 * Feature-local types for the IAM unified audit + security read surface.
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   `iam/specs/contracts/http/admin-api.md`
 *   § `GET /api/admin/audit` (unified view over admin_actions +
 *     login_history + suspicious_events, discriminated by `source`).
 * Consumer obligation: `console-integration-contract.md` § 2.4.2.
 *
 * These zod schemas are the runtime parsers the api-client / tests assert
 * against. They are feature-local (not cross-feature) per
 * architecture.md § Allowed Dependencies.
 *
 * Discriminated union on `source`, tolerant of unknown/future discriminants:
 * an unrecognised `source` parses to a `GenericAuditRow` (never throws —
 * console-integration-contract § 2.4.2 "discriminated rendering tolerance" /
 * task Edge Case "Unknown/future source value").
 */

// --- source filter --------------------------------------------------------

/** The producer's documented `source` discriminants. `admin` (or no
 *  filter) needs `audit.read`; `login_history`/`suspicious` ALSO need
 *  `security.event.read` (intersection — both). */
export const AUDIT_SOURCES = ['admin', 'login_history', 'suspicious'] as const;
export type AuditSource = (typeof AUDIT_SOURCES)[number];

/** The two security `source`s whose query additionally requires
 *  `security.event.read` (intersection-permission rule). */
export const SECURITY_SOURCES: readonly AuditSource[] = [
  'login_history',
  'suspicious',
];

export function isSecuritySource(source: AuditSource | undefined): boolean {
  return source !== undefined && SECURITY_SOURCES.includes(source);
}

// --- discriminated rows ---------------------------------------------------

/** `source=admin` — admin_actions row. */
export const AdminAuditRowSchema = z.object({
  source: z.literal('admin'),
  auditId: z.string(),
  actionCode: z.string(),
  operatorId: z.string(),
  targetId: z.string().nullable().optional(),
  reason: z.string().nullable().optional(),
  outcome: z.string(),
  occurredAt: z.string(),
});
export type AdminAuditRow = z.infer<typeof AdminAuditRowSchema>;

/** `source=login_history` — login_history row (producer-masked IP, geo). */
export const LoginHistoryRowSchema = z.object({
  source: z.literal('login_history'),
  eventId: z.string(),
  accountId: z.string(),
  outcome: z.string(),
  ipMasked: z.string().nullable().optional(),
  geoCountry: z.string().nullable().optional(),
  occurredAt: z.string(),
});
export type LoginHistoryRow = z.infer<typeof LoginHistoryRowSchema>;

/** `source=suspicious` — suspicious_events row (analogous to login_history;
 *  the producer documents it as analogous so the consumer keeps the same
 *  masked shape and renders unknown extra fields generically). */
export const SuspiciousRowSchema = z.object({
  source: z.literal('suspicious'),
  eventId: z.string(),
  accountId: z.string().nullable().optional(),
  outcome: z.string().nullable().optional(),
  ipMasked: z.string().nullable().optional(),
  geoCountry: z.string().nullable().optional(),
  occurredAt: z.string(),
});
export type SuspiciousRow = z.infer<typeof SuspiciousRowSchema>;

/**
 * Fallback for an unrecognised / future `source`. NEVER throws — only
 * `source` + `occurredAt` are required; everything else is rendered
 * generically. (console-integration-contract § 2.4.2 tolerance.)
 */
export const GenericAuditRowSchema = z
  .object({
    source: z.string(),
    occurredAt: z.string().optional(),
  })
  .passthrough();
export type GenericAuditRow = z.infer<typeof GenericAuditRowSchema>;

/**
 * One audit row. Tries the three known discriminants first; any other
 * `source` (or a row that fails a known shape) falls through to the
 * generic row so the table never crashes on a producer evolution.
 */
export const AuditRowSchema = z.union([
  AdminAuditRowSchema,
  LoginHistoryRowSchema,
  SuspiciousRowSchema,
  GenericAuditRowSchema,
]);
export type AuditRow = z.infer<typeof AuditRowSchema>;

// --- page envelope --------------------------------------------------------

export const AuditPageSchema = z.object({
  content: z.array(AuditRowSchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
});
export type AuditPage = z.infer<typeof AuditPageSchema>;

// --- query params ---------------------------------------------------------

/** Hard ceiling — the producer rejects `size > 100` with `422`; the
 *  console client-caps to pre-empt it (task Edge Case / AC). */
export const AUDIT_MAX_PAGE_SIZE = 100;
export const AUDIT_DEFAULT_PAGE_SIZE = 20;

export interface AuditQueryParams {
  accountId?: string;
  actionCode?: string;
  /** ISO-8601 datetime. */
  from?: string;
  /** ISO-8601 datetime. */
  to?: string;
  source?: AuditSource;
  /** SUPER_ADMIN explicit cross-tenant read ONLY — never sent for a
   *  non-super operator (no free-text override). */
  tenantId?: string;
  page?: number;
  size?: number;
}
