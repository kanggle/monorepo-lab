export type PageItem = number | '...';

/**
 * 페이지네이션 말줄임 패턴으로 페이지 번호 배열을 생성한다.
 *
 * - 10페이지 미만: 전체 페이지 번호 반환
 * - 10페이지 이상: 첫 페이지, 마지막 페이지, 현재 페이지 ±2를 표시하고 나머지는 '...'으로 표시
 *
 * @param current 현재 페이지 (0-based)
 * @param total 전체 페이지 수
 * @returns 표시할 페이지 번호 배열 (0-based 숫자 또는 '...')
 */
export function buildPageNumbers(current: number, total: number): PageItem[] {
  if (total <= 0) return [];
  if (total < 10) {
    return Array.from({ length: total }, (_, i) => i);
  }

  const pages = new Set<number>();

  // 첫 페이지, 마지막 페이지
  pages.add(0);
  pages.add(total - 1);

  // 현재 페이지 ±2
  for (let i = current - 2; i <= current + 2; i++) {
    if (i >= 0 && i < total) {
      pages.add(i);
    }
  }

  const sorted = [...pages].sort((a, b) => a - b);
  const result: PageItem[] = [];

  for (let i = 0; i < sorted.length; i++) {
    if (i > 0 && sorted[i] - sorted[i - 1] > 1) {
      result.push('...');
    }
    result.push(sorted[i]);
  }

  return result;
}
