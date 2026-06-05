import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, DEFAULT_HEADERS } from './config.js';

/**
 * 테스트 사용자 회원가입 후 로그인하여 토큰 반환
 */
export function setupTestUser(index) {
  const email = `loadtest-${index}-${Date.now()}@test.com`;
  const password = 'LoadTest1234!';
  const name = `LoadTestUser${index}`;

  const signupRes = http.post(
    `${BASE_URL}/api/auth/signup`,
    JSON.stringify({ email, password, name }),
    { headers: DEFAULT_HEADERS }
  );

  if (signupRes.status !== 201) {
    console.error(`Signup failed for ${email}: ${signupRes.status} ${signupRes.body}`);
    return null;
  }

  const loginRes = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email, password }),
    { headers: DEFAULT_HEADERS }
  );

  if (loginRes.status !== 200) {
    console.error(`Login failed for ${email}: ${loginRes.status} ${loginRes.body}`);
    return null;
  }

  const body = JSON.parse(loginRes.body);
  return {
    email,
    password,
    accessToken: body.accessToken,
    refreshToken: body.refreshToken,
  };
}

/**
 * 응답 상태 코드 체크 헬퍼
 */
export function checkStatus(res, expectedStatus, label) {
  return check(res, {
    [`${label} - status ${expectedStatus}`]: (r) => r.status === expectedStatus,
  });
}

/**
 * 임의 정수 생성 (min 이상, max 미만)
 */
export function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min)) + min;
}

/**
 * 배열에서 랜덤 요소 선택
 */
export function randomChoice(arr) {
  return arr[randomInt(0, arr.length)];
}
