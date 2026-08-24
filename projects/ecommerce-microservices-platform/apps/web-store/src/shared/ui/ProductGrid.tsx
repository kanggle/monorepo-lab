import styles from './ProductGrid.module.css';

interface ProductGridProps {
  children: React.ReactNode;
}

/**
 * 상품 카드 목록의 단일 그리드 정의.
 * 전체 상품 / 홈 인기 상품 / 검색 결과 / 로딩 스켈레톤이 모두 이 컴포넌트를 통해
 * 같은 열 수와 간격을 사용한다 (사본을 두면 한쪽만 수정되어 열이 어긋난다).
 */
export function ProductGrid({ children }: ProductGridProps) {
  return <div className={styles.grid}>{children}</div>;
}
