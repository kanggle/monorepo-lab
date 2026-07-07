/**
 * Single metric tile (label + value) for the IAM composed operator overview
 * (TASK-PC-FE-005 — extracted from {@link OperatorOverviewScreen},
 * TASK-PC-FE-212 presentational split). Renders `—` for a `null` value; the
 * markup / testid is byte-verbatim from the former god-file.
 */
export function Metric({
  label,
  value,
  testid,
}: {
  label: string;
  value: number | null;
  testid: string;
}) {
  return (
    <div>
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd
        className="text-2xl font-semibold tabular-nums text-foreground"
        data-testid={testid}
      >
        {value === null ? '—' : value.toLocaleString()}
      </dd>
    </div>
  );
}
