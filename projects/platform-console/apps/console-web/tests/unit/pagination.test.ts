import { describe, it, expect } from 'vitest';
import { clampPageSize } from '@/shared/lib/pagination';

/**
 * TASK-PC-FE-116 — page-size clamp to [1, max] with a default fallback.
 * Off-by-one / unguarded boundaries here drive bad page requests; pinned here.
 */
describe('clampPageSize', () => {
  it('falls back to the default when size is undefined', () => {
    expect(clampPageSize(undefined, 20, 100)).toBe(20);
  });

  it('passes through a value already within [1, max]', () => {
    expect(clampPageSize(50, 20, 100)).toBe(50);
  });

  it('clamps down to max when the request exceeds it', () => {
    expect(clampPageSize(500, 20, 100)).toBe(100);
  });

  it('clamps up to 1 for zero', () => {
    expect(clampPageSize(0, 20, 100)).toBe(1);
  });

  it('clamps up to 1 for a negative request', () => {
    expect(clampPageSize(-5, 20, 100)).toBe(1);
  });

  it('clamps the default itself when it exceeds max', () => {
    // undefined → defaultSize (200) → clamped to max (100).
    expect(clampPageSize(undefined, 200, 100)).toBe(100);
  });
});
