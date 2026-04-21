'use client';

import { formStyles } from '@/shared/lib/form-styles';

interface Props {
  startDate: string;
  endDate: string;
  onStartDateChange: (value: string) => void;
  onEndDateChange: (value: string) => void;
  isEdit: boolean;
  dateError: boolean;
}

const today = () => new Date().toISOString().slice(0, 10);

const labelStyle = {
  display: 'block',
  marginBottom: '6px',
  fontSize: '0.8125rem',
  fontWeight: 600,
  color: '#374151',
} as const;

function openPicker(e: React.MouseEvent<HTMLInputElement>) {
  const input = e.target as HTMLInputElement;
  if (typeof input.showPicker === 'function') input.showPicker();
}

export function PromotionPeriodFields({
  startDate,
  endDate,
  onStartDateChange,
  onEndDateChange,
  isEdit,
  dateError,
}: Props) {
  return (
    <div style={{ maxWidth: '320px' }}>
      <div style={{ display: 'flex', alignItems: 'flex-end', gap: '8px' }}>
        <div style={{ flex: 1 }}>
          <label htmlFor="startDate" style={labelStyle}>
            시작일 *
          </label>
          <input
            id="startDate"
            type="date"
            value={startDate}
            onChange={(e) => onStartDateChange(e.target.value)}
            onClick={openPicker}
            min={isEdit ? undefined : today()}
            required
            style={formStyles.dateInput}
          />
        </div>
        <span
          style={{
            color: '#9ca3af',
            fontSize: '1rem',
            userSelect: 'none',
            flexShrink: 0,
            paddingBottom: '10px',
          }}
        >
          ~
        </span>
        <div style={{ flex: 1 }}>
          <label htmlFor="endDate" style={labelStyle}>
            종료일 *
          </label>
          <input
            id="endDate"
            type="date"
            value={endDate}
            onChange={(e) => onEndDateChange(e.target.value)}
            onClick={openPicker}
            min={startDate || (isEdit ? undefined : today())}
            required
            style={formStyles.dateInput}
          />
        </div>
      </div>
      {dateError && (
        <p style={{ color: '#dc2626', fontSize: '0.8125rem', marginTop: '8px' }}>
          종료일은 시작일 이후여야 합니다.
        </p>
      )}
    </div>
  );
}
