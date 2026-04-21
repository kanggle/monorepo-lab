import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { BASE_URL, DEFAULT_HEADERS, DEFAULT_THRESHOLDS, authHeaders } from '../lib/config.js';
import { randomChoice, randomInt } from '../lib/helpers.js';

/**
 * E2E 사용자 흐름 부하 테스트
 *
 * 실제 사용자 시나리오 시뮬레이션:
 * 회원가입 → 로그인 → 상품 검색 → 상품 상세 조회 → 주문 생성 → 결제 조회
 */

const e2eDuration = new Trend('e2e_flow_duration', true);
const e2eErrors = new Rate('e2e_error_rate');

const SEARCH_KEYWORDS = ['신발', '셔츠', '가방', '모자', '바지'];

export const options = {
  scenarios: {
    e2e_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 10 },
        { duration: '2m', target: 20 },
        { duration: '1m', target: 20 },
        { duration: '1m', target: 0 },
      ],
      tags: { test_type: 'e2e' },
    },
  },
  thresholds: {
    ...DEFAULT_THRESHOLDS,
    e2e_flow_duration: ['p(95)<5000', 'p(99)<8000'],
    e2e_error_rate: ['rate<0.05'],
    http_reqs: ['rate>5'],
  },
};

export default function () {
  const startTime = Date.now();
  const uniqueId = `${__VU}-${__ITER}-${Date.now()}`;

  // Step 1: 회원가입
  const email = `e2e-${uniqueId}@test.com`;
  const password = 'E2ETest1234!';

  const signupRes = http.post(
    `${BASE_URL}/api/auth/signup`,
    JSON.stringify({ email, password, name: `E2EUser${uniqueId}` }),
    { headers: DEFAULT_HEADERS, tags: { step: 'signup' } }
  );

  if (!check(signupRes, { 'e2e signup: 201': (r) => r.status === 201 })) {
    e2eErrors.add(1);
    return;
  }

  sleep(1);

  // Step 2: 로그인
  const loginRes = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email, password }),
    { headers: DEFAULT_HEADERS, tags: { step: 'login' } }
  );

  if (!check(loginRes, { 'e2e login: 200': (r) => r.status === 200 })) {
    e2eErrors.add(1);
    return;
  }

  const { accessToken } = JSON.parse(loginRes.body);
  const headers = authHeaders(accessToken);

  sleep(randomInt(1, 3));

  // Step 3: 상품 검색
  const keyword = randomChoice(SEARCH_KEYWORDS);
  const searchRes = http.get(
    `${BASE_URL}/api/search/products?q=${encodeURIComponent(keyword)}&page=0&size=20`,
    { headers: DEFAULT_HEADERS, tags: { step: 'search' } }
  );

  check(searchRes, { 'e2e search: 200': (r) => r.status === 200 });

  sleep(randomInt(2, 4));

  // Step 4: 상품 목록 조회 (fallback: 검색 결과 없을 경우)
  const listRes = http.get(
    `${BASE_URL}/api/products?page=0&size=10&status=ON_SALE`,
    { headers: DEFAULT_HEADERS, tags: { step: 'product_list' } }
  );

  if (!check(listRes, { 'e2e product list: 200': (r) => r.status === 200 })) {
    e2eErrors.add(1);
    return;
  }

  const products = JSON.parse(listRes.body).content || [];
  if (products.length === 0) {
    e2eErrors.add(0);
    e2eDuration.add(Date.now() - startTime);
    return;
  }

  // Step 5: 상품 상세 조회
  const selectedProduct = randomChoice(products);
  const detailRes = http.get(
    `${BASE_URL}/api/products/${selectedProduct.id}`,
    { headers: DEFAULT_HEADERS, tags: { step: 'product_detail' } }
  );

  check(detailRes, { 'e2e product detail: 200': (r) => r.status === 200 });

  let variantId = null;
  if (detailRes.status === 200) {
    const detail = JSON.parse(detailRes.body);
    if (detail.variants && detail.variants.length > 0) {
      variantId = detail.variants[0].id;
    }
  }

  sleep(randomInt(2, 5));

  // Step 6: 주문 생성
  const orderPayload = {
    items: [
      {
        productId: selectedProduct.id,
        variantId: variantId || 'default-variant',
        quantity: 1,
      },
    ],
    shippingAddress: {
      recipient: 'E2E테스트사용자',
      phone: '010-0000-0000',
      zipCode: '00000',
      address1: '서울시 종로구 세종대로 1',
      address2: `테스트 ${__VU}-${__ITER}`,
    },
  };

  const orderRes = http.post(
    `${BASE_URL}/api/orders`,
    JSON.stringify(orderPayload),
    { headers, tags: { step: 'order_create' } }
  );

  if (!check(orderRes, { 'e2e order: 201': (r) => r.status === 201 })) {
    e2eErrors.add(1);
    e2eDuration.add(Date.now() - startTime);
    return;
  }

  const orderId = JSON.parse(orderRes.body).orderId;

  sleep(randomInt(1, 3));

  // Step 7: 결제 정보 조회
  const paymentRes = http.get(
    `${BASE_URL}/api/payments/orders/${orderId}`,
    { headers, tags: { step: 'payment_query' } }
  );

  check(paymentRes, {
    'e2e payment: 200 or 404': (r) => r.status === 200 || r.status === 404,
  });

  e2eErrors.add(0);
  e2eDuration.add(Date.now() - startTime);

  sleep(randomInt(3, 6));
}
