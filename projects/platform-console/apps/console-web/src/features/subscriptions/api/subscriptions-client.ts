import { SubscriptionsUnavailableError } from '@/shared/api/errors';
import {
  callAdminGateway,
  type AdminGatewayProfile,
} from '@/shared/api/iam-gateway';

/**
 * Server-side IAM admin-service tenant domain-subscription client — a thin
 * wrapper over the shared {@link callAdminGateway} core (TASK-PC-FE-208 dedup;
 * originally TASK-PC-FE-183 / ADR-MONO-023, admin-api.md § Subscription
 * Management, BE-343).
 *
 * Auth invariant (§ 2.1 trust boundary): the `/api/admin/**` credential is the
 * EXCHANGED operator token (`getOperatorToken()`), NEVER the IAM OIDC access
 * token. Absent ⇒ `401 TOKEN_INVALID`, no fetch. A self-onboarded tenant owner
 * holds this operator token (from the onboarding re-exchange, PC-FE-182) with
 * `subscription.manage` via their `TENANT_BILLING_ADMIN` grant.
 *
 * Tenant invariant: the owner's active tenant is always sent as `X-Tenant-Id`
 * (`getActiveTenant()`); absent ⇒ `400 NO_ACTIVE_TENANT`. The producer enforces
 * `subscription.manage` is tenant-scoped, so the owner can only ever manage
 * their own tenant's subscriptions.
 *
 * Header matrix (admin-api.md § Subscription): BOTH the subscribe POST and the
 * status PATCH require `X-Operator-Reason` (percent-encoded — the producer
 * decodes). NO `Idempotency-Key` (the producer documents none). Resilience
 * (§ 2.5): 401 → `ApiError` (re-login); 403/404/409/400 → `ApiError` (inline
 * actionable — the 409 ALREADY_EXISTS drives the resume affordance); 503/timeout
 * → {@link SubscriptionsUnavailableError} (subscription surface degrades only).
 */

export const SUBSCRIPTIONS_PREFIX = '/api/admin/subscriptions';

type HttpMethod = 'POST' | 'PATCH';

export interface SubscriptionCallOptions {
  method: HttpMethod;
  path: string;
  /** Operator-entered audit reason → `X-Operator-Reason` (required by both). */
  reason: string;
  body: unknown;
}

/**
 * subscriptions profile for the shared {@link callAdminGateway} core: the IAM
 * subscription surface (`SUBSCRIPTIONS_TIMEOUT_MS`) that degrades via
 * {@link SubscriptionsUnavailableError} and logs `subscriptions_*` events.
 * `forbiddenMode: 'generic'` (403 → inline `!ok`); no forced mutation headers
 * (the caller always supplies the required reason; no idempotency key).
 */
const SUBSCRIPTIONS_PROFILE: AdminGatewayProfile = {
  logPrefix: 'subscriptions',
  requestFailedLabel: 'subscription request failed',
  resolveTimeoutMs: (env) => env.SUBSCRIPTIONS_TIMEOUT_MS,
  makeUnavailable: (reason, code, message) =>
    new SubscriptionsUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof SubscriptionsUnavailableError,
  messages: {
    degraded: 'IAM subscription service unavailable',
    timeout: 'IAM subscription call timed out',
    network: 'IAM subscription call failed',
  },
  forbiddenMode: 'generic',
  forceMutationHeaders: false,
};

/**
 * Single hardened call site — a thin wrapper over the shared
 * {@link callAdminGateway} core with the {@link SUBSCRIPTIONS_PROFILE}. Both
 * endpoints carry a required reason + JSON body.
 */
export async function callSubscriptions<T>(
  opts: SubscriptionCallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  return callAdminGateway(
    {
      method: opts.method,
      path: opts.path,
      reason: opts.reason,
      body: opts.body,
    },
    parse,
    SUBSCRIPTIONS_PROFILE,
  );
}
