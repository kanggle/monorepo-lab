import styles from './Skeleton.module.css';

interface SkeletonProps {
  width?: string;
  height?: string;
  borderRadius?: string;
}

export function Skeleton({ width = '100%', height = '16px', borderRadius = 'var(--radius-md)' }: SkeletonProps) {
  return <div className={styles.skeleton} style={{ width, height, borderRadius }} />;
}

export function SkeletonText({ lines = 3, width = '100%' }: { lines?: number; width?: string }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)', width }}>
      {Array.from({ length: lines }).map((_, i) => (
        <Skeleton key={i} height="14px" width={i === lines - 1 ? '60%' : '100%'} />
      ))}
    </div>
  );
}

export function SkeletonCard() {
  return (
    <div className={styles.card}>
      <Skeleton height="0" borderRadius="0" />
      <div className={styles.cardImageWrap}>
        <Skeleton width="100%" height="100%" borderRadius="0" />
      </div>
      <div style={{ padding: 'var(--space-3) var(--space-4)' }}>
        <Skeleton width="70%" height="14px" />
        <div style={{ marginTop: 'var(--space-2)' }}>
          <Skeleton width="40%" height="16px" />
        </div>
      </div>
    </div>
  );
}

export function SkeletonProductGrid({ count = 8 }: { count?: number }) {
  return (
    <div className={styles.grid}>
      {Array.from({ length: count }).map((_, i) => (
        <SkeletonCard key={i} />
      ))}
    </div>
  );
}

export function SkeletonListItem() {
  return (
    <div className={styles.listItem}>
      <div style={{ flex: 1 }}>
        <Skeleton width="50%" height="14px" />
        <div style={{ marginTop: 'var(--space-2)' }}>
          <Skeleton width="30%" height="12px" />
        </div>
      </div>
      <Skeleton width="80px" height="14px" />
    </div>
  );
}

export function SkeletonList({ count = 3 }: { count?: number }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
      {Array.from({ length: count }).map((_, i) => (
        <SkeletonListItem key={i} />
      ))}
    </div>
  );
}
