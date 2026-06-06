import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';

/**
 * TASK-PC-FE-010 no-drift guard for the IAM § 3 parity matrix.
 *
 * § 2.4.8 (erp) is an ADDITIVE non-IAM domain binding — it MUST
 * NOT mutate § 3 (the IAM `admin-web` absorption parity gate
 * finalized by TASK-PC-FE-006 at 16/16 verified rows). This guard
 * pins the exact invariants the task AC requires:
 *
 *   - the § 3.1 per-row attestation marker phrase appears EXACTLY 16
 *     times (FE-006's no-drift count is preserved — § 2.4.8 prose
 *     deliberately does NOT use it);
 *   - § 2.4.8 prose IS present in the contract (the binding
 *     actually landed);
 *   - the erp section is described as "additive domain scope",
 *     "Not a § 3 parity row", and explicitly NOT a parity row;
 *   - the spec records the honest erp-side facts: list-driven +
 *     asOf-first-class (INVERSE of finance), no fabricated 429
 *     (identical to finance), E1/E2/E3 + confidential + honest
 *     enum surfacing.
 */

const APP_ROOT = path.resolve(__dirname, '../..');
const CONTRACT_PATH = path.resolve(
  APP_ROOT,
  '../../specs/contracts/console-integration-contract.md',
);

describe('§ 3.1 no-drift — § 2.4.8 (erp) preserves the FE-006 16-row attestation count', () => {
  it('the § 3.1 per-row attestation marker phrase appears EXACTLY 16 times (no §2.4.8 row added)', () => {
    const spec = readFileSync(CONTRACT_PATH, 'utf8');
    const count = spec.split('verified by TASK-PC-FE-006').length - 1;
    expect(count).toBe(16);
  });

  it('§ 2.4.8 is present in the contract (the erp binding landed)', () => {
    const spec = readFileSync(CONTRACT_PATH, 'utf8');
    expect(spec).toContain('2.4.8 erp operations surface');
    expect(spec).toContain('TASK-PC-FE-010');
  });

  it('§ 2.4.8 records the honest erp facts (list-driven + asOf-first-class; no fabricated 429; E1/E2/E3; confidential; honest enums)', () => {
    const spec = readFileSync(CONTRACT_PATH, 'utf8');
    // List-driven + asOf-first-class — the INVERSE of the FE-009
    // finance account-id-driven shape.
    expect(spec).toMatch(/list-driven/i);
    expect(spec).toMatch(/asOf/);
    // The no-429 honest difference from scm (identical to finance)
    // is recorded explicitly.
    expect(spec).toMatch(/no\s+(?:documented\s+)?`?429`?/i);
    // E1 / E2 / E3 references.
    expect(spec).toContain('E1');
    expect(spec).toContain('E2');
    expect(spec).toContain('E3');
    // Honest RETIRED / SEPARATED enum surfacing.
    expect(spec).toContain('RETIRED');
    expect(spec).toContain('SEPARATED');
    // Confidential + audit-heavy discipline (employee PII /
    // business-partner financial / cost-center sensitive).
    expect(spec).toMatch(/confidential/i);
  });

  it('§ 2.4.8 is explicitly NOT a § 3 parity row (additive domain scope)', () => {
    const spec = readFileSync(CONTRACT_PATH, 'utf8');
    // The closing blockquote of § 2.4.8 mirrors § 2.4.5 / § 2.4.6 /
    // § 2.4.7.
    expect(spec).toMatch(/Not a § 3 parity row[\s\S]+§\s*2\.4\.8/);
  });

  it('the erp section reuses the § 2.4.5 per-domain credential rule (NOT re-derived; same as § 2.4.6/§ 2.4.7)', () => {
    const spec = readFileSync(CONTRACT_PATH, 'utf8');
    // The reuse-not-redefine wording the task pins.
    expect(spec).toMatch(
      /erp\s+(?:reuses|REUSE)[\s\S]+§\s*2\.4\.5|§\s*2\.4\.5[\s\S]+erp/i,
    );
    // The credential is the IAM OIDC access token, NEVER the
    // operator token — recorded in prose for erp.
    expect(spec).toMatch(/getAccessToken/);
    expect(spec).toMatch(/never[\s\S]+getOperatorToken|getOperatorToken[\s\S]+never/i);
  });
});
