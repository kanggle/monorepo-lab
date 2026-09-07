/**
 * 브라우저 안 검색 — 순수 함수(`filterRows`)와 그것을 쓰는 화면(`SampleTableCard`).
 *
 * 🔴 «검색이 서버로 안 나간다» 는 성질을 **fetch 호출 수**로 잰다. 「구현이 그렇게 생겼다」
 *    를 읽는 것과 「실제로 안 불렀다」를 재는 것은 다른 일이다.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ConsoleSampleTable } from '@demo/public-data';
import { filterRows, rowSearchText, SampleTableCard } from '@/features/demo-tour';

const TABLE: ConsoleSampleTable = {
  key: 'orders',
  title: '주문',
  description: '주문 상태 전이와 결제·배송 연계를 확인하는 화면입니다.',
  columns: [
    { key: 'orderNo', label: '주문번호' },
    { key: 'customer', label: '주문자' },
    { key: 'status', label: '상태' },
  ],
  rows: [
    { orderNo: 'DEMO-2026-0912', customer: '데모 고객 A', status: '결제완료' },
    { orderNo: 'DEMO-2026-0911', customer: '데모 고객 B', status: '출고대기' },
    { orderNo: 'DEMO-2026-0910', customer: '데모 고객 C', status: '배송중' },
  ],
};

describe('filterRows', () => {
  it('빈 질의는 전부 통과시킨다', () => {
    expect(filterRows(TABLE, '')).toHaveLength(3);
    expect(filterRows(TABLE, '   ')).toHaveLength(3);
  });

  it('부분 문자열로 거른다(대소문자 무시)', () => {
    expect(filterRows(TABLE, '0911')).toHaveLength(1);
    expect(filterRows(TABLE, 'demo-2026')).toHaveLength(3);
    expect(filterRows(TABLE, '출고')).toHaveLength(1);
  });

  it('안 맞으면 0건 — 그것은 «데이터 없음» 이 아니라 «검색 결과 없음» 이다', () => {
    expect(filterRows(TABLE, 'zzz')).toHaveLength(0);
  });

  it('🔴 **컬럼에 선언된 값만** 검색한다 — 안 그려지는 필드로 검색되면 안 된다', () => {
    const withHidden: ConsoleSampleTable = {
      ...TABLE,
      rows: [{ ...TABLE.rows[0], internalMemo: '내부메모' }],
    };
    expect(rowSearchText(withHidden, withHidden.rows[0])).not.toContain('내부메모');
    expect(filterRows(withHidden, '내부메모')).toHaveLength(0);
  });
});

describe('SampleTableCard', () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchSpy = vi.fn(() => {
      throw new Error('검색이 서버로 나갔습니다 — 이 화면에는 질의 표면이 없어야 합니다.');
    });
    vi.stubGlobal('fetch', fetchSpy);
  });
  afterEach(() => vi.unstubAllGlobals());

  it('표 설명을 항상 그린다(요구사항의 "각 화면의 기능 설명")', () => {
    render(<SampleTableCard table={TABLE} />);
    expect(screen.getByTestId('demo-table-orders-description').textContent).toBe(
      TABLE.description,
    );
  });

  it('입력하면 화면의 행이 줄고, **네트워크 호출은 0** 이다', async () => {
    render(<SampleTableCard table={TABLE} />);
    expect(screen.getAllByTestId(/^demo-row-orders-/)).toHaveLength(3);

    await userEvent.type(screen.getByTestId('demo-search-orders'), '0911');

    expect(screen.getAllByTestId(/^demo-row-orders-/)).toHaveLength(1);
    expect(screen.getByTestId('demo-grid-orders').textContent).toContain(
      'DEMO-2026-0911',
    );
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('0건은 «검색 결과가 없습니다» 로 말한다(«데이터가 없습니다» 가 아니다)', async () => {
    render(<SampleTableCard table={TABLE} />);
    await userEvent.type(screen.getByTestId('demo-search-orders'), 'zzz');
    const empty = screen.getByTestId('demo-empty-orders');
    expect(empty.textContent).toContain('검색 결과가 없습니다');
    expect(empty.textContent).not.toContain('데이터가 없습니다');
  });
});
