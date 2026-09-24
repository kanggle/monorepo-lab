/**
 * `/login?error=session_expired` — the demo-shutdown variant (TASK-PC-FE-299 AC-4).
 *
 * 🔴 The Goal explicitly forbids a guessed distinction — the message must
 * come from the SAME real signal `DemoBackendNotice` uses
 * (`resolveDemoBackendState()`, `shared/config/demo-backend.ts`). This suite
 * mocks that resolver directly (same pattern as
 * `tests/unit/demo-backend-notice.test.tsx`) rather than the network it
 * wraps, and pins: only `'unavailable'` gets the demo-specific message.
 * `'starting'` (the instance IS up, just not fully ready — TASK-MONO-668)
 * must NOT say "종료" (that would be a lie), and `'not-demo'`/`'running'`
 * keep the pre-existing generic message unchanged (regression guard —
 * `login-error-messages.test.tsx` already pins that literal, this file only
 * adds the new branch).
 *
 * 🔵 `DemoBackendNotice` is mocked to a sync no-op — same reason every
 *    sibling `/login` suite does it (it is an async Server Component; jsdom
 *    + react-dom cannot render an unresolved async component, TASK-PC-FE-278
 *    hit this exact failure). This file's target is `resolveDemoBackendState`
 *    as consumed by the PAGE, not by the widget — the widget has its own
 *    suite (`demo-backend-notice.test.tsx`) already proving it reads the same
 *    resolver correctly.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SESSION_EXPIRED } from '@/shared/lib/re-login';

const redirectMock = vi.fn();
vi.mock('next/navigation', () => ({
  redirect: (path: string) => {
    redirectMock(path);
    throw new Error(`REDIRECT:${path}`);
  },
}));

vi.mock('@/shared/lib/session', () => ({ isAuthenticated: async () => false }));

vi.mock('next/link', () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

vi.mock('@/widgets/demo-credentials/DemoLoginCredentials', () => ({
  DemoLoginCredentials: () => null,
}));
vi.mock('@/widgets/demo-notice/DemoBackendNotice', () => ({
  DemoBackendNotice: () => null,
}));

const { state } = vi.hoisted(() => ({ state: { value: 'not-demo' as string } }));
vi.mock('@/shared/config/demo-backend', () => ({
  resolveDemoBackendState: async () => state.value,
}));

async function renderLogin(sp: { error?: string } = {}) {
  const { default: LoginPage } = await import('@/app/(auth)/login/page');
  const el = await LoginPage({ searchParams: Promise.resolve(sp) });
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={qc}>
      <div data-testid="host">{el}</div>
    </QueryClientProvider>,
  );
}

function alertText(): string | null {
  return screen.queryByRole('alert')?.textContent?.trim() ?? null;
}

beforeEach(() => {
  vi.resetModules();
  redirectMock.mockClear();
  state.value = 'not-demo';
});

describe('세션 만료 문구 — 데모 종료 신호 (TASK-PC-FE-299 AC-4)', () => {
  it('🔴🔴 `unavailable`(데모 꺼짐, 실신호) → 데모 종료 문구', async () => {
    state.value = 'unavailable';
    await renderLogin({ error: SESSION_EXPIRED });
    const t = alertText()!;
    expect(t).toContain('데모 서버가 종료');
    expect(t).not.toContain('세션이 만료');
  });

  it('🔴 `starting`(켜지는 중 — 종료가 아니다) → 일반 세션 만료 문구, "종료" 라고 말하지 않는다', async () => {
    state.value = 'starting';
    await renderLogin({ error: SESSION_EXPIRED });
    const t = alertText()!;
    expect(t).toContain('세션이 만료');
    expect(t).not.toContain('종료');
  });

  it('🔵 대조군 — `running`(데모가 살아 있다) → 일반 세션 만료 문구', async () => {
    state.value = 'running';
    await renderLogin({ error: SESSION_EXPIRED });
    expect(alertText()).toContain('세션이 만료');
  });

  it('🔵 대조군 — `not-demo`(로컬·CI·비데모 배포) → 일반 세션 만료 문구', async () => {
    state.value = 'not-demo';
    await renderLogin({ error: SESSION_EXPIRED });
    expect(alertText()).toContain('세션이 만료');
  });

  it('🔵 대조군 — 마커 없는 일반 `/login` 방문은 이 신호를 아예 안 묻는다(다른 에러코드는 영향 없음)', async () => {
    state.value = 'unavailable';
    await renderLogin({ error: 'state_mismatch' });
    const t = alertText()!;
    expect(t).not.toContain('데모');
    expect(t).toContain('다시 로그인');
  });
});
