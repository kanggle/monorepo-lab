/** Account status badge — pure presentational, status-aware colour. */
export function AccountStatusBadge({ status }: { status: string }) {
  const tone =
    status === 'ACTIVE'
      ? 'bg-muted text-foreground'
      : status === 'LOCKED'
        ? 'bg-destructive/15 text-destructive'
        : 'bg-muted-foreground/20 text-muted-foreground';
  return (
    <span
      className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${tone}`}
      data-testid="account-status"
    >
      {status}
    </span>
  );
}
