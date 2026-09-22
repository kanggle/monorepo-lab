// =============================================================================
// client-graph.mjs — `'use client'` 에서 **전이적으로 도달하는 모듈**의 집합
// =============================================================================
// 🔴 이것은 가드가 아니라 **가드들이 공유하는 순회기**다. 자기 판정을 갖지 않는다.
//
// -----------------------------------------------------------------------------
// 🔴🔴 왜 «선언» 이 아니라 «도달» 인가 — 이 모듈의 존재 이유
// -----------------------------------------------------------------------------
// `TASK-MONO-585` 착수 실측(2026-09-05): console-web 의 `shared/config/env.ts` 를
// 임포트하는 파일 **20개 중 `"use client"` 는 0개**였다. 선언 경계만 보면 완벽하다.
// 그런데 같은 커밋의 산출물에는 그 모듈의 백엔드 URL **12개가 전부** 클라이언트 청크에
// 있었다. 클라이언트 컴포넌트가 **몇 다리 건너** 임포트하면 그것으로 충분하기 때문이다.
//
// ⇒ **선언 경계 ≠ 번들 경계.**
//
// -----------------------------------------------------------------------------
// 🔴 왜 «추출» 되었는가 (TASK-MONO-720)
// -----------------------------------------------------------------------------
// 2026-09-22 에 **두 번째** 축이 생겼다: 서버 전용 모듈(`next/headers` 등)이 같은
// 그래프를 타고 브라우저 번들로 새는 것. 실제로 밟았다 —
//   `OrdersScreen`('use client') → 위젯 **배럴** → `DomainTenantGate` → `next/headers`
// 그때 vitest 316파일·3552칸 · `tsc` · `next lint` 가 **전부 초록**이었다. 번들 경계를
// 묻는 것은 번들러뿐인데, CI 는 console-web 의 `next build` 를 돌리지 않는다.
//
// 🔴 축이 둘이면 **가드도 둘**이다(실패 메시지가 다르면 처방이 다르다). 그러나 **그래프는
//    하나여야 한다** — 순회기를 복사하면 둘이 갈라지고 한쪽이 조용히 틀린다. 이 저장소엔
//    그 실패를 감시하는 가드가 이름부터 있다(`check-demo-resolver-copies.sh` —
//    *"두 번째 것은 결정이지 사본이 아니다"*).
//
// 🔴 이 파일은 `scripts/` **바로 아래**에 있어야 한다. `check-ls-files-guard-count.sh` 의
//    분모는 `find scripts -maxdepth 1 -type f`(하위 디렉터리 제외)인데 분자는
//    `grep -rl 'ls-files' scripts`(**재귀**)다 ⇒ `scripts/lib/` 에 두면 분자만 늘어
//    비율이 비틀리고, 그 수치는 산문 집 두 곳이 들고 있다.
// =============================================================================

import { execFileSync } from 'node:child_process';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, sep } from 'node:path';

export function repoRoot() {
  return execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim();
}

/**
 * 트리의 파일 목록. 합성 트리(self-test)를 가리킬 때는 git 을 쓰지 않는다.
 * 🔵 실 트리에서는 **`git ls-files`** 가 모집단이다 — 커밋될 것을 묻는 가드이므로.
 *   🔴 그래서 **스테이지 전에 돌리면 다른 질문**이 된다(CLAUDE.md § 「가드를 돌리기 전에 스테이지」).
 */
export function listFiles(root, { synthetic = false } = {}) {
  if (synthetic) return walk(root, root);
  return execFileSync('git', ['-C', root, 'ls-files'], { encoding: 'utf8', maxBuffer: 64 << 20 })
    .split('\n')
    .filter(Boolean);
}

export function walk(dir, root, out = []) {
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

/**
 * 문자열/정규식 안의 `//` 를 주석으로 오인하지 않게, 아주 보수적으로 지운다.
 *
 * 🔴 주석을 **먼저 걷어내야** 한다. 걷지 않으면 판별자가 **자기 설명 문구에 걸린다** —
 *    이 저장소가 이미 이름 붙여 둔 실패다(`check-demo-resolver-copies.sh`, 2026-09-02).
 *    그리고 번들러도 주석을 지우므로, 주석을 세는 것은 **틀린 것을 세는 것**이다.
 */
export function stripComments(src) {
  return src
    .replace(/\/\*[\s\S]*?\*\//g, '')            // 블록 주석
    .replace(/(^|[\s({[,;=])\/\/[^\n]*/g, '$1'); // 줄 주석 (URL 의 `://` 는 앞이 `:` 라 안 걸린다)
}

/**
 * 세 모양을 **전부** 잡는다:
 *   ① `import … from 'x'` / `export … from 'x'`
 *   ② `import('x')` (동적)
 *   ③ `import 'x'`    ← **부수효과 임포트**
 *
 * 🔴🔴 ③ 은 2026-09-22 에 형제 가드의 self-test 가 잡은 **원래부터 있던 구멍**이다.
 *    `import 'server-only';` 가 정확히 그 모양이고, 부수효과 임포트도 모듈을 **실행**하므로
 *    번들에 들어간다 ⇒ 두 축 모두에서 세야 한다. 추출 전 판도 이것을 놓치고 있었고,
 *    그 사실을 고친 것이지 추출이 만든 결함이 아니다.
 * 🔵 ③ 의 스펙은 그래프 순회에서 대개 `null` 로 해석된다(`next/headers` 는 저장소 밖) —
 *    그래도 `importsOf` 에는 남아야 한다. 서버 전용 축이 보는 것이 바로 그 **미해석 스펙**이다.
 */
export const IMPORT_RE =
  /(?:^|[\s;}])(?:import|export)\s[^;]*?from\s*['"]([^'"]+)['"]|(?:^|[^\w.])import\s*\(\s*['"]([^'"]+)['"]\s*\)|(?:^|[\s;}])import\s*['"]([^'"]+)['"]\s*;?/g;

const EXT_ORDER = ['.ts', '.tsx', '.js', '.jsx', '.mjs'];

/**
 * 저장소 **상대** POSIX 경로끼리의 결합. 🔴 `path.resolve` 를 쓰면 안 된다 — 그것은
 * 결과를 **절대 경로**로 만들고, 그러면 `git ls-files` 가 준 상대 경로 집합과 영원히
 * 매치되지 않는다. 즉 상대 임포트(`../hooks/x`)가 **하나도 해석되지 않는데** 가드는
 * 조용히 초록이 된다.
 *
 * 🔴🔴 이것이 첫 판에서 실제로 일어났다 (2026-09-05, bite 테스트가 잡았다): 착수 전
 * 트리에서 console-web 이 `hits=0` 이었다 — 같은 커밋의 산출물에 백엔드 URL 12개가
 * **있는데도**. 그 앱의 누출 경로가 전부 상대 임포트였다.
 * ⇒ **가드는 무는지 확인하기 전까지 무는 것이 아니다.**
 */
export function joinRel(fromDir, spec) {
  const parts = fromDir === '' ? [] : fromDir.split('/');
  for (const seg of spec.split('/')) {
    if (seg === '' || seg === '.') continue;
    if (seg === '..') parts.pop();
    else parts.push(seg);
  }
  return parts.join('/');
}

/** 저장소 안의 파일로 해석되면 그 경로, 아니면 `null`(node_modules / next 내장). */
export function resolveSpec(spec, fromFile, aliasBase, files) {
  let base = null;
  if (spec.startsWith('@/')) base = joinRel(aliasBase, spec.slice(2));
  else if (spec.startsWith('./') || spec.startsWith('../')) {
    const dir = fromFile.includes('/') ? fromFile.slice(0, fromFile.lastIndexOf('/')) : '';
    base = joinRel(dir, spec);
  } else if (spec === '@demo/backend-resolver') base = 'infra/demo/backend-resolver/src/index';
  else return null; // node_modules / next builtins — 그래프의 범위 밖
  for (const ext of ['', ...EXT_ORDER, ...EXT_ORDER.map((e) => '/index' + e)]) {
    if (files.has(base + ext)) return base + ext;
  }
  return null;
}

export function hasTopDirective(src, name) {
  // 파일 **맨 위**의 디렉티브만 본다. 함수 본문 안의 `'use server'` 는 모듈 경계가 아니다.
  const head = src.slice(0, 400);
  return new RegExp(`(^|\\n)\\s*['"]use ${name}['"]\\s*;?\\s*(\\n|$)`).test(head);
}

export const isClientRoot = (src) => hasTopDirective(src, 'client');

/**
 * 🔴🔴 `'use server'` 는 **클라이언트 그래프의 끝**이다 — 여기서 순회를 멈춘다.
 *
 * Server Action 모듈은 클라이언트 컴포넌트가 임포트해도 코드가 브라우저로 가지 않는다.
 * Next 가 그 임포트를 **참조 스텁**으로 바꾸고, 실제 본문은 서버에만 남는다.
 *
 * 🔴 이 규칙이 없으면 fan-platform-web 을 **거짓으로 고발한다** (2026-09-05 실측:
 * `FollowButton`('use client') → `follow/api/actions.ts`('use server') → `env.ts`).
 * fan 의 실제 산출물은 깨끗했다 — 술어가 번들러의 규칙 하나를 빠뜨린 것이지 fan 이
 * 틀린 것이 아니었다.
 */
export const isServerBoundary = (src) => hasTopDirective(src, 'server');

/**
 * 앱마다 «클라이언트 뿌리에서 전이적으로 도달하는 모듈 집합» 을 만든다.
 *
 * 반환: `{ appsScanned, apps: [{ app, roots, reached: string[], text: Map, importsOf }] }`
 * 🔵 **판정은 하지 않는다** — 호출자가 `reached` 위에서 자기 축을 잰다.
 * 🔵 `importsOf`: 파일 → 해석되지 않은 임포트 스펙 목록(저장소 밖으로 나가는 것 포함).
 *    서버 전용 축은 **해석되지 않는 스펙**(`next/headers`)을 봐야 하므로 이것이 필요하다.
 */
export function buildClientGraph(root, { synthetic = false } = {}) {
  const all = listFiles(root, { synthetic });
  const files = new Set(all);
  const apps = [
    ...new Set(
      all
        .filter((f) => /(?:^|\/)next\.config\.[a-z]+$/.test(f))
        .map((f) => f.replace(/(?:^|\/)next\.config\.[a-z]+$/, '') || '.'),
    ),
  ].sort();

  const out = { appsScanned: apps.length, apps: [] };

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
    const importsOf = new Map();
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
      if (isServerBoundary(s)) { importsOf.set(f, []); continue; }
      const body = stripComments(s);
      const specs = [];
      IMPORT_RE.lastIndex = 0;
      let m;
      while ((m = IMPORT_RE.exec(body))) {
        const spec = m[1] ?? m[2] ?? m[3];
        if (!spec) continue;
        specs.push(spec);
        const target = resolveSpec(spec, f, aliasBase, files);
        if (target && !seen.has(target)) { seen.add(target); queue.push(target); }
      }
      importsOf.set(f, specs);
    }

    out.apps.push({ app, roots, reached: [...seen], text, importsOf });
  }
  return out;
}
