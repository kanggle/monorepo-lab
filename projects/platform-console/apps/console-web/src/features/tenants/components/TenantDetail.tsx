'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { DetailHeader } from '@/shared/ui/DetailHeader';
import { StatusBadge, type StatusTone } from '@/shared/ui/StatusBadge';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { formatDateTime } from '@/shared/lib/datetime';
import { useTenant, useUpdateTenant } from '../hooks/use-tenants';
import type { Tenant, UpdateTenantInput } from '../api/types';
import { TenantForm } from './TenantForm';
import { TenantConfirmDialog } from './TenantConfirmDialog';

function tenantStatusTone(status: string): StatusTone {
  return status === 'ACTIVE' ? 'success' : status === 'SUSPENDED' ? 'warning' : 'neutral';
}

function tenantTypeLabel(tenantType: string): string {
  if (tenantType === 'B2C_CONSUMER') return 'B2C (소비자)';
  if (tenantType === 'B2B_ENTERPRISE') return 'B2B (기업)';
  return tenantType;
}

/**
 * IAM tenant detail + inline-edit surface (TASK-PC-FE-226 — combined
 * "상세/수정" route per the task's single `[tenantId]/page.tsx` shape, NOT a
 * separate `/edit` route). dl field order follows the console convention
 * (`proj_console_ecommerce_detail_conventions`): 명칭 → 상태 → 식별자 → 날짜.
 *
 * Edit is a two-step flow (draft here via {@link TenantForm}, reason +
 * confirm via {@link TenantConfirmDialog}) — mirrors the create flow on
 * `TenantsScreen`; a tenant mutation never fires one-click.
 */
export interface TenantDetailProps {
  tenant: Tenant;
}

export function TenantDetail({ tenant }: TenantDetailProps) {
  const detailQ = useTenant(tenant.tenantId, tenant);
  const data = detailQ.data ?? tenant;

  const update = useUpdateTenant();
  const [editing, setEditing] = useState(false);
  const [pendingDraft, setPendingDraft] = useState<UpdateTenantInput | null>(
    null,
  );

  const updateError =
    update.error instanceof ApiError
      ? messageForCode((update.error as ApiError).code, update.error.message)
      : update.error
        ? '테넌트 수정에 실패했습니다.'
        : null;

  function openEdit() {
    update.reset();
    setEditing(true);
  }

  function submitDraft(draft: UpdateTenantInput) {
    setPendingDraft(draft);
  }

  function confirmUpdate(reason: string) {
    if (!pendingDraft) return;
    update.mutate(
      { tenantId: data.tenantId, input: pendingDraft, reason },
      {
        onSuccess: () => {
          setPendingDraft(null);
          setEditing(false);
        },
      },
    );
  }

  return (
    <section aria-labelledby="tenant-detail-heading" data-testid="tenant-detail">
      <DetailHeader
        headingId="tenant-detail-heading"
        title="테넌트 상세"
        backHref="/tenants"
        backTestId="tenant-detail-back"
        actions={
          !editing ? (
            <Button
              variant="secondary"
              onClick={openEdit}
              data-testid="tenant-detail-edit"
            >
              수정
            </Button>
          ) : undefined
        }
      />

      {!editing ? (
        <dl className="mb-6 grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
          <div className="col-span-2 sm:col-span-3">
            <dt className="text-muted-foreground">명칭</dt>
            <dd data-testid="tenant-detail-display-name" className="font-medium">
              {data.displayName}
            </dd>
          </div>
          <div>
            <dt className="text-muted-foreground">상태</dt>
            <dd data-testid="tenant-detail-status">
              <StatusBadge tone={tenantStatusTone(data.status)}>
                {data.status}
              </StatusBadge>
            </dd>
          </div>
          <div>
            <dt className="text-muted-foreground">테넌트 ID</dt>
            <dd className="font-mono text-xs" data-testid="tenant-detail-tenant-id">
              {data.tenantId}
            </dd>
          </div>
          <div>
            <dt className="text-muted-foreground">구분</dt>
            <dd data-testid="tenant-detail-tenant-type">
              {tenantTypeLabel(data.tenantType)}
            </dd>
          </div>
          <div>
            <dt className="text-muted-foreground">등록일</dt>
            <dd className="text-xs" data-testid="tenant-detail-created-at">
              {formatDateTime(data.createdAt)}
            </dd>
          </div>
          <div>
            <dt className="text-muted-foreground">수정일</dt>
            <dd className="text-xs" data-testid="tenant-detail-updated-at">
              {formatDateTime(data.updatedAt)}
            </dd>
          </div>
        </dl>
      ) : (
        <TenantForm
          mode="edit"
          tenant={data}
          onSubmitUpdateDraft={submitDraft}
          serverError={pendingDraft ? null : updateError}
          pending={update.isPending}
        />
      )}

      {editing && !pendingDraft && (
        <Button
          variant="ghost"
          size="sm"
          onClick={() => setEditing(false)}
          data-testid="tenant-edit-cancel"
        >
          취소
        </Button>
      )}

      {pendingDraft && (
        <TenantConfirmDialog
          title="테넌트를 수정할까요?"
          description={`"${data.displayName}" (${data.tenantId}) 테넌트의 정보를 변경합니다.`}
          confirmLabel="수정"
          pending={update.isPending}
          error={updateError}
          onConfirm={confirmUpdate}
          onCancel={() => setPendingDraft(null)}
        />
      )}
    </section>
  );
}
