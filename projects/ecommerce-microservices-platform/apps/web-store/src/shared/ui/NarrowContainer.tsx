interface NarrowContainerProps {
  children: React.ReactNode;
}

export function NarrowContainer({ children }: NarrowContainerProps) {
  return (
    <div style={{ maxWidth: '600px', margin: '0 auto', padding: 'var(--space-8) var(--space-6) var(--space-16)' }}>
      {children}
    </div>
  );
}
