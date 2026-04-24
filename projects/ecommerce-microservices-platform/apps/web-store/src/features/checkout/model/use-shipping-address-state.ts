import { useEffect, useState } from 'react';
import type { Address, ShippingAddress } from '@repo/types';

function addressToShipping(addr: Address): ShippingAddress {
  return {
    recipient: addr.recipientName,
    phone: addr.phone,
    zipCode: addr.zipCode,
    address1: addr.address1,
    address2: addr.address2 ?? '',
  };
}

export interface UseShippingAddressStateResult {
  selectedAddressId: string;
  address: ShippingAddress;
  handleAddressSelect: (id: string) => void;
  handleAddressSearchSelect: (result: { zipCode: string; address1: string }) => void;
  updateField: (field: keyof ShippingAddress, value: string) => void;
}

export function useShippingAddressState(
  savedAddresses: Address[],
  addressData: { addresses: Address[] } | undefined,
): UseShippingAddressStateResult {
  const [selectedAddressId, setSelectedAddressId] = useState<string>('');
  const [address, setAddress] = useState<ShippingAddress>({
    recipient: '', phone: '', zipCode: '', address1: '', address2: '',
  });

  useEffect(() => {
    if (addressData && !selectedAddressId) {
      const addrs = addressData.addresses;
      const defaultAddr = addrs.find((a) => a.isDefault) ?? addrs[0];
      if (defaultAddr) {
        setSelectedAddressId(defaultAddr.id);
        setAddress(addressToShipping(defaultAddr));
      }
    }
  }, [addressData, selectedAddressId]);

  function handleAddressSelect(id: string) {
    setSelectedAddressId(id);
    if (id === 'new') {
      setAddress({ recipient: '', phone: '', zipCode: '', address1: '', address2: '' });
      return;
    }
    const found = savedAddresses.find((a) => a.id === id);
    if (found) {
      setAddress(addressToShipping(found));
    }
  }

  function updateField(field: keyof ShippingAddress, value: string) {
    setAddress((prev) => ({ ...prev, [field]: value }));
  }

  function handleAddressSearchSelect({ zipCode, address1 }: { zipCode: string; address1: string }) {
    setAddress((prev) => ({ ...prev, zipCode, address1 }));
  }

  return {
    selectedAddressId,
    address,
    handleAddressSelect,
    handleAddressSearchSelect,
    updateField,
  };
}
