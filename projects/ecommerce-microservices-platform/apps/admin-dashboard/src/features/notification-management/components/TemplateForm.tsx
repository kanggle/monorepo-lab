'use client';

import { useRouter } from 'next/navigation';
import type {
  NotificationTemplateType,
  NotificationChannel,
} from '@repo/types';
import { useTemplateForm } from '../hooks/use-template-form';
import { Section } from '@/shared/ui';
import { formStyles } from '@/shared/lib/form-styles';
import type { TemplateEditData } from '../types';

interface Props {
  template?: TemplateEditData;
}

const TYPE_OPTIONS: { value: NotificationTemplateType; label: string }[] = [
  { value: 'ORDER_PLACED', label: '주문 완료' },
  { value: 'PAYMENT_COMPLETED', label: '결제 완료' },
  { value: 'SHIPPING_STATUS_CHANGED', label: '배송 상태 변경' },
  { value: 'WELCOME', label: '회원 가입' },
];

const CHANNEL_OPTIONS: { value: NotificationChannel; label: string }[] = [
  { value: 'EMAIL', label: '이메일' },
  { value: 'SMS', label: 'SMS' },
  { value: 'PUSH', label: '푸시' },
];

const styles = {
  ...formStyles,
  fieldGrid: {
    display: 'grid',
    gap: '12px',
    maxWidth: '600px',
  } as const,
  select: {
    width: '100%',
    padding: '8px 12px',
    border: '1px solid #d1d5db',
    borderRadius: '6px',
    backgroundColor: '#fff',
  } as const,
  selectDisabled: {
    width: '100%',
    padding: '8px 12px',
    border: '1px solid #d1d5db',
    borderRadius: '6px',
    backgroundColor: '#f3f4f6',
  } as const,
  textarea: {
    width: '100%',
    padding: '8px 12px',
    border: '1px solid #d1d5db',
    borderRadius: '6px',
    resize: 'vertical' as const,
    fontFamily: 'monospace',
    fontSize: '0.875rem',
    lineHeight: '1.5',
  } as const,
  helperText: {
    fontSize: '0.75rem',
    color: '#6b7280',
    marginTop: '4px',
  } as const,
};

export function TemplateForm({ template }: Props) {
  const router = useRouter();
  const {
    type,
    setType,
    channel,
    setChannel,
    subject,
    setSubject,
    body,
    setBody,
    error,
    isSubmitting,
    isEdit,
    isValid,
    handleSubmit,
  } = useTemplateForm(template);

  return (
    <form onSubmit={handleSubmit}>
      {error && (
        <p role="alert" style={styles.error}>
          {error}
        </p>
      )}

      <Section title="템플릿 정보">
        <div style={styles.fieldGrid}>
          <div>
            <label htmlFor="type" style={styles.label}>
              유형 *
            </label>
            <select
              id="type"
              value={type}
              onChange={(e) =>
                setType(e.target.value as NotificationTemplateType)
              }
              disabled={isEdit}
              style={isEdit ? styles.selectDisabled : styles.select}
            >
              {TYPE_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="channel" style={styles.label}>
              채널 *
            </label>
            <select
              id="channel"
              value={channel}
              onChange={(e) =>
                setChannel(e.target.value as NotificationChannel)
              }
              disabled={isEdit}
              style={isEdit ? styles.selectDisabled : styles.select}
            >
              {CHANNEL_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="subject" style={styles.label}>
              제목 *
            </label>
            <input
              id="subject"
              type="text"
              value={subject}
              onChange={(e) => setSubject(e.target.value)}
              required
              style={styles.input}
            />
            <p style={styles.helperText}>
              {'{{variable}}'} 형식의 플레이스홀더를 사용할 수 있습니다. 예:{' '}
              {'{{userName}}'}
            </p>
          </div>
          <div>
            <label htmlFor="body" style={styles.label}>
              본문 *
            </label>
            <textarea
              id="body"
              value={body}
              onChange={(e) => setBody(e.target.value)}
              rows={8}
              required
              style={styles.textarea}
            />
            <p style={styles.helperText}>
              사용 가능한 변수: {'{{orderNumber}}'}, {'{{userName}}'},{' '}
              {'{{trackingNumber}}'} 등
            </p>
          </div>
        </div>
      </Section>

      <div style={styles.buttonRow}>
        <button
          type="submit"
          disabled={!isValid || isSubmitting}
          style={isValid ? styles.submitBtn : styles.submitBtnDisabled}
        >
          {isSubmitting ? '저장 중...' : isEdit ? '수정' : '등록'}
        </button>
        <button
          type="button"
          onClick={() => router.back()}
          style={styles.cancelBtn}
        >
          취소
        </button>
      </div>
    </form>
  );
}
