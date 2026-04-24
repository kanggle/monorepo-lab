'use client';

interface StarRatingProps {
  rating: number;
  onChange?: (rating: number) => void;
  size?: 'sm' | 'md';
}

export function StarRating({ rating, onChange, size = 'sm' }: StarRatingProps) {
  const fontSize = size === 'md' ? '1.5rem' : '1rem';
  const interactive = typeof onChange === 'function';

  return (
    <div
      style={{ display: 'inline-flex', gap: '2px' }}
      role={interactive ? 'radiogroup' : undefined}
      aria-label={interactive ? '별점 선택' : `별점 ${rating}점`}
    >
      {[1, 2, 3, 4, 5].map((star) => (
        <button
          key={star}
          type="button"
          onClick={interactive ? () => onChange(star) : undefined}
          disabled={!interactive}
          aria-label={`${star}점`}
          aria-checked={interactive ? star === rating : undefined}
          role={interactive ? 'radio' : undefined}
          style={{
            background: 'none',
            border: 'none',
            padding: 0,
            cursor: interactive ? 'pointer' : 'default',
            fontSize,
            color: star <= rating ? '#facc15' : '#d1d5db',
            lineHeight: 1,
          }}
        >
          &#9733;
        </button>
      ))}
    </div>
  );
}
