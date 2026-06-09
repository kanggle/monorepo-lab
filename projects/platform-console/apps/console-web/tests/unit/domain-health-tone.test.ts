import { describe, it, expect } from 'vitest';
import { healthTone } from '@/features/domain-health';
import type { Card } from '@/features/domain-health';

/** TASK-PC-FE-064 — shared 3-tone classification (summary band + catalog dot). */
describe('healthTone', () => {
  it('ok + UP → healthy', () => {
    expect(healthTone({ domain: 'iam', status: 'ok', data: { status: 'UP' } } as Card)).toBe(
      'healthy',
    );
  });

  it('ok + non-UP (DOWN / OUT_OF_SERVICE / UNKNOWN) → attention', () => {
    for (const s of ['DOWN', 'OUT_OF_SERVICE', 'UNKNOWN'] as const) {
      expect(
        healthTone({ domain: 'wms', status: 'ok', data: { status: s } } as Card),
      ).toBe('attention');
    }
  });

  it('degraded → unknown', () => {
    expect(
      healthTone({ domain: 'scm', status: 'degraded', reason: 'TIMEOUT' } as Card),
    ).toBe('unknown');
  });
});
