#!/usr/bin/env node
// =============================================================================
// check-client-graph-server-only.mjs
//   — 서버 전용 모듈이 **클라이언트 번들로 새는 것**을 막는다 (TASK-MONO-720)
// =============================================================================
// 사용: node scripts/check-client-graph-server-only.mjs [--self-test]
//
// 모집단은 `git ls-files` 다(`client-graph.mjs` 가 준다) — 커밋될 것을 묻는 가드이므로.
// 🔴 그래서 **스테이지 전에 돌리면 다른 질문**이 된다(CLAUDE.md § 「가드를 돌리기 전에 스테이지」).
//
// -----------------------------------------------------------------------------
// 🔴🔴 왜 이 가드가 있는가 — 2026-09-22 에 실제로 밟았다
// -----------------------------------------------------------------------------
// `TASK-MONO-719` 구현 중 `next build` 가 이렇게 죽었다:
//
//   You're importing a component that needs "next/headers".
//   trace: session.ts → DomainTenantGate.tsx → index.ts(배럴) → OrdersScreen.tsx
//
// `OrdersScreen` 은 `'use client'` 인데 위젯 **배럴**에서 형제 컴포넌트를 불렀고, 그
// 배럴은 서버 전용 `DomainTenantGate` 도 내보낸다 ⇒ 서버 코드가 클라이언트 번들로 딸려 왔다.
//
// 🔴 그때 **초록이었던 것들**: vitest **316파일·3552칸** · `tsc --noEmit` · `next lint`.
//    vitest 는 번들 경계를 세우지 않고, `tsc` 도 `eslint` 도 «이 모듈이 클라이언트
//    번들에 들어가는가» 를 묻지 않는다. 그 질문을 하는 것은 **번들러뿐**이다.
// 🔴 그리고 CI 는 console-web 의 `next build` 를 **돌리지 않는다**(비용: 수 분).
//    ⇒ 로컬에서 안 잡았으면 그대로 초록으로 머지됐다.
//
// -----------------------------------------------------------------------------
// 🔵 형제 가드(`check-client-graph-backend-origins.mjs`)와 무엇이 다른가
// -----------------------------------------------------------------------------
// **그래프는 같고 잎이 다르다.** 저쪽은 «브라우저가 백엔드 **주소**를 아는가»,
// 이쪽은 «브라우저가 서버 전용 **능력**을 끌어오는가» 다. 축이 다르면 **처방이 다르므로**
// 메시지를 합치지 않는다 — 합치면 읽는 사람이 엉뚱한 파일을 고친다.
// 🔵 순회기는 `client-graph.mjs` 하나다(사본 금지 — `check-demo-resolver-copies.sh` 참조).
//
// -----------------------------------------------------------------------------
// 🔴 금지 잎의 목록은 **이 코퍼스에서 셌다**, 내가 아는 목록이 아니다 (AC-1)
// -----------------------------------------------------------------------------
// 2026-09-22 실측 (`projects/*/apps/*/src`, `from '<X>'` 기준):
//     next/headers  12 파일      server-only  0      next/cache  0      client-only  0
// ⇒ **오늘 이 저장소가 실제로 쓰는 서버 전용 잎은 `next/headers` 하나뿐이다.**
// 🔵 그런데 목록에 `server-only` · `next/cache` 도 둔다. 이것은 «세는 모집단» 이 아니라
//    **금지 목록**이라 0건이어도 공허하지 않다 — 아무도 안 쓰면 그냥 안 걸릴 뿐이고,
//    다음 사건이 그쪽으로 올 때 **그날** 문다(Failure Scenario 1 이 지목한 모양).
// 🔴 `client-only` 는 **넣지 않는다** — 축이 반대다(클라이언트 전용이 서버로 가는 것).
// =============================================================================

import { readFileSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { repoRoot, buildClientGraph, stripComments, isServerBoundary } from './client-graph.mjs';

const SELF_TEST = process.argv.includes('--self-test');
const ROOT = process.env.CGSO_ROOT ?? repoRoot();
// 🔴 **호출마다** 본다 — 굳히면 self-test 의 케이스별 root 세팅을 못 따라간다.
const synthetic = () => !!process.env.CGSO_ROOT;

/**
 * 서버 전용 모듈 스펙. 🔴 `next/headers` 하나만 박지 마라 — 다음 사건은 다른 잎으로 온다.
 * 정확 일치 또는 하위 경로(`next/cache/x`)까지 본다.
 */
const SERVER_ONLY = ['next/headers', 'server-only', 'next/cache'];
const isServerOnlySpec = (spec) =>
  SERVER_ONLY.some((m) => spec === m || spec.startsWith(m + '/'));

/**
 * 🔵 **타입 전용 임포트는 번들에 안 들어간다** ⇒ 물면 오탐이다(티켓 § Edge Cases).
 * `import type { X } from 'next/headers'` 같은 줄을 스펙 단계에서 거른다.
 */
const TYPE_ONLY_RE = (spec) =>
  new RegExp(`import\\s+type\\s[^;]*?from\\s*['"]${spec.replace(/[/\\-]/g, '\\$&')}['"]`);

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
      // 🔴 `'use server'` 모듈은 브라우저로 안 간다 — 세지 않는다. 순회는 그래프
      //    모듈에서 이미 멈췄지만, 그 모듈 자체는 `reached` 안에 있다.
      if (isServerBoundary(s)) continue;
      const body = stripComments(s);
      const specs = (a.importsOf.get(f) ?? []).filter(isServerOnlySpec);
      const real = specs.filter((spec) => !TYPE_ONLY_RE(spec).test(body));
      if (real.length) hits.push({ file: f, specs: [...new Set(real)] });
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
  const tmp = join(process.env.TMPDIR ?? process.env.TEMP ?? '/tmp', 'cgso-selftest-' + process.pid);
  const cases = [
    {
      // 🔴🔴 이 칸이 **이 가드를 만든 사건 자체**다. 경로가 **배럴을 지난다** —
      //    직접 임포트만 보는 술어는 이 사건을 통째로 놓친다.
      name: "(a) 'use client' → **배럴** → 서버 전용  ->  문다 (2026-09-22 에 실제로 일어난 모양)",
      files: {
        'app/next.config.js': 'module.exports = {}\n',
        'app/src/features/OrdersScreen.tsx':
          "'use client';\nimport { Hint } from '@/widgets/gate';\nexport const S = () => <Hint/>;\n",
        'app/src/widgets/gate/index.ts':
          "export { Hint } from './Hint';\nexport { Gate } from './Gate';\n",
        'app/src/widgets/gate/Hint.tsx': 'export const Hint = () => null;\n',
        'app/src/widgets/gate/Gate.tsx':
          "import { cookies } from 'next/headers';\nexport const Gate = async () => (await cookies()).get('x');\n",
      },
      expectBad: 1,
    },
    {
      // 대조군 ① — 서버 컴포넌트가 같은 모듈을 부르는 것은 **정상**이다.
      name: "(b) 서버 컴포넌트가 같은 모듈을 부른다  ->  안 문다 (그것이 그 모듈의 용도다)",
      files: {
        'app/next.config.js': 'module.exports = {}\n',
        'app/src/app/page.tsx': "import { Gate } from '@/widgets/gate';\nexport default () => <Gate/>;\n",
        'app/src/widgets/gate/index.ts': "export { Gate } from './Gate';\n",
        'app/src/widgets/gate/Gate.tsx':
          "import { cookies } from 'next/headers';\nexport const Gate = async () => (await cookies()).get('x');\n",
      },
      expectBad: 0,
    },
    {
      // 대조군 ② — 클라이언트가 안전한 모듈만 부른다.
      name: "(c) 'use client' 가 안전한 모듈만 부른다  ->  안 문다",
      files: {
        'app/next.config.js': 'module.exports = {}\n',
        'app/src/ui/B.tsx': "'use client';\nimport { fmt } from '@/lib/fmt';\nexport const B = () => fmt(1);\n",
        'app/src/lib/fmt.ts': 'export const fmt = (n: number) => String(n);\n',
      },
      expectBad: 0,
    },
    {
      // 🔴 `'use server'` 가 그래프의 끝이다 — 형제 가드가 fan 을 거짓 고발했던 그 규칙.
      name: "(d) 중간이 `'use server'` 다  ->  안 문다 (Server Action 너머는 브라우저가 아니다)",
      files: {
        'app/next.config.js': 'module.exports = {}\n',
        'app/src/ui/B.tsx': "'use client';\nimport { act } from '@/actions/a';\nexport const B = () => act();\n",
        'app/src/actions/a.ts':
          "'use server';\nimport { cookies } from 'next/headers';\nexport async function act() { return (await cookies()).get('x'); }\n",
      },
      expectBad: 0,
    },
    {
      // 🔵 타입 전용은 번들에 안 들어간다 ⇒ 물면 오탐이다.
      name: '(e) `import type` 만 한다  ->  안 문다 (번들에 안 들어간다)',
      files: {
        'app/next.config.js': 'module.exports = {}\n',
        'app/src/ui/B.tsx':
          "'use client';\nimport { t } from '@/lib/t';\nexport const B = () => t;\n",
        'app/src/lib/t.ts':
          "import type { ReadonlyHeaders } from 'next/headers';\nexport const t: ReadonlyHeaders | null = null;\n",
      },
      expectBad: 0,
    },
    {
      // 🔴 다음 사건이 다른 잎으로 올 때를 고정한다(Failure Scenario 1).
      name: "(f) 잎이 `server-only` 다  ->  문다 (`next/headers` 하나만 박으면 놓친다)",
      files: {
        'app/next.config.js': 'module.exports = {}\n',
        'app/src/ui/B.tsx': "'use client';\nimport { x } from '@/lib/s';\nexport const B = () => x;\n",
        'app/src/lib/s.ts': "import 'server-only';\nexport const x = 1;\n",
      },
      expectBad: 1,
    },
    {
      // 🔴 「일을 하나도 안 하고 rc=0」 — 뿌리 0개는 통과가 아니다.
      name: '(g) 클라이언트 뿌리가 0개다  ->  판정 불가 (통과로 읽지 않는다)',
      files: {
        'app/next.config.js': 'module.exports = {}\n',
        'app/src/lib/x.ts': 'export const x = 1;\n',
      },
      expectBad: 0,
      expectRoots: 0,
    },
  ];

  let failed = 0;
  for (const c of cases) {
    rmSync(tmp, { recursive: true, force: true });
    for (const [rel, body] of Object.entries(c.files)) {
      const abs = join(tmp, rel);
      mkdirSync(dirname(abs), { recursive: true });
      writeFileSync(abs, body, 'utf8');
    }
    process.env.CGSO_ROOT = tmp;
    const r = run(tmp);
    delete process.env.CGSO_ROOT;
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
const say = (m) => console.log(`[client-graph-server-only] ${m}`);

if (SELF_TEST) {
  say('--self-test — 합성 트리에서 무는지/안 무는지 확인합니다');
  const failed = selfTest();
  if (failed) { say(`✗ self-test ${failed}건 실패`); process.exit(1); }
  say('ok — self-test 전부 통과');
  process.exit(0);
}

const r = run(ROOT);

// 🔴 하한은 **위반 수**가 아니라 **계측기가 살아 있는가**에 건다. 「위반 0건」은 정당하게
//    참일 수 있지만(그것이 목표다), 뿌리 0개·도달 0개는 순회가 죽은 것이다.
//    🔵 형제 가드와 **같은 그래프**이므로 하한도 같은 값을 쓴다 — 갈라지면 한쪽이
//       조용히 약해진다.
const FLOOR_APPS = Number(process.env.CGSO_FLOOR_APPS ?? 3);
const FLOOR_ROOTS = Number(process.env.CGSO_FLOOR_ROOTS ?? 20);
const FLOOR_REACHED = Number(process.env.CGSO_FLOOR_REACHED ?? 60);

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
  say('✗ 클라이언트 그래프에서 **서버 전용 모듈**에 닿습니다:');
  for (const b of r.bad) say(`    ${b.file}  →  ${b.specs.join(' · ')}`);
  say('  → 이 경로는 `next build` 에서 깨집니다. 그리고 CI 는 console-web 의 build 를');
  say('     돌리지 않으므로, 이 가드가 없으면 **초록으로 머지됩니다**.');
  say('  → 처방은 대개 **배럴을 건너뛰는 것**입니다: 클라이언트 화면은 그 컴포넌트를');
  say('     모듈 경로로 직접 임포트하세요(배럴은 서버 전용 형제도 함께 내보냅니다).');
  say('  → 그것으로 안 되면 모듈을 가르세요 — 서버 전용 값·능력은 클라이언트가');
  say('     (몇 다리 건너라도) 임포트하지 않는 모듈에 둡니다.');
  process.exit(1);
}

say(`ok — 앱 ${r.appsScanned}개 · 클라이언트 뿌리 ${r.clientRoots}개에서 도달하는 ${r.reached}개 모듈에 서버 전용 임포트 0건`);
