#!/usr/bin/env node
// -----------------------------------------------------------------------------
// check-spec-metrics-implemented.mjs — a metric or span a SPEC table declares is
// either exported by the code, or the row says it is planned      (TASK-MONO-841)
// -----------------------------------------------------------------------------
// WHY
//   Three directions exist between a metric's spec, its code and its consumers:
//     code → spec        scripts/check-metric-name-registry.sh   (TASK-MONO-833)
//     alerts/dashboards → code   scripts/check-alert-rule-metrics.sh (834 · 839)
//     spec → code        THIS FILE
//   The third was measured once by hand (TASK-MONO-837, 2026-11-25 UTC): of 251
//   names spec tables declared, 74 were exported. iam's «필수 메트릭» tables listed 52
//   rows and 32 did not exist — "required" read as "present" and nothing could fail on
//   it. TASK-MONO-840 then marked every still-missing row `🔵 *계획 · 미구현 …*`. This
//   check keeps that true: a new unmarked row for a metric nobody exports fails.
//
// POPULATION (the same rule TASK-MONO-837 measured with)
//   Tracked projects/*/specs/**/*.md. A table row counts when its innermost
//   classifying heading matches metric/메트릭/observability/관측 (→ metric) or
//   \bspan/trace/tracing\b/추적 (→ span; word boundaries — "Contract" is not a trace,
//   837's first run made exactly that mistake on 217 rows). Headings about logs
//   (log/logging/로그/로깅/MDC) end the search — log-field tables are not metrics.
//   Names = backticked tokens of the FIRST cell that look like dotted/underscored names.
//
// VERDICT, per row
//   exempt  — the row carries the planned mark (`🔵 *계획 · 미구현`), or says `파생`
//             (a derived value: PromQL, not an instrument), or the spec lives in a
//             `*-deprecated` service directory.
//   metric  — every name must be EXPORTED by that project. The judgement is NOT
//             re-implemented here: names are piped to
//             `bash scripts/check-alert-rule-metrics.sh --unexported <project>`, which
//             applies the one predicate the other two guards use.
//   span    — no repository code creates spans by hand (measured 0 in 837), so a span
//             name cannot be judged exported: an unmarked span row fails.
//   FAIL CLOSED on an empty population.
//
// NOT GUARDED: metrics written as bullet lists instead of tables; label agreement.
//
// usage: node scripts/check-spec-metrics-implemented.mjs [--root <dir>] [--self-test]
// -----------------------------------------------------------------------------
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync, cpSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const PLANNED = '🔵 *계획 · 미구현';
const HEAD = /^(#+)\s+(.*)$/;
const METRIC_HEAD = /metric|메트릭|observability|관측/i;
const SPAN_HEAD = /\bspans?\b|\btraces?\b|\btracing\b|추적/i;
const LOG_HEAD = /\blogs?\b|logging|로그|로깅|\bmdc\b/i;
const NAME = /^[a-z][a-z0-9]*([._][a-z0-9]+)+$/;

function lsFiles(root, ...specs) {
  const out = execFileSync('git', ['-C', root, 'ls-files', '-z', '--', ...specs],
    { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
  return out.split('\0').filter(Boolean);
}

/** [{file, line, kind, names, exempt}] for one spec file's text. */
export function specRows(file, text) {
  const rows = [];
  let stack = [];
  text.replace(/\r/g, '').split('\n').forEach((line, i) => {
    const h = HEAD.exec(line);
    if (h) {
      const lvl = h[1].length;
      stack = stack.filter((s) => s.lvl < lvl).concat([{ lvl, title: h[2].trim() }]);
      return;
    }
    if (!line.startsWith('|')) return;
    let kind = null;
    for (const { title } of [...stack].reverse()) {
      if (LOG_HEAD.test(title) && !METRIC_HEAD.test(title.replace('Observability', ''))) break;
      if (SPAN_HEAD.test(title)) { kind = 'span'; break; }
      if (METRIC_HEAD.test(title)) { kind = 'metric'; break; }
    }
    if (!kind) return;
    const cells = line.split('|');
    if (cells.length < 3) return;
    const names = [...cells[1].matchAll(/`([^`]+)`/g)]
      .map((m) => m[1].trim().replace(/\{.*$/, ''))
      .filter((n) => NAME.test(n));
    if (names.length === 0) return;
    const exempt = line.includes(PLANNED) || line.includes('파생') || /\/specs\/services\/[^/]*-deprecated\//.test(file);
    rows.push({ file, line: i + 1, kind, names, exempt });
  });
  return rows;
}

function unexported(root, project, names) {
  const r = spawnSync('bash', [join(root, 'scripts/check-alert-rule-metrics.sh'), '--unexported', project],
    { cwd: root, input: names.join('\n') + '\n', encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 });
  if (r.status !== 0) throw new Error(`check-alert-rule-metrics.sh --unexported ${project} exited ${r.status}: ${r.stderr}`);
  return new Set(r.stdout.split('\n').map((s) => s.trim()).filter(Boolean));
}

export function check(root) {
  const files = lsFiles(root, ':(glob)projects/*/specs/**/*.md');
  const rows = files.flatMap((f) => specRows(f, readFileSync(join(root, f), 'utf8')));
  if (rows.length === 0) return { problems: ['found ZERO metric/span table rows under projects/*/specs — the check would pass having verified nothing'], rows: 0 };
  const problems = [];
  const byProject = new Map();
  for (const r of rows) {
    if (r.exempt) continue;
    if (r.kind === 'span') {
      problems.push(`${r.file}:${r.line}  span ${r.names.join(', ')} — no code creates spans by hand; mark the row planned`);
      continue;
    }
    const p = r.file.split('/')[1];
    if (!byProject.has(p)) byProject.set(p, []);
    byProject.get(p).push(r);
  }
  for (const [p, prow] of byProject) {
    const miss = unexported(root, p, [...new Set(prow.flatMap((r) => r.names))]);
    for (const r of prow) {
      const bad = r.names.filter((n) => miss.has(n));
      if (bad.length) problems.push(`${r.file}:${r.line}  ${bad.join(', ')} — projects/${p} does not export it`);
    }
  }
  const planned = rows.filter((r) => r.exempt).length;
  return { problems, rows: rows.length, planned, projects: new Set(rows.map((r) => r.file.split('/')[1])).size };
}

// ---------------------------------------------------------------------------
// self-test — throwaway git repos carrying the REAL judge script plus fixtures.
// ---------------------------------------------------------------------------
function fixtureRepo(files) {
  const root = mkdtempSync(join(tmpdir(), 'spec-metrics-'));
  mkdirSync(join(root, 'scripts'), { recursive: true });
  cpSync(join(HERE, 'check-alert-rule-metrics.sh'), join(root, 'scripts/check-alert-rule-metrics.sh'));
  for (const [p, body] of Object.entries(files)) {
    mkdirSync(join(root, dirname(p)), { recursive: true });
    writeFileSync(join(root, p), body);
  }
  const quiet = { stdio: ['ignore', 'ignore', 'ignore'] };
  execFileSync('git', ['-C', root, 'init', '-q'], quiet);
  execFileSync('git', ['-C', root, '-c', 'core.autocrlf=false', 'add', '-A'], quiet);
  return root;
}
const SPEC = 'projects/p/specs/services/svc/observability.md';
const CODE = 'projects/p/apps/svc/src/main/java/M.java';
const java = (name) => `class M { Object c = io.micrometer.core.instrument.Counter.builder("${name}"); }\n`;
const table = (heading, rows) => `# svc\n\n## ${heading}\n\n| 이름 | 설명 |\n|---|---|\n${rows.join('\n')}\n`;

function selfTest() {
  const cells = [
    ['unmarked row for a metric nobody exports bites', { [SPEC]: table('Metrics', ['| `p_nobody_total` | x |']), [CODE]: java('p.other') }, 1],
    ['the planned mark exempts it', { [SPEC]: table('Metrics', ['| `p_nobody_total` | x — 🔵 *계획 · 미구현 (TASK-MONO-837)* |']), [CODE]: java('p.other') }, 0],
    ['an exported metric passes (dotted code name, _total spelling)', { [SPEC]: table('Metrics', ['| `p_done_total` | x |']), [CODE]: java('p.done') }, 0],
    ['a derived row passes', { [SPEC]: table('Metrics', ['| `p_hit_rate` | x — 파생 — PromQL: `a/b` |']), [CODE]: java('p.other') }, 0],
    ['a *-deprecated service spec is exempt', { 'projects/p/specs/services/old-deprecated/observability.md': table('Metrics', ['| `p_gone_total` | x |']), [CODE]: java('p.other') }, 0],
    ['a log-field table is not a metric table', { [SPEC]: '# svc\n\n## Observability\n\n### Logs\n\n| 필드 | 설명 |\n|---|---|\n| `trace.id` | x |\n\n### Metrics\n\n| 이름 | 설명 |\n|---|---|\n| `p_other_total` | x |\n', [CODE]: java('p.other') }, 0],
    ['a "Contract" heading is not a trace heading', { [SPEC]: '# svc\n\n## Event Contract\n\n| Topic | x |\n|---|---|\n| `p.thing.happened` | x |\n\n## Metrics\n\n| 이름 | 설명 |\n|---|---|\n| `p_other_total` | x |\n', [CODE]: java('p.other') }, 0],
    ['an unmarked span row bites', { [SPEC]: table('Traces (OTel)', ['| `p.login` | x |']), [CODE]: java('p.other') }, 1],
    ['a marked span row passes', { [SPEC]: table('Traces (OTel)', ['| `p.login` | x — 🔵 *계획 · 미구현 (TASK-MONO-837)* |']), [CODE]: java('p.other') }, 0],
    ['a framework metric passes', { [SPEC]: table('Metrics', ['| `http.server.requests` | x |', '| `resilience4j_circuitbreaker_state` | x |']), [CODE]: java('p.other') }, 0],
    ['two names, one missing and unmarked, bites', { [SPEC]: table('Metrics', ['| `p_done_total` / `p_nobody_total` | x |']), [CODE]: java('p.done') }, 1],
    ['another project exporting it does not count', {
      [SPEC]: table('Metrics', ['| `q_only_total` | x |']), [CODE]: java('p.other'),
      'projects/q/apps/svc/src/main/java/M.java': java('q.only') }, 1],
    ['empty population fails closed', { [CODE]: java('p.other') }, 1],
  ];
  const bad = [];
  for (const [name, files, want] of cells) {
    const root = fixtureRepo(files);
    try {
      const got = check(root).problems.length;
      const pass = got === want;
      console.log(`${pass ? 'PASS' : 'FAIL'}  ${name}  (problems=${got}, want ${want})`);
      if (!pass) bad.push(name);
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  }
  console.log(`self-test: ${cells.length - bad.length}/${cells.length} passed`);
  return bad.length === 0 ? 0 : 1;
}

const argv = process.argv.slice(2);
if (argv.includes('--self-test')) process.exit(selfTest());
const ri = argv.indexOf('--root');
const root = ri >= 0 ? argv[ri + 1] : execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim();
const res = check(root);
if (res.problems.length) {
  console.error('FAIL: spec table row(s) declare a metric/span the code does not export, and the row is not marked planned:');
  for (const p of res.problems) console.error(`  ${p}`);
  console.error('');
  console.error('  Implement it, or — if it is not built yet — end the row\'s last cell with');
  console.error('  "— 🔵 *계획 · 미구현 (TASK-…)*" (TASK-MONO-840 format). A derived value says "파생".');
  process.exit(1);
}
console.log(`check-spec-metrics-implemented: OK — ${res.rows} metric/span row(s) in specs across ${res.projects} project(s); ` +
  `${res.rows - res.planned} exported, ${res.planned} marked planned / derived / deprecated.`);
