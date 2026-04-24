export function maskPhone(phone: string): string {
  const digitsOnly = phone.replace(/[^0-9]/g, '');

  if (digitsOnly.length === 11) {
    return `${digitsOnly.slice(0, 3)}-****-${digitsOnly.slice(7)}`;
  }

  if (digitsOnly.length === 10) {
    return `${digitsOnly.slice(0, 3)}-***-${digitsOnly.slice(6)}`;
  }

  if (digitsOnly.length >= 8) {
    const midStart = Math.floor(digitsOnly.length / 2) - 2;
    const midEnd = midStart + 4;
    return digitsOnly.slice(0, midStart) + '-****-' + digitsOnly.slice(midEnd);
  }

  return phone;
}
