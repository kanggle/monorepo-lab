#!/usr/bin/env node
// DEMO-LAUNCHER: 론처 카드 캐러셀에 들어갈 **실제 화면**을 찍는다 — TASK-MONO-639
//
// =============================================================================
// 왜 손으로 안 찍고 스크립트인가
// =============================================================================
// 🔴 손으로 한 번 찍으면 화면이 바뀌는 날 이미지가 **조용히 낡는다.** 낡음을 알려 주는
//    것이 아무것도 없고, 그때 포트폴리오는 «옛 UI 를 광고하는 화면» 이 된다.
//    ⇒ 다시 만들 수 있어야 한다. 그것이 이 파일이 존재하는 이유다.
//
// 🔴🔴 **가짜 화면을 만들지 않는다**(TASK-MONO-634 AC-6 의 금지). 목업·합성·손으로 그린
//    스크린샷은 전부 금지다. 여기서 찍는 것은 **그 주소가 그 순간 실제로 준 화면**이다.
//
// 🔵 EC2 가 필요 없다. ADR-MONO-070 이 세 화면을 백엔드 없이 공개로 세웠으므로 Vercel
//    표면에서 찍는다 — TASK-MONO-634 는 이 캡처가 «EC2 기동 창» 을 필요로 한다고 적었고,
//    그 전제는 2026-09-08 실측으로 거짓임이 드러났다(셋 다 200).
//
// =============================================================================
// 사용
// =============================================================================
//   node infra/demo/aws/site/capture-shots.mjs                  # 전부 찍는다
//   node infra/demo/aws/site/capture-shots.mjs --dry-run        # 목록 + **의존 해석**만
//   node infra/demo/aws/site/capture-shots.mjs --from <경로>    # 특정 @playwright/test 설치본
//
// 환경변수 (TASK-MONO-648 AC-2):
//   CAPTURE_CONSOLE_BASE / CAPTURE_STORE_BASE / CAPTURE_FAN_BASE
//       호스트를 바꾼다(기본 = 배포된 주소). 로컬 스택이나 CI 에서 찍을 때 쓴다.
//       🔴 기본값이 아니면 스크립트가 경고를 찍는다 — § HOSTS 의 대조 의무를 읽어라.
//   CAPTURE_AUTH_EMAIL / CAPTURE_AUTH_PASSWORD / CAPTURE_AUTH_TENANT(기본 demo-corp)
//       `requiresAuth: true` 인 장을 찍을 때 필요하다. 🔴 파일에 적지 않는다.
//       🔵 `--dry-run` 은 자격증명이 필요 없다(네트워크에 안 붙는다).
//
// 종료코드 — 🔴 «미설치» 와 «고장» 은 다른 상태다(TASK-MONO-643):
//   0  정상
//   3  Playwright 가 설치돼 있지 않다. **고장이 아니다** — clean clone·CI 의 정상 상태다
//   1  그 밖의 실패(캡처 실패 등)
//
// 🔴 Playwright 가 필요하다. 워크스페이스 어딘가에 설치돼 있으면 **찾아서** 쓴다
//    (`projects/` 아래 깊이 1~3). 손으로 적은 경로 목록은 쓰지 않는다 — 그 목록이 틀려서
//    이 스크립트가 문서대로 실행하면 죽었던 것이 TASK-MONO-643 이다.
// =============================================================================

import { mkdir, writeFile, readFile } from 'node:fs/promises';
import { existsSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT_DIR = join(HERE, 'thumbnails');
// 🔴 형식은 **JPEG** 다. PNG 로 찍으니 여덟 장 합계 1,388KB 였고 그중 한 장이 565KB 였다
//    (사진이 많은 상품 목록). 캐러셀은 카드마다 첫 장을 즉시 로드하므로 그 무게가 곧
//    방문자의 첫 화면이다. UI 스크린샷은 JPEG 로도 글자가 읽히고, 아래 실측이 그것을 잰다.
const FORMAT = { type: 'jpeg', quality: 82, ext: 'jpg' };
const MANIFEST = join(OUT_DIR, 'manifest.json');

// -----------------------------------------------------------------------------
// 무엇이 «주요 화면» 인가 — 목록과 그 근거
// -----------------------------------------------------------------------------
// 주소는 **호스트 단위로 설정 가능하다** — TASK-MONO-648 AC-2
// -----------------------------------------------------------------------------
// 🔴🔴 첫 판은 여덟 장의 주소를 **완성된 문자열 상수**로 박아 뒀다. 그래서 「같은 화면을
//    다른 곳에서 찍는다」가 **불가능**했고, 그것이 두 가지를 동시에 막았다:
//      ① 로컬 스택에서 찍기 — EC2 기동 창 없이 찍고 싶어도 주소를 못 바꾼다
//      ② CI(리눅스)에서 찍기 — Windows-Docker 의 파일 번역 비용 없이 돌리고 싶어도 같다
//
// 🔵 그래서 «호스트» 와 «경로» 를 가른다. 기본값은 **지금 배포된 그대로**이므로 이 변경은
//    기존 동작을 한 글자도 안 바꾼다(대조군: `--dry-run` 출력의 여덟 주소가 동일해야 한다).
//
// 🔴🔴 **그러나 «어디서 찍어도 된다» 는 뜻이 아니다.** 썸네일은 캐러셀에 걸리는 **광고**이고
//    방문자는 그것을 «눌러 보면 이 화면이 나온다» 로 읽는다. 로컬에 시드를 더 넣고 찍으면
//    **방문자가 영영 못 볼 화면을 광고하게 된다** — 목업은 아니지만 효과는 목업과 같고,
//    이 파일 머리말의 「가짜 화면을 만들지 않는다」가 금지하는 것이 바로 그 효과다.
//    ⇒ 다른 호스트로 찍었으면 **찍은 화면이 배포된 그 주소가 주는 화면과 같은지 대조하라.**
//    실측 근거(2026-09-10 데모 창): 같은 콘솔에서 `/erp/masters` 는 마스터 5종이 **꽉 찼고**
//    `/ecommerce/orders` 는 **비어 있었다**(시드가 사유를 말한다: `⚠ 데모 계정의 주문이
//    없습니다`). 후자를 로컬의 풍부한 시드로 찍으면 그 광고는 **거짓이 된다.**
const HOSTS = {
  console: process.env.CAPTURE_CONSOLE_BASE || 'https://console.hubwang.com',
  store: process.env.CAPTURE_STORE_BASE || 'https://store.hubwang.com',
  fan: process.env.CAPTURE_FAN_BASE || 'https://fan.hubwang.com',
};

// 🔵 기준: **방문자가 이 제품을 이해하는 데 필요한 화면**이다. 「기능이 많아 보이는 화면」
//    이 아니다. 그래서 각 앱마다 ① 첫인상(홈/둘러보기) ② 목록 ③ 상세·부가 순으로 셋씩 —
//    캐러셀이 세 장이면 좌우 이동이 의미 있고, 그 이상은 방문자가 다 안 넘긴다.
// 🔴 `requiresAuth` 가 없는 장은 **로그인 없이 열리는 경로**다(2026-09-08·09 실측).
//    보호 경로를 «인증 없이» 찍으면 로그인 화면이 찍히고, 그것은 «주요 화면» 이 아니다.
// 🔴🔴 **`tenant` 는 장마다 다르다 — 전역이 아니다** (TASK-MONO-648 AC-2, 2026-09-11 실측).
//    같은 계정으로 같은 콘솔을 봐도 **어느 테넌트를 assume 했느냐에 따라 화면이 갈린다**:
//      `/erp/masters`        demo-corp → 16행 ✅   /  ecommerce → **「권한이 없습니다」**
//      `/ecommerce/products` ecommerce → 20행 ✅   /  demo-corp → **빈 목록**
//    ⇒ 「전부 `ecommerce` 로 재촬영」했으면 **유일하게 쓸 만한 장을 깨뜨렸다.**
//    그래서 `CAPTURE_AUTH_TENANT` 하나로는 이 목록을 찍을 수 없고, 아래 `assumeTenant` 가
//    장 사이에 전환한다.
const SHOTS = [
  // 운영자 콘솔 — 🔴 **진짜 콘솔이다.** 옛 판의 `/demo/*` 세 장은 `src/app/(demo)/` 의
  //    **합성 샘플 투어**였고 그 페이지가 스스로 *"실제 운영 데이터는 한 건도 포함되어
  //    있지 않으며…"* 라고 적는다. `requiresAuth` 가 그 제약을 풀었다.
  //
  // 🔵 **두 장인 것은 결정이다** (소유자, 2026-09-11: 「확정된 둘로 줄인다」).
  //    승인 4장(`/dashboards/overview` · `/ecommerce/orders` ·
  //    `/ecommerce/products/[id]/edit` · `/ecommerce/settlements`)은 **테넌트 선택만으로
  //    살지 않았다** — 각각 저하 / 두 테넌트 다 빈값 / 권한거부 / 1행뿐이었다.
  //    🔴 빈 표를 «승인된 장» 으로 적으면 매니페스트가 **거짓이 된다**(이 파일 머리말의
  //    「가짜 화면을 만들지 않는다」와 같은 축). 카드가 셋에서 둘로 줄지만 **찍힌 것이 참이다.**
  { bundle: 'console', name: 'console-1-erp-masters', path: '/erp/masters',
    requiresAuth: true, tenant: 'demo-corp', minChars: 900,
    alt: '운영자 콘솔 — ERP 마스터 데이터(부서·직급·원가센터 … effective-dating)' },
  { bundle: 'console', name: 'console-2-ecommerce-products', path: '/ecommerce/products',
    requiresAuth: true, tenant: 'ecommerce', minChars: 700,
    alt: '운영자 콘솔 — 이커머스 상품 관리(목록 · 상태 필터 · 수정/삭제)' },

  // 이커머스 스토어
  { bundle: 'store', name: 'store-1-home', path: '/',
    alt: '이커머스 스토어 홈' },
  { bundle: 'store', name: 'store-2-products', path: '/products',
    alt: '이커머스 스토어 — 상품 목록' },

  // 팬 플랫폼
  { bundle: 'fan', name: 'fan-1-feed', path: '/',
    alt: '팬 플랫폼 — 공개 피드' },
  { bundle: 'fan', name: 'fan-2-artists', path: '/artists',
    alt: '팬 플랫폼 — 아티스트 목록' },
  { bundle: 'fan', name: 'fan-3-membership', path: '/membership',
    alt: '팬 플랫폼 — 멤버십 소개' },
];

for (const s of SHOTS) s.url = `${HOSTS[s.bundle]}${s.path}`;

// 🔴 뷰포트와 스케일을 **고정한다.** 안 그러면 재생성마다 이미지가 달라져 diff 가 무의미
//    해지고, «바뀐 것이 화면인지 내 창 크기인지» 를 못 가른다.
const VIEWPORT = { width: 1280, height: 800 };
const SCALE = 1;

// -----------------------------------------------------------------------------
// 인증 촬영 (`requiresAuth`) — TASK-MONO-648 AC-2
// -----------------------------------------------------------------------------
// 🔵 **왜 필요한가**: 지금 콘솔 썸네일 3장은 진짜 콘솔이 아니다. `/demo` 는 합성 샘플
//    투어이고 그 페이지가 스스로 *"샘플(합성) 데이터로 만든 둘러보기입니다. 실제 운영
//    데이터는 한 건도 포함되어 있지 않으며…"* 라고 적는다. 로그인 없이 찍을 수 있는 콘솔
//    화면이 그것뿐이었기 때문이고, 이 절이 그 제약을 푼다.
//
// 🔴 로그인은 **OIDC 리다이렉트**다(2026-09-10 실측): `/login` 에는 폼이 없고
//    `[data-testid=iam-login]` 버튼이 IAM(Spring Authorization Server)으로 보낸다.
//    첫 판에서 `input[type=email]` 을 찾다가 못 찾아 **모든 화면이 `/login` 으로 튕겼고**,
//    그때 나온 「UUID 0건」은 «없다» 가 아니라 **«안 봤다»** 였다.
//
// 🔴🔴 그리고 로그인만으로는 부족하다 — **테넌트를 assume 해야 한다.** 복귀 직후 활성
//    테넌트가 `ecommerce` 라 도메인 화면들이 전부 「권한이 없습니다」를 그렸다. 셀렉트의
//    value 는 이미 `demo-corp` 인데도 그랬다 ⇒ **표시된 선택과 실제 assume 이 다른 상태**가
//    있다. 다른 테넌트로 갔다가 돌아오는 **왕복**이 그것을 강제한다.
//
// 🔴 자격증명은 **환경변수로만** 받는다. 이 파일에 적지 않는다.
// 🔵 `CAPTURE_AUTH_TENANT` 는 **장이 테넌트를 안 적었을 때의 폴백**으로만 남는다.
//    실제 목록은 장마다 `tenant` 를 갖는다(위 SHOTS 의 🔴🔴 주석).
const AUTH = {
  email: process.env.CAPTURE_AUTH_EMAIL || '',
  password: process.env.CAPTURE_AUTH_PASSWORD || '',
  tenant: process.env.CAPTURE_AUTH_TENANT || 'demo-corp',
};

/** 로그인이 필요한 장이 하나라도 있는가. */
const NEEDS_AUTH = SHOTS.some((s) => s.requiresAuth);

/** 인증 장들이 요구하는 테넌트 집합 — 하나가 아닐 수 있다. */
const REQUIRED_TENANTS = [...new Set(SHOTS.filter((s) => s.requiresAuth).map((s) => s.tenant ?? AUTH.tenant))];

/**
 * 로그인한다(테넌트 assume 은 **안 한다** — `assumeTenant` 가 장마다 따로 한다).
 * 🔴 **각 단계를 단언한다** — 실패해도 조용히 다음으로 가면 뒤의 모든 캡처가 로그인
 * 화면이 되고, 매니페스트는 그것을 «성공» 으로 적는다.
 */
async function signIn(page) {
  const base = HOSTS.console;
  await page.goto(`${base}/login`, { waitUntil: 'domcontentloaded', timeout: 45000 });
  const btn = page.locator('[data-testid=iam-login]');
  if (!(await btn.isVisible().catch(() => false))) {
    throw new Error('로그인 진입점 [data-testid=iam-login] 을 못 찾았습니다 — 로그인 화면 구조가 바뀌었습니다');
  }
  await btn.click();
  await page.waitForTimeout(6000);

  const user = page.locator('input[name="username"], input[type="email"], #username').first();
  if (!(await user.isVisible().catch(() => false))) {
    throw new Error(`IAM 로그인 폼을 못 찾았습니다 (url=${page.url()})`);
  }
  await user.fill(AUTH.email);
  await page.locator('input[name="password"], input[type="password"], #password').first().fill(AUTH.password);
  await page.locator('button[type="submit"], input[type="submit"]').first().click();
  await page.waitForTimeout(9000);
  if (page.url().includes('/login')) {
    throw new Error('로그인 뒤에도 /login 입니다 — 자격증명 또는 IAM 상태를 확인하세요');
  }
}

/**
 * 테넌트를 assume 한다. 🔴 **왕복이 필수다** — 다른 테넌트로 갔다가 돌아와야 실제
 * assume 이 걸린다(위 § 의 «표시된 선택과 실제 assume 이 다른 상태»).
 *
 * 🔴🔴 **장 사이에 여러 번 불린다.** 목록이 테넌트 둘을 요구하기 때문이고, 그것이
 * 이 함수가 `signIn` 에서 갈라져 나온 이유다. 🔵 같은 테넌트가 연속이면 **건너뛴다** —
 * 왕복 한 번이 11초이므로 장마다 무조건 돌면 값없이 느려진다.
 */
async function assumeTenant(page, tenant) {
  const sel = page.locator('[data-testid=tenant-select]');
  if (!(await sel.isVisible().catch(() => false))) {
    // 🔵 셀렉터는 콘솔 레이아웃에 있다. 공개 화면에 있다가 불리면 콘솔로 한 번 들어간다.
    await page.goto(`${HOSTS.console}/console`, { waitUntil: 'domcontentloaded', timeout: 45000 });
    await page.waitForTimeout(3000);
    if (!(await sel.isVisible().catch(() => false))) {
      throw new Error('테넌트 셀렉트 [data-testid=tenant-select] 를 못 찾았습니다');
    }
  }
  const options = (await sel.locator('option').allTextContents()).map((t) => t.trim()).filter(Boolean);
  if (!options.includes(tenant)) {
    // 🔴 «없는 테넌트» 와 «assume 실패» 는 다른 사실이다. 사유를 대며 죽는다.
    throw new Error(`테넌트 ${tenant} 가 셀렉트에 없습니다 (있는 것: ${options.join(', ')})`);
  }
  const others = options.filter((t) => t !== tenant);
  if (others.length > 0) {
    await sel.selectOption(others[0]);
    await page.waitForTimeout(4000);
  }
  await sel.selectOption(tenant);
  await page.waitForTimeout(7000);
  const active = await sel.inputValue();
  if (active !== tenant) {
    throw new Error(`테넌트 assume 실패: 기대 ${tenant} · 실제 ${active}`);
  }
  return active;
}

/**
 * 🔴🔴 찍기 **전에** 그 화면이 정말 열렸는지 판정한다. 「권한이 없습니다」나 `/login` 은
 * HTTP 200 이므로 상태코드로는 안 걸린다 — 그 둘을 찍으면 포트폴리오가 **거절 화면을
 * 광고**하게 되고, 매니페스트는 그것을 성공으로 적는다.
 */
async function assertRendered(page, shot) {
  if (page.url().includes('/login')) throw new Error('로그인 화면으로 튕겼습니다 (인증이 끊겼습니다)');
  const txt = await page.evaluate(() => document.body.innerText || '');
  if (/권한이 없습니다/.test(txt)) throw new Error('「권한이 없습니다」 화면입니다 (테넌트 스코프/역할 확인)');
  // 🔵 하한은 «본문이 있나» 다. 껍데기만 렌더된 장(내비게이션뿐)을 성공으로 세지 않는다.
  if (txt.length < (shot.minChars ?? 300)) {
    throw new Error(`본문이 너무 짧습니다 (${txt.length}자 < 하한 ${shot.minChars ?? 300}) — 빈 화면일 수 있습니다`);
  }
  return txt.length;
}

// -----------------------------------------------------------------------------
// Playwright 를 어떻게 찾는가 — TASK-MONO-643
// -----------------------------------------------------------------------------
// 🔴🔴 첫 판은 후보 경로 **셋을 손으로 적었고 셋 다 틀렸다**(프로젝트 층을 봤는데 설치본은
//    앱 층에 있었다). 그래서 문서에 적힌 `node capture-shots.mjs` 가 rc=1 로 죽었고,
//    TASK-MONO-639 는 `--from` 으로 우회해 성공했기 때문에 그 사실이 안 드러났다.
//
// 🔴 그래서 **네 번째 경로를 적는 것으로 고치지 않는다.** 그것이 지금 결함의 모양이다 —
//    손으로 적은 목록은 디렉터리가 한 층 움직이는 날 다시 틀린다. **찾아낸다.**
//
// 🔵 왜 `import.meta.resolve` / `createRequire` 가 아닌가: 이 파일은 워크스페이스 **밖**
//    (`infra/demo/aws/site/`)에 있고 자기 `package.json` 이 없다. Node 의 해석은 이 파일의
//    위치에서 위로만 올라가므로 `projects/**/node_modules` 에 절대 닿지 않는다. 그래서
//    저장소 구조를 아는 **경계 있는 탐색**이 맞다.
//
// 🔵 깊이 1~3 인 이유(실측 2026-09-08): 설치본은 앱 층에 있다 —
//      projects/<p>/apps/<app>/node_modules          → 깊이 3
//      projects/<p>/web/<app>/node_modules           → 깊이 3
//    그리고 프로젝트 층(깊이 1)에도 생길 수 있으므로 1~3 을 본다. 무한 재귀는 하지 않는다
//    (node_modules 안을 훑으면 느리고, 이 저장소는 «느린 가드=틀린 가드» 를 이미 겪었다).
const REPO_ROOT = resolve(HERE, '../../../..');

function discoverPlaywright() {
  const found = [];
  const projects = join(REPO_ROOT, 'projects');
  if (!existsSync(projects)) return found;
  const dirsIn = (d) => {
    try {
      return readdirSync(d, { withFileTypes: true })
        .filter((e) => e.isDirectory() && e.name !== 'node_modules' && !e.name.startsWith('.'))
        .map((e) => join(d, e.name));
    } catch { return []; }
  };
  const hit = (d) => {
    const c = join(d, 'node_modules', '@playwright', 'test');
    if (existsSync(c) && !found.includes(c)) found.push(c);
  };
  for (const l1 of dirsIn(projects)) {          // projects/<p>
    hit(l1);
    for (const l2 of dirsIn(l1)) {              // projects/<p>/<apps|web|...>
      hit(l2);
      for (const l3 of dirsIn(l2)) hit(l3);     // projects/<p>/<apps>/<app>
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
        const mod = await import(new URL(entry, `file:///${c.split(String.fromCharCode(92)).join('/')}/`).href);
        if (mod.chromium) return { chromium: mod.chromium, at: c };
      } catch {
        /* 다음 후보 */
      }
    }
  }
  return { chromium: null, at: null, candidates };
}

async function main() {
  const dry = process.argv.includes('--dry-run');
  const fromIdx = process.argv.indexOf('--from');
  const from = fromIdx >= 0 ? process.argv[fromIdx + 1] : null;

  console.log(`[capture] ${SHOTS.length}장 · 뷰포트 ${VIEWPORT.width}x${VIEWPORT.height} · scale ${SCALE}`);
  for (const s of SHOTS) {
    // 🔵 인증 장은 **테넌트까지** 보여 준다 — 이 목록의 요점이 «테넌트가 장마다 다르다» 이고,
    //    dry-run 이 그것을 안 보여 주면 목록을 눈으로 검토할 수 없다.
    const tag = s.requiresAuth ? `   [로그인 필요 · 테넌트 ${s.tenant ?? AUTH.tenant}]` : '';
    console.log(`  ${s.bundle.padEnd(8)} ${s.name.padEnd(28)} ${s.url}${tag}`);
  }
  // 🔵 기본 호스트가 아니면 그것을 **크게** 알린다. 다른 곳에서 찍은 그림이 조용히
  //    캐러셀에 걸리면, 방문자는 «눌러도 안 나오는 화면» 을 광고당한다.
  for (const [b, h] of Object.entries(HOSTS)) {
    if (!h.endsWith('.hubwang.com')) {
      console.log(`[capture] ⚠ ${b} 호스트가 기본값이 아닙니다: ${h}`);
      console.log(`             🔴 찍은 화면이 «배포된 그 주소가 주는 화면» 과 같은지 대조하십시오.`);
    }
  }
  // 🔴 «테넌트 하나» 로 요약하지 않는다 — 이 목록은 둘을 요구하고, 그 사실이 목록의 요점이다.
  if (NEEDS_AUTH) {
    console.log(`[capture] 로그인 필요 ${SHOTS.filter((s) => s.requiresAuth).length}장 · 요구 테넌트 ${REQUIRED_TENANTS.length}종: ${REQUIRED_TENANTS.join(', ')}`);
    if (REQUIRED_TENANTS.length > 1) {
      console.log('  🔵 장 사이에 테넌트를 전환합니다(왕복 1회 ≈ 11초). 같은 테넌트가 연속이면 건너뜁니다.');
    }
  }
  // 🔴🔴 의존 해석을 **--dry-run 보다 먼저** 한다. 첫 판은 순서가 반대라서
  //    `--dry-run` 이 «목록만 찍고 rc=0» 으로 끝났고, 그래서 **Playwright 를 못 찾는
  //    상태를 통과시켰다.** 그 순서가 결함의 일부였다(TASK-MONO-643 Failure Scenario 4).
  const { chromium, at, candidates } = await loadChromium(from);
  if (!chromium) {
    // 🔴 두 상태를 **다른 종료코드로** 가른다. 게이트가 이 둘을 섞으면 설치가 없는
    //    clean CI 에서 «스크립트 고장» 을 신고하거나(영구 빨강), 반대로 진짜 고장을
    //    «설치 안 됨» 으로 삼킨다(영구 초록).
    //      rc=3 — Playwright 가 설치돼 있지 않다. **정상 상태다**(clean clone·CI).
    //      rc=1 — 그 밖의 실패(캡처 실패 등). 스크립트나 대상의 문제다.
    console.error('[capture] ⚠ Playwright 가 설치돼 있지 않습니다 (rc=3 — 고장이 아닙니다).');
    if (from) console.error(`  → --from 으로 준 경로에 없습니다: ${from}`);
    else console.error(`  → projects/ 아래 깊이 1~3 을 훑었고 후보를 못 찾았습니다(${(candidates || []).length}건).`);
    console.error('  → 워크스페이스 중 하나에서 npm/pnpm install 하거나 --from <@playwright/test 경로> 를 주세요.');
    process.exit(3);
  }
  console.log(`[capture] Playwright: ${at}`);

  if (dry) {
    console.log('[capture] --dry-run — 의존 해석까지 마쳤고 찍지 않았습니다.');
    return;
  }

  // 🔴🔴 자격증명 검사는 **`--dry-run` 반환보다 뒤**여야 한다. 앞에 두면 자격증명 없는
  //    환경(clean CI)에서 `--dry-run` 이 rc=1 로 죽고, 가드 `(z38)` 이 기대하는 «설치
  //    여부에 따른 rc 0/3» 이 깨진다. 🔵 dry-run 은 네트워크에 안 붙으므로 애초에
  //    자격증명이 필요 없다 — 순서가 곧 의미다.
  //    (이 파일은 이미 같은 부류를 한 번 겪었다: `--dry-run` 이 의존 해석보다 **먼저**
  //     반환해서 「못 찾는 상태」를 통과시켰다 — TASK-MONO-643.)
  if (NEEDS_AUTH && (!AUTH.email || !AUTH.password)) {
    console.error('[capture] ✗ 로그인이 필요한 장이 있는데 자격증명이 없습니다.');
    console.error('  → CAPTURE_AUTH_EMAIL / CAPTURE_AUTH_PASSWORD 를 주세요 (CAPTURE_AUTH_TENANT 기본 demo-corp).');
    console.error('  🔴 그냥 진행하면 그 장들이 «로그인 화면» 으로 찍히고 매니페스트가 그것을 성공으로 적습니다.');
    process.exit(1);
  }

  await mkdir(OUT_DIR, { recursive: true });
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: VIEWPORT, deviceScaleFactor: SCALE });
  const entries = [];
  let failed = 0;

  // 🔴 로그인은 **한 번만** 한다. 장마다 하면 IAM 에 불필요한 부하를 주고, 세션이 장 사이에
  //    끊겼는지도 못 가른다(아래 `assertRendered` 가 `/login` 튕김을 장마다 다시 문다).
  // 🔵 **테넌트는 그렇지 않다** — 목록이 둘을 요구하므로 장마다 맞춘다(`activeTenant`).
  let activeTenant = null;
  if (NEEDS_AUTH) {
    try {
      await signIn(page);
      console.log(`[capture] 로그인 완료 · 이 목록이 요구하는 테넌트: ${REQUIRED_TENANTS.join(', ')}`);
    } catch (e) {
      await browser.close();
      console.error(`[capture] ✗ 로그인 실패 — 아무것도 찍지 않았습니다: ${e.message}`);
      process.exit(1);
    }
  }

  for (const s of SHOTS) {
    const file = `${s.name}.${FORMAT.ext}`;
    try {
      // 🔴🔴 이 장이 요구하는 테넌트로 **먼저** 맞춘다. 안 맞추면 같은 주소가 다른 화면을
      //    준다 — `/erp/masters` 는 `ecommerce` 에서 「권한이 없습니다」이고
      //    `/ecommerce/products` 는 `demo-corp` 에서 **빈 목록**이다(2026-09-11 실측).
      //    🔵 같은 테넌트가 연속이면 `assumeTenant` 를 건너뛴다(왕복 한 번이 11초다).
      if (s.requiresAuth) {
        const want = s.tenant ?? AUTH.tenant;
        if (activeTenant !== want) {
          activeTenant = await assumeTenant(page, want);
          console.log(`[capture]   테넌트 assume → ${activeTenant}`);
        }
      }
      // 🔵 networkidle 이 아니라 domcontentloaded + 짧은 안정화. 공개 장들은 백엔드로
      //    가는 요청이 없으므로(ADR-MONO-070) networkidle 은 불필요하게 오래 기다린다.
      // 🔴 인증 장은 다르다 — BFF 를 실제로 부르므로 더 기다려야 표가 찬다.
      const res = await page.goto(s.url, { waitUntil: 'domcontentloaded', timeout: 45000 });
      const status = res ? res.status() : 0;
      // 🔴 200 이 아니면 **캡처하지 않고 실패로 센다.** 옛 이미지를 남기고 성공하면
      //    «화면이 죽었는데 포트폴리오는 멀쩡» 이 된다.
      if (status !== 200) throw new Error(`HTTP ${status}`);
      await page.waitForLoadState('load', { timeout: 20000 }).catch(() => {});
      await page.waitForTimeout(s.requiresAuth ? 4500 : 1200); // 폰트·이미지(·인증 장은 데이터) 안정화
      // 🔴🔴 인증 장은 **200 만으로 판정할 수 없다.** 「권한이 없습니다」도 `/login` 도
      //    200 이다 — 그 둘을 찍으면 포트폴리오가 **거절 화면을 광고**한다.
      let chars = null;
      if (s.requiresAuth) chars = await assertRendered(page, s);
      await page.screenshot({ path: join(OUT_DIR, file), fullPage: false,
                              type: FORMAT.type, quality: FORMAT.quality });
      entries.push({
        bundle: s.bundle, file, url: s.url, alt: s.alt, status,
        capturedAt: new Date().toISOString(),
        // 🔴 인증 캡처였다는 **사실을 남긴다.** 안 적으면 다음 사람이 왜 재생성이 안 되는지
        //    모른다(자격증명 없이 돌리면 이 장은 안 나온다).
        // 🔴🔴 **그리고 «어느 테넌트로» 찍었는지를 장마다 남긴다** — 이 목록은 테넌트가
        //    둘이고, 그것을 안 적으면 재생성이 **다른 화면을 찍는다**(권한거부 또는 빈 목록).
        ...(s.requiresAuth ? { requiresAuth: true, tenant: activeTenant, bodyChars: chars } : {}),
      });
      console.log(`[capture] ✔ ${file}  ← ${s.url}${s.requiresAuth ? `  [인증 · ${chars}자]` : ''}`);
    } catch (e) {
      failed += 1;
      console.error(`[capture] ✗ ${file}  ← ${s.url}\n    ${e.message}`);
    }
  }

  await browser.close();

  // 🔴 실패가 하나라도 있으면 매니페스트를 **안 쓰고** 죽는다. 반쪽짜리 매니페스트는
  //    «이만큼은 최신» 이라는 거짓을 만든다.
  if (failed > 0) {
    console.error(`[capture] ✗ ${failed}장 실패 — 매니페스트를 쓰지 않았습니다.`);
    process.exit(1);
  }

  // 🔵 어느 주소를 언제 찍었는지 남긴다. 기록이 없으면 다음 사람이 «이게 아직 맞는
  //    화면인가» 를 판정할 방법이 없다(TASK-MONO-639 AC-1).
  const manifest = { viewport: VIEWPORT, deviceScaleFactor: SCALE, format: FORMAT, shots: entries };
  await writeFile(MANIFEST, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
  console.log(`[capture] ✔ ${entries.length}장 · manifest.json 기록`);
}

main().catch((e) => {
  console.error(`[capture] ✗ ${e && e.stack ? e.stack : e}`);
  process.exit(1);
});
