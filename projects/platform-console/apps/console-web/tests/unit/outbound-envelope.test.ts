import { describe, it, expect } from 'vitest';
import { OutboundOrderPageSchema } from '@/features/wms-outbound-ops/api/types';

/**
 * `OutboundOrderPageSchema` (built by `outboundPage(row)` + `normaliseOutboundPage`)
 * must accept BOTH the live outbound-service `PagedResponse` flat shape AND the
 * documented contract nested shape without throwing.
 *
 * Tests added for the already-implemented fix in TASK-PC-FE-057 that resolves
 * the production integration bug: the live service serialises
 *   `{ items, page:<int>, size, total }`
 * but the console previously expected
 *   `{ content, page:{ number, size, totalElements, totalPages } }`.
 */

/** A minimal but realistic order summary row (orderId is the only required field). */
const ROW = {
  orderId: 'o1',
  orderNo: 'ORD-1',
  status: 'PICKING',
  sagaState: 'RESERVED',
  lineCount: 1,
};

describe('OutboundOrderPageSchema — live-service flat shape (items/page:int/size/total)', () => {
  it('parses the flat { items, page:int, size, total } shape and normalises to { content, page:{} }', () => {
    const input = { items: [ROW], page: 0, size: 20, total: 1 };
    const result = OutboundOrderPageSchema.parse(input);

    expect(result.content).toHaveLength(1);
    expect(result.content[0].orderId).toBe('o1');
    expect(result.page.number).toBe(0);
    expect(result.page.size).toBe(20);
    expect(result.page.totalElements).toBe(1);
    expect(result.page.totalPages).toBe(1);
  });

  it('does NOT throw when parsing the flat shape (regression guard against the original bug)', () => {
    const input = { items: [ROW], page: 0, size: 20, total: 1 };
    expect(() => OutboundOrderPageSchema.parse(input)).not.toThrow();
  });
});

describe('OutboundOrderPageSchema — documented contract nested shape (content/page:{})', () => {
  it('still parses the documented { content, page:{number,size,totalElements,totalPages}, sort } shape unchanged', () => {
    const input = {
      content: [ROW],
      page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
      sort: 'updatedAt,desc',
    };
    const result = OutboundOrderPageSchema.parse(input);

    expect(result.content).toHaveLength(1);
    expect(result.content[0].orderId).toBe('o1');
    expect(result.page.number).toBe(0);
    expect(result.page.size).toBe(20);
    expect(result.page.totalElements).toBe(1);
    expect(result.page.totalPages).toBe(1);
    expect(result.sort).toBe('updatedAt,desc');
  });

  it('does NOT throw when parsing the documented shape (regression guard)', () => {
    const input = {
      content: [ROW],
      page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
      sort: 'updatedAt,desc',
    };
    expect(() => OutboundOrderPageSchema.parse(input)).not.toThrow();
  });
});

describe('OutboundOrderPageSchema — totalPages math (ceil(total/size))', () => {
  it('computes totalPages = ceil(total / size) correctly for a partially-filled last page', () => {
    // 5 items, page-size 2 → pages: [0,1], [2,3], [4] → 3 pages total
    const items = [
      { orderId: 'a' },
      { orderId: 'b' },
      { orderId: 'c' },
    ];
    const input = { items, page: 1, size: 2, total: 5 };
    const result = OutboundOrderPageSchema.parse(input);

    expect(result.page.totalPages).toBe(3);
    expect(result.page.number).toBe(1);
    expect(result.page.size).toBe(2);
    expect(result.page.totalElements).toBe(5);
    expect(result.content).toHaveLength(3);
  });

  it('totalPages is 1 (not 0) when total === size (exactly one full page)', () => {
    const input = { items: [ROW], page: 0, size: 1, total: 1 };
    const result = OutboundOrderPageSchema.parse(input);
    expect(result.page.totalPages).toBe(1);
  });

  it('totalPages is 1 when total is 0 (empty result set — guard against divide-by-zero / 0-page)', () => {
    const input = { items: [], page: 0, size: 20, total: 0 };
    const result = OutboundOrderPageSchema.parse(input);
    expect(result.page.totalPages).toBeGreaterThanOrEqual(1);
    expect(result.content).toHaveLength(0);
  });
});

describe('OutboundOrderPageSchema — row tolerance (passthrough + unknown fields)', () => {
  it('a row with only orderId (minimum required) is accepted', () => {
    const input = { items: [{ orderId: 'min-row' }], page: 0, size: 20, total: 1 };
    const result = OutboundOrderPageSchema.parse(input);
    expect(result.content[0].orderId).toBe('min-row');
  });

  it('unknown/extra row fields pass through (tolerant schema)', () => {
    const rowWithExtras = { ...ROW, futureField: 'v2', anotherUnknown: 42 };
    const input = { items: [rowWithExtras], page: 0, size: 20, total: 1 };
    const result = OutboundOrderPageSchema.parse(input);
    expect((result.content[0] as Record<string, unknown>).futureField).toBe('v2');
  });
});
