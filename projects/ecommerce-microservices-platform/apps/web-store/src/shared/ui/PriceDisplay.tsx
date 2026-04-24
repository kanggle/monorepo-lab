interface PriceDisplayProps {
  amount: number;
  unitStyle?: React.CSSProperties;
  className?: string;
}

export function PriceDisplay({ amount, unitStyle, className }: PriceDisplayProps) {
  const unitSpan = (
    <span style={unitStyle ?? { fontSize: 'var(--font-size-xs)', fontWeight: 'var(--font-weight-normal)', color: 'var(--color-text-secondary)', marginLeft: '2px' }}>원</span>
  );

  if (className) {
    return (
      <span className={className}>
        {amount.toLocaleString()}
        {unitSpan}
      </span>
    );
  }

  return (
    <>
      {amount.toLocaleString()}
      {unitSpan}
    </>
  );
}
