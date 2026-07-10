'use client';

import { useState } from 'react';
import { DetailHeader } from '@/shared/ui/DetailHeader';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { formatDateTime } from '@/shared/lib/datetime';
import type { OrgNode, Ceiling } from '../api/types';
import {
  descendantIds,
  effectiveCeilingOf,
} from '../lib/tree';
import {
  useOrgNodeTenants,
  useOrgNodeAdmins,
  useSetCeiling,
  useUpdateOrgNode,
  useDeleteOrgNode,
  useGrantOrgAdmin,
  useRevokeOrgAdmin,
} from '../hooks/use-org-nodes';
import { CeilingEditor } from './CeilingEditor';
import { OrgAdminPanel } from './OrgAdminPanel';
import { OrgReasonDialog } from './OrgReasonDialog';

/**
 * Org-node detail panel (TASK-PC-FE-237). Shared `DetailHeader` ghost back
 * button + a `<dl>` in the house order 명칭 → 상태(depth / 상한 요약) →
 * 식별자(orgNodeId / parentId) → 날짜(createdAt / updatedAt via
 * `formatDateTime`). Sections: 상한 (`CeilingEditor`), 소속 테넌트
 * (`useOrgNodeTenants` — node + all descendants), 노드 관리자
 * (`OrgAdminPanel`). Rename / re-parent / delete actions each go through the
 * reason-capture gate.
 *
 * Mount with `key={node.orgNodeId}` from the screen so all local editor state
 * resets cleanly when a different node is selected.
 */
export interface OrgNodeDetailProps {
  node: OrgNode;
  /** Full flat node list — parent-effective ceiling, re-parent targets, and
   *  the descendant cycle-guard are all derived from it. */
  nodes: OrgNode[];
  /** Caller's grantable roles (fetched by the screen); `null` ⇒ full known set. */
  grantableRoles: string[] | null;
}

function errText(err: unknown): string | null {
  if (!err) return null;
  if (err instanceof ApiError) return messageForCode(err.code, err.message);
  return '요청을 처리하지 못했습니다.';
}

function describeCeiling(c: Ceiling): string {
  if (c.mode === 'UNBOUNDED') return '제한 없음 (UNBOUNDED)';
  if (c.domains.length === 0) return '허용 도메인 없음 (BOUNDED, 전면 차단)';
  return `상한: ${c.domains.join(', ')}`;
}

export function OrgNodeDetail({ node, nodes, grantableRoles }: OrgNodeDetailProps) {
  const parentEffective: Ceiling =
    node.parentId !== null
      ? effectiveCeilingOf(nodes, node.parentId)
      : { mode: 'UNBOUNDED' };

  const tenants = useOrgNodeTenants(node.orgNodeId);
  const admins = useOrgNodeAdmins(node.orgNodeId);
  const setCeiling = useSetCeiling();
  const update = useUpdateOrgNode();
  const remove = useDeleteOrgNode();
  const grant = useGrantOrgAdmin();
  const revoke = useRevokeOrgAdmin();

  // rename
  const [name, setName] = useState(node.name);
  const [renaming, setRenaming] = useState(false);
  // re-parent — '' means "(루트)" (parentId = null)
  const descendants = descendantIds(nodes, node.orgNodeId);
  const reparentTargets = nodes.filter(
    (n) => n.orgNodeId !== node.orgNodeId && !descendants.has(n.orgNodeId),
  );
  const [parentSel, setParentSel] = useState<string>(node.parentId ?? '');
  const [reparenting, setReparenting] = useState(false);
  // delete
  const [deleting, setDeleting] = useState(false);

  const nameChanged = name.trim().length > 0 && name.trim() !== node.name;
  const parentChanged = parentSel !== (node.parentId ?? '');

  return (
    <div className="space-y-6">
      <DetailHeader
        headingId="org-node-detail-heading"
        title={`${node.name} 상세`}
        backHref="/org-hierarchy"
        backTestId="org-node-detail-back"
        actions={
          <Button
            variant="ghost"
            onClick={() => setDeleting(true)}
            disabled={remove.isPending}
            data-testid="org-node-delete"
          >
            삭제
          </Button>
        }
      />

      <dl className="grid grid-cols-[8rem_1fr] gap-x-4 gap-y-2 text-sm">
        <dt className="text-muted-foreground">명칭</dt>
        <dd className="font-medium text-foreground" data-testid="org-node-name">
          {node.name}
        </dd>

        <dt className="text-muted-foreground">깊이 / 상한</dt>
        <dd className="text-foreground" data-testid="org-node-status">
          깊이 {node.depth} · {describeCeiling(node.ceiling)}
        </dd>

        <dt className="text-muted-foreground">노드 ID</dt>
        <dd className="break-all font-mono text-xs text-foreground">
          {node.orgNodeId}
        </dd>

        <dt className="text-muted-foreground">상위 노드 ID</dt>
        <dd className="break-all font-mono text-xs text-foreground">
          {node.parentId ?? '(루트)'}
        </dd>

        <dt className="text-muted-foreground">생성 / 수정</dt>
        <dd className="text-foreground">
          {formatDateTime(node.createdAt)} / {formatDateTime(node.updatedAt)}
        </dd>
      </dl>

      {/* rename + re-parent */}
      <section aria-labelledby="org-node-edit-heading" className="space-y-3">
        <h3
          id="org-node-edit-heading"
          className="text-base font-semibold text-foreground"
        >
          이름 · 위치 변경
        </h3>

        <div className="flex flex-wrap items-end gap-2">
          <div className="flex flex-col gap-1">
            <label htmlFor="org-node-name-input" className="text-sm font-medium text-foreground">
              이름
            </label>
            <input
              id="org-node-name-input"
              value={name}
              onChange={(e) => setName(e.target.value)}
              maxLength={100}
              data-testid="org-node-name-input"
              className="rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />
          </div>
          <Button
            onClick={() => setRenaming(true)}
            disabled={!nameChanged || update.isPending}
            data-testid="org-node-rename"
          >
            이름 변경
          </Button>
        </div>

        <div className="flex flex-wrap items-end gap-2">
          <div className="flex flex-col gap-1">
            <label htmlFor="org-node-parent-select" className="text-sm font-medium text-foreground">
              상위 노드
            </label>
            <select
              id="org-node-parent-select"
              value={parentSel}
              onChange={(e) => setParentSel(e.target.value)}
              data-testid="org-node-parent-select"
              className="rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <option value="">(루트)</option>
              {reparentTargets.map((n) => (
                <option key={n.orgNodeId} value={n.orgNodeId}>
                  {n.name}
                </option>
              ))}
            </select>
          </div>
          <Button
            onClick={() => setReparenting(true)}
            disabled={!parentChanged || update.isPending}
            data-testid="org-node-reparent"
          >
            재부모화
          </Button>
        </div>

        {update.isError && (
          <p role="alert" data-testid="org-node-edit-error" className="text-sm text-destructive">
            {errText(update.error)}
          </p>
        )}
        {remove.isError && (
          <p role="alert" data-testid="org-node-delete-error" className="text-sm text-destructive">
            {errText(remove.error)}
          </p>
        )}
      </section>

      <CeilingEditor
        node={node}
        parentEffective={parentEffective}
        pending={setCeiling.isPending}
        errorMessage={errText(setCeiling.error)}
        onSubmit={(ceiling, reason) =>
          setCeiling.mutate({ id: node.orgNodeId, ceiling, reason })
        }
      />

      <section aria-labelledby="org-node-tenants-heading" className="space-y-2">
        <h3
          id="org-node-tenants-heading"
          className="text-base font-semibold text-foreground"
        >
          소속 테넌트 (하위 노드 포함)
        </h3>
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
          <ul data-testid="org-node-tenants-list" className="flex flex-wrap gap-2">
            {tenants.data?.map((t) => (
              <li
                key={t}
                className="rounded-md border border-border bg-muted px-2 py-1 text-xs text-foreground"
              >
                {t}
              </li>
            ))}
          </ul>
        )}
      </section>

      <OrgAdminPanel
        node={node}
        admins={admins.data ?? []}
        adminsLoading={admins.isLoading}
        adminsError={admins.isError ? errText(admins.error) : null}
        grantableRoles={grantableRoles}
        grantPending={grant.isPending}
        grantError={grant.isError ? errText(grant.error) : null}
        revokePending={revoke.isPending}
        revokeError={revoke.isError ? errText(revoke.error) : null}
        onGrant={(operatorId, roleName, reason) =>
          grant.mutate({ id: node.orgNodeId, input: { operatorId, roleName }, reason })
        }
        onRevoke={(operatorId, reason) =>
          revoke.mutate({ id: node.orgNodeId, operatorId, reason })
        }
      />

      {renaming && (
        <OrgReasonDialog
          title="노드 이름 변경"
          description={`이 노드의 이름을 "${name.trim()}" 로 변경합니다.`}
          confirmLabel="이름 변경"
          pending={update.isPending}
          error={update.isError ? errText(update.error) : null}
          onConfirm={(reason) => {
            update.mutate({ id: node.orgNodeId, patch: { name: name.trim() }, reason });
            setRenaming(false);
          }}
          onCancel={() => setRenaming(false)}
        />
      )}

      {reparenting && (
        <OrgReasonDialog
          title="노드 재부모화"
          description={
            parentSel === ''
              ? '이 노드를 최상위(루트)로 옮깁니다.'
              : `이 노드를 다른 상위 노드 아래로 옮깁니다.`
          }
          confirmLabel="재부모화"
          pending={update.isPending}
          error={update.isError ? errText(update.error) : null}
          onConfirm={(reason) => {
            update.mutate({
              id: node.orgNodeId,
              patch: { parentId: parentSel === '' ? null : parentSel },
              reason,
            });
            setReparenting(false);
          }}
          onCancel={() => setReparenting(false)}
        />
      )}

      {deleting && (
        <OrgReasonDialog
          title="노드 삭제"
          description="자식 노드나 소속 테넌트가 있으면 서버가 삭제를 거부합니다 (먼저 비워야 합니다)."
          confirmLabel="삭제"
          tone="destructive"
          pending={remove.isPending}
          error={remove.isError ? errText(remove.error) : null}
          onConfirm={(reason) => {
            remove.mutate({ id: node.orgNodeId, reason });
            setDeleting(false);
          }}
          onCancel={() => setDeleting(false)}
        />
      )}
    </div>
  );
}
