// DEMO-PUBLIC-DATA: 봉투·포인터 계약의 **런타임**. 판독자(TS)와 발행자(CLI)가 이 파일 하나를 공유한다.
//
// =============================================================================
// 왜 이 파일만 `.mjs` 인가 — 그리고 왜 사본을 만들지 않았는가
// =============================================================================
// 이 계약에는 소비자가 **둘**이고 둘의 실행 환경이 다르다:
//
//   · 판독자 — 세 Next 앱의 서버 런타임. TypeScript 로 쓰고 번들러가 컴파일한다.
//   · 발행자 — `node bin/publish-public-data.mjs`. **빌드 단계도 node_modules 도 없이**
//              돌아야 한다. 그 스크립트가 도는 자리는 개발자 셸과 데모 호스트이고,
//              둘 다 «먼저 설치하라» 를 요구하면 «갱신» 이라는 동작 자체가 안 일어난다.
//
// 두 벌로 쓰면 **한쪽만 고쳐진다** — 이 저장소가 이름 붙인 함정이고, 여기서 그것이 뜻하는
// 바는 특히 나쁘다: 발행자의 허용 목록과 판독자의 검증이 갈라지면, 발행자가 실은 개인정보를
// 실어 보내는데 판독자는 «계약대로» 라고 읽는다.
//
// ⇒ **런타임은 한 벌, 타입은 옆 파일**(`contract.d.mts`). TS 쪽은 이 파일을 임포트하고
//   타입만 그 선언에서 받는다. 사본이 없으므로 갈라질 자리가 없다.
//
// 🔴 여기에 `@vercel/blob` 을 임포트하지 마라. 이 파일은 세 Next 앱의 서버 번들에 들어간다.
// =============================================================================

/**
 * 봉투 계약의 판.
 *
 * 🔴 **올릴 때는 판독자를 함께 고쳐라.** 판독자는 모르는 판을 «못 읽음» 으로 떨어뜨리고
 * 번들 시드로 폴백한다 — 조용히 빈 목록을 그리지 않는다. 그 폴백이 옳지만, 그 상태는
 * 「Vercel 저장본이 기본 출처」라는 성질을 잃은 상태이기도 하다.
 */
export const PUBLIC_DATA_SCHEMA_VERSION = 1;

/** 데이터셋 하나 = Blob 접두사 하나 = 포인터 하나. */
export const PUBLIC_DATASETS = ['fan', 'store', 'console-sample'];

/** 봉투가 말하는 출처. 🔴 `bundled` 는 결함이 아니라 **선언된 상태**다. */
export const PUBLIC_DATA_SOURCES = ['backend', 'bundled', 'authored'];

/** 컬렉션 하나의 수집 결과. 🔴 `empty` 와 `failed` 는 **다른 사실**이다. */
export const COLLECTION_STATUSES = ['ok', 'empty', 'failed'];

// ---------------------------------------------------------------------------
// Blob 안의 경로 — 발행자와 판독자가 **같은 함수**로 만든다
// ---------------------------------------------------------------------------

/** @param {string} dataset @returns {string} */
export function pointerPath(dataset) {
  return `public-data/${dataset}/current.json`;
}

/** @param {string} dataset @param {string} dataVersion @returns {string} */
export function versionPath(dataset, dataVersion) {
  return `public-data/${dataset}/v/${dataVersion}.json`;
}

/**
 * 이미지 경로는 **내용 주소**다 — `public-data/img/<sha256>.<ext>`.
 *
 * 🔴 요구사항 두 개를 한 결정으로 만족시킨다: 「동일 이미지 중복 업로드를 줄이고」와
 *    「갱신으로 저장량이 무제한 증가하지 않게」. 같은 바이트는 같은 경로가 되므로 재발행이
 *    저장량을 늘리지 않고, 세대가 바뀌어도 이미지는 공유된다.
 * 🔵 그래서 이미지는 **세대에 안 묶인다.** 구세대 정리가 이미지를 지우지 않는 이유이기도
 *    하다 — 지우려면 「어느 세대도 안 가리키는가」를 세어야 한다(§ retention, README).
 *
 * @param {string} sha256 @param {string} ext @returns {string}
 */
export function imagePath(sha256, ext) {
  const safeExt = String(ext).replace(/[^a-z0-9]/gi, '').toLowerCase() || 'bin';
  return `public-data/img/${sha256}.${safeExt}`;
}

/**
 * 세대 식별자 — **사전순 = 시간순**이 되도록 `YYYYMMDDTHHMMSSZ-<hex>`.
 *
 * 🔴 이 성질에 동시 발행의 안전성이 걸려 있다: 포인터 교체는 «내 판이 지금 포인터보다
 *    사전순으로 뒤인가» 로 판정한다. 모양을 바꾸면 그 비교가 조용히 틀린 답을 낸다.
 *
 * @param {Date} now @param {string} randomHex @returns {string}
 */
export function makeDataVersion(now, randomHex) {
  const iso = now.toISOString().replace(/[-:]/g, '').replace(/\.\d+Z$/, 'Z');
  return `${iso}-${randomHex}`;
}

// ---------------------------------------------------------------------------
// 검증 — «모양을 믿지 않는다»
// ---------------------------------------------------------------------------

/**
 * 봉투의 **모양**을 본다. 내용물(`data`)은 `validateDatasetData` 가 본다.
 *
 * 🔴 Blob 이 404 HTML 을 주거나, 잘린 JSON 이 오거나, 다른 데이터셋의 봉투가 오는 일은
 *    전부 **200 으로** 온다. 통과시키면 화면은 «데이터가 없다» 를 그리고, 그 화면은 수집
 *    실패와 구별되지 않는다.
 *
 * @param {unknown} value
 * @param {{dataset: string, dataVersion?: string}} expected
 * @returns {{ok: true} | {ok: false, reason: string}}
 */
export function validateEnvelopeShape(value, expected) {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    return { ok: false, reason: 'JSON 객체가 아닙니다' };
  }
  const e = /** @type {Record<string, unknown>} */ (value);
  if (e.schemaVersion !== PUBLIC_DATA_SCHEMA_VERSION) {
    return { ok: false, reason: `schemaVersion 이 ${String(e.schemaVersion)} 입니다(기대 ${PUBLIC_DATA_SCHEMA_VERSION})` };
  }
  if (e.dataset !== expected.dataset) {
    return { ok: false, reason: `dataset 이 '${String(e.dataset)}' 입니다(기대 '${expected.dataset}')` };
  }
  if (typeof e.dataVersion !== 'string' || e.dataVersion === '') {
    return { ok: false, reason: 'dataVersion 이 없습니다' };
  }
  // 🔴 포인터가 말한 세대와 봉투가 말하는 세대가 다르면 **읽지 않는다.** 그 불일치는
  //    「덮어쓰기가 일어났다」는 뜻이고, 그때 목록/상세 정합은 이미 깨져 있다.
  if (expected.dataVersion !== undefined && e.dataVersion !== expected.dataVersion) {
    return { ok: false, reason: `포인터는 '${expected.dataVersion}' 를 가리키는데 봉투는 '${String(e.dataVersion)}' 입니다` };
  }
  if (typeof e.generatedAt !== 'string' || e.generatedAt === '') return { ok: false, reason: 'generatedAt 이 없습니다' };
  if (!PUBLIC_DATA_SOURCES.includes(/** @type {string} */ (e.source))) {
    return { ok: false, reason: `source 가 모르는 값입니다: '${String(e.source)}'` };
  }
  if (typeof e.origin !== 'string' || e.origin === '') return { ok: false, reason: 'origin 이 없습니다' };
  if (typeof e.coverage !== 'object' || e.coverage === null) return { ok: false, reason: 'coverage 가 없습니다' };
  if (typeof e.collectionStatus !== 'object' || e.collectionStatus === null) {
    return { ok: false, reason: 'collectionStatus 가 없습니다' };
  }
  // 🔴🔴 `failed` 가 든 봉투는 애초에 발행되지 않아야 하지만, 판독자도 그것을 정상으로 읽지
  //    않는다. 가드가 한 겹이면 그 한 겹이 빠진 날 조용히 통과한다.
  for (const [k, v] of Object.entries(e.collectionStatus)) {
    if (!COLLECTION_STATUSES.includes(/** @type {string} */ (v))) {
      return { ok: false, reason: `collectionStatus['${k}'] 가 모르는 값입니다: '${String(v)}'` };
    }
    if (v === 'failed') return { ok: false, reason: `collectionStatus['${k}'] 가 failed 입니다 — 부분 수집본입니다` };
  }
  // 🔴 coverage 와 collectionStatus 의 **키 집합이 같아야** 한다. 한쪽에만 있는 키는
  //    「센 것」과 「상태를 아는 것」이 어긋났다는 뜻이고, 그 어긋남은 0건 판정을 망가뜨린다.
  const covKeys = Object.keys(e.coverage).sort().join(',');
  const staKeys = Object.keys(e.collectionStatus).sort().join(',');
  if (covKeys !== staKeys) {
    return { ok: false, reason: `coverage 키(${covKeys || '없음'})와 collectionStatus 키(${staKeys || '없음'})가 다릅니다` };
  }
  if (typeof e.data !== 'object' || e.data === null) return { ok: false, reason: 'data 가 없습니다' };
  return { ok: true };
}

/**
 * 포인터의 모양 검사. 통과 못 하면 판독자는 **번들 시드로 간다**(백엔드로 가지 않는다).
 *
 * @param {unknown} value
 * @param {{dataset: string}} expected
 * @returns {{ok: true, pointer: any} | {ok: false, reason: string}}
 */
export function validatePointerShape(value, expected) {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    return { ok: false, reason: 'JSON 객체가 아닙니다' };
  }
  const p = /** @type {Record<string, unknown>} */ (value);
  if (p.schemaVersion !== PUBLIC_DATA_SCHEMA_VERSION) {
    return { ok: false, reason: `schemaVersion 이 ${String(p.schemaVersion)} 입니다` };
  }
  if (p.dataset !== expected.dataset) return { ok: false, reason: `dataset 이 '${String(p.dataset)}' 입니다` };
  if (typeof p.dataVersion !== 'string' || p.dataVersion === '') return { ok: false, reason: 'dataVersion 이 없습니다' };
  if (typeof p.url !== 'string' || !/^https:\/\//.test(p.url)) {
    return { ok: false, reason: `url 이 https 절대주소가 아닙니다: '${String(p.url)}'` };
  }
  if (typeof p.publishedAt !== 'string' || p.publishedAt === '') return { ok: false, reason: 'publishedAt 이 없습니다' };
  if (!PUBLIC_DATA_SOURCES.includes(/** @type {string} */ (p.source))) {
    return { ok: false, reason: `source 가 모르는 값입니다: '${String(p.source)}'` };
  }
  return { ok: true, pointer: p };
}

/** @param {unknown} v @returns {boolean} */
function isArrayOfObjects(v) {
  return Array.isArray(v) && v.every((x) => typeof x === 'object' && x !== null && !Array.isArray(x));
}

/**
 * 내용물의 **최소 모양** + **되돌릴 수 없는 누출**을 본다.
 *
 * 🔴 «필드를 전수 검사» 하지 않는다 — 그것은 타입 시스템의 일이다. 런타임에 필요한 것은
 *    «이 JSON 이 이 데이터셋의 것인가» 뿐이다. 다만 회원 전용 본문·개인정보·재고 누출만은
 *    런타임에도 막는다: 그것은 모양 문제가 아니라 **되돌릴 수 없는 사실**이라서 한 겹으로
 *    두지 않는다. 발행자도 같은 함수를 부르고, 판독자도 부른다.
 *
 * @param {string} dataset
 * @param {unknown} data
 * @returns {{ok: true} | {ok: false, reason: string}}
 */
export function validateDatasetData(dataset, data) {
  if (typeof data !== 'object' || data === null || Array.isArray(data)) {
    return { ok: false, reason: 'data 가 객체가 아닙니다' };
  }
  const d = /** @type {Record<string, unknown>} */ (data);

  if (dataset === 'fan') {
    if (!isArrayOfObjects(d.artists)) return { ok: false, reason: 'fan.artists 가 객체 배열이 아닙니다' };
    if (!isArrayOfObjects(d.posts)) return { ok: false, reason: 'fan.posts 가 객체 배열이 아닙니다' };
    if (!isArrayOfObjects(d.membershipPlans)) return { ok: false, reason: 'fan.membershipPlans 가 객체 배열이 아닙니다' };
    for (const p of /** @type {Array<Record<string, unknown>>} */ (d.posts)) {
      // 🔴🔴 잠긴 글에 본문·이미지가 실려 오면 봉투를 **통째로 거부한다.**
      if (p.locked === true && (p.body !== null || (Array.isArray(p.imageUrls) && p.imageUrls.length > 0))) {
        return { ok: false, reason: `잠긴 게시물 '${String(p.id)}' 에 본문 또는 이미지가 실려 있습니다` };
      }
      if (p.visibility !== 'PUBLIC' && p.body !== null) {
        return { ok: false, reason: `비공개 게시물 '${String(p.id)}' 에 본문이 실려 있습니다` };
      }
      if (p.visibility === 'PUBLIC' && p.locked !== false) {
        return { ok: false, reason: `공개 게시물 '${String(p.id)}' 의 locked 가 false 가 아닙니다` };
      }
      // 🔴 `bodyPreview` 는 **공개 계약에 없는 필드**다. 백엔드 `FeedItem` 에는 있고, 그것이
      //    무엇을 담는지는 community-service 의 리댁션 규칙에 달려 있다. 확인되지 않은
      //    리댁션을 신뢰하는 것과 아무것도 안 싣는 것 중 되돌릴 수 없는 쪽은 전자다.
      if ('bodyPreview' in p) {
        return { ok: false, reason: `게시물 '${String(p.id)}' 에 bodyPreview 가 있습니다 — 공개 계약에 없는 필드입니다` };
      }
    }
    for (const a of /** @type {Array<Record<string, unknown>>} */ (d.artists)) {
      for (const banned of ['realName', 'accountId', 'tenantId', 'email']) {
        if (banned in a) return { ok: false, reason: `아티스트 '${String(a.id)}' 에 ${banned} 이(가) 실려 있습니다` };
      }
    }
    return { ok: true };
  }

  if (dataset === 'store') {
    if (!isArrayOfObjects(d.products)) return { ok: false, reason: 'store.products 가 객체 배열이 아닙니다' };
    if (!isArrayOfObjects(d.categories)) return { ok: false, reason: 'store.categories 가 객체 배열이 아닙니다' };
    for (const p of /** @type {Array<Record<string, unknown>>} */ (d.products)) {
      if (p.status !== 'ON_SALE' && p.status !== 'SOLD_OUT') {
        return { ok: false, reason: `상품 '${String(p.id)}' 의 status 가 '${String(p.status)}' 입니다(공개 계약은 두 값뿐)` };
      }
      if ('stock' in p) return { ok: false, reason: `상품 '${String(p.id)}' 에 stock 이 실려 있습니다` };
      if (Array.isArray(p.options)) {
        for (const o of /** @type {Array<Record<string, unknown>>} */ (p.options)) {
          // 🔴🔴 「표시만 안 하면 된다」가 아니다 — 공개 JSON 에 있는 값은 **이미 공개된 값**
          //    이고, 그것을 실시간으로 오인하는 것은 다음 소비자다.
          if ('stock' in o) return { ok: false, reason: `상품 '${String(p.id)}' 의 옵션에 stock 이 실려 있습니다` };
        }
      }
    }
    return { ok: true };
  }

  if (dataset === 'console-sample') {
    if (!isArrayOfObjects(d.domains)) return { ok: false, reason: 'console-sample.domains 가 객체 배열이 아닙니다' };
    return { ok: true };
  }

  return { ok: false, reason: `모르는 데이터셋: '${String(dataset)}'` };
}

/**
 * 봉투 + 내용물을 한 번에. 발행자가 «올리기 전에» 부르고, 판독자가 «쓰기 전에» 부른다.
 *
 * @param {unknown} value
 * @param {{dataset: string, dataVersion?: string}} expected
 * @returns {{ok: true} | {ok: false, reason: string}}
 */
export function validateEnvelope(value, expected) {
  const shape = validateEnvelopeShape(value, expected);
  if (!shape.ok) return shape;
  return validateDatasetData(expected.dataset, /** @type {any} */ (value).data);
}
