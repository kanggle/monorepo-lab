interface ListErrorProps {
  message: string;
  onRetry: () => void;
}

export function ListError({ message, onRetry }: ListErrorProps) {
  return (
    <div
      role="alert"
      style={{
        padding: '48px 24px',
        textAlign: 'center',
        backgroundColor: '#fff',
        borderRadius: '12px',
        border: '1px solid #e0e0e0',
      }}
    >
      <p style={{ color: '#333', fontSize: '0.875rem', marginBottom: '16px' }}>{message}</p>
      <button
        onClick={onRetry}
        style={{
          padding: '8px 20px',
          borderRadius: '8px',
          border: '1px solid #e5e7eb',
          backgroundColor: '#fff',
          cursor: 'pointer',
          fontSize: '0.8125rem',
          color: '#374151',
          fontWeight: 500,
        }}
      >
        다시 시도
      </button>
    </div>
  );
}
