'use client';

import { useId, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/shared/ui/Button';
import { useActiveAgencyOptions, useCreateArtistGroup } from '../hooks/use-fan-directory';
import { fanErrorMessage } from '../lib/fan-messages';
import { FanError, inputCls, labelCls } from './fan-ui';

/**
 * Fan artist groups (TASK-MONO-751). 🔴 artist-service has NO group list endpoint
 * (artist-api § Artist groups: create · get-by-id · members · affiliation only), so this
 * screen does not pretend to list groups: it opens a group by id and creates one (the create
 * answer carries the id and lands on its detail). A group list is a producer change, not a
 * console one — recorded in the ticket, not invented here.
 */
export function GroupsScreen() {
  const router = useRouter();
  const ids = { open: useId(), name: useId(), debut: useId(), agency: useId() };
  const options = useActiveAgencyOptions();
  const [openValue, setOpenValue] = useState('');
  const [name, setName] = useState('');
  const [debutDate, setDebutDate] = useState('');
  const [agencyId, setAgencyId] = useState('');
  const [error, setError] = useState<string | null>(null);
  const create = useCreateArtistGroup();
  const valid = name.trim() !== '' && name.trim().length <= 120;

  return (
    <section aria-labelledby="fan-groups-heading" className="space-y-8">
      <div>
        <h1 id="fan-groups-heading" className="mb-2 text-2xl font-semibold">
          아티스트 그룹
        </h1>
        <p className="text-sm text-muted-foreground" data-testid="fan-group-no-list-note">
          팬 아티스트 서비스에는 그룹 목록 API 가 없습니다 — 그룹은 ID 로 열거나, 새로 만든 뒤
          그 상세로 이동합니다.
        </p>
      </div>

      <form
        className="flex max-w-xl items-end gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          const v = openValue.trim();
          if (v) router.push(`/fan/groups/${encodeURIComponent(v)}`);
        }}
      >
        <div className="flex-1">
          <label htmlFor={ids.open} className={labelCls}>
            ID로 열기
          </label>
          <input
            id={ids.open}
            value={openValue}
            onChange={(e) => setOpenValue(e.target.value)}
            placeholder="그룹 ID"
            className={inputCls}
            data-testid="fan-group-open-id"
          />
        </div>
        <Button type="submit" variant="secondary" data-testid="fan-group-open-submit">
          열기
        </Button>
      </form>

      <form
        className="max-w-xl space-y-4"
        data-testid="fan-group-create-form"
        onSubmit={(e) => {
          e.preventDefault();
          if (!valid) return;
          setError(null);
          create.mutate(
            { name: name.trim(), debutDate: debutDate || undefined, agencyId: agencyId || undefined },
            {
              onSuccess: (group) => router.push(`/fan/groups/${encodeURIComponent(group.id)}`),
              onError: (err) => setError(fanErrorMessage(err, '그룹을 만들지 못했습니다.')),
            },
          );
        }}
      >
        <h2 className="text-lg font-medium">그룹 만들기</h2>
        <div>
          <label htmlFor={ids.name} className={labelCls}>
            그룹 이름 <span className="text-destructive">*</span>
          </label>
          <input id={ids.name} value={name} onChange={(e) => setName(e.target.value)} maxLength={120} className={inputCls} data-testid="fan-group-create-name" />
        </div>
        <div>
          <label htmlFor={ids.debut} className={labelCls}>
            데뷔일
          </label>
          <input id={ids.debut} type="date" value={debutDate} onChange={(e) => setDebutDate(e.target.value)} className={inputCls} data-testid="fan-group-create-debut" />
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
            data-testid="fan-group-create-agency"
          >
            <option value="">소속 없음</option>
            {(options.data ?? []).map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}
              </option>
            ))}
          </select>
        </div>
        <FanError testId="fan-group-create-error" message={error} />
        <Button type="submit" disabled={!valid || create.isPending} data-testid="fan-group-create-submit">
          그룹 만들기
        </Button>
      </form>
    </section>
  );
}
