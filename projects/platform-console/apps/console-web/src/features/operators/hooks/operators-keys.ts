'use client';

import { useQueryClient } from '@tanstack/react-query';
import { type OperatorListParams } from '../api/types';

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

export { OPERATORS_KEY, listKey, invalidateOperators, assignmentsKey };
