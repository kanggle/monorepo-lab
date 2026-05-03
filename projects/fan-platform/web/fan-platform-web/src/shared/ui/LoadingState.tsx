/**
 * Generic loading placeholder. Use inside `<Suspense>` fallback or before
 * React Query has resolved.
 */
export function LoadingState({ label = '불러오는 중...' }: { label?: string }) {
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex flex-col items-center justify-center gap-3 py-12 text-ink-600"
    >
      <span
        aria-hidden="true"
        className="h-8 w-8 animate-spin rounded-full border-2 border-brand-300 border-t-brand-600"
      />
      <span className="text-sm">{label}</span>
    </div>
  );
}
