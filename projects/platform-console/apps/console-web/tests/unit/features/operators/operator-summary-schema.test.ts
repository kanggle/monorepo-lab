import { describe, it, expect } from 'vitest';
import { OperatorSummarySchema } from '@/features/operators/api/types';

/**
 * TASK-PC-FE-018 — parse-check for the BE-308 extension to
 * `GET /api/admin/operators` items. The producer adds an optional
 * `operatorContext: { defaultAccountId?: string }` field per item with
 * field-level `@JsonInclude.NON_NULL` discipline:
 *
 *   - Operators with NO value → producer omits `operatorContext` entirely.
 *     The consumer must accept the field's absence (backwards-compat with
 *     pre-BE-308 main + present on operators with NULL column).
 *   - Operators with a value → producer emits
 *     `{ "operatorContext": { "defaultAccountId": "<uuid>" } }`. The
 *     consumer reads `defaultAccountId` to pre-populate the dialog.
 *
 * Both shapes parse. The carrier itself is strict on the key set — a
 * forward-compat new nested key (e.g. wmsDefaultWarehouseId) would
 * surface as a parse-time signal, not a silent acceptance.
 */
describe('OperatorSummarySchema (TASK-PC-FE-018 BE-308 extension)', () => {
  const baseItem = {
    operatorId: 'op-1',
    email: 'one@example.com',
    displayName: 'One',
    status: 'ACTIVE',
    roles: ['SUPPORT_LOCK'],
    totpEnrolled: false,
    lastLoginAt: null,
    createdAt: '2026-01-01T00:00:00Z',
  };

  it('parses an item WITHOUT operatorContext (producer omitted via @JsonInclude.NON_NULL)', () => {
    const parsed = OperatorSummarySchema.parse(baseItem);
    expect(parsed.operatorContext).toBeUndefined();
  });

  it('parses an item WITH operatorContext.defaultAccountId set', () => {
    const parsed = OperatorSummarySchema.parse({
      ...baseItem,
      operatorContext: { defaultAccountId: 'acc-uuid-7' },
    });
    expect(parsed.operatorContext?.defaultAccountId).toBe('acc-uuid-7');
  });

  it('parses an item with an empty operatorContext object (defensive — should not happen post BE-308 but tolerated)', () => {
    const parsed = OperatorSummarySchema.parse({
      ...baseItem,
      operatorContext: {},
    });
    expect(parsed.operatorContext).toBeDefined();
    expect(parsed.operatorContext?.defaultAccountId).toBeUndefined();
  });
});
