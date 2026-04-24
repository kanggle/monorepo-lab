import { Skeleton, SkeletonList } from '@/shared/ui/Skeleton';

export default function MyLoading() {
  return (
    <div>
      <Skeleton width="120px" height="24px" />
      <div style={{ marginTop: 'var(--space-6)' }}>
        <SkeletonList count={3} />
      </div>
    </div>
  );
}
