'use client';

import { useMutation } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { DataExportResponseSchema, type DataExportResponse } from '@/shared/api/admin-api';

export interface ExportVariables {
  accountId: string;
}

export function useExportAccount() {
  return useMutation<DataExportResponse, Error, ExportVariables>({
    mutationFn: async ({ accountId }) => {
      const data = await apiClient.get<unknown>(
        `/api/admin/accounts/${accountId}/export`,
        { operatorReason: 'account.export' },
      );
      return DataExportResponseSchema.parse(data);
    },
  });
}
