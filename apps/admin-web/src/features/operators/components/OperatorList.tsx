'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/button';
import { Badge } from '@/shared/ui/badge';
import { Table, THead, TBody, TR, TH, TD } from '@/shared/ui/table';
import { formatDateTime } from '@/shared/lib/date';
import { useOperatorSession } from '@/features/auth/hooks/useOperatorSession';
import type { Operator } from '@/shared/api/admin-api';
import { useOperatorList } from '../hooks/useOperatorList';
import { EditRolesDialog } from './EditRolesDialog';
import { ChangeStatusDialog } from './ChangeStatusDialog';

export function OperatorList() {
  const { data, isLoading, isError } = useOperatorList();
  const session = useOperatorSession();
  const currentOperatorId = session.data?.operatorId;

  const [rolesOpen, setRolesOpen] = useState(false);
  const [statusOpen, setStatusOpen] = useState(false);
  const [selected, setSelected] = useState<Operator | null>(null);

  if (isLoading) return <p className="text-sm text-muted-foreground">목록을 불러오는 중...</p>;
  if (isError || !data) {
    return (
      <p role="alert" className="text-sm text-destructive">
        운영자 목록을 불러오지 못했습니다.
      </p>
    );
  }

  const rows = data.content;

  if (rows.length === 0) {
    return <p className="text-sm text-muted-foreground">등록된 운영자가 없습니다.</p>;
  }

  function openEditRoles(op: Operator) {
    setSelected(op);
    setRolesOpen(true);
  }

  function openChangeStatus(op: Operator) {
    setSelected(op);
    setStatusOpen(true);
  }

  return (
    <>
      <Table>
        <THead>
          <TR>
            <TH>이메일</TH>
            <TH>표시 이름</TH>
            <TH>역할</TH>
            <TH>상태</TH>
            <TH>최근 로그인</TH>
            <TH>작업</TH>
          </TR>
        </THead>
        <TBody>
          {rows.map((op) => {
            const isSelf = currentOperatorId === op.operatorId;
            const isActive = op.status === 'ACTIVE';
            const statusLabel = isActive ? '정지' : '활성화';
            return (
              <TR key={op.operatorId}>
                <TD>{op.email}</TD>
                <TD>{op.displayName}</TD>
                <TD>
                  {op.roles.length === 0 ? (
                    <span className="text-xs text-muted-foreground">역할 없음</span>
                  ) : (
                    <span className="flex flex-wrap gap-1">
                      {op.roles.map((role) => (
                        <Badge key={role}>{role}</Badge>
                      ))}
                    </span>
                  )}
                </TD>
                <TD>
                  <Badge>{op.status}</Badge>
                </TD>
                <TD>{formatDateTime(op.lastLoginAt ?? undefined)}</TD>
                <TD>
                  <span className="flex gap-2">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => openEditRoles(op)}
                    >
                      역할 변경
                    </Button>
                    <Button
                      type="button"
                      variant="default"
                      size="sm"
                      onClick={() => openChangeStatus(op)}
                      disabled={isActive && isSelf}
                      title={
                        isActive && isSelf
                          ? '본인 계정은 정지할 수 없습니다.'
                          : undefined
                      }
                    >
                      {statusLabel}
                    </Button>
                  </span>
                </TD>
              </TR>
            );
          })}
        </TBody>
      </Table>

      {selected ? (
        <>
          <EditRolesDialog
            open={rolesOpen}
            onOpenChange={setRolesOpen}
            operator={selected}
          />
          <ChangeStatusDialog
            open={statusOpen}
            onOpenChange={setStatusOpen}
            operator={selected}
            nextStatus={selected.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'}
          />
        </>
      ) : null}
    </>
  );
}
