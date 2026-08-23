import { describe, expect, it } from 'vitest';
import { decideVerdict, type ProbeResult } from '@/app/api/ac0-probe/verdict';

/**
 * ⏳ TEMPORARY — TASK-MONO-571 / ADR-MONO-067 AC-0 ②. Removed with the probe (AC-5).
 *
 * The probe's live JSON will look equally authoritative whether or not its verdict predicate is
 * correct, so the predicate is exercised here against synthetic cells first. Every case below is a
 * way the live measurement could be MISREAD, not a way the network could fail.
 */

const cell = (over: Partial<ProbeResult> = {}): ProbeResult => ({
  url: 'http://example.test/',
  ok: true,
  status: 200,
  location: null,
  error: null,
  ...over,
});

const DOWN = cell({ ok: false, status: null, error: { name: 'TypeError', message: 'fetch failed', code: 'ECONNREFUSED' } });
const REDIRECTED = cell({ ok: false, status: 301, location: 'https://example.test/' });

describe('AC-0 ② verdict predicate', () => {
  it('reports success when a plaintext call reached 2xx with no redirect hop', () => {
    expect(decideVerdict({ plaintextA: cell(), plaintextB: cell(), httpsControl: cell() })).toBe(
      'PLAINTEXT_HTTP_EGRESS_WORKS',
    );
  });

  it('still reports success when only ONE of the two plaintext hosts answered', () => {
    // Two independent subject hosts exist precisely so one dead host is not read as a block.
    expect(decideVerdict({ plaintextA: DOWN, plaintextB: cell(), httpsControl: cell() })).toBe(
      'PLAINTEXT_HTTP_EGRESS_WORKS',
    );
  });

  it('reports BLOCKED only when both plaintext hosts failed and the control passed', () => {
    expect(decideVerdict({ plaintextA: DOWN, plaintextB: DOWN, httpsControl: cell() })).toBe(
      'PLAINTEXT_HTTP_EGRESS_BLOCKED',
    );
  });

  it('🔴 refuses to judge when the https control also failed', () => {
    // Without this branch, a runtime with no egress at all reports "plaintext is blocked" — a
    // false verdict that would sink the ADR on evidence that says nothing about plaintext.
    expect(decideVerdict({ plaintextA: DOWN, plaintextB: DOWN, httpsControl: DOWN })).toMatch(/^UNJUDGEABLE/);
  });

  it('🔴 does not count a 301-to-https as a plaintext success', () => {
    expect(decideVerdict({ plaintextA: REDIRECTED, plaintextB: REDIRECTED, httpsControl: cell() })).toMatch(
      /^INCONCLUSIVE/,
    );
  });

  it('🔴 a 2xx that carries a location header is not a clean plaintext success either', () => {
    // Defends the `location === null` half of the predicate independently of the status half:
    // dropping it would let a 2xx-with-redirect-hint pass as clean.
    const twoHundredWithLocation = cell({ ok: true, status: 200, location: 'https://example.test/' });
    expect(
      decideVerdict({ plaintextA: twoHundredWithLocation, plaintextB: DOWN, httpsControl: cell() }),
    ).toMatch(/^INCONCLUSIVE/);
  });

  it('the control passing does not by itself produce a success verdict', () => {
    // Guards against a predicate that reads the control as if it were the subject.
    expect(decideVerdict({ plaintextA: DOWN, plaintextB: DOWN, httpsControl: cell() })).not.toBe(
      'PLAINTEXT_HTTP_EGRESS_WORKS',
    );
  });
});
