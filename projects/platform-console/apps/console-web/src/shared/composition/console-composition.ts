import { SpanStatusCode, trace } from '@opentelemetry/api';
import { getServerEnv } from '@/shared/config/env';
import { logger } from '@/shared/lib/logger';

/**
 * Cross-domain composition in the console-web server (ADR-MONO-081, A —
 * `console-integration-contract.md` § 2.4.9). Replaces the former BFF
 * (retired — ADR-MONO-081) as the producer of `operator-overview`
 * (§ 2.4.9.1) and `domain-health` (§ 2.4.9.2) without changing a byte of
 * their wire envelope.
 *
 * This module holds no network primitive. The route handler passes its own
 * `fetch` in, AFTER its `sampleGate(` — a sample visitor never reaches a leg
 * (ADR-MONO-074 A2; `tests/unit/sample-fetch-allowlist.test.ts`) — and that
 * `fetch` runs each leg URL through `resolveBackendUrl` at the call site, so
 * the demo host is resolved where the request is made
 * (`scripts/check-fetch-resolution.mjs`). Leg URLs here are the CONFIGURED ones.
 *
 * Rules carried over from the former BFF, not re-decided (ADR-MONO-081 D2):
 * - one leg failing (timeout / 5xx / network / unreadable body) degrades
 *   THAT card only — the composition still answers 200;
 * - a DATA leg answering 401 collapses the whole composition to 401 — an
 *   expired session must not hide as a partial outage;
 * - a DATA leg answering 403 is `forbidden` (TENANT_FORBIDDEN when the body
 *   says so, else PERMISSION_DENIED);
 * - a HEALTH leg sends no credential, so any non-success — 401/403 included —
 *   is a producer misconfiguration and degrades that card.
 *
 * Deliberately NOT carried over (ADR-MONO-081 R1/R2): the circuit-breaker
 * (a serverless function keeps no state between requests, so `CIRCUIT_OPEN`
 * is never produced here) and the `bff_*` metrics (one structured log line
 * per leg instead).
 *
 * Carried over: the per-leg trace span (contract § 2.4.9 Observability — «per-leg
 * span carries domain + route attributes»). Each leg runs inside an active span
 * `console.composition.leg` tagged `composition.domain` / `composition.route`, so
 * the auto-instrumented `fetch` client span and the producer's server span join
 * it under the request's trace (`tests/federation-hardening-e2e` gates this).
 * With no OTel SDK registered (unit tests, Vercel without an exporter) the API
 * is a no-op.
 */

export type LegDomain = 'iam' | 'wms' | 'scm' | 'finance' | 'erp' | 'ecommerce';

/** Fixed card order — § 2.4.9.1 / § 2.4.9.2 «exactly 6 entries in fixed order». */
export const CARD_ORDER: readonly LegDomain[] = [
  'iam',
  'wms',
  'scm',
  'finance',
  'erp',
  'ecommerce',
];

/**
 * Per-leg timeout. Legs run in parallel, so this also bounds the composition.
 *
 * Sized against the Vercel function limit (TASK-PC-FE-302 AC-6): the
 * documented default is 300 s on every plan with fluid compute (Vercel docs
 * «Maximum Duration», fetched 2026-10-02, page last updated 2026-08-24). The
 * project's own dashboard override could not be read (no Vercel CLI / API
 * access from this repo), so the value is chosen to hold under the most
 * conservative limit Vercel has shipped (10 s) as well — 4 s leaves room for
 * the session reads and response before either ceiling. It replaces
 * the former BFF's 2 s leg × bounded retry inside a 5 s composition budget.
 */
export const LEG_TIMEOUT_MS = 4000;

export type CardStatus = 'ok' | 'degraded' | 'forbidden';

export interface Card {
  domain: LegDomain;
  status: CardStatus;
  data?: unknown;
  reason?: string;
}

export interface CompositionEnvelope {
  asOf: string;
  cards: Card[];
}

export type FetchLeg = (url: string, init: RequestInit) => Promise<Response>;

/** A leg is either decided before any call (`decided`) or fetched. */
export type LegSpec =
  | { domain: LegDomain; decided: Card }
  | {
      domain: LegDomain;
      kind: 'data' | 'health';
      url: string;
      headers: Record<string, string>;
    };

export type CompositionRoute = 'operator-overview' | 'domain-health';

export type CompositionResult =
  | { unauthorized: true; domain: LegDomain }
  | { unauthorized: false; envelope: CompositionEnvelope };

type LegResult =
  | { unauthorized: false; card: Card }
  | { unauthorized: true; domain: LegDomain };

interface LegContext {
  route: CompositionRoute;
  requestId: string;
  timeoutMs: number;
  fetchLeg: FetchLeg;
}

function card(domain: LegDomain, status: CardStatus, reason?: string, data?: unknown): Card {
  const c: Card = { domain, status };
  if (data !== undefined) c.data = data;
  if (reason !== undefined) c.reason = reason;
  return c;
}

function logLeg(
  ctx: LegContext,
  domain: LegDomain,
  status: CardStatus | 'unauthorized',
  startedAt: number,
  reason?: string,
): void {
  // R1 — one line per leg. No token, no account id, no body.
  const fields: Record<string, unknown> = {
    route: ctx.route,
    domain,
    status,
    latencyMs: Date.now() - startedAt,
    requestId: ctx.requestId,
  };
  if (reason) fields.reason = reason;
  if (status === 'ok') logger.info('console_composition_leg', fields);
  else logger.warn('console_composition_leg', fields);
}

const HEALTH_STATUSES = new Set(['UP', 'DOWN', 'OUT_OF_SERVICE', 'UNKNOWN']);

function isHealthDocument(body: unknown): boolean {
  if (!body || typeof body !== 'object') return false;
  const status = (body as { status?: unknown }).status;
  return typeof status === 'string' && HEALTH_STATUSES.has(status);
}

async function forbiddenReason(res: Response): Promise<string> {
  try {
    const text = await res.text();
    if (text.includes('TENANT_FORBIDDEN')) return 'TENANT_FORBIDDEN';
  } catch {
    /* body absent / unreadable */
  }
  return 'PERMISSION_DENIED';
}

const tracer = trace.getTracer('console-web.composition');

function runLeg(spec: LegSpec, ctx: LegContext): Promise<LegResult> {
  return tracer.startActiveSpan(
    'console.composition.leg',
    { attributes: { 'composition.domain': spec.domain, 'composition.route': ctx.route } },
    async (span) => {
      try {
        const r = await runLegInner(spec, ctx);
        span.setAttribute(
          'composition.outcome',
          r.unauthorized ? 'unauthorized' : r.card.status,
        );
        return r;
      } catch (e) {
        span.setStatus({ code: SpanStatusCode.ERROR });
        throw e;
      } finally {
        span.end();
      }
    },
  );
}

async function runLegInner(spec: LegSpec, ctx: LegContext): Promise<LegResult> {
  const startedAt = Date.now();
  if ('decided' in spec) {
    logLeg(ctx, spec.domain, spec.decided.status, startedAt, spec.decided.reason);
    return { unauthorized: false, card: spec.decided };
  }
  const { domain } = spec;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), ctx.timeoutMs);
  const degraded = (reason: 'TIMEOUT' | 'DOWNSTREAM_ERROR'): LegResult => {
    logLeg(ctx, domain, 'degraded', startedAt, reason);
    return { unauthorized: false, card: card(domain, 'degraded', reason) };
  };
  try {
    let res: Response;
    try {
      res = await ctx.fetchLeg(spec.url, {
        method: 'GET',
        headers: spec.headers,
        cache: 'no-store',
        signal: controller.signal,
      });
    } catch {
      return degraded(controller.signal.aborted ? 'TIMEOUT' : 'DOWNSTREAM_ERROR');
    }

    if (res.ok) {
      try {
        // `data` is the producer's body VERBATIM (§ 2.4.9.1 «ok card data shapes»).
        const data: unknown = await res.json();
        logLeg(ctx, domain, 'ok', startedAt);
        return { unauthorized: false, card: card(domain, 'ok', undefined, data) };
      } catch {
        return degraded(controller.signal.aborted ? 'TIMEOUT' : 'DOWNSTREAM_ERROR');
      }
    }

    if (spec.kind === 'health' && res.status === 503) {
      // Spring Boot answers 503 when the aggregate status is DOWN /
      // OUT_OF_SERVICE. That is a SUCCESSFUL health document — the producer is
      // honestly reporting itself down — and § 2.4.9.2 renders it as an `ok`
      // card with `data.status`, distinct from `degraded` (could not reach it).
      // 🔴 The former BFF (retired — ADR-MONO-081) got this wrong: RestClient threw on 503 and the card
      // went `degraded`. Only a real health body qualifies; a gateway error
      // envelope on 503 still degrades.
      try {
        const body: unknown = await res.json();
        if (isHealthDocument(body)) {
          logLeg(ctx, domain, 'ok', startedAt);
          return { unauthorized: false, card: card(domain, 'ok', undefined, body) };
        }
      } catch {
        /* not a health document */
      }
      return degraded(controller.signal.aborted ? 'TIMEOUT' : 'DOWNSTREAM_ERROR');
    }
    if (spec.kind === 'data' && res.status === 401) {
      logLeg(ctx, domain, 'unauthorized', startedAt);
      return { unauthorized: true, domain };
    }
    if (spec.kind === 'data' && res.status === 403) {
      const reason = await forbiddenReason(res);
      logLeg(ctx, domain, 'forbidden', startedAt, reason);
      return { unauthorized: false, card: card(domain, 'forbidden', reason) };
    }
    return degraded('DOWNSTREAM_ERROR');
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Runs every leg in parallel and builds the envelope. Never throws for a leg
 * failure; a leg that rejected unexpectedly degrades its own card.
 */
export async function compose(
  specs: readonly LegSpec[],
  opts: {
    route: CompositionRoute;
    requestId: string;
    fetchLeg: FetchLeg;
    timeoutMs?: number;
    now?: () => Date;
  },
): Promise<CompositionResult> {
  const ctx: LegContext = {
    route: opts.route,
    requestId: opts.requestId,
    timeoutMs: opts.timeoutMs ?? LEG_TIMEOUT_MS,
    fetchLeg: opts.fetchLeg,
  };
  const asOf = (opts.now ?? (() => new Date()))().toISOString();
  const settled = await Promise.allSettled(specs.map((s) => runLeg(s, ctx)));

  const byDomain = new Map<LegDomain, Card>();
  for (let i = 0; i < settled.length; i++) {
    const s = settled[i];
    const domain = specs[i].domain;
    if (s.status === 'rejected') {
      byDomain.set(domain, card(domain, 'degraded', 'DOWNSTREAM_ERROR'));
      continue;
    }
    if (s.value.unauthorized) return { unauthorized: true, domain: s.value.domain };
    byDomain.set(domain, s.value.card);
  }

  const cards = CARD_ORDER.filter((d) => byDomain.has(d)).map((d) => byDomain.get(d)!);
  if (cards.length > 0 && cards.every((c) => c.status !== 'ok')) {
    // Still 200 — the dashboard is never blanked (ADR-MONO-017 D5.A).
    logger.warn('console_composition_all_down', {
      route: opts.route,
      requestId: opts.requestId,
    });
  }
  return { unauthorized: false, envelope: { asOf, cards } };
}

// ---------------------------------------------------------------------------
// Leg addresses — the same base URL + path each domain's own console screen
// already uses (§ 2.4.9 «Which address each leg uses»). The route's
// `fetchLeg` resolves them through the demo backend resolver exactly like
// those clients do. 🔴 Never the docker-only
// direct-to-service paths the former BFF used: they are unreachable from Vercel.
// ---------------------------------------------------------------------------

function originOf(base: string): string {
  return new URL(base).origin;
}

export interface OverviewCredentials {
  tenant: string;
  /** RFC 8693 exchanged operator token — IAM leg only (ADR-MONO-017 D4). */
  operatorToken: string;
  /** Domain-facing IAM OIDC token — every non-IAM data leg. */
  domainFacingToken: string;
  /** Finance card prerequisite; blank ⇒ `forbidden / MISSING_PREREQUISITE`. */
  financeDefaultAccountId: string | null;
  requestId: string;
}

export async function operatorOverviewLegs(c: OverviewCredentials): Promise<LegSpec[]> {
  const env = getServerEnv();
  const domainHeaders = {
    Accept: 'application/json',
    Authorization: `Bearer ${c.domainFacingToken}`,
    'X-Request-Id': c.requestId,
    // No X-Tenant-Id: each domain resolves the tenant from the token claim,
    // exactly as the domain's own console client does.
  };
  const data = async (
    domain: LegDomain,
    url: string,
    headers: Record<string, string>,
  ): Promise<LegSpec> => ({
    domain,
    kind: 'data',
    url,
    headers,
  });

  const accountId = c.financeDefaultAccountId?.trim() ?? '';
  const finance: LegSpec = accountId
    ? await data(
        'finance',
        `${env.FINANCE_BASE_URL}/api/finance/accounts/${encodeURIComponent(accountId)}/balances`,
        domainHeaders,
      )
    : { domain: 'finance', decided: card('finance', 'forbidden', 'MISSING_PREREQUISITE') };

  return [
    // TASK-PC-FE-304: `tenantId` MUST ride as a query param, not only the
    // `X-Tenant-Id` header — `AccountAdminController#search` (iam-platform
    // admin-service) reads the tenant EXCLUSIVELY from the `tenantId` query
    // param (`QueryTenantScopeGate` falls back to the operator's HOME tenant
    // when it is absent). Without this, switching the active tenant moved
    // every other card but left the IAM card pinned to the operator's home
    // tenant — the accounts screen (`searchAccounts`, TASK-BE-357) already
    // appends `tenantId` for the same reason; this leg now matches it.
    await data(
      'iam',
      `${env.IAM_ADMIN_API_BASE}/api/admin/accounts?page=0&size=1&tenantId=${encodeURIComponent(c.tenant)}`,
      {
        Accept: 'application/json',
        Authorization: `Bearer ${c.operatorToken}`,
        'X-Tenant-Id': c.tenant,
        'X-Request-Id': c.requestId,
      },
    ),
    await data('wms', `${env.WMS_ADMIN_BASE_URL}/dashboard/inventory`, domainHeaders),
    await data(
      'scm',
      `${env.SCM_GATEWAY_BASE_URL}/api/v1/inventory-visibility/snapshot`,
      domainHeaders,
    ),
    finance,
    await data(
      'erp',
      `${env.ERP_BASE_URL}/api/erp/masterdata/departments?active=true&page=0&size=1`,
      domainHeaders,
    ),
    await data('ecommerce', `${env.ECOMMERCE_ADMIN_BASE_URL}/products?page=0&size=1`, domainHeaders),
  ];
}

/** Health legs: public `/actuator/health` on each domain's edge — no credential (§ 2.4.9.2). */
export async function domainHealthLegs(requestId: string): Promise<LegSpec[]> {
  const env = getServerEnv();
  const bases: Record<LegDomain, string> = {
    iam: env.IAM_ADMIN_API_BASE,
    wms: env.WMS_ADMIN_BASE_URL,
    scm: env.SCM_GATEWAY_BASE_URL,
    finance: env.FINANCE_BASE_URL,
    erp: env.ERP_BASE_URL,
    ecommerce: env.ECOMMERCE_ADMIN_BASE_URL,
  };
  return Promise.all(
    CARD_ORDER.map(async (domain) => ({
      domain,
      kind: 'health' as const,
      url: `${originOf(bases[domain])}/actuator/health`,
      headers: { Accept: 'application/json', 'X-Request-Id': requestId },
    })),
  );
}
