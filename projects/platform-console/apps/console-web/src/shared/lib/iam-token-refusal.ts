/**
 * TASK-MONO-772 S4 (console-integration-contract § 2.6.3) — IAM token-endpoint refusals the console tells
 * apart by the IAM error body, never by guessing. One module so the callback and the refresh sequence read
 * the SAME predicate (two copies is how one side quietly drifts).
 *
 * `operator_eligibility_unavailable` — IAM could not ask admin-service whether a personal (consumer-pool)
 * account has a live operator facet, and refused fail-closed (IAM `auth-api.md` § 풀 계정의 콘솔 토큰,
 * `TenantClaimTokenCustomizer.OPERATOR_ELIGIBILITY_UNAVAILABLE`). Matched by **whole-value equality** on
 * `error_description` — the IAM fixed-constant convention. It is NOT «wrong account»: the TASK-PC-FE-324
 * `'consumer-pool'` substring predicate stays as it was, and this one is checked before it.
 */
export const OPERATOR_ELIGIBILITY_UNAVAILABLE = 'operator_eligibility_unavailable';

/** The console's own login error code for that refusal (`/login?error=…`). */
export const OPERATOR_CHECK_UNAVAILABLE = 'operator_check_unavailable';

/** The BFF error code the browser `POST /api/auth/refresh` answers with (`503`). */
export const OPERATOR_CHECK_UNAVAILABLE_CODE = 'OPERATOR_CHECK_UNAVAILABLE';

export function isOperatorEligibilityUnavailable(body: unknown): boolean {
  if (typeof body !== 'object' || body === null) return false;
  const { error, error_description: description } = body as {
    error?: unknown;
    error_description?: unknown;
  };
  return error === 'invalid_grant' && description === OPERATOR_ELIGIBILITY_UNAVAILABLE;
}
