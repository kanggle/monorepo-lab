/**
 * `features/auth` public API — the IAM OIDC session surface.
 *
 * TASK-PC-FE-259 — `performLogout` moved to `shared/lib/logout.ts`. It is
 * consumed by `shared/ui/AccountMenu` (the top-bar account menu item), and a
 * `shared/` module importing a feature is the INVERSE of the same rule this
 * task enforces: `architecture.md` § Allowed Dependencies says `shared/` may
 * import "자체만 (features / app 금지)". The RP-initiated logout is a
 * session-level concern with no feature coupling (one `fetch` + one
 * navigation), so `shared/lib/` is its correct home. Re-exported here so the
 * documented `features/auth` surface is preserved.
 */
export { performLogout } from '@/shared/lib/logout';
