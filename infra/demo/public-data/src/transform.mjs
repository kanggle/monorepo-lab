// DEMO-PUBLIC-DATA: 백엔드 응답 → 공개 DTO. **허용 목록이 실제로 집행되는 자리.**
//
// =============================================================================
// 왜 이 변환이 별도 모듈인가
// =============================================================================
// 이 저장소는 백엔드를 이 세션에서 띄울 수 없다. 그래서 발행 경로를 «HTTP 배관» 과
// «필드 선별» 로 가른다:
//
//   · HTTP 배관(`bin/publish-public-data.mjs`)  — 실측 없이는 검증 불가. 문서 + 가드로 만다.
//   · 필드 선별(이 파일)                        — **순수 함수**다. 픽스처로 전수 시험한다.
//
// 🔴🔴 보안 성질을 들고 있는 쪽은 **이 파일**이다. 개인정보·회원 전용 본문·재고가 공개
//    JSON 에 실리느냐는 여기서 정해지고, 여기는 네트워크 없이 시험할 수 있다. 그러니
//    「백엔드를 못 띄워서 검증 못 했다」가 이 파일에는 **해당되지 않는다** — 해당된다고
//    보고하면 그것이 거짓이다.
//
// -----------------------------------------------------------------------------
// 🔴 규율: **원본을 spread 하지 않는다**
// -----------------------------------------------------------------------------
// `{...raw, ...}` 한 줄이면 이 파일 전체가 무효가 된다. 백엔드에 필드가 하나 늘어나는 날
// 그것이 자동으로 공개되고, 아무 가드도 안 문다. 아래 함수는 전부 **새 객체를 손으로
// 조립**한다. `verify-demo-wrapper.sh` 의 가드가 이 파일에서 spread 를 금지한다.
// =============================================================================

/** @param {unknown} v @param {string} fallback @returns {string} */
function str(v, fallback = '') {
  return typeof v === 'string' ? v : fallback;
}

/** @param {unknown} v @returns {string | null} */
function strOrNull(v) {
  return typeof v === 'string' && v !== '' ? v : null;
}

/** @param {unknown} v @returns {number} */
function num(v) {
  return typeof v === 'number' && Number.isFinite(v) ? v : 0;
}

/**
 * 검색용 결합 문자열.
 * 🔵 **공개 필드만** 넘겨라. 「검색이 잘 되니까」라는 이유로 비공개 값을 섞으면 그 값이
 *    공개 JSON 에 실린다 — 이 파일이 막는 실패의 가장 흔한 모양이다.
 * @param {Array<string | null | undefined>} parts
 */
function searchText(parts) {
  return parts.filter((p) => typeof p === 'string' && p !== '').join(' ').toLowerCase();
}

// ---------------------------------------------------------------------------
// fan
// ---------------------------------------------------------------------------

/**
 * 아티스트 하나.
 *
 * 🔴 `realName` 을 **읽지도 않는다.** 백엔드 `Artist` 에는 있다. 읽어서 버리는 것과 안 읽는
 *    것의 차이는, 다음 사람이 이 함수를 고칠 때 그 변수가 손에 잡히느냐다.
 *
 * @param {Record<string, unknown>} raw
 * @returns {Record<string, unknown> | null} 공개할 수 없는 상태면 null
 */
export function toPublicArtist(raw) {
  const id = strOrNull(raw.id ?? raw.artistId);
  const stageName = strOrNull(raw.stageName);
  if (id === null || stageName === null) return null;
  // 🔴 공개 상태가 아닌 아티스트는 공개하지 않는다. `status` 를 모르면(필드 부재) 공개하되,
  //    아는데 공개 상태가 아니면 거른다 — «모르는 것» 과 «아니라고 아는 것» 은 다르다.
  //
  // 🔴🔴 값은 **`PUBLISHED`** 다. 초판은 `ACTIVE` 로 적었고 **틀렸다** — 실측이 뒤집었다:
  //    `infra/demo/seed/seed-fan.sh:110` 의 INSERT 가 `artists.status` 에 `'PUBLISHED'` 를
  //    넣는다(`artist_groups.status` 만 `'ACTIVE'` 다 — 두 테이블이 다른 어휘를 쓴다).
  //    `ACTIVE` 로 뒀으면 이 발행자는 **시드된 아티스트 3명을 전부 걸러** 빈 목록을 내고,
  //    그 빈 목록은 «아직 데이터가 없습니다» 로 보인다 — 수집 실패와 구별되지 않는 모양이다.
  if (typeof raw.status === 'string' && raw.status !== 'PUBLISHED') return null;
  const artistType = raw.artistType === 'GROUP_MEMBER' ? 'GROUP_MEMBER' : 'SOLO';
  const agency = strOrNull(raw.agency);
  const bio = strOrNull(raw.bio);
  return {
    id,
    stageName,
    artistType,
    agency,
    debutDate: strOrNull(raw.debutDate),
    bio,
    // 🔴 원본 참조는 그대로 쓰지 않는다 — 발행자가 영속 저장소로 복사한 뒤 그 주소로
    //    바꿔 넣는다(`rewriteImages`). 여기서는 **원본 참조**를 담고, 복사 단계가 이 값을
    //    소비한다. 복사에 실패하면 그 단계가 null 로 만든다(EC2 주소가 새 나가지 않게).
    profileImageUrl: strOrNull(raw.profileImageRef),
    searchText: searchText([stageName, agency, bio]),
  };
}

/**
 * 게시물 하나.
 *
 * 🔴🔴 **회원 전용 본문은 여기서 사라진다.** `visibility !== 'PUBLIC'` 이면 `body` 는 null,
 *    `imageUrls` 는 빈 배열이다 — 이미지 **경로도 본문**이기 때문이다(파일명이 제목을
 *    담거나, 순번으로 개수를 알 수 있다).
 * 🔴 `bodyPreview` 는 **읽지 않는다.** 백엔드가 그것을 어떻게 리댁션하는지 이 저장소의
 *    공개 저장본이 의존해서는 안 된다(확인되지 않은 리댁션을 신뢰하는 쪽이 되돌릴 수 없다).
 *
 * @param {Record<string, unknown>} raw
 * @param {Map<string, string>} artistNames  artistId → stageName
 * @returns {Record<string, unknown> | null}
 */
export function toPublicPost(raw, artistNames) {
  const id = strOrNull(raw.id ?? raw.postId);
  const artistId = strOrNull(raw.artistId ?? raw.artistAccountId);
  if (id === null || artistId === null) return null;
  // 🔴 게시 상태가 아닌 글(삭제·미승인·초안)은 **공개 목록에 존재하지 않는다.**
  if (typeof raw.status === 'string' && raw.status !== 'PUBLISHED') return null;
  // 🔴 목록에 없는 아티스트의 글은 버린다 — 아티스트가 걸러진 이유(비활성)가 그 글에도
  //    적용되어야 하고, 이름을 못 붙이면 화면이 «알 수 없는 아티스트» 를 그린다.
  const artistStageName = artistNames.get(artistId);
  if (artistStageName === undefined) return null;

  const visibility =
    raw.visibility === 'MEMBERS_ONLY' || raw.visibility === 'PREMIUM' ? raw.visibility : 'PUBLIC';
  const locked = visibility !== 'PUBLIC';
  const rawImages = Array.isArray(raw.imageRefs) ? raw.imageRefs : Array.isArray(raw.images) ? raw.images : [];

  return {
    id,
    artistId,
    artistStageName,
    title: str(raw.title, '(제목 없음)'),
    body: locked ? null : strOrNull(raw.body),
    visibility,
    locked,
    publishedAt: str(raw.publishedAt ?? raw.createdAt, ''),
    imageUrls: locked
      ? []
      : rawImages
          .map((r) => (typeof r === 'string' ? r : strOrNull(/** @type {Record<string, unknown>} */ (r ?? {}).url)))
          .filter((/** @type {string | null} */ u) => u !== null),
  };
}

// ---------------------------------------------------------------------------
// store
// ---------------------------------------------------------------------------

/**
 * 상품 하나.
 *
 * 🔴🔴 **`stock` 을 읽지 않는다.** 백엔드 `ProductVariant.stock` 은 실시간 값이고, 저장본에
 *    실린 순간 그것은 «과거의 수» 다. 필드를 안 만들면 화면이 그것을 실시간처럼 보여줄 수
 *    없다 — 문구는 지워지고 필드는 안 지워진다.
 * 🔴 `HIDDEN` 은 공개 목록에 없다.
 *
 * @param {Record<string, unknown>} raw  상세(`ProductDetail`) 또는 요약(`ProductSummary`)
 * @returns {Record<string, unknown> | null}
 */
export function toPublicProduct(raw) {
  const id = strOrNull(raw.id ?? raw.productId);
  const name = strOrNull(raw.name);
  if (id === null || name === null) return null;
  const status = raw.status === 'SOLD_OUT' ? 'SOLD_OUT' : raw.status === 'ON_SALE' ? 'ON_SALE' : null;
  // 🔴 `HIDDEN` 이거나 모르는 상태면 **공개하지 않는다.** 모르는 것은 통과가 아니다.
  if (status === null) return null;

  const description = strOrNull(raw.description);
  const categoryId = str(raw.categoryId, 'uncategorized');
  const rawImages = Array.isArray(raw.images) ? raw.images : [];
  const images = rawImages
    .map((i) => {
      const img = /** @type {Record<string, unknown>} */ (i ?? {});
      const url = strOrNull(img.url);
      return url === null ? null : { url, sortOrder: num(img.sortOrder), isPrimary: img.isPrimary === true };
    })
    .filter((/** @type {unknown} */ i) => i !== null);

  const rawVariants = Array.isArray(raw.variants) ? raw.variants : [];
  const options = rawVariants
    .map((v) => {
      const variant = /** @type {Record<string, unknown>} */ (v ?? {});
      const optionName = strOrNull(variant.optionName);
      if (optionName === null) return null;
      // 🔵 `variant.stock` 은 여기서 **손에 잡히지도 않는다** — 아래 객체에 자리가 없다.
      return { id: str(variant.id, `${id}:${optionName}`), optionName, additionalPrice: num(variant.additionalPrice) };
    })
    .filter((/** @type {unknown} */ o) => o !== null);

  return {
    id,
    name,
    description,
    status,
    price: num(raw.price),
    thumbnailUrl: strOrNull(raw.thumbnailUrl),
    images,
    categoryId,
    options,
    searchText: searchText([name, description, categoryId, ...options.map((o) => /** @type {any} */ (o).optionName)]),
    createdAt: str(raw.createdAt, ''),
  };
}

/**
 * 카테고리 목록을 **상품에서 파생**한다.
 *
 * 🔴 백엔드에 카테고리 조회 API 가 **없다**(실측: `packages/api-client/src/services/` 에
 *    category 서비스가 없고 `packages/types/src/` 에 카테고리 엔티티 타입이 없다 —
 *    `categoryId` 는 문자열 파라미터이고 목록은 검색 패싯으로만 나온다).
 *    그러므로 카테고리를 «조회해서 저장» 할 수 없다. 파생이 유일한 정직한 방법이고,
 *    그 사실을 여기 적어 둔다 — 다음 사람이 «왜 카테고리는 안 뽑았나» 를 다시 묻지 않도록.
 * 🔵 이름은 id 를 사람이 읽을 수 있게 다듬은 것이다. 백엔드에 표시명이 생기면 그때 그것을
 *    쓰되, 지금 없는 것을 있는 척하지 않는다.
 *
 * @param {Array<Record<string, unknown>>} products
 * @returns {Array<Record<string, unknown>>}
 */
export function deriveCategories(products) {
  /** @type {Map<string, number>} */
  const counts = new Map();
  for (const p of products) {
    const c = str(p.categoryId, 'uncategorized');
    counts.set(c, (counts.get(c) ?? 0) + 1);
  }
  return [...counts.entries()]
    .map(([id, productCount]) => ({ id, name: humanizeCategoryId(id), productCount }))
    .sort((a, b) => b.productCount - a.productCount || a.id.localeCompare(b.id));
}

/** @param {string} id @returns {string} */
export function humanizeCategoryId(id) {
  if (id === 'uncategorized') return '미분류';
  return id
    .replace(/[-_]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

// ---------------------------------------------------------------------------
// 봉투 조립
// ---------------------------------------------------------------------------

/**
 * 컬렉션 하나의 수집 결과를 판정한다.
 *
 * 🔴🔴 **정상적인 0건과 수집 실패를 가르는 지점이 여기다.** 인자가 두 개인 이유가 그것이다:
 *    `fetched === false` 는 «못 물어봤다», `rows.length === 0` 은 «물어봤더니 없더라».
 *    한 인자로 합치면 그 순간 둘이 같은 모양이 되고, 그 뒤로는 영영 구별되지 않는다.
 *
 * @param {boolean} fetched  수집 자체가 성공했는가(HTTP 2xx + 파싱 성공)
 * @param {Array<unknown>} rows
 * @returns {'ok' | 'empty' | 'failed'}
 */
export function collectionStatusOf(fetched, rows) {
  if (!fetched) return 'failed';
  return rows.length === 0 ? 'empty' : 'ok';
}
