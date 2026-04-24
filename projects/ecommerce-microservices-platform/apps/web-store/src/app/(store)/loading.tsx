import { Skeleton, SkeletonProductGrid } from '@/shared/ui/Skeleton';

export default function StoreLoading() {
  return (
    <div>
      <div style={{ height: 300, background: 'var(--color-bg-tertiary)' }}>
        <Skeleton width="100%" height="100%" borderRadius="0" />
      </div>
      <section className="container" style={{ padding: 'var(--space-12) var(--space-6)' }}>
        <Skeleton width="120px" height="28px" />
        <div style={{ marginTop: 'var(--space-6)' }}>
          <SkeletonProductGrid count={8} />
        </div>
      </section>
    </div>
  );
}
