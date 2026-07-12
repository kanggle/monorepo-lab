import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

/**
 * TASK-MONO-358 — the cookie `Secure` flag is opt-OUT, and only the exact
 * string "false" opts out.
 *
 * Why this is worth a test: `Secure` became configurable so the plain-HTTP
 * portfolio demo can log in at all (a browser refuses to STORE a Secure cookie
 * delivered over http:// on any origin but localhost, so the PKCE/state cookies
 * vanished and every callback bounced with `invalid_state`). That is a
 * legitimate need — but it puts a security-relevant flag under environment
 * control, and the failure mode of getting it wrong is silent: sessions would
 * ship without `Secure` in production and nothing would break visibly.
 *
 * So the contract is pinned from both sides: unset/typo/empty/"FALSE"/"0" all
 * leave Secure ON; only the literal "false" turns it off.
 *
 * `session.ts` reads `process.env` once at module load, so every case needs a
 * fresh module registry — `vi.resetModules()` before each import.
 */
describe('cookie Secure flag (CONSOLE_COOKIE_SECURE)', () => {
  const original = process.env.CONSOLE_COOKIE_SECURE;

  beforeEach(() => {
    vi.resetModules();
  });

  afterEach(() => {
    if (original === undefined) delete process.env.CONSOLE_COOKIE_SECURE;
    else process.env.CONSOLE_COOKIE_SECURE = original;
  });

  async function secureFlag(): Promise<boolean> {
    const mod = await import('@/shared/lib/session');
    return mod.tokenCookieOpts.secure;
  }

  it('defaults to Secure when the variable is unset', async () => {
    delete process.env.CONSOLE_COOKIE_SECURE;
    expect(await secureFlag()).toBe(true);
  });

  it('turns Secure off ONLY for the exact string "false"', async () => {
    process.env.CONSOLE_COOKIE_SECURE = 'false';
    expect(await secureFlag()).toBe(false);
  });

  // Anything that merely *looks* like a disable must NOT disable — a typo in a
  // deployment env must fail safe, not silently strip Secure from production
  // session cookies.
  it.each(['', 'FALSE', 'False', '0', 'no', 'off', 'true', ' false'])(
    'keeps Secure ON for %o',
    async (value) => {
      process.env.CONSOLE_COOKIE_SECURE = value;
      expect(await secureFlag()).toBe(true);
    },
  );

  it('applies to the transient PKCE/state cookies too (they inherit)', async () => {
    process.env.CONSOLE_COOKIE_SECURE = 'false';
    const mod = await import('@/shared/lib/session');
    // These are the cookies whose loss caused `invalid_state` — if they ever
    // stop inheriting the flag, the demo login silently breaks again.
    expect(mod.transientCookieOpts.secure).toBe(false);
    expect(mod.transientCookieOpts.httpOnly).toBe(true);
    expect(mod.transientCookieOpts.sameSite).toBe('lax');
  });
});

/**
 * `publicOrigin()` — the runtime-resolvable browser origin.
 *
 * `NEXT_PUBLIC_APP_URL` is inlined by Next at BUILD time, so a prebuilt image
 * is permanently pinned to the host the build knew about. `CONSOLE_PUBLIC_ORIGIN`
 * carries no `NEXT_PUBLIC_` prefix and is therefore read at runtime. The
 * fallback keeps every existing deployment byte-identical.
 */
describe('publicOrigin()', () => {
  it('prefers the runtime origin when set', async () => {
    const { publicOrigin } = await import('@/shared/config/env');
    expect(
      publicOrigin({
        CONSOLE_PUBLIC_ORIGIN: 'http://console.1-2-3-4.sslip.io',
        NEXT_PUBLIC_APP_URL: 'http://console.local',
      } as never),
    ).toBe('http://console.1-2-3-4.sslip.io');
  });

  it('falls back to the build-time value when unset', async () => {
    const { publicOrigin } = await import('@/shared/config/env');
    expect(
      publicOrigin({
        CONSOLE_PUBLIC_ORIGIN: undefined,
        NEXT_PUBLIC_APP_URL: 'http://console.local',
      } as never),
    ).toBe('http://console.local');
  });
});
