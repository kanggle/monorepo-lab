import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useAddressFormValidation } from '@/features/user/model/use-address-form-validation';

describe('useAddressFormValidation', () => {
  it('모든 필드가 유효하면 true를 반환하고 에러가 없다', () => {
    const { result } = renderHook(() => useAddressFormValidation());

    let isValid: boolean;
    act(() => {
      isValid = result.current.validate('집', '홍길동', '010-1234-5678', '12345', '서울시 강남구');
    });

    expect(isValid!).toBe(true);
    expect(result.current.fieldErrors).toEqual({});
  });

  it('빈 배송지명에 대해 에러를 반환한다', () => {
    const { result } = renderHook(() => useAddressFormValidation());

    let isValid: boolean;
    act(() => {
      isValid = result.current.validate('', '홍길동', '010-1234-5678', '12345', '서울시');
    });

    expect(isValid!).toBe(false);
    expect(result.current.fieldErrors.label).toBeDefined();
  });

  it('잘못된 연락처 형식에 대해 에러를 반환한다', () => {
    const { result } = renderHook(() => useAddressFormValidation());

    let isValid: boolean;
    act(() => {
      isValid = result.current.validate('집', '홍길동', 'abc', '12345', '서울시');
    });

    expect(isValid!).toBe(false);
    expect(result.current.fieldErrors.phone).toBeDefined();
  });

  it('clearFieldError로 특정 필드 에러를 제거한다', () => {
    const { result } = renderHook(() => useAddressFormValidation());

    act(() => {
      result.current.validate('', '', '', '', '');
    });
    expect(result.current.fieldErrors.label).toBeDefined();

    act(() => {
      result.current.clearFieldError('label');
    });
    expect(result.current.fieldErrors.label).toBeUndefined();
  });
});
