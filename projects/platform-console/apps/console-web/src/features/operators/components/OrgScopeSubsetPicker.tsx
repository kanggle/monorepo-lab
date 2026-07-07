'use client';

import type { useOrgScopeForm } from '../hooks/use-org-scope-form';

type OrgScopeForm = ReturnType<typeof useOrgScopeForm>;

/**
 * Subset-mode department picker for `OrgScopeDialog` (TASK-PC-FE-209 split) —
 * the `mode === 'subset'` panel: the erp department multi-select, its
 * degrade fallback (departments fetch fail → manual id textarea + warning
 * banner), the loading state, and the "select at least one" hint. Pure
 * presentation from the `useOrgScopeForm` result; the manual-entry field id is
 * a view concern owned by the container.
 */
export interface OrgScopeSubsetPickerProps {
  f: OrgScopeForm;
  manualId: string;
}

export function OrgScopeSubsetPicker({ f, manualId }: OrgScopeSubsetPickerProps) {
  return (
    <div className="mt-3" data-testid="org-scope-subset-panel">
      {f.deptsFailed ? (
        <div>
          <div
            role="status"
            data-testid="org-scope-depts-degraded"
            className="mb-2 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-xs text-destructive"
          >
            부서 목록을 불러올 수 없습니다 (erp 미가용 또는 테넌트
            미-entitled). 부서 ID를 직접 입력하세요 (한 줄에 하나 또는
            쉼표로 구분). GAP은 ID 존재를 검증하지 않으며 erp가 소비
            시점에 검증합니다.
          </div>
          <label
            htmlFor={manualId}
            className="block text-sm font-medium text-foreground"
          >
            부서 ID 직접 입력
          </label>
          <textarea
            id={manualId}
            value={f.manual}
            onChange={(e) => f.setManual(e.target.value)}
            rows={3}
            data-testid="org-scope-manual-input"
            placeholder={'dept-sales\ndept-eng'}
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
          <p className="mt-1 text-xs text-muted-foreground">
            입력된 ID: {f.subsetIds.length}개
          </p>
        </div>
      ) : f.deptsLoading ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="org-scope-depts-loading"
        >
          부서 목록을 불러오는 중…
        </p>
      ) : (
        <div
          className="max-h-48 overflow-y-auto rounded-md border border-border p-2"
          data-testid="org-scope-dept-list"
        >
          {f.activeDepartments.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              선택할 수 있는 활성 부서가 없습니다.
            </p>
          ) : (
            f.activeDepartments.map((d) => (
              <label
                key={d.id}
                className="flex items-center gap-2 py-1 text-sm text-foreground"
              >
                <input
                  type="checkbox"
                  checked={f.selected.includes(d.id)}
                  onChange={() => f.toggleDept(d.id)}
                  data-testid={`org-scope-dept-${d.id}`}
                />
                <span>
                  {d.code} · {d.name}
                </span>
              </label>
            ))
          )}
        </div>
      )}
      {f.subsetEmpty && (
        <p
          className="mt-1 text-xs text-muted-foreground"
          data-testid="org-scope-subset-empty"
        >
          부서를 하나 이상 선택해야 합니다. (어떤 부서도 아님을
          원하면 &quot;차단&quot;을 선택하세요.)
        </p>
      )}
    </div>
  );
}
