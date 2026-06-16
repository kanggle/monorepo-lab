'use client';

import { useRef } from 'react';

/**
 * Tab strip for the ledger ops screen (TASK-PC-FE-106 split) — the ARIA
 * `tablist` with roving keyboard navigation (ArrowLeft / ArrowRight / Home /
 * End). The tab model (`TABS` / `TabKey`) lives here; the container owns the
 * `active` state (the per-tab queries gate on it) and passes it down with the
 * `onSelect` setter. `tabRefs` + the keydown handler are internal to this
 * widget.
 */
export const TABS = [
  { key: 'trial-balance', label: '시산표' },
  { key: 'periods', label: '회계 기간' },
  { key: 'entry', label: '분개 조회' },
  { key: 'reconciliation', label: '대사' },
  { key: 'account', label: '계정' },
  { key: 'lots', label: 'FX 포지션 로트' },
  { key: 'fx-rates', label: 'FX 환율 피드' },
] as const;
export type TabKey = (typeof TABS)[number]['key'];

export function LedgerOpsTabs({
  active,
  onSelect,
}: {
  active: TabKey;
  onSelect: (key: TabKey) => void;
}) {
  const tabRefs = useRef<Record<string, HTMLButtonElement | null>>({});

  function onTabKeyDown(e: React.KeyboardEvent, idx: number) {
    let next = idx;
    if (e.key === 'ArrowRight') next = (idx + 1) % TABS.length;
    else if (e.key === 'ArrowLeft') next = (idx - 1 + TABS.length) % TABS.length;
    else if (e.key === 'Home') next = 0;
    else if (e.key === 'End') next = TABS.length - 1;
    else return;
    e.preventDefault();
    const nextKey = TABS[next].key;
    onSelect(nextKey);
    tabRefs.current[nextKey]?.focus();
  }

  return (
    <div
      role="tablist"
      aria-label="ledger 운영 보기"
      className="mb-6 flex gap-1 border-b border-border"
    >
      {TABS.map((tab, idx) => {
        const selected = active === tab.key;
        return (
          <button
            key={tab.key}
            ref={(el) => {
              tabRefs.current[tab.key] = el;
            }}
            role="tab"
            id={`ledger-tab-${tab.key}`}
            aria-selected={selected}
            aria-controls={`ledger-panel-${tab.key}`}
            tabIndex={selected ? 0 : -1}
            data-testid={`ledger-tab-${tab.key}`}
            onClick={() => onSelect(tab.key)}
            onKeyDown={(e) => onTabKeyDown(e, idx)}
            className={
              selected
                ? 'rounded-t-md border-b-2 border-primary px-3 py-2 text-sm font-medium text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary'
                : 'rounded-t-md border-b-2 border-transparent px-3 py-2 text-sm text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary'
            }
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}
