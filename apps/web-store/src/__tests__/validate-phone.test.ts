import { describe, it, expect } from 'vitest';
import { isValidPhone } from '@/shared/lib/validate-phone';

describe('isValidPhone', () => {
  it('010으로 시작하는 11자리 전화번호는 유효하다', () => {
    expect(isValidPhone('01012345678')).toBe(true);
  });

  it('하이픈이 포함된 010 전화번호는 유효하다', () => {
    expect(isValidPhone('010-1234-5678')).toBe(true);
  });

  it('016으로 시작하는 전화번호는 유효하다', () => {
    expect(isValidPhone('01612345678')).toBe(true);
  });

  it('017으로 시작하는 전화번호는 유효하다', () => {
    expect(isValidPhone('017-123-4567')).toBe(true);
  });

  it('018으로 시작하는 전화번호는 유효하다', () => {
    expect(isValidPhone('01812345678')).toBe(true);
  });

  it('019으로 시작하는 전화번호는 유효하다', () => {
    expect(isValidPhone('01912345678')).toBe(true);
  });

  it('011으로 시작하는 전화번호는 유효하다', () => {
    expect(isValidPhone('011-234-5678')).toBe(true);
  });

  it('중간 번호가 3자리인 전화번호도 유효하다', () => {
    expect(isValidPhone('010-123-4567')).toBe(true);
  });

  it('하이픈 없이 중간 3자리인 10자리 번호도 유효하다', () => {
    expect(isValidPhone('0101234567')).toBe(true);
  });

  it('앞뒤 공백이 있어도 유효하다', () => {
    expect(isValidPhone('  010-1234-5678  ')).toBe(true);
  });

  it('012로 시작하는 전화번호는 유효하지 않다', () => {
    expect(isValidPhone('01212345678')).toBe(false);
  });

  it('02로 시작하는 일반 전화번호는 유효하지 않다', () => {
    expect(isValidPhone('0212345678')).toBe(false);
  });

  it('숫자가 아닌 문자가 포함되면 유효하지 않다', () => {
    expect(isValidPhone('010-abcd-5678')).toBe(false);
  });

  it('빈 문자열은 유효하지 않다', () => {
    expect(isValidPhone('')).toBe(false);
  });

  it('공백만 있는 문자열은 유효하지 않다', () => {
    expect(isValidPhone('   ')).toBe(false);
  });

  it('뒷자리가 3자리이면 유효하지 않다', () => {
    expect(isValidPhone('010-1234-567')).toBe(false);
  });

  it('뒷자리가 5자리이면 유효하지 않다', () => {
    expect(isValidPhone('010-1234-56789')).toBe(false);
  });

  it('중간에 공백이 포함되면 유효하지 않다', () => {
    expect(isValidPhone('010 1234 5678')).toBe(false);
  });
});
