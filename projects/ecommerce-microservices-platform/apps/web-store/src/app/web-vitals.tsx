'use client';

import { useReportWebVitals } from 'next/web-vitals';
import { buildVitalsPayload } from '@/shared/lib';

/**
 * Core Web Vitals reporter — the field-data half of the web-store performance
 * baseline. Mounted once in the root layout; renders nothing.
 *
 * - **dev**: logs a one-line `[web-vitals]` summary to the console so the
 *   baseline is observable while iterating locally.
 * - **prod**: fire-and-forget beacon to `/api/vitals` (sendBeacon survives
 *   page unload; `fetch keepalive` is the fallback). The server route logs a
 *   structured line ready to pipe into Vector/VictoriaMetrics later.
 *
 * Uses Next's built-in `useReportWebVitals` (no extra dependency); rating +
 * payload shaping live in the pure, tested `@/shared/lib/web-vitals`.
 */
export function WebVitals() {
  useReportWebVitals((metric) => {
    const payload = buildVitalsPayload(metric, window.location.pathname);

    if (process.env.NODE_ENV !== 'production') {
      // eslint-disable-next-line no-console
      console.log(
        `[web-vitals] ${payload.name}=${payload.value} (${payload.rating ?? 'n/a'}) @ ${payload.path}`,
      );
      return;
    }

    const body = JSON.stringify(payload);
    if (typeof navigator.sendBeacon === 'function') {
      navigator.sendBeacon('/api/vitals', body);
    } else {
      void fetch('/api/vitals', { body, method: 'POST', keepalive: true });
    }
  });

  return null;
}
