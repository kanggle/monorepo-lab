import { describe, it, expect } from 'vitest';
import { newIdempotencyKey } from '@/shared/lib/idempotency';

describe('newIdempotencyKey', () => {
  it('returns a UUID-shaped string', () => {
    const key = newIdempotencyKey();
    expect(key).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i);
  });
});
