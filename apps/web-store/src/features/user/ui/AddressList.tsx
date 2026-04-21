'use client';

import { useState } from 'react';
import type { Address } from '@repo/types';
import { isApiError, ERROR_MESSAGES } from '@repo/types/guards';
import { deleteAddress, updateAddress } from '../api/address-api';
import { DeleteConfirmation } from './DeleteConfirmation';
import { maskPhone } from '@/shared/lib/mask-phone';

const MAX_ADDRESSES = 10;

const styles = {
  header: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-4)' } as const,
  count: { color: 'var(--color-text-secondary)', fontSize: 'var(--font-size-sm)' } as const,
  limitWarning: { color: 'var(--color-warning)', fontSize: 'var(--font-size-sm)', marginBottom: 'var(--space-3)' } as const,
  error: { color: 'var(--color-error)', marginBottom: 'var(--space-3)' } as const,
  list: { display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' } as const,
  cardHeader: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-2)' } as const,
  labelRow: { display: 'flex', alignItems: 'center', gap: 'var(--space-2)' } as const,
  actionRow: { display: 'flex', gap: 'var(--space-2)' } as const,
  smallBtn: { fontSize: 'var(--font-size-xs)', padding: 'var(--space-1) var(--space-2)' } as const,
  detailText: { margin: 'var(--space-1) 0', color: 'var(--color-text-secondary)' } as const,
  cardDefault: { border: '2px solid var(--color-primary)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)', background: 'rgba(26, 26, 46, 0.03)' } as const,
  card: { border: '1px solid var(--color-border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)' } as const,
  labelText: { fontWeight: 'var(--font-weight-bold)' } as const,
  badge: { display: 'inline-block', padding: '2px 8px', fontSize: 'var(--font-size-xs)', fontWeight: 'var(--font-weight-semibold)', backgroundColor: 'var(--color-primary)', color: '#fff', borderRadius: 'var(--radius-full)' } as const,
};

interface AddressListProps {
  addresses: Address[];
  onAddClick: () => void;
  onEditClick: (address: Address) => void;
  onChanged: () => void;
  onSetDefault: (addressId: string) => void;
  onDeleted: (addressId: string) => void;
}

export function AddressList({ addresses, onAddClick, onEditClick, onChanged, onSetDefault, onDeleted }: AddressListProps) {
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);
  const [settingDefaultId, setSettingDefaultId] = useState<string | null>(null);
  const [error, setError] = useState('');

  const isAtLimit = addresses.length >= MAX_ADDRESSES;

  async function handleDelete(addressId: string) {
    setError('');
    setDeletingId(addressId);
    try {
      await deleteAddress(addressId);
      setConfirmDeleteId(null);
      onDeleted(addressId);
    } catch (err) {
      if (isApiError(err)) {
        setError(ERROR_MESSAGES[err.code] ?? err.message ?? '배송지 삭제에 실패했습니다.');
      } else {
        setError('배송지 삭제에 실패했습니다.');
      }
    } finally {
      setDeletingId(null);
    }
  }

  async function handleSetDefault(addressId: string) {
    setError('');
    setSettingDefaultId(addressId);
    try {
      await updateAddress(addressId, { isDefault: true });
      onSetDefault(addressId);
    } catch (err) {
      onChanged();
      if (isApiError(err)) {
        setError(err.message ?? '기본 배송지 변경에 실패했습니다.');
      } else {
        setError('기본 배송지 변경에 실패했습니다.');
      }
    } finally {
      setSettingDefaultId(null);
    }
  }

  return (
    <div>
      <div style={styles.header}>
        <p style={styles.count}>{addresses.length}개 / 최대 {MAX_ADDRESSES}개</p>
        <button onClick={onAddClick} disabled={isAtLimit}
          className="btn btn-primary">
          배송지 추가
        </button>
      </div>

      {isAtLimit && <p style={styles.limitWarning}>배송지는 최대 {MAX_ADDRESSES}개까지 등록 가능합니다.</p>}
      {error && <p role="alert" style={styles.error}>{error}</p>}

      <div style={styles.list}>
        {addresses.map((address) => (
          <div key={address.id}
            style={address.isDefault ? styles.cardDefault : styles.card}>
            <div style={styles.cardHeader}>
              <div style={styles.labelRow}>
                <span style={styles.labelText}>{address.label}</span>
                {address.isDefault && <span style={styles.badge}>기본 배송지</span>}
              </div>
              <div style={styles.actionRow}>
                {!address.isDefault && (
                  <button onClick={() => handleSetDefault(address.id)} disabled={settingDefaultId === address.id}
                    aria-label={`${address.label} 기본 배송지로 설정`}
                    className="btn btn-outline" style={styles.smallBtn}>
                    {settingDefaultId === address.id ? '설정 중...' : '기본으로 설정'}
                  </button>
                )}
                <button onClick={() => onEditClick(address)} aria-label={`${address.label} 수정`} className="btn btn-outline" style={styles.smallBtn}>수정</button>
                <button onClick={() => setConfirmDeleteId(address.id)} aria-label={`${address.label} 삭제`} className="btn btn-outline" style={{ ...styles.smallBtn, color: 'var(--color-error)' }}>삭제</button>
              </div>
            </div>
            <p style={styles.detailText}>{address.recipientName} / {maskPhone(address.phone)}</p>
            <p style={styles.detailText}>({address.zipCode}) {address.address1}{address.address2 ? ` ${address.address2}` : ''}</p>

            {confirmDeleteId === address.id && (
              <DeleteConfirmation isDeleting={deletingId === address.id} onConfirm={() => handleDelete(address.id)} onCancel={() => setConfirmDeleteId(null)} />
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
