import { useState } from 'react';

export interface AddressFields {
  zipCode: string;
  address1: string;
}

export interface UseAddressFieldsResult {
  zipCode: string;
  address1: string;
  setZipCode: (value: string) => void;
  setAddress1: (value: string) => void;
  handleAddressSearchSelect: (result: { zipCode: string; address1: string }) => void;
}

export function useAddressFields(initial?: Partial<AddressFields>): UseAddressFieldsResult {
  const [zipCode, setZipCode] = useState(initial?.zipCode ?? '');
  const [address1, setAddress1] = useState(initial?.address1 ?? '');

  function handleAddressSearchSelect({ zipCode: z, address1: a }: { zipCode: string; address1: string }) {
    setZipCode(z);
    setAddress1(a);
  }

  return { zipCode, address1, setZipCode, setAddress1, handleAddressSearchSelect };
}
