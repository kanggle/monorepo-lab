#!/usr/bin/env node
// DEMO-PUBLIC-DATA: 공개 저장본 **발행자**. (ADR-MONO-070 § D4)
//
// =============================================================================
// 사용법
// =============================================================================
//   # 백엔드에서 뽑아 Vercel Blob 에 발행 (운영)
//   BLOB_READ_WRITE_TOKEN=… DEMO_PUBLIC_DATA_BASE_URL=https://xxx.public.blob.vercel-storage.com \
//     node infra/demo/public-data/bin/publish-public-data.mjs \
//       --dataset store --from http://ecommerce.1-2-3-4.sslip.io
//
//   # 저장소의 번들 시드를 그대로 발행 (**최초 발행** — 백엔드가 아직 없을 때)
//   BLOB_READ_WRITE_TOKEN=… DEMO_PUBLIC_DATA_BASE_URL=… \
//     node …/publish-public-data.mjs --dataset console-sample --seed
//
//   # 로컬 디렉터리로 발행 (토큰 없이 **절차 자체**를 돌려 본다 — 시험·리허설)
//   node …/publish-public-data.mjs --dataset store --seed --out /tmp/blob \
//     --base-url https://example.invalid
//
// 플래그:
//   --dataset <fan|store|console-sample>   필수
//   --from <baseUrl>      백엔드 게이트웨이. 없으면 --seed 필수.
//   --seed                백엔드 대신 저장소의 번들 시드를 발행한다.
//   --out <dir>           Blob 대신 로컬 디렉터리에 쓴다(리허설).
//   --base-url <url>      저장본의 공개 베이스 URL. 미지정 시 DEMO_PUBLIC_DATA_BASE_URL.
//   --tenant <slug>       추출 대상 테넌트. 기본 demo-corp. § 다중 테넌트.
//   --copy-images-from <origin>  이 오리진의 이미지를 영속 저장소로 **복사**한다(반복 가능).
//   --retain <n>          남길 세대 수(기본 5). 0 이면 현재 세대만 남긴다.
//   --force               더 새로운 세대가 이미 있어도 포인터를 되돌린다. 🔴 기본은 거부.
//   --dry-run             1~3 단계까지만 하고 포인터를 안 옮긴다.
//
// -----------------------------------------------------------------------------
// 🔴🔴 자격증명은 **환경변수에서만** 온다
// -----------------------------------------------------------------------------
// `--token` 같은 플래그를 만들지 않는다 — argv 는 `ps` 에 남고, 셸 히스토리에도 남는다.
// 그리고 이 스크립트는 **공개 요청으로 부를 수 없다**: 실행 경로가 사람의 셸이거나
// 데모 호스트의 시드 훅이고, 그 어느 쪽도 HTTP 표면이 아니다. 요구사항의
// *"공개 요청으로 임의 데이터 발행·서버 기동·백엔드 URL 호출이 가능하지 않게 한다"* 는
// **엔드포인트를 만들지 않음으로써** 만족된다 — 만들고 지키는 것보다 강하다.
//
// -----------------------------------------------------------------------------
// 🔴 꺼진 EC2 를 깨우지 않는다
// -----------------------------------------------------------------------------
// *"갱신을 위해 꺼진 EC2 를 주기적으로 깨우는 Cron 은 만들지 마라"* — 그래서 이 파일에는
// **스케줄러가 없고**, `--from` 이 응답하지 않으면 그냥 실패한다(깨우지 않는다).
// 발행 시점은 넷뿐이고 전부 **이미 서버가 떠 있는 순간**이다:
//   ① 최초 발행        — `--seed` (서버 없이 돈다)
//   ② 시드 완료 직후    — `seed-demo-domain.sh` 가 부른다(서버가 방금 떴다)
//   ③ 운영 중 변경 후   — 사람이 부른다(서버가 떠 있다)
//   ④ 수동 갱신        — 같음
// =============================================================================

import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { PUBLIC_DATASETS } from '../src/contract.mjs';
import { createLocalStore, createBlobStore } from '../src/store.mjs';
import { buildEnvelope, publishEnvelope, copyImage, newDataVersion } from '../src/publish.mjs';
import {
  toPublicArtist,
  toPublicPost,
  toPublicProduct,
  deriveCategories,
  humanizeCategoryId,
  collectionStatusOf,
} from '../src/transform.mjs';
import { MEMBERSHIP_PLANS } from '../fixtures/membership-plans.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const log = (m) => console.log(m);
const die = (m) => {
  console.error(`[publish] ✗ ${m}`);
  process.exit(1);
};

// ---------------------------------------------------------------------------
// 인자
// ---------------------------------------------------------------------------
function parseArgs(argv) {
  const out = { copyImagesFrom: [], retain: 5 };
  for (let i = 0; i < argv.length; i += 1) {
    const a = argv[i];
    const next = () => {
      const v = argv[i + 1];
      if (v === undefined || v.startsWith('--')) die(`${a} 에 값이 없습니다`);
      i += 1;
      return v;
    };
    switch (a) {
      case '--dataset': out.dataset = next(); break;
      case '--from': out.from = next(); break;
      case '--seed': out.seed = true; break;
      case '--out': out.out = next(); break;
      case '--base-url': out.baseUrl = next(); break;
      case '--tenant': out.tenant = next(); break;
      case '--copy-images-from': out.copyImagesFrom.push(next()); break;
      case '--retain': out.retain = Number(next()); break;
      case '--force': out.force = true; break;
      case '--dry-run': out.dryRun = true; break;
      default: die(`알 수 없는 인자: ${a}`);
    }
  }
  return out;
}

const args = parseArgs(process.argv.slice(2));
if (!args.dataset || !PUBLIC_DATASETS.includes(args.dataset)) {
  die(`--dataset 은 ${PUBLIC_DATASETS.join(' | ')} 중 하나여야 합니다 (받은 값: ${args.dataset ?? '(없음)'})`);
}
// 🔴 `console-sample` 에는 **추출 경로가 없다**(설계다 — `datasets.ts` 의 그 절 참조).
//    `--from` 을 주면 조용히 무시하지 않고 거부한다: 무시하면 «뽑았다고 믿는데 안 뽑힌»
//    상태가 되고, 그 상태의 산출물은 시드와 같아 **구별되지 않는다.**
if (args.dataset === 'console-sample' && args.from) {
  die('console-sample 은 백엔드에서 추출하지 않습니다 — 합성 데이터셋입니다. --seed 를 쓰세요.\n' +
      '  이유: 콘솔은 정의상 실제 고객·주문·재무 데이터만 그리는 화면이라, 한 필드만 새도 그것이\n' +
      '  실제 운영 데이터입니다. 그래서 추출 경로 자체를 두지 않았습니다.');
}
if (!args.from && !args.seed) die('--from <baseUrl> 또는 --seed 중 하나가 필요합니다');
if (args.from && args.seed) die('--from 과 --seed 는 함께 쓸 수 없습니다 (무엇을 발행하는지 모호해집니다)');

const baseUrl = args.baseUrl ?? process.env.DEMO_PUBLIC_DATA_BASE_URL ?? '';
if (!/^https:\/\//.test(baseUrl)) {
  die('공개 베이스 URL 이 필요합니다 — --base-url 또는 DEMO_PUBLIC_DATA_BASE_URL (https 절대주소).\n' +
      '  🔴 이 값이 포인터의 url 이 됩니다. 틀리면 판독자가 저장본을 못 읽고 **조용히 번들 시드로**\n' +
      '     떨어집니다 — 화면은 뜨지만 발행한 데이터가 아무 데도 안 나옵니다.');
}

// ---------------------------------------------------------------------------
// 저장소 선택
// ---------------------------------------------------------------------------
async function makeStore() {
  if (args.out) {
    log(`[publish] 저장소 = 로컬 디렉터리 ${args.out} (리허설)`);
    return createLocalStore({ dir: args.out, baseUrl });
  }
  const token = process.env.BLOB_READ_WRITE_TOKEN;
  if (!token) {
    die('BLOB_READ_WRITE_TOKEN 이 없습니다.\n' +
        '  운영: Vercel 프로젝트의 Blob 스토어에서 발급해 **환경변수로만** 주세요(argv 금지 — ps 에 남습니다).\n' +
        '  리허설: --out <dir> 로 로컬 디렉터리에 같은 절차를 돌려 볼 수 있습니다.');
  }
  log('[publish] 저장소 = Vercel Blob');
  return createBlobStore({ token, baseUrl });
}

// ---------------------------------------------------------------------------
// HTTP 수집 — 🔴 **실패를 «0건» 으로 번역하지 않는다**
// ---------------------------------------------------------------------------
const FETCH_TIMEOUT_MS = 15_000;

async function getJson(url, token) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
  try {
    const headers = { accept: 'application/json' };
    if (token) headers.authorization = `Bearer ${token}`;
    const res = await fetch(url, { signal: controller.signal, headers });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
  } finally {
    clearTimeout(timer);
  }
}

/**
 * 페이지를 끝까지 따라간다.
 *
 * 🔴 **부분 수집을 성공으로 보고하지 않는다.** 3페이지 중 2페이지만 받고 끝나면 그것은
 *    «상품이 줄었다» 가 아니라 «수집이 깨졌다» 이고, 두 상태는 산출물에서 **구별되지
 *    않는다.** 그래서 한 페이지라도 실패하면 `{fetched:false}` 로 돌려주고, 봉투는
 *    `collectionStatus: 'failed'` 를 받아 **발행 자체가 거부된다.**
 * 🔴 상한을 둔다. 서버가 `totalElements` 를 잘못 주면 무한 루프가 되고, 그때 이 스크립트는
 *    «느리다» 로 보인다(멈춘 것과 구별 안 됨).
 */
async function fetchAllPages(baseHref, token, { pageParam = 'page', sizeParam = 'size', size = 100, maxPages = 50 } = {}) {
  const rows = [];
  for (let page = 0; page < maxPages; page += 1) {
    const sep = baseHref.includes('?') ? '&' : '?';
    const url = `${baseHref}${sep}${pageParam}=${page}&${sizeParam}=${size}`;
    let body;
    try {
      body = await getJson(url, token);
    } catch (err) {
      return { fetched: false, rows: [], error: `${url} → ${err instanceof Error ? err.message : String(err)}` };
    }
    const content = Array.isArray(body) ? body : Array.isArray(body?.content) ? body.content : null;
    if (content === null) {
      return { fetched: false, rows: [], error: `${url} → 응답에 배열이 없습니다` };
    }
    rows.push(...content);
    const total = typeof body?.totalElements === 'number' ? body.totalElements : null;
    if (content.length < size) break;
    if (total !== null && rows.length >= total) break;
    if (page === maxPages - 1) {
      return { fetched: false, rows: [], error: `페이지 상한 ${maxPages} 초과 — 서버의 페이지 정보가 이상합니다` };
    }
  }
  return { fetched: true, rows };
}

// ---------------------------------------------------------------------------
// 데이터셋별 추출
// ---------------------------------------------------------------------------
const token = process.env.DEMO_PUBLISH_ACCESS_TOKEN || '';

async function extractFan(from) {
  // 🔴 경로는 팬 게이트웨이의 것이다: `/api/v1/**` 만 받아 다운스트림 `/api/**` 로 rewrite
  //    한다 — `/api/artists` 는 **404** 다(`infra/demo/seed/seed-fan.sh` 헤더의 실측).
  const artistsRes = await fetchAllPages(`${from}/api/v1/artists`, token);
  const artists = artistsRes.fetched ? artistsRes.rows.map(toPublicArtist).filter((a) => a !== null) : [];
  if (!artistsRes.fetched) console.error(`[publish] ⚠ 아티스트 수집 실패: ${artistsRes.error}`);

  const names = new Map(artists.map((a) => [a.id, a.stageName]));
  const postsRes = await fetchAllPages(`${from}/api/v1/community/feed`, token);
  const posts = postsRes.fetched
    ? postsRes.rows.map((p) => toPublicPost(p, names)).filter((p) => p !== null)
        .sort((a, b) => String(b.publishedAt).localeCompare(String(a.publishedAt)))
    : [];
  if (!postsRes.fetched) console.error(`[publish] ⚠ 게시물 수집 실패: ${postsRes.error}`);

  return {
    data: { artists, posts, membershipPlans: MEMBERSHIP_PLANS },
    coverage: { artists: artists.length, posts: posts.length, membershipPlans: MEMBERSHIP_PLANS.length },
    collectionStatus: {
      artists: collectionStatusOf(artistsRes.fetched, artists),
      posts: collectionStatusOf(postsRes.fetched, posts),
      // 요금제는 저장소가 소유한 authored 콘텐츠라 수집이 아니지만, 봉투 계약이 모든
      // 컬렉션에 상태를 요구한다(키 집합이 갈라지면 0건 판정이 망가진다).
      membershipPlans: collectionStatusOf(true, MEMBERSHIP_PLANS),
    },
  };
}

async function extractStore(from) {
  const listRes = await fetchAllPages(`${from}/api/products`, token);
  if (!listRes.fetched) console.error(`[publish] ⚠ 상품 목록 수집 실패: ${listRes.error}`);

  // 🔴 목록만으로는 **상세가 없다** — `ProductSummary` 에는 description·images·variants 가
  //    없다. 목록만 저장하고 상세는 백엔드를 부르는 구현이 요구사항이 금지한 바로 그것이므로,
  //    여기서 상세를 **전부** 받는다.
  // 🔴 상세 하나라도 실패하면 **컬렉션 전체를 failed** 로 만든다. 「일부 상품만 상세가 없다」
  //    는 화면에서 «그 상품은 원래 설명이 없다» 로 보이고, 그 오독은 되돌릴 수 없다.
  let detailsOk = listRes.fetched;
  const products = [];
  for (const summary of listRes.rows) {
    const id = summary?.id ?? summary?.productId;
    if (typeof id !== 'string') { detailsOk = false; continue; }
    try {
      const detail = await getJson(`${from}/api/products/${encodeURIComponent(id)}`, token);
      const pub = toPublicProduct(detail);
      if (pub !== null) products.push(pub);
    } catch (err) {
      detailsOk = false;
      console.error(`[publish] ⚠ 상품 상세 실패 ${id}: ${err instanceof Error ? err.message : String(err)}`);
    }
  }

  // 🔵 카테고리는 **파생**이다 — 백엔드에 조회 API 가 없다(실측: product-service 에
  //    CategoryJpaEntity 는 있으나 컨트롤러 매핑이 없고, api-client 에도 category 서비스가
  //    없다). 없는 것을 있는 척하지 않고, 이름은 id 를 사람이 읽게 다듬는다.
  const categories = deriveCategories(products).map((c) => ({
    id: c.id, name: humanizeCategoryId(c.id), productCount: c.productCount,
  }));

  return {
    data: { products, categories },
    coverage: { products: products.length, categories: categories.length },
    collectionStatus: {
      products: collectionStatusOf(detailsOk, products),
      categories: collectionStatusOf(detailsOk, categories),
    },
  };
}

// ---------------------------------------------------------------------------
// 이미지 복사
// ---------------------------------------------------------------------------
/**
 * 🔴🔴 **기본값이 «복사 안 함» 이다.** 요구사항: *"접근 제한 이미지나 재배포 권리가 없는
 *    외부 이미지를 무분별하게 복사하지 않는다."*
 *
 *    데모 시드의 상품 썸네일은 **Unsplash CDN 주소**다(V9/V11 마이그레이션). 그것은 이미
 *    공개 CDN 이고 브라우저가 직접 열 수 있으므로 **복사할 이유가 없고**, 복사하면 재배포가
 *    된다. 반면 MinIO(데모 호스트) 이미지는 EC2 가 꺼지면 **열리지 않으므로** 복사해야 한다.
 *
 *    그 구별을 자동으로 하지 않고 `--copy-images-from` 으로 **명시하게** 한다. 자동 판정을
 *    두면 새 출처가 조용히 복사 대상이 된다.
 */
async function rewriteImages(store, data, allowedOrigins) {
  if (allowedOrigins.length === 0) {
    log('[publish] 이미지 복사 대상 오리진이 지정되지 않았습니다 — 주소를 그대로 둡니다 (--copy-images-from)');
    return { copied: 0, dropped: 0 };
  }
  const cache = new Map();
  let copied = 0, dropped = 0;
  const one = async (url) => {
    if (typeof url !== 'string' || url === '') return url;
    let origin;
    try { origin = new URL(url).origin; } catch { return url; }
    // 허용 목록 밖이면 **그대로 둔다**(복사도 삭제도 안 한다) — Unsplash 같은 공개 CDN 이
    // 여기로 온다. 🔵 허용 목록 안인데 복사에 실패하면 **null 로 만든다**: 그 주소는
    // EC2 가 꺼지면 안 열리므로, 남겨 두면 화면에 깨진 이미지가 뜬다.
    if (!allowedOrigins.includes(origin)) return url;
    const copiedUrl = await copyImage({ store, url, allowedOrigins, cache, log });
    if (copiedUrl === null) { dropped += 1; return null; }
    copied += 1;
    return copiedUrl;
  };

  for (const a of data.artists ?? []) a.profileImageUrl = await one(a.profileImageUrl);
  for (const p of data.posts ?? []) {
    const next = [];
    for (const u of p.imageUrls ?? []) { const r = await one(u); if (r) next.push(r); }
    p.imageUrls = next;
  }
  for (const p of data.products ?? []) {
    p.thumbnailUrl = await one(p.thumbnailUrl);
    const next = [];
    for (const img of p.images ?? []) { const r = await one(img.url); if (r) next.push({ ...img, url: r }); }
    p.images = next;
  }
  return { copied, dropped };
}

// ---------------------------------------------------------------------------
// main
// ---------------------------------------------------------------------------
async function main() {
  const store = await makeStore();
  let envelope;

  if (args.seed) {
    // 최초 발행 — 저장소의 번들 시드를 **그대로** 올린다. 🔴 새 세대 식별자를 부여한다:
    //    시드의 고정 dataVersion 을 그대로 쓰면 두 번째 발행이 «같은 세대» 로 거부된다.
    const raw = JSON.parse(await readFile(join(HERE, '..', 'snapshots', `${args.dataset}.json`), 'utf8'));
    envelope = buildEnvelope({
      dataset: args.dataset,
      source: raw.source,
      origin: 'repo-seed',
      data: raw.data,
      coverage: raw.coverage,
      collectionStatus: raw.collectionStatus,
      dataVersion: newDataVersion(),
    });
    log(`[publish] ① 번들 시드 발행 — ${JSON.stringify(raw.coverage)}`);
  } else {
    const from = args.from.replace(/\/+$/, '');
    log(`[publish] ① 백엔드에서 추출 — ${from} (tenant=${args.tenant ?? 'demo-corp'})`);
    const extracted = args.dataset === 'fan' ? await extractFan(from) : await extractStore(from);
    const img = await rewriteImages(store, extracted.data, args.copyImagesFrom);
    if (img.copied || img.dropped) log(`[publish]   이미지 — 복사 ${img.copied} · 제외 ${img.dropped}`);
    envelope = buildEnvelope({
      dataset: args.dataset,
      source: 'backend',
      origin: from,
      data: extracted.data,
      coverage: extracted.coverage,
      collectionStatus: extracted.collectionStatus,
    });
    log(`[publish]   수집 — ${JSON.stringify(extracted.coverage)} / ${JSON.stringify(extracted.collectionStatus)}`);
  }

  if (args.dryRun) {
    // 🔴 dry-run 도 **검증은 한다** — 그것이 dry-run 의 요점이다. 검증 없이 "만들어 봤다"
    //    는 아무것도 말해 주지 않는다.
    const { validateEnvelope } = await import('../src/contract.mjs');
    const v = validateEnvelope(envelope, { dataset: args.dataset });
    if (!v.ok) die(`dry-run 검증 실패: ${v.reason}`);
    log(`[publish] ✔ dry-run — 봉투는 유효합니다 (dataVersion=${envelope.dataVersion}). 포인터는 안 옮겼습니다.`);
    return;
  }

  const result = await publishEnvelope({ store, envelope, force: args.force, log });
  log(`[publish] ✔ 발행 완료 — dataVersion=${result.dataVersion}`);
  log(`[publish]   url=${result.url}`);
  if (result.removed) log(`[publish]   구세대 ${result.removed}개 정리됨`);
}

main().catch((err) => {
  console.error(err instanceof Error ? err.stack : String(err));
  process.exit(1);
});
