import type { Address, ShippingAddress } from '@repo/types';
import { Skeleton, AddressSearch, PhoneFieldError } from '@/shared/ui';

interface AddressSectionProps {
  addressLoading: boolean;
  savedAddresses: Address[];
  selectedAddressId: string;
  address: ShippingAddress;
  phoneValid: boolean;
  isNewAddress: boolean;
  onAddressSelect: (id: string) => void;
  onAddressSearchSelect: (result: { zipCode: string; address1: string }) => void;
  onFieldChange: (field: keyof ShippingAddress, value: string) => void;
}

export function AddressSection({
  addressLoading,
  savedAddresses,
  selectedAddressId,
  address,
  phoneValid,
  isNewAddress,
  onAddressSelect,
  onAddressSearchSelect,
  onFieldChange,
}: AddressSectionProps) {
  return (
    <section style={{ marginBottom: 'var(--space-8)' }}>
      <h2 className="section-title">배송지 정보</h2>

      {addressLoading && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)', marginBottom: 'var(--space-4)' }}>
          <Skeleton width="100%" height="72px" borderRadius="var(--radius-md)" />
          <Skeleton width="100%" height="72px" borderRadius="var(--radius-md)" />
        </div>
      )}

      {!addressLoading && savedAddresses.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)', marginBottom: 'var(--space-4)' }}>
          {savedAddresses.map((addr) => (
            <SavedAddressOption
              key={addr.id}
              addr={addr}
              selected={selectedAddressId === addr.id}
              onSelect={onAddressSelect}
            />
          ))}
          <NewAddressOption
            selected={selectedAddressId === 'new'}
            onSelect={onAddressSelect}
          />
        </div>
      )}

      {!addressLoading && (isNewAddress || savedAddresses.length === 0) && (
        <ShippingFormSection
          address={address}
          phoneValid={phoneValid}
          onFieldChange={onFieldChange}
          onAddressSearchSelect={onAddressSearchSelect}
        />
      )}
    </section>
  );
}

interface SavedAddressOptionProps {
  addr: Address;
  selected: boolean;
  onSelect: (id: string) => void;
}

function SavedAddressOption({ addr, selected, onSelect }: SavedAddressOptionProps) {
  return (
    <label
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 'var(--space-3)',
        padding: 'var(--space-3)',
        border: selected ? '2px solid var(--color-primary)' : '1px solid var(--color-border-light)',
        borderRadius: 'var(--radius-md)',
        cursor: 'pointer',
        background: selected ? 'rgba(26, 26, 46, 0.03)' : 'var(--color-white)',
        transition: 'all var(--transition-fast)',
      }}
    >
      <input
        type="radio"
        name="savedAddress"
        checked={selected}
        onChange={() => onSelect(addr.id)}
        style={{ flexShrink: 0 }}
      />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', marginBottom: '2px' }}>
          <span style={{ fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-semibold)' }}>{addr.label}</span>
          {addr.isDefault && (
            <span style={{ display: 'inline-block', padding: '1px 6px', fontSize: '0.65rem', fontWeight: 'var(--font-weight-semibold)', backgroundColor: 'var(--color-primary)', color: '#fff', borderRadius: 'var(--radius-full)' }}>기본</span>
          )}
        </div>
        <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
          {addr.recipientName} · {addr.phone}
        </div>
        <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
          ({addr.zipCode}) {addr.address1}{addr.address2 ? ` ${addr.address2}` : ''}
        </div>
      </div>
    </label>
  );
}

interface NewAddressOptionProps {
  selected: boolean;
  onSelect: (id: string) => void;
}

function NewAddressOption({ selected, onSelect }: NewAddressOptionProps) {
  return (
    <label
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 'var(--space-3)',
        padding: 'var(--space-3)',
        border: selected ? '2px solid var(--color-primary)' : '1px solid var(--color-border-light)',
        borderRadius: 'var(--radius-md)',
        cursor: 'pointer',
        background: selected ? 'rgba(26, 26, 46, 0.03)' : 'var(--color-white)',
        transition: 'all var(--transition-fast)',
      }}
    >
      <input
        type="radio"
        name="savedAddress"
        checked={selected}
        onChange={() => onSelect('new')}
        style={{ flexShrink: 0 }}
      />
      <span style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)' }}>새 배송지 직접 입력</span>
    </label>
  );
}

interface ShippingFormSectionProps {
  address: ShippingAddress;
  phoneValid: boolean;
  onFieldChange: (field: keyof ShippingAddress, value: string) => void;
  onAddressSearchSelect: (result: { zipCode: string; address1: string }) => void;
}

function ShippingFormSection({
  address,
  phoneValid,
  onFieldChange,
  onAddressSearchSelect,
}: ShippingFormSectionProps) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
      <div className="form-group" style={{ marginBottom: 0 }}>
        <label htmlFor="recipient" className="label">수령인</label>
        <input id="recipient" type="text" className="input" value={address.recipient} onChange={(e) => onFieldChange('recipient', e.target.value)} required />
      </div>
      <div className="form-group" style={{ marginBottom: 0 }}>
        <label htmlFor="phone" className="label">전화번호</label>
        <input id="phone" type="tel" className="input" value={address.phone} onChange={(e) => onFieldChange('phone', e.target.value)} required placeholder="010-0000-0000" />
        <PhoneFieldError phone={address.phone} isValid={phoneValid} />
      </div>
      <div className="form-group" style={{ marginBottom: 0 }}>
        <label className="label">주소</label>
        <div style={{ display: 'flex', gap: 'var(--space-2)', marginBottom: 'var(--space-2)' }}>
          <input id="address1" type="text" className="input" value={address.address1} readOnly placeholder="주소 검색을 눌러주세요" style={{ flex: 1, background: 'var(--color-bg-secondary)' }} />
          <AddressSearch onSelect={onAddressSearchSelect} />
        </div>
        <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
          <input id="address2" type="text" className="input" value={address.address2 ?? ''} onChange={(e) => onFieldChange('address2', e.target.value)} placeholder="상세주소 입력" style={{ flex: 1 }} />
          <input id="zipCode" type="text" className="input" value={address.zipCode} readOnly placeholder="우편번호" style={{ width: 100, background: 'var(--color-bg-secondary)', textAlign: 'center' }} />
        </div>
      </div>
    </div>
  );
}
