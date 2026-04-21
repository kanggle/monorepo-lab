import { renderHook, act } from '@testing-library/react';
import { useAsyncAction } from '@/shared/hooks/use-async-action';

describe('useAsyncAction', () => {
  it('초기 상태에서 error는 빈 문자열이다', () => {
    const { result } = renderHook(() => useAsyncAction());
    expect(result.current.error).toBe('');
  });

  it('execute 성공 시 error가 비어있다', async () => {
    const { result } = renderHook(() => useAsyncAction());

    await act(async () => {
      await result.current.execute(async () => {}, '실패 메시지');
    });

    expect(result.current.error).toBe('');
  });

  it('execute 실패 시 에러 메시지를 설정한다', async () => {
    const { result } = renderHook(() => useAsyncAction());

    await act(async () => {
      await result.current.execute(async () => {
        throw new Error('서버 오류');
      }, '기본 메시지');
    });

    expect(result.current.error).toBe('서버 오류');
  });

  it('Error가 아닌 예외 시 fallback 메시지를 사용한다', async () => {
    const { result } = renderHook(() => useAsyncAction());

    await act(async () => {
      await result.current.execute(async () => {
        throw 'string error';
      }, '기본 메시지');
    });

    expect(result.current.error).toBe('기본 메시지');
  });

  it('clearError 호출 시 error를 빈 문자열로 초기화한다', async () => {
    const { result } = renderHook(() => useAsyncAction());

    await act(async () => {
      await result.current.execute(async () => {
        throw new Error('에러 발생');
      }, '기본 메시지');
    });
    expect(result.current.error).toBeTruthy();

    act(() => {
      result.current.clearError();
    });

    expect(result.current.error).toBe('');
  });

  it('재실행 시 이전 에러를 초기화한다', async () => {
    const { result } = renderHook(() => useAsyncAction());

    await act(async () => {
      await result.current.execute(async () => {
        throw new Error('첫 번째 에러');
      }, '기본 메시지');
    });
    expect(result.current.error).toBe('첫 번째 에러');

    await act(async () => {
      await result.current.execute(async () => {}, '기본 메시지');
    });
    expect(result.current.error).toBe('');
  });
});
