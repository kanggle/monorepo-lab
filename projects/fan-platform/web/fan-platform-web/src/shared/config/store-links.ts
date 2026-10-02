/**
 * 굿즈샵(이커머스 스토어)으로 나가는 링크 — `ADR-MONO-077` 갈래 D · `TASK-MONO-739`.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 팬 코드가 아는 스토어의 사실은 **셋뿐**이다
 * ─────────────────────────────────────────────────────────────────────────
 *   ① 스토어 주소 — `NEXT_PUBLIC_STORE_URL`, 미설정이면 정본 공개 호스트(D3).
 *   ② 카테고리 id **하나** — «아티스트 굿즈». 상품 id 는 코드에 적지 않는다(저장본에서 읽는다, D5 갈래 D).
 *   ③ 스토어 목록 URL 의 쿼리 이름 `categoryId` · `q` — `web-store/src/app/(store)/products/page.tsx`
 *      가 «이름·의미 고정» 을 선언한 그대로 쓴다.
 *
 * 🔴 이 세 사실이 스토어 저장본과 **같이** 맞는지는 `__tests__/store-links.test.ts` 가 판다 —
 *    여기서 만든 URL 을 스토어의 `queryProducts` 로 풀어서 «굿즈만, 1건 이상» 을 단언한다(AC-2).
 *    한쪽만 바뀌면(카테고리 id 가 바뀌거나, 예명이 바뀌거나) 링크가 **0건 목록**으로 조용히
 *    떨어지는 부류이고, 그 시험이 그 부류를 문다.
 *
 * 🔵 기본값이 운영 주소인 이유(D3): 이 값을 안 넘긴 환경에서 **조용히 죽는 쪽이 아니라 운영
 *    스토어로 가는 쪽**으로 실패하게 한다. `.local`/`sslip.io` 는 기본값이 될 수 없다 —
 *    `check-client-graph-backend-origins.mjs` 가 막는 부류이고 방문자 브라우저에서 안 열린다.
 * 🔵 순수 모듈이다(`server-only` 없음, 비공개 env 없음) — 서버 컴포넌트가 쓰지만 클라이언트가
 *    임포트해도 새는 것이 없다. `NEXT_PUBLIC_` 값만 읽는다(`public-env.ts` 와 같은 규율).
 * 🔵 링크는 **같은 탭**으로 연다(R3) — 이 모듈은 URL 만 만들고 `target` 은 화면이 정한다(안 붙인다).
 */

/** 정본 공개 호스트 — `TEMPLATE.md` § PUBLIC-HOSTNAMES 의 web-store 행. */
export const DEFAULT_STORE_URL = 'https://store.hubwang.com';

/**
 * 스토어의 «아티스트 굿즈» 카테고리 id — 팬이 아는 **유일한** 스토어 식별자(D5 갈래 D).
 * 시드: `product-service` `V21__seed_artist_goods.sql` · h2 `V14__…` · 공개 픽스처 `CATEGORY_NAMES`.
 */
export const STORE_GOODS_CATEGORY_ID = 'a0000000-0000-0000-0000-000000000008';

/**
 * 스토어 주소. 끝의 `/` 는 떼고, 비었거나 공백뿐이면 기본값.
 *
 * 🔴 `process.env.NEXT_PUBLIC_STORE_URL` 을 **이 모양 그대로** 읽는다 — Next 는 빌드 때 이 표현식을
 *    문자 그대로 찾아 값을 박는다. 구조 분해나 동적 키로 바꾸면 클라이언트 쪽에서 조용히 기본값이 된다.
 */
export function storeBaseUrl(raw: string | undefined = process.env.NEXT_PUBLIC_STORE_URL): string {
  const v = (raw ?? '').trim().replace(/\/+$/, '');
  return v === '' ? DEFAULT_STORE_URL : v;
}

/** 헤더 「굿즈샵 ↗」 — 스토어의 «아티스트 굿즈» 전체 목록. */
export function storeGoodsHref(base: string = storeBaseUrl()): string {
  const params = new URLSearchParams({ categoryId: STORE_GOODS_CATEGORY_ID });
  return `${base}/products?${params.toString()}`;
}

/**
 * 아티스트 페이지 「전체 보기」 — 그 아티스트의 굿즈 목록(D5: «B 와 같은 목록»).
 *
 * 🔴 스토어 목록에는 컬렉션 필터가 **없다**(쿼리 이름 고정 — 위 ③). 그래서 이 링크는 굿즈 카테고리 안에서
 *    예명을 검색한다. 카드 선택(`artistGoods`)은 `collectionRef`(id)로 하고, 이 링크가 **같은 집합**을
 *    돌려주는지는 AC-2 시험이 대조한다 — 예명이 바뀌거나 남의 굿즈 이름에 섞이면 거기서 빨개진다.
 */
export function storeArtistGoodsHref(stageName: string, base: string = storeBaseUrl()): string {
  const params = new URLSearchParams({ categoryId: STORE_GOODS_CATEGORY_ID, q: stageName });
  return `${base}/products?${params.toString()}`;
}

/** 굿즈 카드 → 스토어 상품 상세. */
export function storeProductHref(productId: string, base: string = storeBaseUrl()): string {
  return `${base}/products/${encodeURIComponent(productId)}`;
}
