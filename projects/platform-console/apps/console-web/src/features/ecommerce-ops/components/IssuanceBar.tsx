import { ProportionBar } from '@/shared/ui/InlineBar';

/**
 * Issuance-rate progress bar next to the `{issued} / {max}` text
 * (TASK-PC-FE-357). Server-safe. `max` must be > 0 (the schema's `positive()`);
 * anything else draws no bar. `issued > max` clamps to a full bar (the text
 * keeps the raw numbers); a full bar takes the warning tone.
 */
export function IssuanceBar({
  issued,
  max,
  testid,
}: {
  issued: number;
  max: number;
  testid?: string;
}) {
  if (!(max > 0)) return null;
  return (
    <ProportionBar
      value={issued}
      max={max}
      tone={issued >= max ? 'warning' : 'progress'}
      className="w-20"
      testid={testid}
    />
  );
}
