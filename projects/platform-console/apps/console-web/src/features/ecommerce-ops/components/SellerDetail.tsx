'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { formatDateTime } from '@/shared/lib/datetime';
import {
  useSeller,
  useProvisionSeller,
  useSuspendSeller,
  useCloseSeller,
} from '../hooks/use-ecommerce-sellers';
import {
  sellerStatusTone,
  sellerActionsFor,
  type SellerLifecycleAction,
  type SellerDetail as SellerDetailType,
} from '../api/seller-types';
import { ConfirmDialog } from './ConfirmDialog';
import { DetailHeader } from './DetailHeader';

/**
 * ecommerce seller detail section (TASK-PC-FE-090 read + TASK-PC-FE-154
 * lifecycle — § 2.4.10.5 sellers). The console seller detail screen: shared
 * DetailHeader (title + status-conditional lifecycle actions + 목록 back).
 *
 * The seller domain has no update/delete (CRUD); its mutation surface is state
 * transitions (ADR-MONO-042): PENDING → provision; ACTIVE → suspend / close;
 * SUSPENDED → close; CLOSED → none. Each action is confirm-gated (ConfirmDialog)
 * and, on success, invalidates detail + list so the badge and the available
 * action set reflect the new state without a full reload.
 */

export interface SellerDetailProps {
  seller: SellerDetailType;
}

interface ActionMeta {
  label: string;
  confirmLabel: string;
  tone: 'default' | 'destructive';
  title: string;
  describe: (displayName: string) => string;
  failMsg: string;
}

const ACTION_META: Record<SellerLifecycleAction, ActionMeta> = {
  provision: {
    label: '프로비저닝',
    confirmLabel: '프로비저닝',
    tone: 'default',
    title: '셀러를 프로비저닝할까요?',
    describe: (n) => `"${n}" 셀러의 계정 생성을 재시도하여 활성화합니다.`,
    failMsg: '셀러를 프로비저닝하지 못했습니다.',
  },
  suspend: {
    label: '정지',
    confirmLabel: '정지',
    tone: 'destructive',
    title: '셀러를 정지할까요?',
    describe: (n) =>
      `"${n}" 셀러를 정지하고 연결된 계정을 잠급니다. 콘솔에서는 다시 활성화할 수 없습니다.`,
    failMsg: '셀러를 정지하지 못했습니다.',
  },
  close: {
    label: '폐점',
    confirmLabel: '폐점',
    tone: 'destructive',
    title: '셀러를 폐점할까요?',
    describe: (n) =>
      `"${n}" 셀러를 영구 폐점하고 연결된 계정을 비활성화합니다. 이 작업은 되돌릴 수 없습니다.`,
    failMsg: '셀러를 폐점하지 못했습니다.',
  },
};

export function SellerDetail({ seller }: SellerDetailProps) {
  const detailQ = useSeller(seller.sellerId, seller);
  const data = detailQ.data ?? seller;

  const provisionM = useProvisionSeller();
  const suspendM = useSuspendSeller();
  const closeM = useCloseSeller();
  const mutationFor = (a: SellerLifecycleAction) =>
    a === 'provision' ? provisionM : a === 'suspend' ? suspendM : closeM;

  const [pendingAction, setPendingAction] =
    useState<SellerLifecycleAction | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const actions = sellerActionsFor(data.status);
  const tone = sellerStatusTone(data.status);
  const activeMeta = pendingAction ? ACTION_META[pendingAction] : null;
  const activeM = pendingAction ? mutationFor(pendingAction) : null;

  function runAction() {
    if (!pendingAction) return;
    const meta = ACTION_META[pendingAction];
    setActionError(null);
    mutationFor(pendingAction).mutate(data.sellerId, {
      onSuccess: () => setPendingAction(null),
      onError: (e) => {
        const code = e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
        setActionError(messageForCode(code, meta.failMsg));
      },
    });
  }

  return (
    <section
      aria-labelledby="seller-detail-heading"
      data-testid="seller-detail"
    >
      <DetailHeader
        headingId="seller-detail-heading"
        title="셀러 상세"
        backHref="/ecommerce/sellers"
        backTestId="seller-detail-back"
        actions={actions.map((a) => (
          <Button
            key={a}
            variant="secondary"
            size="sm"
            onClick={() => {
              setActionError(null);
              setPendingAction(a);
            }}
            data-testid={`seller-action-${a}`}
          >
            {ACTION_META[a].label}
          </Button>
        ))}
      />

      <dl className="mb-6 grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
        <div>
          <dt className="text-muted-foreground">셀러 이름</dt>
          <dd data-testid="seller-detail-display-name">{data.displayName}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">상태</dt>
          <dd data-testid="seller-detail-status">
            <span
              className={`inline-block rounded px-2 py-0.5 text-xs font-medium ${tone.className}`}
            >
              {tone.label}
            </span>
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">셀러 ID</dt>
          <dd className="font-mono text-xs" data-testid="seller-detail-id">
            {data.sellerId}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">등록일</dt>
          <dd className="text-xs" data-testid="seller-detail-created-at">
            {formatDateTime(data.createdAt)}
          </dd>
        </div>
        {data.updatedAt != null && data.updatedAt !== '' && (
          <div>
            <dt className="text-muted-foreground">수정일</dt>
            <dd className="text-xs" data-testid="seller-detail-updated-at">
              {formatDateTime(data.updatedAt)}
            </dd>
          </div>
        )}
      </dl>

      <ConfirmDialog
        open={pendingAction !== null}
        title={activeMeta?.title ?? ''}
        description={activeMeta ? activeMeta.describe(data.displayName) : ''}
        confirmLabel={activeMeta?.confirmLabel ?? ''}
        tone={activeMeta?.tone ?? 'default'}
        pending={activeM?.isPending ?? false}
        errorMessage={actionError}
        onConfirm={runAction}
        onCancel={() => {
          setPendingAction(null);
          setActionError(null);
        }}
      />
    </section>
  );
}
