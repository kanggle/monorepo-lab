#!/usr/bin/env node
// =============================================================================
// check-client-graph-backend-origins.mjs
//   — 브라우저가 백엔드 주소를 **알게 되는 것**을 막는다 (ADR-MONO-067 D1)
// =============================================================================
// 사용: node scripts/check-client-graph-backend-origins.mjs [--self-test]
//
// -----------------------------------------------------------------------------
// 🔴🔴 왜 «선언» 이 아니라 «도달» 을 재는가 — 그 차이가 이 가드의 존재 이유다
// -----------------------------------------------------------------------------
// `TASK-MONO-585` 착수 실측(2026-09-05): console-web 의 `shared/config/env.ts` 를
// 임포트하는 파일 **20개 중 `"use client"` 는 0개**였다. 선언 경계만 보면 완벽하다.
// 그런데 같은 커밋의 산출물에는 그 모듈의 백엔드 URL **12개가 전부** 클라이언트 청크에
// 있었다(한 청크 `6921-*.js`, 콘솔 입구 3라우트에 실림).
//
// 이유는 단순하다: 클라이언트 컴포넌트가 **몇 다리 건너** 임포트하면 그것으로 충분하다.
//   `<RetryButton>`('use client') → `use-domain-health` → `domain-health-api` → `env.ts`
// 중간 세 파일 어디에도 `"use client"` 는 없다.
//
// ⇒ **선언 경계 ≠ 번들 경계.** 이 가드는 `'use client'` 파일들을 뿌리로 잡고 임포트를
//   **전이적으로** 따라가, 그렇게 닿는 모듈 안에 백엔드 오리진 리터럴이 있으면 문다.
//   착수 전 트리에 대고 돌리면 빨갛고, 이 티켓의 수정 뒤에 돌리면 초록이다.
//
// -----------------------------------------------------------------------------
// 🔵 산출물 스캐너(`scan-client-bundle-origins.mjs`)와 무엇이 다른가
// -----------------------------------------------------------------------------
// 그쪽이 **권위**다 — 실제로 구워진 `.next/static` 을 센다. 그러나 빌드가 필요하고
// (console-web 은 수 분), 그래서 PR 마다 돌릴 수 없다. 이 가드는 **소스만 읽는다**:
// 같은 축을 싸게, 그리고 «규칙을 어긴 커밋» 이 들어오는 순간에 문다.
// 🔴 그러니 이 가드의 초록을 「산출물이 깨끗하다」로 읽지 마라. 그것은 빌드가 말한다.
//
// -----------------------------------------------------------------------------
// 🔴 술어가 무엇을 «백엔드 오리진» 으로 보는가 — 그리고 무엇을 일부러 안 보는가
// -----------------------------------------------------------------------------
//   문다 : `http(s)://<host>.local`  ·  `http(s)://<host>.sslip.io`
//          앞은 Local Network Convention 의 호스트명(`${DEMO_DOMAIN:-local}` 의 기본값),
//          뒤는 데모 인스턴스의 실제 도메인이다. 둘 다 «브라우저가 알면 안 되는 주소» 다.
//   안 문다: `localhost[:port]`. 프런트 툴링·문서·테스트 URL 이 정당하게 쓰고, 그것까지
//          물면 이 가드는 그날로 꺼진다(그리고 꺼진 가드는 없는 가드다). 🔴 이것은
//          **선언된 공백**이다 — 산출물 스캐너는 `localhost` 도 backend 로 센다.
//
// 🔴 주석은 **먼저 걷어낸다.** 걷지 않으면 이 파일의 형제인 `@demo/backend-resolver` 가
//    자기 JSDoc 의 예시(`http://ecommerce.13-125-1-2.sslip.io`)로 자신을 고발한다 —
//    이 저장소가 이미 이름 붙여 둔 실패다: **판별자가 자기 설명 문구에 걸린다**
//    (`check-demo-resolver-copies.sh` 가 2026-09-02 에 정확히 그것을 밟았다).
//    그리고 번들러도 주석을 지우므로, 주석을 세는 것은 **틀린 것을 세는 것**이다.
// =============================================================================

import { execFileSync } from 'node:child_process';
import { readFileSync, readdirSync, statSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { join, dirname, sep } from 'node:path';

const SELF_TEST = process.argv.includes('--self-test');
const ROOT = process.env.CGBO_ROOT ?? repoRoot();

function repoRoot() {
  return execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim();
}

/** 트리의 파일 목록. `CGBO_ROOT` 로 합성 트리를 가리킬 때는 git 을 쓰지 않는다. */
function listFiles(root) {
  if (process.env.CGBO_ROOT) return walk(root, root);
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

// --- 술어 -------------------------------------------------------------------
const BACKEND_ORIGIN_RE = /https?:\/\/[A-Za-z0-9.\-]*\.(?:local|sslip\.io)(?=$|[:/?#'"`\s\\])/g;

/** 문자열/정규식 안의 `//` 를 주석으로 오인하지 않게, 아주 보수적으로 지운다. */
function stripComments(src) {
  return src
    .replace(/\/\*[\s\S]*?\*\//g, '')            // 블록 주석
    .replace(/(^|[\s({[,;=])\/\/[^\n]*/g, '$1'); // 줄 주석 (URL 의 `://` 는 앞이 `:` 라 안 걸린다)
}

const IMPORT_RE =
  /(?:^|[\s;}])(?:import|export)\s[^;]*?from\s*['"]([^'"]+)['"]|(?:^|[^\w.])import\s*\(\s*['"]([^'"]+)['"]\s*\)/g;

const EXT_ORDER = ['.ts', '.tsx', '.js', '.jsx', '.mjs'];

/**
 * 저장소 **상대** POSIX 경로끼리의 결합. 🔴 `path.resolve` 를 쓰면 안 된다 — 그것은
 * 결과를 **절대 경로**로 만들고, 그러면 `git ls-files` 가 준 상대 경로 집합과 영원히
 * 매치되지 않는다. 즉 상대 임포트(`../hooks/x`)가 **하나도 해석되지 않는데** 가드는
 * 조용히 초록이 된다.
 *
 * 🔴🔴 이것이 이 가드의 첫 판에서 실제로 일어났다 (2026-09-05, bite 테스트가 잡았다):
 * 착수 전 트리에 대고 돌렸을 때 console-web 이 `hits=0` 이었다 — 같은 커밋의 산출물에
 * 백엔드 URL 12개가 **있는데도**. 그 앱의 누출 경로가 전부 상대 임포트였기 때문이다.
 * ⇒ **가드는 무는지 확인하기 전까지 무는 것이 아니다.**
 */
function joinRel(fromDir, spec) {
  const parts = fromDir === '' ? [] : fromDir.split('/');
  for (const seg of spec.split('/')) {
    if (seg === '' || seg === '.') continue;
    if (seg === '..') parts.pop();
    else parts.push(seg);
  }
  return parts.join('/');
}

function resolveSpec(spec, fromFile, aliasBase, files) {
  let base = null;
  if (spec.startsWith('@/')) base = joinRel(aliasBase, spec.slice(2));
  else if (spec.startsWith('./') || spec.startsWith('../')) {
    const dir = fromFile.includes('/') ? fromFile.slice(0, fromFile.lastIndexOf('/')) : '';
    base = joinRel(dir, spec);
  } else if (spec === '@demo/backend-resolver') base = 'infra/demo/backend-resolver/src/index';
  else return null; // node_modules / next builtins — 이 가드의 범위 밖
  for (const ext of ['', ...EXT_ORDER, ...EXT_ORDER.map((e) => '/index' + e)]) {
    if (files.has(base + ext)) return base + ext;
  }
  return null;
}

function hasTopDirective(src, name) {
  // 파일 **맨 위**의 디렉티브만 본다. 함수 본문 안의 `'use server'` 는 모듈 경계가 아니다.
  const head = src.slice(0, 400);
  return new RegExp(`(^|\\n)\\s*['"]use ${name}['"]\\s*;?\\s*(\\n|$)`).test(head);
}

const isClientRoot = (src) => hasTopDirective(src, 'client');

/**
 * 🔴🔴 `'use server'` 는 **클라이언트 그래프의 끝**이다 — 여기서 순회를 멈춘다.
 *
 * Server Action 모듈은 클라이언트 컴포넌트가 임포트해도 코드가 브라우저로 가지 않는다.
 * Next 가 그 임포트를 **참조 스텁**으로 바꾸고, 실제 본문은 서버에만 남는다(그래서
 * `'use server'` 모듈은 async 함수 외에는 내보낼 수 없다 — 값이 건너갈 길이 없다).
 *
 * 🔴 이 규칙이 없으면 이 가드는 **fan-platform-web 을 거짓으로 고발한다** (2026-09-05
 * 실측: `FollowButton`('use client') → `follow/api/actions.ts`('use server') →
 * `shared/config/env.ts` 로 `http://iam.local`·`http://fan-platform.local` 에 «닿는다»).
 * 그런데 fan 의 실제 산출물은 깨끗하다 — `TASK-MONO-586` 이 그것을 재서 랜딩했다.
 * ⇒ 남의 프로젝트를 못 고치게 만드는 거짓 빨강이었고, 술어가 번들러의 규칙 하나를
 *   빠뜨린 것이지 fan 이 틀린 것이 아니었다.
 */
const isServerBoundary = (src) => hasTopDirective(src, 'server');

function run(root) {
  const all = listFiles(root);
  const files = new Set(all);
  const apps = [
    ...new Set(
      all
        .filter((f) => /(?:^|\/)next\.config\.[a-z]+$/.test(f))
        .map((f) => f.replace(/(?:^|\/)next\.config\.[a-z]+$/, '') || '.'),
    ),
  ].sort();

  const report = { apps: [], appsScanned: apps.length, clientRoots: 0, reached: 0, bad: [] };

  for (const app of apps) {
    const appDir = app === '.' ? '' : app + '/';
    const aliasBase = appDir + 'src';
    const src = all.filter(
      (f) =>
        f.startsWith(appDir) &&
        /\.(ts|tsx)$/.test(f) &&
        !/(^|\/)(node_modules|\.next)\//.test(f) &&
        !/(__tests__|\.test\.|\.spec\.)/.test(f),
    );
    const roots = [];
    const text = new Map();
    for (const f of src) {
      let s;
      try { s = readFileSync(join(root, f), 'utf8'); } catch { continue; }
      text.set(f, s);
      if (isClientRoot(s)) roots.push(f);
    }

    // --- 전이 닫힘 ----------------------------------------------------------
    const seen = new Set(roots);
    const queue = [...roots];
    while (queue.length) {
      const f = queue.shift();
      let s = text.get(f);
      if (s === undefined) {
        try { s = readFileSync(join(root, f), 'utf8'); } catch { continue; }
        text.set(f, s);
      }
      // 🔴 `'use server'` 모듈에서는 더 들어가지 않는다 — 위 `isServerBoundary` 참조.
      //    (뿌리 자신이 `'use server'` 일 수는 없다: 뿌리는 `'use client'` 로 골랐다.)
      if (isServerBoundary(s)) continue;
      const body = stripComments(s);
      IMPORT_RE.lastIndex = 0;
      let m;
      while ((m = IMPORT_RE.exec(body))) {
        const spec = m[1] ?? m[2];
        if (!spec) continue;
        const target = resolveSpec(spec, f, aliasBase, files);
        if (target && !seen.has(target)) { seen.add(target); queue.push(target); }
      }
    }

    // --- 판정 ---------------------------------------------------------------
    const hits = [];
    for (const f of seen) {
      let s = text.get(f);
      if (s === undefined) {
        try { s = readFileSync(join(root, f), 'utf8'); } catch { continue; }
      }
      // 🔴 `'use server'` 모듈의 본문은 브라우저로 안 간다 — 세지 않는다. (순회는 위에서
      //    이미 멈췄지만, 이 모듈 자체는 `seen` 안에 있으므로 여기서도 걸러야 한다.)
      if (isServerBoundary(s)) continue;
      const found = [...new Set(stripComments(s).match(BACKEND_ORIGIN_RE) ?? [])];
      if (found.length) hits.push({ file: f, origins: found });
    }

    report.apps.push({ app, clientRoots: roots.length, reached: seen.size, hits: hits.length });
    report.clientRoots += roots.length;
    report.reached += seen.size;
    for (const h of hits) report.bad.push({ app, ...h });
  }
  return report;
}

// --- 대역 (self-test) --------------------------------------------------------
function selfTest() {
  const tmp = join(process.env.TMPDIR ?? process.env.TEMP ?? '/tmp', 'cgbo-selftest-' + process.pid);
  const cases = [
    {
      name: '(a) 클라이언트 뿌리 → (2다리) → .local 리터럴  ->  문다',
      files: {
        'app/next.config.js': 'module.exports = {}\n',
        'app/src/ui/Button.tsx': "'use client';\nimport { hook } from '@/hooks/h';\nexport const B = () => hook();\n",
        'app/src/hooks/h.ts': "import { cfg } from '@/config/env';\nexport const hook = () => cfg;\n",
        'app/src/config/env.ts': "export const cfg = { base: 'http://iam.local/api/admin' };\n",
      },
      expectBad: 1,
    },
    {
      name: '(b) 같은 리터럴이 **주석에만** 있다  ->  안 문다 (판별자가 설명 문구에 안 걸린다)',
      files: {
        'app/next.config.js': 'module.exports = {}\n',
        'app/src/ui/Button.tsx': "'use client';\nimport { hook } from '@/hooks/h';\nexport const B = () => hook();\n",
        'app/src/hooks/h.ts': "import { cfg } from '@/config/env';\nexport const hook = () => cfg;\n",
        'app/src/config/env.ts': "// 예: http://iam.local/api/admin — 설명일 뿐이다\nexport const cfg = { base: process.env.X };\n",
      },
      expectBad: 0,
    },
    {
      name: '(c) 리터럴이 **클라이언트에서 안 닿는** 모듈에 있다  ->  안 문다 (서버 전용은 정당하다)',
      files: {
        'app/next.config.js': 'module.exports = {}\n',
        'app/src/ui/Button.tsx': "'use client';\nexport const B = () => null;\n",
        'app/src/config/env.ts': "export const cfg = { base: 'http://iam.local/api/admin' };\n",
      },
      expectBad: 0,
    },
    {
      // 🔴🔴 이 칸이 첫 판의 결함을 고정한다 — 위 (a) 는 `@/` 임포트만 썼고, 그래서
      //    상대 임포트 해석이 통째로 죽어 있는 채로 통과했다. 두 모양을 **따로** 잰다.
      name: '(a2) 같은 누출을 **상대 임포트**로만 만든다  ->  문다 (해석기가 죽으면 여기서 빨개진다)',
      files: {
        'app/next.config.js': 'module.exports = {}\n',
        'app/src/ui/Button.tsx':
          "'use client';\nimport { hook } from '../hooks/h';\nexport const B = () => hook();\n",
        'app/src/hooks/h.ts':
          "import { cfg } from '../config/env';\nexport const hook = () => cfg;\n",
        'app/src/config/env.ts':
          "export const cfg = { base: 'http://iam.local/api/admin' };\n",
      },
      expectBad: 1,
    },
    {
      // 🔴🔴 fan-platform-web 이 이 모양이다. 이 칸이 없으면 이 가드는 남의 프로젝트를
      //    **거짓으로** 고발하고, 그 빨강은 고칠 방법이 없다(fan 의 산출물은 깨끗하다).
      name: "(a3) 같은 사슬이지만 중간이 `'use server'`  ->  안 문다 (Server Action 은 그래프의 끝)",
      files: {
        'app/next.config.js': 'module.exports = {}\n',
        'app/src/ui/Button.tsx':
          "'use client';\nimport { act } from '@/actions/a';\nexport const B = () => act();\n",
        'app/src/actions/a.ts':
          "'use server';\nimport { cfg } from '@/config/env';\nexport async function act() { return cfg; }\n",
        'app/src/config/env.ts':
          "export const cfg = { base: 'http://iam.local/api/admin' };\n",
      },
      expectBad: 0,
    },
    {
      name: '(d) sslip.io 데모 도메인도 같은 축이다  ->  문다',
      files: {
        'app/next.config.js': 'module.exports = {}\n',
        'app/src/ui/Button.tsx': "'use client';\nimport { u } from '@/config/env';\nexport const B = () => u;\n",
        'app/src/config/env.ts': "export const u = 'http://iam.13-1-2-3.sslip.io';\n",
      },
      expectBad: 1,
    },
    {
      name: '(e) 뿌리가 0개면 **판정 불가**다 — 통과로 읽지 않는다',
      files: {
        'app/next.config.js': 'module.exports = {}\n',
        'app/src/config/env.ts': "export const cfg = { base: 'http://iam.local' };\n",
      },
      expectBad: 0,
      expectRoots: 0,
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
    process.env.CGBO_ROOT = tmp;
    const r = run(tmp);
    delete process.env.CGBO_ROOT;
    const okBad = r.bad.length === c.expectBad;
    const okRoots = c.expectRoots === undefined || r.clientRoots === c.expectRoots;
    if (okBad && okRoots) {
      console.log(`  ok: ${c.name}  (bad=${r.bad.length}, roots=${r.clientRoots})`);
    } else {
      failed++;
      console.log(`  ✗  ${c.name}  (bad=${r.bad.length} 기대 ${c.expectBad}, roots=${r.clientRoots})`);
    }
  }
  rmSync(tmp, { recursive: true, force: true });
  return failed;
}

// --- main --------------------------------------------------------------------
const say = (m) => console.log(`[client-graph-origins] ${m}`);

if (SELF_TEST) {
  say('--self-test — 합성 트리에서 무는지/안 무는지 확인합니다');
  const failed = selfTest();
  if (failed) { say(`✗ self-test ${failed}건 실패`); process.exit(1); }
  say('ok — self-test 전부 통과');
  process.exit(0);
}

const r = run(ROOT);

// 🔴 하한은 **판정 대상 수**가 아니라 **계측기가 살아 있는가**에 건다. 「위반 0건」은
//    정당하게 참일 수 있지만(그것이 목표다), 뿌리 0개·도달 0개는 순회가 죽은 것이다.
const FLOOR_APPS = Number(process.env.CGBO_FLOOR_APPS ?? 3);
const FLOOR_ROOTS = Number(process.env.CGBO_FLOOR_ROOTS ?? 20);
const FLOOR_REACHED = Number(process.env.CGBO_FLOOR_REACHED ?? 60);

for (const a of r.apps) {
  say(`  ${a.app}  client roots=${a.clientRoots}  reached=${a.reached}  hits=${a.hits}`);
}

if (r.appsScanned < FLOOR_APPS) {
  say(`✗ Next 앱을 ${r.appsScanned}개밖에 못 찾았습니다 (하한 ${FLOOR_APPS}) — 열거가 깨졌습니다.`);
  say('  → 0건을 「위반 없음」으로 보고하지 않습니다. 계측기부터 보세요.');
  process.exit(2);
}
if (r.clientRoots < FLOOR_ROOTS || r.reached < FLOOR_REACHED) {
  say(`✗ 순회가 빈약합니다: client roots=${r.clientRoots} (하한 ${FLOOR_ROOTS}) · reached=${r.reached} (하한 ${FLOOR_REACHED}).`);
  say("  → `'use client'` 탐지나 임포트 해석이 형태를 놓쳤을 때 이 가드는 **조용히 초록**이 됩니다.");
  process.exit(2);
}

if (r.bad.length) {
  say('✗ 클라이언트 그래프에서 백엔드 오리진 리터럴에 닿습니다:');
  for (const b of r.bad) say(`    ${b.file}  →  ${b.origins.join(' · ')}`);
  say('  → 브라우저가 백엔드 주소를 알게 됩니다(ADR-MONO-067 D1 이 금지하는 것).');
  say('  → 처방은 값을 바꾸는 것이 아니라 **모듈을 가르는 것**입니다: 서버 전용 값은');
  say('     클라이언트 컴포넌트가 (몇 다리 건너라도) 임포트하지 않는 모듈에 두세요.');
  say('  → 실제로 구워진 산출물은 `node scripts/scan-client-bundle-origins.mjs <app> <label>` 로 재세요.');
  process.exit(1);
}

say(`ok — 앱 ${r.appsScanned}개 · 클라이언트 뿌리 ${r.clientRoots}개에서 도달하는 ${r.reached}개 모듈에 백엔드 오리진 리터럴 0건`);
