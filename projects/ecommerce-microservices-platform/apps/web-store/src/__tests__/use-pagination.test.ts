import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { usePagination } from '@/shared/hooks/use-pagination';

describe('usePagination', () => {
  it('기본 size는 20, 초기 page는 0이다', () => {
    const { result } = renderHook(() => usePagination(100));

    expect(result.current.page).toBe(0);
    expect(result.current.size).toBe(20);
    expect(result.current.totalPages).toBe(5);
  });

  it('defaultSize를 지정하면 해당 값이 적용된다', () => {
    const { result } = renderHook(() => usePagination(100, 10));

    expect(result.current.size).toBe(10);
    expect(result.current.totalPages).toBe(10);
  });

  it('totalElements가 0이어도 totalPages는 최소 1이다', () => {
    const { result } = renderHook(() => usePagination(0));

    expect(result.current.totalPages).toBe(1);
  });

  it('나누어 떨어지지 않는 경우 올림으로 totalPages를 계산한다', () => {
    const { result } = renderHook(() => usePagination(25, 10));

    expect(result.current.totalPages).toBe(3);
  });

  it('handlePageChange로 음수가 아닌 페이지로 이동할 수 있다', () => {
    const { result } = renderHook(() => usePagination(100, 10));

    act(() => {
      result.current.handlePageChange(3);
    });

    expect(result.current.page).toBe(3);
  });

  it('handlePageChange는 음수 페이지를 무시한다', () => {
    const { result } = renderHook(() => usePagination(100, 10));

    act(() => {
      result.current.handlePageChange(2);
    });
    act(() => {
      result.current.handlePageChange(-1);
    });

    expect(result.current.page).toBe(2);
  });

  it('handleSizeChange는 size를 변경하고 페이지를 0으로 리셋한다', () => {
    const { result } = renderHook(() => usePagination(100, 10));

    act(() => {
      result.current.handlePageChange(4);
    });
    act(() => {
      result.current.handleSizeChange(50);
    });

    expect(result.current.size).toBe(50);
    expect(result.current.page).toBe(0);
  });
});
