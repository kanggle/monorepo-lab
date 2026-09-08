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
// 🔵 기준: **방문자가 이 제품을 이해하는 데 필요한 화면**이다. 「기능이 많아 보이는 화면」
//    이 아니다. 그래서 각 앱마다 ① 첫인상(홈/둘러보기) ② 목록 ③ 상세·부가 순으로 셋씩 —
//    캐러셀이 세 장이면 좌우 이동이 의미 있고, 그 이상은 방문자가 다 안 넘긴다.
// 🔴 전부 **로그인 없이 열리는 경로**다(2026-09-08·09 실측). 보호 경로를 찍으면 로그인
//    화면이 찍히고, 그것은 «이 제품의 주요 화면» 이 아니다.
const SHOTS = [
  // 운영자 콘솔 — 둘러보기(/demo)가 공개 표면이다.
  { bundle: 'console', name: 'console-1-overview', url: 'https://console.hubwang.com/demo',
    alt: '운영자 콘솔 둘러보기 — 도메인 요약' },
  { bundle: 'console', name: 'console-2-ecommerce', url: 'https://console.hubwang.com/demo/ecommerce',
    alt: '운영자 콘솔 — 이커머스 주문·상품 표' },
  { bundle: 'console', name: 'console-3-wms', url: 'https://console.hubwang.com/demo/wms',
    alt: '운영자 콘솔 — WMS 재고 표' },

  // 이커머스 스토어
  { bundle: 'store', name: 'store-1-home', url: 'https://store.hubwang.com/',
    alt: '이커머스 스토어 홈' },
  { bundle: 'store', name: 'store-2-products', url: 'https://store.hubwang.com/products',
    alt: '이커머스 스토어 — 상품 목록' },

  // 팬 플랫폼
  { bundle: 'fan', name: 'fan-1-feed', url: 'https://fan.hubwang.com/',
    alt: '팬 플랫폼 — 공개 피드' },
  { bundle: 'fan', name: 'fan-2-artists', url: 'https://fan.hubwang.com/artists',
    alt: '팬 플랫폼 — 아티스트 목록' },
  { bundle: 'fan', name: 'fan-3-membership', url: 'https://fan.hubwang.com/membership',
    alt: '팬 플랫폼 — 멤버십 소개' },
];

// 🔴 뷰포트와 스케일을 **고정한다.** 안 그러면 재생성마다 이미지가 달라져 diff 가 무의미
//    해지고, «바뀐 것이 화면인지 내 창 크기인지» 를 못 가른다.
const VIEWPORT = { width: 1280, height: 800 };
const SCALE = 1;

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
  for (const s of SHOTS) console.log(`  ${s.bundle.padEnd(8)} ${s.name.padEnd(22)} ${s.url}`);
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

  await mkdir(OUT_DIR, { recursive: true });
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: VIEWPORT, deviceScaleFactor: SCALE });
  const entries = [];
  let failed = 0;

  for (const s of SHOTS) {
    const file = `${s.name}.${FORMAT.ext}`;
    try {
      // 🔵 networkidle 이 아니라 domcontentloaded + 짧은 안정화. 이 화면들은 백엔드로
      //    가는 요청이 없으므로(ADR-MONO-070) networkidle 은 불필요하게 오래 기다린다.
      const res = await page.goto(s.url, { waitUntil: 'domcontentloaded', timeout: 45000 });
      const status = res ? res.status() : 0;
      // 🔴 200 이 아니면 **캡처하지 않고 실패로 센다.** 옛 이미지를 남기고 성공하면
      //    «화면이 죽었는데 포트폴리오는 멀쩡» 이 된다.
      if (status !== 200) throw new Error(`HTTP ${status}`);
      await page.waitForLoadState('load', { timeout: 20000 }).catch(() => {});
      await page.waitForTimeout(1200); // 폰트·이미지 안정화
      await page.screenshot({ path: join(OUT_DIR, file), fullPage: false,
                              type: FORMAT.type, quality: FORMAT.quality });
      entries.push({ bundle: s.bundle, file, url: s.url, alt: s.alt, status, capturedAt: new Date().toISOString() });
      console.log(`[capture] ✔ ${file}  ← ${s.url}`);
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
