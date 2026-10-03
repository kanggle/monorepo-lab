'use client';

import { useId, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/shared/ui/Button';
import { useCreateAgency } from '../hooks/use-fan-directory';
import { fanErrorMessage } from '../lib/fan-messages';
import { FanError, inputCls, labelCls } from './fan-ui';

/**
 * Create an agency (TASK-MONO-751 AC-1). `POST /api/fan/agencies` → on success the new
 * agency's detail, where the store seller can be linked. The producer normalises the name
 * (whitespace runs collapse; case is significant) and answers a clash with 409
 * `AGENCY_NAME_CONFLICT`, shown inline.
 */
export function AgencyCreateForm() {
  const router = useRouter();
  const nameId = useId();
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const create = useCreateAgency();
  const trimmed = name.trim();
  const valid = trimmed !== '' && trimmed.length <= 120;

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!valid) return;
    setError(null);
    create.mutate(
      { name: trimmed },
      {
        onSuccess: (agency) => {
          router.push(`/fan/agencies/${encodeURIComponent(agency.id)}`);
          router.refresh();
        },
        onError: (err) => setError(fanErrorMessage(err, '소속사를 등록하지 못했습니다.')),
      },
    );
  }

  return (
    <form onSubmit={onSubmit} className="max-w-xl space-y-5" data-testid="fan-agency-create-form">
      <div>
        <label htmlFor={nameId} className={labelCls}>
          소속사 이름 <span className="text-destructive">*</span>
        </label>
        <input
          id={nameId}
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={120}
          placeholder="예: Aurora Entertainment"
          className={inputCls}
          data-testid="fan-agency-create-name"
        />
        <p className="mt-1 text-xs text-muted-foreground">
          최대 120자. 공백만 다른 이름은 같은 이름으로 보고, 대소문자는 구별합니다.
        </p>
      </div>
      <FanError testId="fan-agency-create-error" message={error} />
      <div className="flex gap-3">
        <Button type="submit" disabled={!valid || create.isPending} data-testid="fan-agency-create-submit">
          소속사 등록
        </Button>
        <Button type="button" variant="secondary" onClick={() => router.back()} data-testid="fan-agency-create-cancel">
          취소
        </Button>
      </div>
    </form>
  );
}
