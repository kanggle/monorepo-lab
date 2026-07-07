import { messageForCode } from '@/shared/api/errors';

export interface SeedFormStatesProps {
  /** testid prefix for the emitted banner (`map` / `policy`). */
  testidPrefix: string;
  isLoading: boolean;
  forbidden: boolean;
  rateLimited: boolean;
  /** The section-degrade message (differs per form). */
  degradedMessage: string;
}

/**
 * Shared loading / forbidden / rate-limited / degraded status banner for the scm
 * seed-config forms (TASK-PC-FE-215 split). `PolicyForm` and `SupplierMapForm`
 * previously inlined a byte-identical ternary chain of these four states (only
 * the testid prefix + the degrade copy differed); this is that chain extracted
 * once. Rendered ONLY when the read query is blocked (loading/forbidden/
 * rate-limited/degraded) — the parent shows the form otherwise, so exactly one
 * of the four branches renders here. Pure presentation — markup + testids
 * (`<prefix>-loading` / `-forbidden` / `-ratelimited` / `-degraded`) + aria
 * preserved verbatim from the pre-split forms.
 */
export function SeedFormStates({
  testidPrefix,
  isLoading,
  forbidden,
  rateLimited,
  degradedMessage,
}: SeedFormStatesProps) {
  if (isLoading) {
    return (
      <p className="mt-4 text-sm text-muted-foreground" data-testid={`${testidPrefix}-loading`}>
        불러오는 중…
      </p>
    );
  }
  if (forbidden) {
    return (
      <p
        role="status"
        data-testid={`${testidPrefix}-forbidden`}
        className="mt-4 rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground"
      >
        {messageForCode('TENANT_FORBIDDEN')}
      </p>
    );
  }
  if (rateLimited) {
    return (
      <p
        role="status"
        data-testid={`${testidPrefix}-ratelimited`}
        className="mt-4 rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground"
      >
        {messageForCode('RATE_LIMIT_EXCEEDED')}
      </p>
    );
  }
  return (
    <p
      role="status"
      data-testid={`${testidPrefix}-degraded`}
      className="mt-4 rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground"
    >
      {degradedMessage}
    </p>
  );
}
