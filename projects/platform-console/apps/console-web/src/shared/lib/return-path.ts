/**
 * Same-site return-path sanitiser — the SINGLE predicate for the post-login
 * `redirect` target, shared by the login page (`(auth)/login/page.tsx`) and
 * the login route handler (`api/auth/login/route.ts`).
 *
 * TASK-PC-FE-253 collapsed a divergence where the page checked only
 * `startsWith('/')` — admitting the protocol-relative `//evil.com` — while the
 * route additionally rejected `//`. Two copies of "is this redirect safe?"
 * with different predicates is the defect: the day the stronger side stops
 * re-blocking, the weaker one silently opens a redirect. One function, one
 * predicate, both call sites.
 *
 * Accepts only a same-site absolute path (a single leading `/`). Anything
 * else collapses to `/`:
 *   - absolute URL (`https://evil.com`, `http:/…`) — no leading `/`;
 *   - protocol-relative authority `//evil.com` and its backslash-normalised
 *     twin `/\evil.com` (browsers fold `\` → `/`, so `/\evil.com` reaches the
 *     network as `//evil.com`);
 *   - empty / missing value.
 *
 * This is the CONSUME side (the attacker-controllable `?redirect=` query
 * param). The layout guard `buildLoginRedirect()` in `(console)/layout.tsx` is
 * the PRODUCE side — it derives the param from the trusted `x-pathname` header
 * and layers on extra destination rules (`/login`, `/api/`), so it stays a
 * deliberately separate, stricter predicate rather than a call site here.
 */
export function sanitizeReturnPath(raw: string | null | undefined): string {
  if (!raw || !raw.startsWith('/')) return '/';
  // Protocol-relative forms a browser normalises to an off-site origin.
  if (raw.startsWith('//') || raw.startsWith('/\\')) return '/';
  return raw;
}
