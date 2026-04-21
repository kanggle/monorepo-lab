'use client';

import { useSearchParams, useRouter } from 'next/navigation';

interface PagedData {
  totalElements: number;
  size: number;
}

export function useListParams() {
  const searchParams = useSearchParams();
  const router = useRouter();

  const page = Number(searchParams.get('page') ?? '0');

  const getParam = (key: string): string | null => searchParams.get(key);

  const setFilter = (key: string, value: string | undefined) => {
    const params = new URLSearchParams(searchParams.toString());
    if (value) {
      params.set(key, value);
    } else {
      params.delete(key);
    }
    params.set('page', '0');
    router.push(`?${params.toString()}`);
  };

  const setPage = (newPage: number) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set('page', String(newPage));
    router.push(`?${params.toString()}`);
  };

  const buildPagination = (data: PagedData | undefined) => ({
    page,
    totalPages: data ? Math.ceil(data.totalElements / (data.size || 20)) : 0,
    onPageChange: setPage,
  });

  return { page, getParam, setFilter, setPage, buildPagination };
}
