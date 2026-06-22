import { describe, it, expect } from 'vitest';
import {
  WEB_VITALS_THRESHOLDS,
  rateMetric,
  buildVitalsPayload,
} from '@/shared/lib/web-vitals';

describe('rateMetric', () => {
  it('rates values below the good bound as good', () => {
    expect(rateMetric('LCP', 2000)).toBe('good');
    expect(rateMetric('CLS', 0.05)).toBe('good');
    expect(rateMetric('INP', 150)).toBe('good');
    expect(rateMetric('TTFB', 500)).toBe('good');
  });

  it('rates values in the middle band as needs-improvement', () => {
    expect(rateMetric('LCP', 3000)).toBe('needs-improvement');
    expect(rateMetric('CLS', 0.2)).toBe('needs-improvement');
    expect(rateMetric('FCP', 2500)).toBe('needs-improvement');
  });

  it('rates values above the poor bound as poor', () => {
    expect(rateMetric('LCP', 5000)).toBe('poor');
    expect(rateMetric('CLS', 0.3)).toBe('poor');
    expect(rateMetric('INP', 600)).toBe('poor');
  });

  it('treats bound values as the better bucket (good ≤, poor ≤)', () => {
    expect(rateMetric('LCP', 2500)).toBe('good'); // exactly the good bound
    expect(rateMetric('LCP', 4000)).toBe('needs-improvement'); // exactly the poor bound
    expect(rateMetric('CLS', 0.1)).toBe('good');
    expect(rateMetric('CLS', 0.25)).toBe('needs-improvement');
  });

  it('returns null for metrics without a defined threshold', () => {
    expect(rateMetric('Next.js-hydration', 1234)).toBeNull();
    expect(rateMetric('unknown', 0)).toBeNull();
  });

  it('exposes all standard Core Web Vitals in the threshold table', () => {
    expect(Object.keys(WEB_VITALS_THRESHOLDS).sort()).toEqual(
      ['CLS', 'FCP', 'FID', 'INP', 'LCP', 'TTFB'].sort(),
    );
  });
});

describe('buildVitalsPayload', () => {
  it('rounds timing metrics to whole milliseconds and attaches rating + path', () => {
    const payload = buildVitalsPayload(
      { name: 'LCP', value: 2345.67, id: 'v1-abc', navigationType: 'navigate' },
      '/products',
    );
    expect(payload).toEqual({
      name: 'LCP',
      value: 2346,
      rating: 'good',
      id: 'v1-abc',
      navigationType: 'navigate',
      path: '/products',
    });
  });

  it('keeps 3 decimals for the unitless CLS metric', () => {
    const payload = buildVitalsPayload(
      { name: 'CLS', value: 0.12345, id: 'v1-cls', navigationType: 'navigate' },
      '/',
    );
    expect(payload.value).toBe(0.123);
    expect(payload.rating).toBe('needs-improvement');
  });

  it('passes through a null rating for custom Next.js metrics', () => {
    const payload = buildVitalsPayload(
      { name: 'Next.js-hydration', value: 88.9, id: 'v1-hyd' },
      '/cart',
    );
    expect(payload.value).toBe(89);
    expect(payload.rating).toBeNull();
    expect(payload.navigationType).toBeUndefined();
  });
});
