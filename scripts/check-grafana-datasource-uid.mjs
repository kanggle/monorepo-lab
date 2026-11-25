#!/usr/bin/env node
// -----------------------------------------------------------------------------
// check-grafana-datasource-uid.mjs — a Grafana dashboard may only point at a
// datasource its own project provisions                         (TASK-MONO-838)
// -----------------------------------------------------------------------------
// WHAT THIS GUARDS
//
//   Measured 2026-11-25 UTC: the "Alert Overview" dashboard (`alerts.json`) of
//   iam, ecommerce and wms referenced datasource uid "prometheus" in 11 places
//   each, while all three projects provision their Prometheus datasource as
//   uid "PBFA97CFB590B2093". A Grafana 11.2.0 started with the wms provisioning
//   answered `GET /api/datasources/uid/prometheus` with 404 "Data source not
//   found" — every panel of that dashboard was broken, in three projects, and
//   nothing in the repository could notice (TASK-BE-666 fixed wms; this task
//   fixed the other two and added this check).
//
// PREDICATE (per project p that has projects/p/infra/grafana/dashboards/**.json)
//
//   P = datasources provisioned in projects/p/infra/grafana/provisioning/
//       datasources/*.y(a)ml — for each list item `- name: X`, its `uid:` key at
//       the item's own indentation (nested keys such as derivedFields' `name:`
//       or `datasourceUid:` are not datasources and are not read).
//   R = every value of a `datasource` key anywhere in each dashboard JSON
//       (panel, target, templating, annotation): an object's `uid`, or a bare
//       string (the legacy form, a datasource NAME).
//   Each reference must be in P (uid → P.uids, string → P.names). Exempt:
//       Grafana built-ins (`-- Grafana --`, `-- Mixed --`, `-- Dashboard --`,
//       `grafana`), template variables (`$x`, `${x}`), and null/absent uid.
//   A dashboard's own top-level `uid` is NOT a datasource and is never read —
//       only values under a `datasource` key are.
//   Fail closed: zero dashboard files in the repository, or a project with
//       dashboards but zero provisioned datasources.
//   Population = `git ls-files` (stage before running locally).
//
// WHAT THIS DOES NOT GUARD
//   * Whether the queries name metrics that exist (TASK-MONO-837 / TASK-BE-666:
//     iam dashboards still name metrics nothing exports — separate work).
//   * Whether the datasource's url is reachable.
//
// usage: node scripts/check-grafana-datasource-uid.mjs [--root <dir>] [--self-test]
// -----------------------------------------------------------------------------
import { execFileSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';

const BUILTIN = new Set(['-- Grafana --', '-- Mixed --', '-- Dashboard --', 'grafana']);

function lsFiles(root) {
  // projects/ has ~10^5 tracked paths — far over execFileSync's 1 MiB default buffer (ENOBUFS).
  const out = execFileSync('git', ['-C', root, 'ls-files', '-z', '--', 'projects/*/infra/grafana/**'],
    { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
  return out.split('\0').filter(Boolean);
}

/** Provisioned datasources of one YAML file: [{name, uid}] (item-level keys only). */
export function parseProvisioning(text) {
  const items = [];
  let cur = null;
  let keyIndent = -1;
  for (const raw of text.replace(/\r/g, '').split('\n')) {
    const line = raw.replace(/\s+#.*$/, '');
    const item = /^(\s*)-\s+name:\s*(.+?)\s*$/.exec(line);
    if (item) {
      cur = { name: unquote(item[2]), uid: null };
      items.push(cur);
      keyIndent = item[1].length + 2;
      continue;
    }
    const key = /^(\s*)uid:\s*(.+?)\s*$/.exec(line);
    if (cur && key && key[1].length === keyIndent) cur.uid = unquote(key[2]);
  }
  return items;
}

function unquote(s) {
  return s.replace(/^['"]|['"]$/g, '');
}

/** Every value under a `datasource` key, with a JSON-ish path for the report. */
export function datasourceRefs(json) {
  const refs = [];
  const walk = (o, path) => {
    if (Array.isArray(o)) {
      o.forEach((v, i) => walk(v, `${path}[${i}]`));
    } else if (o && typeof o === 'object') {
      for (const [k, v] of Object.entries(o)) {
        if (k === 'datasource') {
          if (typeof v === 'string') refs.push({ path: `${path}.datasource`, kind: 'name', value: v });
          else if (v && typeof v === 'object' && typeof v.uid === 'string')
            refs.push({ path: `${path}.datasource.uid`, kind: 'uid', value: v.uid });
        }
        walk(v, `${path}.${k}`);
      }
    }
  };
  walk(json, '$');
  return refs;
}

function exempt(value) {
  return BUILTIN.has(value) || value.startsWith('$');
}

export function check(root) {
  const files = lsFiles(root);
  const dashboards = files.filter((f) => /^projects\/[^/]+\/infra\/grafana\/dashboards\/.+\.json$/.test(f));
  const problems = [];
  if (dashboards.length === 0) {
    problems.push('found ZERO tracked dashboards under projects/*/infra/grafana/dashboards — the check would pass having verified nothing');
    return { problems, dashboards: 0, refs: 0 };
  }
  const projects = [...new Set(dashboards.map((f) => f.split('/')[1]))].sort();
  let refCount = 0;
  for (const p of projects) {
    const prov = files.filter((f) =>
      new RegExp(`^projects/${p}/infra/grafana/provisioning/datasources/[^/]+\\.ya?ml$`).test(f));
    const items = prov.flatMap((f) => parseProvisioning(readFileSync(join(root, f), 'utf8')));
    const uids = new Set(items.map((i) => i.uid).filter(Boolean));
    const names = new Set(items.map((i) => i.name));
    if (items.length === 0) {
      problems.push(`projects/${p}: has dashboards but provisions ZERO datasources (infra/grafana/provisioning/datasources/*.yml)`);
      continue;
    }
    for (const f of dashboards.filter((d) => d.split('/')[1] === p)) {
      let json;
      try {
        json = JSON.parse(readFileSync(join(root, f), 'utf8'));
      } catch (e) {
        problems.push(`${f}: not valid JSON (${e.message})`);
        continue;
      }
      for (const r of datasourceRefs(json)) {
        refCount++;
        if (exempt(r.value)) continue;
        const ok = r.kind === 'uid' ? uids.has(r.value) : names.has(r.value);
        if (!ok) {
          problems.push(`${f}  ${r.path} = "${r.value}" — not a datasource ${r.kind} provisioned by projects/${p} ` +
            `(provisioned ${r.kind === 'uid' ? 'uids' : 'names'}: ${[...(r.kind === 'uid' ? uids : names)].sort().join(', ')})`);
        }
      }
    }
  }
  return { problems, dashboards: dashboards.length, refs: refCount, projects: projects.length };
}

// ---------------------------------------------------------------------------
// self-test — throwaway git repos built from fixtures, then mutated.
// ---------------------------------------------------------------------------
function fixtureRepo(files) {
  const root = mkdtempSync(join(tmpdir(), 'grafana-uid-'));
  for (const [p, body] of Object.entries(files)) {
    mkdirSync(join(root, dirname(p)), { recursive: true });
    writeFileSync(join(root, p), body);
  }
  const quiet = { stdio: ['ignore', 'ignore', 'ignore'] };
  execFileSync('git', ['-C', root, 'init', '-q'], quiet);
  execFileSync('git', ['-C', root, '-c', 'core.autocrlf=false', 'add', '-A'], quiet);
  return root;
}

const PROV = `apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    uid: PROM1
  - name: Loki
    type: loki
    uid: loki
    jsonData:
      derivedFields:
        - name: TraceID
          datasourceUid: tempo
`;
const dash = (uid, extra = {}) => JSON.stringify({
  uid: 'my-dashboard', title: 't',
  panels: [{ id: 1, datasource: { type: 'prometheus', uid }, targets: [{ expr: 'up', datasource: { type: 'prometheus', uid } }] }],
  ...extra,
});
const A = 'projects/a/infra/grafana';
const B = 'projects/b/infra/grafana';

function selfTest() {
  const cells = [
    ['provisioned uid passes', { [`${A}/provisioning/datasources/ds.yml`]: PROV, [`${A}/dashboards/d.json`]: dash('PROM1') }, 0],
    ['unprovisioned uid bites (the 2026-11-25 defect)', { [`${A}/provisioning/datasources/ds.yml`]: PROV, [`${A}/dashboards/d.json`]: dash('prometheus') }, 2],
    ['template variable passes', { [`${A}/provisioning/datasources/ds.yml`]: PROV, [`${A}/dashboards/d.json`]: dash('${DS_PROMETHEUS}') }, 0],
    ['Grafana built-in passes', { [`${A}/provisioning/datasources/ds.yml`]: PROV,
      [`${A}/dashboards/d.json`]: JSON.stringify({ uid: 'x', annotations: { list: [{ datasource: { type: 'grafana', uid: '-- Grafana --' } }] } }) }, 0],
    ['dashboard top-level uid is not a datasource', { [`${A}/provisioning/datasources/ds.yml`]: PROV,
      [`${A}/dashboards/d.json`]: JSON.stringify({ uid: 'not-a-datasource', panels: [] }) }, 0],
    ['nested derivedFields name/datasourceUid are not datasources', { [`${A}/provisioning/datasources/ds.yml`]: PROV, [`${A}/dashboards/d.json`]: dash('tempo') }, 2],
    ['legacy string datasource = provisioned name passes', { [`${A}/provisioning/datasources/ds.yml`]: PROV,
      [`${A}/dashboards/d.json`]: JSON.stringify({ uid: 'x', panels: [{ datasource: 'Prometheus' }] }) }, 0],
    ['legacy string datasource = unknown name bites', { [`${A}/provisioning/datasources/ds.yml`]: PROV,
      [`${A}/dashboards/d.json`]: JSON.stringify({ uid: 'x', panels: [{ datasource: 'prometheus' }] }) }, 1],
    ['another project\'s uid does not count', {
      [`${A}/provisioning/datasources/ds.yml`]: PROV,
      [`${B}/provisioning/datasources/ds.yml`]: PROV.replace('PROM1', 'PROM2'),
      [`${B}/dashboards/d.json`]: dash('PROM1') }, 2],
    ['dashboards but no provisioning bites', { [`${A}/dashboards/d.json`]: dash('PROM1') }, 1],
    ['zero dashboards bites (fail closed)', { [`${A}/provisioning/datasources/ds.yml`]: PROV }, 1],
    ['invalid JSON bites', { [`${A}/provisioning/datasources/ds.yml`]: PROV, [`${A}/dashboards/d.json`]: '{ not json' }, 1],
    ['untracked dashboard is not in the population', { [`${A}/provisioning/datasources/ds.yml`]: PROV, [`${A}/dashboards/d.json`]: dash('PROM1') }, 0, (root) => {
      writeFileSync(join(root, `${A}/dashboards/untracked.json`), dash('prometheus'));
    }],
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
  console.error('FAIL: Grafana dashboard datasource reference(s) not provisioned by the dashboard\'s own project:');
  for (const p of res.problems) console.error(`  ${p}`);
  console.error('');
  console.error('  A reference Grafana cannot resolve renders the panel as "Data source not found".');
  console.error('  Point it at a uid listed in that project\'s infra/grafana/provisioning/datasources/*.yml,');
  console.error('  or use a template variable ($datasource) if the dashboard must be portable.');
  process.exit(1);
}
console.log(`check-grafana-datasource-uid: OK — ${res.refs} datasource reference(s) in ${res.dashboards} dashboard(s) ` +
  `across ${res.projects} project(s), each provisioned by its own project.`);
