'use client';

import { useMemo, useState, type FormEvent } from 'react';
import {
  KNOWN_OPERATOR_ROLES,
  ELEVATED_ROLE,
  passwordPolicyError,
  type CreateOperatorInput,
} from '../api/types';

/**
 * Form-state logic for `CreateOperatorForm` (TASK-PC-FE-196 split — the PC-FE
 * "fat form → custom hook" pattern, cf. PC-FE-112 `useOrgScopeForm` /
 * PC-FE-141 `usePromotionForm`).
 *
 * 🔴 TASK-MONO-772 S5 — this form is now **platform-scope (`*`) only**
 * (console-integration-contract § 2.4.3 row 2: `POST /api/admin/operators`
 * creates only `*` operators; a company operator is INVITED — rows 11–14).
 * So the tenant is fixed to `*` (no picker), and the TASK-MONO-334
 * account-existence PRE-GATE is gone: the contract RETIRES it together with
 * the non-`*` create path, and `*` was always exempt from it anyway.
 *
 * Owns the field states, the validation, the grantable-role pre-filter and
 * the confirm-gated submit; the component is a presentational shell.
 */

/** The only tenant this form creates into (ADR-MONO-080 D1 — platform scope). */
export const PLATFORM_TENANT = '*';

export interface UseCreateOperatorFormArgs {
  /** Hand the validated draft up; the parent confirms + fires the create. */
  onSubmitDraft: (draft: CreateOperatorInput, grantsElevated: boolean) => void;
  /** In-flight create — disables submit. */
  pending: boolean;
  /** feat/iam-grantable-roles-filter — the seed role names the CALLING operator
   *  may grant. `null` (absent / fetch failed) ⇒ render EVERY known role. */
  grantableRoles: string[] | null;
}

export function useCreateOperatorForm({
  onSubmitDraft,
  pending,
  grantableRoles,
}: UseCreateOperatorFormArgs) {
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [roles, setRoles] = useState<string[]>([]);
  const [touched, setTouched] = useState(false);

  const pwError = useMemo(
    () => (password === '' ? null : passwordPolicyError(password)),
    [password],
  );

  const emailOk = /.+@.+\..+/.test(email.trim());
  const nameOk = displayName.trim().length >= 1;
  // ADR-MONO-035 O2 / TASK-BE-377: the password is OPTIONAL (a demoted
  // break-glass local login). Blank ⇒ an OIDC-only operator. Only a NON-blank
  // password must satisfy the policy — `pwError` is already null when blank.
  const pwOk = password === '' || pwError === null;

  const grantsElevated = roles.includes(ELEVATED_ROLE);
  const canSubmit = emailOk && nameOk && pwOk && !pending;

  // feat/iam-grantable-roles-filter — render only the KNOWN_OPERATOR_ROLES
  // that are also in the server-provided grantable set. `null` (fetch
  // failed / not provided) ⇒ render the full known-roles list (fallback —
  // never an empty checkbox group).
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
    onSubmitDraft(
      {
        email: email.trim(),
        displayName: displayName.trim(),
        // Omit when blank ⇒ the producer creates an OIDC-only operator (no
        // break-glass password_hash stored). A blank string must NOT be sent
        // (the producer @Size(min=10) would reject "" — it is not null).
        ...(password === '' ? {} : { password }),
        roles,
        tenantId: PLATFORM_TENANT,
      },
      grantsElevated,
    );
  }

  return {
    email,
    setEmail,
    displayName,
    setDisplayName,
    password,
    setPassword,
    roles,
    touched,
    emailOk,
    pwError,
    canSubmit,
    grantsElevated,
    renderableRoles,
    toggleRole,
    handleSubmit,
  };
}
