'use client';

import Link from 'next/link';
import type { MouseEvent } from 'react';
import { hasInAppHistory } from '@/shared/navigation/inAppHistory';

/**
 * «← 뒤로» — 앱 안에서 왔으면 브라우저 뒤로, 아니면 `fallbackHref` (TASK-FAN-FE-023).
 *
 * 🔴🔴 고정 링크도, 무조건 `history.back()` 도 아니다.
 *    · 고정 `/` 는 `/artists/{id}` 에서 들어온 방문자를 피드로 튕긴다.
 *    · 무조건 back 은 공유 링크로 들어온 방문자를 빈 탭이나 이전 사이트로 내보낸다.
 *    판정은 `inAppHistory.ts` 가 한다.
 *
 * 🔵 진짜 `<a href>` 다 — JS 가 없거나 ⌘/Ctrl/Shift/Alt/가운데 클릭이면 가로채지 않고 브라우저에 맡긴다.
 * 🔵 `useRouter` 를 안 쓴다 — 앱 라우터 컨텍스트 없이도 렌더된다(서버 컴포넌트 페이지 시험이 그대로 돈다).
 */
export function BackLink({ fallbackHref = '/' }: { fallbackHref?: string }) {
  function handleClick(e: MouseEvent<HTMLAnchorElement>) {
    if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
    if (!hasInAppHistory()) return;
    e.preventDefault();
    window.history.back();
  }

  return (
    <Link
      href={fallbackHref}
      onClick={handleClick}
      data-testid="back-link"
      className="mb-4 inline-flex items-center gap-1 py-1 text-sm font-medium text-ink-600 hover:text-brand-600 dark:text-ink-300"
    >
      <span aria-hidden="true">←</span>
      뒤로
    </Link>
  );
}
