'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  ORG_DOMAIN_KEYS,
  type OrgNode,
  type Ceiling,
} from '../api/types';
import { permitsNothing } from '../lib/tree';
import { useOrgNodes, useCreateOrgNode } from '../hooks/use-org-nodes';
import { OrgNodeTree } from './OrgNodeTree';
import { OrgNodeDetail } from './OrgNodeDetail';
import { OrgReasonDialog } from './OrgReasonDialog';

/**
 * Org-hierarchy management screen (TASK-PC-FE-237 / ADR-047 § 4 step 3). Left:
 * the {@link OrgNodeTree}. Right: {@link OrgNodeDetail} for the selected node.
 * Includes create-root / create-child affordances, each gated by the
 * reason-capture dialog. The caller's grantable roles are fetched once (fail
 * → `null` → the admin panel offers the full known set, never an empty
 * select).
 */
export interface OrgHierarchyScreenProps {
  initial: OrgNode[];
}

function errText(err: unknown): string | null {
  if (!err) return null;
  if (err instanceof ApiError) return messageForCode(err.code, err.message);
  return '요청을 처리하지 못했습니다.';
}

export function OrgHierarchyScreen({ initial }: OrgHierarchyScreenProps) {
  const nodesQuery = useOrgNodes(initial);
  const nodes = nodesQuery.data ?? [];
  const create = useCreateOrgNode();

  const [selectedId, setSelectedId] = useState<string | null>(
    initial[0]?.orgNodeId ?? null,
  );
  const selected = nodes.find((n) => n.orgNodeId === selectedId) ?? null;

  // create context — null = closed, { parentId } = open for that parent
  const [createFor, setCreateFor] = useState<{ parentId: string | null } | null>(
    null,
  );

  // Grantable roles for the admin panel — fetched once, fail-soft to null.
  const grantableQuery = useQuery({
    queryKey: ['operators', 'grantable-roles'],
    queryFn: async (): Promise<string[] | null> => {
      try {
        const raw = await apiClient.get<{ roles?: string[] }>(
          '/api/operators/grantable-roles',
        );
        return raw.roles ?? null;
      } catch {
        return null;
      }
    },
  });
  const grantableRoles = grantableQuery.data ?? null;

  return (
    <section aria-labelledby="org-hierarchy-heading" className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 id="org-hierarchy-heading" className="text-2xl font-semibold">
          조직 계층
        </h1>
        <Button
          onClick={() => setCreateFor({ parentId: null })}
          data-testid="org-create-root"
        >
          루트 노드 생성
        </Button>
      </div>

      {nodesQuery.isError && (
        <p role="alert" className="text-sm text-destructive">
          {errText(nodesQuery.error)}
        </p>
      )}

      <div className="grid gap-6 md:grid-cols-[minmax(14rem,20rem)_1fr]">
        <div className="space-y-3">
          <OrgNodeTree
            nodes={nodes}
            selectedId={selectedId}
            onSelect={setSelectedId}
          />
        </div>

        <div className="min-w-0">
          {selected ? (
            <div className="space-y-3">
              <div className="flex justify-end">
                <Button
                  variant="secondary"
                  onClick={() =>
                    setCreateFor({ parentId: selected.orgNodeId })
                  }
                  data-testid="org-create-child"
                >
                  하위 노드 생성
                </Button>
              </div>
              <OrgNodeDetail
                key={selected.orgNodeId}
                node={selected}
                nodes={nodes}
                grantableRoles={grantableRoles}
              />
            </div>
          ) : (
            <p
              className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
              data-testid="org-detail-empty"
            >
              왼쪽 트리에서 노드를 선택하면 상세가 표시됩니다.
            </p>
          )}
        </div>
      </div>

      {createFor !== null && (
        <CreateNodePanel
          parentId={createFor.parentId}
          pending={create.isPending}
          errorMessage={create.isError ? errText(create.error) : null}
          onSubmit={(input, reason) => {
            create.mutate(
              { input: { ...input, parentId: createFor.parentId }, reason },
              {
                onSuccess: (node) => {
                  setSelectedId(node.orgNodeId);
                  setCreateFor(null);
                },
              },
            );
          }}
          onCancel={() => setCreateFor(null)}
        />
      )}
    </section>
  );
}

// ---------------------------------------------------------------------------
// Create panel — name + a ceiling radio pair (UNBOUNDED / BOUNDED) + domains,
// then the reason-capture gate. Kept inline (screen-local) — the create ceiling
// control is a lighter sibling of `CeilingEditor` (no parent-subset preview:
// the SERVER validates the subset on create, 422 surfaced verbatim).
// ---------------------------------------------------------------------------

interface CreateNodePanelProps {
  parentId: string | null;
  pending: boolean;
  errorMessage: string | null;
  onSubmit: (input: { name: string; ceiling: Ceiling }, reason: string) => void;
  onCancel: () => void;
}

function CreateNodePanel({
  parentId,
  pending,
  errorMessage,
  onSubmit,
  onCancel,
}: CreateNodePanelProps) {
  const [name, setName] = useState('');
  const [mode, setMode] = useState<'UNBOUNDED' | 'BOUNDED'>('UNBOUNDED');
  const [domains, setDomains] = useState<string[]>([]);
  const [confirming, setConfirming] = useState(false);

  const ceiling: Ceiling =
    mode === 'UNBOUNDED' ? { mode: 'UNBOUNDED' } : { mode: 'BOUNDED', domains };
  const nameOk = name.trim().length > 0;
  const lockOut = permitsNothing(ceiling);

  function toggleDomain(key: string) {
    setDomains((prev) =>
      prev.includes(key) ? prev.filter((d) => d !== key) : [...prev, key],
    );
  }

  return (
    <div
      className="fixed inset-0 z-40 flex items-center justify-center bg-black/50 px-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="org-create-title"
    >
      <div className="w-full max-w-lg space-y-3 rounded-lg border border-border bg-background p-6 shadow-lg">
        <h2 id="org-create-title" className="text-lg font-semibold text-foreground">
          {parentId === null ? '루트 노드 생성' : '하위 노드 생성'}
        </h2>

        <div className="flex flex-col gap-1">
          <label htmlFor="org-create-name" className="text-sm font-medium text-foreground">
            이름
          </label>
          <input
            id="org-create-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={100}
            data-testid="org-create-name"
            className="rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            placeholder="회사 / 부서 이름"
          />
        </div>

        <fieldset className="space-y-1">
          <legend className="text-sm font-medium text-foreground">
            상한 (entitlement ceiling)
          </legend>
          <p className="text-xs text-muted-foreground">
            상한은 사용 가능한 도메인을 좁히기만 하며, 어떤 권한도 새로 생기지
            않습니다.
          </p>
          <label className="flex items-center gap-2 text-sm text-foreground">
            <input
              type="radio"
              name="org-create-ceiling"
              checked={mode === 'UNBOUNDED'}
              onChange={() => setMode('UNBOUNDED')}
              data-testid="org-create-ceiling-unbounded"
            />
            제한 없음 (UNBOUNDED)
          </label>
          <label className="flex items-center gap-2 text-sm text-foreground">
            <input
              type="radio"
              name="org-create-ceiling"
              checked={mode === 'BOUNDED'}
              onChange={() => setMode('BOUNDED')}
              data-testid="org-create-ceiling-bounded"
            />
            상한 지정 (BOUNDED)
          </label>
        </fieldset>

        <fieldset className="flex flex-wrap gap-3" disabled={mode !== 'BOUNDED'}>
          <legend className="sr-only">허용 도메인</legend>
          {ORG_DOMAIN_KEYS.map((key) => (
            <label key={key} className="flex items-center gap-1.5 text-sm text-foreground">
              <input
                type="checkbox"
                checked={mode === 'BOUNDED' && domains.includes(key)}
                onChange={() => toggleDomain(key)}
                disabled={mode !== 'BOUNDED'}
                data-testid={`org-create-domain-${key}`}
              />
              {key}
            </label>
          ))}
        </fieldset>

        {lockOut && (
          <p
            role="alert"
            data-testid="org-create-lockout"
            className="rounded-md border border-amber-300/50 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:border-amber-700/40 dark:bg-amber-950/40 dark:text-amber-200"
          >
            이 회사의 모든 서비스가 어떤 도메인도 사용할 수 없게 됩니다.
          </p>
        )}

        {errorMessage && (
          <p role="alert" data-testid="org-create-error" className="text-sm text-destructive">
            {errorMessage}
          </p>
        )}

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onCancel} disabled={pending}>
            취소
          </Button>
          <Button
            onClick={() => setConfirming(true)}
            disabled={!nameOk || pending}
            data-testid="org-create-next"
          >
            다음
          </Button>
        </div>
      </div>

      {confirming && (
        <OrgReasonDialog
          title="조직 노드 생성"
          description={`"${name.trim()}" 노드를 생성합니다.`}
          confirmLabel="생성"
          pending={pending}
          error={errorMessage}
          onConfirm={(reason) => {
            onSubmit({ name: name.trim(), ceiling }, reason);
            setConfirming(false);
          }}
          onCancel={() => setConfirming(false)}
        />
      )}
    </div>
  );
}
