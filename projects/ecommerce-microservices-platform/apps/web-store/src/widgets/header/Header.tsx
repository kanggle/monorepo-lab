'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/features/auth';
import { useCart } from '@/features/cart';
import { ThemeToggle } from '@/shared/ui/ThemeToggle';
import { ProfileDropdown } from './ProfileDropdown';
import styles from './Header.module.css';

export function Header() {
  const { user, isAuthenticated, isLoading, logout } = useAuth();
  const { items } = useCart();
  const [mobileOpen, setMobileOpen] = useState(false);
  const cartCount = items.reduce((sum, item) => sum + item.quantity, 0);

  return (
    <header className={styles.header}>
      <div className={styles.inner}>
        <Link href="/" className={styles.logo}>
          WebStore
        </Link>

        <button
          type="button"
          className={styles.mobileMenuBtn}
          onClick={() => setMobileOpen(!mobileOpen)}
          aria-label={mobileOpen ? '메뉴 닫기' : '메뉴 열기'}
        >
          {mobileOpen ? '\u2715' : '\u2630'}
        </button>

        <nav className={`${styles.nav} ${mobileOpen ? styles.mobileOpen : ''}`}>
          <Link href="/products" className={styles.navLink} onClick={() => setMobileOpen(false)}>
            전체상품
          </Link>
        </nav>

        <div className={styles.actions}>
          <ThemeToggle className={styles.iconButton} />
          {!isLoading && isAuthenticated && (
            <Link href="/my/notifications" className={styles.iconButton} aria-label="알림" title="알림">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
                <path d="M13.73 21a2 2 0 0 1-3.46 0" />
              </svg>
            </Link>
          )}
          {!isLoading && isAuthenticated && (
            <Link href="/cart" className={`${styles.iconButton} ${styles.cartLink}`} aria-label="장바구니">
              <span className={styles.cartIcon} aria-hidden="true">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M6 2L3 6v14a2 2 0 002 2h14a2 2 0 002-2V6l-3-4z"/>
                  <line x1="3" y1="6" x2="21" y2="6"/>
                  <path d="M16 10a4 4 0 01-8 0"/>
                </svg>
              </span>
              <span className={styles.cartBadge} style={{ visibility: cartCount > 0 ? 'visible' : 'hidden' }}>
                {cartCount > 99 ? '99+' : cartCount || 0}
              </span>
            </Link>
          )}
          {isLoading ? (
            <div style={{ width: 36, height: 36 }} />
          ) : isAuthenticated ? (
            <ProfileDropdown userName={user?.name} onLogout={logout} />
          ) : (
            <Link href="/login" className={styles.authLink}>
              로그인
            </Link>
          )}
        </div>
      </div>
    </header>
  );
}
