#!/usr/bin/env node
// DEMO-PUBLIC-DATA: 번들 시드를 **생성**한다. 손으로 쓰지 않는다.
//
// =============================================================================
// 왜 시드를 손으로 안 쓰고 생성하는가
// =============================================================================
// 번들 시드(`snapshots/*.json`)는 «외부 설정이 하나도 없어도 화면이 서게 하는 바닥» 이다.
// 그것을 손으로 쓰면 두 가지가 동시에 나빠진다:
//
//   ① 시드가 **변환기를 안 지난다** ⇒ 허용 목록이 시드에는 적용되지 않는다. 그러면 이
//      저장소의 가장 눈에 잘 띄는 공개 JSON 이 **유일하게 검증 안 된 것**이 된다.
//   ② 시드가 «백엔드가 실제로 무엇을 주는가» 와 무관해진다 ⇒ 실제 발행이 처음 도는 날
//      모양이 달라져서 화면이 깨진다. 그때 원인은 발행자가 아니라 **시드가 거짓말을 하고
//      있었던 것**이고, 그 진단은 오래 걸린다.
//
// ⇒ 시드는 «실제 시드 DB 의 행을 담은 픽스처» 를 **발행자와 똑같은 변환기**에 통과시켜
//   만든다. 그러면 시드 자체가 변환기가 도는 증거가 되고, 픽스처의 음성 대조군(본명·재고·
//   회원 전용 본문·삭제된 글)이 산출물에 없다는 사실이 **파일로 남는다.**
//
// 사용법:
//   node infra/demo/public-data/bin/build-bundled-snapshots.mjs          # 생성
//   node infra/demo/public-data/bin/build-bundled-snapshots.mjs --check  # 드리프트만 검사
//
// 🔴 `--check` 는 CI/가드용이다. 생성물이 커밋된 것과 다르면 **실패한다** — 픽스처를 고치고
//    시드를 다시 안 만든 상태가 조용히 머지되지 않게.
// =============================================================================

import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { validateEnvelope } from '../src/contract.mjs';
import {
  toPublicArtist,
  toPublicPost,
  toPublicProduct,
  deriveCategories,
  humanizeCategoryId,
  collectionStatusOf,
} from '../src/transform.mjs';
import { RAW_ARTISTS, RAW_POSTS, RAW_PRODUCTS, CATEGORY_NAMES } from '../fixtures/raw-backend-responses.mjs';
import { MEMBERSHIP_PLANS } from '../fixtures/membership-plans.mjs';
import { CONSOLE_SAMPLE_DOMAINS } from '../fixtures/console-sample.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT_DIR = join(HERE, '..', 'snapshots');

/**
 * 🔴 시드의 `generatedAt`/`dataVersion` 은 **고정값**이다.
 *
 * 지금 시각을 쓰면 `--check` 가 매번 드리프트를 보고하고, 그 가드는 즉시 무시된다(늘
 * 빨간 가드는 없는 가드다). 그리고 시드는 «언제 만들었나» 가 의미 있는 값이 아니다 —
 * 그것은 **발행본**의 축이고, 시드는 저장소의 판이다. 커밋 이력이 그 시각을 안다.
 *
 * 🔴 값을 손으로 올리지 마라. 픽스처가 바뀌면 그 사실은 파일 내용이 말한다.
 */
const SEED_VERSION = '20260907T000000Z-00000001';
const SEED_GENERATED_AT = '2026-09-07T00:00:00.000Z';

function envelope(dataset, source, origin, data, coverage, collectionStatus) {
  return {
    schemaVersion: 1,
    dataset,
    dataVersion: SEED_VERSION,
    generatedAt: SEED_GENERATED_AT,
    source,
    origin,
    coverage,
    collectionStatus,
    data,
  };
}

function buildFan() {
  const artists = RAW_ARTISTS.map(toPublicArtist).filter((a) => a !== null);
  const names = new Map(artists.map((a) => [a.id, a.stageName]));
  const posts = RAW_POSTS.map((p) => toPublicPost(p, names))
    .filter((p) => p !== null)
    .sort((a, b) => String(b.publishedAt).localeCompare(String(a.publishedAt)));
  const data = { artists, posts, membershipPlans: MEMBERSHIP_PLANS };
  return envelope(
    'fan',
    'bundled',
    'repo-bundled',
    data,
    { artists: artists.length, posts: posts.length, membershipPlans: MEMBERSHIP_PLANS.length },
    {
      artists: collectionStatusOf(true, artists),
      posts: collectionStatusOf(true, posts),
      // 🔵 요금제는 저장소가 소유한 `authored` 콘텐츠라 «수집» 이라는 말이 안 맞지만,
      //    봉투 계약이 모든 컬렉션에 상태를 요구한다 — 키 집합이 갈라지면 0건 판정이
      //    망가지기 때문이다(contract.mjs 의 키 대조).
      membershipPlans: collectionStatusOf(true, MEMBERSHIP_PLANS),
    },
  );
}

function buildStore() {
  const products = RAW_PRODUCTS.map(toPublicProduct).filter((p) => p !== null);
  // 🔵 카테고리 표시명은 **백엔드에서 못 얻는다**(조회 API 없음 — 픽스처 헤더 참조).
  //    시드는 마이그레이션의 표시명을 알고 있으므로 그것을 얹고, 모르는 id 는 `humanize` 로
  //    떨어진다 — 실제 발행이 도는 경로와 같은 폴백이다.
  const categories = deriveCategories(products).map((c) => ({
    id: c.id,
    name: CATEGORY_NAMES[c.id] ?? humanizeCategoryId(c.id),
    productCount: c.productCount,
  }));
  const data = { products, categories };
  return envelope(
    'store',
    'bundled',
    'repo-bundled',
    data,
    { products: products.length, categories: categories.length },
    { products: collectionStatusOf(true, products), categories: collectionStatusOf(true, categories) },
  );
}

function buildConsoleSample() {
  const data = { domains: CONSOLE_SAMPLE_DOMAINS };
  return envelope(
    'console-sample',
    // 🔴 `authored` 다. 이 데이터셋에는 백엔드 추출 경로가 **없다**(그것이 설계다 —
    //    콘솔은 정의상 실제 고객·주문·재무 데이터만 그리는 화면이라, 「잘 걸러서 일부만」
    //    이라는 접근 자체가 위험하다). `datasets.ts` 의 console-sample 절 참조.
    'authored',
    'repo-authored',
    data,
    { domains: CONSOLE_SAMPLE_DOMAINS.length },
    { domains: collectionStatusOf(true, CONSOLE_SAMPLE_DOMAINS) },
  );
}

const BUILDERS = { fan: buildFan, store: buildStore, 'console-sample': buildConsoleSample };

/**
 * 🔴🔴 **음성 대조군 검사.** 픽스처에 일부러 넣어 둔 «새면 안 되는 값» 들이 산출물에
 *    문자열로도 없어야 한다.
 *
 * 🔵 이것을 여기(생성기)에도 두는 이유: 시험은 `tests/` 에 있지만, 시험은 **돌려야** 돌고
 *    생성은 **하면** 돈다. 시드를 다시 만드는 사람이 시험을 안 돌려도 여기서 막힌다.
 * 🔴 `realName` 값들은 픽스처에서 **읽어 온다**(여기 다시 적지 않는다) — 두 벌이면 픽스처가
 *    바뀔 때 한쪽만 고쳐지고, 그러면 이 검사는 없는 값을 찾으며 늘 통과한다.
 */
function assertNoLeak(dataset, envelopeObj) {
  const json = JSON.stringify(envelopeObj);
  /** @type {string[]} */
  const banned = [];
  if (dataset === 'fan') {
    for (const a of RAW_ARTISTS) if (a.realName) banned.push(a.realName);
    for (const a of RAW_ARTISTS) if (a.tenantId) banned.push(a.tenantId);
    for (const p of RAW_POSTS) {
      if (p.visibility !== 'PUBLIC' || p.status !== 'PUBLISHED') {
        if (p.body) banned.push(p.body);
        if (p.bodyPreview) banned.push(p.bodyPreview);
        if (p.title && p.title.includes('MUST-NOT-LEAK')) banned.push(p.title);
      }
    }
  }
  if (dataset === 'store') {
    for (const p of RAW_PRODUCTS) {
      if (p.status === 'HIDDEN') {
        banned.push(p.name);
        if (p.description) banned.push(p.description);
      }
    }
    // 재고는 숫자라 문자열 검사로는 못 잡는다 — 필드 이름으로 잡는다(계약도 그렇게 잡는다).
    if (json.includes('"stock"')) {
      throw new Error(`[build-snapshots] '${dataset}' 산출물에 "stock" 필드가 있습니다.`);
    }
  }
  // 🔴🔴 `console-sample` 은 **다른 것을 물어야 한다.**
  //
  // 초판은 세 데이터셋에 같은 검사를 걸었고, 그 순간 이 함수가 자기 비공허성 가드에
  // 걸려 죽었다 — 옳게 죽었다. console-sample 에는 백엔드 픽스처가 **없고**(그것이 설계다),
  // 없는 모집단에 대고 «누출 없음» 을 말하는 것은 공허하다.
  //
  // 🔴 그래서 하한을 내리지 않고 **축을 바꾼다.** 이 데이터셋에서 물어야 할 것은
  //    «백엔드 값이 안 샜나» 가 아니라 **«백엔드에서 온 값이 애초에 없나»** 다:
  //      (1) 봉투가 스스로 `authored` 라고 말하는가
  //      (2) 다른 데이터셋의 실제 시드 식별자가 **한 개도** 안 들어 있는가
  //          — 누군가 "실제 데이터를 조금만 가져다 쓰자" 를 하는 순간 여기가 문다.
  if (dataset === 'console-sample') {
    if (envelopeObj.source !== 'authored') {
      throw new Error(
        `[build-snapshots] console-sample 의 source 가 '${envelopeObj.source}' 입니다(기대 'authored').
` +
          `→ 이 데이터셋에 백엔드 추출 경로가 생겼다는 뜻입니다. 그것이 이 설계가 막는 것입니다.`,
      );
    }
    const realIds = [
      ...RAW_ARTISTS.map((a) => a.id),
      ...RAW_ARTISTS.map((a) => a.realName).filter(Boolean),
      ...RAW_PRODUCTS.map((p) => p.id),
    ];
    for (const needle of realIds) {
      if (json.includes(needle)) {
        throw new Error(
          `[build-snapshots] console-sample 에 다른 데이터셋의 실제 시드 값이 있습니다: ${JSON.stringify(needle)}`,
        );
      }
    }
    return realIds.length;
  }

  // 🔴 대조군의 대조군 — banned 가 비어 있으면 이 검사는 **아무것도 안 하면서 통과**한다.
  if (banned.length === 0) {
    throw new Error(
      `[build-snapshots] '${dataset}' 의 누출 검사 목록이 비었습니다 — 이 검사는 공허합니다.\n` +
        `→ 픽스처에서 음성 대조군(본명·회원전용 본문·숨김 상품)이 사라졌습니다. 되살리세요.`,
    );
  }
  for (const needle of banned) {
    if (json.includes(needle)) {
      throw new Error(
        `[build-snapshots] '${dataset}' 산출물에 공개되면 안 되는 값이 있습니다: ${JSON.stringify(needle.slice(0, 60))}`,
      );
    }
  }
  return banned.length;
}

async function main() {
  const check = process.argv.includes('--check');
  await mkdir(OUT_DIR, { recursive: true });
  let drift = 0;

  for (const [dataset, build] of Object.entries(BUILDERS)) {
    const env = build();
    const v = validateEnvelope(env, { dataset });
    if (!v.ok) {
      console.error(`[build-snapshots] ✗ '${dataset}' 이 계약을 어깁니다: ${v.reason}`);
      process.exit(1);
    }
    const guarded = assertNoLeak(dataset, env);
    const text = `${JSON.stringify(env, null, 2)}\n`;
    const target = join(OUT_DIR, `${dataset}.json`);

    if (check) {
      let existing = null;
      try {
        existing = await readFile(target, 'utf8');
      } catch {
        existing = null;
      }
      // 🔴🔴 TASK-MONO-640 — **줄바꿈으로 비교하지 않는다.**
      //
      // 이 스크립트는 LF 로 쓰는데, `core.autocrlf=true` 인 체크아웃(Windows 기본)은 작업
      // 트리를 **CRLF 로** 만든다. 바이트로 비교하면 내용이 완전히 같아도 세 데이터셋이
      // 전부 «드리프트» 가 된다 — 실측(2026-09-08): 작업트리 `CR=125 LF=125` · 재생성본
      // `CR=0 LF=125` · git blob `CR=0`. 정규화 md5 로는 셋 다 동일했다.
      //
      // 🔴 그 오진은 조용하지 않고 **해롭다**: 아래 메시지가 «픽스처와 다릅니다» 라서 읽는
      //    사람이 «스냅샷이 손으로 편집됐다» 로 결론짓고, 그러면 세 파일을 통째로 다시 써서
      //    **거대한 가짜 diff** 를 만든다. `TASK-MONO-638` AC-0 이 정확히 그 문장을 담고 있었다.
      //
      // 🔵 비교가 느슨해지는 것이 아니다 — JSON 의 의미에 줄바꿈 표현은 들어 있지 않다.
      //    픽스처를 고치고 스냅샷을 안 만든 **진짜 드리프트는 여전히 빨개진다.**
      // 🔵 `generatedAt` 을 상수로 고정한 것(위 § 49~51)과 같은 부류의 처방이다: 비교가
      //    **재려던 것만** 재게 만든다.
      const unixEol = (s) => (s === null ? null : s.replace(/\r\n/g, '\n'));
      if (unixEol(existing) !== unixEol(text)) {
        drift += 1;
        console.error(`[build-snapshots] ✗ 드리프트: ${dataset}.json 이 픽스처와 다릅니다.`);
        console.error(`  → node infra/demo/public-data/bin/build-bundled-snapshots.mjs 로 다시 만드세요.`);
      } else {
        console.log(`[build-snapshots] ✔ ${dataset}.json — 드리프트 없음 (누출 대조군 ${guarded}건)`);
      }
    } else {
      await writeFile(target, text, 'utf8');
      const counts = Object.entries(env.coverage).map(([k, n]) => `${k}=${n}`).join(' ');
      console.log(`[build-snapshots] ✔ ${dataset}.json — ${counts} (누출 대조군 ${guarded}건)`);
    }
  }

  if (drift > 0) process.exit(1);
}

main().catch((err) => {
  console.error(err instanceof Error ? err.stack : String(err));
  process.exit(1);
});
