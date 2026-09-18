#!/usr/bin/env node
// =============================================================================
// check-capture-route-staleness.mjs — TASK-MONO-648 AC-3 «낡음을 무는 가드»
// =============================================================================
// 포트폴리오 촬영본이 낡았는가를 **날짜로 묻지 않는다.** 예약 트리거는 이 저장소가 이미
// 데인 함정이고(«예약된 트리거는 늙은 원소를 넣어도 발화»), 「30일 지났다」는 화면이
// 바뀌었다는 증거가 아니다. 대신 **경로가 아직 실재하는가**를 묻는다:
//
//     찍은 경로  ↔  `app/**/page.tsx` 에서 유도한 라우트 목록
//
// 페이지가 사라지거나 이름이 바뀌면 빨개진다.
//
// -----------------------------------------------------------------------------
// 술어 하나, 모집단 둘 — 🔴 이 갈래가 이 파일의 설계 결정이다
// -----------------------------------------------------------------------------
// AC-3 의 문장은 «**매니페스트의** 경로 ↔ 유도한 라우트» 다. 그런데 매니페스트는
// `portfolio-captures/manifest.json` 이고 그 디렉터리는 **`.gitignore:106` 이 삼킨다**
// (전량 102장이 15–50MB 라 일부러 그렇게 정했다 — 이 티켓 § 「산출물이 어디 사는가」).
// ⇒ 매니페스트만 읽는 가드는 **CI 에서 한 번도 못 돈다.** 그리고 «아무도 안 돌리는 가드는
//    없는 가드보다 나쁘다» 는 것을 이 저장소가 바로 직전에 치렀다(`TASK-MONO-711` AC-1b:
//    16칸짜리 self-test 를 **어떤 워크플로도 안 불렀다**).
//
//   **모드 A — 표면**(기본 · 인자 없음 · CI 가 매 PR 에서 돌린다 · 매니페스트가 **필요 없다**)
//     `capture-portfolio.mjs` 의 `APPS` 를 읽어 앱마다:
//       ① `appDir` 이 실재하는가
//       ② 거기서 유도된 라우트가 0개가 아닌가
//       ③ 🔴 그 앱이 선언한 `probe` 가 그 유도 목록에 **있는가**  ← 날카로운 칸
//     ③ 이 이 모드의 존재 이유다. 오늘 콘솔의 프로브는 `/dashboards/overview` 이고,
//     그 라우트가 이름만 바뀌어도 `sanityCheck()` 는 404 를 받아 **앱 전체를 건너뛴다**
//     (`captured 0`). 2026-09-17 둘째 창이 정확히 그 모양으로 67계획/0촬영이었다 — 사유는
//     달랐지만 증상은 같다. 지금 저장소에서 그 고장을 **미리** 볼 수 있는 것은 아무것도 없다.
//
//   **모드 B — 매니페스트**(`--manifest <경로>` · 창/로컬)
//     `shots[].path` 가 전부 그 앱의 유도 목록에 대응되는가. 동적 촬영은 **구체 경로**
//     (`/ecommerce/products/01a0…/edit`)로 적히므로 `[id]` 꼴 세그먼트와 **모양으로** 맞춘다.
//     🔴 이걸 틀리면 동적 장 전부가 매번 «낡았다» 로 보고된다 — 그래서 self-test 에
//        대조군 칸으로 박아 두었다.
//
// -----------------------------------------------------------------------------
// 🔴🔴 라우트 유도를 **다시 적지 않는다 — import 한다**
// -----------------------------------------------------------------------------
// `APPS` 와 `deriveRoutes()` 는 `capture-portfolio.mjs` 에서 온다. 앱 목록이나 route group
// 제거 규칙을 여기 상수로 옮겨 적으면 이 가드는 **그 재진술**을 재게 되고, 그것은 막으려는
// 결함 그 자체다. 저장소의 선례가 같은 문장을 적어 두었다 —
// `projects/iam-platform/apps/auth-service/src/test/java/com/example/auth/demoseed/FanArtistDemoSeedTest.java`:
//   *"A test that restated them as its own constants would verify the restatement —
//     the exact failure mode it is here to prevent."*
//
// -----------------------------------------------------------------------------
// 🔴 비공허성(non-vacuity) — 축을 **이름**으로 잡았지 수로 잡지 않았다
// -----------------------------------------------------------------------------
// 라우트는 늘기도 줄기도 한다(이 티켓만 해도 102 → 100 으로 줄었다). 그러므로 «라우트 ≥ N»
// 같은 수 하한은 **줄어드는 모집단에 하한** 이 되어 정상 변경에서 터진다 — 이 저장소가
// 이름 붙인 함정이다. 대신 하한을 **이름 있는 원소**에 건다:
//   · `probe` 경로가 유도 목록에 있을 것 — 이름이지 수가 아니다.
//   · **`probe` 를 선언한 앱이 최소 하나**일 것. 🔴 이 모집단은 «정상적으로 줄어들 수 없다»:
//     `probe` 를 지우면 `capture-portfolio.mjs` 의 `if (app.probe)` 가 사전 점검을 통째로
//     건너뛰므로, 그 삭제 자체가 이 가드가 막으려는 회귀다. ⇒ 하한이 옳다.
//   · 앱당 «유도 라우트 0개가 아닐 것» — 0 이 되려면 `app/` 이 통째로 사라져야 하고, 그러면
//     `APPS` 도 같이 고쳐야 한다. 조용히 0 으로 빠질 길이 없다.
//
// -----------------------------------------------------------------------------
// 🔴 이 술어가 **못 잡는 것** (AC-3 이 적으라고 한 한계 — 지우지 마라)
// -----------------------------------------------------------------------------
// ① **«페이지가 사라졌다» 만 잡고 «내용이 바뀌었다» 는 못 잡는다.** 실사례가 이 티켓에 있다:
//    `/erp/masters` 는 참조 칸에 raw UUID 를 그리고 있었는데 **글자는 가득 차 있어서**
//    `empty` 도 `degraded` 도 아니었고 경로도 멀쩡했다 — 사람이 그림을 열어서 찾았다
//    (`TASK-PC-FE-276` § 발견 경위). 이 가드는 그날도 초록이었을 것이다.
// ② **«재생성 가능» 은 인증 캡처에서 약하다.** `TASK-MONO-639` 는 캡처에 «언제든 다시 만들 수
//    있다» 는 성질을 줬지만, 그것은 로그인 없이 열리는 표면의 이야기다. 콘솔의 보호 경로
//    64개는 **데모 기동 창**(예산 · 소유자 승인 · AMI 재굽기 선행)이 있어야만 다시 찍힌다.
//    ⇒ 이 가드가 빨개져도 «고치면 된다» 가 아니라 **«창을 하나 써야 한다»** 이다. 빨강을 보고
//    바로 재촬영할 수 없는 것이 정상이고, 그 사실을 모르면 빨강이 방치된다.
// ③ **모드 A 는 «이 사진이 아직 유효한가» 를 못 묻는다** — 매니페스트가 CI 에 없기 때문이다
//    (위 § ). 모드 A 가 묻는 것은 더 좁다: «촬영이 애초에 돌 수 있는 표면인가».
// ④ **«라우트 파일이 있다» 이지 «그 URL 이 200 이다» 가 아니다.** 권한 거부·리다이렉트는 이
//    술어로 초록이다. 그쪽은 `capture-portfolio.mjs` 의 거부/저하 판정기가 창에서 잰다.
// ⑤ **동적 경로는 모양만 맞춘다.** `/…/<uuid>/edit` 이 `[id]/edit` 에 맞는다는 것은 **라우트가
//    살아 있다** 는 뜻이지 그 uuid 의 엔티티가 아직 있다는 뜻이 아니다. 후자는 백엔드가
//    떠 있어야 알 수 있고, 그것은 창에서 촬영이 직접 답한다.
// ⑥ **유도 로직의 결함은 못 본다.** 유도를 `capture-portfolio.mjs` 에서 import 하므로, 그
//    유도가 틀리면 촬영과 가드가 **같은 방향으로** 틀린다. 일부러 그렇게 했다 — 사본을 두면
//    가드가 재는 것은 사본이지 촬영이 아니다(위 § ). 유도 자체는 `--list` 로 사람이 본다.
//
// -----------------------------------------------------------------------------
// 사용
// -----------------------------------------------------------------------------
//   node scripts/check-capture-route-staleness.mjs                    # 모드 A (CI)
//   node scripts/check-capture-route-staleness.mjs --manifest <path>  # 모드 B
//   node scripts/check-capture-route-staleness.mjs --self-test        # bite + 대조군
//
// 종료코드: 0 = 통과 · 1 = 낡음(또는 self-test 실패)
// =============================================================================

import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

import { APPS, deriveRoutes } from './capture-portfolio.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(HERE, '..');
const CAPTURE_SCRIPT = join(HERE, 'capture-portfolio.mjs');

// -----------------------------------------------------------------------------
// 경로 ↔ 라우트 맞추기
// -----------------------------------------------------------------------------
// Next 의 동적 세그먼트 세 가지를 다 받는다:
//   `[id]`        정확히 한 조각
//   `[...slug]`   한 조각 이상 (마지막이어야 한다)
//   `[[...slug]]` 0 조각 이상 (마지막이어야 한다)
// 🔵 route group `(console)` 은 여기까지 오지 않는다 — `deriveRoutes()` 가 URL 을 만들 때 이미
//    뺐다. **그 사실을 여기서 다시 구현하지 않는 것**이 import 의 요점이다.
const DYN_ONE = /^\[[^.\]][^\]]*\]$/;
const CATCH_ALL = /^\[\.\.\..+\]$/;
const OPT_CATCH_ALL = /^\[\[\.\.\..+\]\]$/;

export function segmentsOf(p) {
  // 질의·프래그먼트를 떼고 조각으로 자른다. `/a/b/` 와 `/a/b` 는 같은 경로다.
  return String(p).split('#')[0].split('?')[0].split('/').filter(Boolean);
}

export function routeMatchesPath(route, path) {
  const r = segmentsOf(route);
  const p = segmentsOf(path);
  let ri = 0;
  let pi = 0;
  while (ri < r.length) {
    const seg = r[ri];
    if (OPT_CATCH_ALL.test(seg)) return ri === r.length - 1; // 0개 이상 — 남은 것을 전부 먹는다
    if (CATCH_ALL.test(seg)) return ri === r.length - 1 && p.length - pi >= 1;
    if (pi >= p.length) return false;
    if (DYN_ONE.test(seg)) {
      ri += 1;
      pi += 1;
      continue;
    }
    if (seg !== p[pi]) return false;
    ri += 1;
    pi += 1;
  }
  // 🔴 조각이 남으면 **안 맞는 것**이다. 접두사 일치로 두면 `/erp` 가 `/erp/masters` 를
  //    삼켜서 «사라진 페이지» 를 초록으로 만든다 — self-test 에 그 칸이 있다.
  return pi === p.length;
}

export function matchesAny(routes, path) {
  return routes.filter((r) => routeMatchesPath(r, path));
}

// -----------------------------------------------------------------------------
// 모드 A — 표면
// -----------------------------------------------------------------------------
export function checkSurface(apps) {
  const problems = [];
  const rows = [];
  let probesDeclared = 0;
  for (const [key, app] of Object.entries(apps)) {
    const r = deriveRoutes(app);
    if (r.error) {
      problems.push(`${key}: ${r.error} — APPS 의 appDir 이 낡았거나 앱이 옮겨졌습니다`);
      rows.push({ key, routes: 0, probe: app.probe || null, probeOk: null, error: r.error });
      continue;
    }
    if (r.all.length === 0) {
      problems.push(`${key}: ${app.appDir} 에서 유도된 라우트가 0개입니다 — 촬영이 아무 장도 안 엽니다`);
      rows.push({ key, routes: 0, probe: app.probe || null, probeOk: null });
      continue;
    }
    let probeOk = null;
    if (app.probe) {
      probesDeclared += 1;
      probeOk = r.all.includes(app.probe);
      if (!probeOk) {
        problems.push(
          `${key}: 사전 점검 경로 '${app.probe}' 에 대응하는 page.tsx 가 없습니다 — ` +
            `sanityCheck() 가 404 를 받아 이 앱을 **통째로 건너뜁니다**(찍음 0). ` +
            `APPS.${key}.probe 를 살아 있는 경로로 옮기세요.`,
        );
      }
    }
    rows.push({ key, routes: r.all.length, static: r.static.length, dynamic: r.dynamic.length, probe: app.probe || null, probeOk });
  }
  if (probesDeclared === 0) {
    problems.push(
      'probe 를 선언한 앱이 하나도 없습니다 — 이 가드의 날카로운 칸이 공허해지고, ' +
        '동시에 촬영의 사전 점검(sanityCheck)도 통째로 꺼집니다.',
    );
  }
  return { problems, rows, probesDeclared };
}

// -----------------------------------------------------------------------------
// 모드 B — 매니페스트
// -----------------------------------------------------------------------------
export function checkManifest(manifest, apps) {
  const problems = [];
  const rows = [];
  const shots = manifest && Array.isArray(manifest.shots) ? manifest.shots : null;
  if (!shots) {
    return { problems: ['매니페스트에 `shots` 배열이 없습니다 — 이 파일은 촬영 매니페스트가 아닙니다'], rows, checked: 0 };
  }
  if (shots.length === 0) {
    // 🔴 «결함 0» 과 «측정 0» 은 같은 종료코드를 쓰면 안 된다. 촬영 0장짜리 실행은 실제로
    //    있었다(2026-09-17 둘째 창, ecommerce 67계획/0촬영) — 그 매니페스트를 초록으로
    //    통과시키면 이 가드는 «가장 나쁜 실행» 에서만 조용해진다.
    return { problems: ['`shots` 가 0개입니다 — 이것은 «낡은 것이 없다» 가 아니라 «아무것도 안 쟀다» 입니다'], rows, checked: 0 };
  }
  const cache = new Map();
  const routesFor = (key) => {
    if (!cache.has(key)) {
      const r = deriveRoutes(apps[key]);
      cache.set(key, r.error ? { error: r.error, all: [] } : r);
    }
    return cache.get(key);
  };
  let checked = 0;
  for (const shot of shots) {
    const key = shot && shot.app;
    const path = shot && (shot.path || shot.route);
    const where = `${key || '?'} ${path || '(경로 없음)'}${shot && shot.file ? ` (${shot.file})` : ''}`;
    if (!key || !apps[key]) {
      problems.push(`${where}: 모르는 앱 '${key}' — APPS 에 없습니다(앱이 지워졌거나 키가 바뀌었습니다)`);
      continue;
    }
    if (!path) {
      problems.push(`${where}: shot 에 path 도 route 도 없습니다`);
      continue;
    }
    const r = routesFor(key);
    if (r.error) {
      problems.push(`${where}: ${r.error}`);
      continue;
    }
    checked += 1;
    const hit = matchesAny(r.all, path);
    if (hit.length === 0) {
      problems.push(`${where}: 이 경로에 대응하는 page.tsx 가 더 이상 없습니다 — 사진이 없는 화면을 광고합니다`);
    } else {
      rows.push({ key, path, via: hit[0] });
    }
  }
  return { problems, rows, checked };
}

// =============================================================================
// self-test — 🔴 bite 와 **대조군**을 둘 다 (AC-3 이 명시적으로 요구한다)
// =============================================================================
// 픽스처는 임시 디렉터리에 **진짜 `page.tsx` 트리**를 세운다. 문자열을 흉내내지 않는 이유:
// 그러면 재는 것이 `deriveRoutes()` 가 아니라 내가 상상한 그 함수의 출력이 된다.
function writePage(root, relDir) {
  const d = join(root, relDir);
  mkdirSync(d, { recursive: true });
  writeFileSync(join(d, 'page.tsx'), 'export default function P() { return null; }\n', 'utf8');
}

// 실제 콘솔과 같은 모양의 작은 앱: route group · 동적 · catch-all · API 라우트(page 아님).
function buildFixtureApp(root) {
  writePage(root, '.'); //                                   → /
  writePage(root, '(console)/dashboards/overview'); //        → /dashboards/overview   (route group!)
  writePage(root, '(console)/erp'); //                        → /erp
  writePage(root, '(console)/erp/masters'); //                → /erp/masters
  writePage(root, '(console)/ecommerce/products'); //         → /ecommerce/products
  writePage(root, '(console)/ecommerce/products/[id]/edit'); // → /ecommerce/products/[id]/edit
  writePage(root, '(auth)/login'); //                         → /login
  writePage(root, 'docs/[...slug]'); //                       → /docs/[...slug]
  // 🔵 `route.ts` 는 페이지가 아니다 — 유도 목록에 들어오면 안 된다(대조군 ⑧ 이 본다).
  const api = join(root, 'api', 'tenant');
  mkdirSync(api, { recursive: true });
  writeFileSync(join(api, 'route.ts'), 'export async function GET() {}\n', 'utf8');
}

function selfTest() {
  const tmp = mkdtempSync(join(tmpdir(), 'capture-staleness-'));
  const cells = [];
  const cell = (name, kind, want, got, extra = '') => {
    const ok = want === got;
    cells.push({ name, kind, ok, want, got, extra });
    console.log(`  ${ok ? '✔' : '✗'} ${name}  [${kind}] want=${want} got=${got}${extra ? '  ' + extra : ''}`);
    return ok;
  };
  try {
    const live = join(tmp, 'live');
    buildFixtureApp(live);
    const app = { label: '픽스처', appDir: live, probe: '/dashboards/overview' };
    const apps = { console: app };
    const routes = deriveRoutes(app).all;
    console.log(`  · 픽스처 라우트 ${routes.length}개: ${routes.join(' ')}`);

    // ── 모드 A ──────────────────────────────────────────────────────────────
    cell('A1 control-surface-intact', '대조군', 0, checkSurface(apps).problems.length);

    // probe 의 page.tsx 만 지운 트리 — 이름은 그대로 두고 **파일만** 없앤다
    const noProbe = join(tmp, 'no-probe');
    buildFixtureApp(noProbe);
    rmSync(join(noProbe, '(console)', 'dashboards', 'overview'), { recursive: true, force: true });
    const r2 = checkSurface({ console: { ...app, appDir: noProbe } });
    cell('A2 probe-route-removed', 'bite', 1, r2.problems.length, r2.problems[0] ? '← ' + r2.problems[0].slice(0, 60) : '');

    // 🔴 이 둘은 «문제 1건» 이 아니라 **«그 사유로» 물었는가**를 본다. 앱이 통째로 사라지면
    //    probe 도 같이 못 세어지므로 문제는 2건이 되는데(둘 다 참인 문장이다), 개수로 단언하면
    //    칸이 «맞는 이유로 초록» 인지 «우연히 개수가 맞아 초록» 인지 구별하지 못한다.
    const named = (res, needle) => res.problems.some((p) => p.includes(needle));
    cell('A3 app-dir-missing', 'bite', true, named(checkSurface({ console: { ...app, appDir: join(tmp, 'nope') } }), 'app 디렉터리가 없습니다'));

    const emptyDir = join(tmp, 'empty');
    mkdirSync(emptyDir, { recursive: true });
    cell('A4 app-dir-with-no-pages', 'bite', true, named(checkSurface({ console: { ...app, appDir: emptyDir } }), '유도된 라우트가 0개'));

    // 🔴 probe 선언이 **사라지면** 날카로운 칸이 공허해진다 — 그것도 물어야 한다
    cell('A5 no-app-declares-a-probe', 'bite', 1, checkSurface({ console: { label: 'x', appDir: live } }).problems.length);

    // ── 모드 B ──────────────────────────────────────────────────────────────
    const shot = (path, extra = {}) => ({ app: 'console', path, file: 'x.jpg', ...extra });

    cell('B1 control-static-path-live', '대조군', 0, checkManifest({ shots: [shot('/erp/masters')] }, apps).problems.length);

    // 🔴 이 칸이 이 가드의 본론이다 — 찍은 장의 page.tsx 가 사라졌다
    const gone = join(tmp, 'gone');
    buildFixtureApp(gone);
    rmSync(join(gone, '(console)', 'erp', 'masters'), { recursive: true, force: true });
    const b2 = checkManifest({ shots: [shot('/erp/masters')] }, { console: { ...app, appDir: gone } });
    cell('B2 manifest-path-whose-page-was-removed', 'bite', 1, b2.problems.length, b2.problems[0] ? '← ' + b2.problems[0].slice(0, 60) : '');

    // 🔴🔴 대조군 — route group `(console)` 은 URL 에 없다. 순진한 구현이 깨지는 자리다.
    cell('B3 route-group-path-not-stale', '대조군', 0, checkManifest({ shots: [shot('/dashboards/overview')] }, apps).problems.length);

    // 🔴🔴 대조군 — 동적 촬영은 **구체 경로**로 적힌다. 여기서 틀리면 동적 장 전부가 오탐이다.
    cell(
      'B4 dynamic-id-path-not-stale',
      '대조군',
      0,
      checkManifest({ shots: [shot('/ecommerce/products/01a085a1-bc98-741e-aaaa-000000000001/edit', { resolvedFrom: '/ecommerce/products/[id]/edit' })] }, apps).problems.length,
    );

    cell('B5 catch-all-path-not-stale', '대조군', 0, checkManifest({ shots: [shot('/docs/a/b/c')] }, apps).problems.length);

    cell('B6 root-path-not-stale', '대조군', 0, checkManifest({ shots: [shot('/')] }, apps).problems.length);

    cell('B7 query-and-trailing-slash-not-stale', '대조군', 0, checkManifest({ shots: [shot('/erp/masters/?asOf=2026-01-01')] }, apps).problems.length);

    // 🔴 접두사 일치로 구현하면 이 칸이 초록이 된다 — 사라진 페이지를 부모가 삼킨다
    const shallow = join(tmp, 'shallow');
    buildFixtureApp(shallow);
    rmSync(join(shallow, '(console)', 'erp', 'masters'), { recursive: true, force: true });
    cell('B8 parent-route-must-not-absorb-child', 'bite', 1, checkManifest({ shots: [shot('/erp/masters')] }, { console: { ...app, appDir: shallow } }).problems.length);

    // 🔴 동적이면 무조건 통과하는 구현을 거른다 — `[id]/edit` 자체를 지운 트리
    const noDyn = join(tmp, 'no-dyn');
    buildFixtureApp(noDyn);
    rmSync(join(noDyn, '(console)', 'ecommerce', 'products', '[id]'), { recursive: true, force: true });
    cell(
      'B9 dynamic-route-removed-is-stale',
      'bite',
      1,
      checkManifest({ shots: [shot('/ecommerce/products/01a0/edit')] }, { console: { ...app, appDir: noDyn } }).problems.length,
    );

    cell('B10 unknown-app-key', 'bite', 1, checkManifest({ shots: [{ app: 'ghost', path: '/x' }] }, apps).problems.length);

    // 🔴 «결함 0» 이 아니라 «측정 0» — 조용한 초록을 막는다
    cell('B11 empty-manifest-measures-nothing', 'bite', 1, checkManifest({ shots: [] }, apps).problems.length);

    cell('B12 not-a-manifest', 'bite', 1, checkManifest({ generatedAt: 'x' }, apps).problems.length);

    // 🔵 `api/tenant/route.ts` 는 페이지가 아니다 — 촬영도 안 하고, 낡음 판정도 안 한다
    cell('B13 api-route-is-not-a-page', 'bite', 1, checkManifest({ shots: [shot('/api/tenant')] }, apps).problems.length);

    // ── 배선 ────────────────────────────────────────────────────────────────
    // 🔴🔴 이 가드는 `capture-portfolio.mjs` 를 **import** 한다. 그러려면 그 파일이 import
    //    시점에 `main()` 을 돌리면 안 되는데, 그 판정을 잘못 좁히면 CLI 가 **아무 일도 안 하고
    //    rc=0** 이 된다. 두 방향을 각각 단언한다 — 한쪽만 보면 반대쪽 결함이 초록이다.
    const url = pathToFileURL(CAPTURE_SCRIPT).href;
    let importOut = '';
    let importRc = 0;
    try {
      importOut = execFileSync(process.execPath, ['-e', `import(${JSON.stringify(url)}).then(() => console.log('IMPORTED'))`], {
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
      });
    } catch (e) {
      importRc = e.status == null ? -1 : e.status;
      importOut = String(e.stdout || '') + String(e.stderr || '');
    }
    cell('W1 import-does-not-run-the-capture', 'bite', true, importRc === 0 && importOut.includes('IMPORTED') && !importOut.includes('[portfolio]'), `rc=${importRc}`);

    let cliOut = '';
    let cliRc = 0;
    try {
      cliOut = execFileSync(process.execPath, [CAPTURE_SCRIPT, '--list', '--app', 'fan'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
    } catch (e) {
      cliRc = e.status == null ? -1 : e.status;
      cliOut = String(e.stdout || '') + String(e.stderr || '');
    }
    cell('W2 cli-still-does-its-work', '대조군', true, cliRc === 0 && cliOut.includes('[portfolio]') && cliOut.includes('라우트 유도'), `rc=${cliRc}`);

    // ── 공허성 하한 ──────────────────────────────────────────────────────────
    const bites = cells.filter((c) => c.kind === 'bite').length;
    const controls = cells.filter((c) => c.kind === '대조군').length;
    const bad = cells.filter((c) => !c.ok);
    if (!bites || !controls) {
      console.error(`[staleness] ✗ self-test 픽스처가 공허합니다 (bite ${bites} · 대조군 ${controls})`);
      return 1;
    }
    console.log(`[staleness] self-test ${cells.length - bad.length}/${cells.length} (bite ${bites} · 대조군 ${controls})`);
    return bad.length ? 1 : 0;
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
}

// =============================================================================
function main() {
  const argv = process.argv.slice(2);
  if (argv.includes('--self-test')) {
    process.exit(selfTest());
  }

  const mi = argv.indexOf('--manifest');
  if (mi !== -1) {
    const p = argv[mi + 1];
    if (!p) {
      console.error('[staleness] ✗ --manifest 에 경로가 없습니다');
      process.exit(1);
    }
    const abs = resolve(p);
    if (!existsSync(abs)) {
      // 🔴 «매니페스트가 없다» 를 «낡은 것이 없다» 로 읽으면 안 된다.
      console.error(`[staleness] ✗ 매니페스트가 없습니다: ${abs}`);
      console.error('  🔵 전량 촬영본은 gitignore 된 `portfolio-captures/` 에 있습니다(이 저장소 밖일 수 있습니다).');
      process.exit(1);
    }
    let manifest;
    try {
      manifest = JSON.parse(readFileSync(abs, 'utf8'));
    } catch (e) {
      console.error(`[staleness] ✗ 매니페스트를 읽을 수 없습니다: ${e.message}`);
      process.exit(1);
    }
    console.log(`[staleness] 모드 B — 매니페스트 ${abs}`);
    const { problems, rows, checked } = checkManifest(manifest, APPS);
    console.log(`[staleness] shots ${checked}개를 유도 라우트와 대조했습니다 (통과 ${rows.length})`);
    if (problems.length) {
      console.error('[staleness] ✗ 낡은 캡처:');
      for (const m of problems) console.error(`  · ${m}`);
      console.error('');
      console.error('  🔴 다시 찍어야 합니다. 인증 경로라면 **데모 기동 창**이 필요합니다(이 파일 § 한계 ②).');
      process.exit(1);
    }
    console.log('[staleness] OK — 찍은 경로가 전부 아직 실재합니다.');
    return;
  }

  console.log('[staleness] 모드 A — 촬영 표면 (매니페스트 없이 · app/**/page.tsx 에서 유도)');
  const { problems, rows, probesDeclared } = checkSurface(APPS);
  for (const r of rows) {
    const probe = r.probe ? `probe ${r.probe} ${r.probeOk ? '✔' : '✗'}` : 'probe 없음';
    console.log(`  ${r.key.padEnd(8)} 라우트 ${String(r.routes).padStart(3)}  ${probe}${r.error ? '  ✗ ' + r.error : ''}`);
  }
  if (problems.length) {
    console.error('[staleness] ✗ 촬영 표면이 낡았습니다:');
    for (const m of problems) console.error(`  · ${m}`);
    process.exit(1);
  }
  console.log(`[staleness] OK — 앱 ${rows.length}개의 라우트 표면이 살아 있고 probe ${probesDeclared}개가 실재합니다.`);
}

if (!process.argv[1] || resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url))) {
  main();
}
