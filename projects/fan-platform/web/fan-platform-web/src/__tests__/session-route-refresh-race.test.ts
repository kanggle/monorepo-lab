// @vitest-environment node
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

// session-route.ts reaches next-auth/jwt through session-token.ts; every test
// here injects its own decoder, so the real one is never needed.
vi.mock('next-auth/jwt', () => ({ getToken: vi.fn() }));

import { jwtCallback, sessionCallback } from '@/shared/auth/auth-callbacks';
import {
  withRefreshRaceRetry,
  REFRESH_RACE_GRACE_MS,
  SESSION_RETRY_PARAM,
} from '@/shared/auth/session-route';

/**
 * TASK-FAN-FE-027 — two requests carrying the SAME expired session cookie
 * refresh concurrently (parallel session reads / tabs / serverless instances).
 * Copy of the web-store harness (TASK-FE-106 AC-4), on fan's real callbacks.
 *
 * The world here is deliberately small but keeps the properties the defect
 * depends on:
 *   - IAM (fetch fake) rotates on the first use of a refresh token and answers
 *     any later use of it `400 invalid_grant` — the iam TASK-BE-606 grace-window
 *     refusal ("refused, nothing revoked");
 *   - the Auth.js session action (handler fake) ALWAYS re-writes the session
 *     cookie with whatever the real `jwtCallback` returned;
 *   - the browser applies Set-Cookie in the order responses ARRIVE.
 */

const ORIGIN = 'https://fan.example';
const COOKIE = 'authjs.session-token';
type Token = Record<string, unknown>;

const enc = (t: Token) => Buffer.from(JSON.stringify(t)).toString('base64url');
const parseCookies = (header: string) =>
  Object.fromEntries(
    header
      .split(';')
      .map((p) => p.trim())
      .filter(Boolean)
      .map((p) => [p.slice(0, p.indexOf('=')), p.slice(p.indexOf('=') + 1)]),
  ) as Record<string, string>;
const decode = async (header: string): Promise<Token | null> => {
  const v = parseCookies(header)[COOKIE];
  return v ? (JSON.parse(Buffer.from(v, 'base64url').toString()) as Token) : null;
};

/**
 * Fake of the Auth.js `session` action (`@auth/core/lib/actions/session.js`):
 * decode → real jwt callback → real session callback (with the same default
 * `user` object core builds) → re-write cookie.
 */
async function authSessionAction(req: Request): Promise<Response> {
  const token = await decode(req.headers.get('cookie') ?? '');
  if (!token) return new Response('null', { headers: { 'content-type': 'application/json' } });
  const next = await jwtCallback({ token: { ...token }, account: null });
  const session = sessionCallback({
    session: { user: { name: next.name, email: next.email, image: next.picture } },
    token: next,
  });
  return new Response(JSON.stringify(session), {
    headers: {
      'content-type': 'application/json',
      'set-cookie': `${COOKIE}=${enc(next)}; Path=/; HttpOnly; SameSite=Lax`,
    },
  });
}

/**
 * Fake of the request-less `auth()` (RSC / middleware form, `next-auth/lib/index.js`
 * `getSession(h, config).then((r) => r.json())`): the session action runs, its
 * body is returned, its Set-Cookie is DROPPED.
 */
async function requestlessAuth(b: Browser): Promise<Record<string, unknown> | null> {
  const res = await authSessionAction(b.request('/api/auth/session'));
  return res.json() as Promise<Record<string, unknown> | null>;
}

class Browser {
  jar = new Map<string, string>();
  cookieHeader() {
    return [...this.jar].map(([k, v]) => `${k}=${v}`).join('; ');
  }
  request(path: string) {
    return new Request(new URL(path, ORIGIN), { headers: { cookie: this.cookieHeader() } });
  }
  apply(res: Response) {
    for (const sc of res.headers.getSetCookie()) {
      const [pair, ...attrs] = sc.split(';').map((s) => s.trim());
      const name = pair.slice(0, pair.indexOf('='));
      if (attrs.some((a) => /^max-age=0$/i.test(a))) this.jar.delete(name);
      else this.jar.set(name, pair.slice(pair.indexOf('=') + 1));
    }
  }
  token() {
    return decode(this.cookieHeader());
  }
}

/** IAM `/oauth2/token` fake with BE-606 grace semantics. */
function iam(opts: { dead?: boolean } = {}) {
  const used = new Set<string>();
  let n = 0;
  return vi.fn(async (_url: string, init: { body: URLSearchParams }) => {
    const rt = new URLSearchParams(String(init.body)).get('refresh_token') ?? '';
    if (opts.dead || used.has(rt)) {
      return { ok: false, status: 400, json: async () => ({ error: 'invalid_grant' }) };
    }
    used.add(rt);
    n += 1;
    return {
      ok: true,
      status: 200,
      json: async () => ({ access_token: `at-${n}`, refresh_token: `rt-${n}`, expires_in: 1800 }),
    };
  });
}

const nowSec = () => Math.floor(Date.now() / 1000);
function loggedInIdleBrowser() {
  const b = new Browser();
  b.jar.set(
    COOKIE,
    enc({
      name: '팬',
      email: 'fan@example.com',
      accountId: 'acc-1',
      tenantId: 'fan-platform',
      roles: ['FAN'],
      accessToken: 'at-0',
      refreshToken: 'rt-0',
      expiresAt: nowSec() - 5 * 60, // idle: access token expired, refresh token alive
    }),
  );
  return b;
}

describe('TASK-FAN-FE-027 — concurrent refresh of the same expired session JWT', () => {
  let fetchMock: ReturnType<typeof iam>;
  afterEach(() => vi.unstubAllGlobals());

  describe('🔵 control — the defect shapes the harness must be able to show', () => {
    it('bare Auth.js session action: the loser lands its RefreshAccessTokenError cookie over the winner → logged out', async () => {
      fetchMock = iam();
      vi.stubGlobal('fetch', fetchMock);
      const b = loggedInIdleBrowser();
      const [reqA, reqB] = [b.request('/api/auth/session'), b.request('/api/auth/session')];
      const resA = await authSessionAction(reqA); // winner: rotates rt-0 → rt-1
      const resB = await authSessionAction(reqB); // loser: rt-0 again → 400 invalid_grant
      b.apply(resA);
      b.apply(resB); // arrives last

      expect(fetchMock).toHaveBeenCalledTimes(2);
      expect((await b.token())?.error).toBe('RefreshAccessTokenError');
    });

    it('request-less auth() (the old middleware / getFanSession / isAuthenticated read): rotates at IAM, drops the cookie, and the next read replays rt-0', async () => {
      fetchMock = iam();
      vi.stubGlobal('fetch', fetchMock);
      const b = loggedInIdleBrowser();

      const first = await requestlessAuth(b); // e.g. the middleware gate
      expect(first?.accountId).toBe('acc-1'); // looks fine…
      expect((await b.token())?.refreshToken).toBe('rt-0'); // …but the rotated pair was thrown away

      const second = await requestlessAuth(b); // e.g. the header's isAuthenticated()
      const sent = fetchMock.mock.calls.map(([, init]) =>
        new URLSearchParams(String(init.body)).get('refresh_token'),
      );
      expect(sent).toEqual(['rt-0', 'rt-0']); // the replay IAM calls reuse after 30s
      expect(second?.accountId).toBeNull(); // within 30s: grace refusal → anonymous
    });
  });

  describe('withRefreshRaceRetry', () => {
    beforeEach(() => {
      fetchMock = iam();
      vi.stubGlobal('fetch', fetchMock);
    });

    it('🔴 the loser converges on the winner’s rotated tokens — session kept, nothing replayed', async () => {
      const b = loggedInIdleBrowser();
      const [reqA, reqB] = [b.request('/api/auth/session'), b.request('/api/auth/session')];

      const winner: { res?: Response } = {};
      const sleep = vi.fn(async (ms: number) => {
        expect(ms).toBe(REFRESH_RACE_GRACE_MS);
        b.apply(winner.res!); // the winner's Set-Cookie lands while the loser waits
      });
      const GET = withRefreshRaceRetry({ handler: authSessionAction, decode, sleep });

      winner.res = await GET(reqA);
      expect(winner.res.status).toBe(200);
      const firstB = await GET(reqB);

      // The loser's first answer carries NO cookie — it cannot overwrite the winner.
      expect(firstB.status).toBe(307);
      expect(firstB.headers.getSetCookie()).toEqual([]);
      expect(firstB.headers.get('location')).toBe(`/api/auth/session?${SESSION_RETRY_PARAM}=1`);
      expect(sleep).toHaveBeenCalledOnce();

      const sessionB = await follow(b, GET, firstB);
      expect(sessionB?.accountId).toBe('acc-1');
      const final = await b.token();
      expect(final?.refreshToken).toBe('rt-1');
      expect(final?.accessToken).toBe('at-1');
      expect(final?.error).toBeUndefined();
      // Exactly the two concurrent attempts — the retry hop never refreshes.
      expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    it('the winner is untouched — a single fresh refresh is answered normally', async () => {
      const b = loggedInIdleBrowser();
      const GET = withRefreshRaceRetry({ handler: authSessionAction, decode, sleep: vi.fn() });
      const res = await GET(b.request('/api/auth/session'));
      expect(res.status).toBe(200);
      b.apply(res);
      expect((await res.json())?.accountId).toBe('acc-1');
      expect((await b.token())?.refreshToken).toBe('rt-1');
    });

    it('🔴 winner’s cookie not landed in time → logged out WITHOUT re-sending rt-0', async () => {
      const b = loggedInIdleBrowser();
      const GET = withRefreshRaceRetry({ handler: authSessionAction, decode, sleep: vi.fn() });
      const reqA = b.request('/api/auth/session');
      const reqB = b.request('/api/auth/session');
      await GET(reqA); // winner's response never reaches the jar before the retry
      const session = await follow(b, GET, await GET(reqB));

      expect(session).toBeNull();
      expect(b.jar.has(COOKIE)).toBe(false);
      expect(fetchMock).toHaveBeenCalledTimes(2); // rt-0 sent once by each, never a third time
    });
  });

  describe('genuine failures still degrade to logged out', () => {
    it('dead refresh token (400 invalid_grant, no winner) → one retry hop → cleared, anonymous, one IAM call', async () => {
      fetchMock = iam({ dead: true });
      vi.stubGlobal('fetch', fetchMock);
      const b = loggedInIdleBrowser();
      const GET = withRefreshRaceRetry({ handler: authSessionAction, decode, sleep: vi.fn() });

      const first = await GET(b.request('/api/auth/session'));
      expect(first.status).toBe(307);
      const session = await follow(b, GET, first);

      expect(session).toBeNull();
      expect(b.jar.has(COOKIE)).toBe(false);
      expect(fetchMock).toHaveBeenCalledOnce();
    });

    it('non-race failure (IAM 503) → no retry hop, error cookie written as before', async () => {
      fetchMock = vi.fn(async () => ({ ok: false, status: 503, json: async () => ({}) })) as never;
      vi.stubGlobal('fetch', fetchMock);
      const b = loggedInIdleBrowser();
      const sleep = vi.fn();
      const GET = withRefreshRaceRetry({ handler: authSessionAction, decode, sleep });

      const res = await GET(b.request('/api/auth/session'));
      expect(res.status).toBe(200);
      expect(sleep).not.toHaveBeenCalled();
      b.apply(res);
      expect((await res.json())?.accountId).toBeNull();
      expect((await b.token())?.error).toBe('RefreshAccessTokenError');
    });
  });

  describe('routing', () => {
    it('non-session Auth.js actions pass straight through', async () => {
      const handler = vi.fn(async () => new Response('{"csrfToken":"x"}'));
      const GET = withRefreshRaceRetry({ handler, decode, sleep: vi.fn() });
      const res = await GET(new Request(`${ORIGIN}/api/auth/csrf`));
      expect(handler).toHaveBeenCalledOnce();
      expect(await res.text()).toBe('{"csrfToken":"x"}');
    });

    it('retry hop with no session cookie → the normal (anonymous) answer', async () => {
      const handler = vi.fn(
        async () => new Response('null', { headers: { 'content-type': 'application/json' } }),
      );
      const GET = withRefreshRaceRetry({ handler, decode, sleep: vi.fn() });
      const res = await GET(new Request(`${ORIGIN}/api/auth/session?${SESSION_RETRY_PARAM}=1`));
      expect(handler).toHaveBeenCalledOnce();
      expect(await res.json()).toBeNull();
    });
  });
});

/** What `SessionKeeper`'s `fetch` does: follow redirects transparently, apply cookies. */
async function follow(b: Browser, GET: (r: Request) => Promise<Response>, first: Response) {
  let res = first;
  for (let hops = 0; res.status === 307 && hops < 3; hops += 1) {
    res = await GET(b.request(res.headers.get('location')!));
  }
  b.apply(res);
  return res.json() as Promise<Record<string, unknown> | null>;
}
