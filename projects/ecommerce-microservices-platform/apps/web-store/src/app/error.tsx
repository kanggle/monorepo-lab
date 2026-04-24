'use client';

interface ErrorProps {
  error: Error & { digest?: string };
  reset: () => void;
}

export default function GlobalError({ error, reset }: ErrorProps) {
  return (
    <div
      role="alert"
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '50vh',
        padding: 'var(--space-6)',
        textAlign: 'center',
      }}
    >
      <h2
        style={{
          fontSize: 'var(--font-size-2xl)',
          fontWeight: 'var(--font-weight-bold)',
          marginBottom: 'var(--space-4)',
          color: 'var(--color-text)',
        }}
      >
        문제가 발생했습니다
      </h2>
      <p
        style={{
          color: 'var(--color-text-secondary)',
          marginBottom: 'var(--space-6)',
        }}
      >
        {error.message || '알 수 없는 오류가 발생했습니다.'}
      </p>
      <button
        type="button"
        onClick={reset}
        aria-label="다시 시도"
        className="btn btn-primary btn-lg"
      >
        다시 시도
      </button>
    </div>
  );
}
