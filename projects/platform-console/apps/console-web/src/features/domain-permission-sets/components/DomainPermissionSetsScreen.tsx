'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { formatDateTime } from '@/shared/lib/datetime';
import {
  useDomainPermissionSets,
  useCreateDomainPermissionSet,
  useUpdateDomainPermissionSet,
  useDeleteDomainPermissionSet,
} from '../hooks/use-domain-permission-sets';
import type {
  DomainRole,
  DomainPermissionSet,
  DomainSetRoleRef,
} from '../api/types';
import { DomainPermissionSetForm } from './DomainPermissionSetForm';
import { DomainSetReasonDialog, DOMAIN_SET_LATENCY_COPY } from './DomainSetReasonDialog';

/**
 * 「업무 권한 세트」 management screen (TASK-PC-FE-338 / ADR-MONO-083 D9,
 * contract § 2.4.3.4 screen 1) — presets first (badge «시스템», read-only),
 * then the active tenant's sets with `grantCount`/`groupCount`. Mirrors
 * `OperatorGroupsScreen` (seeded list + reason+confirm create/edit/delete).
 *
 * 🔴 Status honesty (ADR-083 D9): P3 (the token-issuance change) is not
 * deployed — a set recorded here does not yet narrow any assume-tenant
 * token. The screen carries a persistent «적용 예정» note; it never claims a
 * set currently limits access.
 */
export interface DomainPermissionSetsScreenProps {
  roles: DomainRole[];
  initialSets: DomainPermissionSet[];
  /** `null` ⇒ unknown — the form must never disable a domain in that case. */
  subscribedDomains: string[] | null;
}

type Draft = { name: string; description?: string; roles: DomainSetRoleRef[] };
type FormState =
  | { mode: 'create'; prefillRoles?: DomainSetRoleRef[] }
  | { mode: 'edit'; set: DomainPermissionSet }
  | null;

function errText(err: unknown): string | null {
  if (!err) return null;
  if (err instanceof ApiError) return messageForCode(err.code, err.message);
  return '요청을 처리하지 못했습니다.';
}

function newIdempotencyKey(prefix: string): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${prefix}-${Date.now()}`;
}

export function DomainPermissionSetsScreen({
  roles,
  initialSets,
  subscribedDomains,
}: DomainPermissionSetsScreenProps) {
  const list = useDomainPermissionSets(initialSets);
  const sets = list.data ?? [];
  const presets = sets.filter((s) => s.readOnly);
  const tenantSets = sets.filter((s) => !s.readOnly);

  const create = useCreateDomainPermissionSet();
  const update = useUpdateDomainPermissionSet();
  const remove = useDeleteDomainPermissionSet();

  const [form, setForm] = useState<FormState>(null);
  const [pendingCreateDraft, setPendingCreateDraft] = useState<Draft | null>(null);
  const [pendingEditDraft, setPendingEditDraft] = useState<
    { set: DomainPermissionSet; draft: Draft } | null
  >(null);
  const [idempotencyKey, setIdempotencyKey] = useState<string | null>(null);
  const [deletingSet, setDeletingSet] = useState<DomainPermissionSet | null>(null);

  const createError = create.isError ? errText(create.error) : null;
  const updateError = update.isError ? errText(update.error) : null;
  const deleteError = remove.isError ? errText(remove.error) : null;
  const listError = list.isError ? errText(list.error) : null;

  function openCreateDraft(draft: Draft) {
    create.reset();
    setIdempotencyKey(newIdempotencyKey('domain-set-create'));
    setPendingCreateDraft(draft);
  }

  function confirmCreate(reason: string) {
    if (!pendingCreateDraft || !idempotencyKey) return;
    create.mutate(
      { input: pendingCreateDraft, reason, idempotencyKey },
      {
        onSuccess: () => {
          setPendingCreateDraft(null);
          setForm(null);
        },
      },
    );
  }

  function openEditDraft(set: DomainPermissionSet, draft: Draft) {
    update.reset();
    setPendingEditDraft({ set, draft });
  }

  function confirmEdit(reason: string) {
    if (!pendingEditDraft) return;
    update.mutate(
      {
        setId: pendingEditDraft.set.setId,
        patch: pendingEditDraft.draft,
        reason,
      },
      {
        onSuccess: () => {
          setPendingEditDraft(null);
          setForm(null);
        },
      },
    );
  }

  function confirmDelete(reason: string) {
    if (!deletingSet) return;
    remove.mutate(
      { setId: deletingSet.setId, reason },
      { onSuccess: () => setDeletingSet(null) },
    );
  }

  return (
    <section aria-labelledby="domain-permission-sets-heading" className="space-y-6">
      <div>
        <h1 id="domain-permission-sets-heading" className="mb-2 text-2xl font-semibold">
          업무 권한 세트
        </h1>
        <p className="text-sm text-muted-foreground">
          테넌트별로 운영자·그룹에게 줄 도메인 업무 역할(WMS·SCM 등)을 세트로
          묶어 관리합니다. IAM 관리 역할과는 다른 이름 공간입니다 — 이 세트는
          관리 권한(<code className="font-mono text-xs">/api/admin/**</code>)을
          주지 않습니다.
        </p>
      </div>

      <div
        role="note"
        data-testid="domain-set-applies-later-note"
        className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
      >
        🔴 지금 배정한 세트는 기록만 되고{' '}
        <strong className="text-foreground">아직 실제 업무 권한을 좁히지 않습니다</strong> —{' '}
        <strong className="text-foreground">적용 예정</strong>입니다. 토큰 발급
        변경이 배포되면, 운영자가 테넌트를 다시 선택할 때부터 이 세트의
        역할만 토큰에 실립니다. 그 전까지는 테넌트의 구독 도메인 전체가
        그대로 적용됩니다.
      </div>

      {listError && (
        <p role="alert" data-testid="domain-set-list-error" className="text-sm text-destructive">
          {listError}
        </p>
      )}

      {!form && (
        <Button
          onClick={() => setForm({ mode: 'create' })}
          data-testid="domain-set-add"
        >
          새 세트 등록
        </Button>
      )}

      {form && (
        <DomainPermissionSetForm
          key={form.mode === 'edit' ? form.set.setId : 'create'}
          mode={form.mode}
          roles={roles}
          subscribedDomains={subscribedDomains}
          initial={form.mode === 'edit' ? form.set : undefined}
          prefillRoles={form.mode === 'create' ? form.prefillRoles : undefined}
          serverError={form.mode === 'create' ? createError : updateError}
          pending={form.mode === 'create' ? create.isPending : update.isPending}
          onCancel={() => setForm(null)}
          onSubmitDraft={(draft) => {
            if (form.mode === 'create') openCreateDraft(draft);
            else openEditDraft(form.set, draft);
          }}
        />
      )}

      {/* presets — read-only (badge «시스템»), no edit/delete */}
      <section aria-labelledby="domain-set-presets-heading" className="space-y-3">
        <h2 id="domain-set-presets-heading" className="text-lg font-semibold text-foreground">
          시스템 프리셋
        </h2>
        {presets.length === 0 ? (
          <p className="text-sm text-muted-foreground" data-testid="domain-set-presets-empty">
            프리셋이 없습니다.
          </p>
        ) : (
          <ul data-testid="domain-set-presets-list" className="divide-y divide-border rounded-md border border-border">
            {presets.map((p) => (
              <li
                key={p.setId}
                data-testid={`domain-set-preset-${p.setId}`}
                className="flex items-center justify-between gap-3 px-3 py-2 text-sm"
              >
                <span className="min-w-0">
                  <span
                    data-testid={`domain-set-preset-${p.setId}-badge`}
                    className="mr-2 rounded bg-muted px-1.5 py-0.5 text-xs font-medium text-foreground"
                  >
                    시스템
                  </span>
                  <span className="font-medium text-foreground">{p.name}</span>{' '}
                  <span className="text-muted-foreground">
                    · {p.roles.map((r) => `${r.domainKey}:${r.role}`).join(', ')}
                  </span>
                </span>
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() =>
                    setForm({
                      mode: 'create',
                      prefillRoles: p.roles.map((r) => ({ domainKey: r.domainKey, role: r.role })),
                    })
                  }
                  data-testid={`domain-set-preset-${p.setId}-clone`}
                >
                  복제해서 새 세트
                </Button>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* tenant sets */}
      <section aria-labelledby="domain-set-tenant-heading" className="space-y-3">
        <h2 id="domain-set-tenant-heading" className="text-lg font-semibold text-foreground">
          테넌트 세트
        </h2>
        {deleteError && (
          <p role="alert" data-testid="domain-set-delete-error" className="text-sm text-destructive">
            {deleteError}
          </p>
        )}
        {tenantSets.length === 0 ? (
          <p className="text-sm text-muted-foreground" data-testid="domain-set-tenant-empty">
            등록된 테넌트 세트가 없습니다.
          </p>
        ) : (
          <ul data-testid="domain-set-tenant-list" className="divide-y divide-border rounded-md border border-border">
            {tenantSets.map((s) => (
              <li
                key={s.setId}
                data-testid={`domain-set-tenant-${s.setId}`}
                className="flex items-center justify-between gap-3 px-3 py-2 text-sm"
              >
                <span className="min-w-0">
                  <span className="font-medium text-foreground">{s.name}</span>{' '}
                  <span className="text-muted-foreground">
                    · 배정 {s.grantCount} · 그룹 {s.groupCount} ·{' '}
                    {s.roles.map((r) => `${r.domainKey}:${r.role}`).join(', ')} ·{' '}
                    {formatDateTime(s.updatedAt)}
                  </span>
                </span>
                <span className="flex gap-2">
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => setForm({ mode: 'edit', set: s })}
                    data-testid={`domain-set-tenant-${s.setId}-edit`}
                  >
                    수정
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setDeletingSet(s)}
                    disabled={remove.isPending}
                    data-testid={`domain-set-tenant-${s.setId}-delete`}
                  >
                    삭제
                  </Button>
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* --- confirm dialogs -------------------------------------------- */}

      {pendingCreateDraft && (
        <DomainSetReasonDialog
          title="업무 권한 세트를 등록할까요?"
          description={`"${pendingCreateDraft.name}" 세트를 등록합니다.`}
          confirmLabel="등록"
          pending={create.isPending}
          error={createError}
          onConfirm={confirmCreate}
          onCancel={() => setPendingCreateDraft(null)}
        />
      )}

      {pendingEditDraft && (
        <DomainSetReasonDialog
          title="업무 권한 세트를 수정할까요?"
          description={`"${pendingEditDraft.set.name}" 세트를 수정합니다. ${DOMAIN_SET_LATENCY_COPY}`}
          confirmLabel="수정"
          pending={update.isPending}
          error={updateError}
          onConfirm={confirmEdit}
          onCancel={() => setPendingEditDraft(null)}
        />
      )}

      {deletingSet && (
        <DomainSetReasonDialog
          title="업무 권한 세트를 삭제할까요?"
          description={`"${deletingSet.name}" 세트를 삭제합니다. 배정이 남아 있으면 거절됩니다(먼저 회수하세요). ${DOMAIN_SET_LATENCY_COPY}`}
          confirmLabel="삭제"
          tone="destructive"
          pending={remove.isPending}
          error={deleteError}
          onConfirm={confirmDelete}
          onCancel={() => setDeletingSet(null)}
        />
      )}
    </section>
  );
}
