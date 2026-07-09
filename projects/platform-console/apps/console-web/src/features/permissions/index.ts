/**
 * `features/permissions` public API (Layered-by-Feature — app/ imports only
 * this barrel, never feature internals; architecture.md § Allowed
 * Dependencies). IAM RBAC 권한 카탈로그 조회, TASK-PC-FE-227 (read-only — no
 * mutation surface). The actual data client (`GET /api/admin/roles` +
 * `GET /api/admin/permissions`) is `shared/api/rbac-catalog.ts` — shared
 * with the sibling `features/permission-sets` (architecture.md § Forbidden
 * Dependencies: cross-feature imports are forbidden, so the shared value is
 * promoted to `shared/`, not owned by either feature).
 */
export { PermissionsScreen } from './components/PermissionsScreen';
export type { PermissionsScreenProps } from './components/PermissionsScreen';
