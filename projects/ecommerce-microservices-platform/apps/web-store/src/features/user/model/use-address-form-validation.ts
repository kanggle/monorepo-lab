import { useState, useCallback } from 'react';
import type { AddressFieldErrors } from './types';
import { isValidPhone } from '@/shared/lib/validate-phone';

function validateFields(
  label: string,
  recipientName: string,
  phone: string,
  zipCode: string,
  address1: string,
): AddressFieldErrors {
  const errors: AddressFieldErrors = {};

  if (!label.trim()) {
    errors.label = '배송지명을 입력해주세요.';
  }
  if (!recipientName.trim()) {
    errors.recipientName = '수령인을 입력해주세요.';
  }
  if (!phone.trim()) {
    errors.phone = '연락처를 입력해주세요.';
  } else if (!isValidPhone(phone)) {
    errors.phone = '올바른 휴대폰 번호를 입력해주세요. (예: 010-1234-5678)';
  }
  if (!zipCode.trim()) {
    errors.zipCode = '우편번호를 입력해주세요.';
  }
  if (!address1.trim()) {
    errors.address1 = '주소를 입력해주세요.';
  }

  return errors;
}

export function useAddressFormValidation() {
  const [fieldErrors, setFieldErrors] = useState<AddressFieldErrors>({});

  const validate = useCallback(
    (label: string, recipientName: string, phone: string, zipCode: string, address1: string): boolean => {
      const errors = validateFields(label, recipientName, phone, zipCode, address1);
      setFieldErrors(errors);
      return Object.keys(errors).length === 0;
    },
    [],
  );

  const clearFieldError = useCallback((field: keyof AddressFieldErrors) => {
    setFieldErrors((prev) => ({ ...prev, [field]: undefined }));
  }, []);

  return { fieldErrors, validate, clearFieldError };
}
