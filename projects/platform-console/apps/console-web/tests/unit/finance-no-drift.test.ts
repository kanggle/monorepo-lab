import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';

/**
 * TASK-PC-FE-009 no-drift guard for the GAP § 3 parity matrix.
 *
 * § 2.4.7 (finance) is an ADDITIVE non-GAP domain binding — it MUST
 * NOT mutate § 3 (the GAP `admin-web` absorption parity gate finalized
 * by TASK-PC-FE-006 at 16/16 verified rows). This guard pins the
 * exact invariants the task AC requires:
 *
 *   - the § 3.1 per-row attestation marker phrase appears EXACTLY 16
 *     times (FE-006's no-drift count is preserved — § 2.4.7 prose
 *     deliberately does NOT use it);
 *   - § 2.4.7 prose IS present in the contract (the binding actually
 *     landed);
 *   - the finance section is described as "additive domain scope",
 *     "Not a § 3 parity row", and explicitly NOT a parity row;
 *   - the spec records the honest finance-side facts: no list/search
 *     GET (account-id-driven), no fabricated 429, F5 money + F7 +
 *     honest regulated states.
 */

const APP_ROOT = path.resolve(__dirname, '../..');
const CONTRACT_PATH = path.resolve(
  APP_ROOT,
  '../../specs/contracts/console-integration-contract.md',
);

describe('§ 3.1 no-drift — § 2.4.7 (finance) preserves the FE-006 16-row attestation count', () => {
  it('the § 3.1 per-row attestation marker phrase appears EXACTLY 16 times (no §2.4.7 row added)', () => {
    const spec = readFileSync(CONTRACT_PATH, 'utf8');
    const count = spec.split('verified by TASK-PC-FE-006').length - 1;
    expect(count).toBe(16);
  });

  it('§ 2.4.7 is present in the contract (the finance binding landed)', () => {
    const spec = readFileSync(CONTRACT_PATH, 'utf8');
    expect(spec).toContain(
      '2.4.7 finance operations surface',
    );
    expect(spec).toContain('TASK-PC-FE-009');
  });

  it('§ 2.4.7 records the honest finance facts (no list/search GET; no fabricated 429; F5 + F7 + honest states)', () => {
    const spec = readFileSync(CONTRACT_PATH, 'utf8');
    expect(spec).toContain('account-id-driven');
    // Honestly recorded: finance v1 has no account list/search GET.
    expect(spec).toMatch(/no\s+account\s+list\/search/);
    // The no-429 honest difference from scm is recorded explicitly.
    expect(spec).toMatch(/no\s+(?:documented\s+)?`?429`?/i);
    // F5 money obligation is recorded.
    expect(spec).toContain('F5');
    expect(spec).toMatch(/minor.units/i);
    // F7 / confidential surfacing.
    expect(spec).toMatch(/F7|confidential/);
    // Honest regulated states.
    expect(spec).toMatch(/FROZEN/);
    expect(spec).toMatch(/REVERSED/);
  });

  it('§ 2.4.7 is explicitly NOT a § 3 parity row (additive domain scope)', () => {
    const spec = readFileSync(CONTRACT_PATH, 'utf8');
    // The closing blockquote of § 2.4.7 mirrors § 2.4.5 / § 2.4.6.
    expect(spec).toMatch(/Not a § 3 parity row[\s\S]+§\s*2\.4\.7/);
  });

  it('the finance section reuses the § 2.4.5 per-domain credential rule (NOT re-derived; same as § 2.4.6 scm)', () => {
    const spec = readFileSync(CONTRACT_PATH, 'utf8');
    // The reuse-not-redefine wording the task pins.
    expect(spec).toMatch(
      /finance\s+(?:reuses|REUSE)[\s\S]+§\s*2\.4\.5|§\s*2\.4\.5[\s\S]+finance/i,
    );
    // The credential is the GAP OIDC access token, NEVER the operator
    // token — recorded in prose for finance.
    expect(spec).toMatch(/getAccessToken/);
    expect(spec).toMatch(/never[\s\S]+getOperatorToken|getOperatorToken[\s\S]+never/i);
  });
});
