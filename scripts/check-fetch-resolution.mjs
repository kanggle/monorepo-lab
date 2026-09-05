#!/usr/bin/env node
// =============================================================================
// check-fetch-resolution.mjs
//   — 백엔드로 나가는 `fetch(` 는 **런타임 해석을 지나거나, 왜 안 지나는지를 선언한다**
//     (TASK-MONO-623 / ADR-MONO-067 D2 · ADR-MONO-068 § D6 = B2)
// =============================================================================
// 사용: node scripts/check-fetch-resolution.mjs [--self-test]
//
// -----------------------------------------------------------------------------
// 무엇을 재는가 — **형제 가드와 축이 다르다**
// -----------------------------------------------------------------------------
//   check-client-graph-backend-origins.mjs : «브라우저가 주소를 **아는가**»
//   이 가드                                : «서버가 주소로 **나가는가**»
//   check-demo-resolver-copies.sh          : «앱이 해석기 **구현**을 갖는가»
// 셋 다 초록이어야 D1+D2 가 성립한다. 하나로 다른 하나를 대신할 수 없다.
//
// -----------------------------------------------------------------------------
// 🔴🔴 술어는 **형제 넷의 실제 코드를 읽고** 정했다 — 처음 세운 것은 틀렸다
// -----------------------------------------------------------------------------
// `TASK-MONO-585` 는 console 에서 `fetch(await resolveBackendUrl(...))` 또는
// `fetch(resolvedXxxUrl, ...)` 형태를 썼고, 그래서 첫 술어는 *"fetch 의 인자에
// 해석기 이름이 보여야 한다"* 였다. **형제 셋이 전부 그 모양이 아니다**(2026-09-05 실측):
//
//   auth-forwarder  fetch(target, …)     ← `resolveDemoBackend()` 는 같은 함수 **30줄 위**
//   web-store       fetch(targetUrl, …)  ← 해석은 헬퍼 `buildTargetUrl()` 안
//   fan             fetch(url, init)     ← 해석은 헬퍼 `buildUrl()` 안
//
// 인자만 보는 술어는 **옳게 해석하고 있는 셋을 전부 거짓 빨강**으로 만든다. 그런 가드는
// 고칠 방법이 없어서 결국 꺼지고, 꺼진 가드는 없는 가드다.
//
// ⇒ 술어를 둘로 나눈다:
//
//   (A) **파일 자격** — 절대 URL 로 나가는 `fetch(` 가 있는 파일은, 그 파일이 해석기의
//       export 중 하나를 **참조**하거나 그 자리에 **면제 마커**가 있어야 한다.
//       헬퍼를 거치든 30줄 위에서 부르든 «이 파일은 해석을 안다» 는 참이다.
//
//   (B) **env 직결 금지** — (A) 를 통과했더라도, `fetch` 의 **인자 자체가 env 값을
//       보간**하면(`${env.X}` · `${X_URL}` · `process.env.X`) 그 자리는 **lexical 로
//       해석되었거나 마커가 있어야** 한다.
//       🔴 (B) 가 없으면 (A) 는 «이미 축복받은 파일» 에 두 번째 미해석 fetch 가 들어오는
//          것을 못 잡는다. 그리고 그것이 이 결함의 **실제 도착 모양**이다 — 새 백엔드
//          주소는 언제나 env 에서 온다.
//
// -----------------------------------------------------------------------------
// 🔵 이 가드가 **못 잡는 것** (선언된 공백 — 숨기지 않는다)
// -----------------------------------------------------------------------------
// (A) 로 자격을 얻은 파일 안에서, env 를 **변수에 먼저 담았다가** 그 변수를 fetch 에
// 넘기는 새 자리. (B) 는 인자를 보므로 그 한 단계를 못 따라간다. 데이터 흐름 분석을
// 하려면 타입체커가 필요하고, 그 비용은 이 축이 지금 지는 위험보다 크다.
// 🔴 이것을 «가드가 있으니 괜찮다» 로 읽지 마라 — 잡히는 것은 **직결**뿐이다.
// =============================================================================

import { execFileSync } from 'node:child_process';
import { readFileSync, readdirSync, statSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { join, dirname, sep } from 'node:path';

const SELF_TEST = process.argv.includes('--self-test');
const ROOT = process.env.CFR_ROOT ?? repoRoot();
const PKG = '@demo/backend-resolver';

function repoRoot() {
  return execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim();
}

function listFiles(root) {
  if (process.env.CFR_ROOT) return walk(root, root);
  return execFileSync('git', ['-C', root, 'ls-files'], { encoding: 'utf8', maxBuffer: 64 << 20 })
    .split('\n')
    .filter(Boolean);
}

function walk(dir, root, out = []) {
  let entries;
  try { entries = readdirSync(dir); } catch { return out; }
  for (const e of entries) {
    if (e === 'node_modules' || e === '.next' || e === '.git') continue;
    const p = join(dir, e);
    let st;
    try { st = statSync(p); } catch { continue; }
    if (st.isDirectory()) walk(p, root, out);
    else out.push(p.slice(root.length + 1).split(sep).join('/'));
  }
  return out;
}

// --- 술어의 조각들 -----------------------------------------------------------

/** 주석을 걷어낸다. 🔴 걷지 않으면 판별자가 자기 설명 문구에 걸린다. */
function stripComments(src) {
  return src
    .replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, ' '))  // 줄 번호 보존
    .replace(/(^|[\s({[,;=])\/\/[^\n]*/g, (m, p1) => p1 + ' '.repeat(m.length - p1.length));
}

/**
 * 해석기가 **내보내는 이름들**. 🔵 프로젝트 이름이 아니라 **공유 패키지의 API** 로
 * 술어를 세우므로 project-agnostic 하다(HARDSTOP-03) — 새 앱이 다른 래퍼 이름을 써도
 * 결국 이 넷 중 하나를 부른다.
 * 🔴 `resolveBackendUrl` 은 console 의 래퍼다. 패키지 export 는 아니지만, 「해석을 안다」
 *    는 표시로는 같은 값이라 넣는다 — 빠뜨리면 console 의 자격 파일들이 거짓 빨강이 된다.
 */
const RESOLVER_NAMES = [
  'resolveDemoBackend',
  'resolveDemoBackendState',
  'resolveUpstreamBaseUrl',
  'resolveBackendUrl',
];
const RESOLVER_RE = new RegExp(`\\b(?:${RESOLVER_NAMES.join('|')})\\b`);

/**
 * 면제 선언.
 *
 * 🔴🔴 **줄 수 창으로 찾지 않는다.** 「fetch 위 N줄」 술어는 주석이 N줄보다 길어지는
 * 순간 **조용히 못 찾고**, 그 침묵은 «면제가 없다» 와 구별되지 않는다 — 즉 잘 설명한
 * 자리일수록 빨개진다. 이 가드의 첫 판이 `N=12` 로 쓰였고, 이 티켓의 Edge Case 가
 * 그것을 미리 금지하고 있었다(585 의 마커는 4~5줄, 이 PR 이 형제에 단 것은 7줄).
 *
 * ⇒ **구조로 찾는다**: fetch 바로 위의 **연속된 주석 블록**을 코드 줄이 나올 때까지
 * 거슬러 올라가며 본다. 블록이 100줄이어도 놓치지 않고, 블록 **밖**의 마커는 인정하지
 * 않는다(엉뚱한 자리의 면제가 아래 fetch 를 덮는 것을 막는다).
 */
const MARKER = 'DEMO-URL-EXEMPT:';

/** fetch 인자가 **상대경로 리터럴**인가 — 같은 오리진이므로 이 축의 대상이 아니다. */
const RELATIVE_ARG_RE = /^\s*['"`]\//;

/** fetch 인자가 **env 값을 직접 보간**하는가 (술어 B). */
const ENV_IN_ARG_RE =
  /process\.env\.[A-Za-z_]|\$\{\s*(?:process\.env\.)?[A-Za-z_$][\w$]*\.?[\w$]*(?:_URL|_BASE|_BASE_URL|Url|BaseUrl|IssuerUrl)\b|\$\{\s*[A-Z][A-Z0-9_]*(?:_URL|_BASE|_BASE_URL)\s*\}/;

function collectFetchSites(text) {
  const stripped = stripComments(text);
  const lines = stripped.split('\n');
  const sites = [];
  lines.forEach((line, i) => {
    const re = /\bfetch\s*\(/g;
    let m;
    while ((m = re.exec(line))) {
      // 인자는 같은 줄에 없을 수 있다(형식화된 호출). 다음 두 줄까지 이어 붙인다.
      const arg = (line.slice(m.index + m[0].length) + '\n' +
                   (lines[i + 1] ?? '') + '\n' + (lines[i + 2] ?? '')).trimStart();
      sites.push({ line: i + 1, arg });
    }
  });
  return sites;
}

/** fetch 줄 바로 위의 **연속 주석 블록** 안에 마커가 있는가. */
function hasMarkerAbove(rawLines, lineNo) {
  for (let i = lineNo - 2; i >= 0; i--) {
    const t = rawLines[i].trim();
    if (t === '') continue;                                  // 빈 줄은 블록을 안 끊는다
    if (!(t.startsWith('//') || t.startsWith('*') ||
          t.startsWith('/*') || t.endsWith('*/'))) return false;  // 코드 줄 → 블록 끝
    if (rawLines[i].includes(MARKER)) return true;
  }
  return false;
}

function run(root) {
  const all = listFiles(root);

  // --- (1) 모집단: 해석기를 **의존으로 선언한** 앱 --------------------------
  // 🔴 파일 전체를 grep 하면 안 된다 — 패키지 **자신의 `name` 필드**가 걸려서
  //    `infra/demo/backend-resolver` 가 소비자로 들어온다(2026-09-05 실측, 첫 계수가
  //    실제로 그렇게 5를 냈다). 소비 여부는 dependencies 에만 있다.
  const apps = [];
  for (const f of all.filter((f) => /(^|\/)package\.json$/.test(f))) {
    let j;
    try { j = JSON.parse(readFileSync(join(root, f), 'utf8')); } catch { continue; }
    const dep = (j.dependencies && j.dependencies[PKG]) || (j.devDependencies && j.devDependencies[PKG]);
    if (dep) apps.push(f.replace(/package\.json$/, '').replace(/\/$/, ''));
  }
  apps.sort();

  // --- (2) 사이트 열거 ------------------------------------------------------
  const report = { apps, sites: 0, filtered: 0, relative: 0, qualified: 0, marked: 0, bad: [] };

  for (const app of apps) {
    const src = all.filter(
      (f) =>
        f.startsWith(app + '/') &&
        /\.(ts|tsx)$/.test(f) &&
        !/(^|\/)(node_modules|\.next)\//.test(f) &&
        !/(__tests__|\.test\.|\.spec\.)/.test(f),
    );
    for (const f of src) {
      let text;
      try { text = readFileSync(join(root, f), 'utf8'); } catch { continue; }
      // 🔴 «걸러진 건수» 를 함께 센다. 0이면 술어가 이상한 것이다 —
      //    `refetch(` / `prefetch(` 가 실재하기 때문이다(실측: 65 → 39).
      report.filtered += (text.match(/fetch\s*\(/g) || []).length -
                         (text.match(/\bfetch\s*\(/g) || []).length;

      const rawLines = text.split('\n');
      const fileQualifies = RESOLVER_RE.test(stripComments(text));
      for (const site of collectFetchSites(text)) {
        report.sites++;
        if (RELATIVE_ARG_RE.test(site.arg)) { report.relative++; continue; }

        const marked = hasMarkerAbove(rawLines, site.line);
        const lexical = RESOLVER_RE.test(site.arg.split('\n').slice(0, 2).join('\n'));
        const envDirect = ENV_IN_ARG_RE.test(site.arg.split('\n')[0]);

        // (B) env 직결은 파일 자격으로 면제되지 않는다.
        if (envDirect && !lexical && !marked) {
          report.bad.push({
            file: f, line: site.line, why: 'env-직결', arg: site.arg.split('\n')[0].slice(0, 70),
          });
          continue;
        }
        // (A) 파일 자격 또는 마커.
        if (!fileQualifies && !marked) {
          report.bad.push({
            file: f, line: site.line, why: '파일이 해석기를 모름', arg: site.arg.split('\n')[0].slice(0, 70),
          });
          continue;
        }
        if (marked) report.marked++;
        else report.qualified++;
      }
    }
  }
  return report;
}

// --- 대역 (self-test) --------------------------------------------------------
function selfTest() {
  const tmp = join(process.env.TMPDIR ?? process.env.TEMP ?? '/tmp', 'cfr-selftest-' + process.pid);
  const PKGJSON = JSON.stringify({ name: 'app', dependencies: { [PKG]: 'link:../x' } });
  const OTHER = JSON.stringify({ name: 'other', dependencies: { [PKG]: 'link:../x' } });
  const SELFNAMED = JSON.stringify({ name: PKG, version: '0.0.0' });
  const NL = String.fromCharCode(10);
  const LONG_MARKER =
    '// DEMO-URL-EXEMPT: long — 이유가 길다' + NL +
    ('// filler' + NL).repeat(30) +
    'export async function f(){ return fetch(`${env.IAM_BASE_URL}/t`, {}); }' + NL;
  const STALE_MARKER =
    '// DEMO-URL-EXEMPT: stale — 다른 자리의 선언' + NL +
    'const unrelated = 1;' + NL +
    'export async function f(){ return fetch(`${env.IAM_BASE_URL}/t`, {}); }' + NL;

  const cases = [
    {
      name: '(a) env 를 fetch 인자에 직결 · 해석도 마커도 없음  ->  문다',
      files: {
        'a/package.json': PKGJSON, 'b/package.json': OTHER,
        'a/src/x.ts': 'export async function f(){ return fetch(`${env.IAM_BASE_URL}/t`, {}); }\n',
      },
      expectBad: 1,
    },
    {
      name: '(a2) 같은 자리에 마커가 있다  ->  안 문다',
      files: {
        'a/package.json': PKGJSON, 'b/package.json': OTHER,
        'a/src/x.ts': '// DEMO-URL-EXEMPT: oidc-issuer\nexport async function f(){ return fetch(`${env.IAM_BASE_URL}/t`, {}); }\n',
      },
      expectBad: 0,
    },
    {
      name: '(a3) 🔴 파일이 해석기를 알아도 **env 직결은 면제 안 된다**  ->  문다',
      files: {
        'a/package.json': PKGJSON, 'b/package.json': OTHER,
        'a/src/x.ts': 'import { resolveUpstreamBaseUrl } from "p";\nexport async function g(){ await resolveUpstreamBaseUrl(); }\nexport async function f(){ return fetch(`${env.IAM_BASE_URL}/t`, {}); }\n',
      },
      expectBad: 1,
    },
    {
      name: '(b) 🔵 해석이 **헬퍼 안**에 있어도 안 문다 (형제 셋의 실제 모양)',
      files: {
        'a/package.json': PKGJSON, 'b/package.json': OTHER,
        'a/src/x.ts': 'import { resolveUpstreamBaseUrl } from "p";\nasync function build(){ return (await resolveUpstreamBaseUrl())+"/x"; }\nexport async function f(){ const url = await build(); return fetch(url, {}); }\n',
      },
      expectBad: 0,
    },
    {
      // 🔴🔴 이 칸이 첫 판(`N=12` 줄 창)의 결함을 고정한다. 잘 설명한 자리일수록
      //    주석이 길어지고, 줄 창 술어는 **거기서** 조용히 못 찾는다.
      name: '(b2) 마커 블록이 31줄이어도 찾는다 (줄 수 창이 아니라 구조)',
      files: {
        'a/package.json': PKGJSON, 'b/package.json': OTHER,
        'a/src/x.ts': LONG_MARKER,
      },
      expectBad: 0,
    },
    {
      // 🔴 블록 **밖**의 마커는 인정하지 않는다 — 엉뚱한 자리의 면제가 아래 fetch 를
      //    덮으면, 면제는 «그 자리의 선언» 이 아니라 파일 전체의 통행증이 된다.
      name: '(b3) 마커와 fetch 사이에 코드 줄이 있으면 인정 안 한다  ->  문다',
      files: {
        'a/package.json': PKGJSON, 'b/package.json': OTHER,
        'a/src/x.ts': STALE_MARKER,
      },
      expectBad: 1,
    },
    {
      name: '(c) 상대경로 리터럴은 이 축의 대상이 아니다  ->  안 문다',
      files: {
        'a/package.json': PKGJSON, 'b/package.json': OTHER,
        'a/src/x.ts': 'export async function f(){ return fetch("/api/x", {}); }\n',
      },
      expectBad: 0,
    },
    {
      name: '(d) 해석기를 모르는 파일의 절대 fetch  ->  문다',
      files: {
        'a/package.json': PKGJSON, 'b/package.json': OTHER,
        'a/src/x.ts': 'export async function f(){ return fetch(someUrl, {}); }\n',
      },
      expectBad: 1,
    },
    {
      name: "(e) 🔴 `refetch(` 는 세지 않는다 — 그리고 «걸러진 건수» 가 0이 아니다",
      files: {
        'a/package.json': PKGJSON, 'b/package.json': OTHER,
        'a/src/x.ts': 'export function f(q){ return q.refetch(); }\n',
      },
      expectBad: 0, expectSites: 0, expectFilteredAtLeast: 1,
    },
    {
      name: '(f) 🔴 패키지 **자신의 `name`** 은 소비자가 아니다',
      files: {
        'a/package.json': PKGJSON, 'b/package.json': OTHER,
        'r/package.json': SELFNAMED,
        'r/src/impl.ts': 'export async function q(){ return fetch(base+"/status", {}); }\n',
        'a/src/x.ts': 'export async function f(){ return fetch("/api/x", {}); }\n',
      },
      expectBad: 0, expectApps: 2,
    },
    {
      name: '(g) 🔴 주석 안의 fetch 는 세지 않는다 (판별자가 설명 문구에 안 걸린다)',
      files: {
        'a/package.json': PKGJSON, 'b/package.json': OTHER,
        'a/src/x.ts': '// 예: fetch(`${env.IAM_BASE_URL}/t`) 처럼 쓰면 안 된다\nexport const k = 1;\n',
      },
      expectBad: 0, expectSites: 0,
    },
  ];

  let failed = 0;
  for (const c of cases) {
    rmSync(tmp, { recursive: true, force: true });
    for (const [rel, body] of Object.entries(c.files)) {
      const p = join(tmp, rel);
      mkdirSync(dirname(p), { recursive: true });
      writeFileSync(p, body, 'utf8');
    }
    process.env.CFR_ROOT = tmp;
    const r = run(tmp);
    delete process.env.CFR_ROOT;
    const ok =
      r.bad.length === c.expectBad &&
      (c.expectSites === undefined || r.sites === c.expectSites) &&
      (c.expectApps === undefined || r.apps.length === c.expectApps) &&
      (c.expectFilteredAtLeast === undefined || r.filtered >= c.expectFilteredAtLeast);
    if (ok) console.log(`  ok: ${c.name}  (bad=${r.bad.length}, sites=${r.sites}, apps=${r.apps.length}, filtered=${r.filtered})`);
    else { failed++; console.log(`  ✗  ${c.name}  (bad=${r.bad.length} 기대 ${c.expectBad}, sites=${r.sites}, apps=${r.apps.length}, filtered=${r.filtered})`); }
  }
  rmSync(tmp, { recursive: true, force: true });
  return failed;
}

// --- main --------------------------------------------------------------------
const say = (m) => console.log(`[fetch-resolution] ${m}`);

if (SELF_TEST) {
  say('--self-test — 합성 트리에서 무는지/안 무는지 확인합니다');
  const failed = selfTest();
  if (failed) { say(`✗ self-test ${failed}건 실패`); process.exit(1); }
  say('ok — self-test 전부 통과');
  process.exit(0);
}

const r = run(ROOT);

// 🔴 하한은 «위반 수» 가 아니라 **«계측기가 살아 있는가»** 에 건다. 위반 0건은 정당하게
//    참일 수 있지만(그게 목표다) 앱 0개·사이트 0건은 열거가 죽은 것이다.
const FLOOR_APPS = Number(process.env.CFR_FLOOR_APPS ?? 3);
const FLOOR_SITES = Number(process.env.CFR_FLOOR_SITES ?? 15);

say(`소비자 앱 ${r.apps.length}개 · fetch 사이트 ${r.sites}건 ` +
    `(상대경로 ${r.relative} · 파일자격 ${r.qualified} · 마커 ${r.marked} · 미분류 ${r.bad.length})`);
say(`  «refetch(» 등으로 걸러진 건수 ${r.filtered} — 0이면 \\b 술어가 이상한 것입니다`);
for (const a of r.apps) say(`  - ${a}`);

if (r.apps.length < FLOOR_APPS) {
  say(`✗ 소비자 앱을 ${r.apps.length}개밖에 못 찾았습니다 (하한 ${FLOOR_APPS}) — 열거가 깨졌습니다.`);
  say('  → 0건을 「위반 없음」으로 보고하지 않습니다. 계측기부터 보세요.');
  say(`  → 소비 여부는 dependencies 로 판정합니다. 패키지 자신의 name 필드가 아닙니다.`);
  process.exit(2);
}
if (r.sites < FLOOR_SITES) {
  say(`✗ fetch 사이트를 ${r.sites}건밖에 못 찾았습니다 (하한 ${FLOOR_SITES}) — 열거가 깨졌습니다.`);
  process.exit(2);
}

if (r.bad.length) {
  say('✗ 백엔드로 나가는데 해석도 선언도 없는 fetch 가 있습니다:');
  for (const b of r.bad) say(`    ${b.file}:${b.line}  [${b.why}]  ${b.arg}`);
  say('  → 처방은 둘 중 하나입니다:');
  say(`     ① 그 주소를 해석기에 통과시킨다(${RESOLVER_NAMES.join(' / ')})`);
  say(`     ② 지나지 않는 **이유**를 그 자리에 선언한다 — \`// ${MARKER} <태그> — <이유>\``);
  say('  → 증상은 조용합니다: 데모 배포에서 **그 화면만** 죽고 나머지가 멀쩡해 원인이 안 보입니다.');
  process.exit(1);
}

say(`ok — 사이트 ${r.sites}건 전부 분류됨 (미분류 0)`);
