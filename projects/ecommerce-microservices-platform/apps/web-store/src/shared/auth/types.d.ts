/**
 * Module augmentation for next-auth v5 — extends the default Session and JWT.
 *
 * Phase 4.5 F2 (token confidentiality): the public `Session` carries ONLY
 * non-sensitive identity claims (`account_id` / `tenant_id` / `roles` + the
 * default display `email`·`name`). The access / refresh / id tokens live ONLY
 * on the server-side `JWT` (encrypted HttpOnly cookie) and are NEVER added to
 * `Session` — that object is serialized to client JS via `/api/auth/session`.
 * Server code reads the bearer through the server-only `getWebStoreSession()`
 * helper; client code reaches the backend only via the same-origin BFF proxy.
 */
import 'next-auth';
import 'next-auth/jwt';

declare module 'next-auth' {
  interface Session {
    accountId?: string | null;
    tenantId?: string | null;
    roles?: string[];
    // NOTE: no `accessToken` here — tokens are server-only (F2).
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
    /**
     * GAP OIDC `id_token` — kept server-side on the JWT ONLY to serve as the
     * `id_token_hint` for RP-initiated logout (GAP `end_session`). Never copied
     * onto the public Session (would leak via /api/auth/session).
     */
    idToken?: string;
    /**
     * Set to `'RefreshAccessTokenError'` by the `jwt` callback when a silent
     * refresh (Phase 4.5 F3) fails. The `session` callback then degrades the
     * session to anonymous so the next protected request forces a full re-auth
     * (F1). Cleared on a fresh sign-in.
     */
    error?: 'RefreshAccessTokenError';
  }
}
