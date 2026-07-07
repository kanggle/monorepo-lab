'use client';

/**
 * host-side partner invite form (TASK-PC-FE-211 split from `PartnershipsScreen`).
 * Presentational only — the controlled values, their setters, and the submit
 * handler live in the container; this component renders the form markup. The
 * host tenant is NEVER a field here (server-side active tenant); the body
 * carries only `partnerTenantId` + delegated scope inputs.
 */
export interface PartnershipInviteFormProps {
  invitePartner: string;
  setInvitePartner: (v: string) => void;
  inviteDomains: string;
  setInviteDomains: (v: string) => void;
  inviteRoles: string;
  setInviteRoles: (v: string) => void;
  onSubmit: (e: React.FormEvent) => void;
}

export function PartnershipInviteForm({
  invitePartner,
  setInvitePartner,
  inviteDomains,
  setInviteDomains,
  inviteRoles,
  setInviteRoles,
  onSubmit,
}: PartnershipInviteFormProps) {
  return (
    <form
      onSubmit={onSubmit}
      data-testid="partnership-invite-form"
      className="mb-4 rounded-md border border-border bg-muted/40 p-4"
    >
      <p className="mb-3 text-sm font-medium text-foreground">
        파트너 조직 초대
      </p>
      <div className="grid gap-3 sm:grid-cols-3">
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-muted-foreground">파트너 조직 ID</span>
          <input
            type="text"
            value={invitePartner}
            onChange={(e) => setInvitePartner(e.target.value)}
            data-testid="partnership-invite-partner"
            placeholder="globex-corp"
            className="rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-muted-foreground">위임 도메인 (쉼표)</span>
          <input
            type="text"
            value={inviteDomains}
            onChange={(e) => setInviteDomains(e.target.value)}
            data-testid="partnership-invite-domains"
            placeholder="wms, scm"
            className="rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-muted-foreground">위임 역할 (쉼표)</span>
          <input
            type="text"
            value={inviteRoles}
            onChange={(e) => setInviteRoles(e.target.value)}
            data-testid="partnership-invite-roles"
            placeholder="WMS_OUTBOUND_OPERATOR"
            className="rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
        </label>
      </div>
      <div className="mt-3 flex justify-end">
        <button
          type="submit"
          disabled={invitePartner.trim() === ''}
          data-testid="partnership-invite-submit"
          className="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
        >
          초대
        </button>
      </div>
    </form>
  );
}
