interface EmptyStateProps {
  message: string;
}

export function EmptyState({ message }: EmptyStateProps) {
  return (
    <div style={{ padding: '3rem', textAlign: 'center', color: '#666' }}>
      <p>{message}</p>
    </div>
  );
}
