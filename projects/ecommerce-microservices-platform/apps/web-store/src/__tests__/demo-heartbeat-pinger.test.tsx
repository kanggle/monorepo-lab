/**
 * `DemoHeartbeat` — 로그인 영역에서만 도는 핑거.
 *
 * 🔵 여기서 재는 것은 «주기» 와 «마운트 즉시 한 번» 뿐이다. «익명은 안 보낸다» 는 성질은
 *    이 컴포넌트가 아니라 **마운트 지점**(로그인 가드를 통과한 자리)과 **라우트 핸들러**가
 *    만든다 — 그 판정은 `demo-heartbeat-route.test.ts` 에 있다.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render } from '@testing-library/react';
import { DemoHeartbeat } from '@/features/demo-heartbeat';

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  vi.useFakeTimers();
  fetchMock = vi.fn().mockResolvedValue({ status: 204 } as Response);
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('DemoHeartbeat', () => {
  it('마운트 즉시 한 번 보낸다 — 첫 틱을 기다리면 그 사이 idle 판정이 지나간다', () => {
    render(<DemoHeartbeat />);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/demo/heartbeat');
    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
  });

  it('주기마다 다시 보낸다', () => {
    render(<DemoHeartbeat intervalMs={1000} />);
    expect(fetchMock).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(3000);

    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it('언마운트하면 멈춘다 (로그인 영역을 떠나면 더 안 보낸다)', () => {
    const { unmount } = render(<DemoHeartbeat intervalMs={1000} />);
    unmount();

    vi.advanceTimersByTime(5000);

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
