'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import type { OrgNode } from '../api/types';
import {
  useOrgNodeTenants,
  usePlacementCandidates,
  usePlacementPreview,
  usePlaceTenant,
  PLACEMENT_CONFLICT_CODE,
} from '../hooks/use-org-nodes';
import { OrgReasonDialog } from './OrgReasonDialog';

/**
 * «소속 테넌트» section of the org-node detail (TASK-PC-FE-237 read list →
 * TASK-PC-FE-312 adds the writes of `TASK-BE-625`, ADR-MONO-047 § 개정
 * 2026-10-07): [테넌트 추가] · per-tenant [다른 노드로 옮기기] · [빼기].
 *
 * Every write goes through ONE confirmation dialog that first reads the
 * server's preview of the effect (rider P2 — a ceiling is a confirmation, not
 * a refusal) and names the domains that turn off. The console never computes
 * that itself (an `ORG_ADMIN` cannot read subscriptions); the dialog cannot be
 * confirmed until the preview has loaded.
 *
 * 🔴 Out of reach on either side is a 404 from the server (never 403 — it
 *    would confirm existence). The screen says «no permission to manage this
 *    node or tenant» for ANY 404 and never guesses which one or whether it
 *    exists (task Failure Scenario 2).
 *
 * The list is the node + ALL descendants (`GET /{id}/tenants`), so a listed
 * tenant may sit under a child node — the dialog shows the real «from» node
 * from the preview instead of assuming it is this one.
 *
 * Sample visitors (ADR-MONO-074 R1ⓐ): the buttons stay, as everywhere else in
 * this feature; the gateway core refuses the write with `SAMPLE_READ_ONLY`.
 */

export const PLACEMENT_NOT_ADMINISTERED_COPY =
  '이 노드 또는 테넌트를 관리할 권한이 없습니다.';
export const PLACEMENT_CONFLICT_COPY =
  '확인하는 사이에 다른 요청이 이 테넌트의 소속을 먼저 바꿨습니다. 목록을 새로 불러왔으니 다시 시도하세요.';

function placementErrText(err: unknown): string | null {
  if (!err) return null;
  if (err instanceof ApiError) {
    // Any 404 — TENANT_NOT_FOUND (source side) or ORG_NODE_NOT_FOUND
    // (destination side) — is «out of my reach», never «does not exist».
    if (err.status === 404) return PLACEMENT_NOT_ADMINISTERED_COPY;
    if (err.code === PLACEMENT_CONFLICT_CODE) return PLACEMENT_CONFLICT_COPY;
    return messageForCode(err.code, err.message);
  }
  return '요청을 처리하지 못했습니다.';
}

function errText(err: unknown): string | null {
  if (!err) return null;
  if (err instanceof ApiError) return messageForCode(err.code, err.message);
  return '요청을 처리하지 못했습니다.';
}

type PlacementIntent =
  | { kind: 'add'; tenantId: string; toOrgNodeId: string }
  | { kind: 'move'; tenantId: string; toOrgNodeId: string }
  | { kind: 'detach'; tenantId: string; toOrgNodeId: null };

export interface TenantPlacementSectionProps {
  node: OrgNode;
  /** The reach-scoped flat node list — move destinations, node names, and the
   *  reach roots the add-candidate list is read from. */
  nodes: OrgNode[];
}

const SELECT_CLASS =
  'rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring';

export function TenantPlacementSection({ node, nodes }: TenantPlacementSectionProps) {
  const tenants = useOrgNodeTenants(node.orgNodeId);
  const place = usePlaceTenant();

  const [adding, setAdding] = useState(false);
  const [addSel, setAddSel] = useState('');
  const [movingTenant, setMovingTenant] = useState<string | null>(null);
  const [moveSel, setMoveSel] = useState('');
  const [intent, setIntent] = useState<PlacementIntent | null>(null);
  const [done, setDone] = useState<string | null>(null);

  const candidates = usePlacementCandidates(nodes, adding);
  const inSubtree = new Set(tenants.data ?? []);
  const addable = (candidates.data ?? []).filter((t) => !inSubtree.has(t));

  function openIntent(next: PlacementIntent) {
    place.reset();
    setDone(null);
    setIntent(next);
  }

  function nodeName(id: string | null): string {
    if (id === null) return '무소속';
    return nodes.find((n) => n.orgNodeId === id)?.name ?? '이름 확인 불가';
  }

  return (
    <section aria-labelledby="org-node-tenants-heading" className="space-y-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3
          id="org-node-tenants-heading"
          className="text-base font-semibold text-foreground"
        >
          소속 테넌트 (하위 노드 포함)
        </h3>
        <Button
          variant="secondary"
          size="sm"
          onClick={() => {
            setAdding((v) => !v);
            setAddSel('');
          }}
          aria-expanded={adding}
          data-testid="org-node-tenant-add"
        >
          테넌트 추가
        </Button>
      </div>

      {adding && (
        <div
          className="flex flex-wrap items-end gap-2 rounded-md border border-border p-3"
          data-testid="org-node-tenant-add-panel"
        >
          {candidates.isError ? (
            <p role="alert" className="text-sm text-destructive">
              {errText(candidates.error)}
            </p>
          ) : candidates.isLoading ? (
            <p className="text-sm text-muted-foreground">불러오는 중…</p>
          ) : addable.length === 0 ? (
            <p
              className="text-sm text-muted-foreground"
              data-testid="org-node-tenant-add-empty"
            >
              추가할 수 있는 테넌트가 없습니다.
            </p>
          ) : (
            <>
              <div className="flex flex-col gap-1">
                <label
                  htmlFor="org-node-tenant-add-select"
                  className="text-sm font-medium text-foreground"
                >
                  추가할 테넌트
                </label>
                <select
                  id="org-node-tenant-add-select"
                  value={addSel}
                  onChange={(e) => setAddSel(e.target.value)}
                  data-testid="org-node-tenant-add-select"
                  className={SELECT_CLASS}
                >
                  <option value="">테넌트 선택</option>
                  {addable.map((t) => (
                    <option key={t} value={t}>
                      {t}
                    </option>
                  ))}
                </select>
              </div>
              <Button
                onClick={() =>
                  openIntent({
                    kind: 'add',
                    tenantId: addSel,
                    toOrgNodeId: node.orgNodeId,
                  })
                }
                disabled={addSel === ''}
                data-testid="org-node-tenant-add-next"
              >
                다음
              </Button>
            </>
          )}
          <Button variant="ghost" onClick={() => setAdding(false)}>
            닫기
          </Button>
        </div>
      )}

      {tenants.isError ? (
        <p role="alert" className="text-sm text-destructive">
          {errText(tenants.error)}
        </p>
      ) : tenants.isLoading ? (
        <p className="text-sm text-muted-foreground">불러오는 중…</p>
      ) : (tenants.data?.length ?? 0) === 0 ? (
        <p className="text-sm text-muted-foreground" data-testid="org-node-tenants-empty">
          이 노드와 하위 노드에 소속된 테넌트가 없습니다.
        </p>
      ) : (
        <ul data-testid="org-node-tenants-list" className="space-y-1">
          {tenants.data?.map((t) => (
            <li
              key={t}
              data-testid={`org-node-tenant-row-${t}`}
              className="flex flex-wrap items-center gap-2 rounded-md border border-border bg-muted px-2 py-1 text-xs text-foreground"
            >
              <span className="font-mono">{t}</span>
              <span className="ml-auto flex flex-wrap items-center gap-1">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    setMovingTenant(movingTenant === t ? null : t);
                    setMoveSel('');
                  }}
                  aria-expanded={movingTenant === t}
                  data-testid={`org-node-tenant-move-${t}`}
                >
                  다른 노드로 옮기기
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() =>
                    openIntent({ kind: 'detach', tenantId: t, toOrgNodeId: null })
                  }
                  data-testid={`org-node-tenant-detach-${t}`}
                >
                  빼기
                </Button>
              </span>
              {movingTenant === t && (
                <span className="flex w-full flex-wrap items-end gap-2 pt-1">
                  <label htmlFor={`org-node-tenant-move-select-${t}`} className="sr-only">
                    {`${t} 를 옮길 노드`}
                  </label>
                  <select
                    id={`org-node-tenant-move-select-${t}`}
                    value={moveSel}
                    onChange={(e) => setMoveSel(e.target.value)}
                    data-testid={`org-node-tenant-move-select-${t}`}
                    className={SELECT_CLASS}
                  >
                    <option value="">옮길 노드 선택</option>
                    {nodes.map((n) => (
                      <option key={n.orgNodeId} value={n.orgNodeId}>
                        {n.name}
                      </option>
                    ))}
                  </select>
                  <Button
                    size="sm"
                    onClick={() =>
                      openIntent({ kind: 'move', tenantId: t, toOrgNodeId: moveSel })
                    }
                    disabled={moveSel === ''}
                    data-testid={`org-node-tenant-move-next-${t}`}
                  >
                    다음
                  </Button>
                </span>
              )}
            </li>
          ))}
        </ul>
      )}

      {done && (
        <p role="status" data-testid="org-node-tenant-placement-done" className="text-sm text-foreground">
          {done}
        </p>
      )}

      {intent && (
        <PlacementConfirmDialog
          intent={intent}
          nodeName={nodeName}
          pending={place.isPending}
          error={place.isError ? placementErrText(place.error) : null}
          onConfirm={(reason) =>
            place.mutate(
              {
                tenantId: intent.tenantId,
                toOrgNodeId: intent.toOrgNodeId,
                reason,
              },
              {
                onSuccess: (result) => {
                  setIntent(null);
                  setAdding(false);
                  setMovingTenant(null);
                  setDone(
                    result.changed
                      ? `"${result.tenantId}" 의 소속을 ${nodeName(result.toOrgNodeId)} (으)로 바꿨습니다.`
                      : `"${result.tenantId}" 는 이미 ${nodeName(result.toOrgNodeId)} 에 있어 바뀐 것이 없습니다.`,
                  );
                },
              },
            )
          }
          onCancel={() => setIntent(null)}
        />
      )}
    </section>
  );
}

// ---------------------------------------------------------------------------
// Confirmation — reads the preview, names the lost domains, captures a reason.
// ---------------------------------------------------------------------------

interface PlacementConfirmDialogProps {
  intent: PlacementIntent;
  nodeName: (id: string | null) => string;
  pending: boolean;
  error: string | null;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
}

const COPY: Record<PlacementIntent['kind'], { title: string; confirm: string }> = {
  add: { title: '테넌트 추가', confirm: '추가' },
  move: { title: '테넌트 옮기기', confirm: '옮기기' },
  detach: { title: '테넌트 빼기', confirm: '빼기' },
};

function PlacementConfirmDialog({
  intent,
  nodeName,
  pending,
  error,
  onConfirm,
  onCancel,
}: PlacementConfirmDialogProps) {
  const preview = usePlacementPreview(intent.tenantId, intent.toOrgNodeId);
  const effect = preview.data;

  const description =
    intent.kind === 'detach'
      ? `"${intent.tenantId}" 테넌트를 조직 계층에서 뺍니다 (무소속).`
      : intent.kind === 'add'
        ? `"${intent.tenantId}" 테넌트를 "${nodeName(intent.toOrgNodeId)}" 노드에 둡니다.`
        : `"${intent.tenantId}" 테넌트를 "${nodeName(intent.toOrgNodeId)}" 노드로 옮깁니다.`;

  return (
    <OrgReasonDialog
      title={COPY[intent.kind].title}
      description={description}
      confirmLabel={COPY[intent.kind].confirm}
      pending={pending}
      error={error}
      confirmBlocked={!preview.isSuccess}
      onConfirm={onConfirm}
      onCancel={onCancel}
    >
      <div className="mt-4 space-y-2 text-sm" data-testid="org-placement-effect">
        {preview.isError ? (
          <p role="alert" data-testid="org-placement-preview-error" className="text-destructive">
            {placementErrText(preview.error)}
          </p>
        ) : !effect ? (
          <p className="text-muted-foreground" data-testid="org-placement-preview-loading">
            바뀌는 도메인을 확인하는 중…
          </p>
        ) : (
          <>
            <p className="text-foreground" data-testid="org-placement-route">
              지금 위치: {nodeName(effect.fromOrgNodeId)} → 목적지:{' '}
              {nodeName(effect.toOrgNodeId)}
            </p>
            {effect.fromOrgNodeId === effect.toOrgNodeId && (
              <p className="text-muted-foreground" data-testid="org-placement-noop">
                이미 이 위치에 있습니다. 확인해도 바뀌는 것이 없습니다.
              </p>
            )}
            {effect.lostDomains.length > 0 && (
              <div
                data-testid="org-placement-lost-domains"
                className="rounded-md border border-amber-300/50 bg-amber-50 px-3 py-2 text-amber-900 dark:border-amber-700/40 dark:bg-amber-950/40 dark:text-amber-200"
              >
                <p className="font-medium">
                  이동하면 이 도메인들이 꺼집니다: {effect.lostDomains.join(', ')}
                </p>
                <p className="mt-1 text-xs">
                  목적지 노드의 상한 때문입니다. 구독은 그대로 남아 있어, 빼거나
                  넓은 노드로 옮기면 다시 켜집니다.
                </p>
              </div>
            )}
            {effect.gainedDomains.length > 0 && (
              <p className="text-foreground" data-testid="org-placement-gained-domains">
                이동하면 이 도메인들이 다시 켜집니다: {effect.gainedDomains.join(', ')}
              </p>
            )}
          </>
        )}
      </div>
    </OrgReasonDialog>
  );
}
