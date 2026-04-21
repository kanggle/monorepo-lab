import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useClickOutside } from '@/shared/hooks/use-click-outside';
import { type RefObject } from 'react';

describe('useClickOutside', () => {
  let container: HTMLDivElement;
  let outside: HTMLDivElement;

  beforeEach(() => {
    container = document.createElement('div');
    outside = document.createElement('div');
    document.body.appendChild(container);
    document.body.appendChild(outside);
  });

  afterEach(() => {
    document.body.removeChild(container);
    document.body.removeChild(outside);
  });

  function createRef(element: HTMLElement | null): RefObject<HTMLElement | null> {
    return { current: element };
  }

  it('요소 바깥을 클릭하면 콜백이 호출된다', () => {
    const handler = vi.fn();
    const ref = createRef(container);

    renderHook(() => useClickOutside(ref, handler));

    outside.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));

    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('요소 안쪽을 클릭하면 콜백이 호출되지 않는다', () => {
    const handler = vi.fn();
    const ref = createRef(container);

    renderHook(() => useClickOutside(ref, handler));

    container.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));

    expect(handler).not.toHaveBeenCalled();
  });

  it('자식 요소를 클릭해도 콜백이 호출되지 않는다', () => {
    const child = document.createElement('span');
    container.appendChild(child);
    const handler = vi.fn();
    const ref = createRef(container);

    renderHook(() => useClickOutside(ref, handler));

    child.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));

    expect(handler).not.toHaveBeenCalled();
  });

  it('ref.current가 null이면 콜백이 호출되지 않는다', () => {
    const handler = vi.fn();
    const ref = createRef(null);

    renderHook(() => useClickOutside(ref, handler));

    outside.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));

    expect(handler).not.toHaveBeenCalled();
  });

  it('언마운트 후에는 이벤트 리스너가 제거된다', () => {
    const handler = vi.fn();
    const ref = createRef(container);

    const { unmount } = renderHook(() => useClickOutside(ref, handler));

    unmount();

    outside.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));

    expect(handler).not.toHaveBeenCalled();
  });

  it('콜백이 변경되면 최신 콜백이 호출된다', () => {
    const handler1 = vi.fn();
    const handler2 = vi.fn();
    const ref = createRef(container);

    const { rerender } = renderHook(
      ({ callback }) => useClickOutside(ref, callback),
      { initialProps: { callback: handler1 } },
    );

    rerender({ callback: handler2 });

    outside.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));

    expect(handler1).not.toHaveBeenCalled();
    expect(handler2).toHaveBeenCalledTimes(1);
  });
});
