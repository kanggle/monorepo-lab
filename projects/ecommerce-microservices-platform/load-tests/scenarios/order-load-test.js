import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { BASE_URL, DEFAULT_HEADERS, DEFAULT_THRESHOLDS, authHeaders } from '../lib/config.js';
import { setupTestUser, randomInt } from '../lib/helpers.js';

/**
 * 주문 생성 API 부하 테스트
 *
 * 대상 엔드포인트:
 * - POST /api/orders (주문 생성)
 * - GET /api/orders (주문 목록 조회)
 * - GET /api/orders/{orderId} (주문 상세 조회)
 */

const orderCreateDuration = new Trend('order_create_duration', true);
const orderListDuration = new Trend('order_list_duration', true);
const orderDetailDuration = new Trend('order_detail_duration', true);
const orderErrors = new Rate('order_error_rate');

export const options = {
  scenarios: {
    order_smoke: {
      executor: 'constant-vus',
      vus: 3,
      duration: '30s',
      tags: { test_type: 'smoke' },
    },
    order_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '1m', target: 30 },
        { duration: '30s', target: 30 },
        { duration: '30s', target: 0 },
      ],
      startTime: '35s',
      tags: { test_type: 'load' },
    },
    order_stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 30 },
        { duration: '1m', target: 60 },
        { duration: '30s', target: 60 },
        { duration: '30s', target: 0 },
      ],
      startTime: '3m30s',
      tags: { test_type: 'stress' },
    },
  },
  thresholds: {
    ...DEFAULT_THRESHOLDS,
    order_create_duration: ['p(95)<800', 'p(99)<1500'],
    order_list_duration: ['p(95)<400', 'p(99)<800'],
    order_detail_duration: ['p(95)<400', 'p(99)<800'],
    order_error_rate: ['rate<0.02'],
    http_reqs: ['rate>10'],
  },
};

// VU별 인증 정보 저장
let vuUser = null;

export function setup() {
  // 상품 목록에서 테스트용 상품 ID 수집
  const listRes = http.get(`${BASE_URL}/api/products?page=0&size=10&status=ON_SALE`, {
    headers: DEFAULT_HEADERS,
  });

  let products = [];
  if (listRes.status === 200) {
    const body = JSON.parse(listRes.body);
    products = (body.content || []).map((p) => p.id);
  }

  return { productIds: products };
}

export default function (data) {
  // VU별로 한 번만 사용자 생성
  if (!vuUser) {
    vuUser = setupTestUser(__VU);
    if (!vuUser) {
      sleep(5);
      return;
    }
  }

  const headers = authHeaders(vuUser.accessToken);

  // 1. 주문 생성
  const productIds = data.productIds;
  if (productIds.length === 0) {
    // 상품이 없으면 주문 목록 조회만 수행
    const listRes = http.get(`${BASE_URL}/api/orders?page=0&size=20`, {
      headers,
      tags: { endpoint: 'order_list' },
    });
    orderListDuration.add(listRes.timings.duration);
    check(listRes, { 'order list (no products): status 200': (r) => r.status === 200 });
    sleep(3);
    return;
  }

  const productId = productIds[randomInt(0, productIds.length)];

  // 상품 상세에서 variant 조회
  const detailRes = http.get(`${BASE_URL}/api/products/${productId}`, {
    headers: DEFAULT_HEADERS,
  });

  let variantId = null;
  if (detailRes.status === 200) {
    const detail = JSON.parse(detailRes.body);
    if (detail.variants && detail.variants.length > 0) {
      variantId = detail.variants[0].id;
    }
  }

  const orderPayload = {
    items: [
      {
        productId: productId,
        variantId: variantId || 'default-variant',
        quantity: randomInt(1, 3),
      },
    ],
    shippingAddress: {
      recipient: '부하테스트사용자',
      phone: '010-1234-5678',
      zipCode: '12345',
      address1: '서울시 강남구 테헤란로 1',
      address2: `${__VU}동 ${__ITER}호`,
    },
  };

  const createRes = http.post(
    `${BASE_URL}/api/orders`,
    JSON.stringify(orderPayload),
    { headers, tags: { endpoint: 'order_create' } }
  );

  orderCreateDuration.add(createRes.timings.duration);

  const createOk = check(createRes, {
    'order create: status 201': (r) => r.status === 201,
    'order create: has orderId': (r) => r.status === 201 && JSON.parse(r.body).orderId !== undefined,
  });

  if (!createOk) {
    orderErrors.add(1);
    sleep(2);
    return;
  }
  orderErrors.add(0);

  const orderId = JSON.parse(createRes.body).orderId;

  sleep(randomInt(1, 3));

  // 2. 주문 목록 조회
  const listRes = http.get(`${BASE_URL}/api/orders?page=0&size=20`, {
    headers,
    tags: { endpoint: 'order_list' },
  });

  orderListDuration.add(listRes.timings.duration);

  check(listRes, {
    'order list: status 200': (r) => r.status === 200,
  });

  sleep(randomInt(1, 2));

  // 3. 주문 상세 조회
  const detailOrderRes = http.get(`${BASE_URL}/api/orders/${orderId}`, {
    headers,
    tags: { endpoint: 'order_detail' },
  });

  orderDetailDuration.add(detailOrderRes.timings.duration);

  check(detailOrderRes, {
    'order detail: status 200': (r) => r.status === 200,
    'order detail: correct orderId': (r) =>
      r.status === 200 && JSON.parse(r.body).orderId === orderId,
  });

  sleep(randomInt(2, 5));
}
