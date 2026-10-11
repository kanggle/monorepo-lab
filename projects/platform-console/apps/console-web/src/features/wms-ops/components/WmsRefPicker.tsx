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
import { RefPageSchema, type GenericRow, type RefType } from '../api/types';

/**
 * `<WmsRefPicker>` — TASK-PC-FE-333, the shared "코드 · 이름으로 고르는" combobox
 * that replaces the six raw-UUID text inputs on `/wms/inbound` · `/wms/inventory`
 * · `/wms/outbound` (창고 · 공급처 · SKU · 위치 · 로트). The operator types a
 * code/name fragment, picks a candidate from the dropdown, and this component
 * hands the parent the candidate's **id** — never the typed text — via
 * `onChange`. The filter request to the real list API therefore still carries
 * an id (판단: "코드 · 이름은 바뀔 수 있고 ID 는 안 바뀐다. ID 는 화면 뒤에서만.").
 *
 * Candidates come from the same-origin `/api/wms/master/refs/{type}` proxy
 * (never wms directly — architecture.md § Forbidden Dependencies), debounced
 * per keystroke (`DEBOUNCE_MS`) to avoid a request-per-character flood.
 *
 * AC-4 (🔴 critical — read before touching the filter below): the demo wms
 * backend runs on a BAKED AMI (TASK-BE-630 added server-side `q` filtering,
 * but this console ships the moment its PR merges — the AMI does not re-bake
 * with it until later). Until that re-bake, the server may IGNORE `q` and
 * return the unfiltered first page. If this component trusted the server's
 * filtering alone, the candidate list would show rows unrelated to what the
 * operator typed (Failure Scenario 1). So every fetched row is ALSO filtered
 * here, client-side, by a case-insensitive substring match on `code`/`name`
 * — correct AFTER the re-bake too (redundant-but-harmless), and the only
 * thing that is correct BEFORE it. 🔴 The same holds for the SCOPE params
 * (`warehouseId` / `skuId` / `partnerType`): a pre-re-bake server ignores
 * them too, so without the client-side scope filter the supplier picker would
 * list customers and the location picker every warehouse's locations. Rows
 * carry those values in `attributes`; a row missing the attribute is kept
 * (tolerant — the server is still the authority once it filters).
 * Limitation: before the re-bake the client can only filter the first page
 * the server returned (`PICKER_PAGE_SIZE`, newest first) — fine for the demo's
 * small master set, and gone once the server filters.
 *
 * Scope change (e.g. a different warehouse picked for the location picker)
 * drops the cached candidates — otherwise the next open would show the
 * previous scope's rows.
 *
 * AC-5: a pasted/typed value that parses as a UUID is offered as its own
 * "이 ID로 조회" option — the escape hatch for incident/log-copy workflows
 * where the operator already has a raw id and the picker's own candidate
 * list cannot (yet, or ever, for an id that fell out of the first page)
 * contain it.
 *
 * AC-6: a fetch failure renders an inline error row in the dropdown — never
 * a silently-empty list (Failure Scenario 3: an empty list reads as "그런
 * 창고 없음", which is a different, wrong claim).
 */

const DEBOUNCE_MS = 200;
const PICKER_PAGE_SIZE = 20;

/** UUID "형식"만 본다 — 생성기 버전(v1/v4/…) 무관, AC-5 탈출구 판정용. */
const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export interface WmsRefPickerScope {
  /** zones/locations 후보를 한 창고로 좁힌다 (AC-2). */
  warehouseId?: string;
  /** lots 후보를 한 SKU 로 좁힌다 (AC-2). */
  skuId?: string;
  /** partners 후보를 거래처 유형으로 좁힌다 (반복 파라미터 — AC-2). */
  partnerType?: string[];
}

export interface WmsRefPickerProps {
  /** `<label htmlFor>` 대상 입력 id (화면이 `useId()` 로 생성해 전달). */
  id: string;
  label: string;
  type: RefType;
  /** 선택된 항목의 **id** (텍스트 아님). */
  value: string | undefined;
  onChange: (id: string | undefined) => void;
  /** 테스트/스타일 훅의 베이스 id — `${testid}-input` 등 하위 요소에 접미사가 붙는다. */
  testid: string;
  scope?: WmsRefPickerScope;
  placeholder?: string;
}

interface RefOption {
  key: string;
  id: string;
  /** 목록(listbox)에 보이는 라벨. */
  listLabel: string;
  /** 선택 후 입력칸에 보이는 라벨 (보통 listLabel 과 같다). */
  selectedLabel: string;
  isUuidEscape?: boolean;
}

interface PickerRow {
  id: string;
  code: string;
  name: string | null;
  /** Scope attributes (`attributes.*`) — undefined when the producer omits them. */
  warehouseId?: string;
  skuId?: string;
  partnerType?: string;
}

function attr(row: GenericRow, key: string): string | undefined {
  const a = row.attributes;
  if (a && typeof a === 'object') {
    const v = (a as Record<string, unknown>)[key];
    if (typeof v === 'string' && v) return v;
  }
  return undefined;
}

/** AC-4 for scope — see the component doc. Missing attribute = keep (tolerant). */
function inScope(r: PickerRow, scope: WmsRefPickerScope | undefined): boolean {
  if (!scope) return true;
  if (scope.warehouseId && r.warehouseId && r.warehouseId !== scope.warehouseId) return false;
  if (scope.skuId && r.skuId && r.skuId !== scope.skuId) return false;
  if (scope.partnerType?.length && r.partnerType && !scope.partnerType.includes(r.partnerType)) {
    return false;
  }
  return true;
}

function rowCodeName(row: GenericRow): { code: string; name: string | null } {
  const code =
    typeof row.code === 'string' && row.code
      ? row.code
      : typeof row.id === 'string'
        ? row.id
        : '';
  const name = typeof row.name === 'string' ? row.name : null;
  return { code, name };
}

/** 이름 없는 타입(위치 · 로트) 은 코드만 — Edge Case "이름 없는 타입 → 코드만 표시". */
function rowLabel(code: string, name: string | null): string {
  return name ? `${code} · ${name}` : code;
}

function buildPickerQs(q: string, scope: WmsRefPickerScope | undefined): string {
  const qs = new URLSearchParams();
  // Edge Case (RefQueryParams doc, 이어서 적용): 빈 q 는 와이어에 안 보낸다.
  if (q) qs.set('q', q);
  if (scope?.warehouseId) qs.set('warehouseId', scope.warehouseId);
  if (scope?.skuId) qs.set('skuId', scope.skuId);
  for (const pt of scope?.partnerType ?? []) qs.append('partnerType', pt);
  qs.set('size', String(PICKER_PAGE_SIZE));
  return qs.toString();
}

export function WmsRefPicker({
  id,
  label,
  type,
  value,
  onChange,
  testid,
  scope,
  placeholder,
}: WmsRefPickerProps) {
  const listboxId = useId();
  const containerRef = useRef<HTMLDivElement>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const [inputText, setInputText] = useState('');
  const [selectedLabel, setSelectedLabel] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [rows, setRows] = useState<PickerRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [errored, setErrored] = useState(false);
  const [highlight, setHighlight] = useState(-1);

  // 부모가 바깥에서 value 를 비우면(예: 창고 변경 → 위치 선택 해제, Edge Case) 표시
  // 라벨도 같이 지운다 — 그래야 지워진 필터가 선택된 것처럼 안 보인다.
  useEffect(() => {
    if (!value) setSelectedLabel(null);
  }, [value]);

  useEffect(() => {
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
      abortRef.current?.abort();
    };
  }, []);

  async function runFetch(q: string) {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setLoading(true);
    setErrored(false);
    try {
      const raw = await apiClient.get<unknown>(
        `/api/wms/master/refs/${type}?${buildPickerQs(q, scope)}`,
        { signal: controller.signal },
      );
      const page = RefPageSchema.parse(raw);
      setRows(
        page.content.map((row) => {
          const { code, name } = rowCodeName(row);
          const idVal = typeof row.id === 'string' ? row.id : code;
          return {
            id: idVal,
            code,
            name,
            warehouseId: attr(row, 'warehouseId'),
            skuId: attr(row, 'skuId'),
            partnerType: attr(row, 'partnerType'),
          };
        }),
      );
    } catch (err) {
      if ((err as { name?: string } | null)?.name === 'AbortError') return;
      setErrored(true);
      setRows([]);
    } finally {
      setLoading(false);
    }
  }

  function scheduleFetch(q: string) {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      void runFetch(q);
    }, DEBOUNCE_MS);
  }

  // AC-4 — 서버가 q 를 무시해도(AMI 재굽기 전) 후보가 입력과 맞도록 클라이언트에서도
  // 코드 · 이름 부분 일치(대소문자 무시)로 한 번 더 거른다. 서버가 이미 맞게 걸러도
  // 이 필터는 통과만 시키므로 무해하다(재굽기 후에도 정확함을 유지).
  const filteredRows = useMemo(() => {
    const q = inputText.trim().toLowerCase();
    const scoped = rows.filter((r) => inScope(r, scope));
    if (!q) return scoped;
    return scoped.filter(
      (r) => r.code.toLowerCase().includes(q) || (r.name ?? '').toLowerCase().includes(q),
    );
  }, [rows, inputText, scope]);

  // Scope change → drop the previous scope's candidates (and any in-flight fetch);
  // refetch at once if the dropdown is open. The key is a string so a parent passing a
  // fresh-but-equal scope object each render does not loop.
  const scopeKey = `${scope?.warehouseId ?? ''}|${scope?.skuId ?? ''}|${(scope?.partnerType ?? []).join(',')}`;
  const firstScope = useRef(true);
  useEffect(() => {
    if (firstScope.current) {
      firstScope.current = false;
      return;
    }
    abortRef.current?.abort();
    setRows([]);
    setErrored(false);
    if (open) scheduleFetch(inputText);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only a scope change should reset
  }, [scopeKey]);

  const uuidText = inputText.trim();
  const uuidEscape = UUID_RE.test(uuidText) ? uuidText : null;

  const options: RefOption[] = useMemo(() => {
    const list: RefOption[] = filteredRows.map((r) => ({
      key: r.id,
      id: r.id,
      listLabel: rowLabel(r.code, r.name),
      selectedLabel: rowLabel(r.code, r.name),
    }));
    if (uuidEscape) {
      list.push({
        key: `uuid:${uuidEscape}`,
        id: uuidEscape,
        listLabel: `이 ID로 조회: ${uuidEscape}`,
        selectedLabel: uuidEscape,
        isUuidEscape: true,
      });
    }
    return list;
  }, [filteredRows, uuidEscape]);

  function selectOption(opt: RefOption) {
    onChange(opt.id);
    setSelectedLabel(opt.selectedLabel);
    setInputText('');
    setOpen(false);
    setHighlight(-1);
  }

  function handleFocus() {
    setOpen(true);
    if (rows.length === 0 && !loading && !errored) scheduleFetch(inputText);
  }

  function handleInputChange(e: ChangeEvent<HTMLInputElement>) {
    const next = e.target.value;
    if (selectedLabel !== null) {
      // AC-3 — 선택된 값을 지우고 다시 타이핑하면 그 필터는 즉시 요청에서 빠진다.
      setSelectedLabel(null);
      onChange(undefined);
    }
    setInputText(next);
    setOpen(true);
    setHighlight(-1);
    scheduleFetch(next);
  }

  function handleClear() {
    setSelectedLabel(null);
    setInputText('');
    setRows([]);
    setErrored(false);
    setOpen(false);
    onChange(undefined);
  }

  function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (!open) {
        setOpen(true);
        scheduleFetch(inputText);
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
          className="absolute z-10 mt-1 max-h-56 w-full overflow-auto rounded-md border border-border bg-background text-sm shadow-md"
        >
          {errored ? (
            <li
              role="alert"
              data-testid={`${testid}-error`}
              className="px-3 py-2 text-red-600 dark:text-red-400"
            >
              기준정보를 불러오지 못했습니다. 잠시 후 다시 시도하세요.
            </li>
          ) : loading ? (
            <li className="px-3 py-2 text-muted-foreground">불러오는 중…</li>
          ) : options.length === 0 ? (
            <li
              data-testid={`${testid}-empty`}
              className="px-3 py-2 text-muted-foreground"
            >
              일치하는 항목 없음
            </li>
          ) : (
            options.map((opt, idx) => (
              <li
                key={opt.key}
                id={`${listboxId}-option-${idx}`}
                role="option"
                aria-selected={idx === highlight}
                data-testid={
                  opt.isUuidEscape ? `${testid}-option-uuid` : `${testid}-option-${opt.id}`
                }
                onMouseDown={(e) => {
                  // 리스트 클릭이 input 의 blur 를 먼저 발생시키지 않도록 — blur 가
                  // 먼저 일어나면 드롭다운이 닫히고 선택이 무시된다.
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
        </ul>
      )}
    </div>
  );
}
