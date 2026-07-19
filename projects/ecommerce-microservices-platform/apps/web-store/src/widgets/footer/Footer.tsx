import Link from 'next/link';
import styles from './Footer.module.css';

export function Footer() {
  return (
    <footer className={styles.footer}>
      <div className={styles.inner}>
        <div>
          <div className={styles.brand}>WebStore</div>
          <p className={styles.description}>
            합리적인 가격, 빠른 배송. 최고의 쇼핑 경험을 제공합니다.
          </p>
        </div>
        <div>
          <div className={styles.columnTitle}>쇼핑</div>
          <div className={styles.linkList}>
            <Link href="/products" className={styles.link}>전체상품</Link>
          </div>
        </div>
        <div>
          <div className={styles.columnTitle}>고객지원</div>
          <div className={styles.linkList}>
            <Link href="/my/profile" className={styles.link}>마이페이지</Link>
            <Link href="/my/orders" className={styles.link}>주문조회</Link>
            <Link href="/my/addresses" className={styles.link}>배송지관리</Link>
          </div>
        </div>
      </div>
      <div className={styles.bottom}>
        &copy; {new Date().getFullYear()} WebStore. All rights reserved.
      </div>
    </footer>
  );
}
