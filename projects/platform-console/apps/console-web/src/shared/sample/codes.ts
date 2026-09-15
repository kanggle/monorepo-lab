/**
 * Sample-mode vocabulary (ADR-MONO-074 — anonymous visitors see the real
 * console, answered with sample data).
 *
 * 🔴 This module is imported by BOTH the server (the sample router, the
 *    session module) and the client (`shared/api/client.ts`, the sample
 *    widgets). It must stay dependency-free: no `next/headers`, no env, no
 *    network — the `shared/sample/**` isolation guard
 *    (`tests/unit/sample-router-isolation.test.ts`) enforces that.
 */

/**
 * R1ⓐ — every non-GET a sample visitor makes is refused with this code.
 *
 * 🔴🔴 The copy the visitor reads is NOT carried in the response message: four
 *    of the gateway cores overwrite a 403's message (`'not permitted'`) and only
 *    keep the code. The copy therefore comes from ONE code → copy mapping
 *    (`messageForCode` in `shared/api/errors.ts`), never from a message.
 */
export const SAMPLE_READ_ONLY = 'SAMPLE_READ_ONLY';

/**
 * A9 — a GET whose sample data has not been written yet. The cores map a 503
 * to a profile-fixed degrade message, so — for the same reason as
 * {@link SAMPLE_READ_ONLY} — the copy comes from the code.
 */
export const SAMPLE_NOT_READY = 'SAMPLE_NOT_READY';

export type SampleErrorCode = typeof SAMPLE_READ_ONLY | typeof SAMPLE_NOT_READY;

export function isSampleErrorCode(code: unknown): code is SampleErrorCode {
  return code === SAMPLE_READ_ONLY || code === SAMPLE_NOT_READY;
}

/**
 * The single read-only tenant a sample visitor sits in. It is an identifier,
 * so it carries no «(샘플)» suffix (R2ⓐ — ids/codes are parsed, not read).
 */
export const SAMPLE_TENANT_ID = 'sample';

/** R2ⓐ — appended to every human-readable fixture string. */
export const SAMPLE_LABEL_SUFFIX = ' (샘플)';

/**
 * The instant every fixture is "as of". Written in UTC (the authoring host is
 * KST; CI is UTC — a local-time stamp would read as a future date there).
 */
export const SAMPLE_AS_OF = '2026-09-15T11:00:00Z';
