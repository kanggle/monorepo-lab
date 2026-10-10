'use client';

import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { KNOWN_OPERATOR_ROLES, ELEVATED_ROLE } from '../api/types';
import type { InviteOperatorInput } from '../api/invitation-types';

/**
 * Form-state logic for `InviteOperatorForm` (TASK-MONO-772 S5 — the «초대»
 * form, § 2.4.3 row 11). Same «fat form → hook» shape as
 * `useCreateOperatorForm`, minus everything the invitation does not have:
 *   - NO password (the invitee logs in with their own verified IAM account);
 *   - NO tenant picker (the target is the ACTIVE tenant — never `*`);
 *   - NO account-existence probe (the TASK-MONO-334 pre-gate is RETIRED by
 *     772 — the invitee need not have an account yet; the IdP accept page
 *     offers a site-less sign-up, OD-3).
 *
 * The draft is handed up; the parent gates the actual call behind the
 * reason + confirm dialog (no one-click invite).
 */

export interface UseInviteOperatorFormArgs {
  /** The active tenant (the invitation target). */
  tenantId: string;
  onSubmitDraft: (draft: InviteOperatorInput) => void;
  pending: boolean;
  /** Seed role names the CALLER may grant; `null` ⇒ every known role. */
  grantableRoles: string[] | null;
  /** Bumped by the parent after a successful invite → clear the fields. */
  resetSignal: number;
}

const EMAIL_RE = /.+@.+\..+/;

export function useInviteOperatorForm({
  tenantId,
  onSubmitDraft,
  pending,
  grantableRoles,
  resetSignal,
}: UseInviteOperatorFormArgs) {
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [roles, setRoles] = useState<string[]>([]);
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (resetSignal === 0) return;
    setEmail('');
    setDisplayName('');
    setRoles([]);
    setTouched(false);
  }, [resetSignal]);

  const trimmedEmail = email.trim();
  const trimmedName = displayName.trim();
  const emailOk = EMAIL_RE.test(trimmedEmail) && trimmedEmail.length <= 255;
  const nameOk = trimmedName.length >= 1 && trimmedName.length <= 64;
  // An invitation with no role would create an operator who can do nothing —
  // the UI asks for at least one (the producer is the final authority).
  const rolesOk = roles.length >= 1;
  const tenantOk = tenantId !== '' && tenantId !== '*';
  const canSubmit = emailOk && nameOk && rolesOk && tenantOk && !pending;
  const grantsElevated = roles.includes(ELEVATED_ROLE);

  const renderableRoles = useMemo(
    () =>
      grantableRoles === null
        ? KNOWN_OPERATOR_ROLES
        : KNOWN_OPERATOR_ROLES.filter((role) => grantableRoles.includes(role)),
    [grantableRoles],
  );

  function toggleRole(role: string) {
    setRoles((prev) =>
      prev.includes(role) ? prev.filter((r) => r !== role) : [...prev, role],
    );
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setTouched(true);
    if (!canSubmit) return;
    onSubmitDraft({
      email: trimmedEmail.toLowerCase(),
      displayName: trimmedName,
      roles,
      tenantId,
    });
  }

  return {
    email,
    setEmail,
    displayName,
    setDisplayName,
    roles,
    touched,
    emailOk,
    nameOk,
    rolesOk,
    canSubmit,
    grantsElevated,
    renderableRoles,
    toggleRole,
    handleSubmit,
  };
}
