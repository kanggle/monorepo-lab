/**
 * Client-side access token holder shared between NextAuth's `useSession()`
 * and the synchronous `getAccessToken` callback used by `@repo/api-client`.
 *
 * See `apps/web-store/src/shared/auth/token-bridge.ts` for the rationale —
 * admin-dashboard uses the same pattern.
 */

let currentAccessToken: string | null = null;

export function setAccessToken(token: string | null | undefined): void {
  currentAccessToken = token ?? null;
}

export function getAccessToken(): string | null {
  return currentAccessToken;
}

export function clearAccessToken(): void {
  currentAccessToken = null;
}
