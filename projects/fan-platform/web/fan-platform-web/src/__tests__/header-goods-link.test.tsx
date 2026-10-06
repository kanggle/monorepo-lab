/**
 * `TASK-MONO-739` AC-4 — 헤더 「굿즈샵 ↗」 와 아티스트 굿즈 조각이 **서버 컴포넌트로 남고**, 익명 렌더가 **요청 0** 이다.
 *
 * 🔴 헤더는 공개 페이지를 포함한 `(main)` 셸 전부에 붙는다(`Header.tsx` 머리 주석). 여기서 새는 요청 하나가
 *    «익명 방문은 백엔드를 안 부른다» 를 통째로 깬다 — 그래서 `fetch` 스파이로 **0회**를 단언하고, 회원 경로의
 *    조각(세션·알림·heartbeat)은 스텁이 «불렸다» 를 기록하게 해서 **만들어지지도 않음**을 같이 단언한다.
 * 🔵 서버 전용 모듈(auth.js · 알림 API)은 jsdom 에서 로드가 안 되므로 갈아 끼운다 — 링크를 만드는
 *    `shared/config/store-links.ts` 는 **진짜**다.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const state = vi.hoisted(() => ({ memberCalls: [] as string[] }));

vi.mock('@/shared/auth/auth', () => ({ signOut: async () => undefined }));
vi.mock('@/shared/auth/federated-logout', () => ({ buildGapEndSessionUrl: async () => null }));
vi.mock('@/shared/auth/session', () => ({
  isAuthenticated: async () => false,
  getFanSession: async () => {
    state.memberCalls.push('getFanSession');
    return null;
  },
}));
vi.mock('@/features/notification', () => ({
  NotificationBell: () => {
    state.memberCalls.push('NotificationBell');
    return null;
  },
  getRecentNotifications: async () => {
    state.memberCalls.push('getRecentNotifications');
    return [];
  },
  getUnreadCount: async () => {
    state.memberCalls.push('getUnreadCount');
    return 0;
  },
}));
vi.mock('@/widgets/heartbeat/DemoHeartbeat', () => ({
  DemoHeartbeat: () => {
    state.memberCalls.push('DemoHeartbeat');
    return null;
  },
}));
// TASK-FAN-FE-027 — the session keeper reads `/api/auth/session`; it is a member fragment too.
vi.mock('@/shared/auth/SessionKeeper', () => ({
  SessionKeeper: () => {
    state.memberCalls.push('SessionKeeper');
    return null;
  },
}));

import { Header } from '@/widgets/header/Header';
import { STORE_GOODS_CATEGORY_ID } from '@/shared/config/store-links';

let fetchSpy: ReturnType<typeof vi.fn>;

beforeEach(() => {
  state.memberCalls = [];
  fetchSpy = vi.fn().mockRejectedValue(new Error('익명 헤더에서 네트워크 요청이 발생했다'));
  vi.stubGlobal('fetch', fetchSpy);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe('헤더 「굿즈샵 ↗」 — 익명', () => {
  it('🔴 스토어의 굿즈 목록으로 가는 평범한 `<a>` 다 — 같은 탭(R3), 외부임을 글자로(「↗」)', async () => {
    render(await Header());
    const link = screen.getByTestId('nav-goods-shop');
    expect(link.tagName).toBe('A');
    expect(link).toHaveTextContent('굿즈샵 ↗');
    expect(link).not.toHaveAttribute('target');
    expect(link.getAttribute('href')).toBe(
      `https://store.hubwang.com/products?categoryId=${STORE_GOODS_CATEGORY_ID}`,
    );
    // 🔵 기존 메뉴 묶음 안에 있다 — 400px 에서 둘째 줄로 내려가는 그 묶음(TASK-FAN-FE-023).
    expect(screen.getByTestId('nav-primary')).toContainElement(link);
  });

  it('🔴🔴 fetch 0회 · 회원 조각(세션·알림·heartbeat) 미생성', async () => {
    render(await Header());
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(state.memberCalls).toEqual([]);
  });

  it('`NEXT_PUBLIC_STORE_URL` 이 있으면 그 주소로 간다', async () => {
    vi.stubEnv('NEXT_PUBLIC_STORE_URL', 'https://store.example.test/');
    render(await Header());
    expect(screen.getByTestId('nav-goods-shop').getAttribute('href')).toBe(
      `https://store.example.test/products?categoryId=${STORE_GOODS_CATEGORY_ID}`,
    );
  });
});

describe("AC-4 — 서버 경계: `'use client'` 를 새로 들이지 않았다", () => {
  // 🔵 선언만 본다 — «클라이언트 그래프에 닿는가» 는 `check-client-graph-backend-origins.mjs` 의 축이다.
  const SRC = resolve(__dirname, '..');
  for (const rel of [
    'widgets/header/Header.tsx',
    'app/(main)/artists/[id]/page.tsx',
    'features/public-browse/ui/PublicArtistGoods.tsx',
    'shared/config/store-links.ts',
  ]) {
    it(rel, () => {
      const src = readFileSync(resolve(SRC, rel), 'utf8');
      expect(src).not.toMatch(/^\s*['"]use client['"]/m);
    });
  }
});
