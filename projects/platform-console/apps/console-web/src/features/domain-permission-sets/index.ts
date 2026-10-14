/**
 * `features/domain-permission-sets` public API (Layered-by-Feature — app/
 * imports only this barrel, never feature internals; architecture.md
 * § Allowed Dependencies). ADR-MONO-083 D9 / TASK-PC-FE-338 — the console
 * 「업무 권한 세트」 surface: tenant-scoped DOMAIN BUSINESS permission bundles
 * operators/groups receive. A SEPARATE entity/producer from the IAM admin
 * role catalog (`features/permission-sets`, 「IAM 관리 역할」) — the two names
 * must never collapse into one (ADR-MONO-035 O1-C).
 *
 * This slice (P4 "a") binds rows 1-5 of the producer contract (role catalog
 * read + tenant-set CRUD). Rows 6-11 (operator/group grant/revoke) are a
 * later slice.
 *
 * 🔴 Status honesty (ADR-083 D9): P3 (token-issuance change) is not deployed
 * — a set recorded here does not yet narrow any assume-tenant token.
 */
export { DomainPermissionSetsScreen } from './components/DomainPermissionSetsScreen';
export type { DomainPermissionSetsScreenProps } from './components/DomainPermissionSetsScreen';

export { DomainPermissionSetForm } from './components/DomainPermissionSetForm';
export type { DomainPermissionSetFormProps } from './components/DomainPermissionSetForm';

export {
  DomainSetReasonDialog,
  DOMAIN_SET_LATENCY_COPY,
} from './components/DomainSetReasonDialog';
export type { DomainSetReasonDialogProps } from './components/DomainSetReasonDialog';

export {
  getDomainPermissionSetsState,
} from './api/domain-permission-sets-state';
export type { DomainPermissionSetsState } from './api/domain-permission-sets-state';

// Server-only api fns (consumed by the proxy routes).
export {
  listDomainRoles,
  listDomainPermissionSets,
  getDomainPermissionSet,
  createDomainPermissionSet,
  updateDomainPermissionSet,
  deleteDomainPermissionSet,
} from './api/domain-permission-sets-api';

// Types + schemas.
export {
  DomainRoleSchema,
  DomainRoleListSchema,
  DomainSetRoleRefSchema,
  DomainPermissionSetSchema,
  DomainPermissionSetListSchema,
} from './api/types';
export type {
  DomainRole,
  DomainRoleList,
  DomainSetRoleRef,
  DomainPermissionSet,
  DomainPermissionSetList,
  CreateDomainPermissionSetInput,
  UpdateDomainPermissionSetInput,
} from './api/types';

// Client-side hooks.
export {
  useDomainRoles,
  useDomainPermissionSets,
  useCreateDomainPermissionSet,
  useUpdateDomainPermissionSet,
  useDeleteDomainPermissionSet,
} from './hooks/use-domain-permission-sets';
