import 'server-only';
import { auth } from '@/shared/auth/auth';

/**
 * Server-only access to the authenticated session + bearer token. NEVER import
 * from a client component — `server-only` will throw at build time.
 *
 * Web-store uses `@repo/api-client` (axios) for API calls. The synchronous
 * `getAccessToken` callback expected by `createApiClient` is wired up via
 * `./token-bridge.ts`; this helper exists for direct server-side fetches
 * (Server Actions, Server Components, route handlers) that bypass axios.
 */

export interface WebStoreSession {
  accessToken: string | null;
  accountId: string | null;
  tenantId: string | null;
  roles: string[];
  accountType: string | null;
}

const EMPTY: WebStoreSession = {
  accessToken: null,
  accountId: null,
  tenantId: null,
  roles: [],
  accountType: null,
};

export async function getWebStoreSession(): Promise<WebStoreSession> {
  const session = await auth();
  if (!session) return EMPTY;
  return {
    accessToken: session.accessToken ?? null,
    accountId: session.accountId ?? null,
    tenantId: session.tenantId ?? null,
    roles: session.roles ?? [],
    accountType: session.accountType ?? null,
  };
}

export async function isAuthenticated(): Promise<boolean> {
  const session = await auth();
  return Boolean(session?.accountId);
}
