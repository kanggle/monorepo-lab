'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useAuth } from '@/shared/hooks';

interface NavItem {
  label: string;
  href: string;
  icon: string;
}

const NAV_ITEMS: NavItem[] = [
  { label: '대시보드', href: '/dashboard', icon: '📊' },
  { label: '상품 관리', href: '/products', icon: '📦' },
  { label: '주문 관리', href: '/orders', icon: '🛒' },
  { label: '사용자 관리', href: '/users', icon: '👥' },
  { label: '프로모션 관리', href: '/promotions', icon: '🎁' },
  { label: '배송 관리', href: '/shippings', icon: '🚚' },
  { label: '알림 관리', href: '/notifications/templates', icon: '🔔' },
];

export function Sidebar() {
  const pathname = usePathname();
  const router = useRouter();
  const { user, logout } = useAuth();

  async function handleLogout() {
    await logout();
    router.push('/login');
  }

  return (
    <aside
      style={{
        width: '260px',
        minWidth: '260px',
        minHeight: '100vh',
        background: '#171A1C',
        color: '#fff',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <div
        style={{
          padding: '28px 24px',
          borderBottom: '1px solid rgba(255,255,255,0.1)',
        }}
      >
        <div style={{ fontSize: '1.25rem', fontWeight: 800, letterSpacing: '-0.02em' }}>
          <span style={{ color: '#fff' }}>Admin</span>
          <span style={{ color: '#555', fontWeight: 400, fontSize: '0.75rem', marginLeft: '8px' }}>v1.0</span>
        </div>
      </div>

      <nav style={{ flex: 1, padding: '16px 12px' }}>
        <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: '4px' }}>
          {NAV_ITEMS.map((item) => {
            const isActive = pathname.startsWith(item.href);
            return (
              <li key={item.href}>
                <Link
                  href={item.href}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '12px',
                    padding: '10px 16px',
                    color: isActive ? '#fff' : '#888',
                    backgroundColor: isActive ? 'rgba(255,255,255,0.1)' : 'transparent',
                    borderRadius: '8px',
                    textDecoration: 'none',
                    fontSize: '0.875rem',
                    fontWeight: isActive ? 600 : 400,
                    transition: 'all 0.15s',
                    borderLeft: isActive ? '3px solid #fff' : '3px solid transparent',
                  }}
                >
                  <span style={{ fontSize: '1rem' }}>{item.icon}</span>
                  {item.label}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>

      <div
        style={{
          padding: '16px 16px 20px',
          borderTop: '1px solid rgba(255,255,255,0.1)',
        }}
      >
        {user && (
          <div style={{
            fontSize: '0.75rem',
            color: '#888',
            marginBottom: '12px',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            padding: '0 4px',
          }}>
            {user.email}
          </div>
        )}
        <button
          onClick={handleLogout}
          style={{
            width: '100%',
            padding: '8px 12px',
            backgroundColor: 'transparent',
            color: '#888',
            border: '1px solid rgba(255,255,255,0.15)',
            borderRadius: '8px',
            cursor: 'pointer',
            fontSize: '0.8125rem',
            transition: 'all 0.15s',
          }}
        >
          로그아웃
        </button>
      </div>
    </aside>
  );
}
