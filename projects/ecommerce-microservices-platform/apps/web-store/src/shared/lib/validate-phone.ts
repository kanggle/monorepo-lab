const PHONE_REGEX = /^01[016789]-?\d{3,4}-?\d{4}$/;

export function isValidPhone(phone: string): boolean {
  return PHONE_REGEX.test(phone.trim());
}
