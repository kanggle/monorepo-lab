/**
 * NON-blocking read-model-lag hint banner (TASK-PC-FE-197 split) — the shared
 * presentation used by `WmsOpsScreen` / `WmsInventoryScreen` /
 * `WmsShipmentsScreen`. Renders nothing when there is no lag message
 * (eventual-consistency honesty: the section still renders, this is a hint, not
 * an error). Each container computes the message string and passes its own
 * `testid` (`wms-lag-hint` for the 개요/재고 screens, `wms-ship-lag-hint` for the
 * 택배/출고 section) — markup + className preserved verbatim.
 */
export function WmsLagHint({
  testid,
  message,
}: {
  testid: string;
  message: string | null;
}) {
  if (!message) return null;
  return (
    <div
      role="status"
      data-testid={testid}
      className="mb-6 rounded-md border border-amber-300/50 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-700/40 dark:bg-amber-950/40 dark:text-amber-200"
    >
      {message}
    </div>
  );
}
