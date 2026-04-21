'use client';

import { useEffect, useState } from 'react';

interface FilterOption {
  label: string;
  value: string;
}

interface FilterBarProps {
  searchPlaceholder?: string;
  searchValue?: string;
  onSearchChange?: (value: string) => void;
  statusOptions?: FilterOption[];
  statusValue?: string;
  onStatusChange?: (value: string | undefined) => void;
}

export function FilterBar({
  searchPlaceholder = '검색...',
  searchValue = '',
  onSearchChange,
  statusOptions,
  statusValue,
  onStatusChange,
}: FilterBarProps) {
  const [localSearch, setLocalSearch] = useState(searchValue);

  useEffect(() => {
    setLocalSearch(searchValue);
  }, [searchValue]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSearchChange?.(localSearch);
  };

  return (
    <div
      style={{
        display: 'flex',
        gap: '12px',
        marginBottom: '20px',
        alignItems: 'center',
        padding: '16px 20px',
        backgroundColor: '#fff',
        borderRadius: '12px',
        border: '1px solid #e5e7eb',
        boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
      }}
    >
      {onSearchChange && (
        <form onSubmit={handleSearchSubmit} style={{ display: 'flex', gap: '8px', flex: 1 }}>
          <label htmlFor="filter-search" className="sr-only" style={{ position: 'absolute', width: '1px', height: '1px', padding: 0, margin: '-1px', overflow: 'hidden', clip: 'rect(0,0,0,0)', border: 0 }}>검색</label>
          <input
            id="filter-search"
            type="text"
            value={localSearch}
            onChange={(e) => setLocalSearch(e.target.value)}
            placeholder={searchPlaceholder}
            style={{
              padding: '9px 14px',
              border: '1px solid #e5e7eb',
              borderRadius: '8px',
              fontSize: '0.875rem',
              width: '280px',
              backgroundColor: '#f9fafb',
              outline: 'none',
              transition: 'border-color 0.15s',
            }}
          />
          <button
            type="submit"
            aria-label="검색"
            style={{
              padding: '9px 18px',
              borderRadius: '8px',
              border: 'none',
              backgroundColor: '#1A1A2E',
              color: '#fff',
              cursor: 'pointer',
              fontSize: '0.8125rem',
              fontWeight: 500,
              transition: 'opacity 0.15s',
            }}
          >
            검색
          </button>
        </form>
      )}

      {statusOptions && onStatusChange && (
        <select
          aria-label="상태 필터"
          value={statusValue ?? ''}
          onChange={(e) => onStatusChange(e.target.value || undefined)}
          style={{
            padding: '9px 14px',
            border: '1px solid #e5e7eb',
            borderRadius: '8px',
            fontSize: '0.875rem',
            backgroundColor: '#f9fafb',
            color: '#374151',
            outline: 'none',
            cursor: 'pointer',
          }}
        >
          <option value="">전체 상태</option>
          {statusOptions.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
      )}
    </div>
  );
}
