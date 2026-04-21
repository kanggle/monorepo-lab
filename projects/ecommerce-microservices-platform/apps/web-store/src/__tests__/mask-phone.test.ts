import { describe, it, expect } from 'vitest';
import { maskPhone } from '@/shared/lib/mask-phone';

describe('maskPhone', () => {
  it('11자리 전화번호(010-1234-5678)를 마스킹한다', () => {
    expect(maskPhone('01012345678')).toBe('010-****-5678');
  });

  it('하이픈이 포함된 11자리 전화번호를 마스킹한다', () => {
    expect(maskPhone('010-1234-5678')).toBe('010-****-5678');
  });

  it('10자리 전화번호(02-1234-5678)를 마스킹한다', () => {
    expect(maskPhone('0212345678')).toBe('021-***-5678');
  });

  it('하이픈이 포함된 10자리 전화번호를 마스킹한다', () => {
    expect(maskPhone('02-1234-5678')).toBe('021-***-5678');
  });

  it('8자리 이상 비표준 전화번호도 중간 자리를 마스킹한다', () => {
    const result = maskPhone('12345678');
    expect(result).toContain('****');
  });

  it('8자리 미만 전화번호는 원본 그대로 반환한다', () => {
    expect(maskPhone('1234')).toBe('1234');
  });

  it('빈 문자열은 그대로 반환한다', () => {
    expect(maskPhone('')).toBe('');
  });

  it('공백이 포함된 번호도 처리한다', () => {
    expect(maskPhone('010 1234 5678')).toBe('010-****-5678');
  });
});
