'use client';

import { useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { useActiveAgencyOptions } from '../hooks/use-fan-directory';
import { fanErrorMessage } from '../lib/fan-messages';
import { FanError, inputCls, labelCls } from './fan-ui';

/** The sentinel option value for «unaffiliated» (`agencyId: null`). */
const NONE = '';

/**
 * Change an artist's / group's agency (TASK-MONO-751 AC-1 — TASK-MONO-748 AC-2). Options are
 * the ACTIVE agencies (an ARCHIVED one is refused by the producer — 422 `AGENCY_ARCHIVED`);
 * «소속 없음» sends `agencyId: null`. The current agency stays visible even when it is
 * ARCHIVED (its display name comes from the record, not from the options).
 */
export function AffiliationEditor({
  testIdPrefix,
  currentAgencyId,
  currentAgencyName,
  disabled,
  pending,
  onSave,
}: {
  testIdPrefix: string;
  currentAgencyId: string | null | undefined;
  currentAgencyName: string | null | undefined;
  disabled?: boolean;
  pending: boolean;
  onSave: (agencyId: string | null, done: (err?: unknown) => void) => void;
}) {
  const selectId = useId();
  const options = useActiveAgencyOptions();
  const [value, setValue] = useState<string>(currentAgencyId ?? NONE);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const changed = value !== (currentAgencyId ?? NONE);

  return (
    <section className="max-w-2xl space-y-3" aria-labelledby={`${selectId}-heading`}>
      <h2 id={`${selectId}-heading`} className="text-lg font-medium">
        소속 변경
      </h2>
      <p className="text-sm">
        현재 소속:{' '}
        <span data-testid={`${testIdPrefix}-agency-current`}>
          {currentAgencyName ?? '소속 없음'}
        </span>
      </p>
      <div className="flex flex-wrap items-end gap-3">
        <div className="min-w-[16rem] flex-1">
          <label htmlFor={selectId} className={labelCls}>
            새 소속사
          </label>
          <select
            id={selectId}
            value={value}
            disabled={disabled || options.isLoading}
            onChange={(e) => {
              setValue(e.target.value);
              setSaved(false);
            }}
            className={inputCls}
            data-testid={`${testIdPrefix}-agency-select`}
          >
            <option value={NONE}>소속 없음</option>
            {(options.data ?? []).map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}
              </option>
            ))}
          </select>
          {options.isError && (
            <p className="mt-1 text-xs text-muted-foreground" data-testid={`${testIdPrefix}-agency-options-error`}>
              소속사 목록을 불러오지 못했습니다.
            </p>
          )}
        </div>
        <Button
          disabled={disabled || !changed || pending}
          onClick={() => {
            setError(null);
            onSave(value === NONE ? null : value, (err) => {
              if (err) setError(fanErrorMessage(err, '소속을 바꾸지 못했습니다.'));
              else setSaved(true);
            });
          }}
          data-testid={`${testIdPrefix}-agency-save`}
        >
          소속 저장
        </Button>
      </div>
      {saved && (
        <p className="text-sm text-muted-foreground" data-testid={`${testIdPrefix}-agency-saved`}>
          저장했습니다.
        </p>
      )}
      <FanError testId={`${testIdPrefix}-agency-error`} message={error} />
    </section>
  );
}
