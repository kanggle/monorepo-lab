#!/usr/bin/env node
// -----------------------------------------------------------------------------
// node_modules 를 물려받는 스테이지는 그 심링크의 **대상도** 물려받아야 한다
//   (TASK-MONO-655)
// -----------------------------------------------------------------------------
// 🔴 `pnpm` 의 `link:` 의존은 **대상이 없어도 심링크를 만든다.** 그래서 `pnpm install` 은
//    통과하고, 죽는 것은 **빌드**다 — `Module not found: Can't resolve '@demo/…'`.
//    그리고 그 빌드는 다단계 Dockerfile 의 **다른 스테이지**에서 돈다.
//
//    deps 스테이지     COPY --from=demo-public-data …   ← 대상을 놓는다
//                      RUN pnpm install                  ← 심링크가 생긴다
//    builder 스테이지  COPY --from=deps …/node_modules   ← **심링크만** 물려받는다
//                      RUN pnpm build                    ← 대상이 없으면 여기서 죽는다
//
// ⇒ 불변식: **`node_modules` 를 다른 스테이지에서 복사해 오는 스테이지는, 그 원본
//    스테이지가 놓은 외부 컨텍스트를 하나도 빠짐없이 자기도 놓아야 한다.**
//
// 🔴🔴 **이 축은 `check-build-context-declarations.sh` 가 못 본다.** 그 가드는
//    「Dockerfile 이 요구(`COPY --from=X`) ↔ compose 가 공급(`additional_contexts`)」을
//    본다. 2026-09-07 의 결함은 **공급도 됐고 복사도 했는데 스테이지 하나를 빠뜨린** 것이라
//    그 가드에 초록으로 통과했고, `main` 의 nightly 가 **이틀** 빨갰다(콘솔 e2e 가 Playwright
//    에 닿지도 못했다). 같은 계열이 이 저장소를 문 것은 이번이 네 번째다
//    (`TASK-MONO-585` · `615` · `629` · `655`).
//
// 🔵 **한 스테이지에서 install 과 build 를 다 하는 앱은 이 가드의 대상이 아니다** —
//    물려받는 것이 없으므로 빠뜨릴 것도 없다. 형제 앱(web-store · fan-web)이 그 모양이고,
//    바로 그 점이 콘솔에서 오독을 낳았다(*"그들의 한 번 복사는 여기서 한 번 복사해도 된다는
//    선례가 아니다"* — 콘솔 Dockerfile 자신의 주석).
//
// 사용:  node scripts/check-stage-inherits-its-contexts.mjs [--root <dir>] [--self-test]
// -----------------------------------------------------------------------------
import { existsSync, readFileSync, readdirSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { join, dirname, resolve, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { tmpdir } from 'node:os';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(HERE, '..');
const FLOOR = 1; // 실제로 비교한 (원본→상속) 스테이지 쌍의 하한

/** Dockerfile 한 벌을 스테이지로 쪼갠다. */
export function parseStages(src) {
  const stages = [];
  let cur = null;
  for (const raw of src.split('\n')) {
    const line = raw.trim();
    const from = line.match(/^FROM\s+\S+(?:\s+AS\s+(\S+))?/i);
    if (from) {
      cur = { name: (from[1] || `#${stages.length}`).toLowerCase(), copies: [], inherits: null };
      stages.push(cur);
      continue;
    }
    if (!cur) continue;
    const cp = line.match(/^COPY\s+--from=(\S+)\s+(\S+)\s+(\S+)/i);
    if (!cp) continue;
    const [, srcName, srcPath] = cp;
    // node_modules 를 다른 스테이지에서 통째로 가져오는가
    if (/node_modules/.test(srcPath)) cur.inherits = srcName.toLowerCase();
    else cur.copies.push(srcName.toLowerCase());
  }
  return stages;
}

export function inspect(root) {
  const problems = [];
  let compared = 0;
  const files = [];

  const walk = (dir, depth) => {
    if (depth > 6) return;
    let entries;
    try {
      entries = readdirSync(dir, { withFileTypes: true });
    } catch {
      return;
    }
    for (const e of entries) {
      if (e.name === 'node_modules' || e.name === '.git' || e.name === 'build') continue;
      const p = join(dir, e.name);
      if (e.isDirectory()) walk(p, depth + 1);
      else if (e.name === 'Dockerfile' || e.name.startsWith('Dockerfile.')) files.push(p);
    }
  };
  walk(root, 0);

  for (const file of files.sort()) {
    const stages = parseStages(readFileSync(file, 'utf8'));
    const byName = new Map(stages.map((s) => [s.name, s]));
    for (const st of stages) {
      if (!st.inherits) continue;
      const src = byName.get(st.inherits);
      if (!src) continue; // 이미지에서 가져오는 것 — 이 가드의 대상이 아니다
      compared++;
      const missing = src.copies.filter((c) => !st.copies.includes(c));
      for (const m of missing) {
        problems.push(
          `${relative(root, file).replace(/\\/g, '/')}: 스테이지 «${st.name}» 이 ` +
            `«${st.inherits}» 의 node_modules 를 물려받는데 그 스테이지가 놓은 ` +
            `«${m}» 을 자기는 안 놓는다`,
        );
      }
    }
  }
  return { problems, compared, files: files.length };
}

// -----------------------------------------------------------------------------
function selfTest() {
  const base = join(tmpdir(), `sic-selftest-${process.pid}`);
  const cells = [];
  const cell = (name, fn) => {
    const d = join(base, name);
    mkdirSync(d, { recursive: true });
    try {
      fn(d);
      cells.push([name, true, '']);
    } catch (e) {
      cells.push([name, false, String(e.message || e)]);
    }
  };
  const eq = (got, want, what) => {
    if (got !== want) throw new Error(`${what}: ${got} (기대 ${want})`);
  };
  const df = (d, body) => {
    mkdirSync(join(d, 'app'), { recursive: true });
    writeFileSync(join(d, 'app', 'Dockerfile'), body, 'utf8');
  };

  // (1) 🔴 결함 그대로 — builder 가 형제 하나를 빠뜨렸다
  cell('1-missing-sibling-bites', (d) => {
    df(d, `FROM node AS deps
COPY --from=demo-backend-resolver . /infra/a
COPY --from=demo-public-data . /infra/b
RUN pnpm install
FROM node AS builder
COPY --from=demo-backend-resolver . /infra/a
COPY --from=deps /app/node_modules ./node_modules
RUN pnpm build
`);
    const r = inspect(d);
    eq(r.compared, 1, '비교한 쌍');
    eq(r.problems.length, 1, '문제 수');
    if (!r.problems[0].includes('demo-public-data')) throw new Error(`이름이 틀림: ${r.problems[0]}`);
  });

  // (2) ✅ 고친 모양 — 조용하다
  cell('2-symmetric-is-silent', (d) => {
    df(d, `FROM node AS deps
COPY --from=demo-backend-resolver . /infra/a
COPY --from=demo-public-data . /infra/b
FROM node AS builder
COPY --from=demo-backend-resolver . /infra/a
COPY --from=demo-public-data . /infra/b
COPY --from=deps /app/node_modules ./node_modules
`);
    const r = inspect(d);
    eq(r.compared, 1, '비교한 쌍');
    eq(r.problems.length, 0, '문제 수');
  });

  // (3) 🔵 대조군 — **한 스테이지에서 install 과 build 를 다 하는 앱**(형제 앱들의 모양).
  //     물려받는 것이 없으므로 대상이 아니고, 여기서 빨개지면 그 앱들이 이유 없이 죽는다.
  cell('3-single-stage-not-compared', (d) => {
    df(d, `FROM node AS app
COPY --from=demo-public-data . /infra/b
RUN pnpm install && pnpm build
`);
    const r = inspect(d);
    eq(r.compared, 0, '비교한 쌍');
    eq(r.problems.length, 0, '문제 수');
  });

  // (4) 🔵 대조군 — 상속 스테이지가 **더** 놓는 것은 결함이 아니다
  cell('4-extra-in-builder-is-fine', (d) => {
    df(d, `FROM node AS deps
COPY --from=demo-public-data . /infra/b
FROM node AS builder
COPY --from=demo-public-data . /infra/b
COPY --from=demo-extra . /infra/c
COPY --from=deps /app/node_modules ./node_modules
`);
    eq(inspect(d).problems.length, 0, '문제 수');
  });

  // (5) 🔵 대조군 — `node_modules` 를 **이미지**에서 가져오면 판정 불가이지 결함이 아니다
  cell('5-inherit-from-unknown-stage', (d) => {
    df(d, `FROM node AS builder
COPY --from=some-published-image /app/node_modules ./node_modules
`);
    const r = inspect(d);
    eq(r.compared, 0, '비교한 쌍');
    eq(r.problems.length, 0, '문제 수');
  });

  rmSync(base, { recursive: true, force: true });
  const bad = cells.filter(([, ok]) => !ok);
  for (const [n, ok, why] of cells) console.log(`  ${ok ? '✅' : '🔴'} (${n})${why ? ' — ' + why : ''}`);
  console.log(`self-test: ${cells.length - bad.length}/${cells.length} 통과`);
  return bad.length ? 1 : 0;
}

// -----------------------------------------------------------------------------
const argv = process.argv.slice(2);
if (argv.includes('--self-test')) process.exit(selfTest());

const ri = argv.indexOf('--root');
const root = ri >= 0 ? resolve(argv[ri + 1]) : REPO;
const { problems, compared, files } = inspect(root);

console.log(`check-stage-inherits-its-contexts: Dockerfile ${files}개 · 비교한 스테이지 쌍 ${compared}개`);

if (ri < 0 && compared < FLOOR) {
  console.error(
    `\n🔴 비-공허성 하한 미달: 비교한 쌍이 ${compared}개다(하한 ${FLOOR}).\n` +
      `   이 가드가 «아무것도 안 보고» 초록이 되는 것을 막는 장치다 — 파서가 스테이지를\n` +
      `   못 읽고 있는지 먼저 의심하라.`,
  );
  process.exit(1);
}

if (problems.length) {
  console.error(`\n🔴 어긋남 ${problems.length}건:`);
  for (const p of problems) console.error(`   ${p}`);
  console.error(
    `\n   고치는 법: 그 줄을 상속 스테이지에도 더하라.\n` +
      `   🔴 «install 이 통과했으니 괜찮다» 가 아니다 — pnpm 의 link: 는 대상이 없어도\n` +
      `      심링크를 만들고, 죽는 것은 그 다음 스테이지의 build 다.`,
  );
  process.exit(1);
}

console.log('  node_modules 를 물려받는 스테이지가 그 대상도 전부 놓는다.');
