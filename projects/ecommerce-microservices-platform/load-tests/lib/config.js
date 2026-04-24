/**
 * 부하 테스트 공통 설정
 * 모든 시나리오에서 사용하는 기본 URL, 헤더, 임계값 정의
 */

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const DEFAULT_HEADERS = {
  'Content-Type': 'application/json',
};

export function authHeaders(accessToken) {
  return {
    ...DEFAULT_HEADERS,
    Authorization: `Bearer ${accessToken}`,
  };
}

/**
 * 성능 기준 (SLA) 임계값
 *
 * - P95 응답시간: 500ms 이하
 * - P99 응답시간: 1000ms 이하
 * - 에러율: 1% 이하
 * - 최소 RPS: 10 req/s (전체 기본 기준)
 */
export const DEFAULT_THRESHOLDS = {
  http_req_duration: [
    'p(95)<500',
    'p(99)<1000',
  ],
  http_req_failed: ['rate<0.01'],
  http_reqs: ['rate>10'],
};
