'use client';

import { useCallback, useState, useRef } from 'react';
import Link from 'next/link';
import Image from 'next/image';
import { useProfileImage } from '@/shared/context/ProfileImageContext';
import { useClickOutside } from '@/shared/hooks/use-click-outside';
import styles from './Header.module.css';

interface ProfileDropdownProps {
  userName: string | undefined;
  onLogout: () => void;
}

export function ProfileDropdown({ userName, onLogout }: ProfileDropdownProps) {
  const { imageUrl: profileImageUrl } = useProfileImage();
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  useClickOutside(dropdownRef, useCallback(() => setDropdownOpen(false), []));

  return (
    <div ref={dropdownRef} className={styles.profileWrapper}>
      <button
        type="button"
        className={styles.profileLink}
        aria-label="프로필 메뉴"
        onClick={() => setDropdownOpen((o) => !o)}
      >
        <span className={styles.profileAvatar}>
          {profileImageUrl ? (
            <Image
              src={profileImageUrl}
              alt="프로필"
              width={36}
              height={36}
              style={{ width: '100%', height: '100%', borderRadius: '50%', objectFit: 'cover' }}
            />
          ) : (
            userName?.charAt(0).toUpperCase() ?? 'U'
          )}
        </span>
      </button>
      {dropdownOpen && (
        <div className={styles.dropdown}>
          <Link href="/my/profile" className={styles.dropdownItem} onClick={() => setDropdownOpen(false)}>
            내 프로필
          </Link>
          <Link href="/my/orders" className={styles.dropdownItem} onClick={() => setDropdownOpen(false)}>
            주문내역
          </Link>
          <Link href="/my/addresses" className={styles.dropdownItem} onClick={() => setDropdownOpen(false)}>
            배송지 관리
          </Link>
          <hr className={styles.dropdownDivider} />
          <button
            type="button"
            className={styles.dropdownItem}
            onClick={() => { setDropdownOpen(false); onLogout(); }}
          >
            로그아웃
          </button>
        </div>
      )}
    </div>
  );
}
