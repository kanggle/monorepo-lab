import { NarrowContainer } from '@/shared/ui';
import { Skeleton, SkeletonList } from '@/shared/ui/Skeleton';

export default function CheckoutLoading() {
  return (
    <NarrowContainer>
      <Skeleton width="100px" height="24px" />
      <div style={{ marginTop: 'var(--space-6)' }}>
        <Skeleton width="80px" height="18px" />
        <div style={{ marginTop: 'var(--space-3)' }}>
          <SkeletonList count={2} />
        </div>
      </div>
      <div style={{ marginTop: 'var(--space-8)' }}>
        <Skeleton width="80px" height="18px" />
        <div style={{ marginTop: 'var(--space-3)', display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <Skeleton width="100%" height="40px" />
          <Skeleton width="100%" height="40px" />
          <Skeleton width="100%" height="40px" />
        </div>
      </div>
      <div style={{ marginTop: 'var(--space-8)' }}>
        <Skeleton width="100%" height="48px" />
      </div>
    </NarrowContainer>
  );
}
