import { NarrowContainer } from '@/shared/ui';
import { Skeleton, SkeletonList } from '@/shared/ui/Skeleton';

export default function CartLoading() {
  return (
    <NarrowContainer>
      <Skeleton width="100px" height="24px" />
      <div style={{ marginTop: 'var(--space-6)' }}>
        <SkeletonList count={3} />
      </div>
      <div style={{ marginTop: 'var(--space-6)' }}>
        <Skeleton width="100%" height="48px" />
      </div>
    </NarrowContainer>
  );
}
