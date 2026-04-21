import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { BASE_URL, DEFAULT_HEADERS, DEFAULT_THRESHOLDS, authHeaders } from '../lib/config.js';
import { setupTestUser, randomInt } from '../lib/helpers.js';

/**
 * 결제 조회 API 부하 테스트
 *
 * 대상 엔드포인트:
 * - GET /api/payments/orders/{orderId} (결제 정보 조회)
 *
 * 주문 생성 → 결제 정보 조회 흐름을 시뮬레이션
 */

const paymentQueryDuration = new Trend('payment_query_duration', true);
const paymentErrors = new Rate('payment_error_rate');

export const options = {
  scenarios: {
    payment_smoke: {
      executor: 'constant-vus',
      vus: 3,
      duration: '30s',
      tags: { test_type: 'smoke' },
    },
    payment_load: {
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
    payment_stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 30 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 50 },
        { duration: '30s', target: 0 },
      ],
      startTime: '3m30s',
      tags: { test_type: 'stress' },
    },
  },
  thresholds: {
    ...DEFAULT_THRESHOLDS,
    payment_query_duration: ['p(95)<500', 'p(99)<1000'],
    payment_error_rate: ['rate<0.02'],
    http_reqs: ['rate>10'],
  },
};

let vuUser = null;

export function setup() {
  const listRes = http.get(`${BASE_URL}/api/products?page=0&size=5&status=ON_SALE`, {
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
  if (!vuUser) {
    vuUser = setupTestUser(__VU);
    if (!vuUser) {
      sleep(5);
      return;
    }
  }

  const headers = authHeaders(vuUser.accessToken);
  const productIds = data.productIds;

  if (productIds.length === 0) {
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

  // 1. 주문 생성
  const orderPayload = {
    items: [
      {
        productId: productId,
        variantId: variantId || 'default-variant',
        quantity: 1,
      },
    ],
    shippingAddress: {
      recipient: '결제테스트사용자',
      phone: '010-9876-5432',
      zipCode: '54321',
      address1: '서울시 서초구 서초대로 1',
      address2: `${__VU}동 ${__ITER}호`,
    },
  };

  const orderRes = http.post(
    `${BASE_URL}/api/orders`,
    JSON.stringify(orderPayload),
    { headers, tags: { endpoint: 'order_for_payment' } }
  );

  if (orderRes.status !== 201) {
    paymentErrors.add(1);
    sleep(2);
    return;
  }

  const orderId = JSON.parse(orderRes.body).orderId;

  sleep(randomInt(1, 3));

  // 2. 결제 정보 조회
  const paymentRes = http.get(
    `${BASE_URL}/api/payments/orders/${orderId}`,
    { headers, tags: { endpoint: 'payment_query' } }
  );

  paymentQueryDuration.add(paymentRes.timings.duration);

  const queryOk = check(paymentRes, {
    'payment query: status 200 or 404': (r) => r.status === 200 || r.status === 404,
  });

  if (!queryOk) {
    paymentErrors.add(1);
  } else {
    paymentErrors.add(0);
  }

  if (paymentRes.status === 200) {
    check(paymentRes, {
      'payment query: has paymentId': (r) => JSON.parse(r.body).paymentId !== undefined,
      'payment query: correct orderId': (r) => JSON.parse(r.body).orderId === orderId,
    });
  }

  sleep(randomInt(2, 5));
}
