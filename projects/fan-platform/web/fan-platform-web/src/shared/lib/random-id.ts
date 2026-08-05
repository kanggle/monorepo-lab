/**
 * A UUID v4 string that also works over plain HTTP (TASK-FAN-FE-015).
 *
 * `crypto.randomUUID()` is only exposed in a **secure context** — HTTPS or `localhost`. The demo
 * is served over plain HTTP on a `*.local` hostname, which is neither, so there the property is
 * simply `undefined` and calling it throws `TypeError: crypto.randomUUID is not a function`.
 *
 * That was a latent second defect, not a new one: `requestPortOnePayment` and the billing-key
 * helper have always generated their ids this way, so the real PortOne path would have hit the
 * same wall over HTTP. Nobody saw it because the missing-keys guard returned first and the
 * request never got that far — removing that guard for the demo is what surfaced it.
 *
 * `crypto.getRandomValues` has no secure-context requirement, so it carries the same randomness
 * everywhere. `randomUUID` is still preferred when present: it is the platform's own
 * implementation and this stays a fallback rather than a replacement.
 */
export function randomUuid(): string {
  const c: Crypto | undefined = globalThis.crypto;
  if (typeof c?.randomUUID === 'function') {
    return c.randomUUID();
  }
  if (typeof c?.getRandomValues === 'function') {
    const b = c.getRandomValues(new Uint8Array(16));
    b[6] = (b[6] & 0x0f) | 0x40; // version 4
    b[8] = (b[8] & 0x3f) | 0x80; // variant 10x
    const hex = Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  }
  // No Web Crypto at all. The value only has to be unique per attempt — the backend's
  // idempotency guard is what it feeds — so a non-cryptographic id is acceptable here, and
  // throwing instead would block checkout entirely on a browser that is already unusual.
  return `nc-${Date.now().toString(16)}-${Math.random().toString(16).slice(2, 14)}`;
}
