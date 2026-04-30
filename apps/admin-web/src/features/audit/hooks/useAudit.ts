'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { AuditPageSchema, type AuditPage } from '@/shared/api/admin-api';

export interface AuditFilters {
  accountId?: string;
  actionCode?: string;
  from?: string;
  to?: string;
  source?: 'admin' | 'login_history' | 'suspicious';
  page?: number;
  size?: number;
}

export function useAudit(filters: AuditFilters) {
  const qs = new URLSearchParams();
  Object.entries(filters).forEach(([k, v]) => {
    if (v !== undefined && v !== '') qs.set(k, String(v));
  });
  return useQuery<AuditPage>({
    queryKey: ['audit', filters],
    queryFn: async () => {
      const data = await apiClient.get<unknown>(`/api/admin/audit?${qs.toString()}`);
      return AuditPageSchema.parse(data);
    },
  });
}
