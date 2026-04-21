import { buildPageNumbers } from '@repo/utils';

describe('buildPageNumbers', () => {
  it('totalPages가 0이면 빈 배열을 반환한다', () => {
    expect(buildPageNumbers(0, 0)).toEqual([]);
  });

  it('totalPages가 1이면 [0]을 반환한다', () => {
    expect(buildPageNumbers(0, 1)).toEqual([0]);
  });

  it('10페이지 미만이면 전체 페이지 번호를 반환한다', () => {
    expect(buildPageNumbers(0, 5)).toEqual([0, 1, 2, 3, 4]);
    expect(buildPageNumbers(3, 9)).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8]);
  });

  it('정확히 9페이지일 때 전체 페이지 번호를 반환한다', () => {
    expect(buildPageNumbers(0, 9)).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8]);
  });

  it('정확히 10페이지일 때 말줄임을 적용한다', () => {
    const result = buildPageNumbers(0, 10);
    expect(result).toEqual([0, 1, 2, '...', 9]);
  });

  it('첫 페이지 근처에서 올바른 말줄임을 표시한다', () => {
    // current=0, total=100 → [0, 1, 2, '...', 99]
    expect(buildPageNumbers(0, 100)).toEqual([0, 1, 2, '...', 99]);

    // current=1, total=100 → [0, 1, 2, 3, '...', 99]
    expect(buildPageNumbers(1, 100)).toEqual([0, 1, 2, 3, '...', 99]);

    // current=2, total=100 → [0, 1, 2, 3, 4, '...', 99]
    expect(buildPageNumbers(2, 100)).toEqual([0, 1, 2, 3, 4, '...', 99]);
  });

  it('마지막 페이지 근처에서 올바른 말줄임을 표시한다', () => {
    // current=99, total=100 → [0, '...', 97, 98, 99]
    expect(buildPageNumbers(99, 100)).toEqual([0, '...', 97, 98, 99]);

    // current=98, total=100 → [0, '...', 96, 97, 98, 99]
    expect(buildPageNumbers(98, 100)).toEqual([0, '...', 96, 97, 98, 99]);

    // current=97, total=100 → [0, '...', 95, 96, 97, 98, 99]
    expect(buildPageNumbers(97, 100)).toEqual([0, '...', 95, 96, 97, 98, 99]);
  });

  it('중간 페이지에서 양쪽 말줄임을 표시한다', () => {
    // current=50, total=100 → [0, '...', 48, 49, 50, 51, 52, '...', 99]
    expect(buildPageNumbers(50, 100)).toEqual([0, '...', 48, 49, 50, 51, 52, '...', 99]);
  });

  it('current=4, total=100일 때 양쪽 말줄임이 표시된다', () => {
    // current=4 → pages: 0, 2, 3, 4, 5, 6, 99
    expect(buildPageNumbers(4, 100)).toEqual([0, '...', 2, 3, 4, 5, 6, '...', 99]);
  });

  it('current=95, total=100일 때 양쪽 말줄임이 표시된다', () => {
    // current=95 → pages: 0, 93, 94, 95, 96, 97, 99
    expect(buildPageNumbers(95, 100)).toEqual([0, '...', 93, 94, 95, 96, 97, '...', 99]);
  });

  it('current=3, total=20일 때 왼쪽이 첫 페이지와 연결된다', () => {
    // current=3 → pages: 0, 1, 2, 3, 4, 5, 19
    expect(buildPageNumbers(3, 20)).toEqual([0, 1, 2, 3, 4, 5, '...', 19]);
  });
});
