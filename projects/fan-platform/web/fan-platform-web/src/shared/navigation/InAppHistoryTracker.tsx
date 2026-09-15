'use client';

import { useEffect } from 'react';
import { usePathname } from 'next/navigation';
import { recordPathname } from './inAppHistory';

/**
 * 화면이 바뀔 때마다 경로를 `inAppHistory` 에 적는다. 아무것도 그리지 않고, 요청도 보내지 않는다.
 *
 * 🔴 `useSearchParams` 를 쓰지 않는다 — 그 훅은 정적 라우트를 Suspense 경계로 밀어내 페이지 대신
 *    fallback 이 나갈 수 있다. 판정에 필요한 것은 경로뿐이다(`inAppHistory.ts` § 쿼리).
 */
export function InAppHistoryTracker() {
  const pathname = usePathname();
  useEffect(() => {
    if (pathname) recordPathname(pathname);
  }, [pathname]);
  return null;
}
