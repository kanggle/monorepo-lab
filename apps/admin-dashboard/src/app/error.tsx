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
        padding: '24px',
        textAlign: 'center',
      }}
    >
      <h2 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '16px' }}>
        문제가 발생했습니다
      </h2>
      <p style={{ color: '#6b7280', marginBottom: '24px' }}>
        {error.message || '알 수 없는 오류가 발생했습니다.'}
      </p>
      <button
        type="button"
        onClick={reset}
        style={{
          padding: '10px 24px',
          borderRadius: '6px',
          border: 'none',
          backgroundColor: '#1A1A2E',
          color: '#fff',
          fontWeight: 600,
          cursor: 'pointer',
        }}
      >
        다시 시도
      </button>
    </div>
  );
}
