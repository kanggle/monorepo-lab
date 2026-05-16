import { fetchRegistry } from '@/shared/api/registry-client';
import { RegistryUnavailableError, ApiError } from '@/shared/api/errors';
import type { CatalogState } from '@/shared/api/registry-types';

/**
 * Server-side catalog source. Resilience boundary (integration-heavy I2 +
 * console-integration-contract § 2.5): a registry timeout / 5xx / circuit-open
 * returns a DEGRADED empty catalog rather than throwing — the shell renders,
 * never blank-crashes (task Acceptance "Registry timeout → degraded state").
 *
 * An auth failure (401/403) is rethrown so the page can force re-login
 * (no partial authenticated state — task Failure Scenario).
 */
export async function getCatalog(): Promise<CatalogState> {
  try {
    const registry = await fetchRegistry();
    return { products: registry.products, degraded: false };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) throw err;
    if (
      err instanceof RegistryUnavailableError &&
      err.reason === 'unauthorized'
    ) {
      throw new ApiError(401, 'TOKEN_INVALID', 'session expired');
    }
    // timeout / downstream / circuit_open → degraded.
    return { products: [], degraded: true };
  }
}
