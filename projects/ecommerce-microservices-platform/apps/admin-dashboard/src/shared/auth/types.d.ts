/**
 * Module augmentation for next-auth v5 — extends the default Session and JWT
 * to carry the GAP-specific claims (`tenant_id`, `account_id`, `roles`,
 * `account_type`) and the access/refresh tokens. Mirrors web-store's
 * augmentation; both apps share the same `next-auth` types schema.
 */
import 'next-auth';
import 'next-auth/jwt';

declare module 'next-auth' {
  interface Session {
    accountId?: string | null;
    tenantId?: string | null;
    roles?: string[];
    accountType?: string | null;
    accessToken?: string;
  }

  interface User {
    accountId?: string;
    tenantId?: string | null;
    roles?: string[];
    accountType?: string | null;
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
    accountType?: string | null;
  }
}
