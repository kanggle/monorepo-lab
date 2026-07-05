'use client';

import Link from 'next/link';
import { useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';

/**
 * Assign-operator-to-tenant form (TASK-PC-FE-157 / TASK-BE-347, ADR-MONO-024
 * D3-i — the delegation onboarding step). Collects a target operatorId and
 * hands it up; the parent gates the actual assignment behind the reason-capture
 * + confirm dialog (privilege-sensitive; the assign never fires one-click).
 *
 * A free-text operatorId (NOT a row action) because the target operator is
 * typically OUTSIDE the current active-tenant list scope — the whole point of
 * assigning is to bring an operator (an employee/partner from home tenant or
 * another tenant) INTO this tenant, so they would not yet appear in the
 * active-tenant-scoped operators table. The assignment always targets the
 * ACTIVE tenant (shown here); a SUPER_ADMIN switches the active tenant to
 * assign elsewhere. The producer is authoritative on existence / scope
 * (404 OPERATOR_NOT_FOUND / 403 TENANT_SCOPE_DENIED / 409 ALREADY_EXISTS map
 * inline in the confirm dialog).
 */

export interface AssignOperatorFormProps {
  /** The active tenant the assignment targets (display + path building). */
  activeTenant: string;
  /** Hand the trimmed operatorId up; the parent confirms + fires the assign. */
  onSubmitOperatorId: (operatorId: string) => void;
  pending?: boolean;
}

export function AssignOperatorForm({
  activeTenant,
  onSubmitOperatorId,
  pending = false,
}: AssignOperatorFormProps) {
  const inputId = useId();
  const [operatorId, setOperatorId] = useState('');

  const trimmed = operatorId.trim();
  const canSubmit = trimmed.length > 0 && !pending;

  function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!canSubmit) return;
    onSubmitOperatorId(trimmed);
    setOperatorId('');
  }

  return (
    <form
      onSubmit={submit}
      className="mb-8 grid gap-4 rounded-md border border-border bg-background p-4"
      aria-label="테넌트 배정"
      data-testid="assign-operator-form"
    >
      <div>
        <h2 className="text-lg font-semibold text-foreground">테넌트 배정</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          기존 운영자를 현재 테넌트{' '}
          <strong className="text-foreground">{activeTenant}</strong> 의{' '}
          <strong className="text-foreground">운영자로 편입</strong>합니다 (같은
          조직 내 위임 온보딩). 배정 후 &ldquo;조직 스코프&rdquo;로 부서 범위를
          좁힐 수 있습니다.
        </p>
        <p
          data-testid="assign-operator-partnership-hint"
          className="mt-2 rounded-md border border-border bg-muted/40 px-3 py-2 text-xs text-muted-foreground"
        >
          다른 회사(협력사) 조직이{' '}
          <strong className="text-foreground">자기 테넌트를 운영하면서</strong>{' '}
          이 테넌트의 일부(도메인·역할 slice)만 맡게 하려면 여기가 아니라{' '}
          <Link
            href="/partnerships"
            data-testid="assign-operator-partnership-link"
            className="font-medium text-primary underline underline-offset-2"
          >
            파트너십
          </Link>{' '}
          화면을 쓰세요. 파트너십은 관계 단위로 관리되어, 종료하거나 participant
          를 해제하면 그 접근이 즉시 회수됩니다(cascade offboarding).
        </p>
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <div>
          <label
            htmlFor={inputId}
            className="block text-sm font-medium text-foreground"
          >
            운영자 ID <span aria-hidden="true">*</span>
          </label>
          <input
            id={inputId}
            type="text"
            value={operatorId}
            onChange={(e) => setOperatorId(e.target.value)}
            placeholder="admin_operators.operator_id (UUID)"
            aria-required="true"
            data-testid="assign-operator-id"
            className="mt-1 w-96 max-w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>
        <Button
          type="submit"
          disabled={!canSubmit}
          data-testid="assign-operator-submit"
        >
          {pending ? '처리 중…' : '테넌트에 배정 (확인 필요)'}
        </Button>
      </div>
    </form>
  );
}
