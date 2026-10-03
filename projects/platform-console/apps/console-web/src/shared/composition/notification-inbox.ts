import { SpanStatusCode, trace } from '@opentelemetry/api';
import { getServerEnv } from '@/shared/config/env';
import { logger } from '@/shared/lib/logger';
import { LEG_TIMEOUT_MS, type FetchLeg } from './console-composition';

/**
 * Notification aggregator in the console-web server (ADR-MONO-081, A —
 * `platform/contracts/notification-inbox-contract.md` § 4; TASK-PC-FE-303).
 * Replaces the former BFF's `NotificationAggregationUseCase` (retired —
 * ADR-MONO-081) without changing the
 * inbox wire shape: `{ asOf, items, meta: { page, size, totalElements },
 * degradedDomains }`.
 *
 * Like `console-composition.ts`, this module holds no network primitive: the
 * route passes its own `fetch` in, after its `sampleGate(`, and resolves the
 * demo host at that call site.
 *
 * Rules (contract § 4):
 * - one domain failing (timeout / 5xx / network / 403 / unreadable body) puts
 *   THAT domain in `degradedDomains`; the inbox still answers 200 with the
 *   other domains' items (item 4 — HARD INVARIANT, ADR-MONO-043 D5);
 * - a domain answering 401 is NOT a degraded domain — the session is no
 *   longer valid and the whole inbox answers 401 (item 4). 🔴 The former
 *   BFF degraded it instead, which kept an expired session looking like a quiet bell;
 * - each domain gets its own credential (item 3) — every configured domain
 *   today takes the domain-facing IAM OIDC token and NO `X-Tenant-Id` (erp
 *   resolves tenant + recipient from the token);
 * - mark-read goes to the owning domain exactly once, never retried (item 6);
 *   an unconfigured `sourceDomain` is answered 404 with no downstream call.
 *
 * Not carried over (ADR-MONO-081 R1/R2): the circuit-breaker and the `bff_*`
 * metrics — one structured log line per leg instead.
 */

/** One inbox domain: where its inbox and its mark-read live. */
export interface InboxDomain {
  /** The `sourceDomain` value — attribution AND the mark-read path segment. */
  name: string;
  inboxUrl(query: InboxQuery): string;
  markReadUrl(id: string): string;
}

export interface InboxQuery {
  page: number;
  size: number;
  /** `true` / `false` filter; `null` = all (contract § 2.1). */
  unread: boolean | null;
}

export interface InboxBody {
  asOf: string;
  items: Record<string, unknown>[];
  meta: { page: number; size: number; totalElements: number };
  degradedDomains: string[];
}

export type InboxResult =
  | { unauthorized: true; domain: string }
  | { unauthorized: false; body: InboxBody };

/**
 * Domains that have a conforming inbox the console can reach. A name in
 * `CONSOLE_NOTIFICATION_DOMAINS` that is not here is skipped with a warning
 * (a misconfigured domain must not fail the whole bell — D5).
 */
const KNOWN_DOMAINS: Record<string, (env: ReturnType<typeof getServerEnv>) => InboxDomain> = {
  // Same base the erp console screens use; the erp gateway routes
  // `/api/erp/notifications/**` to notification-service (contract § 2.4.8).
  erp: (env) => ({
    name: 'erp',
    inboxUrl: (q) => `${env.ERP_BASE_URL}/api/erp/notifications?${inboxSearch(q)}`,
    markReadUrl: (id) =>
      `${env.ERP_BASE_URL}/api/erp/notifications/${encodeURIComponent(id)}/read`,
  }),
};

function inboxSearch(q: InboxQuery): string {
  const p = new URLSearchParams({ page: String(q.page), size: String(q.size) });
  if (q.unread !== null) p.set('unread', String(q.unread));
  return p.toString();
}

/** The configured domain set, in configured order (default `erp`). */
export function configuredInboxDomains(): InboxDomain[] {
  const env = getServerEnv();
  const raw = env.CONSOLE_NOTIFICATION_DOMAINS ?? 'erp';
  const out: InboxDomain[] = [];
  for (const name of raw.split(',').map((s) => s.trim().toLowerCase())) {
    if (!name || out.some((d) => d.name === name)) continue;
    const build = KNOWN_DOMAINS[name];
    if (!build) {
      logger.warn('notification_inbox_unknown_domain', { domain: name });
      continue;
    }
    out.push(build(env));
  }
  return out;
}

/** Per-domain credential — the domain-facing IAM OIDC token, no `X-Tenant-Id`. */
export function domainInboxHeaders(
  domainFacingToken: string,
  requestId: string,
): Record<string, string> {
  return {
    Accept: 'application/json',
    Authorization: `Bearer ${domainFacingToken}`,
    'X-Request-Id': requestId,
  };
}

type Route = 'notifications-inbox' | 'notifications-read';

function logLeg(
  route: Route,
  domain: string,
  status: 'ok' | 'degraded' | 'unauthorized',
  startedAt: number,
  requestId: string,
  reason?: string,
): void {
  // R1 — one line per leg. No token, no notification body.
  const fields: Record<string, unknown> = {
    route,
    domain,
    status,
    latencyMs: Date.now() - startedAt,
    requestId,
  };
  if (reason) fields.reason = reason;
  if (status === 'ok') logger.info('console_composition_leg', fields);
  else logger.warn('console_composition_leg', fields);
}

const tracer = trace.getTracer('console-web.composition');

function inSpan<T>(route: Route, domain: string, fn: () => Promise<T>, outcome: (r: T) => string) {
  return tracer.startActiveSpan(
    'console.composition.leg',
    { attributes: { 'composition.domain': domain, 'composition.route': route } },
    async (span) => {
      try {
        const r = await fn();
        span.setAttribute('composition.outcome', outcome(r));
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

type LegResult =
  | { kind: 'ok'; items: Record<string, unknown>[]; total: number }
  | { kind: 'degraded' }
  | { kind: 'unauthorized' };

async function readDomain(
  domain: InboxDomain,
  query: InboxQuery,
  headers: Record<string, string>,
  fetchLeg: FetchLeg,
  timeoutMs: number,
  requestId: string,
): Promise<LegResult> {
  const startedAt = Date.now();
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  const degraded = (reason: string): LegResult => {
    logLeg('notifications-inbox', domain.name, 'degraded', startedAt, requestId, reason);
    return { kind: 'degraded' };
  };
  try {
    let res: Response;
    try {
      res = await fetchLeg(domain.inboxUrl(query), {
        method: 'GET',
        headers,
        cache: 'no-store',
        signal: controller.signal,
      });
    } catch {
      return degraded(controller.signal.aborted ? 'TIMEOUT' : 'DOWNSTREAM_ERROR');
    }
    if (res.status === 401) {
      logLeg('notifications-inbox', domain.name, 'unauthorized', startedAt, requestId);
      return { kind: 'unauthorized' };
    }
    if (!res.ok) {
      return degraded(res.status === 403 ? 'PERMISSION_DENIED' : 'DOWNSTREAM_ERROR');
    }
    let body: unknown;
    try {
      body = await res.json();
    } catch {
      return degraded(controller.signal.aborted ? 'TIMEOUT' : 'DOWNSTREAM_ERROR');
    }
    const extracted = extractItems(body, domain.name);
    logLeg('notifications-inbox', domain.name, 'ok', startedAt, requestId);
    return { kind: 'ok', ...extracted };
  } finally {
    clearTimeout(timer);
  }
}

/**
 * `{ data: [...], meta: { totalElements } }` → items + total, injecting
 * `sourceDomain` on any item that omits it (contract § 4 item 2 — never
 * overwriting one the domain supplied).
 */
function extractItems(
  body: unknown,
  domainName: string,
): { items: Record<string, unknown>[]; total: number } {
  if (!body || typeof body !== 'object') return { items: [], total: 0 };
  const { data, meta } = body as { data?: unknown; meta?: unknown };
  const items: Record<string, unknown>[] = [];
  if (Array.isArray(data)) {
    for (const el of data) {
      if (!el || typeof el !== 'object' || Array.isArray(el)) continue;
      const copy: Record<string, unknown> = { ...(el as Record<string, unknown>) };
      const existing = copy.sourceDomain;
      if (existing == null || (typeof existing === 'string' && existing.trim() === '')) {
        copy.sourceDomain = domainName;
      }
      items.push(copy);
    }
  }
  let total = 0;
  if (meta && typeof meta === 'object') {
    const te = (meta as { totalElements?: unknown }).totalElements;
    if (typeof te === 'number' && Number.isFinite(te)) total = te;
  }
  return { items, total };
}

function createdAtOf(item: Record<string, unknown>): number | null {
  const v = item.createdAt;
  if (typeof v !== 'string' || v.trim() === '') return null;
  const t = Date.parse(v);
  return Number.isNaN(t) ? null : t;
}

/** `createdAt` desc, missing/unparseable last (contract § 1 default sort). */
function byCreatedAtDesc(a: Record<string, unknown>, b: Record<string, unknown>): number {
  const ta = createdAtOf(a);
  const tb = createdAtOf(b);
  if (ta === null && tb === null) return 0;
  if (ta === null) return 1;
  if (tb === null) return -1;
  return tb - ta;
}

/**
 * Fans in every domain's inbox. Never throws for a domain failure; a 401 from
 * any domain makes the result `unauthorized`.
 */
export async function aggregateInbox(
  domains: readonly InboxDomain[],
  opts: {
    query: InboxQuery;
    headers: Record<string, string>;
    requestId: string;
    fetchLeg: FetchLeg;
    timeoutMs?: number;
    now?: () => Date;
  },
): Promise<InboxResult> {
  const timeoutMs = opts.timeoutMs ?? LEG_TIMEOUT_MS;
  const asOf = (opts.now ?? (() => new Date()))().toISOString();
  const settled = await Promise.allSettled(
    domains.map((d) =>
      inSpan(
        'notifications-inbox',
        d.name,
        () => readDomain(d, opts.query, opts.headers, opts.fetchLeg, timeoutMs, opts.requestId),
        (r) => r.kind,
      ),
    ),
  );

  const items: Record<string, unknown>[] = [];
  const degradedDomains: string[] = [];
  let totalElements = 0;
  for (let i = 0; i < settled.length; i++) {
    const s = settled[i];
    const name = domains[i].name;
    if (s.status === 'rejected' || s.value.kind === 'degraded') {
      degradedDomains.push(name);
      continue;
    }
    if (s.value.kind === 'unauthorized') return { unauthorized: true, domain: name };
    items.push(...s.value.items);
    totalElements += s.value.total;
  }
  items.sort(byCreatedAtDesc);

  if (degradedDomains.length > 0) {
    logger.warn('notification_inbox_degraded', {
      requestId: opts.requestId,
      degradedDomains,
      configured: domains.length,
    });
  }
  return {
    unauthorized: false,
    body: {
      asOf,
      items,
      meta: { page: opts.query.page, size: opts.query.size, totalElements },
      degradedDomains,
    },
  };
}

/** The configured domain named by a mark-read path segment, or `null` (→ 404). */
export function findInboxDomain(
  domains: readonly InboxDomain[],
  sourceDomain: string,
): InboxDomain | null {
  const name = sourceDomain.trim().toLowerCase();
  return domains.find((d) => d.name === name) ?? null;
}

/**
 * Sends ONE mark-read to the owning domain. Never retried — it is a write
 * (contract § 4 item 6). A timeout or network failure surfaces as a thrown
 * error for the route to map; it is not re-sent.
 */
export async function markReadOnce(
  domain: InboxDomain,
  id: string,
  opts: {
    headers: Record<string, string>;
    requestId: string;
    fetchLeg: FetchLeg;
    timeoutMs?: number;
  },
): Promise<Response> {
  return inSpan(
    'notifications-read',
    domain.name,
    async () => {
      const startedAt = Date.now();
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), opts.timeoutMs ?? LEG_TIMEOUT_MS);
      try {
        const res = await opts.fetchLeg(domain.markReadUrl(id), {
          method: 'POST',
          headers: opts.headers,
          cache: 'no-store',
          signal: controller.signal,
        });
        const status = res.ok ? 'ok' : res.status === 401 ? 'unauthorized' : 'degraded';
        logLeg(
          'notifications-read',
          domain.name,
          status,
          startedAt,
          opts.requestId,
          res.ok ? undefined : `HTTP_${res.status}`,
        );
        return res;
      } catch (e) {
        logLeg(
          'notifications-read',
          domain.name,
          'degraded',
          startedAt,
          opts.requestId,
          controller.signal.aborted ? 'TIMEOUT' : 'DOWNSTREAM_ERROR',
        );
        throw e;
      } finally {
        clearTimeout(timer);
      }
    },
    (r) => (r.ok ? 'ok' : `http_${r.status}`),
  );
}
