#!/usr/bin/env node
// PORTFOLIO: 세 앱의 **전 페이지**를 찍어 취업 포트폴리오·README 자산을 만든다 — TASK-MONO-648
//
// =============================================================================
// 이 스크립트가 `capture-shots.mjs` 와 다른 점
// =============================================================================
// `infra/demo/aws/site/capture-shots.mjs` 는 **론처 캐러셀용 8장**을 찍는다. 그 8장은
// 저장소에 커밋되고 `(z37)` 이 «참조 ↔ 파일» 을 양방향으로 지킨다.
//
// 이 스크립트는 **102장 전량**을 찍는다. 아무도 «참조» 하지 않으므로 (z37) 의 양방향
// 검사에 넣을 수 없고, 15–50MB 라 커밋하지도 않는다. ⇒ **별개 스크립트 · 별개 출력**이다.
//
// 🔴🔴 **로더를 공유 모듈로 빼지 마라 — 일부러 복제했다.**
//    `(z38)` 가드는 `capture-shots.mjs` 를 **임시 디렉터리로 파일 하나만 복사**해서 돌린다
//    (bite). 그 파일이 형제 모듈을 import 하면 복사본이 모듈을 못 찾고 죽고, 그러면 bite 가
//    **«틀린 이유로» 통과**한다 — 거짓 초록이다. 중복을 없애려면 **(z38) 을 먼저 고쳐라.**
//
// =============================================================================
// 사용
// =============================================================================
//   node scripts/capture-portfolio.mjs                 # 전량
//   node scripts/capture-portfolio.mjs --dry-run       # 라우트 유도 + 의존 해석까지만
//   node scripts/capture-portfolio.mjs --app console   # 한 앱만
//   node scripts/capture-portfolio.mjs --out <경로>    # 출력 위치 (기본: portfolio-captures/)
//   node scripts/capture-portfolio.mjs --no-auth       # 로그인 건너뜀 (공개 경로만 · 파이프라인 시험용)
//
// 종료코드 — 🔴 «미설치» 와 «고장» 은 다른 상태다(TASK-MONO-643 과 같은 규약):
//   0  정상 (실패한 페이지가 있어도, 그것이 «보고된» 상태면 0)
//   3  Playwright 미설치 — 고장이 아니다
//   1  그 밖의 실패 (백엔드가 안 떠 있음 · 로그인 불가 등)
//
// 🔴 선행: AMI 재굽기. 구워진 AMI 가 `main` 보다 낡으면 **옛 데이터가 담긴 화면**이 찍힌다.
//    TASK-MONO-648 AC-0 이 그것을 STOP 게이트로 걸어 두었다.
// =============================================================================

import { mkdir, writeFile } from 'node:fs/promises';
import { existsSync, readdirSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(HERE, '..');

// -----------------------------------------------------------------------------
// 무엇을 찍는가 — 앱마다 «어디서 라우트를 유도하고 어디로 요청하는가»
// -----------------------------------------------------------------------------
// 🔴 라우트 목록을 **손으로 적지 않는다.** 102개짜리 목록은 반드시 낡는다(TASK-MONO-648
//    AC-1). `app/**/page.tsx` 에서 유도한다 — 파일이 곧 라우트인 것이 Next 의 규약이다.
const APPS = {
  console: {
    label: '운영자 콘솔',
    appDir: 'projects/platform-console/apps/console-web/src/app',
    baseUrl: process.env.CONSOLE_URL || 'https://console.hubwang.com',
    // 🔴 콘솔은 보호 경로가 64개다. 로그인 없이 찍으면 전부 로그인 화면이 된다.
    auth: true,
    // 🔴🔴 그리고 **테넌트를 골라야** 5개 도메인의 운영자 권한이 붙는다(로그인 화면이 직접
    //    그렇게 말한다). 안 고르면 64장이 「권한 없음」으로 찍히고 그것은 200 이다.
    tenant: true,
    // 🔴 찍기 전에 한 장을 열어 «운영자 화면이 맞는지» 본다. 이 한 줄이 위 실패를 막는다.
    probe: '/dashboards/overview',
  },
  store: {
    label: '이커머스 스토어',
    appDir: 'projects/ecommerce-microservices-platform/apps/web-store/src/app',
    baseUrl: process.env.STORE_URL || 'https://store.hubwang.com',
    auth: true,
  },
  fan: {
    label: '팬 플랫폼',
    appDir: 'projects/fan-platform/web/fan-platform-web/src/app',
    baseUrl: process.env.FAN_URL || 'https://fan.hubwang.com',
    auth: true,
  },
};

// 🔴 뷰포트·스케일 **고정**. 안 그러면 재생성마다 이미지가 달라져 «바뀐 것이 화면인지 내
//    창 크기인지» 를 못 가른다(TASK-MONO-639 와 같은 이유).
const VIEWPORT = { width: 1440, height: 900 };
const SCALE = 1;
// 🔵 포트폴리오용은 **전체 페이지**를 찍는다 — 잘린 화면은 «이 제품의 화면» 이 아니다.
//    (론처 캐러셀은 반대로 fullPage:false 다 — 카드에 들어가는 것이라 첫 화면만 필요하다.)
const FULL_PAGE = true;
const FORMAT = { type: 'jpeg', quality: 85, ext: 'jpg' };

// -----------------------------------------------------------------------------
// 라우트 유도
// -----------------------------------------------------------------------------
function walkPages(dir, out = []) {
  let entries;
  try {
    entries = readdirSync(dir, { withFileTypes: true });
  } catch {
    return out;
  }
  for (const e of entries) {
    const p = join(dir, e.name);
    if (e.isDirectory()) walkPages(p, out);
    else if (e.name === 'page.tsx' || e.name === 'page.jsx') out.push(p);
  }
  return out;
}

const SEP = String.fromCharCode(92); // 백슬래시 — 리터럴로 쓰면 셸 경계에서 먹힌다

function fileToRoute(appDir, file) {
  let rel = relative(appDir, file).split(SEP).join('/');
  // 🔴 `(^|\/)` 가 필요하다. `\/page\.tsx$` 만 쓰면 **최상위 `app/page.tsx`** 에만 안 맞아서
  //    `/page.tsx` 라는 없는 경로가 나온다 — 그리고 그 실패는 조용하다(404 화면이 «찍힌다»).
  //    🔵 전수 조사에서만 잡혔다: 무작위 표본 18개는 이 한 건을 놓쳤다(86개 중 1개).
  rel = rel.replace(/(^|\/)page\.(tsx|jsx)$/, '');
  // Next 의 route group `(name)` 은 URL 에 안 나온다
  const segs = rel.split('/').filter((s) => s && !(s.startsWith('(') && s.endsWith(')')));
  return '/' + segs.join('/');
}

function deriveRoutes(app) {
  const dir = join(REPO_ROOT, app.appDir);
  if (!existsSync(dir)) return { error: `app 디렉터리가 없습니다: ${app.appDir}` };
  const routes = walkPages(dir).map((f) => fileToRoute(dir, f));
  const uniq = [...new Set(routes)].sort();
  return {
    all: uniq,
    static: uniq.filter((r) => !r.includes('[')),
    dynamic: uniq.filter((r) => r.includes('[')),
  };
}

// -----------------------------------------------------------------------------
// Playwright 해석 — 🔴 위 § 의 이유로 **일부러 복제**했다
// -----------------------------------------------------------------------------
function discoverPlaywright() {
  const found = [];
  const projects = join(REPO_ROOT, 'projects');
  if (!existsSync(projects)) return found;
  const dirsIn = (d) => {
    try {
      return readdirSync(d, { withFileTypes: true })
        .filter((e) => e.isDirectory() && e.name !== 'node_modules' && !e.name.startsWith('.'))
        .map((e) => join(d, e.name));
    } catch {
      return [];
    }
  };
  const hit = (d) => {
    const c = join(d, 'node_modules', '@playwright', 'test');
    if (existsSync(c) && !found.includes(c)) found.push(c);
  };
  for (const l1 of dirsIn(projects)) {
    hit(l1);
    for (const l2 of dirsIn(l1)) {
      hit(l2);
      for (const l3 of dirsIn(l2)) hit(l3);
    }
  }
  return found;
}

async function loadChromium(from) {
  const candidates = from ? [from] : discoverPlaywright();
  for (const c of candidates) {
    if (!existsSync(c)) continue;
    for (const entry of ['index.mjs', 'index.js']) {
      try {
        const url = 'file:///' + c.split(SEP).join('/') + '/' + entry;
        const mod = await import(url);
        if (mod.chromium) return { chromium: mod.chromium, at: c };
      } catch {
        /* 다음 후보 */
      }
    }
  }
  return { chromium: null, at: null, candidates };
}

// -----------------------------------------------------------------------------
// 로그인
// -----------------------------------------------------------------------------
// 🔴 자격을 **코드에 박지 않는다**(TASK-MONO-648 AC-1). 기본값은 화면이 이미 공개하고
//    있는 데모 계정이고(`DemoLoginCredentials.tsx`), 환경변수로 덮을 수 있다.
const DEMO_EMAIL = process.env.DEMO_EMAIL || 'demo@demo.com';
const DEMO_PASSWORD = process.env.DEMO_PASSWORD || '';

// 🔴🔴 **앱의 `/login` 에는 아이디·비밀번호 폼이 없다.** 2026-09-09 실측:
//    console `/login` → input 0개 · button 0개 · 링크 `/api/auth/login?redirect=%2F` 하나
//    fan     `/login` → 「GAP 로 로그인」 버튼(서버 액션) 하나
//    ⇒ 로그인은 **IAM 호스트로의 OIDC 리다이렉트**(Authorization Code + PKCE)이고,
//      실제 폼은 IAM 의 `auth-service` 가 그린다. 그 마크업은 리포에 있다(실측):
//        templates/login.html → input[type=email]#username[name=username]
//                               input[type=password]#password[name=password]
//                               button[type=submit]
//    🔴 이 사실을 모르고 앱 `/login` 에서 폼을 찾으면 **셀렉터가 0개를 찾고** 그대로 죽는다.
async function login(page, app) {
  if (!DEMO_PASSWORD) {
    return { ok: false, reason: 'DEMO_PASSWORD 가 없습니다 — 환경변수로 주세요' };
  }
  const res = await page.goto(app.baseUrl + '/login', { waitUntil: 'domcontentloaded', timeout: 45000 });
  if (!res || res.status() !== 200) {
    return { ok: false, reason: `로그인 화면이 ${res ? res.status() : '무응답'} 입니다` };
  }
  await page.waitForTimeout(1500);

  // --- ① OIDC 입구를 눌러 IAM 으로 간다 -------------------------------------
  // 🔵 두 모양을 다 받는다: 링크(console)와 버튼(fan). 하나로 박으면 앱이 늘 때 죽는다.
  // 🔴 세 앱이 **셋 다 다른 모양**이다(2026-09-09 실측):
  //    console  a[href="/api/auth/login?..."]  "IAM 계정으로 로그인"
  //    fan      button[type=submit]            "GAP 로 로그인"          (Next 서버 액션)
  //    store    button[type=button]            "Global Account 로 로그인"  ← type 이 submit 이 아니다
  //    ⇒ `button[type="submit"]` 만 쓰면 **store 를 놓친다**. 텍스트로도 잡는다.
  const entry = 'a[href*="/api/auth/login"], a[href*="/oauth2/authorization"], button:has-text("로그인")';
  const hasEntry = await page.locator(entry).count();
  if (!hasEntry) {
    return { ok: false, reason: `로그인 입구를 못 찾았습니다(${entry}) — 마크업이 바뀌었습니다` };
  }
  await page.locator(entry).first().click().catch(() => {});
  // IAM 은 다른 호스트다. 리다이렉트가 끝날 때까지 기다린다.
  await page.waitForLoadState('domcontentloaded', { timeout: 45000 }).catch(() => {});
  await page.waitForTimeout(2500);

  // --- ② IAM 의 폼을 채운다 --------------------------------------------------
  const userSel = 'input[type="email"], input[name="username"], #username';
  const passSel = 'input[type="password"], input[name="password"], #password';
  if (!(await page.locator(userSel).count()) || !(await page.locator(passSel).count())) {
    // 🔴 여기서 실패하면 «IAM 이 안 떴다» 이거나 «폼이 바뀌었다» 다 — 둘을 구별해서 적는다.
    return {
      ok: false,
      reason: `IAM 로그인 폼을 못 찾았습니다 (현재 URL: ${page.url()}). 데모 백엔드가 떠 있는지, 그리고 auth-service 의 templates/login.html 이 바뀌지 않았는지 보세요`,
    };
  }
  await page.fill(userSel, DEMO_EMAIL, { timeout: 15000 });
  await page.fill(passSel, DEMO_PASSWORD, { timeout: 15000 });
  await page.locator('button[type="submit"]').first().click().catch(() => {});
  await page.waitForLoadState('domcontentloaded', { timeout: 45000 }).catch(() => {});
  await page.waitForTimeout(3000);

  // 🔴 «로그인 화면을 벗어났는가» 로 판정한다. 「버튼을 눌렀다」는 성공의 증거가 아니다.
  if (/\/login(\?|$)/.test(page.url())) {
    return { ok: false, reason: `여전히 로그인 화면입니다: ${page.url()}` };
  }
  return { ok: true, landedOn: page.url() };
}

// -----------------------------------------------------------------------------
// 테넌트 선택 — 🔴🔴 빠뜨리면 64페이지가 «권한 없음» 으로 찍히고 그것은 200 이다
// -----------------------------------------------------------------------------
// 콘솔 로그인 화면이 직접 말한다(2026-09-09 실측):
//   *"로그인한 뒤 테넌트를 demo-corp 로 선택하세요. 5개 도메인(이커머스·WMS·SCM·ERP·재무)의
//     운영자 권한은 **그 시점에** 부여됩니다."*
// `TenantSwitcher` 는 `<select data-testid="tenant-select">` 이고 선택이 `/api/tenant` 로
// **쿠키**를 세팅한다(테넌트가 하나뿐이면 `data-testid="tenant-single"` 로 이미 정해져 있다).
const TENANT = process.env.DEMO_TENANT || 'demo-corp';

async function selectTenant(page, app) {
  await page.goto(app.baseUrl + '/console', { waitUntil: 'domcontentloaded', timeout: 45000 }).catch(() => {});
  await page.waitForTimeout(2000);
  if (await page.locator('[data-testid="tenant-single"]').count()) {
    return { ok: true, how: 'single', tenant: (await page.locator('[data-testid="tenant-single"]').innerText()).trim() };
  }
  const sel = page.locator('[data-testid="tenant-select"]');
  if (!(await sel.count())) {
    return { ok: false, reason: '테넌트 선택 UI 가 없습니다 — 마크업이 바뀌었거나 로그인이 안 됐습니다' };
  }
  try {
    await sel.selectOption({ value: TENANT });
  } catch {
    // 🔵 value 가 아니라 라벨일 수 있다. 한 번 더 시도하고, 그래도 안 되면 이름을 대며 실패한다.
    try {
      await sel.selectOption({ label: TENANT });
    } catch (e) {
      const opts = await sel.locator('option').allTextContents();
      return { ok: false, reason: `테넌트 '${TENANT}' 를 못 골랐습니다. 후보: ${opts.join(' | ')}` };
    }
  }
  await page.waitForTimeout(2500);
  return { ok: true, how: 'select', tenant: TENANT };
}

// 🔴🔴 로그인·테넌트 뒤에 **한 장을 시험 삼아 열어** 운영자 화면이 맞는지 본다.
//    이게 없으면 「권한 없음」 64장을 «성공» 으로 세고, 그 실패는 조용하다(이미지는 생긴다).
const DENIED_MARKERS = ['권한이 없습니다', '접근 권한', '테넌트를 선택', 'Forbidden', '403'];

async function sanityCheck(page, app, probePath) {
  const res = await page.goto(app.baseUrl + probePath, { waitUntil: 'domcontentloaded', timeout: 45000 });
  await page.waitForTimeout(2000);
  if (!res || res.status() !== 200) return { ok: false, reason: `${probePath} 가 ${res ? res.status() : '무응답'} 입니다` };
  if (/\/login(\?|$)/.test(page.url())) return { ok: false, reason: `${probePath} 가 로그인으로 튕겼습니다` };
  const text = await page.evaluate(() => document.body.innerText);
  const hit = DENIED_MARKERS.find((m) => text.includes(m));
  if (hit) return { ok: false, reason: `${probePath} 가 «${hit}» 를 그리고 있습니다 — 테넌트/권한이 안 잡혔습니다` };
  return { ok: true, probePath, chars: text.length };
}

// -----------------------------------------------------------------------------
// 동적 경로 해결
// -----------------------------------------------------------------------------
// 🔴 id 를 **손으로 박지 않는다**(TASK-MONO-648 AC-1). 시드가 바뀌는 날 404 를 찍고,
//    그 실패는 **조용하다** — 이미지는 생기기 때문이다.
//    목록 페이지에서 **실제 링크**를 하나 골라 따라간다.
async function resolveDynamic(page, app, route) {
  // "/ecommerce/products/[id]/edit" → 부모 목록 "/ecommerce/products", 꼬리 "/edit"
  const idx = route.indexOf('/[');
  const parent = route.slice(0, idx);
  const tail = route.slice(route.indexOf(']', idx) + 1);
  // 🔴 «어느 페이지를 볼 것인가»(visit)와 «어떤 접두사의 링크를 찾을 것인가»(prefix)는
  //    **다른 것**이다. 처음엔 하나로 합쳐 뒀는데, 홈(`/`)에서 찾을 때 접두사가 `'//'` 가
  //    되어 **링크가 눈앞에 있는데 0건**이 나왔다(2026-09-09 실측: fan 홈에 `/posts/…` 10개).
  const findLink = async (visit, prefix) => {
    const res = await page.goto(app.baseUrl + visit, { waitUntil: 'domcontentloaded', timeout: 45000 });
    if (!res || res.status() !== 200) return { status: res ? res.status() : 0, href: null };
    await page.waitForTimeout(1500);
    const href = await page.evaluate((p) => {
      const links = [...document.querySelectorAll('a[href]')].map((a) => a.getAttribute('href'));
      // `/x/new` 는 생성 화면이라 «어떤 항목» 이 아니다. 하위 경로가 더 붙은 것도 제외
      // (`/x/1/edit` 를 잡으면 꼬리가 두 번 붙는다).
      const cand = links.filter(
        (h) => h && h.startsWith(p + '/') && h !== p + '/new' && h.slice(p.length + 1).indexOf('/') === -1,
      );
      return cand[0] || null;
    }, prefix);
    return { status: 200, href };
  };

  // ① 부모 목록에서 찾는다 — 대개 여기서 끝난다
  const r = await findLink(parent, parent);
  if (r.href) return { ok: true, path: r.href + tail, via: parent };

  // ② 🔵 부모 «목록 페이지» 가 없는 경우가 실제로 있다 — fan 의 `/posts/[id]` 가 그렇다
  //    (실측: `/posts` 는 404 이고 링크는 피드 `/` 에 있다). 앱 홈에서 **같은 접두사**로
  //    한 번 더 찾는다. 🔴 그래도 못 찾으면 **실패로 세고 이름을 댄다** — 아무거나 집어넣지 않는다.
  const home = await findLink('/', parent);
  if (home.href) return { ok: true, path: home.href + tail, via: '/' };

  return {
    ok: false,
    reason:
      r.status === 200
        ? `목록에 따라갈 항목이 없습니다: ${parent} (홈에서도 못 찾음)`
        : `부모 목록이 ${r.status}: ${parent} — 홈(/)에서도 ${parent}/… 링크를 못 찾았습니다`,
  };
}

// -----------------------------------------------------------------------------
// 한 장 찍기
// -----------------------------------------------------------------------------
// 🔴 실패를 **분류**한다. 「로그인으로 튕겼다」를 «그 페이지의 캡처» 로 저장하면
//    포트폴리오가 거짓이 된다(TASK-MONO-648 Failure Scenario 2).
async function captureOne(page, app, appKey, route, path, outDir) {
  const url = app.baseUrl + path;
  const name = (appKey + (path === '/' ? '/home' : path))
    .replace(/[^A-Za-z0-9/_-]/g, '_')
    .split('/')
    .filter(Boolean)
    .join('__');
  const file = `${name}.${FORMAT.ext}`;
  try {
    const res = await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 45000 });
    const status = res ? res.status() : 0;
    if (status !== 200) return { route, path, file, ok: false, kind: 'http', status, url };
    await page.waitForLoadState('load', { timeout: 20000 }).catch(() => {});
    await page.waitForTimeout(1500);
    if (/\/login(\?|$)/.test(page.url()) && !/\/login(\?|$)/.test(path)) {
      return { route, path, file, ok: false, kind: 'redirected-to-login', status, url, landedOn: page.url() };
    }
    await page.screenshot({
      path: join(outDir, file),
      fullPage: FULL_PAGE,
      type: FORMAT.type,
      quality: FORMAT.quality,
    });
    return { route, path, file, ok: true, status, url, capturedAt: new Date().toISOString() };
  } catch (e) {
    return { route, path, file, ok: false, kind: 'error', reason: e.message, url };
  }
}

// -----------------------------------------------------------------------------
async function main() {
  const argv = process.argv.slice(2);
  const dry = argv.includes('--dry-run');
  const noAuth = argv.includes('--no-auth');
  const onlyApp = argv.includes('--app') ? argv[argv.indexOf('--app') + 1] : null;
  const from = argv.includes('--from') ? argv[argv.indexOf('--from') + 1] : null;
  const outDir = resolve(
    argv.includes('--out') ? argv[argv.indexOf('--out') + 1] : join(REPO_ROOT, 'portfolio-captures'),
  );

  const keys = onlyApp ? [onlyApp] : Object.keys(APPS);
  for (const k of keys) {
    if (!APPS[k]) {
      console.error(`[portfolio] ✗ 모르는 앱: ${k} (아는 것: ${Object.keys(APPS).join(', ')})`);
      process.exit(1);
    }
  }

  // --- 라우트 유도 (기동 없이도 된다) ---
  const plan = {};
  let totalStatic = 0;
  let totalDynamic = 0;
  console.log('[portfolio] 라우트 유도 — app/**/page.tsx 에서');
  for (const k of keys) {
    const r = deriveRoutes(APPS[k]);
    if (r.error) {
      console.error(`[portfolio] ✗ ${k}: ${r.error}`);
      process.exit(1);
    }
    plan[k] = r;
    totalStatic += r.static.length;
    totalDynamic += r.dynamic.length;
    console.log(`  ${k.padEnd(8)} ${APPS[k].label.padEnd(12)} 정적 ${String(r.static.length).padStart(3)} · 동적 ${String(r.dynamic.length).padStart(2)} · 합계 ${r.all.length}`);
  }
  console.log(`[portfolio] 합계 ${totalStatic + totalDynamic} 페이지 (정적 ${totalStatic} · 동적 ${totalDynamic})`);

  // 🔵 `--list` — 유도된 경로를 **눈으로 확인**하는 자리. route group `(console)` 제거가
  //    틀리면 전량이 404 를 찍는데, 그 실패는 「이미지가 안 생긴다」가 아니라
  //    「404 화면이 102장 생긴다」로 온다. 찍기 전에 목록을 볼 수 있어야 한다.
  if (argv.includes('--list')) {
    for (const k of keys) {
      console.log(`\n--- ${k} (${APPS[k].label}) ---`);
      for (const r of plan[k].all) console.log(`  ${APPS[k].baseUrl}${r}`);
    }
    return;
  }

  // --- 의존 해석 ---
  // 🔴 `--dry-run` 보다 **먼저** 한다. 반대 순서면 «못 찾는 상태» 를 rc=0 으로 통과시킨다
  //    (TASK-MONO-643 이 고친 결함과 같은 부류).
  const { chromium, at, candidates } = await loadChromium(from);
  if (!chromium) {
    console.error('[portfolio] ⚠ Playwright 가 설치돼 있지 않습니다 (rc=3 — 고장이 아닙니다).');
    console.error(`  → projects/ 아래 깊이 1–3 을 훑었고 후보를 못 찾았습니다(${(candidates || []).length}건).`);
    console.error('  → 워크스페이스 중 하나에서 install 하거나 --from <@playwright/test 경로> 를 주세요.');
    process.exit(3);
  }
  console.log(`[portfolio] Playwright: ${at}`);

  if (dry) {
    console.log('[portfolio] --dry-run — 라우트 유도와 의존 해석까지 마쳤고 찍지 않았습니다.');
    return;
  }

  await mkdir(outDir, { recursive: true });
  console.log(`[portfolio] 출력: ${outDir}`);

  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: VIEWPORT, deviceScaleFactor: SCALE });
  const page = await context.newPage();
  const shots = [];
  const failures = [];

  for (const k of keys) {
    const app = APPS[k];
    console.log(`\n[portfolio] === ${k} (${app.label}) ${app.baseUrl}`);

    // 🔵 `--no-auth` — 로그인을 건너뛴다. 두 쓸모가 있다:
    //    ① 데모가 꺼져 있어도 **공개 경로는 찍힌다**(포트폴리오의 공개 표면 자산).
    //    🔴 ② 그리고 이 모드가 아니면 **파이프라인을 시험할 방법이 없다** — 로그인이
    //       실패하면 앱을 통째로 건너뛰므로 goto·screenshot·manifest·실패분류가 한 번도
    //       안 돌아 본다. 기동 창에서 처음 돌리는 코드는 그 자체가 위험이다.
    if (app.auth && !noAuth) {
      const l = await login(page, app);
      if (!l.ok) {
        // 🔴 로그인이 안 되면 이 앱의 보호 경로는 **전부 거짓 캡처**가 된다. 찍지 않는다.
        console.error(`[portfolio] ✗ ${k} 로그인 실패 — 이 앱을 건너뜁니다: ${l.reason}`);
        failures.push({ app: k, kind: 'login', reason: l.reason });
        continue;
      }
      console.log(`[portfolio] ✔ 로그인 → ${l.landedOn}`);

      if (app.tenant) {
        const t = await selectTenant(page, app);
        if (!t.ok) {
          // 🔴 테넌트가 안 잡히면 64페이지가 「권한 없음」으로 찍힌다. 그것은 200 이라
          //    «성공» 으로 세어진다 ⇒ 찍지 않고 이 앱을 건너뛴다.
          console.error(`[portfolio] ✗ ${k} 테넌트 선택 실패 — 이 앱을 건너뜁니다: ${t.reason}`);
          failures.push({ app: k, kind: 'tenant', reason: t.reason });
          continue;
        }
        console.log(`[portfolio] ✔ 테넌트 ${t.tenant} (${t.how})`);
      }

      if (app.probe) {
        const s = await sanityCheck(page, app, app.probe);
        if (!s.ok) {
          console.error(`[portfolio] ✗ ${k} 사전 확인 실패 — 이 앱을 건너뜁니다: ${s.reason}`);
          failures.push({ app: k, kind: 'sanity', reason: s.reason });
          continue;
        }
        console.log(`[portfolio] ✔ 사전 확인 ${s.probePath} (본문 ${s.chars}자)`);
      }
    }

    for (const route of plan[k].static) {
      const r = await captureOne(page, app, k, route, route, outDir);
      if (r.ok) {
        shots.push({ app: k, ...r });
        console.log(`  ✔ ${route}`);
      } else {
        failures.push({ app: k, ...r });
        console.log(`  ✗ ${route}  [${r.kind}${r.status ? ' ' + r.status : ''}]`);
      }
    }

    for (const route of plan[k].dynamic) {
      const d = await resolveDynamic(page, app, route);
      if (!d.ok) {
        failures.push({ app: k, route, kind: 'dynamic-unresolved', reason: d.reason });
        console.log(`  ✗ ${route}  [동적 해결 실패] ${d.reason}`);
        continue;
      }
      const r = await captureOne(page, app, k, route, d.path, outDir);
      if (r.ok) {
        shots.push({ app: k, ...r, resolvedFrom: route });
        console.log(`  ✔ ${route} → ${d.path}`);
      } else {
        failures.push({ app: k, ...r });
        console.log(`  ✗ ${route} → ${d.path}  [${r.kind}${r.status ? ' ' + r.status : ''}]`);
      }
    }
  }

  await browser.close();

  const manifest = {
    generatedAt: new Date().toISOString(),
    viewport: VIEWPORT,
    deviceScaleFactor: SCALE,
    fullPage: FULL_PAGE,
    format: FORMAT,
    apps: Object.fromEntries(keys.map((k) => [k, { label: APPS[k].label, baseUrl: APPS[k].baseUrl }])),
    counts: { planned: totalStatic + totalDynamic, captured: shots.length, failed: failures.length },
    shots,
    failures,
  };
  await writeFile(join(outDir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n', 'utf8');

  console.log('\n[portfolio] ─────────────────────────────');
  console.log(`[portfolio] 계획 ${totalStatic + totalDynamic} · 찍음 ${shots.length} · 실패 ${failures.length}`);
  if (failures.length) {
    // 🔴 실패를 **이름을 대며** 보고한다. 전부 성공해야 한다는 뜻이 아니라,
    //    «어느 것이 왜» 가 적히면 된다(TASK-MONO-648 AC-1).
    console.log('[portfolio] 실패 목록:');
    for (const f of failures) console.log(`  ${(f.app || '?').padEnd(8)} ${f.route || '(앱 전체)'}  [${f.kind}] ${f.reason || f.status || ''}`);
  }
  console.log('[portfolio] manifest.json 기록');
}

main().catch((e) => {
  console.error(`[portfolio] ✗ ${e && e.stack ? e.stack : e}`);
  process.exit(1);
});
