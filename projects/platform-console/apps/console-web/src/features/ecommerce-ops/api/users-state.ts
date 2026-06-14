import { redirect } from 'next/navigation';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';
import { listUsers, getUser } from './users-api';
import type { UserList, UserDetail, UserListParams } from './user-types';

/**
 * Server-side ecommerce user operations section state for the
 * `(console)/ecommerce/users` routes (TASK-PC-FE-084 — the users facet of
 * ADR-MONO-031 Phase 2b). Mirrors `orders-state.ts` exactly.
 *
 * Eligibility gate: the page resolves the operator's ecommerce eligibility
 * from the data-driven registry and passes it here. If not eligible the
 * section blocks with an actionable state and NO ecommerce call is made.
 *
 * READ-ONLY: no mutation paths, no optimistic lock error handling.
 *
 * Resilience boundary (§ 2.4.10 / § 2.5):
 *   - `401` (IAM OIDC session expired) → `redirect('/login')` — WHOLE-SESSION.
 *   - `403` (role-insufficient) → non-crashing inline "not available to role".
 *   - `503` / timeout / network → DEGRADED — ONLY the ecommerce section
 *     renders a degraded notice; the console shell stays intact.
 *   - any other producer error → degrade rather than crash.
 */
export interface UsersSectionState {
  /** Server-seeded page-0 user list (the table's initialData). */
  users: UserList | null;
  /** True when the operator is not ecommerce-eligible — actionable block. */
  notEligible: boolean;
  /** True on a role-insufficient (403) producer response — inline. */
  forbidden: boolean;
  /** True on 503 / timeout / network — section degrades only. */
  degraded: boolean;
}

const EMPTY: UsersSectionState = {
  users: null,
  notEligible: false,
  forbidden: false,
  degraded: false,
};

/**
 * @param eligible whether the operator is ecommerce-eligible, resolved by
 *   the page from the data-driven registry. `false` ⇒ block (no call).
 * @param params optional list filters (status / email / page / size).
 */
export async function getUsersSectionState(
  eligible: boolean,
  params: UserListParams = {},
): Promise<UsersSectionState> {
  if (!eligible) {
    return { ...EMPTY, notEligible: true };
  }

  try {
    const users = await listUsers({
      page: 0,
      size: 20,
      ...params,
    });
    return { ...EMPTY, users };
  } catch (err) {
    return mapSectionError(err);
  }
}

export interface UserDetailSectionState {
  detail: UserDetail | null;
  notEligible: boolean;
  forbidden: boolean;
  /** True on 404 USER_PROFILE_NOT_FOUND — actionable not-found, not a crash. */
  notFound: boolean;
  degraded: boolean;
}

const DETAIL_EMPTY: UserDetailSectionState = {
  detail: null,
  notEligible: false,
  forbidden: false,
  notFound: false,
  degraded: false,
};

/** Detail-page section state (the `[id]` route). */
export async function getUserDetailSectionState(
  eligible: boolean,
  userId: string,
): Promise<UserDetailSectionState> {
  if (!eligible) {
    return { ...DETAIL_EMPTY, notEligible: true };
  }
  try {
    const detail = await getUser(userId);
    return { ...DETAIL_EMPTY, detail };
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) {
      return { ...DETAIL_EMPTY, notFound: true };
    }
    const mapped = mapSectionError(err);
    return {
      ...DETAIL_EMPTY,
      forbidden: mapped.forbidden,
      degraded: mapped.degraded,
    };
  }
}

/** Shared resilience mapping for the list/detail server reads. */
function mapSectionError(err: unknown): UsersSectionState {
  if (err instanceof ApiError && err.status === 401) {
    // No partial authed state → clean WHOLE-SESSION re-login.
    redirect('/login');
  }
  if (err instanceof ApiError && err.status === 403) {
    return { ...EMPTY, forbidden: true };
  }
  if (err instanceof EcommerceUnavailableError) {
    return { ...EMPTY, degraded: true };
  }
  // Any other producer error on a seeded read → degrade, not crash.
  return { ...EMPTY, degraded: true };
}
