'use client';

import { useId, useMemo, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import type {
  DomainRole,
  DomainPermissionSet,
  DomainSetRoleRef,
} from '../api/types';

/**
 * Create/edit form for a 업무 권한 세트 (TASK-PC-FE-338 / ADR-MONO-083 D9).
 * Collects the draft ONLY — it does NOT call the producer; the parent hands
 * the validated draft to the reason-capture + confirm dialog
 * ({@link DomainSetReasonDialog}), mirroring the `GroupForm` →
 * `GroupReasonDialog` two-step pattern (draft here, audit reason there — a
 * mutation never fires one-click).
 *
 * The role picker is grouped by `domainKey` (contract screen 1): an entry
 * with `fineGrained=false` is rendered with its producer `label` verbatim
 * ("… 전체 업무(세분 미지원)") — never split or described as narrower than it
 * is (ADR-083 D3). A domain the active tenant has not subscribed to
 * (`subscribedDomains` excludes it) is shown disabled with a «구독 없음»
 * badge — but ONLY when `subscribedDomains` is known (`!== null`); "don't
 * know ⇒ don't disable" (the producer's `422 DOMAIN_NOT_SUBSCRIBED` stays
 * final either way).
 */
export interface DomainPermissionSetFormProps {
  mode: 'create' | 'edit';
  /** Full role catalog (row 1), in catalog order. */
  roles: DomainRole[];
  /** `null` ⇒ unknown — never disable any domain group. */
  subscribedDomains: string[] | null;
  /** Required when `mode='edit'` — prefills the form. */
  initial?: DomainPermissionSet;
  /** «복제해서 새 세트» pre-fill — the SAME roles as a preset, into a fresh
   *  create draft. Ignored when `mode='edit'`. */
  prefillRoles?: DomainSetRoleRef[];
  onSubmitDraft: (draft: {
    name: string;
    description?: string;
    roles: DomainSetRoleRef[];
  }) => void;
  serverError?: string | null;
  pending?: boolean;
  onCancel?: () => void;
}

function roleKey(ref: { domainKey: string; role: string }): string {
  return `${ref.domainKey}:${ref.role}`;
}

export function DomainPermissionSetForm({
  mode,
  roles,
  subscribedDomains,
  initial,
  prefillRoles,
  onSubmitDraft,
  serverError,
  pending = false,
  onCancel,
}: DomainPermissionSetFormProps) {
  const nameFieldId = useId();
  const descriptionFieldId = useId();

  const [name, setName] = useState(initial?.name ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [selected, setSelected] = useState<Set<string>>(() => {
    const seed = initial?.roles ?? prefillRoles ?? [];
    return new Set(seed.map(roleKey));
  });
  const [touched, setTouched] = useState(false);

  const domainGroups = useMemo(() => {
    const byDomain = new Map<string, DomainRole[]>();
    for (const r of roles) {
      const list = byDomain.get(r.domainKey) ?? [];
      list.push(r);
      byDomain.set(r.domainKey, list);
    }
    return [...byDomain.entries()].sort(([a], [b]) => a.localeCompare(b));
  }, [roles]);

  const trimmedName = name.trim();
  const nameOk = trimmedName.length > 0 && trimmedName.length <= 120;
  const descriptionOk = description.length <= 255;
  const rolesOk = selected.size > 0;
  const canSubmit = !pending && nameOk && descriptionOk && rolesOk;

  function isDomainSubscribed(domainKey: string): boolean {
    return subscribedDomains === null || subscribedDomains.includes(domainKey);
  }

  function toggleRole(ref: DomainSetRoleRef, domainSubscribed: boolean) {
    if (!domainSubscribed) return;
    setSelected((prev) => {
      const next = new Set(prev);
      const key = roleKey(ref);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setTouched(true);
    if (!canSubmit) return;
    const draftRoles: DomainSetRoleRef[] = roles
      .filter((r) => selected.has(roleKey(r)))
      .map((r) => ({ domainKey: r.domainKey, role: r.role }));
    onSubmitDraft({
      name: trimmedName,
      description: description.trim() === '' ? undefined : description.trim(),
      roles: draftRoles,
    });
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="space-y-4 rounded-md border border-border bg-background p-4"
      aria-label={mode === 'create' ? '업무 권한 세트 등록' : '업무 권한 세트 수정'}
      data-testid={mode === 'create' ? 'domain-set-create-form' : 'domain-set-edit-form'}
    >
      <h2 className="text-lg font-semibold text-foreground">
        {mode === 'create' ? '업무 권한 세트 등록' : '업무 권한 세트 수정'}
      </h2>

      <div>
        <label htmlFor={nameFieldId} className="block text-sm font-medium text-foreground">
          이름 <span aria-hidden="true">*</span>
        </label>
        <input
          id={nameFieldId}
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={120}
          data-testid="domain-set-form-name"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          placeholder="재고팀"
        />
        {touched && !nameOk && (
          <p className="mt-1 text-xs text-destructive" data-testid="domain-set-form-name-error">
            1~120자로 입력하세요.
          </p>
        )}
      </div>

      <div>
        <label htmlFor={descriptionFieldId} className="block text-sm font-medium text-foreground">
          설명
        </label>
        <textarea
          id={descriptionFieldId}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={255}
          rows={2}
          data-testid="domain-set-form-description"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          placeholder="선택 설명 (255자 이하)"
        />
      </div>

      <div data-testid="domain-set-form-roles">
        <span className="block text-sm font-medium text-foreground">
          업무 역할 <span aria-hidden="true">*</span>
        </span>
        <div className="mt-2 space-y-3">
          {domainGroups.map(([domainKey, domainRoles]) => {
            const subscribed = isDomainSubscribed(domainKey);
            return (
              <fieldset
                key={domainKey}
                disabled={!subscribed}
                data-testid={`domain-set-form-domain-${domainKey}`}
                className="rounded-md border border-border p-3"
              >
                <legend className="flex items-center gap-2 text-sm font-medium text-foreground">
                  {domainKey}
                  {!subscribed && (
                    <span
                      data-testid={`domain-set-form-domain-${domainKey}-unsubscribed`}
                      className="rounded bg-muted px-1.5 py-0.5 text-xs font-normal text-muted-foreground"
                    >
                      구독 없음
                    </span>
                  )}
                </legend>
                <ul className="mt-2 space-y-1">
                  {domainRoles.map((r) => {
                    const key = roleKey(r);
                    return (
                      <li key={key} className="flex items-start gap-2">
                        <input
                          type="checkbox"
                          id={`domain-set-role-${key}`}
                          checked={selected.has(key)}
                          disabled={!subscribed}
                          onChange={() => toggleRole(r, subscribed)}
                          data-testid={`domain-set-form-role-${domainKey}-${r.role}`}
                          className="mt-0.5"
                        />
                        <label
                          htmlFor={`domain-set-role-${key}`}
                          className="text-sm text-foreground"
                          data-coarse={!r.fineGrained || undefined}
                        >
                          {/* fineGrained=false → verbatim "… 전체 업무(세분 미지원)" —
                              never split or described as narrower (ADR-083 D3). The
                              producer `label` string already names this; no extra
                              text is appended so the rendered text stays byte-identical
                              to the producer value. */}
                          {r.label}
                        </label>
                      </li>
                    );
                  })}
                </ul>
              </fieldset>
            );
          })}
        </div>
        {touched && !rolesOk && (
          <p className="mt-1 text-xs text-destructive" data-testid="domain-set-form-roles-error">
            업무 역할을 하나 이상 선택하세요.
          </p>
        )}
      </div>

      {serverError && (
        <p
          role="alert"
          data-testid="domain-set-form-server-error"
          className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
        >
          {serverError}
        </p>
      )}

      <div className="flex gap-2">
        <Button type="submit" disabled={!canSubmit} data-testid="domain-set-form-submit">
          {pending ? '처리 중…' : mode === 'create' ? '등록 (확인 필요)' : '수정 (확인 필요)'}
        </Button>
        {onCancel && (
          <Button type="button" variant="secondary" onClick={onCancel} data-testid="domain-set-form-cancel">
            취소
          </Button>
        )}
      </div>
    </form>
  );
}
