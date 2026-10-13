'use client';

import {
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type FocusEvent,
  type KeyboardEvent,
} from 'react';
import { cn } from '@/shared/lib/cn';
import { apiClient } from '@/shared/api/client';
import {
  OperatorPageSchema,
  type OperatorSummary,
} from '@/shared/api/iam-operators-types';

/**
 * `<OperatorPicker>` — TASK-PC-FE-334, the shared "이메일 · 이름으로 고르는"
 * combobox that replaces raw-UUID operator-id text inputs across the org
 * hierarchy (노드 관리자 배정) and partnerships (참여자 배정/해제) screens.
 * Mirrors `features/wms-ops/components/WmsRefPicker.tsx` (TASK-PC-FE-333)'s
 * combobox shape and `features/operator-groups` `GroupMemberDialog` +
 * `use-group-member-candidates.ts` (TASK-PC-FE-317)'s data source — the SAME
 * same-origin `/api/operators` proxy (never IAM directly,
 * architecture.md § Forbidden Dependencies), `size=100` pages accumulated via
 * an explicit "더 보기" (the producer has no server-side search param, so
 * every filter is client-side over whatever has been loaded so far).
 *
 * The operator picks from the list and this component hands the parent the
 * candidate's **operatorId** (never the typed text) via `onChange`, plus the
 * full `OperatorSummary` when known (so the caller can show a name/email
 * label instead of a bare id in a confirm dialog).
 *
 * UUID-shaped typed text → a "이 ID로 사용" escape option (the target
 * operator may be outside the active tenant's roster — e.g. a node admin
 * grant's reach can be broader than the active tenant, task Edge Cases).
 *
 * Fetch failure (403/503/network) on the FIRST page → the component falls
 * back PERMANENTLY (for this mount) to a plain operator-ID text input under
 * the SAME `${testid}-input` testid, so the add/grant flow stays reachable
 * (AC-2) without the caller needing to branch.
 */

const PAGE_SIZE = 100;

/** UUID "형식"만 본다 — 생성기 버전(v1/v4/…) 무관, AC-3 탈출구 판정용. */
const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export interface OperatorPickerProps {
  /** `<label htmlFor>` 대상 입력 id. */
  id: string;
  label: string;
  /** 선택된 운영자의 **operatorId** (텍스트 아님). */
  value: string | undefined;
  onChange: (operatorId: string | undefined, operator?: OperatorSummary) => void;
  /** 테스트/스타일 훅의 베이스 id — 하위 요소에 접미사가 붙는다. */
  testid: string;
  /** 명시 테넌트 스코프. 생략 ⇒ 활성 테넌트(프록시 기본값). */
  tenantId?: string;
  placeholder?: string;
}

interface OperatorOption {
  key: string;
  operatorId: string;
  listLabel: string;
  selectedLabel: string;
  operator?: OperatorSummary;
  isUuidEscape?: boolean;
}

function operatorListLabel(op: OperatorSummary): string {
  return `${op.displayName} · ${op.email} · ${op.status}`;
}

function operatorSelectedLabel(op: OperatorSummary): string {
  return `${op.displayName} (${op.email})`;
}

export function OperatorPicker({
  id,
  label,
  value,
  onChange,
  testid,
  tenantId,
  placeholder,
}: OperatorPickerProps) {
  const listboxId = useId();
  const containerRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);

  const [rows, setRows] = useState<OperatorSummary[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [errored, setErrored] = useState(false);
  const [fetchedOnce, setFetchedOnce] = useState(false);

  const [open, setOpen] = useState(false);
  const [inputText, setInputText] = useState('');
  // What the input DISPLAYS once a pick is made — covers BOTH a normal
  // operator pick AND a UUID-escape pick (which has no backing row), mirroring
  // `WmsRefPicker`'s `selectedLabel`/id split. The full `OperatorSummary` (when
  // one exists) is reported to the caller via `onChange`'s 2nd argument —
  // this component does not need to hold onto it itself.
  const [selectedLabel, setSelectedLabel] = useState<string | null>(null);
  const [highlight, setHighlight] = useState(-1);

  // AC-2 fallback — a plain operator-ID text input once the first page fails.
  const [manualId, setManualId] = useState('');

  // 부모가 바깥에서 value 를 비우면(예: 선택 해제) 표시 라벨도 같이 지운다.
  useEffect(() => {
    if (!value) setSelectedLabel(null);
  }, [value]);

  useEffect(() => {
    return () => abortRef.current?.abort();
  }, []);

  async function fetchPage(p: number) {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    if (p === 0) setLoading(true);
    else setLoadingMore(true);
    try {
      const qs = new URLSearchParams();
      if (tenantId) qs.set('tenantId', tenantId);
      qs.set('page', String(p));
      qs.set('size', String(PAGE_SIZE));
      const raw = await apiClient.get<unknown>(
        `/api/operators?${qs.toString()}`,
        { signal: controller.signal },
      );
      const parsed = OperatorPageSchema.parse(raw);
      setRows((prev) => (p === 0 ? parsed.content : [...prev, ...parsed.content]));
      setTotalPages(parsed.totalPages);
      setPage(p);
    } catch (err) {
      if ((err as { name?: string } | null)?.name === 'AbortError') return;
      setErrored(true);
    } finally {
      setLoading(false);
      setLoadingMore(false);
      setFetchedOnce(true);
    }
  }

  function handleFocus() {
    setOpen(true);
    if (!fetchedOnce && !loading && !errored) void fetchPage(0);
  }

  const filtered = useMemo(() => {
    const q = inputText.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter(
      (r) =>
        r.email.toLowerCase().includes(q) ||
        r.displayName.toLowerCase().includes(q),
    );
  }, [rows, inputText]);

  const uuidText = inputText.trim();
  const uuidEscape = UUID_RE.test(uuidText) ? uuidText : null;

  const options: OperatorOption[] = useMemo(() => {
    const list: OperatorOption[] = filtered.map((op) => ({
      key: op.operatorId,
      operatorId: op.operatorId,
      listLabel: operatorListLabel(op),
      selectedLabel: operatorSelectedLabel(op),
      operator: op,
    }));
    if (uuidEscape) {
      list.push({
        key: `uuid:${uuidEscape}`,
        operatorId: uuidEscape,
        listLabel: `이 ID로 사용: ${uuidEscape}`,
        selectedLabel: uuidEscape,
        isUuidEscape: true,
      });
    }
    return list;
  }, [filtered, uuidEscape]);

  const hasMore = page + 1 < totalPages;

  function selectOption(opt: OperatorOption) {
    setSelectedLabel(opt.selectedLabel);
    setInputText('');
    setOpen(false);
    setHighlight(-1);
    onChange(opt.operatorId, opt.operator);
  }

  function handleInputChange(e: ChangeEvent<HTMLInputElement>) {
    const next = e.target.value;
    if (selectedLabel !== null) {
      setSelectedLabel(null);
      onChange(undefined);
    }
    setInputText(next);
    setOpen(true);
    setHighlight(-1);
  }

  function handleClear() {
    setSelectedLabel(null);
    setInputText('');
    setOpen(false);
    onChange(undefined);
  }

  function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (!open) {
        setOpen(true);
        if (!fetchedOnce && !loading && !errored) void fetchPage(0);
      }
      setHighlight((h) => Math.min(h + 1, options.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlight((h) => Math.max(h - 1, 0));
    } else if (e.key === 'Enter') {
      if (open && highlight >= 0 && options[highlight]) {
        e.preventDefault();
        selectOption(options[highlight]);
      }
    } else if (e.key === 'Escape') {
      if (open) {
        e.preventDefault();
        setOpen(false);
        setHighlight(-1);
      }
    }
  }

  function handleBlur(e: FocusEvent<HTMLDivElement>) {
    const next = e.relatedTarget as Node | null;
    if (next && containerRef.current?.contains(next)) return;
    setOpen(false);
    setHighlight(-1);
  }

  function handleManualChange(e: ChangeEvent<HTMLInputElement>) {
    const next = e.target.value;
    setManualId(next);
    const trimmed = next.trim();
    onChange(trimmed.length > 0 ? trimmed : undefined);
  }

  if (errored) {
    return (
      <div>
        <p
          data-testid={`${testid}-fallback-notice`}
          className="mb-1 text-sm text-muted-foreground"
        >
          운영자 목록을 불러오지 못했습니다. 운영자 ID 를 직접 입력하세요.
        </p>
        <label htmlFor={id} className="block text-sm font-medium text-foreground">
          {label}
        </label>
        <input
          id={id}
          value={manualId}
          onChange={handleManualChange}
          data-testid={`${testid}-input`}
          placeholder={placeholder ?? 'operator-uuid'}
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
      </div>
    );
  }

  const showClear = Boolean(value) || inputText.length > 0;

  return (
    <div ref={containerRef} className="relative" onBlur={handleBlur}>
      <label htmlFor={id} className="block text-sm font-medium text-foreground">
        {label}
      </label>
      <div className="mt-1 flex items-center gap-1">
        <input
          id={id}
          type="text"
          role="combobox"
          aria-expanded={open}
          aria-autocomplete="list"
          aria-haspopup="listbox"
          aria-controls={open ? listboxId : undefined}
          aria-activedescendant={
            open && highlight >= 0 ? `${listboxId}-option-${highlight}` : undefined
          }
          autoComplete="off"
          placeholder={placeholder}
          value={selectedLabel ?? inputText}
          onFocus={handleFocus}
          onChange={handleInputChange}
          onKeyDown={handleKeyDown}
          data-testid={`${testid}-input`}
          className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
        {showClear && (
          <button
            type="button"
            onClick={handleClear}
            data-testid={`${testid}-clear`}
            aria-label={`${label} 초기화`}
            className="shrink-0 rounded-md px-2 py-1 text-xs text-muted-foreground hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            초기화
          </button>
        )}
      </div>
      {open && (
        <ul
          id={listboxId}
          role="listbox"
          aria-label={`${label} 후보`}
          data-testid={`${testid}-listbox`}
          className="absolute z-10 mt-1 max-h-72 w-full overflow-auto rounded-md border border-border bg-background text-sm shadow-md"
        >
          {loading ? (
            <li className="px-3 py-2 text-muted-foreground">불러오는 중…</li>
          ) : options.length === 0 ? (
            <li
              data-testid={`${testid}-empty`}
              className="px-3 py-2 text-muted-foreground"
            >
              일치하는 운영자 없음
            </li>
          ) : (
            options.map((opt, idx) => (
              <li
                key={opt.key}
                id={`${listboxId}-option-${idx}`}
                role="option"
                aria-selected={idx === highlight}
                data-testid={
                  opt.isUuidEscape
                    ? `${testid}-option-uuid`
                    : `${testid}-option-${opt.operatorId}`
                }
                onMouseDown={(e) => {
                  // 리스트 클릭이 input 의 blur 를 먼저 발생시키지 않도록.
                  e.preventDefault();
                  selectOption(opt);
                }}
                className={cn(
                  'cursor-pointer px-3 py-2',
                  idx === highlight && 'bg-accent',
                )}
              >
                {opt.listLabel}
              </li>
            ))
          )}
          {!loading && hasMore && (
            <li className="border-t border-border px-3 py-2">
              <button
                type="button"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => void fetchPage(page + 1)}
                disabled={loadingMore}
                data-testid={`${testid}-load-more`}
                className="text-xs font-medium text-primary hover:underline disabled:opacity-50"
              >
                {loadingMore ? '불러오는 중…' : '더 보기'}
              </button>
              <p
                data-testid={`${testid}-load-more-note`}
                className="mt-1 text-xs text-muted-foreground"
              >
                거르기는 지금까지 불러온 운영자 안에서만 적용됩니다.
              </p>
            </li>
          )}
        </ul>
      )}
    </div>
  );
}
