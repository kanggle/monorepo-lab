'use client';

import { useId, useMemo, useState } from 'react';
import type { ConsoleSampleTable } from '@demo/public-data';
import { filterRows } from '../lib/filter-rows';
import { actionsForTable, DEMO_DISABLED_REASON } from '../lib/sample-actions';
import { DemoActionButton } from './DemoActionButton';

/**
 * 샘플 표 하나 — **설명 + 검색 + 표 + (비활성) 쓰기 작업**.
 *
 * =============================================================================
 * 🔴🔴 이 컴포넌트는 **fetch 를 하지 않는다** — 할 수가 없다
 * =============================================================================
 * 입력은 `table` prop 하나뿐이고, 그 prop 은 서버 컴포넌트가 봉투에서 꺼내 넘긴 것이다.
 * 여기에 검색어를 서버로 보내는 코드를 넣으면 그 순간 익명 표면에 **질의 표면**이 생긴다.
 * 그래서 검색은 «이미 받은 행» 위에서만 돈다(`filterRows`, 순수 함수).
 * 표가 작아서 그렇게 해도 되는 것이 아니라, **작으니까 그렇게 하는 것이 가능한 것**이다.
 *
 * 🔴 `description` 을 항상 그린다. 요구사항의 *"각 화면의 기능 설명"* 이 이 자리이고,
 *    픽스처가 표마다 한 문장을 소유한다(화면이 지어내지 않는다).
 *
 * 🔵 검색 결과 0건은 **정상 상태**다. "데이터가 없습니다" 가 아니라 "검색 결과가
 *    없습니다" 라고 말한다 — 둘은 다른 사실이고, 앞의 문구는 샘플이 비었다는 거짓말이다.
 */
export function SampleTableCard({ table }: { table: ConsoleSampleTable }) {
  const [query, setQuery] = useState('');
  const inputId = useId();
  const rows = useMemo(() => filterRows(table, query), [table, query]);
  const actions = actionsForTable(table.key);

  return (
    <section
      aria-labelledby={`demo-table-${table.key}-heading`}
      data-testid={`demo-table-${table.key}`}
      className="mb-10"
    >
      <h2
        id={`demo-table-${table.key}-heading`}
        className="mb-1 text-lg font-medium text-foreground"
      >
        {table.title}
      </h2>
      <p
        data-testid={`demo-table-${table.key}-description`}
        className="mb-4 text-sm text-muted-foreground"
      >
        {table.description}
      </p>

      {actions.length > 0 && (
        <div
          data-testid={`demo-actions-${table.key}`}
          className="mb-4 flex flex-wrap items-center gap-2"
        >
          {actions.map((label) => (
            <DemoActionButton
              key={label}
              label={label}
              testId={`demo-action-${table.key}-${label}`}
            />
          ))}
          {/* 🔴 툴팁만으로는 부족하다 — 터치 기기에는 hover 가 없다. 사유를 본문으로도 적는다. */}
          <span className="text-xs text-muted-foreground">
            {DEMO_DISABLED_REASON}
          </span>
        </div>
      )}

      <form
        role="search"
        aria-label={`${table.title} 검색`}
        className="mb-3"
        onSubmit={(e) => e.preventDefault()}
      >
        <label htmlFor={inputId} className="sr-only">
          {table.title} 검색
        </label>
        <input
          id={inputId}
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="표 안에서 검색 (예: 상태 · 이름 · 번호)"
          data-testid={`demo-search-${table.key}`}
          className="w-full max-w-sm rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        />
      </form>

      <div className="overflow-x-auto">
        <table className="data-table" data-testid={`demo-grid-${table.key}`}>
          <caption className="sr-only">{table.title}</caption>
          <thead>
            <tr className="text-left">
              {table.columns.map((c) => (
                <th key={c.key} scope="col" className="p-2">
                  {c.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td
                  colSpan={table.columns.length}
                  className="p-4 text-sm text-muted-foreground"
                  data-testid={`demo-empty-${table.key}`}
                >
                  검색 결과가 없습니다.
                </td>
              </tr>
            ) : (
              rows.map((row, i) => (
                <tr
                  key={`${table.key}-${i}`}
                  data-testid={`demo-row-${table.key}-${i}`}
                >
                  {table.columns.map((c) => (
                    <td key={c.key} className="p-2 text-sm text-foreground">
                      {row[c.key] ?? '-'}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
      <p className="mt-2 text-xs text-muted-foreground">
        {rows.length} / {table.rows.length}행 (브라우저에서 필터링 — 서버로
        검색어를 보내지 않습니다)
      </p>
    </section>
  );
}
