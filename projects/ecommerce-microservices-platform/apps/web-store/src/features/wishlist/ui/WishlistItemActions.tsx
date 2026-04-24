import { HeartIcon } from '@/shared/ui/HeartIcon';

interface WishlistItemActionsProps {
  isPending: boolean;
  onRemove: (e: React.MouseEvent) => void;
}

export function WishlistItemActions({ isPending, onRemove }: WishlistItemActionsProps) {
  return (
    <button
      type="button"
      onClick={onRemove}
      disabled={isPending}
      aria-label="위시리스트에서 제거"
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: '32px',
        height: '32px',
        borderRadius: 'var(--radius-full)',
        border: 'none',
        background: 'transparent',
        cursor: isPending ? 'not-allowed' : 'pointer',
        transition: 'all var(--transition-fast)',
        opacity: isPending ? 0.5 : 1,
        padding: 0,
        flexShrink: 0,
      }}
    >
      <HeartIcon size={16} filled />
    </button>
  );
}
