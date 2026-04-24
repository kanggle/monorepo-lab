/**
 * Shared form style objects reused across feature form components.
 * Extracted to reduce duplication per refactoring-policy.md (Reduce Duplication).
 */
export const formStyles = {
  error: { color: 'red', marginBottom: '16px' } as const,
  errorAlert: {
    padding: '12px 16px',
    backgroundColor: '#fef2f2',
    border: '1px solid #fecaca',
    borderRadius: '8px',
    color: '#dc2626',
    fontSize: '0.875rem',
  } as const,
  label: { display: 'block', marginBottom: '4px', fontWeight: 500 } as const,
  input: { width: '100%', padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: '6px' } as const,
  dateInput: {
    width: '100%',
    padding: '10px 14px',
    border: '1px solid #e5e7eb',
    borderRadius: '8px',
    fontSize: '0.875rem',
    color: '#111827',
    background: '#fff',
    outline: 'none',
    cursor: 'pointer',
  } as const,
  buttonRow: { display: 'flex', gap: '8px' } as const,
  cancelBtn: { padding: '10px 24px', borderRadius: '6px', border: '1px solid #d1d5db', backgroundColor: '#fff', cursor: 'pointer' } as const,
  submitBtn: { padding: '10px 24px', borderRadius: '6px', border: 'none', backgroundColor: '#1A1A2E', color: '#fff', cursor: 'pointer', opacity: 1, fontWeight: 600 } as const,
  submitBtnDisabled: { padding: '10px 24px', borderRadius: '6px', border: 'none', backgroundColor: '#1A1A2E', color: '#fff', cursor: 'not-allowed', opacity: 0.5, fontWeight: 600 } as const,
};
