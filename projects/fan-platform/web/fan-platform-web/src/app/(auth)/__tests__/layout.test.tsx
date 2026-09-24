/**
 * `(auth)/layout.tsx` — `TASK-FAN-FE-024` AC-1.
 *
 * 🔴 `Header` 는 async Server Component 다. react-dom 의 client `render()` 는 async
 *    컴포넌트를 **JSX 로**(`<Header />`) 못 그린다(*"Only Server Components can be async
 *    at the moment"*) — Next.js RSC 러너 밖의 jsdom 유닛 테스트가 갖는 구조적 한계다(형제
 *    `login-page.test.tsx` 가 `<LoginPage/>` 대신 `await LoginPage(...)` 를 직접 호출해
 *    같은 벽을 우회한 이유와 같다). 그래서 이 파일은 두 칸으로 나눈다:
 *    ① `Header()` 를 직접 호출·await 해 **로그인 라우트가 실제로 보여줄 내용**(내비 + 익명
 *       zero-gateway)을 렌더 기준으로 확인 — 이 AC 의 본체.
 *    ② `(auth)/layout.tsx` 소스를 읽어 그 `Header` 가 **레이아웃에 실제로 배선**됐는지 확인
 *       — 형제 `fan-post-compose.test.tsx:88` 와 같은 소스-읽기 방식(배선 확인은 렌더로
 *       못 잡을 수 있다는 이 저장소의 반복 함정에 대한 대조군).
 *
 * 🔴 `@/shared/auth/session` · `@/shared/auth/federated-logout` · `@/features/notification`
 *    만 갈아 끼운다 — 셋 다 `server-only` 를 임포트해서 jsdom 에서 로드가 안 되기 때문이다
 *    (형제 `public-pages.test.tsx`·`post-detail-back-link.test.tsx` 와 같은 방식). 그 외
 *    `Header` 자신의 JSX/분기 로직은 **진짜**를 그대로 태운다.
 */

import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';

const state = vi.hoisted(() => ({ authed: false }));

vi.mock('@/shared/auth/session', () => ({
  isAuthenticated: async () => state.authed,
  getFanSession: async () => {
    throw new Error('getFanSession should not be called for an anonymous visitor');
  },
}));
vi.mock('@/shared/auth/federated-logout', () => ({
  buildGapEndSessionUrl: async () => null,
}));
vi.mock('@/shared/auth/auth', () => ({
  auth: async () => null,
  signIn: vi.fn(),
  signOut: vi.fn(),
}));
vi.mock('@/features/notification', () => ({
  NotificationBell: () => null,
  getRecentNotifications: async () => {
    throw new Error('getRecentNotifications should not be called for an anonymous visitor');
  },
  getUnreadCount: async () => {
    throw new Error('getUnreadCount should not be called for an anonymous visitor');
  },
}));

async function renderHeader() {
  const { Header } = await import('@/widgets/header/Header');
  const el = await Header();
  render(<div data-testid="host">{el}</div>);
}

beforeEach(() => {
  vi.resetModules();
  vi.unstubAllGlobals();
  state.authed = false;
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('로그인 라우트가 익명 방문자에게 보여줄 Header (AC-1)', () => {
  it('로고 + 주요 내비(피드/아티스트/멤버십)를 렌더한다', async () => {
    await renderHeader();

    expect(screen.getByText('fan-platform')).toBeInTheDocument();
    expect(screen.getByTestId('nav-primary')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '아티스트' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '멤버십' })).toBeInTheDocument();
    // 익명 방문자에게는 헤더 우측이 "로그인" 링크 하나다(인증 UI 없음).
    expect(screen.getByRole('link', { name: '로그인' })).toBeInTheDocument();
  });

  it('🔴🔴 익명 방문자의 이 렌더에서 게이트웨이 호출이 0회다(zero-gateway 불변식)', async () => {
    const fetchSpy = vi.fn().mockResolvedValue({ ok: true, json: async () => ({}) } as unknown as Response);
    vi.stubGlobal('fetch', fetchSpy);

    await renderHeader();

    expect(fetchSpy).not.toHaveBeenCalled();
  });
});

describe('(auth)/layout.tsx 배선 — Header 가 실제로 로그인 라우트에 붙는다', () => {
  it('레이아웃 소스가 Header 를 임포트하고 렌더한다(children 과 함께)', () => {
    const src = readFileSync(
      join(process.cwd(), 'src/app/(auth)/layout.tsx'),
      'utf8',
    );
    expect(src).toMatch(/import\s*\{\s*Header\s*\}\s*from\s*'@\/widgets\/header\/Header'/);
    expect(src).toMatch(/<Header\s*\/>/);
    expect(src).toMatch(/\{children\}/);
  });
});
