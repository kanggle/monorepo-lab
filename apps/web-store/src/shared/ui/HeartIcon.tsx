interface HeartIconProps {
  size?: number;
  filled?: boolean;
  activeColor?: string;
  inactiveStroke?: string;
}

export function HeartIcon({
  size = 16,
  filled = false,
  activeColor = 'var(--color-error)',
  inactiveStroke = 'var(--color-text-secondary)',
}: HeartIconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill={filled ? activeColor : 'none'}
      stroke={filled ? activeColor : inactiveStroke}
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
    </svg>
  );
}
