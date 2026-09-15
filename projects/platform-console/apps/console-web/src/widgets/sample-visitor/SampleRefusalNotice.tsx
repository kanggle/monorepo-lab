'use client';

import { useEffect, useState } from 'react';
import { messageForCode } from '@/shared/api/errors';
import { SAMPLE_READ_ONLY } from '@/shared/sample/codes';
import { subscribeSampleRefusal } from '@/shared/lib/sample-refusal';

/**
 * Says «샘플 화면에서는 실행되지 않습니다. 로그인하면 실제로 실행됩니다» once a
 * sample visitor's write has been refused (R1ⓐ).
 *
 * The signal comes from `shared/api/client.ts`, the single client backend entry
 * point; the copy comes from `messageForCode` — the one code → copy mapping.
 * Nothing here knows which screen or which action it was.
 */
export function SampleRefusalNotice() {
  const [refused, setRefused] = useState(false);

  useEffect(
    () =>
      subscribeSampleRefusal((code) => {
        if (code === SAMPLE_READ_ONLY) setRefused(true);
      }),
    [],
  );

  if (!refused) return null;
  return (
    <p
      role="alert"
      data-testid="sample-visitor-refusal"
      className="mt-1 font-medium text-destructive"
    >
      {messageForCode(SAMPLE_READ_ONLY)}
    </p>
  );
}
