import { Skeleton, SkeletonText } from '@/shared/ui/Skeleton';

export default function ProductDetailLoading() {
  return (
    <div className="container" style={{ paddingTop: 'var(--space-8)', paddingBottom: 'var(--space-16)' }}>
      <Skeleton width="200px" height="14px" />
      <div style={{ display: 'grid', gridTemplateColumns: '480px minmax(0, 360px)', gap: 'var(--space-8)', justifyContent: 'center', marginTop: 'var(--space-4)' }}>
        <Skeleton width="100%" height="0" borderRadius="var(--radius-lg)" />
        <div style={{ aspectRatio: '1' }}>
          <Skeleton width="100%" height="100%" borderRadius="var(--radius-lg)" />
        </div>
        <div>
          <Skeleton width="60%" height="24px" />
          <div style={{ marginTop: 'var(--space-4)' }}>
            <Skeleton width="30%" height="28px" />
          </div>
          <div style={{ marginTop: 'var(--space-6)' }}>
            <SkeletonText lines={3} />
          </div>
          <div style={{ marginTop: 'var(--space-8)' }}>
            <Skeleton width="100%" height="44px" />
          </div>
        </div>
      </div>
    </div>
  );
}
