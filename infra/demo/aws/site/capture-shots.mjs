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
//   node infra/demo/aws/site/capture-shots.mjs --dry-run        # 목록만 찍어 본다
//
// 🔴 Playwright 가 필요하다. 이 저장소에는 ecommerce/fan 워크스페이스가 이미 들고 있으므로
//    그 설치본을 쓴다(`--from <경로>` 로 다른 설치본을 가리킬 수 있다).
// =============================================================================

import { mkdir, writeFile, readFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
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

async function loadChromium(from) {
  const candidates = from ? [from] : [
    resolve(HERE, '../../../../projects/ecommerce-microservices-platform/node_modules/@playwright/test'),
    resolve(HERE, '../../../../projects/fan-platform/node_modules/@playwright/test'),
    resolve(HERE, '../../../../projects/platform-console/node_modules/@playwright/test'),
  ];
  for (const c of candidates) {
    if (!existsSync(c)) continue;
    try {
      const mod = await import(new URL('index.mjs', `file:///${c.replace(/\\/g, '/')}/`).href);
      if (mod.chromium) return mod.chromium;
    } catch {
      /* 다음 후보 */
    }
    try {
      const mod = await import(`file:///${c.replace(/\\/g, '/')}/index.js`);
      if (mod.chromium) return mod.chromium;
    } catch {
      /* 다음 후보 */
    }
  }
  return null;
}

async function main() {
  const dry = process.argv.includes('--dry-run');
  const fromIdx = process.argv.indexOf('--from');
  const from = fromIdx >= 0 ? process.argv[fromIdx + 1] : null;

  console.log(`[capture] ${SHOTS.length}장 · 뷰포트 ${VIEWPORT.width}x${VIEWPORT.height} · scale ${SCALE}`);
  for (const s of SHOTS) console.log(`  ${s.bundle.padEnd(8)} ${s.name.padEnd(22)} ${s.url}`);
  if (dry) {
    console.log('[capture] --dry-run — 찍지 않았습니다.');
    return;
  }

  const chromium = await loadChromium(from);
  if (!chromium) {
    console.error('[capture] ✗ Playwright 를 못 찾았습니다.');
    console.error('  → 워크스페이스 중 하나에서 설치하거나 --from <@playwright/test 경로> 를 주세요.');
    process.exit(1);
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
