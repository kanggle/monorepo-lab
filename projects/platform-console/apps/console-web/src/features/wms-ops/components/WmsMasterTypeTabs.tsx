'use client';

import { cn } from '@/shared/lib/cn';
import type { RefType } from '../api/types';
import { REF_TYPE_LABELS, REF_TYPE_TABS } from './wms-master-helpers';

/**
 * The ref-**type** tab selector for {@link WmsMasterScreen} (TASK-PC-FE-223)
 * — one tab per producer-supported `{type}` (`admin-service-api.md` § 1.7:
 * warehouses|zones|locations|skus|lots|partners). Switching a tab re-queries
 * that type's page-0 (the container resets filters/page on switch). Pure
 * presentation — the container owns the selected type.
 */
export interface WmsMasterTypeTabsProps {
  selected: RefType;
  onSelect: (type: RefType) => void;
}

export function WmsMasterTypeTabs({
  selected,
  onSelect,
}: WmsMasterTypeTabsProps) {
  return (
    <div
      role="tablist"
      aria-label="마스터 참조 데이터 유형"
      className="mb-4 flex flex-wrap gap-1 border-b border-border"
      data-testid="wms-master-type-tabs"
    >
      {REF_TYPE_TABS.map((type) => {
        const active = type === selected;
        return (
          <button
            key={type}
            type="button"
            role="tab"
            aria-selected={active}
            data-testid={`wms-master-tab-${type}`}
            onClick={() => onSelect(type)}
            className={cn(
              'rounded-t-md px-3 py-2 text-sm transition-colors',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
              active
                ? 'border-b-2 border-primary font-medium text-foreground'
                : 'text-muted-foreground hover:text-foreground',
            )}
          >
            {REF_TYPE_LABELS[type]}
          </button>
        );
      })}
    </div>
  );
}
