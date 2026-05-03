/**
 * Client-side access token holder shared between NextAuth's `useSession()`
 * (the source of truth) and `@repo/api-client`'s synchronous `getAccessToken`
 * callback.
 *
 * Why a module-level holder?
 *   - `@repo/api-client` (axios + interceptor) was designed before NextAuth
 *     was introduced. Its `getAccessToken: () => string | null` callback is
 *     synchronous; NextAuth exposes the access token via the async
 *     `useSession()` hook.
 *   - The `AuthProvider` (see `features/auth/model/auth-context.tsx`)
 *     subscribes to `useSession()` and pushes the latest token into this
 *     holder via `setAccessToken()`. The interceptor reads it synchronously
 *     on every outbound request.
 *   - On sign-out the holder is cleared so subsequent requests omit the
 *     `Authorization` header (matching the pre-cutover behavior).
 *
 * The token here is the OIDC access token issued by GAP — NOT NextAuth's
 * session JWT. Backend (gateway-service) validates GAP-issued JWTs against
 * the GAP issuer URI per TASK-MONO-027.
 *
 * SSR safety: this module is loaded in both server and client bundles, but
 * the holder is only ever written from the client (via AuthProvider) and only
 * read from the client (via the axios interceptor mounted in
 * `shared/config/api.ts`). Server-side fetches use `getWebStoreSession()`
 * from `./session.ts` instead.
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
