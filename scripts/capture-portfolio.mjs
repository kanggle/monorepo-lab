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
//   node scripts/capture-portfolio.mjs --self-test     # 거부 판정기를 창 없이 브라우저 픽스처로 잰다
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

  // --- ② IAM 의 폼을 채운다 --------------------------------------------------
  const userSel = 'input[type="email"], input[name="username"], #username';
  const passSel = 'input[type="password"], input[name="password"], #password';

  // 🔴🔴 **시간으로 기다리지 않는다** (2026-09-09 실측이 이 줄을 다시 쓰게 했다).
  //    이전 판은 `waitForLoadState('domcontentloaded')` + `waitForTimeout(2500)` 였는데,
  //    앞엣것은 **다음 항해를 기다려 주지 않는다** — «지금 문서» 가 이미 로드돼 있으면
  //    즉시 돌아온다. 그래서 실질 대기는 고정 2.5초뿐이었고, 홉이 하나 더인 앱이 졌다:
  //
  //      console  a[href="/api/auth/login"] → Next 라우트 핸들러 → 302 → auth.hubwang.com
  //      fan      같은 모양 — 둘 다 실패
  //      store    홉이 짧아 **우연히** 2.5초 안에 도착 → 통과
  //
  //    🔵 그 초록은 «맞다» 가 아니라 «그날 빨랐다» 였다. 시간으로 기다리면 느린 날 store 도
  //       진다. 그리고 그 패배는 **로그인 화면 87장**으로 조용히 저장된다.
  //    ⇒ 판정을 **조건**으로 바꾼다: 비밀번호 칸이 나타날 때까지 기다린다. 안 나오면
  //       그때가 진짜 실패이고, 아래 진단이 «어디서» 멈췄는지 URL 로 말한다.
  await page.waitForSelector(passSel, { timeout: 45000 }).catch(() => {});

  if (!(await page.locator(userSel).count()) || !(await page.locator(passSel).count())) {
    // 🔴 여기서 실패하면 셋 중 하나다 — 구별해서 적는다.
    //    · 아직 앱 오리진이면  → 리다이렉트가 시작조차 안 했다(입구를 잘못 눌렀다)
    //    · IAM 호스트인데 없으면 → 폼이 바뀌었거나 IAM 이 안 떴다
    const stillOnApp = page.url().startsWith(app.baseUrl);
    return {
      ok: false,
      reason: stillOnApp
        ? `로그인 입구를 눌렀는데 IAM 으로 넘어가지 않았습니다 (여전히 ${page.url()}). 입구 셀렉터가 엉뚱한 원소를 잡았는지 보세요`
        : `IAM 로그인 폼을 못 찾았습니다 (현재 URL: ${page.url()}). 데모 백엔드가 떠 있는지, 그리고 auth-service 의 templates/login.html 이 바뀌지 않았는지 보세요`,
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

// 🔴🔴 **전환은 왕복이다** (TASK-MONO-707). 셀렉트가 이미 그 값이면 재선택은 **no-op** 이고
//    `change` 가 안 나서 `/api/tenant` 도 안 불린다 ⇒ 쿠키가 안 서고, 화면은 «테넌트를 선택
//    하세요» 를 그린다. 2026-09-17 창 실측: `DEMO_TENANT=ecommerce` 실행이 사전 점검에서
//    앱 전체를 건너뛰었다(계획 67 · 찍음 **0**). 같은 창에서 `demo-corp` → `ecommerce` 로
//    **왕복**하자 열렸다.
// 🔵 그래서 판정을 «골랐다» 가 아니라 **«그 테넌트가 실제로 잡혔는가»** 로 바꾼다 —
//    셀렉트 값을 되읽고, 안 맞으면 다른 테넌트를 경유해 한 번 더 시도한다.
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

  const options = await sel.locator('option').evaluateAll((els) =>
    els.map((e) => ({ value: e.value, label: (e.textContent || '').trim() })),
  );
  const target = options.find((o) => o.value === TENANT || o.label === TENANT);
  if (!target) {
    return { ok: false, reason: `테넌트 '${TENANT}' 가 셀렉트에 없습니다. 후보: ${options.map((o) => o.value).join(' | ')}` };
  }

  const pick = async (value) => {
    await sel.selectOption({ value }).catch(() => {});
    await page.waitForTimeout(2500);
  };

  // 🔴🔴 **판정은 «골랐다» 가 아니라 «적용됐다» 다.** 셀렉트 값만 읽으면 «화면은 그 테넌트를
  //    가리키는데 서버 쿠키는 안 섰다» 를 통과시킨다 — 2026-09-17 실패가 정확히 그 모양이었다
  //    (사전 점검이 «테넌트를 선택» 을 그리는 화면을 봤다). 그래서 **한 장을 다시 열어**
  //    셀렉트 값과 «테넌트를 선택» 안내의 부재를 **둘 다** 본다.
  const applied = async () => {
    await page.goto(app.baseUrl + '/dashboards/overview', { waitUntil: 'domcontentloaded', timeout: 45000 }).catch(() => {});
    await page.waitForTimeout(2000);
    const value = await page.locator('[data-testid="tenant-select"]').inputValue().catch(() => '');
    const body = await page.evaluate(() => (document.body ? document.body.innerText : '')).catch(() => '');
    return { value, needsTenant: /테넌트를\s*선택/.test(body) };
  };

  await pick(target.value);
  let seen = await applied();

  if (seen.value !== target.value || seen.needsTenant) {
    // 2차: **경유** — 다른 테넌트로 갔다가 돌아온다(같은 값 재선택은 no-op 이라 `change` 가 안 난다).
    //    경유지가 없으면 그 사실 자체가 실패 사유다.
    const detour = options.find((o) => o.value && o.value !== target.value);
    if (!detour) {
      return { ok: false, reason: `테넌트 '${TENANT}' 가 안 잡혔고 경유할 다른 테넌트도 없습니다(옵션 ${options.length}개)` };
    }
    await page.goto(app.baseUrl + '/console', { waitUntil: 'domcontentloaded', timeout: 45000 }).catch(() => {});
    await page.waitForTimeout(1500);
    await pick(detour.value);
    await pick(target.value);
    seen = await applied();
  }

  if (seen.value !== target.value || seen.needsTenant) {
    return {
      ok: false,
      reason: `테넌트 '${TENANT}' 가 끝내 적용되지 않았습니다 (셀렉트 '${seen.value}'${seen.needsTenant ? ' · 화면이 «테넌트를 선택» 을 그림' : ''}) — 왕복 전환까지 했습니다`,
    };
  }
  return { ok: true, how: 'select', tenant: TENANT, roundTrip: true };
}

// 🔴🔴 로그인·테넌트 뒤에 **한 장을 시험 삼아 열어** 운영자 화면이 맞는지 본다.
//    이게 없으면 「권한 없음」 64장을 «성공» 으로 세고, 그 실패는 조용하다(이미지는 생긴다).
const DENIED_MARKERS = ['권한이 없습니다', '접근 권한', '테넌트를 선택', 'Forbidden', '403'];

// -----------------------------------------------------------------------------
// 권한 거부 판정 — 🔴🔴 본문 **문구** 가 아니라 거부 화면이 렌더하는 **요소** 로 (TASK-MONO-648 AC-1b)
// -----------------------------------------------------------------------------
// 2026-09-12 창에서 `/ecommerce/guide` · `/erp/guide` · `/scm/guide` 가 «권한 거부» 로 세어졌다.
// 세 가이드엔 권한 가드가 없다 — 본문이 *설명 목적으로* «403»·«접근 권한이 없습니다» 를
// **인용**했을 뿐이다(`features/{ecommerce,scm,erp}-guide/data.ts`). 판별자가 자기 설명 문구에 걸렸다.
//
// 🔵 콘솔의 거부 화면은 전부 접미사가 정해진 `data-testid` 를 단다(2026-09-16 전수):
//      `-permission-denied`(IAM 화면) · `-not-eligible`(도메인 자격 없음) · `-forbidden`(403)
//    동적 상세 화면도 `note('product-forbidden', …)` 처럼 같은 접미사다.
// 🔴 **`-card-…-forbidden` 은 페이지 거부가 아니다** — `/dashboards/overview` 의 도메인 카드
//    하나가 막힌 것이고 화면의 나머지는 산다(`DomainCardStates.tsx`). 페이지 거부로 세면
//    대시보드가 **새 오탐**이 된다 ⇒ `partial` 로 따로 센다.
// 🔴 본문 문구 적중은 **판정에서 뺐지만 버리지 않는다** — 마커를 안 단 거부 화면이 새로 생기면
//    요소 판정은 그것을 못 본다. 실패로 세지 않고 `deniedTextOnly` 로 남겨 **사람이 그림을 연다**
//    (틀릴 때 일이 늘어나는 쪽으로 틀린다).
//
// 🔴🔴 **섹션 하나의 거부도 같은 접미사다** (TASK-MONO-702). 2026-09-17 창에서 `/wms/operations`
//    가 `denied` 로 세어져 사진이 안 남았다 — 위 «운영 설정» 은 살아 있고 아래 «프로젝션 상태»
//    섹션**만** `wms-operations-projection-forbidden` 이었다. 같은 모양이 `settlements-*-forbidden` ·
//    `ledger` 패널 · `wms-asn-inspection` 등 수십 곳에 있어 **이름으로는 못 가른다**(`-card-` 에
//    `-projection-` 을 더하는 식의 목록은 다음 모양이 또 샌다).
// ⇒ **구조로 가른다**: 주 영역(`<main>`, 없으면 body)을 복제해 거부 요소 전부와 «본문이 아닌 것»
//    (제목 · 링크 · 버튼 · 폼 컨트롤 · 탭/내비 · 화면에 안 그려지는 것)을 지우고 **남는 글자** 를 센다.
//      남는 글자 0  → 거부 말고는 보여 줄 게 없다 = 페이지 거부(`denied`).
//                    제목+거부+«목록으로» 화면, 섹션 둘 다 거부인 화면이 여기 온다.
//      남는 글자 >0 → 거부 밖에 다른 본문(표 · 설명 · 다른 섹션의 «불러올 수 없음» 안내)이 있다
//                    = 섹션 거부(`partial`) — 사진은 남기고 사람이 연다.
//    🔵 틀리면 «사진을 남기는» 쪽으로 틀린다(`partial` 로 표시되니 큐레이션에서 걸러진다).
//    🔵 대시보드 카드(`-card-`)는 남는 글자와 무관하게 계속 `partial` — 옛 판정을 바꾸지 않는다.
const DENIAL_SUFFIXES = ['-permission-denied', '-not-eligible', '-forbidden'];

// 🔴 이 함수는 **브라우저 안에서** 돈다(`page.evaluate`) — 바깥 변수를 닫아 쓰지 말고 인자로 받는다.
function judgeDenialInPage({ suffixes, textMarkers }) {
  const sel = suffixes.map((s) => `[data-testid$="${s}"]`).join(',');
  const cards = [];
  const others = [];
  for (const el of document.querySelectorAll(sel)) {
    const id = el.getAttribute('data-testid');
    (/-card-/.test(id) ? cards : others).push(id);
  }
  // 거부 요소 밖에 남는 본문 글자 수 — 위 «구조로 가른다».
  let residual = 0;
  if (others.length) {
    const root = document.querySelector('main') || document.body;
    const clone = root ? root.cloneNode(true) : null;
    if (clone) {
      const notBody = [
        sel,
        'h1,h2,h3,h4,h5,h6',
        'a,button,nav,[role="tablist"],[role="tab"]',
        'form label,input,select,textarea,option',
        'script,style,template,[hidden],[aria-hidden="true"],.sr-only',
      ].join(',');
      for (const el of clone.querySelectorAll(notBody)) el.remove();
      residual = (clone.textContent || '').replace(/\s+/g, '').length;
    }
  }
  const pageDenied = others.length > 0 && residual === 0;
  const text = document.body ? document.body.innerText : '';
  return {
    denied: pageDenied,
    deniedBy: pageDenied ? others : [],
    partial: pageDenied ? cards : [...cards, ...others],
    residual,
    textHits: textMarkers.filter((m) => text.includes(m)),
    chars: text.length,
  };
}

const judgeDenial = (page) =>
  page.evaluate(judgeDenialInPage, { suffixes: DENIAL_SUFFIXES, textMarkers: DENIED_MARKERS });

// 🔵 항해와 **판정**을 나눈다 — 판정만 따로 부를 수 있어야 `--self-test` 가 픽스처로
//    이 배선을 물 수 있다. 붙여 두면 self-test 는 `judgeDegraded()` 의 사본을 재게 되고,
//    「프로브가 그것을 부르는가」라는 이 티켓의 질문은 **아무도 안 묻는 채로** 남는다.
async function judgeProbe(page, probePath) {
  const j = await judgeDenial(page);
  if (j.denied) {
    return { ok: false, reason: `${probePath} 가 거부 요소 ${j.deniedBy.join(', ')} 를 그리고 있습니다 — 테넌트/권한이 안 잡혔습니다` };
  }
  // 🔵 프로브는 문구 적중도 **여전히 막는다.** 장별 판정과 다르게 두는 이유: 프로브 경로는
  //    문서 페이지가 아닌 고정 대시보드라 «자기 설명 문구» 오탐의 모집단이 아니고, «테넌트를 먼저
  //    선택하세요» 안내는 마커가 **없다**(`accounts/page.tsx` 등). 여기서 놓치면 앱 전체가 거짓 캡처다.
  if (j.textHits.length) {
    return { ok: false, reason: `${probePath} 가 «${j.textHits[0]}» 를 그리고 있습니다 — 테넌트/권한이 안 잡혔습니다` };
  }

  // --- TASK-MONO-711 ① — 사전 확인이 **저하도 본다** ------------------------
  // 🔴🔴 **경고이지 중단이 아니다.** 저하를 `ok:false` 로 만들면 «저하를 일부러 찍는»
  //    측정이 불가능해진다 — `TASK-MONO-707` AC-3 이 정확히 그것을 했다(재무를 내리고
  //    `/ledger` 가 degraded 로 잡히는지 봤다). 그래서 `ok` 는 건드리지 않고 사실만 얹는다.
  // 🔴 왜 필요한가: 2026-09-18 창에서 프로브가 `✔ 사전 확인 /dashboards/overview (본문 220자)`
  //    를 찍고 통과했는데, **그 220자가 저하 문구 자체**였다. 프로브는 거부·빈값만 보고
  //    저하를 안 봤고, 그래서 68장이 저하 화면 위에서 찍히고도 「정상」으로 보고됐다.
  //    판정기(`judgeDegraded`)는 이미 있었다 — 프로브가 **안 부른 것**이 전부다.
  const text = await page.evaluate(() => (document.body ? document.body.innerText : ''));
  const d = await judgeDegraded(page, text);
  return { ok: true, probePath, chars: j.chars, degraded: d.degraded, degradedBy: d.degradedBy };
}

async function sanityCheck(page, app, probePath) {
  const res = await page.goto(app.baseUrl + probePath, { waitUntil: 'domcontentloaded', timeout: 45000 });
  await page.waitForTimeout(2000);
  if (!res || res.status() !== 200) return { ok: false, reason: `${probePath} 가 ${res ? res.status() : '무응답'} 입니다` };
  if (/\/login(\?|$)/.test(page.url())) return { ok: false, reason: `${probePath} 가 로그인으로 튕겼습니다` };
  return judgeProbe(page, probePath);
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
// 본문 표지 — 🔴🔴 «찍혔다» 는 «볼 만한 것이 찍혔다» 가 **아니다**
// -----------------------------------------------------------------------------
// 2026-09-09 실측이 이 절을 만들었다. 콘솔 `/ecommerce/products` 는 HTTP 200 · 로그인됨 ·
// 테넌트 `demo-corp` 선택됨 · 「권한 없음」 아님인데 본문이 **「표시할 상품이 없습니다」**
// 였다. `/dashboards/overview` 는 **「통합 개요를 일시적으로 불러올 수 없습니다」** 였다.
// 둘 다 `sanityCheck()` 를 통과했고 **성공으로 집계됐다.**
//
// 🔴 취업 자료에 넣을 그림으로 빈 표는 «안 만든 제품» 처럼 읽힌다 — 그림이 거짓말을 하는
//    또 하나의 문이고, 앞문(로그인 화면)만 막아 둔 상태였다.
// 🔵 **고치지 않고 드러낸다.** 데이터가 왜 비었는지는 이 스크립트의 축이 아니다(별도 티켓).
//    여기서 할 일은 «이 장은 큐레이션 후보가 아니다» 를 기계가 말하게 하는 것이다.
// 🔴 실패로 세지 않는다 — 캡처는 **성공했다.** 빈 화면의 정직한 사진이다. 별도 범주로 센다.
const EMPTY_RE = /표시할\s*[^.\n]{0,24}없습니다|(?:데이터|결과|항목|내역)[가이]?\s*없습니다|비어\s*있습니다/;
// 🔴🔴 **문구만으로는 샌다** (TASK-MONO-707). 옛 정규식은 «**일시적으로** 불러올 수 없» 을
//    요구했는데, `/ledger` 는 *"시산표를 불러올 수 없습니다"* 라 안 걸렸고 그 오류 화면이
//    큐레이션 후보 목록에 «쓸 만한 화면» 으로 올라왔다(사람이 이미지를 열어서 걸렀다).
//    2026-09-17 전수: 본문에 «불러올 수 없습니다» 를 쓰는 파일 **96** vs «일시적으로» **90**.
const DEGRADED_RE = /불러올\s*수\s*없|잠시\s*후\s*다시\s*시도|오류가\s*발생/;
// 🔵 그리고 문구에 **기대지 않는 두 번째 술어**를 둔다 — 저하 화면이 다는 `data-testid` 접미사.
//    2026-09-17 전수(console-web/src): `-degraded` 96 · `-error` 62 · `-unavailable` 8 · `-stale` 3.
//    🔴 접미사 하나만 보면 안 된다 — `/ledger` 의 마커는 `ledger-tb-unavailable` 이다(`-degraded` 아님).
// 🔴 **합집합으로 센다**(요소 OR 문구). 저하 표시는 «실패» 가 아니라 «사람이 열어 보라» 는
//    신호이고, 틀리는 방향이 «후보에서 빼는» 쪽이라야 포트폴리오에 오류 화면이 안 실린다.
const DEGRADED_SUFFIXES = ['-degraded', '-error', '-unavailable', '-stale'];

// 🔵 판정을 **한 함수**로 둔다 — `captureOne()` 과 `--self-test` 가 같은 코드를 쓰지 않으면
//    픽스처는 술어가 아니라 «픽스처 안의 사본» 을 재게 된다(이 저장소가 이미 데인 축).
async function judgeDegraded(page, text) {
  const degradedBy = await page
    .evaluate(
      (sufs) =>
        [...document.querySelectorAll(sufs.map((s) => `[data-testid$="${s}"]`).join(','))].map((e) =>
          e.getAttribute('data-testid'),
        ),
      DEGRADED_SUFFIXES,
    )
    .catch(() => []);
  return { degraded: DEGRADED_RE.test(text) || degradedBy.length > 0, degradedBy, textHit: DEGRADED_RE.test(text) };
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
    // 🔴 거부 화면도 로그인 화면과 같다 — «그 페이지의 사진» 으로 저장하지 않는다(AC-1b).
    const denial = await judgeDenial(page);
    if (denial.denied) {
      return { route, path, file, ok: false, kind: 'denied', status, url, deniedBy: denial.deniedBy };
    }
    await page.screenshot({
      path: join(outDir, file),
      fullPage: FULL_PAGE,
      type: FORMAT.type,
      quality: FORMAT.quality,
    });
    // 🔴 그림과 **같은 순간의** 본문을 남긴다. 나중에 따로 재면 그것은 다른 화면이다.
    const text = await page
      .evaluate(() => document.body.innerText.replace(/\s+/g, ' ').trim())
      .catch(() => '');
    const empty = EMPTY_RE.test(text);
    const { degraded, degradedBy } = await judgeDegraded(page, text);
    return {
      route, path, file, ok: true, status, url,
      capturedAt: new Date().toISOString(),
      textLen: text.length,
      head: text.slice(0, 180),
      ...(empty ? { empty: true } : {}),
      ...(degraded ? { degraded: true } : {}),
      ...(degradedBy.length ? { degradedBy } : {}),
      ...(denial.partial.length ? { partialDenied: denial.partial } : {}),
      ...(denial.textHits.length ? { deniedTextOnly: denial.textHits } : {}),
    };
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

  // 🔵 `--self-test` — 거부 판정을 **창 없이** 실제 브라우저로 재는 자리(AC-1b).
  //    데모 창은 예산이 드는 자원이라, 판정기의 결함을 창에서 처음 발견하면 그 창이 버려진다.
  //    🔴 이 픽스처는 판정기의 **술어**를 재지 실제 콘솔이 그 마커를 다는지는 재지 않는다 —
  //       그건 창에서 가이드 셋 + `/tenants` 를 찍어 확인한다(TASK-MONO-648 AC-1b 닫는 조건).
  if (argv.includes('--self-test')) {
    const browser = await chromium.launch();
    const page = await browser.newPage();
    const cases = [
      // 가이드: 본문이 거부 문구를 «인용» 한다 — 2026-09-12 오탐의 모양
      { name: 'guide-quotes-denial-copy', denied: false, partial: 0, textHit: true,
        html: '<main><h1>E-Commerce 가이드</h1><p>역할이 없으면 403 과 함께 「접근 권한이 없습니다」 가 보입니다. Forbidden 은 정상 동작입니다.</p></main>' },
      { name: 'tenants-permission-denied', denied: true, partial: 0, textHit: true,
        html: '<div data-testid="tenants-permission-denied">테넌트 관리 권한이 없습니다.</div>' },
      { name: 'dynamic-detail-forbidden', denied: true, partial: 0, textHit: true,
        html: '<div data-testid="product-forbidden">이 화면을 조회할 권한이 없습니다.</div>' },
      { name: 'domain-not-eligible', denied: true, partial: 0, textHit: true,
        html: '<div data-testid="wms-inventory-not-eligible">wms 재고 화면에 대한 접근 권한이 없습니다.</div>' },
      // 대시보드 카드 하나만 막힘 — 페이지 거부가 아니다
      { name: 'dashboard-card-forbidden', denied: false, partial: 1, textHit: true,
        html: '<section><div data-testid="operator-overview-card-erp-forbidden">이 도메인 조회 권한이 없습니다.</div><div>wms 12 · scm 4</div></section>' },
      { name: 'plain-screen', denied: false, partial: 0, textHit: false,
        html: '<table><tr><td>SKU-APPLE-001</td><td>12</td></tr></table>' },
      // ── TASK-MONO-702: 섹션 거부 vs 페이지 거부(구조 술어). 콘솔 셸처럼 `<main>` 밖에 사이드바가 있다.
      // ① 섹션 하나만 거부 + 다른 섹션은 표 — 2026-09-17 `/wms/operations` 의 모양(옛 규칙이면 denied)
      { name: 'section-forbidden-beside-table', denied: false, partial: 1, textHit: true,
        html: '<nav><a href="/wms">WMS</a></nav><main><section><h1>WMS 운영</h1><div><h2>운영 설정</h2><table><tr><td>allocation.strategy</td><td>FIFO</td></tr></table></div><div><h2>프로젝션 상태</h2><div role="status" data-testid="wms-operations-projection-forbidden">이 항목을 조회할 권한이 없습니다.</div></div></section></main>' },
      // ② 섹션 거부 + 다른 섹션은 «불러올 수 없음» — 사진은 남긴다(degraded 는 따로 표시된다)
      { name: 'section-forbidden-beside-degraded', denied: false, partial: 1, textHit: true,
        html: '<main><section><h1>WMS 운영</h1><div><h2>운영 설정</h2><div role="status" data-testid="wms-operations-settings-degraded">wms 운영 설정을 일시적으로 불러올 수 없습니다.</div></div><div><h2>프로젝션 상태</h2><div role="status" data-testid="wms-operations-projection-forbidden">이 항목을 조회할 권한이 없습니다.</div></div></section></main>' },
      // ③ 섹션 둘 다 거부 — 보여 줄 본문이 없다 = 사실상 페이지 거부
      { name: 'every-section-forbidden', denied: true, partial: 0, textHit: true,
        html: '<main><section><h1>WMS 운영</h1><div><h2>운영 설정</h2><div role="status" data-testid="wms-operations-settings-forbidden">이 항목을 조회할 권한이 없습니다.</div></div><div><h2>프로젝션 상태</h2><div role="status" data-testid="wms-operations-projection-forbidden">이 항목을 조회할 권한이 없습니다.</div></div></section></main>' },
      // ④ 실제 콘솔의 페이지 거부 — 셸(사이드바·헤더) 안에서 제목 + 거부 + «목록으로»
      { name: 'page-forbidden-in-shell', denied: true, partial: 0, textHit: true,
        html: '<header><a href="/">Platform Console</a><button>테넌트</button></header><aside><nav><a href="/ecommerce/products">상품</a></nav></aside><main><div><section><h1>상품 상세</h1><div role="status" data-testid="product-forbidden">이 화면을 조회할 권한이 없습니다. (운영자 역할 확인이 필요합니다.)</div><a href="/ecommerce/products">목록으로</a></section></div></main>' },
      // ⑤ 필터 폼 + 섹션 거부뿐 — 폼 글자는 본문이 아니다 = 페이지 거부
      { name: 'filter-form-and-forbidden-only', denied: true, partial: 0, textHit: true,
        html: '<main><section><h1>정산</h1><form><label>판매자 ID<input name="sellerId"></label><button type="submit">조회</button></form><div role="status" data-testid="settlements-accruals-forbidden">이 항목을 조회할 권한이 없습니다.</div></section></main>' },
    ];
    let bad = 0;
    for (const c of cases) {
      await page.setContent(c.html);
      const j = await judgeDenial(page);
      const got = { denied: j.denied, partial: j.partial.length, textHit: j.textHits.length > 0 };
      const ok = got.denied === c.denied && got.partial === c.partial && got.textHit === c.textHit;
      if (!ok) bad++;
      console.log(`  ${ok ? '✔' : '✗'} ${c.name}  want=${JSON.stringify({ denied: c.denied, partial: c.partial, textHit: c.textHit })} got=${JSON.stringify(got)}`);
    }
    // --- 저하 판정 (TASK-MONO-707) — 같은 `judgeDegraded()` 로 잰다 ---
    const degCases = [
      // ① 2026-09-17 실측이 놓친 모양: «일시적으로» 가 없는 «불러올 수 없습니다» + 마커는 `-unavailable`
      { name: 'ledger-tb-unavailable', degraded: true, byCount: 1,
        html: '<main><section><h1>Finance Ledger 운영</h1><div role="status" data-testid="ledger-tb-unavailable">시산표를 불러올 수 없습니다.</div></section></main>' },
      // ② 마커만 있고 문구는 없는 화면 — 요소 술어가 잡아야 한다
      { name: 'marker-only-no-copy', degraded: true, byCount: 1,
        html: '<main><section><h1>WMS 개요</h1><div data-testid="wms-overview-count-degraded">—</div><table><tr><td>SKU-APPLE-001</td></tr></table></section></main>' },
      // ③ 문구만 있고 마커는 없는 화면 — 문구 술어가 잡아야 한다(합집합의 반대쪽 날개)
      { name: 'copy-only-no-marker', degraded: true, byCount: 0,
        html: '<main><section><h1>통합 개요</h1><p>통합 개요를 일시적으로 불러올 수 없습니다. 잠시 후 다시 시도하세요.</p></section></main>' },
      // ④ 🔴 **정규식 확장만** 무는 칸 — 마커가 없고, «일시적으로» 도 없는 «불러올 수 없습니다».
      //    이 칸이 없으면 옛 정규식으로 되돌려도 ① 이 요소 술어에 걸려 초록이라 확장이 안 물린다
      //    (처음 픽스처가 정확히 그랬다 — bite 가 그것을 드러냈다).
      { name: 'copy-without-temporary-no-marker', degraded: true, byCount: 0,
        html: '<main><section><h1>Finance Ledger 운영</h1><p>시산표를 불러올 수 없습니다.</p></section></main>' },
      // ⑤ 대조군 — 멀쩡한 표. 저하도 마커도 없다
      { name: 'healthy-table', degraded: false, byCount: 0,
        html: '<main><section><h1>WMS 재고</h1><table data-testid="wms-inventory-table"><tr><td>WH01-A-01-01-01</td><td>SKU-APPLE-001</td><td>85</td></tr></table></section></main>' },
    ];
    for (const c of degCases) {
      await page.setContent(c.html);
      const text = await page.evaluate(() => document.body.innerText.replace(/\s+/g, ' ').trim());
      const j = await judgeDegraded(page, text);
      const got = { degraded: j.degraded, byCount: j.degradedBy.length };
      const ok = got.degraded === c.degraded && got.byCount === c.byCount;
      if (!ok) bad++;
      console.log(`  ${ok ? '✔' : '✗'} ${c.name}  want=${JSON.stringify({ degraded: c.degraded, byCount: c.byCount })} got=${JSON.stringify(got)}`);
    }
    // --- ③ 사전 확인이 저하를 **보는가** (TASK-MONO-711 ①) ---------------------
    // 🔴 위 ②는 `judgeDegraded()` 를 **직접** 부른다 — 그래서 «프로브가 그것을 부르는가» 는
    //    한 칸도 안 묻는다. 2026-09-18 창에서 프로브는 저하 화면을 `✔` 로 통과시켰고, 그때
    //    `judgeDegraded()` 는 **이미 있었다.** 없던 것은 판정기가 아니라 **배선**이다.
    // 🔴🔴 그래서 이 칸은 `ok` 와 `degraded` 를 **둘 다** 단언한다:
    //      · `degraded` 만 보면 → 누가 저하를 `ok:false` 로 바꿔도 초록이다(= 측정을 막는다)
    //      · `ok` 만 보면 → 배선을 떼어내도 초록이다(= 원래 결함 그대로)
    const probeCases = [
      { name: 'probe-degraded-marker', ok: true, degraded: true,
        html: '<main><h1>운영자 통합 개요</h1><div data-testid="operator-overview-bff-unavailable">통합 개요를 일시적으로 불러올 수 없습니다.</div></main>' },
      { name: 'probe-degraded-copy-only', ok: true, degraded: true,
        html: '<main><h1>운영자 통합 개요</h1><p>통합 개요를 불러올 수 없습니다.</p></main>' },
      { name: 'probe-healthy', ok: true, degraded: false,
        html: '<main><h1>운영자 통합 개요</h1><table data-testid="overview-table"><tr><td>WMS</td><td>3</td></tr></table></main>' },
      // 🔵 거부는 **여전히 막는다** — 저하를 통과시키는 것과 거부를 통과시키는 것은 다른 일이다.
      { name: 'probe-denied-still-blocks', ok: false, degraded: null,
        html: '<main><div data-testid="wms-overview-forbidden">권한이 없습니다</div></main>' },
    ];
    const probeBrowser = await chromium.launch();
    const probePage = await probeBrowser.newPage();
    for (const c of probeCases) {
      await probePage.setContent(c.html);
      const r = await judgeProbe(probePage, '/dashboards/overview');
      const got = { ok: r.ok, degraded: r.ok ? !!r.degraded : null };
      const okCell = got.ok === c.ok && got.degraded === c.degraded;
      if (!okCell) bad++;
      console.log(`  ${okCell ? '✔' : '✗'} ${c.name}  want=${JSON.stringify({ ok: c.ok, degraded: c.degraded })} got=${JSON.stringify(got)}`);
    }
    await probeBrowser.close();

    await browser.close();
    // 🔴 양성·음성이 **둘 다** 있어야 «0 오탐» 이 공허하지 않다 — 픽스처가 한쪽으로 쏠리면 멈춘다.
    const pos = cases.filter((c) => c.denied).length;
    const neg = cases.filter((c) => !c.denied).length;
    const degPos = degCases.filter((c) => c.degraded).length;
    const degNeg = degCases.filter((c) => !c.degraded).length;
    // 🔴 프로브 칸도 같은 하한을 받는다 — «저하를 보는» 칸과 «안 보는» 칸이 둘 다 있어야
    //    «프로브가 저하를 본다» 가 공허하지 않다. 그리고 «거부는 여전히 막는다» 가 한 칸 필요하다.
    const probeDeg = probeCases.filter((c) => c.degraded === true).length;
    const probeClean = probeCases.filter((c) => c.degraded === false).length;
    const probeBlocked = probeCases.filter((c) => c.ok === false).length;
    if (!pos || !neg || !degPos || !degNeg || !probeDeg || !probeClean || !probeBlocked) {
      console.error(`[portfolio] ✗ self-test 픽스처가 공허합니다 (거부 ${pos}/${neg} · 저하 ${degPos}/${degNeg} · 프로브 저하${probeDeg}/정상${probeClean}/차단${probeBlocked})`);
      process.exit(1);
    }
    const total = cases.length + degCases.length + probeCases.length;
    console.log(`[portfolio] self-test ${total - bad}/${total} (거부 ${pos} · 비거부 ${neg} · 저하 ${degPos} · 비저하 ${degNeg} · 프로브 ${probeCases.length})`);
    process.exit(bad ? 1 : 0);
  }

  if (dry) {
    console.log('[portfolio] --dry-run — 라우트 유도와 의존 해석까지 마쳤고 찍지 않았습니다.');
    return;
  }

  await mkdir(outDir, { recursive: true });
  console.log(`[portfolio] 출력: ${outDir}`);

  const browser = await chromium.launch();
  let context = await browser.newContext({ viewport: VIEWPORT, deviceScaleFactor: SCALE });
  let page = await context.newPage();
  const shots = [];
  const failures = [];
  // TASK-MONO-711 ① — 사전 확인의 결과를 매니페스트에 남긴다. 로그는 흘러가고,
  // 「그때 프로브가 저하였나」는 나중에 큐레이션할 때 묻게 되는 질문이다.
  const probes = [];

  for (const k of keys) {
    const app = APPS[k];
    console.log(`\n[portfolio] === ${k} (${app.label}) ${app.baseUrl}`);


    // 🔴🔴 **앱마다 새 컨텍스트다** (2026-09-09 실측이 이 줄을 만들었다).
    //    하나를 공유하면 앞 앱의 세션이 다음 앱으로 번진다: 콘솔에 **OPERATOR** 로 로그인한
    //    뒤 스토어가 `?error=account_type_mismatch` 로 튕겼다 — 스토어는 CUSTOMER 를 받는다.
    // 🔵 그리고 직전 실행에서 store 가 통과한 것은 **콘솔이 먼저 실패해서 그 세션이
    //    없었기** 때문이다. 즉 그 초록도 «맞다» 가 아니라 «앞이 졌다» 였다 — 앱 순서가
    //    조용히 판정을 바꾸고 있었고, 순서가 바뀌는 날 사라지는 초록이다.
    // 🔴 첫 앱에도 똑같이 갈아끼운다. 「첫 번째만 예외」는 그 자체가 다음 함정이다.
    await context.close().catch(() => {});
    context = await browser.newContext({ viewport: VIEWPORT, deviceScaleFactor: SCALE });
    page = await context.newPage();

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
        // 🔴 TASK-MONO-711 ① — 저하면 **소리 내어 말한다.** 막지는 않는다.
        //    이 줄이 없으면 저하 화면 위에서 앱 전량을 찍고도 로그가 «✔» 하나만 남는다.
        if (s.degraded) {
          console.warn(
            `[portfolio] ⚠ 사전 확인 화면이 **저하 상태**입니다 — ${s.probePath}` +
              (s.degradedBy.length ? ` (${s.degradedBy.join(', ')})` : ' (문구 적중)') +
              `\n           찍기는 계속합니다. 🔵 이 앱의 장들을 큐레이션에 쓰기 전에 눈으로 확인하세요.`,
          );
        }
        probes.push({ app: k, path: s.probePath, chars: s.chars,
          degraded: !!s.degraded, ...(s.degradedBy?.length ? { degradedBy: s.degradedBy } : {}) });
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
    probes,
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

  // 🔴🔴 «찍음 N» 만 보고하면 **빈 화면 N 장**도 성공이다. 실측(2026-09-09): 콘솔의
  //    `/ecommerce/products`·`/ecommerce/orders` 는 HTTP 200 · 로그인됨 · 테넌트 선택됨
  //    · 「권한 없음」 아님인데 본문이 「표시할 …이 없습니다」였고, `/dashboards/overview`
  //    는 「일시적으로 불러올 수 없습니다」였다. 취업 자료로는 **못 쓰는 장**이다.
  // 🔵 실패로 세지 않는다 — 캡처는 성공했고 그 그림은 정직하다. **다른 줄로** 센다.
  const empties = shots.filter((x) => x.empty);
  const degraded = shots.filter((x) => x.degraded);
  if (empties.length || degraded.length) {
    console.log(`[portfolio] ⚠ 찍혔지만 큐레이션 후보가 아님 — 빈 목록 ${empties.length} · 성능저하/오류 ${degraded.length}`);
    for (const x of degraded) console.log(`  [저하] ${x.route}  ${x.head.slice(0, 70)}`);
    for (const x of empties) console.log(`  [빈값] ${x.route}  ${x.head.slice(0, 70)}`);
  }
  // 🔵 요소 판정은 통과했지만 사람이 열어 볼 장 — 실패가 아니다(위 «권한 거부 판정» 절).
  const textOnly = shots.filter((x) => x.deniedTextOnly);
  const partialDenied = shots.filter((x) => x.partialDenied);
  if (textOnly.length || partialDenied.length) {
    console.log(`[portfolio] 👁 이미지를 열어 볼 것 — 거부 문구만 있음 ${textOnly.length} · 일부(카드·섹션) 거부 ${partialDenied.length}`);
    for (const x of textOnly) console.log(`  [문구] ${x.route}  «${x.deniedTextOnly.join('» «')}»`);
    for (const x of partialDenied) console.log(`  [일부] ${x.route}  ${x.partialDenied.join(', ')}`);
  }
  console.log('[portfolio] manifest.json 기록');
}

main().catch((e) => {
  console.error(`[portfolio] ✗ ${e && e.stack ? e.stack : e}`);
  process.exit(1);
});
