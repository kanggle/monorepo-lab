import Link from 'next/link';

export default function ProductNotFound() {
  return (
    <div
      className="container"
      style={{
        paddingTop: 'var(--space-16)',
        paddingBottom: 'var(--space-16)',
        textAlign: 'center',
      }}
    >
      <h1
        style={{
          fontSize: 'var(--font-size-2xl)',
          fontWeight: 'var(--font-weight-bold)',
          marginBottom: 'var(--space-4)',
        }}
      >
        상품을 찾을 수 없습니다
      </h1>
      <p
        style={{
          color: 'var(--color-text-secondary)',
          marginBottom: 'var(--space-6)',
        }}
      >
        요청하신 상품이 존재하지 않거나 삭제되었습니다.
      </p>
      <Link
        href="/products"
        className="btn btn-primary btn-lg"
      >
        상품 목록으로 돌아가기
      </Link>
    </div>
  );
}
