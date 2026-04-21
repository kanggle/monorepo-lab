interface ErrorMessageProps {
  message: string;
  onRetry?: () => void;
  color?: string;
}

export function ErrorMessage({ message, onRetry, color }: ErrorMessageProps) {
  return (
    <div role="alert" style={{ padding: '2rem', textAlign: 'center' }}>
      <p style={color ? { color } : undefined}>{message}</p>
      {onRetry && (
        <button onClick={onRetry} style={{ marginTop: '1rem' }}>
          다시 시도
        </button>
      )}
    </div>
  );
}
