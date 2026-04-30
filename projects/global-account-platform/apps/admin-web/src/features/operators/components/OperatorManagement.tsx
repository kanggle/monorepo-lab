'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/button';
import { RoleGuard } from '@/shared/ui/RoleGuard';
import type { OperatorRole } from '@/shared/api/admin-api';
import { OperatorList } from './OperatorList';
import { CreateOperatorDialog } from './CreateOperatorDialog';

interface Props {
  roles: OperatorRole[];
}

/**
 * Composite widget for the /operators console page. Hides all management
 * controls behind `SUPER_ADMIN` client-side, with the backend as authoritative
 * enforcer (operator.manage permission).
 */
export function OperatorManagement({ roles }: Props) {
  const [createOpen, setCreateOpen] = useState(false);

  return (
    <RoleGuard
      roles={roles}
      allow={['SUPER_ADMIN']}
      fallback={
        <p role="alert" className="text-sm text-muted-foreground">
          권한이 없습니다.
        </p>
      }
    >
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold">운영자 목록</h2>
          <Button type="button" variant="default" onClick={() => setCreateOpen(true)}>
            운영자 추가
          </Button>
        </div>
        <OperatorList />
      </div>
      <CreateOperatorDialog open={createOpen} onOpenChange={setCreateOpen} />
    </RoleGuard>
  );
}
