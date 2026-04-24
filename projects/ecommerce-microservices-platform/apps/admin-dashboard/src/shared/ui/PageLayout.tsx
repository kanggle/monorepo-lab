'use client';

import Link from 'next/link';

interface Action {
  label: string;
  href?: string;
  variant?: 'primary' | 'secondary' | 'danger';
  disabled?: boolean;
  onClick?: () => void;
}

interface PageLayoutProps {
  title: string;
  actions?: Action[];
  children: React.ReactNode;
}

const VARIANT_STYLES: Record<string, React.CSSProperties> = {
  primary: { backgroundColor: '#1A1A2E', color: '#fff', border: 'none' },
  secondary: { backgroundColor: '#fff', color: '#333', border: '1px solid #ccc' },
  danger: { backgroundColor: '#1A1A2E', color: '#fff', border: 'none' },
};

export function PageLayout({ title, actions, children }: PageLayoutProps) {
  return (
    <div style={{ padding: '32px 40px', maxWidth: '1200px' }}>
      <header
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '28px',
        }}
      >
        <h1 style={{ fontSize: '1.625rem', fontWeight: 700, margin: 0, color: '#111827', letterSpacing: '-0.02em' }}>
          {title}
        </h1>
        {actions && actions.length > 0 && (
          <div style={{ display: 'flex', gap: '8px' }}>
            {actions.map((action) =>
              action.href ? (
                <Link
                  key={action.label}
                  href={action.href}
                  aria-label={action.label}
                  style={{
                    padding: '9px 20px',
                    borderRadius: '8px',
                    textDecoration: 'none',
                    fontSize: '0.875rem',
                    fontWeight: 500,
                    transition: 'opacity 0.15s',
                    ...(VARIANT_STYLES[action.variant ?? 'primary'] ?? VARIANT_STYLES.primary),
                  }}
                >
                  {action.label}
                </Link>
              ) : (
                <button
                  key={action.label}
                  onClick={action.onClick}
                  disabled={action.disabled}
                  aria-label={action.label}
                  style={{
                    padding: '9px 20px',
                    borderRadius: '8px',
                    cursor: action.disabled ? 'not-allowed' : 'pointer',
                    fontSize: '0.875rem',
                    fontWeight: 500,
                    opacity: action.disabled ? 0.5 : 1,
                    transition: 'opacity 0.15s',
                    ...(VARIANT_STYLES[action.variant ?? 'primary'] ?? VARIANT_STYLES.primary),
                  }}
                >
                  {action.label}
                </button>
              ),
            )}
          </div>
        )}
      </header>
      <main>{children}</main>
    </div>
  );
}

PageLayout.Skeleton = function Skeleton() {
  return (
    <div style={{ padding: '32px 40px' }}>
      <div
        style={{
          height: '32px',
          width: '200px',
          backgroundColor: '#e5e7eb',
          borderRadius: '6px',
          marginBottom: '28px',
        }}
      />
      <div
        style={{
          height: '200px',
          backgroundColor: '#f3f4f6',
          borderRadius: '12px',
        }}
      />
    </div>
  );
};
