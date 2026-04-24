import { Skeleton, SkeletonProductGrid } from '@/shared/ui/Skeleton';

export default function ProductsLoading() {
  return (
    <div className="container" style={{ paddingTop: 'var(--space-8)', paddingBottom: 'var(--space-16)' }}>
      <Skeleton width="100%" height="44px" borderRadius="var(--radius-md)" />
      <div style={{ marginTop: 'var(--space-6)' }}>
        <Skeleton width="80px" height="24px" />
      </div>
      <div style={{ marginTop: 'var(--space-6)' }}>
        <SkeletonProductGrid count={8} />
      </div>
    </div>
  );
}
