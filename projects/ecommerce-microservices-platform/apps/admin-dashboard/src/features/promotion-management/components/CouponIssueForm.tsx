'use client';

import { useState } from 'react';
import { useIssueCoupons } from '../hooks/use-issue-coupons';
import { getErrorMessage } from '@repo/types/guards';
import { formStyles } from '@/shared/lib/form-styles';

interface Props {
  promotionId: string;
}

const styles = {
  ...formStyles,
  container: { maxWidth: '480px' } as const,
  textarea: { width: '100%', padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: '6px', resize: 'vertical', fontFamily: 'monospace' } as const,
  hint: { fontSize: '0.75rem', color: '#6b7280', marginTop: '4px', marginBottom: '12px' } as const,
  buttonRow: { display: 'flex', gap: '8px', alignItems: 'center' } as const,
  submitBtn: { padding: '8px 20px', borderRadius: '6px', border: 'none', backgroundColor: '#1A1A2E', color: '#fff', cursor: 'pointer', fontWeight: 600, fontSize: '0.875rem' } as const,
  submitBtnDisabled: { padding: '8px 20px', borderRadius: '6px', border: 'none', backgroundColor: '#1A1A2E', color: '#fff', cursor: 'not-allowed', opacity: 0.5, fontWeight: 600, fontSize: '0.875rem' } as const,
  success: { color: '#059669', fontSize: '0.875rem' } as const,
  error: { color: '#ef4444', fontSize: '0.875rem', marginTop: '8px' } as const,
};

export function CouponIssueForm({ promotionId }: Props) {
  const [userIdsInput, setUserIdsInput] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const issueCoupons = useIssueCoupons(promotionId);

  const userIds = userIdsInput
    .split(/[\n,]/)
    .map((id) => id.trim())
    .filter((id) => id.length > 0);

  const isValid = userIds.length > 0;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!isValid) return;

    setSuccessMessage('');
    setErrorMessage('');

    try {
      const result = await issueCoupons.mutateAsync({ userIds });
      setSuccessMessage(`${result.issuedCount}건의 쿠폰이 발급되었습니다.`);
      setUserIdsInput('');
    } catch (err) {
      setErrorMessage(getErrorMessage(err, '쿠폰 발급에 실패했습니다.'));
    }
  }

  return (
    <form onSubmit={handleSubmit} style={styles.container}>
      <div>
        <label htmlFor="userIds" style={styles.label}>대상 사용자 ID</label>
        <textarea
          id="userIds"
          value={userIdsInput}
          onChange={(e) => setUserIdsInput(e.target.value)}
          rows={4}
          placeholder="사용자 ID를 입력하세요 (줄바꿈 또는 쉼표로 구분)"
          style={styles.textarea}
        />
        <p style={styles.hint}>줄바꿈 또는 쉼표로 구분하여 여러 사용자를 입력할 수 있습니다.</p>
      </div>
      <div style={styles.buttonRow}>
        <button
          type="submit"
          disabled={!isValid || issueCoupons.isPending}
          style={isValid && !issueCoupons.isPending ? styles.submitBtn : styles.submitBtnDisabled}
        >
          {issueCoupons.isPending ? '발급 중...' : `쿠폰 발급 (${userIds.length}명)`}
        </button>
        {successMessage && <span style={styles.success}>{successMessage}</span>}
      </div>
      {errorMessage && <p style={styles.error}>{errorMessage}</p>}
    </form>
  );
}
