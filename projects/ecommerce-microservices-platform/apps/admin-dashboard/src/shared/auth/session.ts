import 'server-only';
import { auth } from '@/shared/auth/auth';

/**
 * Server-only access to the authenticated session + bearer token. NEVER import
 * from a client component — `server-only` will throw at build time.
 */

export interface AdminSession {
  accessToken: string | null;
  accountId: string | null;
  tenantId: string | null;
  roles: string[];
  accountType: string | null;
}

const EMPTY: AdminSession = {
  accessToken: null,
  accountId: null,
  tenantId: null,
  roles: [],
  accountType: null,
};

export async function getAdminSession(): Promise<AdminSession> {
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
