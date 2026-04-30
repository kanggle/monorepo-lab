'use client';

import { useState } from 'react';
import { useAudit } from '../hooks/useAudit';
import type { AuditFilters } from '../hooks/useAudit';
import { Table, THead, TBody, TR, TH, TD } from '@/shared/ui/table';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import { Button } from '@/shared/ui/button';
import { formatDateTime } from '@/shared/lib/date';

export function AuditTable({ defaultSource }: { defaultSource?: AuditFilters['source'] }) {
  const [filters, setFilters] = useState<{ accountId: string; actionCode: string; from: string; to: string }>({
    accountId: '',
    actionCode: '',
    from: '',
    to: '',
  });
  const query = useAudit({ ...filters, source: defaultSource });

  return (
    <div className="flex flex-col gap-4">
      <form
        aria-label="audit-filters"
        className="grid grid-cols-4 gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          // useAudit reacts to state change via queryKey.
        }}
      >
        <div className="flex flex-col gap-1">
          <Label htmlFor="f-account">Account ID</Label>
          <Input id="f-account" value={filters.accountId} onChange={(e) => setFilters((f) => ({ ...f, accountId: e.target.value }))} />
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="f-code">Action Code</Label>
          <Input id="f-code" value={filters.actionCode} onChange={(e) => setFilters((f) => ({ ...f, actionCode: e.target.value }))} />
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="f-from">From</Label>
          <Input id="f-from" type="datetime-local" value={filters.from} onChange={(e) => setFilters((f) => ({ ...f, from: e.target.value }))} />
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="f-to">To</Label>
          <Input id="f-to" type="datetime-local" value={filters.to} onChange={(e) => setFilters((f) => ({ ...f, to: e.target.value }))} />
        </div>
        <div className="col-span-4">
          <Button type="submit">조회</Button>
        </div>
      </form>

      {query.isLoading ? <p>로딩 중...</p> : null}
      {query.isError ? <p role="alert" className="text-destructive">조회 실패</p> : null}
      {query.data ? (
        <Table>
          <THead>
            <TR>
              <TH>Source</TH>
              <TH>Action / Outcome</TH>
              <TH>Target / Account</TH>
              <TH>Operator</TH>
              <TH>Occurred At</TH>
            </TR>
          </THead>
          <TBody>
            {query.data.content.map((entry, idx) => {
              if (entry.source === 'admin') {
                return (
                  <TR key={entry.auditId ?? idx}>
                    <TD>admin</TD>
                    <TD>{entry.actionCode} ({entry.outcome})</TD>
                    <TD>{entry.targetId}</TD>
                    <TD>{entry.operatorId}</TD>
                    <TD>{formatDateTime(entry.occurredAt)}</TD>
                  </TR>
                );
              }
              if (entry.source === 'login_history') {
                return (
                  <TR key={entry.eventId ?? idx}>
                    <TD>login_history</TD>
                    <TD>LOGIN ({entry.outcome})</TD>
                    <TD>{entry.accountId}</TD>
                    <TD>—</TD>
                    <TD>{formatDateTime(entry.occurredAt)}</TD>
                  </TR>
                );
              }
              return (
                <TR key={entry.eventId ?? idx}>
                  <TD>suspicious</TD>
                  <TD>{entry.reasonCode ?? 'SUSPICIOUS'}</TD>
                  <TD>{entry.accountId ?? '—'}</TD>
                  <TD>—</TD>
                  <TD>{formatDateTime(entry.occurredAt)}</TD>
                </TR>
              );
            })}
          </TBody>
        </Table>
      ) : null}
    </div>
  );
}
