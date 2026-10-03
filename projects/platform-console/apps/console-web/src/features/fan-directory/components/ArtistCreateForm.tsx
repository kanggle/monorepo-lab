'use client';

import { useId, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/shared/ui/Button';
import { useActiveAgencyOptions, useCreateArtist } from '../hooks/use-fan-directory';
import { fanErrorMessage } from '../lib/fan-messages';
import { ARTIST_TYPES } from '../api/fan-types';
import { FanError, inputCls, labelCls } from './fan-ui';

/**
 * Register an artist (TASK-MONO-751 AC-1). `accountId` is REQUIRED and immutable (artist-api
 * § accountId — the IAM subject that authors as this artist); the console only forwards it,
 * it never authors as the artist (ADR-MONO-059 — the operator manages the directory, not the
 * artist's voice). An agency can be chosen here or later on the detail screen.
 */
export function ArtistCreateForm() {
  const router = useRouter();
  const ids = { account: useId(), type: useId(), stage: useId(), real: useId(), debut: useId(), agency: useId() };
  const options = useActiveAgencyOptions();
  const [accountId, setAccountId] = useState('');
  const [artistType, setArtistType] = useState<(typeof ARTIST_TYPES)[number]>('SOLO');
  const [stageName, setStageName] = useState('');
  const [realName, setRealName] = useState('');
  const [debutDate, setDebutDate] = useState('');
  const [agencyId, setAgencyId] = useState('');
  const [error, setError] = useState<string | null>(null);
  const create = useCreateArtist();

  const valid =
    accountId.trim() !== '' &&
    accountId.trim().length <= 36 &&
    stageName.trim() !== '' &&
    stageName.trim().length <= 120;

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!valid) return;
    setError(null);
    create.mutate(
      {
        accountId: accountId.trim(),
        artistType,
        stageName: stageName.trim(),
        realName: realName.trim() || undefined,
        debutDate: debutDate || undefined,
        agencyId: agencyId || undefined,
      },
      {
        onSuccess: (artist) => {
          router.push(`/fan/artists/${encodeURIComponent(artist.id)}`);
          router.refresh();
        },
        onError: (err) => setError(fanErrorMessage(err, '아티스트를 등록하지 못했습니다.')),
      },
    );
  }

  return (
    <form onSubmit={onSubmit} className="max-w-xl space-y-5" data-testid="fan-artist-create-form">
      <div>
        <label htmlFor={ids.account} className={labelCls}>
          계정 ID (accountId) <span className="text-destructive">*</span>
        </label>
        <input
          id={ids.account}
          value={accountId}
          onChange={(e) => setAccountId(e.target.value)}
          maxLength={36}
          className={inputCls}
          data-testid="fan-artist-create-account"
        />
        <p className="mt-1 text-xs text-muted-foreground">
          이 아티스트로 글을 쓰는 IAM 계정의 ID. 등록 뒤에는 바꿀 수 없습니다.
        </p>
      </div>
      <div>
        <label htmlFor={ids.type} className={labelCls}>
          유형
        </label>
        <select
          id={ids.type}
          value={artistType}
          onChange={(e) => setArtistType(e.target.value as (typeof ARTIST_TYPES)[number])}
          className={inputCls}
          data-testid="fan-artist-create-type"
        >
          {ARTIST_TYPES.map((t) => (
            <option key={t} value={t}>
              {t === 'SOLO' ? '솔로 (SOLO)' : '그룹 멤버 (GROUP_MEMBER)'}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label htmlFor={ids.stage} className={labelCls}>
          활동명 <span className="text-destructive">*</span>
        </label>
        <input
          id={ids.stage}
          value={stageName}
          onChange={(e) => setStageName(e.target.value)}
          maxLength={120}
          className={inputCls}
          data-testid="fan-artist-create-stage"
        />
      </div>
      <div>
        <label htmlFor={ids.real} className={labelCls}>
          본명
        </label>
        <input
          id={ids.real}
          value={realName}
          onChange={(e) => setRealName(e.target.value)}
          maxLength={120}
          className={inputCls}
          data-testid="fan-artist-create-real"
        />
      </div>
      <div>
        <label htmlFor={ids.debut} className={labelCls}>
          데뷔일
        </label>
        <input
          id={ids.debut}
          type="date"
          value={debutDate}
          onChange={(e) => setDebutDate(e.target.value)}
          className={inputCls}
          data-testid="fan-artist-create-debut"
        />
      </div>
      <div>
        <label htmlFor={ids.agency} className={labelCls}>
          소속사
        </label>
        <select
          id={ids.agency}
          value={agencyId}
          onChange={(e) => setAgencyId(e.target.value)}
          disabled={options.isLoading}
          className={inputCls}
          data-testid="fan-artist-create-agency"
        >
          <option value="">소속 없음</option>
          {(options.data ?? []).map((a) => (
            <option key={a.id} value={a.id}>
              {a.name}
            </option>
          ))}
        </select>
      </div>
      <FanError testId="fan-artist-create-error" message={error} />
      <div className="flex gap-3">
        <Button type="submit" disabled={!valid || create.isPending} data-testid="fan-artist-create-submit">
          아티스트 등록
        </Button>
        <Button type="button" variant="secondary" onClick={() => router.back()} data-testid="fan-artist-create-cancel">
          취소
        </Button>
      </div>
    </form>
  );
}
