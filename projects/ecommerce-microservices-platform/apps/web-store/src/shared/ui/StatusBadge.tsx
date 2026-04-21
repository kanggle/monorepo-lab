interface StatusBadgeProps {
  status: string;
  labels: Record<string, string>;
  colors: Record<string, string>;
}

export function StatusBadge({ status, labels, colors }: StatusBadgeProps) {
  return (
    <span
      style={{
        display: 'inline-block',
        padding: 'var(--space-1) var(--space-2)',
        fontSize: 'var(--font-size-xs)',
        fontWeight: 'var(--font-weight-bold)',
        color: 'var(--color-white)',
        backgroundColor: colors[status],
        borderRadius: 'var(--radius-sm)',
      }}
    >
      {labels[status]}
    </span>
  );
}
