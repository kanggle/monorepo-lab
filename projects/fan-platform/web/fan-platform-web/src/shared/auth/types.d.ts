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
    /** TASK-FAN-FE-027 — lost-refresh-race signal for `session-route.ts`; a boolean only. */
    refreshRaceLost?: boolean;
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
    /** Set when a silent refresh failed (F1); the session degrades to anonymous. */
    error?: 'RefreshAccessTokenError';
    /**
     * TASK-FAN-FE-027 — set (alongside `error`) only on the `jwt` call whose
     * refresh got IAM's rotation-shaped `400 invalid_grant`, i.e. possibly the
     * loser of a concurrent refresh. Cleared at the start of every later call.
     * Consumed by `session-route.ts`.
     */
    refreshRaceLost?: boolean;
  }
}
