#!/usr/bin/env node
/**
 * TASK-MONO-373 — assert the e2e specs that MUST run actually ran.
 *
 * WHY THIS EXISTS
 *   The full-stack lane was green for months while running exactly ONE spec
 *   (`auth-redirect`, which makes zero backend calls) and silently skipping the
 *   four that exercise the checkout path. A skipped test reports green. The job
 *   name said "full-stack", the compose file booted 22 containers, and the
 *   coverage those advertised did not exist.
 *
 *   So "the job passed" is NOT evidence. This asserts the thing that actually
 *   matters: each required spec file contributed at least one test that RAN.
 *
 * Usage:  node e2e/tools/assert-specs-ran.mjs <playwright-report.json>
 *
 * Exit codes:
 *   0 — every required spec ran
 *   1 — a required spec was skipped, or did not appear at all
 *   2 — the report is unreadable / parsed to ZERO specs (vacuity guard: a
 *       checker that silently checks nothing is the bug it is meant to catch)
 */

import { readFileSync } from 'node:fs';

/**
 * Specs that MUST run in the full-stack lane.
 *
 * NOT in this list, on purpose: `account-type-guard.spec.ts` — it is
 * `test.fixme()`-marked, because the e2e IAM stack cannot mint a token without
 * the CUSTOMER role (RoleSeedPolicy keys its fail-soft seed on the client's
 * platform, not the user). That deferral is held by TASK-MONO-378, not by this
 * list. If MONO-378 lands, add it here.
 *
 * `rp-initiated-logout.spec.ts` is also absent: it runs in its own lane
 * (web-store-iam-logout-e2e), against the lean IAM stack with no ecommerce
 * backend.
 */
const REQUIRED = [
  'auth-redirect.spec.ts',
  'golden-flow.spec.ts',
  'cart-management.spec.ts',
  'wishlist.spec.ts',
];

const reportPath = process.argv[2];
if (!reportPath) {
  console.error('usage: assert-specs-ran.mjs <playwright-report.json>');
  process.exit(2);
}

let report;
try {
  report = JSON.parse(readFileSync(reportPath, 'utf8'));
} catch (err) {
  console.error(`FATAL: cannot read the Playwright JSON report at ${reportPath}`);
  console.error(`  ${err.message}`);
  console.error('  The run produced no parseable report — that is a failure, not a pass.');
  process.exit(2);
}

/** file -> { ran: [titles], skipped: [titles] } */
const byFile = new Map();

function visit(suite, inheritedFile) {
  const file = suite.file ?? inheritedFile;
  for (const spec of suite.specs ?? []) {
    const specFile = spec.file ?? file;
    if (!specFile) continue;
    const key = specFile.split(/[\\/]/).pop();
    if (!byFile.has(key)) byFile.set(key, { ran: [], skipped: [] });
    const bucket = byFile.get(key);
    for (const t of spec.tests ?? []) {
      // Playwright test status: expected | unexpected | flaky | skipped.
      // `fixme` and `skip` both land as "skipped" — which is precisely why a
      // green job proves nothing on its own.
      (t.status === 'skipped' ? bucket.skipped : bucket.ran).push(spec.title);
    }
  }
  for (const child of suite.suites ?? []) visit(child, file);
}

for (const suite of report.suites ?? []) visit(suite, suite.file);

// --- vacuity guard -----------------------------------------------------------
const totalTests = [...byFile.values()].reduce((n, b) => n + b.ran.length + b.skipped.length, 0);
if (totalTests === 0) {
  console.error('FATAL: the report parsed to ZERO tests.');
  console.error('  Either the run collected nothing, or the report shape changed and this');
  console.error('  parser is reading the wrong field. Both mean this check is not checking.');
  process.exit(2);
}

// --- report ------------------------------------------------------------------
console.log('spec                                 ran  skipped');
console.log('-------------------------------------------------');
for (const [file, b] of [...byFile.entries()].sort()) {
  console.log(`${file.padEnd(36)} ${String(b.ran.length).padStart(3)}  ${String(b.skipped.length).padStart(7)}`);
}
console.log('');

// --- the actual assertion ----------------------------------------------------
const failures = [];
for (const required of REQUIRED) {
  const b = byFile.get(required);
  if (!b) {
    failures.push(`${required} — did not appear in the report AT ALL (not collected)`);
  } else if (b.ran.length === 0) {
    failures.push(`${required} — collected but every test was SKIPPED (${b.skipped.length})`);
  }
}

if (failures.length > 0) {
  console.error('FAIL — specs that must run did not run:');
  for (const f of failures) console.error(`  - ${f}`);
  console.error('');
  console.error('This is the TASK-MONO-373 failure mode: the lane is green and nothing ran.');
  console.error('Check SKIP_GAP_E2E — it must be 0 for BOTH the Playwright runner process');
  console.error('AND the webServer env (playwright.config.ts passes it through separately).');
  process.exit(1);
}

console.log(`OK — all ${REQUIRED.length} required specs ran (${totalTests} tests collected).`);
