import { getErrorMessage } from '@repo/types/guards';

export function alertError(error: unknown, fallbackMessage: string): void {
  window.alert(getErrorMessage(error, fallbackMessage));
}
