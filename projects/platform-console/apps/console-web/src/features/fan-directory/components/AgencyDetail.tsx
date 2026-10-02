'use client';

import { useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog';
import { DetailHeader } from '@/shared/ui/DetailHeader';
import { formatDateTime } from '@/shared/lib/datetime';
import {
  useAgency,
  useArchiveAgency,
  useLinkAgencyStoreSeller,
  useRenameAgency,
} from '../hooks/use-fan-directory';
import { agencyStatusTone, errorCode, fanErrorMessage } from '../lib/fan-messages';
import type { Agency } from '../api/fan-types';
import { FanError, FanField, inputCls, labelCls } from './fan-ui';

/** The one producer code that means «the store could not be asked — nothing saved». */
export const STORE_SELLER_LOOKUP_UNAVAILABLE = 'STORE_SELLER_LOOKUP_UNAVAILABLE';

/**
 * Agency detail (TASK-MONO-751 — ADR-MONO-079 D1/D2): rename · archive · store-seller link.
 *
 * 🔴 The store-seller link is shown and usable, but artist-service currently refuses EVERY
 *    value with `503 STORE_SELLER_LOOKUP_UNAVAILABLE` — its store lookup has no transport yet
 *    (`UnwiredStoreSellerDirectory`, TASK-MONO-748 → TASK-MONO-759). The field is NOT hidden:
 *    hiding it would make «the console cannot link a seller» look like «there is no such
 *    feature». Instead the section says so up front, and a refused attempt renders its own
 *    state (`fan-agency-seller-lookup-unavailable`) that says nothing was saved. A definite
 *    store answer (`STORE_SELLER_NOT_FOUND` / `STORE_SELLER_CLOSED`, 422) renders as the
 *    validation error it is (AC-2). Clearing a link works today (no store lookup).
 */
export function AgencyDetail({ agency: initial }: { agency: Agency }) {
  const q = useAgency(initial.id, initial);
  const agency = q.data ?? initial;
  const archived = agency.status === 'ARCHIVED';

  return (
    <section aria-labelledby="fan-agency-detail-heading" className="space-y-8">
      <DetailHeader
        headingId="fan-agency-detail-heading"
        title="소속사 상세"
        backHref="/fan/agencies"
        backTestId="fan-agency-detail-back"
      />
      <dl data-testid="fan-agency-detail" className="max-w-2xl">
        <FanField label="이름">
          <span data-testid="fan-agency-detail-name">{agency.name}</span>
        </FanField>
        <FanField label="상태">
          <StatusBadge tone={agencyStatusTone(agency.status)}>
            <span data-testid="fan-agency-detail-status">{agency.status}</span>
          </StatusBadge>
        </FanField>
        <FanField label="ID">
          <code className="text-xs">{agency.id}</code>
        </FanField>
        <FanField label="등록일">{agency.createdAt ? formatDateTime(agency.createdAt) : '—'}</FanField>
      </dl>

      <StoreSellerSection agency={agency} archived={archived} />
      {!archived && <RenameSection agency={agency} />}
      {!archived && <ArchiveSection agency={agency} />}
      {archived && (
        <p className="text-sm text-muted-foreground" data-testid="fan-agency-archived-note">
          보관된 소속사입니다. 이름 변경 · 셀러 연결 · 새 소속을 받지 않습니다. 기존 소속은
          그대로 표시됩니다.
        </p>
      )}
    </section>
  );
}

function StoreSellerSection({ agency, archived }: { agency: Agency; archived: boolean }) {
  const inputId = useId();
  const [value, setValue] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [lookupUnavailable, setLookupUnavailable] = useState(false);
  const link = useLinkAgencyStoreSeller(agency.id);
  const trimmed = value.trim();

  function submit(storeSellerId: string | null) {
    setError(null);
    setLookupUnavailable(false);
    link.mutate(storeSellerId, {
      onSuccess: () => setValue(''),
      onError: (err) => {
        if (errorCode(err) === STORE_SELLER_LOOKUP_UNAVAILABLE) {
          setLookupUnavailable(true);
          return;
        }
        setError(fanErrorMessage(err, '셀러 연결을 바꾸지 못했습니다.'));
      },
    });
  }

  return (
    <section aria-labelledby="fan-agency-seller-heading" className="max-w-2xl space-y-3">
      <h2 id="fan-agency-seller-heading" className="text-lg font-medium">
        스토어 셀러 연결
      </h2>
      <p className="text-sm text-muted-foreground">
        이 소속사의 굿즈를 파는 이커머스 셀러(<code>seller_id</code>) — 소속사당 0..1.
        팬 서비스가 스토어에 그 셀러가 있는지 확인한 뒤에만 저장합니다.
      </p>
      <p className="text-sm">
        현재:{' '}
        <code data-testid="fan-agency-seller-current">{agency.storeSellerId ?? '연결 없음'}</code>
      </p>
      <div
        role="note"
        data-testid="fan-agency-seller-unwired-note"
        className="rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-100"
      >
        🔵 지금은 새 연결이 거절됩니다 — 팬 서비스가 스토어 셀러를 확인하는 경로가 아직 배선되지
        않았습니다(TASK-MONO-759). 시도하면 «확인할 수 없어 저장하지 않았습니다» 가 표시되고 아무것도
        바뀌지 않습니다. 연결 해제는 지금도 됩니다.
      </div>
      {!archived && (
        <div className="flex flex-wrap items-end gap-3">
          <div className="min-w-[16rem] flex-1">
            <label htmlFor={inputId} className={labelCls}>
              셀러 ID
            </label>
            <input
              id={inputId}
              value={value}
              onChange={(e) => setValue(e.target.value)}
              maxLength={64}
              placeholder="예: acme-goods"
              className={inputCls}
              data-testid="fan-agency-seller-input"
            />
          </div>
          <Button
            disabled={trimmed === '' || link.isPending}
            onClick={() => submit(trimmed)}
            data-testid="fan-agency-seller-link"
          >
            연결
          </Button>
          {agency.storeSellerId && (
            <Button
              variant="secondary"
              disabled={link.isPending}
              onClick={() => submit(null)}
              data-testid="fan-agency-seller-clear"
            >
              연결 해제
            </Button>
          )}
        </div>
      )}
      {lookupUnavailable && (
        <div
          role="alert"
          data-testid="fan-agency-seller-lookup-unavailable"
          className="rounded-md border border-border bg-muted px-3 py-2 text-sm text-foreground"
        >
          스토어 셀러를 확인할 수 없어 <strong>연결하지 않았습니다 — 아무것도 저장되지 않았습니다.</strong>{' '}
          팬 서비스가 스토어에 묻는 경로가 아직 배선되지 않았습니다(TASK-MONO-759).
        </div>
      )}
      <FanError testId="fan-agency-seller-error" message={error} />
    </section>
  );
}

function RenameSection({ agency }: { agency: Agency }) {
  const inputId = useId();
  const [name, setName] = useState(agency.name);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const rename = useRenameAgency(agency.id);
  const trimmed = name.trim();
  const valid = trimmed !== '' && trimmed.length <= 120 && trimmed !== agency.name;

  return (
    <section aria-labelledby="fan-agency-rename-heading" className="max-w-2xl space-y-3">
      <h2 id="fan-agency-rename-heading" className="text-lg font-medium">
        이름 변경
      </h2>
      <p className="text-sm text-muted-foreground">
        소속된 아티스트·그룹은 바뀐 이름을 바로 표시합니다.
      </p>
      <div className="flex flex-wrap items-end gap-3">
        <div className="min-w-[16rem] flex-1">
          <label htmlFor={inputId} className={labelCls}>
            새 이름
          </label>
          <input
            id={inputId}
            value={name}
            onChange={(e) => {
              setName(e.target.value);
              setSaved(false);
            }}
            maxLength={120}
            className={inputCls}
            data-testid="fan-agency-rename-input"
          />
        </div>
        <Button
          disabled={!valid || rename.isPending}
          onClick={() => {
            setError(null);
            rename.mutate(trimmed, {
              onSuccess: () => setSaved(true),
              onError: (err) => setError(fanErrorMessage(err, '이름을 바꾸지 못했습니다.')),
            });
          }}
          data-testid="fan-agency-rename-submit"
        >
          저장
        </Button>
      </div>
      {saved && (
        <p className="text-sm text-muted-foreground" data-testid="fan-agency-rename-saved">
          저장했습니다.
        </p>
      )}
      <FanError testId="fan-agency-rename-error" message={error} />
    </section>
  );
}

function ArchiveSection({ agency }: { agency: Agency }) {
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const archive = useArchiveAgency(agency.id);

  return (
    <section aria-labelledby="fan-agency-archive-heading" className="max-w-2xl space-y-3">
      <h2 id="fan-agency-archive-heading" className="text-lg font-medium">
        보관
      </h2>
      <p className="text-sm text-muted-foreground">
        보관하면 새 소속 · 이름 변경 · 셀러 연결을 받지 않습니다. 이미 소속된 아티스트·그룹의
        표시는 그대로입니다. 되돌릴 수 없습니다.
      </p>
      <Button variant="secondary" onClick={() => setOpen(true)} data-testid="fan-agency-archive">
        소속사 보관
      </Button>
      <ConfirmDialog
        open={open}
        title="소속사를 보관할까요?"
        description={`"${agency.name}" 을(를) 보관합니다. 되돌릴 수 없습니다.`}
        confirmLabel="보관"
        destructive
        pending={archive.isPending}
        errorMessage={error}
        dialogTestId="fan-confirm-dialog"
        cancelTestId="fan-confirm-cancel"
        confirmTestId="fan-confirm-confirm"
        errorTestId="fan-confirm-error"
        onConfirm={() =>
          archive.mutate(undefined, {
            onSuccess: () => setOpen(false),
            onError: (err) => setError(fanErrorMessage(err, '보관하지 못했습니다.')),
          })
        }
        onCancel={() => {
          setOpen(false);
          setError(null);
        }}
      />
    </section>
  );
}
