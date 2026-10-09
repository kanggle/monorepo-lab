/**
 * `features/tenant-entry-policy` public API (Layered-by-Feature — app/ imports
 * only this barrel). TASK-MONO-771 S5 — the tenant ENTRY POLICY control
 * («운영자 진입 2단계 인증», console-integration-contract § 2.4.3.3), rendered on
 * the tenant detail page and the «보안 설정» page.
 *
 * Server-only API functions (`entry-policy-api.ts`) are imported by the route
 * handlers directly from `./api/entry-policy-api` (the sibling-feature
 * convention for `runtime = 'nodejs'` handlers).
 */
export {
  EntryPolicyPanel,
  ENTRY_POLICY_ON_COPY,
  ENTRY_POLICY_OFF_COPY,
} from './components/EntryPolicyPanel';
export type { EntryPolicyPanelProps } from './components/EntryPolicyPanel';
export { EntryPolicySection } from './components/EntryPolicySection';
export { getEntryPolicyState } from './api/entry-policy-state';
export type { EntryPolicyState } from './api/entry-policy-state';
export type { EntryPolicy, EnrolmentSummary } from './api/types';
