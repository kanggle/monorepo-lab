import { useState, useCallback } from 'react';
import { getErrorMessage } from '@repo/types/guards';

export function useAsyncAction() {
  const [error, setError] = useState('');

  const execute = useCallback(async (fn: () => Promise<void>, fallbackMessage: string) => {
    setError('');
    try {
      await fn();
    } catch (err) {
      setError(getErrorMessage(err, fallbackMessage));
    }
  }, []);

  return { error, execute, clearError: () => setError('') };
}

/**
 * Combines useAsyncAction with isSubmitting state management.
 * Use this in form hooks that need: error, isSubmitting, and a runSubmit wrapper
 * that handles setIsSubmitting(true/false) around execute automatically.
 */
export function useSubmitAction() {
  const { error, execute } = useAsyncAction();
  const [isSubmitting, setIsSubmitting] = useState(false);

  const runSubmit = useCallback(async (fn: () => Promise<void>, fallbackMessage: string) => {
    setIsSubmitting(true);
    await execute(fn, fallbackMessage);
    setIsSubmitting(false);
  }, [execute]);

  return { error, isSubmitting, runSubmit };
}
