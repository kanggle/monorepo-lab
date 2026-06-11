import { describe, it, expect } from 'vitest';
import { formatRelative, TYPE_LABEL } from '@/features/notification/ui/labels';

const NOW = Date.parse('2026-06-11T12:00:00Z');

describe('formatRelative', () => {
  it('renders "방금" for sub-minute deltas', () => {
    expect(formatRelative('2026-06-11T11:59:30Z', NOW)).toBe('방금');
  });

  it('renders minutes', () => {
    expect(formatRelative('2026-06-11T11:55:00Z', NOW)).toBe('5분 전');
  });

  it('renders hours', () => {
    expect(formatRelative('2026-06-11T09:00:00Z', NOW)).toBe('3시간 전');
  });

  it('renders days', () => {
    expect(formatRelative('2026-06-09T12:00:00Z', NOW)).toBe('2일 전');
  });

  it('falls back to a locale date past a week', () => {
    const out = formatRelative('2026-05-01T12:00:00Z', NOW);
    expect(out).not.toMatch(/전$/);
    expect(out).not.toBe('방금');
  });

  it('returns empty string for an unparseable date', () => {
    expect(formatRelative('not-a-date', NOW)).toBe('');
  });
});

describe('TYPE_LABEL', () => {
  it('covers both notification kinds', () => {
    expect(TYPE_LABEL.WELCOME).toBeTruthy();
    expect(TYPE_LABEL.CANCELLATION).toBeTruthy();
  });
});
