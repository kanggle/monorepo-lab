'use client';

import { useEffect } from 'react';
import { onCLS, onINP, onLCP, onTTFB, type Metric } from 'web-vitals';

function report(metric: Metric) {
  const body = JSON.stringify({
    name: metric.name,
    value: metric.value,
    rating: metric.rating,
    id: metric.id,
    navigationType: metric.navigationType,
    ts: Date.now(),
  });
  if (navigator.sendBeacon) {
    navigator.sendBeacon('/api/web-vitals', body);
  } else {
    void fetch('/api/web-vitals', { method: 'POST', body, keepalive: true, headers: { 'Content-Type': 'application/json' } });
  }
}

export function useWebVitals() {
  useEffect(() => {
    onCLS(report);
    onINP(report);
    onLCP(report);
    onTTFB(report);
  }, []);
}
