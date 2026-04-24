'use client';

import { useState } from 'react';
import type { Address } from '@repo/types';
import { ErrorMessage, EmptyState } from '@repo/ui';
import { Skeleton } from '@/shared/ui/Skeleton';
import { AddressList } from './AddressList';
import { AddressForm } from './AddressForm';
import { useAddresses } from '@/entities/user';

type ViewMode = 'list' | 'add' | 'edit';

export function AddressManager() {
  const { data, isLoading, isError, refetch, invalidate } = useAddresses();
  const [viewMode, setViewMode] = useState<ViewMode>('list');
  const [editingAddress, setEditingAddress] = useState<Address | null>(null);

  const addresses = data?.addresses ?? [];
  const error = isError ? '배송지 목록을 불러오는데 실패했습니다.' : '';

  function handleAddClick() {
    setViewMode('add');
    setEditingAddress(null);
  }

  function handleEditClick(address: Address) {
    setViewMode('edit');
    setEditingAddress(address);
  }

  function handleSaved() {
    setViewMode('list');
    setEditingAddress(null);
    invalidate();
  }

  function handleCancel() {
    setViewMode('list');
    setEditingAddress(null);
  }

  function handleSetDefault(_addressId: string) {
    invalidate();
  }

  function handleDeleted(_addressId: string) {
    invalidate();
  }

  return (
    <div>
      <h1 className="page-title">배송지 관리</h1>

      {isLoading && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
          {Array.from({ length: 2 }).map((_, i) => (
            <div key={i} style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border-light)', borderRadius: 'var(--radius-md)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', marginBottom: 'var(--space-2)' }}>
                <Skeleton width="60px" height="14px" />
                <Skeleton width="40px" height="18px" borderRadius="var(--radius-full)" />
              </div>
              <Skeleton width="50%" height="12px" />
              <div style={{ marginTop: 'var(--space-2)' }}>
                <Skeleton width="80%" height="12px" />
              </div>
            </div>
          ))}
        </div>
      )}
      {error && <ErrorMessage message={error} onRetry={() => refetch()} />}

      {!isLoading && !error && (
        <>
          {viewMode !== 'list' && (
            <AddressForm
              address={viewMode === 'edit' ? (editingAddress ?? undefined) : undefined}
              onSaved={handleSaved}
              onCancel={handleCancel}
            />
          )}

          {viewMode === 'list' && addresses.length === 0 && (
            <div>
              <EmptyState message="등록된 배송지가 없습니다." />
              <div style={{ textAlign: 'center' }}>
                <button
                  onClick={handleAddClick}
                  className="btn btn-primary btn-lg"
                >
                  첫 배송지 추가하기
                </button>
              </div>
            </div>
          )}

          {viewMode === 'list' && addresses.length > 0 && (
            <AddressList
              addresses={addresses}
              onAddClick={handleAddClick}
              onEditClick={handleEditClick}
              onChanged={() => invalidate()}
              onSetDefault={handleSetDefault}
              onDeleted={handleDeleted}
            />
          )}
        </>
      )}
    </div>
  );
}
