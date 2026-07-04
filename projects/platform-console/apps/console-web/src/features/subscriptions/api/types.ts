import { z } from 'zod';

/**
 * Tenant domain-subscription types (TASK-PC-FE-183 / ADR-MONO-023).
 * Consumes `admin-api.md` § Subscription Management (BE-343 — authoritative
 * producer; request/response/error owned by IAM admin-service).
 */

/** SubscriptionStatus state machine (account-service authority). The console
 *  only ever DRIVES a transition target; the producer guards the machine. */
export const SubscriptionStatusSchema = z.enum([
  'ACTIVE',
  'SUSPENDED',
  'CANCELLED',
]);
export type SubscriptionStatus = z.infer<typeof SubscriptionStatusSchema>;

/** Producer POST 201 / PATCH 200 response (admin-api.md § Subscription). */
export const SubscriptionResultSchema = z.object({
  tenantId: z.string().min(1),
  domainKey: z.string().min(1),
  previousStatus: SubscriptionStatusSchema.nullable(),
  currentStatus: SubscriptionStatusSchema,
  occurredAt: z.string().min(1),
});
export type SubscriptionResult = z.infer<typeof SubscriptionResultSchema>;

/**
 * The console-derived per-domain subscription state. Because there is NO
 * list/GET subscriptions endpoint, "subscribed" is derived from the catalog
 * (a domain is ACTIVE for the active tenant ⟺ it appears in the registry —
 * SUSPENDED/CANCELLED drop out of the catalog per ADR-023). So the console can
 * reliably distinguish only ACTIVE vs NOT-IN-CATALOG; a NOT-IN-CATALOG domain
 * may be genuinely never-subscribed OR suspended/cancelled — the subscribe
 * `409 SUBSCRIPTION_ALREADY_EXISTS` recovery (→ resume) covers the latter.
 */
export type DerivedSubscriptionState = 'ACTIVE' | 'NOT_SUBSCRIBED';
