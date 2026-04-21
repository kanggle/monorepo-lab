import type { WishlistItem } from '@repo/types';

interface WishlistItemInfoProps {
  item: WishlistItem;
  isDeleted: boolean;
  formattedDate: string;
}

export function WishlistItemInfo({ item, isDeleted, formattedDate }: WishlistItemInfoProps) {
  return (
    <div style={{ flex: 1, minWidth: 0 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
        <span
          style={{
            fontSize: 'var(--font-size-sm)',
            fontWeight: 'var(--font-weight-medium)',
            color: isDeleted ? 'var(--color-text-muted)' : 'var(--color-text)',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {isDeleted ? '판매 종료' : item.productName}
        </span>
        {isDeleted && (
          <span
            style={{
              fontSize: 'var(--font-size-xs)',
              padding: '1px 6px',
              borderRadius: 'var(--radius-sm)',
              background: 'var(--color-bg-tertiary)',
              color: 'var(--color-text-muted)',
              fontWeight: 'var(--font-weight-semibold)',
              flexShrink: 0,
            }}
          >
            삭제됨
          </span>
        )}
      </div>
      <div
        style={{
          marginTop: 'var(--space-1)',
          display: 'flex',
          alignItems: 'center',
          gap: 'var(--space-3)',
        }}
      >
        {!isDeleted && (
          <span
            style={{
              fontSize: 'var(--font-size-sm)',
              fontWeight: 'var(--font-weight-bold)',
              color: 'var(--color-accent)',
            }}
          >
            {item.productPrice.toLocaleString()}원
          </span>
        )}
        <span
          style={{
            fontSize: 'var(--font-size-xs)',
            color: 'var(--color-text-muted)',
          }}
        >
          {formattedDate} 추가
        </span>
      </div>
    </div>
  );
}
