import { z } from 'zod';

/**
 * Manual TypeScript types + zod schemas derived from
 * `specs/contracts/http/admin-api.md`. Keep in sync when the contract changes.
 */

export const AccountStatusSchema = z.enum(['ACTIVE', 'LOCKED', 'DORMANT', 'DELETED']);
export type AccountStatus = z.infer<typeof AccountStatusSchema>;

export const OperatorRoleSchema = z.enum(['SUPER_ADMIN', 'SUPPORT_READONLY', 'SUPPORT_LOCK', 'SECURITY_ANALYST']);
export type OperatorRole = z.infer<typeof OperatorRoleSchema>;

// ---- Lock / Unlock ----
export const LockRequestSchema = z.object({
  reason: z.string().min(1),
  ticketId: z.string().optional(),
});
export type LockRequest = z.infer<typeof LockRequestSchema>;

export const LockResponseSchema = z.object({
  accountId: z.string(),
  previousStatus: AccountStatusSchema,
  currentStatus: AccountStatusSchema,
  operatorId: z.string(),
  lockedAt: z.string(),
  auditId: z.string(),
});
export type LockResponse = z.infer<typeof LockResponseSchema>;

export const UnlockResponseSchema = LockResponseSchema.omit({ lockedAt: true }).extend({
  unlockedAt: z.string(),
});
export type UnlockResponse = z.infer<typeof UnlockResponseSchema>;

// ---- Session revoke ----
export const RevokeResponseSchema = z.object({
  accountId: z.string(),
  revokedSessionCount: z.number().int().nonnegative(),
  operatorId: z.string(),
  revokedAt: z.string(),
  auditId: z.string(),
});
export type RevokeResponse = z.infer<typeof RevokeResponseSchema>;

// ---- Audit ----
export const AuditAdminEntrySchema = z.object({
  source: z.literal('admin'),
  auditId: z.string(),
  actionCode: z.string(),
  operatorId: z.string(),
  targetId: z.string(),
  reason: z.string(),
  outcome: z.enum(['SUCCESS', 'FAILURE']),
  occurredAt: z.string(),
});

export const AuditLoginEntrySchema = z.object({
  source: z.literal('login_history'),
  eventId: z.string(),
  accountId: z.string(),
  outcome: z.enum(['SUCCESS', 'FAILURE']),
  ipMasked: z.string().optional(),
  geoCountry: z.string().optional(),
  occurredAt: z.string(),
});

export const AuditSuspiciousEntrySchema = z.object({
  source: z.literal('suspicious'),
  eventId: z.string(),
  accountId: z.string().optional(),
  reasonCode: z.string().optional(),
  occurredAt: z.string(),
});

export const AuditEntrySchema = z.discriminatedUnion('source', [
  AuditAdminEntrySchema,
  AuditLoginEntrySchema,
  AuditSuspiciousEntrySchema,
]);
export type AuditEntry = z.infer<typeof AuditEntrySchema>;

export const AuditPageSchema = z.object({
  content: z.array(AuditEntrySchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
});
export type AuditPage = z.infer<typeof AuditPageSchema>;

// ---- Accounts (admin-service / account-service projection) ----
export const AccountSummarySchema = z.object({
  id: z.string(),
  email: z.string(),
  status: AccountStatusSchema,
  createdAt: z.string(),
  lastLoginAt: z.string().optional(),
});
export type AccountSummary = z.infer<typeof AccountSummarySchema>;

export const AccountPageSchema = z.object({
  content: z.array(AccountSummarySchema),
  totalElements: z.number().int().nonnegative().default(0),
  page: z.number().int().nonnegative().default(0),
  size: z.number().int().nonnegative().default(20),
  totalPages: z.number().int().nonnegative().default(0),
});
export type AccountPage = z.infer<typeof AccountPageSchema>;

export const AccountDetailSchema = AccountSummarySchema.extend({
  profile: z
    .object({
      displayName: z.string().optional(),
      phoneMasked: z.string().optional(),
    })
    .optional(),
  recentLogins: z.array(AuditLoginEntrySchema).default([]),
});
export type AccountDetail = z.infer<typeof AccountDetailSchema>;

// ---- Operator session (/me) ----
export const OperatorSessionSchema = z.object({
  operatorId: z.string(),
  email: z.string(),
  roles: z.array(OperatorRoleSchema),
});
export type OperatorSession = z.infer<typeof OperatorSessionSchema>;

// ---- Bulk Lock ----
export const BulkLockRequestSchema = z.object({
  accountIds: z.array(z.string()).min(1).max(100),
  reason: z.string().min(8),
  ticketId: z.string().optional(),
});
export type BulkLockRequest = z.infer<typeof BulkLockRequestSchema>;

export const BulkLockOutcomeSchema = z.enum(['LOCKED', 'NOT_FOUND', 'ALREADY_LOCKED', 'FAILURE']);
export type BulkLockOutcome = z.infer<typeof BulkLockOutcomeSchema>;

export const BulkLockResultItemSchema = z.object({
  accountId: z.string(),
  outcome: BulkLockOutcomeSchema,
  error: z.string().optional(),
});
export type BulkLockResultItem = z.infer<typeof BulkLockResultItemSchema>;

export const BulkLockResponseSchema = z.object({
  results: z.array(BulkLockResultItemSchema),
});
export type BulkLockResponse = z.infer<typeof BulkLockResponseSchema>;

// ---- 2FA Enrollment ----
export const TotpEnrollResponseSchema = z.object({
  otpauthUri: z.string(),
  recoveryCodes: z.array(z.string()),
  enrolledAt: z.string(),
  bootstrapToken: z.string(),
  bootstrapTokenTtlSeconds: z.number().int().positive(),
});
export type TotpEnrollResponse = z.infer<typeof TotpEnrollResponseSchema>;

export const TotpVerifyResponseSchema = z.object({
  verified: z.literal(true),
});
export type TotpVerifyResponse = z.infer<typeof TotpVerifyResponseSchema>;

// ---- GDPR Delete ----
export const GdprDeleteRequestSchema = z.object({
  reason: z.string().min(1),
  ticketId: z.string().optional(),
});
export type GdprDeleteRequest = z.infer<typeof GdprDeleteRequestSchema>;

export const GdprDeleteResponseSchema = z.object({
  accountId: z.string(),
  status: z.literal('DELETED'),
  maskedAt: z.string(),
  auditId: z.string(),
});
export type GdprDeleteResponse = z.infer<typeof GdprDeleteResponseSchema>;

// ---- Data Export (GDPR portability) ----
export const DataExportResponseSchema = z.object({
  accountId: z.string(),
  email: z.string(),
  status: z.string(),
  createdAt: z.string(),
  profile: z.object({
    displayName: z.string().nullable(),
    phoneNumber: z.string().nullable(),
    birthDate: z.string().nullable(),
    locale: z.string().nullable(),
    timezone: z.string().nullable(),
  }),
  exportedAt: z.string(),
});
export type DataExportResponse = z.infer<typeof DataExportResponseSchema>;

// ---- Operator management ----
export const OperatorStatusSchema = z.enum(['ACTIVE', 'SUSPENDED']);
export type OperatorStatus = z.infer<typeof OperatorStatusSchema>;

export const OperatorSchema = z.object({
  operatorId: z.string(),
  email: z.string(),
  displayName: z.string(),
  status: OperatorStatusSchema,
  roles: z.array(OperatorRoleSchema),
  totpEnrolled: z.boolean(),
  lastLoginAt: z.string().optional().nullable(),
  createdAt: z.string(),
});
export type Operator = z.infer<typeof OperatorSchema>;

export const OperatorListResponseSchema = z.object({
  content: z.array(OperatorSchema),
  totalElements: z.number().int().nonnegative().default(0),
  page: z.number().int().nonnegative().default(0),
  size: z.number().int().nonnegative().default(20),
  totalPages: z.number().int().nonnegative().default(0),
});
export type OperatorListResponse = z.infer<typeof OperatorListResponseSchema>;

export const CreateOperatorRequestSchema = z.object({
  email: z.string().email(),
  displayName: z.string().min(1).max(64),
  password: z.string().min(10),
  roles: z.array(OperatorRoleSchema),
});
export type CreateOperatorRequest = z.infer<typeof CreateOperatorRequestSchema>;

export const CreateOperatorResponseSchema = z.object({
  operatorId: z.string(),
  email: z.string(),
  displayName: z.string(),
  status: OperatorStatusSchema,
  roles: z.array(OperatorRoleSchema),
  totpEnrolled: z.boolean(),
  createdAt: z.string(),
  auditId: z.string(),
});
export type CreateOperatorResponse = z.infer<typeof CreateOperatorResponseSchema>;

export const PatchRolesRequestSchema = z.object({
  roles: z.array(OperatorRoleSchema),
});
export type PatchRolesRequest = z.infer<typeof PatchRolesRequestSchema>;

export const PatchRolesResponseSchema = z.object({
  operatorId: z.string(),
  roles: z.array(OperatorRoleSchema),
  auditId: z.string(),
});
export type PatchRolesResponse = z.infer<typeof PatchRolesResponseSchema>;

export const PatchStatusRequestSchema = z.object({
  status: OperatorStatusSchema,
});
export type PatchStatusRequest = z.infer<typeof PatchStatusRequestSchema>;

export const PatchStatusResponseSchema = z.object({
  operatorId: z.string(),
  previousStatus: OperatorStatusSchema,
  currentStatus: OperatorStatusSchema,
  auditId: z.string(),
});
export type PatchStatusResponse = z.infer<typeof PatchStatusResponseSchema>;

// ---- Password change ----
export const ChangePasswordRequestSchema = z.object({
  currentPassword: z.string().min(1),
  newPassword: z.string().min(8).max(128),
});
export type ChangePasswordRequest = z.infer<typeof ChangePasswordRequestSchema>;

// ---- Error envelope ----
export const ApiErrorBodySchema = z.object({
  code: z.string(),
  message: z.string(),
  timestamp: z.string().optional(),
});
export type ApiErrorBody = z.infer<typeof ApiErrorBodySchema>;
