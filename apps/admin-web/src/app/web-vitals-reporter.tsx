'use client';

import { useWebVitals } from '@/shared/observability/web-vitals';

export function WebVitalsReporter() {
  useWebVitals();
  return null;
}
