/**
 * 레이아웃 가드의 리다이렉트 술어 — **진짜 함수를 태운다** (`TASK-PC-FE-280`).
 *
 * =============================================================================
 * 🔴🔴 이 파일의 옛 판은 자기가 지키려던 로직을 **로컬에 재구현**해 두고 그것을 검사했다
 * =============================================================================
 * 머리에 이렇게 적혀 있었다:
 *
 *     "Mirrors the sanitisation logic in layout.tsx buildLoginRedirect()."
 *     function buildLoginRedirectFrom(raw: string | null): string { … }
 *
 * ⇒ `(console)/layout.tsx` 의 진짜 `buildLoginRedirect()` 가 **계산에 한 번도 안
 * 들어갔다.** layout 의 규칙이 바뀌어도 이 스위트는 **초록**이다. 그리고 이 파일은
 * `@/` 에서 **아무것도 import 하지 않았다** — `TASK-PC-FE-279` 가 남긴 판별자가
 * 정확히 그것으로 이 파일을 찾아냈다(그 판별자에는 양성 대조군이 있다: 279 가 고친
 * 옛 파일도 `@/` import 0건이었다).
 *
 * 🔵 **고침은 테스트가 아니라 «정의의 수»였다.** 순수한 부분을
 * `shared/lib/login-redirect.ts` 로 빼서 layout 과 이 테스트가 **같은 함수**를 쓴다.
 * 기존 칸은 하나도 안 줄였다 — 10칸 그대로이고, 아래 § 배선 절만 늘었다.
 */

import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { buildLoginRedirectFor } from '@/shared/lib/login-redirect';

describe('layout guard buildLoginRedirect sanitisation (Gap D / F6)', () => {
  it('returns bare /login when x-pathname header is absent (null)', () => {
    expect(buildLoginRedirectFor(null)).toBe('/login');
  });

  it('includes the ?redirect param for a normal console path', () => {
    const result = buildLoginRedirectFor('/console/ecommerce/orders');
    expect(result).toBe(
      `/login?redirect=${encodeURIComponent('/console/ecommerce/orders')}`,
    );
  });

  it('preserves search params in the redirect destination', () => {
    const path = '/console/wms?page=2&sort=asc';
    expect(buildLoginRedirectFor(path)).toBe(
      `/login?redirect=${encodeURIComponent(path)}`,
    );
  });

  it('rejects open-redirect via // prefix', () => {
    expect(buildLoginRedirectFor('//evil.example')).toBe('/login');
  });

  it('rejects absolute URL (http://)', () => {
    expect(buildLoginRedirectFor('http://evil.example/steal')).toBe('/login');
  });

  it('rejects absolute URL (https://)', () => {
    expect(buildLoginRedirectFor('https://evil.example')).toBe('/login');
  });

  it('rejects /login as a redirect target (self-redirect loop)', () => {
    expect(buildLoginRedirectFor('/login')).toBe('/login');
    expect(buildLoginRedirectFor('/login?error=state_mismatch')).toBe('/login');
  });

  it('rejects /api/** paths (not a page destination)', () => {
    expect(buildLoginRedirectFor('/api/auth/callback')).toBe('/login');
    expect(buildLoginRedirectFor('/api/auth/refresh')).toBe('/login');
  });

  it('encodes special characters in the path correctly', () => {
    const path = '/console/search?q=hello world';
    const result = buildLoginRedirectFor(path);
    expect(result).toContain('redirect=');
    // The encoded value must be decodable back to the original path.
    const encoded = result.split('redirect=')[1];
    expect(decodeURIComponent(encoded)).toBe(path);
  });

  it('?redirect param name matches what login/route.ts reads (searchParams.get("redirect"))', () => {
    const result = buildLoginRedirectFor('/console/overview');
    expect(result).toMatch(/[?&]redirect=/);
  });
});

describe('§ 배선 — layout 이 정말 이 함수를 쓰는가 (TASK-PC-FE-280)', () => {
  // 🔴🔴 위 10칸은 **함수**를 잰다. 그것만으로는 layout 이 그 함수를 **쓴다**는 것을
  //    모른다 — 누군가 규칙을 layout 안에 다시 인라인해도 위 칸들은 전부 초록이다.
  //    그러면 이 티켓이 고친 결함이 **그대로 돌아온다.** 그래서 배선을 따로 잰다.
  const layoutSrc = readFileSync(
    join(process.cwd(), 'src/app/(console)/layout.tsx'),
    'utf8',
  );

  it('🔵 비공허성 — layout 소스를 실제로 읽었다', () => {
    expect(layoutSrc.length).toBeGreaterThan(0);
    expect(layoutSrc).toContain('buildLoginRedirect');
  });

  it('🔴 layout 이 `buildLoginRedirectFor` 를 import 해서 부른다', () => {
    expect(layoutSrc).toContain(
      "import { buildLoginRedirectFor } from '@/shared/lib/login-redirect'",
    );
    expect(layoutSrc).toContain('buildLoginRedirectFor(hdrs.get(');
  });

  it('🔴🔴 layout 에 규칙이 **다시 인라인되지 않았다**', () => {
    // 규칙의 지문 — 이 조각이 layout 에 있으면 누군가 술어를 되돌려 놓은 것이다.
    const inlined = [
      "!raw.startsWith('//')",
      "!raw.startsWith('/login')",
      "!raw.startsWith('/api/')",
    ].filter((frag) => layoutSrc.includes(frag));

    expect(
      inlined,
      [
        'layout.tsx 안에 리다이렉트 술어가 다시 인라인됐다.',
        `  발견: ${inlined.join(' · ')}`,
        '  규칙은 shared/lib/login-redirect.ts 한 곳에만 있어야 한다 —',
        '  두 곳이 되는 순간 이 스위트는 그중 하나만 재게 되고,',
        '  그것이 TASK-PC-FE-280 이 고친 결함이다.',
      ].join('\n'),
    ).toEqual([]);
  });
});
