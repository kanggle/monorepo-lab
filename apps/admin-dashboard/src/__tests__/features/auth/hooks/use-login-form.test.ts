import { renderHook, act } from '@testing-library/react';
import { useLoginForm } from '@/features/auth/hooks/use-login-form';

const mockPush = vi.fn();
const mockGet = vi.fn().mockReturnValue(null);
const mockLogin = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  useSearchParams: () => ({ get: mockGet }),
}));

vi.mock('@/shared/hooks', () => ({
  useAuth: () => ({ login: mockLogin }),
}));

describe('useLoginForm', () => {
  beforeEach(() => {
    mockPush.mockClear();
    mockGet.mockClear().mockReturnValue(null);
    mockLogin.mockClear().mockResolvedValue(undefined);
  });

  it('초기 상태는 빈 이메일, 빈 비밀번호, isValid=false이다', () => {
    const { result } = renderHook(() => useLoginForm());

    expect(result.current.email).toBe('');
    expect(result.current.password).toBe('');
    expect(result.current.isValid).toBe(false);
    expect(result.current.isSubmitting).toBe(false);
    expect(result.current.error).toBe('');
  });

  it('error=oauth_failed 쿼리 파라미터가 있으면 초기 에러 메시지를 설정한다', () => {
    mockGet.mockImplementation((key: string) => (key === 'error' ? 'oauth_failed' : null));

    const { result } = renderHook(() => useLoginForm());

    expect(result.current.error).toBe('Google 로그인에 실패했습니다. 다시 시도해 주세요.');
  });

  it('이메일에 @가 포함되고 비밀번호가 8자 이상이면 isValid=true이다', () => {
    const { result } = renderHook(() => useLoginForm());

    act(() => {
      result.current.setEmail('user@example.com');
      result.current.setPassword('12345678');
    });

    expect(result.current.isValid).toBe(true);
  });

  it('이메일에 @가 없으면 isValid=false이다', () => {
    const { result } = renderHook(() => useLoginForm());

    act(() => {
      result.current.setEmail('invalid-email');
      result.current.setPassword('12345678');
    });

    expect(result.current.isValid).toBe(false);
  });

  it('비밀번호가 8자 미만이면 isValid=false이다', () => {
    const { result } = renderHook(() => useLoginForm());

    act(() => {
      result.current.setEmail('user@example.com');
      result.current.setPassword('1234567');
    });

    expect(result.current.isValid).toBe(false);
  });

  it('로그인 성공 시 /dashboard로 이동한다', async () => {
    const { result } = renderHook(() => useLoginForm());

    act(() => {
      result.current.setEmail('user@example.com');
      result.current.setPassword('12345678');
    });

    await act(async () => {
      await result.current.handleSubmit({ preventDefault: vi.fn() } as unknown as React.FormEvent);
    });

    expect(mockLogin).toHaveBeenCalledWith({ email: 'user@example.com', password: '12345678' });
    expect(mockPush).toHaveBeenCalledWith('/dashboard');
  });

  it('로그인 실패 시 에러 메시지를 설정한다', async () => {
    mockLogin.mockRejectedValueOnce(new Error('로그인 실패'));

    const { result } = renderHook(() => useLoginForm());

    act(() => {
      result.current.setEmail('user@example.com');
      result.current.setPassword('12345678');
    });

    await act(async () => {
      await result.current.handleSubmit({ preventDefault: vi.fn() } as unknown as React.FormEvent);
    });

    expect(result.current.error).toBe('로그인 실패');
    expect(result.current.isSubmitting).toBe(false);
  });

  it('ApiErrorResponse 형태의 에러는 매핑된 메시지를 표시한다', async () => {
    mockLogin.mockRejectedValueOnce({
      code: 'INVALID_CREDENTIALS',
      message: '이메일 또는 비밀번호가 올바르지 않습니다.',
      timestamp: '2026-01-01T00:00:00Z',
    });

    const { result } = renderHook(() => useLoginForm());

    act(() => {
      result.current.setEmail('user@example.com');
      result.current.setPassword('wrong-password');
    });

    await act(async () => {
      await result.current.handleSubmit({ preventDefault: vi.fn() } as unknown as React.FormEvent);
    });

    expect(result.current.error).toBeTruthy();
    expect(result.current.isSubmitting).toBe(false);
  });

  it('isValid이 false이면 handleSubmit이 아무 동작도 하지 않는다', async () => {
    const { result } = renderHook(() => useLoginForm());

    await act(async () => {
      await result.current.handleSubmit({ preventDefault: vi.fn() } as unknown as React.FormEvent);
    });

    expect(mockLogin).not.toHaveBeenCalled();
  });
});
