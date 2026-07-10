/**
 * `features/org-hierarchy` public API (Layered-by-Feature — app/ imports only
 * this barrel, never feature internals; architecture.md § Allowed
 * Dependencies). ADR-047 § 4 step 3 — the console org-node hierarchy surface
 * (TASK-PC-FE-237): org-node tree CRUD + entitlement-ceiling editor +
 * subtree-scoped `ORG_ADMIN` assignment. Gated by `org.manage`
 * (SUPER_ADMIN or a parent node's ORG_ADMIN).
 *
 * A ceiling is an entitlement CAP (deny-only), NEVER a grant — `UNBOUNDED`
 * (no cap) and `BOUNDED([])` (permits nothing) are opposite states. See
 * `lib/tree.ts` for the pure ceiling/tree semantics.
 */
export { OrgHierarchyScreen } from './components/OrgHierarchyScreen';
export type { OrgHierarchyScreenProps } from './components/OrgHierarchyScreen';

export { getOrgHierarchyState } from './api/org-nodes-state';
export type { OrgHierarchyState } from './api/org-nodes-state';

// Server-only api fns (consumed by the proxy routes + the layout's tenant-
// switcher grouping).
export {
  listOrgNodes,
  getOrgNode,
  createOrgNode,
  updateOrgNode,
  deleteOrgNode,
  setOrgNodeCeiling,
  getOrgNodeTenants,
  listOrgNodeAdmins,
  grantOrgNodeAdmin,
  revokeOrgNodeAdmin,
} from './api/org-nodes-api';

// Pure lib fns (React-free — unit-testable).
export {
  buildTree,
  descendantIds,
  isCeilingSubset,
  permitsNothing,
  effectiveCeilingOf,
} from './lib/tree';
export type { OrgNodeTreeItem } from './lib/tree';

// Types + schemas (the proxy body schemas reuse `CeilingSchema`).
export {
  CeilingSchema,
  OrgNodeSchema,
  OrgNodeListSchema,
  SubtreeTenantsSchema,
  OrgAdminSchema,
  OrgAdminListSchema,
  OrgAdminGrantSchema,
  ORG_DOMAIN_KEYS,
  ORG_ADMIN_ROLE,
  MAX_ORG_NODE_DEPTH,
  KNOWN_ORG_ADMIN_ROLES,
} from './api/types';
export type {
  Ceiling,
  OrgNode,
  OrgNodeList,
  SubtreeTenants,
  OrgAdmin,
  OrgAdminList,
  OrgAdminGrant,
  OrgDomainKey,
  CreateOrgNodeInput,
  UpdateOrgNodeInput,
  GrantOrgAdminInput,
} from './api/types';
