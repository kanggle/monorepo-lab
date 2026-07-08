'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import {
  CreateOperatorResultSchema,
  type CreateOperatorResult,
  type CreateOperatorInput,
  EditRolesResultSchema,
  type EditRolesResult,
  ChangeStatusResultSchema,
  type ChangeStatusResult,
  type ChangePasswordInput,
  type OperatorStatus,
  type UpdateProfileInput,
} from '../api/types';
import { invalidateOperators } from './operators-keys';

// --- mutation: create -----------------------------------------------------

interface CreateArgs {
  input: CreateOperatorInput;
  reason: string;
  /** Stable per the confirmed create (the confirm dialog generates it). */
  idempotencyKey: string;
}

export function useCreateOperator() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ input, reason, idempotencyKey }: CreateArgs) => {
      const raw = await apiClient.post<unknown>('/api/operators', {
        ...input,
        reason,
        idempotencyKey,
      });
      return CreateOperatorResultSchema.parse(raw) as CreateOperatorResult;
    },
    onSuccess: () => invalidateOperators(qc),
  });
}

// --- mutation: edit-roles (NO idempotency key — per the producer) ---------

interface EditRolesArgs {
  operatorId: string;
  roles: string[];
  reason: string;
}

export function useEditOperatorRoles() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ operatorId, roles, reason }: EditRolesArgs) => {
      const raw = await apiClient.post<unknown>(
        `/api/operators/${encodeURIComponent(operatorId)}/roles`,
        { roles, reason },
      );
      return EditRolesResultSchema.parse(raw) as EditRolesResult;
    },
    // The producer invalidates its own perm cache on a role change; we
    // invalidate the local operators list so the table reflects it.
    onSuccess: () => invalidateOperators(qc),
  });
}

// --- mutation: change-status (NO idempotency key — per the producer) ------

interface ChangeStatusArgs {
  operatorId: string;
  status: OperatorStatus;
  reason: string;
}

export function useChangeOperatorStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ operatorId, status, reason }: ChangeStatusArgs) => {
      const raw = await apiClient.post<unknown>(
        `/api/operators/${encodeURIComponent(operatorId)}/status`,
        { status, reason },
      );
      return ChangeStatusResultSchema.parse(raw) as ChangeStatusResult;
    },
    onSuccess: () => invalidateOperators(qc),
  });
}

// --- mutation: change-password (self; 204; no reason/key) -----------------

export function useChangeOwnPassword() {
  return useMutation({
    mutationFn: async (input: ChangePasswordInput) => {
      // 204 No Content — the proxy returns an empty 200/204; we ignore the
      // body. The password is in the POST body to the same-origin proxy
      // ONLY (HTTPS, server-side forwarded) — never a query string / log.
      await apiClient.post<unknown>('/api/operators/me/password', {
        currentPassword: input.currentPassword,
        newPassword: input.newPassword,
      });
      return true;
    },
    // No list invalidation — changing your own password does not alter the
    // operators table.
  });
}

// --- mutation: update-profile (self; 204; no reason/key) — TASK-PC-FE-016 -

export function useUpdateOwnProfile() {
  return useMutation({
    mutationFn: async (input: UpdateProfileInput) => {
      // 204 No Content — the proxy returns an empty 204. The body shape
      // mirrors the registry read shape verbatim:
      // `{ operatorContext: { defaultAccountId: string | null } }`.
      await apiClient.post<unknown>('/api/operators/me/profile', {
        operatorContext: { defaultAccountId: input.defaultAccountId },
      });
      return true;
    },
    // No operators-list invalidation — the profile carrier lives on the
    // registry response, not the operators table; consumers re-read via
    // `getCatalog()` (registry is read-side authoritative — fire-and-re-read).
  });
}

// --- mutation: admin-set-profile (admin-on-behalf-of; 204; reason ONLY) ---
// TASK-PC-FE-017 / TASK-BE-307. SUPER_ADMIN sets ANOTHER operator's
// `operatorContext.defaultAccountId`. The proxy attaches the operator token +
// active tenant + `X-Operator-Reason` server-side; NO `Idempotency-Key` per
// the producer matrix (mirror /roles + /status non-uniformity — § 2.4.3
// row 7). Self via this path → producer `400 SELF_PROFILE_UPDATE_FORBIDDEN
// _VIA_ADMIN_PATH` (UI gates the per-row button when the row is self).

interface SetOperatorProfileArgs {
  operatorId: string;
  /** UUID-like opaque string OR `null` to clear (the producer rejects ""). */
  defaultAccountId: string | null;
  /** Operator audit reason — required, non-empty trimmed. */
  reason: string;
}

export function useSetOperatorProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      operatorId,
      defaultAccountId,
      reason,
    }: SetOperatorProfileArgs) => {
      // 204 No Content — the proxy returns an empty 204. Body shape on the
      // wire is `{ defaultAccountId, reason }` (the proxy reconstructs the
      // GAP-side `{ operatorContext: { defaultAccountId } }` body).
      await apiClient.post<unknown>(
        `/api/operators/${encodeURIComponent(operatorId)}/profile`,
        { defaultAccountId, reason },
      );
      return true;
    },
    // Invalidate the operators list so any cached value reflects the new
    // baseline (in v1 the list does not expose `operatorContext` per item;
    // when extended in a follow-up task, this invalidation is the read-back).
    onSuccess: () => invalidateOperators(qc),
  });
}
