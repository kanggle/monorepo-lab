'use client';

import { useRouter } from 'next/navigation';
import { cn } from '@/shared/lib/cn';

export const DASHBOARD_TABS = [
  { id: 'accounts', label: '계정 현황' },
  { id: 'security', label: '보안 이벤트' },
  { id: 'system', label: '시스템 상태' },
] as const;

export type DashboardTabId = (typeof DASHBOARD_TABS)[number]['id'];

interface DashboardTabsProps {
  activeTab: DashboardTabId;
}

export function DashboardTabs({ activeTab }: DashboardTabsProps) {
  const router = useRouter();

  return (
    <div className="flex border-b border-border" role="tablist">
      {DASHBOARD_TABS.map((tab) => (
        <button
          key={tab.id}
          role="tab"
          aria-selected={activeTab === tab.id}
          onClick={() => router.push(`/dashboards?tab=${tab.id}`)}
          className={cn(
            'px-4 py-2 text-sm transition-colors',
            activeTab === tab.id
              ? 'border-b-2 border-primary font-medium'
              : 'text-muted-foreground hover:text-foreground',
          )}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
}
