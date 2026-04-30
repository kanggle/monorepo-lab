'use client';

import { useState } from 'react';
import { AuditTable } from './AuditTable';
import type { AuditFilters } from '../hooks/useAudit';

const TABS: { label: string; source?: AuditFilters['source'] }[] = [
  { label: '전체' },
  { label: '운영자 액션', source: 'admin' },
  { label: '로그인 이력', source: 'login_history' },
  { label: '의심 이벤트', source: 'suspicious' },
];

export function AuditTabs() {
  const [activeSource, setActiveSource] = useState<AuditFilters['source'] | undefined>(undefined);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex border-b border-border">
        {TABS.map((tab) => (
          <button
            key={tab.label}
            onClick={() => setActiveSource(tab.source)}
            className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors ${
              activeSource === tab.source
                ? 'border-primary text-foreground'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>
      {activeSource !== undefined && (
        <p className="text-sm text-muted-foreground">
          Account ID를 입력해야 데이터가 조회됩니다.
        </p>
      )}
      <AuditTable defaultSource={activeSource} />
    </div>
  );
}
