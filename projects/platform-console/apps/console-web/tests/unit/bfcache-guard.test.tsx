/**
 * `widgets/bfcache-guard/BfcacheGuard` (TASK-PC-FE-299 AC-3/AC-5).
 *
 * Reproduction this pins: back-button-returned bfcache page must not keep
 * showing an authenticated screen once the session cookies are gone. The
 * real check (a live logout + browser back button) is done manually — see
 * the task's implementation record — this suite pins the mechanism: a
 * `pageshow` event with `persisted: true` forces a real reload (re-entering
 * the server-side auth guard); anything else does nothing.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, cleanup } from '@testing-library/react';
import { BfcacheGuard } from '@/widgets/bfcache-guard/BfcacheGuard';

function firePageShow(persisted: boolean) {
  const event = new Event('pageshow') as PageTransitionEvent & {
    persisted: boolean;
  };
  Object.defineProperty(event, 'persisted', { value: persisted });
  window.dispatchEvent(event);
}

describe('BfcacheGuard', () => {
  let reloadSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    reloadSpy = vi.fn();
    vi.stubGlobal('location', { ...window.location, reload: reloadSpy });
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('🔴🔴 `pageshow` with `persisted: true` (bfcache restore) → reload()', () => {
    render(<BfcacheGuard />);
    firePageShow(true);
    expect(reloadSpy).toHaveBeenCalledTimes(1);
  });

  it('🔵 대조군 — ordinary `pageshow` (`persisted: false`, a normal load) → no reload', () => {
    render(<BfcacheGuard />);
    firePageShow(false);
    expect(reloadSpy).not.toHaveBeenCalled();
  });

  it('🔵 unmount removes the listener — no reload after teardown', () => {
    const { unmount } = render(<BfcacheGuard />);
    unmount();
    firePageShow(true);
    expect(reloadSpy).not.toHaveBeenCalled();
  });

  it('renders nothing', () => {
    const { container } = render(<BfcacheGuard />);
    expect(container).toBeEmptyDOMElement();
  });
});
