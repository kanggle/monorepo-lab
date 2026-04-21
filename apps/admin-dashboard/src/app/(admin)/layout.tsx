'use client';

import { AuthGuard } from '@/shared/hooks';
import { Sidebar } from '@/shared/ui';

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGuard>
      <div style={{ display: 'flex' }}>
        <Sidebar />
        <div style={{ flex: 1, minHeight: '100vh', backgroundColor: '#fafafa' }}>
          {children}
        </div>
      </div>
    </AuthGuard>
  );
}
