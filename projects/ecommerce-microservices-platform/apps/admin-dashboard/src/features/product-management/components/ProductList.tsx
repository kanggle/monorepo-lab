'use client';

import { useRouter } from 'next/navigation';
import { DataTable, StatusBadge, FilterBar, ListError } from '@/shared/ui';
import type { ColumnDef } from '@/shared/ui';
import { useProducts } from '../hooks/use-products';
import { PRODUCT_STATUS_OPTIONS } from '@/shared/lib/status-options';
import type { ProductSummary } from '@repo/types';

const thumbnailStyle: React.CSSProperties = {
  width: 40,
  height: 40,
  objectFit: 'cover',
  borderRadius: 4,
  backgroundColor: '#f3f4f6',
};

const placeholderStyle: React.CSSProperties = {
  ...thumbnailStyle,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  color: '#9ca3af',
  fontSize: 12,
};

const columns: ColumnDef<ProductSummary>[] = [
  {
    key: 'thumbnailUrl',
    header: '',
    render: (product: ProductSummary) =>
      product.thumbnailUrl ? (
        <img src={product.thumbnailUrl} alt={product.name} style={thumbnailStyle} />
      ) : (
        <div style={placeholderStyle}>-</div>
      ),
  },
  { key: 'name', header: '상품명', sortable: true },
  {
    key: 'price',
    header: '가격',
    sortable: true,
    render: (product: ProductSummary) => `${product.price.toLocaleString()}원`,
  },
  {
    key: 'status',
    header: '상태',
    sortable: true,
    render: (product: ProductSummary) => <StatusBadge status={product.status} />,
  },
  { key: 'categoryId', header: '카테고리', sortable: true },
];

export function ProductList() {
  const router = useRouter();
  const { data, isLoading, isError, refetch, pagination, filters } = useProducts();

  if (isError) {
    return <ListError message="상품 목록을 불러오는데 실패했습니다." onRetry={() => refetch()} />;
  }

  return (
    <div>
      <FilterBar
        searchPlaceholder="상품명 검색..."
        searchValue={filters.name ?? ''}
        onSearchChange={(value) => filters.setFilter('name', value || undefined)}
        statusOptions={PRODUCT_STATUS_OPTIONS}
        statusValue={filters.status}
        onStatusChange={(value) => filters.setFilter('status', value)}
      />
      <DataTable<ProductSummary>
        columns={columns}
        data={data?.content ?? []}
        pagination={pagination}
        isLoading={isLoading}
        emptyMessage="등록된 상품이 없습니다."
        onRowClick={(item) => router.push(`/products/${item.id}`)}
      />
    </div>
  );
}
