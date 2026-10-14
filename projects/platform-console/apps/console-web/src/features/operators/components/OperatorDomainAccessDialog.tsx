'use client';

import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { DOMAIN_SET_LATENCY_COPY } from '@/shared/api/domain-permission-sets-copy';
import {
  useOperatorDomainAccess,
  useGrantableDomainSets,
  useGrantOperatorDomainSet,
  useRevokeOperatorDomainSet,
} from '../hooks/use-domain-access';
import type { DomainAccessSet } from '../api/domain-access-types';
import { OperatorDomainSetReasonDialog } from './OperatorDomainSetReasonDialog';

/**
 * Per-operator ADR-MONO-083 「업무 권한」 dialog (TASK-PC-FE-338 slice b —
 * contract § 2.4.3.4 screen 2 / rows 6-8). Mounted from `OperatorsScreen`'s
 * per-row «업무 권한» button (mirrors `OrgScopeDialog` — a self-fetching
 * dialog keyed by target operatorId):
 *
 *   - per-set sources (DIRECT / MIGRATION / «그룹: {name}», + «모든 테넌트»
 *     for a `tenantId='*'` row) — revoke button ONLY on a set carrying a
 *     DIRECT or MIGRATION source;
 *   - the summed effective roles grouped by domain, with a role of a domain
 *     the active tenant has not subscribed to marked «구독 없음 — 토큰에
 *     실리지 않음»;
 *   - grant (pick a set → reason; NO Idempotency-Key — a repeat is `409
 *     DOMAIN_PERMISSION_SET_ALREADY_GRANTED`), including the self-grant
 *     confirm copy (OD-4) when the target IS the caller;
 *   - the persistent «적용 예정» note (P3 not deployed — ADR-083 D9).
 *
 * 🔴 Fail-open (task Edge Case): a `DomainAccessUnavailableError`-class 503/
 * timeout degrades ONLY this dialog's body — it never claims "no permission".
 */
export interface OperatorDomainAccessDialogProps {
  open: boolean;
  operatorId: string;
  operatorLabel: string;
  /** True when the target IS the caller (OD-4 self-grant confirm copy). */
  isSelf: boolean;
  /** `null` ⇒ unknown — a role is never marked «구독 없음» in that case. */
  subscribedDomains: string[] | null;
  onClose: () => void;
}

function errText(err: unknown): string | null {
  if (!err) return null;
  if (err instanceof ApiError) return messageForCode(err.code, err.message);
  return '요청을 처리하지 못했습니다.';
}

function sourceLabel(source: string, groupName: string | null | undefined): string {
  if (source === 'DIRECT') return '직접';
  if (source === 'MIGRATION') return '이전 호환';
  if (source === 'GROUP') return `그룹: ${groupName ?? '(이름 없음)'}`;
  return source;
}

export function OperatorDomainAccessDialog({
  open,
  operatorId,
  operatorLabel,
  isSelf,
  subscribedDomains,
  onClose,
}: OperatorDomainAccessDialogProps) {
  const titleId = useId();
  const descId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);

  const access = useOperatorDomainAccess(open ? operatorId : null);
  const grant = useGrantOperatorDomainSet();
  const revoke = useRevokeOperatorDomainSet();

  const [addingGrant, setAddingGrant] = useState(false);
  const [pickedSetId, setPickedSetId] = useState('');
  const [confirmingGrant, setConfirmingGrant] = useState(false);
  const [revokingSet, setRevokingSet] = useState<DomainAccessSet | null>(null);

  const grantableSets = useGrantableDomainSets(addingGrant);

  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
      }
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  const rolesByDomain = useMemo(() => {
    const m = new Map<string, string[]>();
    for (const r of access.data?.roles ?? []) {
      const arr = m.get(r.domainKey) ?? [];
      arr.push(r.role);
      m.set(r.domainKey, arr);
    }
    return m;
  }, [access.data]);

  if (!open) return null;

  const accessError = access.isError ? errText(access.error) : null;
  const grantError = grant.isError ? errText(grant.error) : null;
  const revokeError = revoke.isError ? errText(revoke.error) : null;

  const sets = access.data?.sets ?? [];

  function openGrant() {
    grant.reset();
    setPickedSetId('');
    setAddingGrant(true);
  }

  function confirmGrant(reason: string) {
    if (!pickedSetId) return;
    grant.mutate(
      { operatorId, setId: pickedSetId, reason },
      {
        onSuccess: () => {
          setConfirmingGrant(false);
          setAddingGrant(false);
          setPickedSetId('');
        },
      },
    );
  }

  function confirmRevoke(reason: string) {
    if (!revokingSet) return;
    revoke.mutate(
      { operatorId, setId: revokingSet.setId, reason },
      { onSuccess: () => setRevokingSet(null) },
    );
  }

  const revokingGroupNames = revokingSet
    ? revokingSet.sources
        .filter((s) => s.source === 'GROUP')
        .map((s) => s.groupName ?? '(이름 없음)')
    : [];

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      data-testid="operator-domain-access-overlay"
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descId}
        data-testid="operator-domain-access-dialog"
        className="w-full max-w-lg overflow-y-auto rounded-lg border border-border bg-background p-6 shadow-lg"
        style={{ maxHeight: '90vh' }}
      >
        <h2 id={titleId} className="text-lg font-semibold text-foreground">
          업무 권한 — {operatorLabel}
        </h2>
        <p id={descId} className="mt-2 text-sm text-muted-foreground">
          이 운영자가 활성 테넌트에서 가진 업무 권한 세트와 출처, 합산 실효
          역할입니다. 직접·이전 호환 출처만 이 화면에서 회수할 수 있습니다 —
          그룹에서 받은 세트는 그 그룹에서 빼야 사라집니다.
        </p>

        <div
          role="note"
          data-testid="operator-domain-access-applies-later-note"
          className="mt-4 rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground"
        >
          🔴 아직 실제 업무 권한을 좁히지 않습니다 — <strong className="text-foreground">적용 예정</strong>
          입니다 (토큰 발급 변경 배포 전).
        </div>

        {accessError && (
          <p
            role="alert"
            data-testid="operator-domain-access-error"
            className="mt-4 text-sm text-destructive"
          >
            {accessError}
          </p>
        )}

        {!accessError && access.isLoading && (
          <p className="mt-4 text-sm text-muted-foreground">불러오는 중…</p>
        )}

        {!accessError && !access.isLoading && (
          <>
            <section className="mt-4 space-y-2">
              <h3 className="text-sm font-semibold text-foreground">배정된 세트</h3>
              {sets.length === 0 ? (
                <p
                  className="text-sm text-muted-foreground"
                  data-testid="operator-domain-access-sets-empty"
                >
                  배정된 업무 권한 세트가 없습니다.
                </p>
              ) : (
                <ul
                  data-testid="operator-domain-access-sets-list"
                  className="divide-y divide-border rounded-md border border-border"
                >
                  {sets.map((s) => {
                    const canRevoke = s.sources.some(
                      (src) => src.source === 'DIRECT' || src.source === 'MIGRATION',
                    );
                    return (
                      <li
                        key={s.setId}
                        data-testid={`operator-domain-access-set-${s.setId}`}
                        className="space-y-1 px-3 py-2 text-sm"
                      >
                        <div className="flex items-center justify-between gap-3">
                          <span className="font-medium text-foreground">{s.name}</span>
                          {canRevoke && (
                            <Button
                              variant="ghost"
                              size="sm"
                              className="text-destructive"
                              onClick={() => setRevokingSet(s)}
                              disabled={revoke.isPending}
                              data-testid={`operator-domain-access-set-${s.setId}-revoke`}
                            >
                              회수
                            </Button>
                          )}
                        </div>
                        <div className="flex flex-wrap gap-1.5">
                          {s.sources.map((src, i) => (
                            <span
                              key={`${src.source}-${src.groupId ?? ''}-${i}`}
                              data-testid={`operator-domain-access-set-${s.setId}-source-${i}`}
                              className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground"
                            >
                              {sourceLabel(src.source, src.groupName)}
                              {src.tenantId === '*' && (
                                <>
                                  {' · '}
                                  <span
                                    data-testid={`operator-domain-access-set-${s.setId}-source-${i}-all-tenants`}
                                  >
                                    모든 테넌트
                                  </span>
                                </>
                              )}
                            </span>
                          ))}
                        </div>
                        {s.sources.some((src) => src.source === 'GROUP') && (
                          <div className="flex flex-wrap gap-2">
                            {s.sources
                              .filter((src) => src.source === 'GROUP')
                              .map((src, i) => (
                                <a
                                  key={`${src.groupId}-${i}`}
                                  href="/operator-groups"
                                  className="text-xs text-primary underline"
                                  data-testid={`operator-domain-access-set-${s.setId}-group-link-${i}`}
                                >
                                  그룹: {src.groupName ?? src.groupId} →
                                </a>
                              ))}
                          </div>
                        )}
                      </li>
                    );
                  })}
                </ul>
              )}

              <Button onClick={openGrant} data-testid="operator-domain-access-add">
                업무 권한 추가
              </Button>

              {addingGrant && (
                <div className="space-y-2 rounded-md border border-border p-3">
                  {grantableSets.isLoading ? (
                    <p className="text-sm text-muted-foreground">불러오는 중…</p>
                  ) : grantableSets.isError ? (
                    <p
                      role="alert"
                      data-testid="operator-domain-access-grant-options-error"
                      className="text-sm text-destructive"
                    >
                      세트 목록을 불러오지 못했습니다.
                    </p>
                  ) : (
                    <select
                      value={pickedSetId}
                      onChange={(e) => setPickedSetId(e.target.value)}
                      data-testid="operator-domain-access-grant-select"
                      className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                    >
                      <option value="">(세트 선택)</option>
                      {(grantableSets.data ?? []).map((opt) => (
                        <option key={opt.setId} value={opt.setId}>
                          {opt.name}
                          {opt.systemKey ? ' (시스템)' : ''} — {opt.domains.join(', ')}
                        </option>
                      ))}
                    </select>
                  )}
                  <div className="flex justify-end gap-2">
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => setAddingGrant(false)}
                    >
                      취소
                    </Button>
                    <Button
                      size="sm"
                      disabled={!pickedSetId}
                      onClick={() => setConfirmingGrant(true)}
                      data-testid="operator-domain-access-grant-next"
                    >
                      다음
                    </Button>
                  </div>
                </div>
              )}
            </section>

            <section className="mt-4 space-y-2">
              <h3 className="text-sm font-semibold text-foreground">합산 실효 업무 권한</h3>
              {rolesByDomain.size === 0 ? (
                <p
                  className="text-sm text-muted-foreground"
                  data-testid="operator-domain-access-roles-empty"
                >
                  업무 역할이 없습니다.
                </p>
              ) : (
                <ul
                  data-testid="operator-domain-access-roles-list"
                  className="space-y-1 text-sm"
                >
                  {Array.from(rolesByDomain.entries()).map(([domainKey, domainRoles]) => {
                    const unsubscribed =
                      subscribedDomains !== null && !subscribedDomains.includes(domainKey);
                    return (
                      <li
                        key={domainKey}
                        data-testid={`operator-domain-access-role-domain-${domainKey}`}
                      >
                        <span className="font-medium text-foreground">{domainKey}</span>
                        {': '}
                        <span className="text-muted-foreground">{domainRoles.join(', ')}</span>
                        {unsubscribed && (
                          <span
                            data-testid={`operator-domain-access-role-domain-${domainKey}-unsubscribed`}
                            className="ml-2 rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground"
                          >
                            구독 없음 — 토큰에 실리지 않음
                          </span>
                        )}
                      </li>
                    );
                  })}
                </ul>
              )}
            </section>
          </>
        )}

        <div className="mt-6 flex justify-end">
          <Button variant="secondary" onClick={onClose} data-testid="operator-domain-access-close">
            닫기
          </Button>
        </div>
      </div>

      {confirmingGrant && (
        <OperatorDomainSetReasonDialog
          title="업무 권한을 추가할까요?"
          description={
            isSelf
              ? '자기 자신에게 업무 권한을 줍니다 — 감사 기록에 자기 배정으로 남습니다.'
              : `${operatorLabel} 에게 선택한 업무 권한 세트를 직접 배정합니다.`
          }
          confirmLabel="추가"
          pending={grant.isPending}
          error={grantError}
          onConfirm={confirmGrant}
          onCancel={() => setConfirmingGrant(false)}
        />
      )}

      {revokingSet && (
        <OperatorDomainSetReasonDialog
          title="업무 권한을 회수할까요?"
          description={
            revokingGroupNames.length > 0
              ? `"${revokingSet.name}" 세트의 직접 배정을 회수합니다. 그룹 ${revokingGroupNames.join(
                  ', ',
                )} 에서 받은 같은 세트는 남습니다 — 그룹에서 빼야 사라집니다. ${DOMAIN_SET_LATENCY_COPY}`
              : `"${revokingSet.name}" 세트의 배정을 회수합니다. ${DOMAIN_SET_LATENCY_COPY}`
          }
          confirmLabel="회수"
          tone="destructive"
          pending={revoke.isPending}
          error={revokeError}
          onConfirm={confirmRevoke}
          onCancel={() => setRevokingSet(null)}
        />
      )}
    </div>
  );
}
