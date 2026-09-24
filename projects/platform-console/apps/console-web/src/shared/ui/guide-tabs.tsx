'use client';

import { useEffect, useId, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { cn } from '@/shared/lib/cn';

/**
 * TASK-PC-FE-298 — 가이드 화면 공용 탭(WAI-ARIA Tabs 패턴, 수동 활성화 아님 —
 * 화살표 키가 곧 선택한다: 탭 콘텐츠가 전부 정적이라 선택 비용이 0 이기 때문).
 *
 * - 모든 패널이 **DOM 에 렌더된다**(비활성 패널은 `hidden`). 서버 컴포넌트가 만든
 *   정적 콘텐츠를 그대로 받으므로 JS 가 없어도 첫 탭은 읽히고, 페이지 내 검색·기존
 *   테스트의 testid 조회도 그대로 성립한다.
 * - URL 해시(`#<tab id>`)가 탭 id 와 같으면 그 탭으로 연다 — 링크로 특정 탭을 공유할 수
 *   있게. 탭을 고르면 `history.replaceState` 로 해시만 바꾼다(스크롤 점프·히스토리 누적 없음).
 * - 키보드: ←/→ 이동(순환), Home/End 처음/끝. 활성 탭만 `tabIndex=0`(roving tabindex).
 *
 * 데이터 페치·권한 없음 — 샘플 방문자(ADR-MONO-074)에게도 그대로 보인다.
 */
export interface GuideTab {
  /** 탭 id — DOM id · URL 해시 · testid 접미사로 쓰인다(전역 유일해야 한다). */
  id: string;
  label: string;
  content: ReactNode;
}

export function GuideTabs({
  tabs,
  label,
  testid,
}: {
  tabs: GuideTab[];
  /** tablist 의 접근성 이름. */
  label: string;
  testid: string;
}) {
  const [active, setActive] = useState(tabs[0]?.id ?? '');
  const refs = useRef<Record<string, HTMLButtonElement | null>>({});
  const uid = useId();

  useEffect(() => {
    const hash = typeof window !== 'undefined' ? window.location.hash.slice(1) : '';
    if (hash && tabs.some((t) => t.id === hash)) setActive(hash);
    // 마운트 1회 — 이후 해시는 이 컴포넌트가 쓴다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const select = (id: string, focus = false) => {
    setActive(id);
    if (typeof window !== 'undefined' && window.history?.replaceState) {
      window.history.replaceState(null, '', `#${id}`);
    }
    if (focus) refs.current[id]?.focus();
  };

  const onKeyDown = (e: KeyboardEvent<HTMLButtonElement>, index: number) => {
    const last = tabs.length - 1;
    let next: number | null = null;
    if (e.key === 'ArrowRight') next = index === last ? 0 : index + 1;
    else if (e.key === 'ArrowLeft') next = index === 0 ? last : index - 1;
    else if (e.key === 'Home') next = 0;
    else if (e.key === 'End') next = last;
    if (next === null) return;
    e.preventDefault();
    select(tabs[next].id, true);
  };

  return (
    <div data-testid={testid}>
      <div
        role="tablist"
        aria-label={label}
        data-testid={`${testid}-list`}
        className="mb-8 flex flex-wrap gap-1 border-b border-border"
      >
        {tabs.map((t, i) => {
          const selected = t.id === active;
          return (
            <button
              key={t.id}
              ref={(el) => {
                refs.current[t.id] = el;
              }}
              type="button"
              role="tab"
              id={`${uid}-${t.id}-tab`}
              aria-selected={selected}
              aria-controls={t.id}
              tabIndex={selected ? 0 : -1}
              data-testid={`${testid}-tab-${t.id}`}
              onClick={() => select(t.id)}
              onKeyDown={(e) => onKeyDown(e, i)}
              className={cn(
                '-mb-px rounded-t-md border-b-2 px-3 py-2 text-sm transition-colors',
                'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                selected
                  ? 'border-foreground font-medium text-foreground'
                  : 'border-transparent text-muted-foreground hover:text-foreground',
              )}
            >
              {t.label}
            </button>
          );
        })}
      </div>
      {tabs.map((t) => (
        <div
          key={t.id}
          role="tabpanel"
          id={t.id}
          aria-labelledby={`${uid}-${t.id}-tab`}
          hidden={t.id !== active}
          tabIndex={0}
          data-testid={`${testid}-panel-${t.id}`}
          className="focus-visible:outline-none"
        >
          {t.content}
        </div>
      ))}
    </div>
  );
}
