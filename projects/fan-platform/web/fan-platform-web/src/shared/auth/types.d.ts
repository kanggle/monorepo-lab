/**
 * Module augmentation for next-auth v5 — extends the default Session and JWT
 * to carry the GAP-specific claims (`tenant_id`, `account_id`, `roles`) and
 * the access/refresh tokens (server-only).
 */
import 'next-auth';
import 'next-auth/jwt';

declare module 'next-auth' {
  interface Session {
    accountId?: string | null;
    tenantId?: string | null;
    roles?: string[];
    /** Server-side only — never read from a client component. */
    accessToken?: string;
  }

  interface User {
    accountId?: string;
    tenantId?: string | null;
    roles?: string[];
  }
}

declare module 'next-auth/jwt' {
  interface JWT {
    accessToken?: string;
    refreshToken?: string;
    expiresAt?: number;
    accountId?: string;
    tenantId?: string | null;
    roles?: string[];
  }
}
