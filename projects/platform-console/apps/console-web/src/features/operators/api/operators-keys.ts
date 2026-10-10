'use client';

import { useQueryClient } from '@tanstack/react-query';
import { type OperatorListParams } from './types';

/**
 * Shared React Query key factory + list invalidation helper for the operators
 * feature hooks (TASK-PC-FE-218 cohesion split of `use-operators.ts`). This is
 * a dependency-free leaf module (no sibling-hook imports) so the list /
 * mutations / assignments hook modules can share the keyspace without a cycle.
 */

const OPERATORS_KEY = 'operators';

function listKey(params: OperatorListParams) {
  return [
    OPERATORS_KEY,
    params.status ?? null,
    params.page ?? 0,
    params.size ?? 20,
  ] as const;
}

function invalidateOperators(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: [OPERATORS_KEY] });
}

// --- org-scope assignments (TASK-PC-FE-050) -------------------------------

/** Assignments query key — scoped by operatorId (the active tenant is
 *  attached server-side, so it is NOT part of the client key; a tenant
 *  switch remounts the page with a fresh server render). */
function assignmentsKey(operatorId: string) {
  return [OPERATORS_KEY, 'assignments', operatorId] as const;
}

// --- operator invitations (TASK-MONO-772 S5) -------------------------------

/**
 * Invitations query key. 🔴 The TENANT is part of the key (TASK-MONO-780 /
 * PC-FE-044 lesson): the list is tenant-scoped, so two tenants must never
 * share a cache entry — a switch produces a NEW key (the page re-renders with
 * the new `activeTenant`) and a fresh fetch, rather than the previous
 * tenant's rows. It also sits under the `['operators']` root, which
 * `useTenantSwitch` invalidates on every switch, so a mounted list refetches
 * through the proxy with the new active-tenant cookie either way.
 */
const INVITATIONS_SEGMENT = 'invitations';

function invitationsKey(
  tenantId: string | null,
  status: string,
  page: number,
  size: number,
) {
  return [OPERATORS_KEY, INVITATIONS_SEGMENT, tenantId, status, page, size] as const;
}

function invalidateInvitations(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: [OPERATORS_KEY, INVITATIONS_SEGMENT] });
}

export {
  OPERATORS_KEY,
  listKey,
  invalidateOperators,
  assignmentsKey,
  INVITATIONS_SEGMENT,
  invitationsKey,
  invalidateInvitations,
};
