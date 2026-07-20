import { EcommerceUnavailableError } from '@/shared/api/errors';
import {
  callEcommerceGateway,
  type EcommerceGatewayProfile,
} from '@/shared/api/ecommerce-gateway';

/**
 * ecommerce-ops feature wrapper over the shared **ecommerce gateway HTTP core**
 * (TASK-PC-FE-213 — the hardened call site was promoted to
 * {@link import('@/shared/api/ecommerce-gateway')}, aligning ecommerce with the
 * scm/wms/iam `shared/api/<domain>-gateway.ts` + `Profile` pattern). This module
 * is the single place that maps a compact per-slice {@link EcommerceCallLabel}
 * to a full {@link EcommerceGatewayProfile} and injects the section-degrade
 * {@link EcommerceUnavailableError} factory — the direct analog of scm's
 * `scm-ops/api/scm-client.ts` (`callScm` + `SCM_PROFILE`). The eight
 * ecommerce-ops slices keep calling `callEcommerce(opts, parse, label)` and so
 * consume the shared core transitively, byte-for-byte unchanged.
 *
 * Behavior is byte-identical to the previous feature-internal core: credential =
 * `getDomainFacingToken()` (NEVER `getOperatorToken()`); NO `X-Tenant-Id`; NO
 * `Idempotency-Key`; `Content-Type` only when a body is present; the ecommerce
 * FLAT error envelope; the § 2.5 resilience taxonomy (401/403 → `ApiError`,
 * 503/timeout/network → `EcommerceUnavailableError`); and the per-slice log-event
 * strings (`ecommerce_ok` for products, `ecommerce_<event>_ok` for the rest).
 * The wire behaviour lives in the shared core; see its module doc.
 */

/**
 * Per-slice observability + message label. Drives the structured log-event
 * names and the synthetic-message wording so each slice's emitted strings are
 * preserved verbatim.
 *
 * `event` is the log-event infix: most slices use their singular name
 * (`order`, `user`, `seller`, …) yielding `ecommerce_<event>_ok`; the products
 * slice uses the EMPTY string, yielding the bare `ecommerce_ok` /
 * `ecommerce_unauthorized` / … (no infix) — preserved exactly.
 */
export interface EcommerceCallLabel {
  /** Log-event infix (e.g. `order`, `seller`); EMPTY for the products slice. */
  event: string;
  /** Synthetic default for the flat-envelope parser:
   *  `ecommerce <noun> request failed (<status>)`. */
  errorNoun: string;
  /** 503 degrade message: `ecommerce <serviceLabel> unavailable`. */
  unavailableLabel: string;
  /** Timeout message: `ecommerce <callLabel> call timed out`. */
  timedOutLabel: string;
  /** Network-failure message: `ecommerce <callLabel> call failed`. */
  failedLabel: string;
}

export interface EcommerceCallOptions {
  method: string;
  /** Absolute base (admin or public subtree, resolved by the caller). */
  base: string;
  /** Path relative to `base`. */
  path: string;
  /** Typed mutation body; `undefined` for reads / DELETE. */
  body?: unknown;
  /** Opt-in `Idempotency-Key` (TASK-BE-536) — see {@link import('@/shared/api/ecommerce-gateway').EcommerceGatewayRequest.idempotencyKey}. */
  idempotencyKey?: string;
}

/**
 * Maps a compact per-slice {@link EcommerceCallLabel} to the shared
 * {@link EcommerceGatewayProfile}. The single definition site (the task's
 * "ECOMMERCE_PROFILE 정의처 하나") — reproduces the original core's event-infix
 * nuance (`logPrefix` = `ecommerce` for the products slice, `ecommerce_<event>`
 * otherwise) and the per-slice synthetic-message strings verbatim, and injects
 * the {@link EcommerceUnavailableError} degrade factory.
 */
function ecommerceProfile(label: EcommerceCallLabel): EcommerceGatewayProfile {
  return {
    logPrefix: label.event ? `ecommerce_${label.event}` : 'ecommerce',
    requestFailedLabel: `ecommerce ${label.errorNoun} request failed`,
    makeUnavailable: (reason, code, message) =>
      new EcommerceUnavailableError(reason, code, message),
    isUnavailable: (err) => err instanceof EcommerceUnavailableError,
    messages: {
      degraded: `ecommerce ${label.unavailableLabel} unavailable`,
      timeout: `ecommerce ${label.timedOutLabel} call timed out`,
      network: `ecommerce ${label.failedLabel} call failed`,
    },
  };
}

/**
 * Thin wrapper over the shared {@link callEcommerceGateway} core with the
 * per-slice {@link ecommerceProfile}. `parse` is `undefined` for a void
 * mutation / 204 (DELETE). Signature preserved so every slice's call site is
 * unchanged.
 */
export function callEcommerce<T>(
  opts: EcommerceCallOptions,
  parse: ((json: unknown) => T) | undefined,
  label: EcommerceCallLabel,
): Promise<T> {
  return callEcommerceGateway(
    {
      method: opts.method,
      base: opts.base,
      path: opts.path,
      body: opts.body,
      idempotencyKey: opts.idempotencyKey,
    },
    parse,
    ecommerceProfile(label),
  );
}
