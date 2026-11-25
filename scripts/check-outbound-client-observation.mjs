#!/usr/bin/env node
// -----------------------------------------------------------------------------
// check-outbound-client-observation.mjs — in a service that exports traces, a
// hand-built HTTP client must be observed, or it drops trace propagation
//                                                               (TASK-MONO-845)
// -----------------------------------------------------------------------------
// WHAT THIS GUARDS
//
//   Measured 2026-11-25 UTC: no main source in the repository referenced
//   `ObservationRegistry`. `RestClient.builder()` / `RestClient.create()` /
//   `WebClient.builder()` default to `ObservationRegistry.NOOP`, and the W3C
//   `traceparent` header is written by the tracing observation handler — so a
//   client built by hand produced no client span AND the downstream service
//   started a new trace. The shared `ResilienceClientFactory.buildRestClient`
//   now requires a registry (the compiler enforces that); this guard covers the
//   clients built directly with the builders.
//
// PREDICATE
//
//   Tracer services = projects/*/apps/*/ whose build.gradle declares
//       `micrometer-tracing-bridge-otel` on a non-comment line.
//   Population = their tracked `src/main/java/**/*.java` (`git ls-files` —
//       stage before running locally).
//   In each file (comments and string literals blanked first), every call
//       site of `RestClient.builder(` / `WebClient.builder(` must be observed:
//       (a) its own statement (up to the `;` at the same nesting depth)
//           contains `.observationRegistry(`, or
//       (b) the statement assigns the builder to a variable `v`
//           (`… v = RestClient.builder()…;`) and the file later calls
//           `v.observationRegistry(` / `v⏎ .observationRegistry(`.
//   `RestClient.create(` / `WebClient.create(` always fail — those builders
//       cannot take a registry; use `.builder()…observationRegistry(r).build()`.
//   A builder obtained from Spring Boot's injected `RestClient.Builder` /
//       `WebClient.Builder` bean is already observed and is not a call site of
//       the static methods, so it is not read.
//   Fail closed: zero tracer services, or zero Java files in them.
//
// WHAT THIS DOES NOT GUARD
//   * Services without a tracer (no `micrometer-tracing-bridge-otel`): there
//     is nothing to propagate with, so the call is not a defect there.
//   * `libs/**` — libraries do not know whether their consumer traces.
//   * JDK `HttpClient` used directly (no observation API exists for it).
//   * That the registry passed is the application's bean and not `NOOP`.
//
// usage: node scripts/check-outbound-client-observation.mjs [--root <dir>] [--self-test]
// -----------------------------------------------------------------------------
import { execFileSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';

function lsFiles(root, pathspecs) {
  // projects/ has ~10^5 tracked paths — far over execFileSync's 1 MiB default buffer (ENOBUFS).
  const out = execFileSync('git', ['-C', root, 'ls-files', '-z', '--', ...pathspecs],
    { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
  return out.split('\0').filter(Boolean);
}

/**
 * Java source with comments and string/char/text-block literal contents
 * replaced by spaces (newlines kept, so offsets and line numbers survive).
 */
export function blank(src) {
  const out = src.split('');
  let i = 0;
  const n = src.length;
  const wipe = (from, to) => { for (let k = from; k < to; k++) if (out[k] !== '\n') out[k] = ' '; };
  while (i < n) {
    const c = src[i];
    const d = src[i + 1];
    if (c === '/' && d === '/') {
      let j = i; while (j < n && src[j] !== '\n') j++;
      wipe(i, j); i = j;
    } else if (c === '/' && d === '*') {
      let j = src.indexOf('*/', i + 2); j = j < 0 ? n : j + 2;
      wipe(i, j); i = j;
    } else if (c === '"' && src.startsWith('"""', i)) {
      let j = src.indexOf('"""', i + 3); j = j < 0 ? n : j + 3;
      wipe(i + 3, Math.max(i + 3, j - 3)); i = j;
    } else if (c === '"' || c === '\'') {
      let j = i + 1;
      while (j < n && src[j] !== c && src[j] !== '\n') j += src[j] === '\\' ? 2 : 1;
      wipe(i + 1, j); i = j + 1;
    } else {
      i++;
    }
  }
  return out.join('');
}

/** End offset (exclusive) of the statement starting at `from`: the first `;` at depth 0. */
function statementEnd(code, from) {
  let depth = 0;
  for (let k = from; k < code.length; k++) {
    const ch = code[k];
    if (ch === '(' || ch === '{' || ch === '[') depth++;
    else if (ch === ')' || ch === '}' || ch === ']') {
      depth--;
      if (depth < 0) return k; // the call was an argument / initializer; the enclosing construct ends here
    } else if (ch === ';' && depth === 0) return k + 1;
  }
  return code.length;
}

const CALL = /\b(RestClient|WebClient)\s*\.\s*(builder|create)\s*\(/g;

/** Problems in one (already-read) Java source: [{line, msg}]. */
export function checkSource(src) {
  const code = blank(src);
  const problems = [];
  const lineOf = (off) => code.slice(0, off).split('\n').length;
  for (const m of code.matchAll(CALL)) {
    const [, type, method] = m;
    const at = m.index;
    if (method === 'create') {
      problems.push({ line: lineOf(at), msg: `${type}.create(…) cannot carry an ObservationRegistry — use ${type}.builder()….observationRegistry(registry).build()` });
      continue;
    }
    const end = statementEnd(code, at);
    if (/\.\s*observationRegistry\s*\(/.test(code.slice(at, end))) continue;
    // (b) assigned to a variable, configured later in the file
    const lineStart = code.lastIndexOf(';', at) + 1;
    const head = code.slice(lineStart, at);
    const v = head.match(/\b([A-Za-z_$][\w$]*)\s*=\s*$/);
    if (v) {
      const later = new RegExp(`\\b${v[1].replace(/\$/g, '\\$')}\\s*\\.\\s*observationRegistry\\s*\\(`);
      if (later.test(code.slice(end))) continue;
    }
    problems.push({ line: lineOf(at), msg: `${type}.builder() without .observationRegistry(…) — no client span, no traceparent` });
  }
  return problems;
}

/** Strips `//` comments, then tests for the tracer bridge dependency. */
function declaresTracer(buildGradle) {
  return buildGradle.split('\n').some((l) => {
    const code = l.replace(/\/\/.*$/, '');
    return code.includes('micrometer-tracing-bridge-otel');
  });
}

export function check(root) {
  const builds = lsFiles(root, ['projects/*/apps/*/build.gradle']);
  const tracerApps = builds
    .filter((b) => declaresTracer(readFileSync(join(root, b), 'utf8')))
    .map((b) => b.slice(0, -'build.gradle'.length)); // projects/p/apps/s/
  const problems = [];
  if (tracerApps.length === 0) {
    problems.push('no service declares micrometer-tracing-bridge-otel — refusing to report OK on an empty population');
    return { problems, services: 0, files: 0, sites: 0 };
  }
  const files = lsFiles(root, tracerApps.map((a) => `${a}src/main/java/**/*.java`));
  if (files.length === 0) {
    problems.push(`zero Java files under src/main/java of ${tracerApps.length} tracer service(s) — refusing to report OK on an empty population`);
  }
  let sites = 0;
  for (const f of files) {
    const src = readFileSync(join(root, f), 'utf8');
    sites += [...blank(src).matchAll(CALL)].length;
    for (const p of checkSource(src)) problems.push(`${f}:${p.line}: ${p.msg}`);
  }
  return { problems, services: tracerApps.length, files: files.length, sites };
}

// ---------------------------------------------------------------------------
// self-test — throwaway git repos built from fixtures, then mutated.
// ---------------------------------------------------------------------------
function fixtureRepo(files) {
  const root = mkdtempSync(join(tmpdir(), 'client-obs-'));
  for (const [p, body] of Object.entries(files)) {
    mkdirSync(join(root, dirname(p)), { recursive: true });
    writeFileSync(join(root, p), body);
  }
  const quiet = { stdio: ['ignore', 'ignore', 'ignore'] };
  execFileSync('git', ['-C', root, 'init', '-q'], quiet);
  execFileSync('git', ['-C', root, '-c', 'core.autocrlf=false', 'add', '-A'], quiet);
  return root;
}

const TRACER = "dependencies {\n    implementation 'io.micrometer:micrometer-tracing-bridge-otel'\n}\n";
const NO_TRACER = "dependencies {\n    // micrometer-tracing-bridge-otel is not used here\n    implementation 'org.springframework.boot:spring-boot-starter-web'\n}\n";
const S = 'projects/p/apps/svc';
const J = `${S}/src/main/java/x/C.java`;
const cls = (body) => `package x;\nclass C {\n  Object f(io.micrometer.observation.ObservationRegistry r) {\n${body}\n  }\n}\n`;

function selfTest() {
  const cells = [
    ['observed chain passes', { [`${S}/build.gradle`]: TRACER,
      [J]: cls('    return RestClient.builder().baseUrl("http://a").observationRegistry(r).build();') }, 0],
    ['unobserved chain bites (the 2026-11-25 defect)', { [`${S}/build.gradle`]: TRACER,
      [J]: cls('    return RestClient.builder().baseUrl("http://a").build();') }, 1],
    ['WebClient.builder unobserved bites', { [`${S}/build.gradle`]: TRACER,
      [J]: cls('    return WebClient.builder().baseUrl("http://a").build();') }, 1],
    ['RestClient.create bites', { [`${S}/build.gradle`]: TRACER,
      [J]: cls('    return RestClient.create();') }, 1],
    ['assigned builder configured later passes', { [`${S}/build.gradle`]: TRACER,
      [J]: cls('    RestClient.Builder b = RestClient.builder().requestFactory(null);\n    if (true) { b.baseUrl("x"); }\n    b\n        .observationRegistry(r);\n    return b.build();') }, 0],
    ['assigned builder never configured bites', { [`${S}/build.gradle`]: TRACER,
      [J]: cls('    RestClient.Builder b = RestClient.builder().requestFactory(null);\n    return b.build();') }, 1],
    ['registry after a lambda with ";" in the chain passes', { [`${S}/build.gradle`]: TRACER,
      [J]: cls('    return RestClient.builder().defaultStatusHandler(s -> true, (q, s) -> { int a = 1; }).observationRegistry(r).build();') }, 0],
    ['registry on the NEXT statement does not count for a chain', { [`${S}/build.gradle`]: TRACER,
      [J]: cls('    Object o = RestClient.builder().build();\n    Object p = WebClient.builder().observationRegistry(r).build();\n    return o;') }, 1],
    ['call inside a comment is ignored', { [`${S}/build.gradle`]: TRACER,
      [J]: cls('    // previously RestClient.builder().build()\n    /* RestClient.create() */\n    return null;') }, 0],
    ['call inside a string is ignored', { [`${S}/build.gradle`]: TRACER,
      [J]: cls('    return "RestClient.builder() // not code";') }, 0],
    ['"//" inside a URL string does not swallow the line', { [`${S}/build.gradle`]: TRACER,
      [J]: cls('    return RestClient.builder().baseUrl("http://a").build();') }, 1],
    ['service without a tracer is not in the population', {
      [`${S}/build.gradle`]: TRACER, [J]: cls('    return null;'),
      'projects/p/apps/other/build.gradle': NO_TRACER,
      'projects/p/apps/other/src/main/java/x/D.java': cls('    return RestClient.create();') }, 0],
    ['test sources are not in the population', { [`${S}/build.gradle`]: TRACER, [J]: cls('    return null;'),
      [`${S}/src/test/java/x/T.java`]: cls('    return RestClient.create();') }, 0],
    ['untracked file is not in the population', { [`${S}/build.gradle`]: TRACER, [J]: cls('    return null;') }, 0, (root) => {
      writeFileSync(join(root, `${S}/src/main/java/x/U.java`), cls('    return RestClient.create();'));
    }],
    ['zero tracer services bites (fail closed)', { [`${S}/build.gradle`]: NO_TRACER, [J]: cls('    return null;') }, 1],
    ['tracer service with zero Java files bites (fail closed)', { [`${S}/build.gradle`]: TRACER }, 1],
  ];
  const bad = [];
  for (const [name, files, want, mutate] of cells) {
    const root = fixtureRepo(files);
    try {
      if (mutate) mutate(root);
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
const root = ri >= 0 ? argv[ri + 1]
  : execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim();
const res = check(root);
if (res.problems.length) {
  console.error('FAIL: hand-built HTTP client(s) in a trace-exporting service are not observed:');
  for (const p of res.problems) console.error(`  ${p}`);
  console.error('');
  console.error('  Without an ObservationRegistry the call makes no client span and sends no W3C traceparent,');
  console.error('  so the downstream service starts a new trace (platform/observability.md § Tracing).');
  console.error('  Inject the application\'s ObservationRegistry bean and add .observationRegistry(registry) to');
  console.error('  the builder chain — or build the client with ResilienceClientFactory.buildRestClient(…, registry).');
  process.exit(1);
}
console.log(`check-outbound-client-observation: OK — ${res.sites} builder call site(s) in ${res.files} file(s) ` +
  `of ${res.services} trace-exporting service(s), each observed.`);
