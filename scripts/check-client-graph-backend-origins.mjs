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

import { readFileSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { join, dirname } from 'node:path';
import {
  repoRoot,
  buildClientGraph,
  stripComments,
  isServerBoundary,
} from './client-graph.mjs';

const SELF_TEST = process.argv.includes('--self-test');
const ROOT = process.env.CGBO_ROOT ?? repoRoot();
// 🔴 **호출마다** 본다 — 모듈 로드 시점에 굳히면 안 된다. self-test 는 케이스마다
//    `CGBO_ROOT` 를 세팅하므로, 한 번 굳힌 값은 첫 케이스에서 이미 틀린다
//    (증상: 합성 트리에서 `fatal: not a git repository`).
const synthetic = () => !!process.env.CGBO_ROOT;

// --- 술어 -------------------------------------------------------------------
// 🔴 술어가 무엇을 «백엔드 오리진» 으로 보는가 — 그리고 무엇을 일부러 안 보는가
//   문다 : `http(s)://<host>.local` · `http(s)://<host>.sslip.io`
//   안 문다: `localhost[:port]` — 프런트 툴링·문서·테스트 URL 이 정당하게 쓴다.
//          🔴 **선언된 공백**이다(산출물 스캐너는 `localhost` 도 backend 로 센다).
const BACKEND_ORIGIN_RE = /https?:\/\/[A-Za-z0-9.\-]*\.(?:local|sslip\.io)(?=$|[:/?#'"`\s\\])/g;

// 🔵 그래프 순회기는 `client-graph.mjs` 가 소유한다 (TASK-MONO-720 추출).
//    이 파일은 **판정만** 한다 — 그리고 그 판정이 형제 가드와 다른 축이다.
//
// 🔴 **모집단은 여전히 `git ls-files` 다** — 이제 그 호출이 공유 모듈 안에 있을 뿐이다.
//    그러므로 **스테이지 전에 돌리면 다른 질문**이 된다(CLAUDE.md § 「가드를 돌리기 전에
//    스테이지」). 🔴 이 문장을 지우지 마라: `check-ls-files-guard-count.sh` 의 분자는
//    **텍스트로** 세므로, 추출하면서 이 문자열까지 잃으면 «참인 멤버가 더 좁은 술어에
//    떨어지는» 상태가 된다 — 그 파일이 스스로 경고하는 바로 그 실패다.

function run(root) {
  const g = buildClientGraph(root, { synthetic: synthetic() });
  const report = { apps: [], appsScanned: g.appsScanned, clientRoots: 0, reached: 0, bad: [] };

  for (const a of g.apps) {
    const hits = [];
    for (const f of a.reached) {
      let s = a.text.get(f);
      if (s === undefined) {
        try { s = readFileSync(join(root, f), 'utf8'); } catch { continue; }
      }
      // 🔴 `'use server'` 모듈의 본문은 브라우저로 안 간다 — 세지 않는다. (순회는
      //    그래프 모듈에서 이미 멈췄지만, 그 모듈 자체는 `reached` 안에 있다.)
      if (isServerBoundary(s)) continue;
      const found = [...new Set(stripComments(s).match(BACKEND_ORIGIN_RE) ?? [])];
      if (found.length) hits.push({ file: f, origins: found });
    }
    report.apps.push({
      app: a.app, clientRoots: a.roots.length, reached: a.reached.length, hits: hits.length,
    });
    report.clientRoots += a.roots.length;
    report.reached += a.reached.length;
    for (const h of hits) report.bad.push({ app: a.app, ...h });
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
