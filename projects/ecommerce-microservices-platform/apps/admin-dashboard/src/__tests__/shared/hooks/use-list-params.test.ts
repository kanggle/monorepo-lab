import { renderHook, act } from '@testing-library/react';
import { useListParams } from '@/shared/hooks/use-list-params';

const mockPush = vi.fn();
let mockSearchParams = new URLSearchParams();

vi.mock('next/navigation', () => ({
  useSearchParams: () => mockSearchParams,
  useRouter: () => ({ push: mockPush }),
}));

describe('useListParams', () => {
  beforeEach(() => {
    mockSearchParams = new URLSearchParams();
    mockPush.mockClear();
  });

  it('page 파라미터가 없으면 0을 반환한다', () => {
    const { result } = renderHook(() => useListParams());
    expect(result.current.page).toBe(0);
  });

  it('page 파라미터가 있으면 해당 숫자를 반환한다', () => {
    mockSearchParams = new URLSearchParams('page=3');
    const { result } = renderHook(() => useListParams());
    expect(result.current.page).toBe(3);
  });

  it('getParam은 해당 키의 값을 반환한다', () => {
    mockSearchParams = new URLSearchParams('status=ACTIVE&page=1');
    const { result } = renderHook(() => useListParams());
    expect(result.current.getParam('status')).toBe('ACTIVE');
    expect(result.current.getParam('missing')).toBeNull();
  });

  it('setFilter는 필터를 설정하고 page를 0으로 초기화한다', () => {
    mockSearchParams = new URLSearchParams('page=5');
    const { result } = renderHook(() => useListParams());

    act(() => {
      result.current.setFilter('status', 'PENDING');
    });

    expect(mockPush).toHaveBeenCalledWith('?page=0&status=PENDING');
  });

  it('setFilter에 undefined를 전달하면 해당 필터를 제거한다', () => {
    mockSearchParams = new URLSearchParams('status=ACTIVE&page=2');
    const { result } = renderHook(() => useListParams());

    act(() => {
      result.current.setFilter('status', undefined);
    });

    expect(mockPush).toHaveBeenCalledWith('?page=0');
  });

  it('setPage는 page 파라미터를 변경한다', () => {
    mockSearchParams = new URLSearchParams('status=ACTIVE&page=0');
    const { result } = renderHook(() => useListParams());

    act(() => {
      result.current.setPage(3);
    });

    expect(mockPush).toHaveBeenCalledWith('?status=ACTIVE&page=3');
  });

  it('buildPagination은 페이지네이션 정보를 반환한다', () => {
    mockSearchParams = new URLSearchParams('page=1');
    const { result } = renderHook(() => useListParams());

    const pagination = result.current.buildPagination({
      totalElements: 50,
      size: 20,
    });

    expect(pagination.page).toBe(1);
    expect(pagination.totalPages).toBe(3);
    expect(typeof pagination.onPageChange).toBe('function');
  });

  it('buildPagination에 undefined가 전달되면 totalPages는 0이다', () => {
    const { result } = renderHook(() => useListParams());

    const pagination = result.current.buildPagination(undefined);

    expect(pagination.totalPages).toBe(0);
  });
});
