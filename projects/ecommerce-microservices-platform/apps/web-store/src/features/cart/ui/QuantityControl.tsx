interface QuantityControlProps {
  quantity: number;
  onDecrease: () => void;
  onIncrease: () => void;
}

export function QuantityControl({ quantity, onDecrease, onIncrease }: QuantityControlProps) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-sm)', overflow: 'hidden', flexShrink: 0, width: 88 }}>
      <button
        type="button"
        onClick={onDecrease}
        disabled={quantity <= 1}
        style={{
          width: 28, height: 28, border: 'none', background: 'var(--color-bg-secondary)',
          fontSize: 'var(--font-size-sm)', cursor: quantity <= 1 ? 'not-allowed' : 'pointer',
          color: quantity <= 1 ? 'var(--color-text-muted)' : 'var(--color-text)',
        }}
      >
        −
      </button>
      <span style={{
        flex: 1, textAlign: 'center', fontSize: 'var(--font-size-sm)',
        fontWeight: 'var(--font-weight-semibold)', lineHeight: '28px',
        borderLeft: '1px solid var(--color-border)', borderRight: '1px solid var(--color-border)',
      }}>
        {quantity}
      </span>
      <button
        type="button"
        onClick={onIncrease}
        style={{
          width: 28, height: 28, border: 'none', background: 'var(--color-bg-secondary)',
          fontSize: 'var(--font-size-sm)', cursor: 'pointer', color: 'var(--color-text)',
        }}
      >
        +
      </button>
    </div>
  );
}
