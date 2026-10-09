'use client';

import { buildStepUpRedirectFor } from '@/shared/lib/login-redirect';

/**
 * «2단계 인증 후 들어가기» — the tenant-switch step-up offer
 * (TASK-MONO-771, console-integration-contract § 2.7 `mfa_required` row).
 *
 * Rendered by a switch surface when `POST /api/tenant` answered
 * `403 MFA_REQUIRED` (the selected tenant needs a second factor). It navigates
 * to `GET /api/auth/step-up?redirect=<current path>`; after the step-up the
 * operator selects the tenant again (the contract does not re-select for them).
 *
 * A full-document navigation (`window.location.assign`), not `router.push`:
 * the destination is a route handler that redirects off-origin to IAM.
 * `<current path>` goes through the § 2.6.1 produce-side predicate
 * ({@link buildStepUpRedirectFor}) — the same function every other step-up
 * entry uses.
 */
export function StepUpOffer() {
  const onClick = () => {
    const current = window.location.pathname + window.location.search;
    window.location.assign(buildStepUpRedirectFor(current));
  };

  return (
    <span role="alert" className="flex items-center gap-2 text-xs text-destructive">
      이 테넌트는 2단계 인증이 필요합니다.
      <button
        type="button"
        onClick={onClick}
        data-testid="tenant-step-up"
        className="rounded-md border border-border px-2 py-0.5 text-xs text-foreground hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
      >
        2단계 인증 후 들어가기
      </button>
    </span>
  );
}
