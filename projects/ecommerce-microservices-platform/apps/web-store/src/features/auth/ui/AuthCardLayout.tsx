interface AuthCardLayoutProps {
  children: React.ReactNode;
}

export function AuthCardLayout({ children }: AuthCardLayoutProps) {
  return (
    <main style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: 'calc(100vh - var(--header-height))',
      padding: 'var(--space-6)',
    }}>
      <div className="card" style={{
        maxWidth: '420px',
        width: '100%',
        padding: 'var(--space-10)',
        boxShadow: '0 4px 24px var(--color-shadow)',
      }}>
        {children}
      </div>
    </main>
  );
}
