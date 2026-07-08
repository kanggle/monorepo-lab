'use client';

/**
 * Client-side operators hooks (architecture.md § Server vs Client
 * Components — React Query is client-only). Every call goes to the
 * same-origin `/api/operators/**` proxy (the typed API client's single
 * backend entry point); the proxy attaches the HttpOnly operator token +
 * tenant server-side AND applies the per-endpoint header matrix — the
 * browser never reads a token or calls IAM directly (contract § 2.3), and
 * a password is never read back from a token cookie.
 *
 * Per-endpoint header fidelity (§ 2.4.3): `create` sends an
 * `idempotencyKey` (the dialog generates it via `crypto.randomUUID()`
 * once per confirmed action, reused only on a retry of THAT action);
 * `edit-roles`/`change-status` send a reason ONLY (NO key); `change-
 * password` is the self path (no reason, no key). The hooks never
 * fabricate a reason — it is required input from the confirm dialog.
 *
 * A password is NEVER placed in a query string, log, or React Query key
 * (the create / change-password keys carry no secret). On a roles change
 * the producer invalidates its own permission cache (awareness note —
 * `admin:operator:perm:{operatorId}`); the console invalidates its local
 * operators list so the table reflects the change.
 *
 * TASK-PC-FE-218 — this file is now a re-export BARREL. The hooks are
 * grouped by cohesion into leaf modules (bodies byte-identical, relocated
 * only): shared keys/helpers in `operators-keys.ts`; the list read in
 * `use-operators-list.ts`; the write mutations in `use-operator-mutations.ts`;
 * the org-scope / assignments read+write set (with the cross-feature,
 * CLIENT-SAFE `ERP_KEY` / erp `types` imports) in `use-operator-assignments.ts`.
 * Consumer import paths (`../hooks/use-operators` / `./use-operators`) are
 * unchanged — everything is re-exported here.
 */

export { useOperatorsList } from './use-operators-list';
export {
  useCreateOperator,
  useEditOperatorRoles,
  useChangeOperatorStatus,
  useChangeOwnPassword,
  useUpdateOwnProfile,
  useSetOperatorProfile,
} from './use-operator-mutations';
export {
  useOperatorAssignments,
  useOrgScopeDepartments,
  useSetOperatorOrgScope,
  useAssignOperator,
  useUnassignOperator,
} from './use-operator-assignments';
