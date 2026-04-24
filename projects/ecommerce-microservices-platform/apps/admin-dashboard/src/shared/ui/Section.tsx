import type { ReactNode } from 'react';

interface SectionProps {
  title: string;
  children: ReactNode;
}

export function Section({ title, children }: SectionProps) {
  return (
    <section
      style={{
        marginBottom: '28px',
        backgroundColor: '#fff',
        borderRadius: '12px',
        border: '1px solid #e5e7eb',
        padding: '24px',
        boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
      }}
    >
      <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '16px', color: '#111827' }}>
        {title}
      </h2>
      {children}
    </section>
  );
}
