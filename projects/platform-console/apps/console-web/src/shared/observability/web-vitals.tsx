'use client';

import { useEffect } from 'react';
import { onCLS, onINP, onLCP, onTTFB, type Metric } from 'web-vitals';

/**
 * Reports Core Web Vitals to `/api/web-vitals` (frontend-app.md
 * § Observability — LCP/INP/CLS/TTFB). Uses `sendBeacon` so reporting does
 * not delay navigation.
 */
function report(metric: Metric) {
  const body = JSON.stringify({
    name: metric.name,
    value: metric.value,
    rating: metric.rating,
    id: metric.id,
    navigationType: metric.navigationType,
    ts: Date.now(),
  });
  if (typeof navigator !== 'undefined' && navigator.sendBeacon) {
    navigator.sendBeacon('/api/web-vitals', body);
  } else {
    void fetch('/api/web-vitals', {
      method: 'POST',
      body,
      keepalive: true,
      headers: { 'Content-Type': 'application/json' },
    });
  }
}

export function WebVitals() {
  useEffect(() => {
    onCLS(report);
    onINP(report);
    onLCP(report);
    onTTFB(report);
  }, []);
  return null;
}
