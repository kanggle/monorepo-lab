'use client';

import { useId, useState, useEffect } from 'react';
import { Button } from '@/shared/ui/Button';
import { showPickerOnClick } from '@/shared/lib/show-picker';
import { useAsOf } from '../hooks/use-erp-ops';

/**
 * `<AsOfPicker>` — the E3 first-class shared component
 * (TASK-PC-FE-010 / § 2.4.8).
 *
 * Reads / writes the URL `?asOf=` search-param via `useAsOf()` —
 * the SINGLE source of truth for the section's effective-instant.
 * Changing the value writes back to the URL, which triggers
 * React Query refetch of every list / detail subscribed under the
 * section (every queryKey depends on `asOf`).
 *
 * WCAG AA: native `<input type="date">` is keyboard-operable +
 * screen-reader-friendly out of the box; the label is associated
 * via `htmlFor`; a `<Button>` clears the param (returns to "today"
 * UTC at the producer).
 *
 * The core E3 invariant the task pins: the input value threads
 * through to the URL → query → producer → rendered state
 * verbatim. The producer returns the state-at-that-instant; the
 * console NEVER substitutes current state.
 */
export interface AsOfPickerProps {
  /** Optional label override (default: "조회 기준일 (asOf)"). */
  label?: string;
}

export function AsOfPicker({ label = '조회 기준일 (asOf)' }: AsOfPickerProps) {
  const { asOf, setAsOf } = useAsOf();
  const fid = useId();
  // Keep a local-controlled state so typing doesn't write back to
  // the URL on every keystroke; commit on form submit / clear.
  const [draft, setDraft] = useState<string>(asOf ?? '');
  useEffect(() => {
    setDraft(asOf ?? '');
  }, [asOf]);

  function submit(e: React.FormEvent) {
    e.preventDefault();
    setAsOf(draft.trim() || null);
  }

  function clear() {
    setAsOf(null);
  }

  return (
    <form
      onSubmit={submit}
      className="mb-6 flex items-end gap-3"
      role="search"
      aria-label="erp 조회 기준일"
    >
      <div className="flex-1">
        <label
          htmlFor={fid}
          className="block text-sm font-medium text-foreground"
        >
          {label}
        </label>
        <input
          id={fid}
          type="date"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onClick={showPickerOnClick}
          data-testid="erp-asof-input"
          autoComplete="off"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
        <p className="mt-1 text-xs text-muted-foreground">
          비워두면 오늘(UTC) 기준으로 조회합니다. 과거 ISO-8601
          DATE 를 입력하면 해당 시점의 마스터 상태를 조회합니다
          (E3 effective-dating).
        </p>
      </div>
      <Button type="submit" data-testid="erp-asof-submit">
        적용
      </Button>
      <Button
        type="button"
        variant="secondary"
        onClick={clear}
        data-testid="erp-asof-clear"
        disabled={!asOf}
      >
        초기화
      </Button>
    </form>
  );
}
