import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { BASE_URL, DEFAULT_HEADERS, DEFAULT_THRESHOLDS } from '../lib/config.js';
import { randomChoice, randomInt } from '../lib/helpers.js';

/**
 * 상품 조회 및 검색 API 부하 테스트
 *
 * 대상 엔드포인트:
 * - GET /api/products (상품 목록 조회)
 * - GET /api/products/{productId} (상품 상세 조회)
 * - GET /api/search/products?q=... (상품 검색)
 */

const productListDuration = new Trend('product_list_duration', true);
const productDetailDuration = new Trend('product_detail_duration', true);
const searchDuration = new Trend('search_duration', true);
const searchErrors = new Rate('search_error_rate');

const SEARCH_KEYWORDS = ['신발', '셔츠', '가방', '모자', '바지', '자켓', '티셔츠', '코트', '운동화', '청바지'];
const SORT_OPTIONS = ['relevance', 'price_asc', 'price_desc', 'newest'];

export const options = {
  scenarios: {
    browse_smoke: {
      executor: 'constant-vus',
      vus: 10,
      duration: '30s',
      tags: { test_type: 'smoke' },
    },
    browse_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 30 },
        { duration: '1m', target: 80 },
        { duration: '30s', target: 80 },
        { duration: '30s', target: 0 },
      ],
      startTime: '35s',
      tags: { test_type: 'load' },
    },
    browse_stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 80 },
        { duration: '1m', target: 150 },
        { duration: '30s', target: 150 },
        { duration: '30s', target: 0 },
      ],
      startTime: '3m30s',
      tags: { test_type: 'stress' },
    },
  },
  thresholds: {
    ...DEFAULT_THRESHOLDS,
    product_list_duration: ['p(95)<400', 'p(99)<800'],
    product_detail_duration: ['p(95)<300', 'p(99)<600'],
    search_duration: ['p(95)<500', 'p(99)<1000'],
    search_error_rate: ['rate<0.01'],
    http_reqs: ['rate>50'],
  },
};

export default function () {
  // 1. 상품 목록 조회 (페이지네이션)
  const page = randomInt(0, 5);
  const listRes = http.get(
    `${BASE_URL}/api/products?page=${page}&size=20&status=ON_SALE`,
    { headers: DEFAULT_HEADERS, tags: { endpoint: 'product_list' } }
  );

  productListDuration.add(listRes.timings.duration);

  const listOk = check(listRes, {
    'product list: status 200': (r) => r.status === 200,
    'product list: has content': (r) => JSON.parse(r.body).content !== undefined,
  });

  if (!listOk) {
    searchErrors.add(1);
    sleep(1);
    return;
  }
  searchErrors.add(0);

  // 2. 상품 상세 조회 (목록에서 랜덤 선택)
  const products = JSON.parse(listRes.body).content;
  if (products && products.length > 0) {
    const product = randomChoice(products);
    const detailRes = http.get(
      `${BASE_URL}/api/products/${product.id}`,
      { headers: DEFAULT_HEADERS, tags: { endpoint: 'product_detail' } }
    );

    productDetailDuration.add(detailRes.timings.duration);

    check(detailRes, {
      'product detail: status 200': (r) => r.status === 200,
      'product detail: has variants': (r) => JSON.parse(r.body).variants !== undefined,
    });
  }

  sleep(randomInt(1, 3));

  // 3. 상품 검색
  const keyword = randomChoice(SEARCH_KEYWORDS);
  const sort = randomChoice(SORT_OPTIONS);
  const searchRes = http.get(
    `${BASE_URL}/api/search/products?q=${encodeURIComponent(keyword)}&sort=${sort}&page=0&size=20`,
    { headers: DEFAULT_HEADERS, tags: { endpoint: 'search' } }
  );

  searchDuration.add(searchRes.timings.duration);

  check(searchRes, {
    'search: status 200': (r) => r.status === 200,
    'search: has content': (r) => JSON.parse(r.body).content !== undefined,
  });

  sleep(randomInt(1, 3));

  // 4. 검색 결과에서 상세 조회
  if (searchRes.status === 200) {
    const searchResults = JSON.parse(searchRes.body).content;
    if (searchResults && searchResults.length > 0) {
      const item = randomChoice(searchResults);
      const detailRes2 = http.get(
        `${BASE_URL}/api/products/${item.productId}`,
        { headers: DEFAULT_HEADERS, tags: { endpoint: 'product_detail' } }
      );

      productDetailDuration.add(detailRes2.timings.duration);

      check(detailRes2, {
        'search→detail: status 200': (r) => r.status === 200,
      });
    }
  }

  sleep(randomInt(2, 5));
}
