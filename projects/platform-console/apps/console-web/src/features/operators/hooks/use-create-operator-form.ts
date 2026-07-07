'use client';

import { useEffect, useMemo, useState, type FormEvent } from 'react';
import {
  KNOWN_OPERATOR_ROLES,
  ELEVATED_ROLE,
  passwordPolicyError,
  type CreateOperatorInput,
} from '../api/types';

/**
 * Form-state logic for `CreateOperatorForm` (TASK-PC-FE-196 split of
 * `CreateOperatorForm.tsx` — the PC-FE "fat form → custom hook" pattern, cf.
 * PC-FE-112 `useOrgScopeForm` / PC-FE-141 `usePromotionForm`). Owns the six
 * field states, the multi-predicate validation, the account-existence PRE-GATE
 * probe (debounced, abortable — TASK-MONO-334, which SUPERSEDES the fail-soft
 * TASK-PC-FE-179 advisory: the probe now blocks submit unless the email owns a
 * tenant account), the grantable-role pre-filter, and the confirm-gated submit.
 * The component is left a presentational shell that renders from this result.
 *
 * `useId` field ids stay in the component (view concern — label/aria wiring).
 */

export type AccountProbeState =
  | 'idle'
  | 'checking'
  | 'exists'
  | 'absent'
  | 'unavailable';

export interface UseCreateOperatorFormArgs {
  /** Hand the validated draft up; the parent confirms + fires the create. */
  onSubmitDraft: (draft: CreateOperatorInput, grantsElevated: boolean) => void;
  /** In-flight create — disables submit. */
  pending: boolean;
  /** feat/iam-grantable-roles-filter — the seed role names the CALLING operator
   *  may grant. `null` (absent / fetch failed) ⇒ render EVERY known role. */
  grantableRoles: string[] | null;
  /** TASK-PC-FE-179 — pre-flight account-existence probe (already defaulted by
   *  the component). Returns `true`/`false`/`null` (unknown — fail-soft). */
  checkAccountExists: (
    email: string,
    tenantId: string,
    signal?: AbortSignal,
  ) => Promise<boolean | null>;
}

export function useCreateOperatorForm({
  onSubmitDraft,
  pending,
  grantableRoles,
  checkAccountExists,
}: UseCreateOperatorFormArgs) {
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [roles, setRoles] = useState<string[]>([]);
  const [tenant, setTenant] = useState('');
  const [touched, setTouched] = useState(false);

  const pwError = useMemo(
    () => (password === '' ? null : passwordPolicyError(password)),
    [password],
  );

  const emailOk = /.+@.+\..+/.test(email.trim());
  const nameOk = displayName.trim().length >= 1;
  const tenantOk = tenant.trim().length >= 1;
  // ADR-MONO-035 O2 / TASK-BE-377: the password is OPTIONAL (a demoted
  // break-glass local login). Blank ⇒ an OIDC-only operator (primary login is
  // the unified IAM credential of this email's account). Only a NON-blank
  // password must satisfy the policy — `pwError` is already null when blank.
  const pwOk = password === '' || pwError === null;

  const grantsElevated = roles.includes(ELEVATED_ROLE);

  // TASK-MONO-334 (ADR-MONO-035 amendment) — account-existence PRE-GATE. Debounce-
  // probe whether the email is a signed-up account in the SELECTED tenant. Unlike
  // the SUPERSEDED TASK-PC-FE-179 advisory (which never blocked), this now GATES
  // submit: an operator may be created ONLY for an email that already owns a tenant
  // account (that account's unified IAM credential is the operator's primary login;
  // a break-glass password is only a secondary login and no longer bypasses this).
  // The platform sentinel `*` has no tenant account to probe and is EXEMPT
  // (SUPER_ADMIN bootstrap) — mirroring the producer, which also exempts `*`. The
  // producer's 422 OPERATOR_ACCOUNT_NOT_FOUND stays the final authority.
  const [acctState, setAcctState] = useState<AccountProbeState>('idle');
  const probeTenant = tenant.trim();
  const probeEmail = email.trim();
  const isPlatformScope = probeTenant === '*';
  const probeEligible = emailOk && tenantOk && !isPlatformScope;

  useEffect(() => {
    if (!probeEligible) {
      setAcctState('idle');
      return;
    }
    let cancelled = false;
    const controller = new AbortController();
    setAcctState('checking');
    const timer = setTimeout(() => {
      checkAccountExists(probeEmail, probeTenant, controller.signal)
        .then((exists) => {
          if (cancelled) return;
          setAcctState(
            exists === null ? 'unavailable' : exists ? 'exists' : 'absent',
          );
        })
        .catch(() => {
          if (!cancelled) setAcctState('unavailable');
        });
    }, 400);
    return () => {
      cancelled = true;
      controller.abort();
      clearTimeout(timer);
    };
  }, [probeEligible, probeEmail, probeTenant, checkAccountExists]);

  // The account gate: '*' is exempt; otherwise the email MUST resolve to an
  // existing tenant account. 'checking' / 'absent' / 'unavailable' all keep submit
  // disabled — fail-CLOSED, so the UI never lets an unverified operator through
  // (the producer would 422 anyway; this pre-gate makes it a clear, blocked state).
  const accountGateOk = isPlatformScope || acctState === 'exists';
  const canSubmit =
    emailOk && nameOk && tenantOk && pwOk && accountGateOk && !pending;

  // Blocking / advisory copy (mutually exclusive, non-'*' only):
  //  - absent      → BLOCKING error (must sign up first; break-glass no longer bypasses)
  //  - unavailable → BLOCKING (cannot verify — try again)
  //  - checking    → transient "checking…" hint
  //  - exists      → OIDC-ok confirmation (submit enabled)
  const showBlockingAbsent = acctState === 'absent';
  const showUnavailable = acctState === 'unavailable';
  const showChecking = acctState === 'checking';
  const showExistsOk = acctState === 'exists';

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
        tenantId: tenant.trim(),
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
    tenant,
    setTenant,
    roles,
    touched,
    emailOk,
    pwError,
    canSubmit,
    grantsElevated,
    probeTenant,
    showBlockingAbsent,
    showUnavailable,
    showChecking,
    showExistsOk,
    renderableRoles,
    toggleRole,
    handleSubmit,
  };
}
