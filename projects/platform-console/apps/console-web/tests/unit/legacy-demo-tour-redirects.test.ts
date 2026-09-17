/**
 * `TASK-MONO-686` — 은퇴한 콘솔 둘러보기(`/demo`, `/demo/<domain>`)의 308 리다이렉트.
 *
 * 🔴 도메인 키 목록은 **손으로 다시 적지 않는다.** 아래 `FROZEN_TOUR_DOMAIN_KEYS` 는 이
 *    커밋이 함께 지운 `infra/demo/public-data/fixtures/console-sample.mjs` 의
 *    `CONSOLE_SAMPLE_DOMAINS`(각 원소의 `key` 필드, 2026-09 작성 · `TASK-PC-FE-282`~`288`
 *    이 채운 도메인 7개 — overview·ecommerce·wms·scm·erp·finance·iam)에서 **그대로 옮긴
 *    동결 사본**이다. 그 파일은 이 커밋에서 삭제됐으므로 이 목록이 지금 그 사실의 유일한
 *    정본 사본이고, 아래 첫 시험이 `next.config.mjs` 의 `DEMO_TOUR_DOMAIN_REDIRECTS` 가
 *    **키 집합**에서 그것과 갈라지지 않았는지를 잰다(둘 중 하나만 고치면 여기서 빨개진다).
 */
import { describe, it, expect } from 'vitest';
import nextConfig, { DEMO_TOUR_DOMAIN_REDIRECTS } from '../../next.config.mjs';

const FROZEN_TOUR_DOMAIN_KEYS = Object.freeze([
  'overview',
  'ecommerce',
  'wms',
  'scm',
  'erp',
  'finance',
  'iam',
]);

describe('DEMO_TOUR_DOMAIN_REDIRECTS — 삭제 직전 픽스처에서 뽑은 키와 대조', () => {
  it('키 집합이 동결 사본과 정확히 같다 (순서 무관, 개수·중복 포함)', () => {
    const keys = DEMO_TOUR_DOMAIN_REDIRECTS.map((r) => r.domain);
    expect(new Set(keys)).toEqual(new Set(FROZEN_TOUR_DOMAIN_KEYS));
    expect(keys).toHaveLength(FROZEN_TOUR_DOMAIN_KEYS.length);
    expect(new Set(keys).size).toBe(keys.length); // 중복 키 없음
  });

  it('모든 destination 이 /demo 로 시작하지 않는다 (실제 콘솔 경로다)', () => {
    for (const { destination } of DEMO_TOUR_DOMAIN_REDIRECTS) {
      expect(destination.startsWith('/demo')).toBe(false);
      expect(destination.startsWith('/')).toBe(true);
    }
  });
});

describe('next.config.mjs redirects() — 308(permanent) · 모든 키가 매핑됨', () => {
  it('도메인 7개 전부에 대해 /demo/<key> → 그 도메인의 실제 경로, permanent:true', async () => {
    const rules = await nextConfig.redirects();
    for (const { domain, destination } of DEMO_TOUR_DOMAIN_REDIRECTS) {
      const rule = rules.find((r) => r.source === `/demo/${domain}`);
      if (!rule) throw new Error(`/demo/${domain} 에 대한 규칙이 없습니다`);
      expect(rule.destination).toBe(destination);
      expect(rule.permanent).toBe(true);
    }
  });

  it('모르는 /demo/<x> 와 맨살 /demo 는 실제 개요로 (404 가 아니다)', async () => {
    const rules = await nextConfig.redirects();
    const fallback = rules.find((r) => r.source === '/demo/:path*');
    if (!fallback) throw new Error('/demo/:path* 폴백 규칙이 없습니다');
    expect(fallback.destination).toBe('/dashboards/overview');
    expect(fallback.permanent).toBe(true);
  });

  it('구체적 도메인 규칙이 폴백보다 배열에서 먼저 온다 (Next 는 첫 매치를 쓴다)', async () => {
    const rules = await nextConfig.redirects();
    const fallbackIndex = rules.findIndex((r) => r.source === '/demo/:path*');
    const specificIndexes = DEMO_TOUR_DOMAIN_REDIRECTS.map(({ domain }) =>
      rules.findIndex((r) => r.source === `/demo/${domain}`),
    );
    expect(fallbackIndex).toBeGreaterThan(-1);
    for (const i of specificIndexes) expect(i).toBeLessThan(fallbackIndex);
  });
});
