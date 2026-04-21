import { alertError } from '@/shared/lib/alert-error';

describe('alertError', () => {
  let alertSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
  });

  afterEach(() => {
    alertSpy.mockRestore();
  });

  it('Error 인스턴스이면 error.message를 표시한다', () => {
    alertError(new Error('네트워크 오류'), '기본 메시지');
    expect(alertSpy).toHaveBeenCalledWith('네트워크 오류');
  });

  it('Error가 아닌 값이면 fallback 메시지를 표시한다', () => {
    alertError(null, '기본 메시지');
    expect(alertSpy).toHaveBeenCalledWith('기본 메시지');
  });

  it('undefined에도 fallback 메시지를 표시한다', () => {
    alertError(undefined, '실패했습니다');
    expect(alertSpy).toHaveBeenCalledWith('실패했습니다');
  });
});
