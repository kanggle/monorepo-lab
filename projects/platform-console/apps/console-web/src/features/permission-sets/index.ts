/**
 * `features/permission-sets` public API (Layered-by-Feature — app/ imports
 * only this barrel, never feature internals; architecture.md § Allowed
 * Dependencies). IAM 권한 세트(=role) 조회, TASK-PC-FE-228 (read-only — no
 * mutation surface). The actual data client (`GET /api/admin/roles` +
 * `GET /api/admin/permissions`) is `shared/api/rbac-catalog.ts` — shared
 * with the sibling `features/permissions` (architecture.md § Forbidden
 * Dependencies: cross-feature imports are forbidden, so the shared value is
 * promoted to `shared/`, not owned by either feature). `permission_set_id`
 * physically = `admin_roles.id` (ADR-MONO-020 § D5) — no new backend
 * entity; this feature is a re-framing, not a new producer surface.
 */
export { PermissionSetsScreen } from './components/PermissionSetsScreen';
export type { PermissionSetsScreenProps } from './components/PermissionSetsScreen';
