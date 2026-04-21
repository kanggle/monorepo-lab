interface PhoneFieldErrorProps {
  phone: string;
  isValid: boolean;
}

export function PhoneFieldError({ phone, isValid }: PhoneFieldErrorProps) {
  if (phone.trim().length === 0 || isValid) return null;
  return (
    <p style={{ color: 'var(--color-error)', fontSize: 'var(--font-size-xs)', margin: 'var(--space-1) 0 0' }}>
      올바른 휴대폰 번호를 입력해주세요. (예: 010-1234-5678)
    </p>
  );
}
