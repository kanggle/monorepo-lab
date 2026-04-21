'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useRequireAuth } from '@/features/auth';

const navItems = [
  { href: '/my/profile', label: '프로필' },
  { href: '/my/orders', label: '주문내역' },
  { href: '/my/wishlist', label: '위시리스트' },
  { href: '/my/reviews', label: '내 리뷰' },
  { href: '/my/coupons', label: '쿠폰' },
  { href: '/my/addresses', label: '배송지 관리' },
  { href: '/my/notifications', label: '알림' },
];

export default function MyLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { isReady } = useRequireAuth();

  if (!isReady) return null;

  return (
    <div
      className="container"
      style={{
        paddingTop: 'var(--space-10)',
        paddingBottom: 'var(--space-16)',
        display: 'flex',
        gap: 'var(--space-8)',
        alignItems: 'flex-start',
      }}
    >
      <aside
        style={{
          width: '200px',
          flexShrink: 0,
          background: 'var(--color-white)',
          border: '1px solid var(--color-border-light)',
          borderRadius: 'var(--radius-lg)',
          padding: 'var(--space-4)',
        }}
      >
        <nav style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-1)' }}>
          {navItems.map(({ href, label }) => {
            const active = pathname === href || pathname.startsWith(href + '/');
            return (
              <Link
                key={href}
                href={href}
                style={{
                  display: 'block',
                  padding: 'var(--space-3) var(--space-4)',
                  borderRadius: 'var(--radius-md)',
                  fontSize: 'var(--font-size-sm)',
                  fontWeight: active ? 'var(--font-weight-semibold)' : 'var(--font-weight-medium)',
                  color: active ? 'var(--color-white)' : 'var(--color-text-secondary)',
                  background: active ? 'var(--color-primary)' : 'transparent',
                  textDecoration: 'none',
                  transition: 'background var(--transition-fast), color var(--transition-fast)',
                }}
              >
                {label}
              </Link>
            );
          })}
        </nav>
      </aside>

      <main style={{ flex: 1, minWidth: 0, maxWidth: '50%' }}>
        {children}
      </main>
    </div>
  );
}
