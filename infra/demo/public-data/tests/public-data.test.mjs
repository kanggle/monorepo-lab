// DEMO-PUBLIC-DATA: 계약·변환·발행 절차 시험. `node --test` 로 돈다(러너 설치 없음).
//
// =============================================================================
// 무엇을 시험하고, 무엇을 **안** 시험하는가 — 그리고 왜 그 경계인가
// =============================================================================
// 이 저장소는 백엔드를 이 세션에서 띄울 수 없다. 그러니 «검증했다» 를 말하려면 **무엇이
// 검증 가능한가**를 먼저 정직하게 갈라야 한다:
//
//   시험한다:
//     · 필드 허용 목록 (개인정보·회원 전용 본문·재고가 안 나가는가)  ← **보안 성질**
//     · 봉투·포인터 계약 (부분 수집·세대 불일치를 거부하는가)
//     · 질의 의미 (검색·필터·정렬·페이지가 백엔드와 같은 뜻인가)
//     · 발행 **절차** (정상본 보존·동시 발행·구세대 정리) ← 로컬 저장소 어댑터로 **실제 실행**
//
//   안 시험한다(그리고 그 사실을 보고에 적는다):
//     · Vercel Blob 어댑터의 실제 왕복 — 이 저장소에 토큰이 없다(TASK-MONO-575 실측).
//     · 백엔드 HTTP 배관 — 게이트웨이가 안 떠 있다.
//
// 🔴 «로컬 어댑터로 돌았으니 Blob 도 된다» 고 쓰지 마라. 로컬 어댑터가 통과시키는 것은
//    **절차**이지 Blob 이 아니다. 두 어댑터가 같은 발행자 코드를 받는다는 사실이 위험을
//    줄일 뿐, 없애지 않는다.
//
// 실행: node --test infra/demo/public-data/tests/
// =============================================================================

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  PUBLIC_DATA_SCHEMA_VERSION,
  validateEnvelope,
  validateEnvelopeShape,
  validatePointerShape,
  validateDatasetData,
  makeDataVersion,
  imagePath,
} from '../src/contract.mjs';
import { createLocalStore } from '../src/store.mjs';
import { buildEnvelope, publishEnvelope, readCurrentPointer, pruneOldVersions, newDataVersion } from '../src/publish.mjs';
import {
  toPublicArtist, toPublicPost, toPublicProduct, deriveCategories, collectionStatusOf,
} from '../src/transform.mjs';
import { RAW_ARTISTS, RAW_POSTS, RAW_PRODUCTS } from '../fixtures/raw-backend-responses.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const BASE = 'https://blob.example.invalid';

// ===========================================================================
// 1. 허용 목록 — **보안 성질**. 이 절이 이 패키지의 존재 이유다.
// ===========================================================================

test('아티스트: 본명·계정·테넌트가 공개 DTO 에 없다', () => {
  const pub = RAW_ARTISTS.map(toPublicArtist).filter(Boolean);
  assert.ok(pub.length >= 3, '모집단이 비면 이 시험은 공허하다');
  for (const a of pub) {
    assert.ok(!('realName' in a), 'realName 이 새어 나왔다');
    assert.ok(!('accountId' in a), 'accountId 가 새어 나왔다');
    assert.ok(!('tenantId' in a), 'tenantId 가 새어 나왔다');
  }
  // 🔴 **양성 대조군** — 픽스처에 본명이 실제로 있었는지 확인한다. 없으면 위 단언은
  //    «없는 것을 못 찾은» 것이고 아무것도 증명하지 않는다.
  assert.ok(RAW_ARTISTS.some((a) => typeof a.realName === 'string' && a.realName !== ''),
    '픽스처에 realName 이 없다 — 위 단언이 공허하다');
});

test('아티스트: 공개 상태가 아니면 목록에서 사라진다', () => {
  const draft = RAW_ARTISTS.find((a) => a.status !== 'PUBLISHED');
  assert.ok(draft, '픽스처에 비공개 아티스트(음성 대조군)가 없다');
  assert.equal(toPublicArtist(draft), null);
  // 🔵 그리고 공개 상태인 것은 통과해야 한다 — «전부 거른다» 와 구별한다.
  const pubd = RAW_ARTISTS.find((a) => a.status === 'PUBLISHED');
  assert.notEqual(toPublicArtist(pubd), null);
});

test('아티스트 status 어휘가 PUBLISHED 다 (ACTIVE 가 아니다)', () => {
  // 🔴🔴 초판이 `ACTIVE` 로 짰다가 실측이 뒤집었다(seed-fan.sh 의 INSERT). `ACTIVE` 였다면
  //    시드된 아티스트 **3명 전부**가 걸러져 빈 목록이 되고, 그 빈 목록은 «데이터 없음» 으로
  //    보인다 — 수집 실패와 구별되지 않는 모양이다. 그 회귀를 여기서 고정한다.
  assert.notEqual(toPublicArtist({ id: 'x', stageName: 'n', status: 'PUBLISHED' }), null);
  assert.equal(toPublicArtist({ id: 'x', stageName: 'n', status: 'ACTIVE' }), null);
});

test('게시물: 회원 전용 본문과 bodyPreview 가 **둘 다** 사라진다', () => {
  const names = new Map(RAW_ARTISTS.filter((a) => a.status === 'PUBLISHED').map((a) => [a.id, a.stageName]));
  const locked = RAW_POSTS.find((p) => p.visibility === 'MEMBERS_ONLY');
  assert.ok(locked, '픽스처에 회원 전용 글(음성 대조군)이 없다');
  assert.ok(locked.body && locked.bodyPreview, '픽스처의 회원 전용 글에 본문/미리보기가 있어야 한다');

  const pub = toPublicPost(locked, names);
  assert.ok(pub, '회원 전용 글은 목록에는 남는다(제목 + 잠김 표시)');
  assert.equal(pub.body, null);
  assert.ok(!('bodyPreview' in pub));
  assert.equal(pub.locked, true);
  assert.deepEqual(pub.imageUrls, [], '이미지 경로도 본문이다');

  // 🔴 직렬화한 문자열에도 없어야 한다 — 필드 이름만 보면 중첩된 자리를 놓친다.
  const json = JSON.stringify(pub);
  assert.ok(!json.includes(locked.body));
  assert.ok(!json.includes(locked.bodyPreview));
});

test('게시물: 삭제된 글과 고아 글이 사라진다', () => {
  const names = new Map(RAW_ARTISTS.filter((a) => a.status === 'PUBLISHED').map((a) => [a.id, a.stageName]));
  const deleted = RAW_POSTS.find((p) => p.status === 'DELETED');
  assert.ok(deleted, '픽스처에 삭제된 글이 없다');
  assert.equal(toPublicPost(deleted, names), null);

  // 걸러진 아티스트의 글 — 아티스트가 안 나가면 그 글도 안 나가야 한다.
  const orphan = RAW_POSTS.find((p) => p.title.includes('ORPHAN'));
  assert.ok(orphan, '픽스처에 고아 글이 없다');
  assert.equal(toPublicPost(orphan, names), null);
});

test('상품: 재고가 어디에도 없다', () => {
  const pub = RAW_PRODUCTS.map(toPublicProduct).filter(Boolean);
  assert.ok(pub.length >= 8, '모집단이 비면 공허하다');
  const json = JSON.stringify(pub);
  assert.ok(!json.includes('"stock"'), 'stock 필드가 새어 나왔다');
  // 🔴 **양성 대조군** — 픽스처에는 재고가 실제로 있었다.
  assert.ok(JSON.stringify(RAW_PRODUCTS).includes('"stock"'), '픽스처에 stock 이 없다 — 위 단언이 공허하다');
  // 옵션 자체는 남아야 한다(재고만 빠지는 것이지 옵션이 빠지는 게 아니다).
  assert.ok(pub.some((p) => p.options.length > 0), '옵션이 통째로 사라졌다');
});

test('상품: HIDDEN 은 공개 목록에 없다', () => {
  const hidden = RAW_PRODUCTS.find((p) => p.status === 'HIDDEN');
  assert.ok(hidden, '픽스처에 숨김 상품(음성 대조군)이 없다');
  assert.equal(toPublicProduct(hidden), null);
  // 모르는 상태도 거른다 — 모르는 것은 통과가 아니다.
  assert.equal(toPublicProduct({ id: 'x', name: 'n', status: 'WEIRD' }), null);
});

test('카테고리는 상품에서 파생되고 건수가 맞는다', () => {
  const pub = RAW_PRODUCTS.map(toPublicProduct).filter(Boolean);
  const cats = deriveCategories(pub);
  const total = cats.reduce((n, c) => n + c.productCount, 0);
  assert.equal(total, pub.length, '카테고리 건수 합이 상품 수와 다르다');
});

// ===========================================================================
// 2. 봉투·포인터 계약
// ===========================================================================

const okEnvelope = (over = {}) => ({
  schemaVersion: PUBLIC_DATA_SCHEMA_VERSION,
  dataset: 'store',
  dataVersion: '20260101T000000Z-aaaaaaaa',
  generatedAt: '2026-01-01T00:00:00.000Z',
  source: 'backend',
  origin: 'http://x.invalid',
  coverage: { products: 1, categories: 1 },
  collectionStatus: { products: 'ok', categories: 'ok' },
  data: { products: [{ id: 'p', name: 'n', status: 'ON_SALE', options: [] }], categories: [{ id: 'c' }] },
  ...over,
});

test('봉투: 정상본은 통과한다 (대조군)', () => {
  assert.equal(validateEnvelope(okEnvelope(), { dataset: 'store' }).ok, true);
});

test('봉투: collectionStatus 에 failed 가 있으면 거부한다', () => {
  const r = validateEnvelope(okEnvelope({ collectionStatus: { products: 'failed', categories: 'ok' } }), { dataset: 'store' });
  assert.equal(r.ok, false);
  assert.match(r.reason, /failed/);
});

test('봉투: coverage 와 collectionStatus 의 키가 다르면 거부한다', () => {
  const r = validateEnvelopeShape(okEnvelope({ coverage: { products: 1 } }), { dataset: 'store' });
  assert.equal(r.ok, false);
  assert.match(r.reason, /키/);
});

test('봉투: 포인터가 말한 세대와 다르면 거부한다', () => {
  const r = validateEnvelopeShape(okEnvelope(), { dataset: 'store', dataVersion: 'other' });
  assert.equal(r.ok, false);
});

test('봉투: 다른 데이터셋의 것이면 거부한다', () => {
  assert.equal(validateEnvelopeShape(okEnvelope(), { dataset: 'fan' }).ok, false);
});

test('봉투: 404 HTML 같은 비-JSON 객체를 거부한다', () => {
  for (const bad of ['<html>404</html>', null, [], 42]) {
    assert.equal(validateEnvelopeShape(bad, { dataset: 'store' }).ok, false, String(bad));
  }
});

test('내용물: 잠긴 글에 본문이 실려 오면 봉투를 거부한다 (두 번째 겹)', () => {
  const r = validateDatasetData('fan', {
    artists: [], membershipPlans: [],
    posts: [{ id: 'p', locked: true, visibility: 'MEMBERS_ONLY', body: '샜다', imageUrls: [] }],
  });
  assert.equal(r.ok, false);
});

test('내용물: 옵션에 stock 이 실려 오면 봉투를 거부한다', () => {
  const r = validateDatasetData('store', {
    categories: [],
    products: [{ id: 'p', status: 'ON_SALE', options: [{ optionName: 'M', stock: 3 }] }],
  });
  assert.equal(r.ok, false);
  assert.match(r.reason, /stock/);
});

test('포인터: https 가 아니면 거부한다', () => {
  const p = { schemaVersion: 1, dataset: 'store', dataVersion: 'v', url: 'http://x/y', publishedAt: 't', source: 'backend' };
  assert.equal(validatePointerShape(p, { dataset: 'store' }).ok, false);
  assert.equal(validatePointerShape({ ...p, url: 'https://x/y' }, { dataset: 'store' }).ok, true);
});

test('세대 식별자는 사전순 = 시간순이다', () => {
  const a = makeDataVersion(new Date('2026-01-01T00:00:00Z'), 'aaaaaaaa');
  const b = makeDataVersion(new Date('2026-01-01T00:00:01Z'), '00000000');
  // 🔴 동시 발행 판정이 이 성질에 **전부** 걸려 있다. 무작위 접미사가 앞서도 시각이 이겨야 한다.
  assert.ok(a < b, `${a} < ${b} 여야 한다`);
});

test('이미지 경로는 내용 주소다 — 같은 바이트면 같은 경로', () => {
  assert.equal(imagePath('abc', 'jpeg'), imagePath('abc', 'jpeg'));
  assert.notEqual(imagePath('abc', 'jpeg'), imagePath('abd', 'jpeg'));
  // 확장자 정규화 — `image/svg+xml` 같은 값이 경로를 깨지 않게.
  assert.equal(imagePath('abc', 'svg+xml'), 'public-data/img/abc.svgxml');
});

test('collectionStatusOf 가 «0건» 과 «수집 실패» 를 가른다', () => {
  assert.equal(collectionStatusOf(true, []), 'empty');
  assert.equal(collectionStatusOf(false, []), 'failed');
  assert.equal(collectionStatusOf(true, [1]), 'ok');
  // 🔴 이 세 값이 같아지는 날 «수집이 깨졌다» 가 «데이터가 없다» 로 조용히 번역된다.
});

// ===========================================================================
// 3. 발행 절차 — 로컬 저장소 어댑터로 **실제 실행**한다
// ===========================================================================

async function withStore(fn) {
  const dir = await mkdtemp(join(tmpdir(), 'pubdata-'));
  try {
    await fn(createLocalStore({ dir, baseUrl: BASE }), dir);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
}

test('발행: 세대를 올리고 포인터를 그 세대로 옮긴다', async () => {
  await withStore(async (store) => {
    const env = buildEnvelope({
      dataset: 'store', source: 'backend', origin: 'x', data: okEnvelope().data,
      coverage: okEnvelope().coverage, collectionStatus: okEnvelope().collectionStatus,
    });
    const r = await publishEnvelope({ store, envelope: env });
    const ptr = await readCurrentPointer(store, 'store');
    assert.equal(ptr.dataVersion, r.dataVersion);
    assert.equal(ptr.url, `${BASE}/public-data/store/v/${r.dataVersion}.json`);
  });
});

test('발행: 부분 수집(failed)은 포인터를 **안 옮긴다** — 정상본이 남는다', async () => {
  await withStore(async (store) => {
    const good = buildEnvelope({
      dataset: 'store', source: 'backend', origin: 'x', data: okEnvelope().data,
      coverage: okEnvelope().coverage, collectionStatus: okEnvelope().collectionStatus,
    });
    await publishEnvelope({ store, envelope: good });

    const bad = buildEnvelope({
      dataset: 'store', source: 'backend', origin: 'x', data: { products: [], categories: [] },
      coverage: { products: 0, categories: 0 },
      collectionStatus: { products: 'failed', categories: 'ok' },
    });
    await assert.rejects(() => publishEnvelope({ store, envelope: bad }), /failed/);

    // 🔴🔴 판정은 «예외가 났나» 가 아니라 **«포인터가 그대로인가»** 다. 예외만 확인하면
    //    "던지고 나서 이미 옮겼다" 를 못 잡는다.
    const ptr = await readCurrentPointer(store, 'store');
    assert.equal(ptr.dataVersion, good.dataVersion, '실패한 발행이 정상본을 덮었다');
  });
});

test('발행: 오래된 작업이 새 세대를 덮지 못한다', async () => {
  await withStore(async (store) => {
    const mk = (v) => buildEnvelope({
      dataset: 'store', source: 'backend', origin: 'x', data: okEnvelope().data,
      coverage: okEnvelope().coverage, collectionStatus: okEnvelope().collectionStatus, dataVersion: v,
    });
    const newer = mk('20260202T000000Z-bbbbbbbb');
    await publishEnvelope({ store, envelope: newer });

    const older = mk('20260101T000000Z-aaaaaaaa');
    await assert.rejects(() => publishEnvelope({ store, envelope: older }), /새로운/);
    assert.equal((await readCurrentPointer(store, 'store')).dataVersion, newer.dataVersion);

    // --force 는 **되돌릴 수 있어야** 한다 — 명시적 의사표시니까.
    await publishEnvelope({ store, envelope: older, force: true });
    assert.equal((await readCurrentPointer(store, 'store')).dataVersion, older.dataVersion);
  });
});

test('발행: 같은 세대를 두 번 발행하면 거부한다', async () => {
  await withStore(async (store) => {
    const env = buildEnvelope({
      dataset: 'store', source: 'backend', origin: 'x', data: okEnvelope().data,
      coverage: okEnvelope().coverage, collectionStatus: okEnvelope().collectionStatus,
      dataVersion: '20260101T000000Z-aaaaaaaa',
    });
    await publishEnvelope({ store, envelope: env });
    await assert.rejects(() => publishEnvelope({ store, envelope: env }), /같은/);
  });
});

test('발행: 깨진 포인터를 «최초 발행» 으로 오인하지 않는다', async () => {
  await withStore(async (store) => {
    await store.put('public-data/store/current.json', 'not json at all', 'application/json');
    const env = buildEnvelope({
      dataset: 'store', source: 'backend', origin: 'x', data: okEnvelope().data,
      coverage: okEnvelope().coverage, collectionStatus: okEnvelope().collectionStatus,
    });
    // 🔴 덮어쓰면 정상본을 잃는다. 「없다」와 「못 읽는다」는 다른 사실이다.
    await assert.rejects(() => publishEnvelope({ store, envelope: env }), /JSON/);
  });
});

test('구세대 정리: 현재 세대는 절대 안 지운다', async () => {
  await withStore(async (store) => {
    const versions = [];
    for (let i = 1; i <= 8; i += 1) {
      const v = `202601${String(i).padStart(2, '0')}T000000Z-aaaaaaaa`;
      versions.push(v);
      await store.put(`public-data/store/v/${v}.json`, '{}', 'application/json');
    }
    const current = versions[0]; // 가장 **오래된** 것을 현재로 둔다(최악의 경우)
    await pruneOldVersions(store, 'store', current, () => {}, 3);
    const left = await store.listPaths('public-data/store/v/');
    assert.ok(left.includes(`public-data/store/v/${current}.json`), '현재 세대가 지워졌다');
    assert.ok(left.length <= 4, `남은 세대가 ${left.length}개다`);
  });
});

test('발행: 세대 파일이 실제로 판독 가능한 봉투다 (되읽기 검증)', async () => {
  await withStore(async (store) => {
    const env = buildEnvelope({
      dataset: 'store', source: 'backend', origin: 'x', data: okEnvelope().data,
      coverage: okEnvelope().coverage, collectionStatus: okEnvelope().collectionStatus,
    });
    const r = await publishEnvelope({ store, envelope: env });
    const raw = await store.getText(`public-data/store/v/${r.dataVersion}.json`);
    const parsed = JSON.parse(raw);
    assert.equal(validateEnvelope(parsed, { dataset: 'store', dataVersion: r.dataVersion }).ok, true);
  });
});

// ===========================================================================
// 4. 번들 시드 — 배포되는 파일 자체를 시험한다
// ===========================================================================

for (const ds of ['fan', 'store', 'console-sample']) {
  test(`번들 시드 ${ds}.json 이 계약을 지킨다`, async () => {
    const raw = JSON.parse(await readFile(join(HERE, '..', 'snapshots', `${ds}.json`), 'utf8'));
    const r = validateEnvelope(raw, { dataset: ds });
    assert.equal(r.ok, true, r.ok ? '' : r.reason);
    // 🔴 비어 있지 않아야 한다 — 빈 시드는 «화면이 선다» 를 만족시키지 못한다.
    const counts = Object.values(raw.coverage);
    assert.ok(counts.length > 0 && counts.every((n) => n > 0), `${ds} 시드의 coverage 에 0 이 있다`);
  });
}

test('번들 시드에 픽스처의 음성 대조군 값이 하나도 없다', async () => {
  const fan = await readFile(join(HERE, '..', 'snapshots', 'fan.json'), 'utf8');
  const store = await readFile(join(HERE, '..', 'snapshots', 'store.json'), 'utf8');
  const all = fan + store;
  const banned = [
    ...RAW_ARTISTS.map((a) => a.realName).filter(Boolean),
    ...RAW_POSTS.filter((p) => p.visibility !== 'PUBLIC' || p.status !== 'PUBLISHED').map((p) => p.body).filter(Boolean),
    ...RAW_PRODUCTS.filter((p) => p.status === 'HIDDEN').map((p) => p.name),
  ];
  assert.ok(banned.length >= 5, `금지 목록이 ${banned.length}건뿐 — 이 시험이 공허하다`);
  for (const needle of banned) assert.ok(!all.includes(needle), `시드에 '${needle.slice(0, 40)}' 가 있다`);
  assert.ok(!all.includes('"stock"'));
});
