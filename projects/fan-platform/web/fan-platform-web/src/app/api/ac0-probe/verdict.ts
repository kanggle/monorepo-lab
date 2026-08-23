/**
 * ⏳ TEMPORARY — TASK-MONO-571 / ADR-MONO-067 AC-0 ②. Deleted with the probe (AC-5).
 *
 * The verdict predicate lives here rather than in `route.ts` for two reasons:
 *
 *  1. A Next route module may only export the HTTP method handlers and the route config. Exporting
 *     anything else fails the generated route typecheck (`OmitWithTag<…>` → `never`), which surfaces
 *     as a `next build` error rather than a lint one.
 *  2. The predicate has to be exercised against synthetic cells BEFORE anyone reads the live output.
 *     The probe's JSON looks equally authoritative whether or not this logic is right, so an
 *     untested discriminator would let a wrong verdict decide the fate of an ADR.
 */

export interface ProbeResult {
  url: string;
  ok: boolean;
  status: number | null;
  /** Set when the server redirected — a 3xx here means the plaintext call did NOT stand on its own. */
  location: string | null;
  /** Populated only on a thrown fetch. `cause.code` separates DNS from connection refusal. */
  error: { name: string; message: string; code: string | null } | null;
}

/** A plaintext call only counts when it reached 2xx on its own — no redirect hop (AC-2). */
const cleanPlaintext = (r: ProbeResult) => r.ok && r.location === null;

export function decideVerdict(cells: {
  plaintextA: ProbeResult;
  plaintextB: ProbeResult;
  httpsControl: ProbeResult;
}): string {
  const { plaintextA, plaintextB, httpsControl } = cells;
  if (!httpsControl.ok) {
    // Control down → the subject cells carry no information at all. Without this branch a runtime
    // with no egress whatsoever reports "plaintext is blocked", which is a false verdict on
    // evidence that says nothing about plaintext.
    return 'UNJUDGEABLE — https control also failed; this is not evidence that plaintext is blocked';
  }
  if (cleanPlaintext(plaintextA) || cleanPlaintext(plaintextB)) {
    return 'PLAINTEXT_HTTP_EGRESS_WORKS';
  }
  if (plaintextA.location || plaintextB.location) {
    return 'INCONCLUSIVE — plaintext answered only with a redirect, which is not a plaintext success';
  }
  return 'PLAINTEXT_HTTP_EGRESS_BLOCKED';
}
