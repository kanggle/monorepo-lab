import { useState } from 'react';

export interface UsePaginationResult {
  page: number;
  size: number;
  totalPages: number;
  handlePageChange: (newPage: number) => void;
  handleSizeChange: (newSize: number) => void;
}

export function usePagination(
  totalElements: number,
  defaultSize = 20,
): UsePaginationResult {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(defaultSize);

  const totalPages = Math.max(1, Math.ceil(totalElements / size));

  function handlePageChange(newPage: number) {
    if (newPage >= 0) {
      setPage(newPage);
    }
  }

  function handleSizeChange(newSize: number) {
    setSize(newSize);
    setPage(0);
  }

  return { page, size, totalPages, handlePageChange, handleSizeChange };
}
