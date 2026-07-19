/**
 * `features/operator-groups` public API (Layered-by-Feature — app/ imports only
 * this barrel, never feature internals; architecture.md § Allowed
 * Dependencies). ADR-MONO-046 § 4 step 3 — the console 운영자 그룹 surface
 * (TASK-PC-FE-250): group CRUD + membership + group-level grants with fan-out +
 * ≤-own no-escalation gating. Gated by `group.manage` (reads too; SUPER_ADMIN,
 * self-tenant TENANT_ADMIN, or subtree ORG_ADMIN).
 *
 * v1 is fan-out (ADR-MONO-046 D2-A): a group grant materialises to each current
 * member's plain flat per-operator row (`group_origin` marker); group
 * membership is NOT an evaluation-time edge. This surface manages the aggregate
 * + its membership/grant templates only.
 */
export { OperatorGroupsScreen } from './components/OperatorGroupsScreen';
export type { OperatorGroupsScreenProps } from './components/OperatorGroupsScreen';

export {
  getOperatorGroupsState,
  getOperatorGroupDetailState,
} from './api/operator-groups-state';
export type {
  OperatorGroupsState,
  OperatorGroupDetailState,
} from './api/operator-groups-state';

// Server-only api fns (consumed by the proxy routes).
export {
  listGroups,
  getGroup,
  createGroup,
  updateGroup,
  deleteGroup,
  listMembers,
  addMember,
  removeMember,
  listGrants,
  addGrants,
  removeGrant,
} from './api/operator-groups-api';

// Types + schemas.
export {
  GroupSchema,
  GroupPageSchema,
  GroupMemberSchema,
  GroupMemberListSchema,
  GroupGrantSchema,
  GroupGrantListSchema,
  GROUP_GRANT_TYPES,
  KNOWN_GROUP_ROLES,
} from './api/types';
export type {
  Group,
  GroupPage,
  GroupMember,
  GroupMemberList,
  GroupGrant,
  GroupGrantList,
  GroupGrantType,
  GroupListParams,
  CreateGroupInput,
  UpdateGroupInput,
  AddMemberInput,
  AddGrantsInput,
} from './api/types';
