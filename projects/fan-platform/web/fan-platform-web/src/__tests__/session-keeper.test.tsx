/**
 * TASK-FAN-FE-027 — `SessionKeeper` is the ONLY thing in fan-platform-web that
 * reads `/api/auth/session`, i.e. the only thing that makes the silent refresh
 * run once every server read is decode-only. If it stops reading, the access
 * token silently expires and every gateway call 401s — so these cells pin
 * when it reads and when it re-renders.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, act } from '@testing-library/react';

const routerRefresh = vi.hoisted(() => vi.fn());
vi.mock('next/navigation', () => ({ useRouter: () => ({ refresh: routerRefresh }) }));

import { SessionKeeper, KEEPER_INTERVAL_MS, SESSION_ENDPOINT } from '@/shared/auth/SessionKeeper';
import { REFRESH_MARGIN_SECONDS } from '@/shared/auth/auth-callbacks';

let fetchMock: ReturnType<typeof vi.fn>;
let visibility: DocumentVisibilityState = 'visible';

function setVisibility(v: DocumentVisibilityState) {
  visibility = v;
  document.dispatchEvent(new Event('visibilitychange'));
}

/** Let resolved promises (fetch → then → refresh) run under fake timers. */
async function flush() {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
  });
}

beforeEach(() => {
  vi.useFakeTimers();
  routerRefresh.mockReset();
  fetchMock = vi.fn(async () => new Response('{}', { status: 200 }));
  vi.stubGlobal('fetch', fetchMock);
  visibility = 'visible';
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => visibility });
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('SessionKeeper', () => {
  it('🔴 poll period stays below the jwt callback refresh margin — a visible tab never holds an expired access token', () => {
    expect(KEEPER_INTERVAL_MS).toBeLessThan(REFRESH_MARGIN_SECONDS * 1000);
  });

  it('reads /api/auth/session every interval while visible', async () => {
    render(<SessionKeeper staleAtRender={false} />);
    expect(fetchMock).not.toHaveBeenCalled(); // fresh render: nothing to do yet
    await act(async () => {
      vi.advanceTimersByTime(KEEPER_INTERVAL_MS);
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBe(SESSION_ENDPOINT);
    await flush();
    await act(async () => {
      vi.advanceTimersByTime(KEEPER_INTERVAL_MS);
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(routerRefresh).not.toHaveBeenCalled();
  });

  it('does not poll while hidden, and reads once on return to the tab', async () => {
    render(<SessionKeeper staleAtRender={false} />);
    setVisibility('hidden');
    await act(async () => {
      vi.advanceTimersByTime(KEEPER_INTERVAL_MS * 3);
    });
    expect(fetchMock).not.toHaveBeenCalled();
    await act(async () => {
      setVisibility('visible');
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('🔴 stale server render → reads at once, then router.refresh() with the rotated cookie', async () => {
    render(<SessionKeeper staleAtRender />);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    await flush();
    expect(routerRefresh).toHaveBeenCalledTimes(1);
  });

  it('stale render but the read failed (network) → no re-render loop', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('offline'));
    render(<SessionKeeper staleAtRender />);
    await flush();
    expect(routerRefresh).not.toHaveBeenCalled();
  });

  it('reads are single-flight within a tab', async () => {
    let release!: () => void;
    fetchMock.mockImplementationOnce(
      () => new Promise<Response>((r) => (release = () => r(new Response('{}')))),
    );
    render(<SessionKeeper staleAtRender />);
    await act(async () => {
      setVisibility('visible');
      vi.advanceTimersByTime(KEEPER_INTERVAL_MS);
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    release();
    await flush();
    expect(routerRefresh).toHaveBeenCalledTimes(1);
  });

  it('stops on unmount', async () => {
    const { unmount } = render(<SessionKeeper staleAtRender={false} />);
    unmount();
    await act(async () => {
      vi.advanceTimersByTime(KEEPER_INTERVAL_MS * 2);
      setVisibility('visible');
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
