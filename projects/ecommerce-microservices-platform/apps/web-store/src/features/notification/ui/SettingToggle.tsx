interface ToggleProps {
  label: string;
  description: string;
  checked: boolean;
  disabled: boolean;
  onChange: (checked: boolean) => void;
}

export function SettingToggle({ label, description, checked, disabled, onChange }: ToggleProps) {
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: 'var(--space-4) 0',
        borderBottom: '1px solid var(--color-border-light)',
      }}
    >
      <div>
        <p style={{ margin: 0, fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-semibold)' }}>
          {label}
        </p>
        <p style={{ margin: 'var(--space-1) 0 0', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
          {description}
        </p>
      </div>
      <label style={{ position: 'relative', display: 'inline-block', width: '44px', height: '24px', flexShrink: 0, marginLeft: 'var(--space-4)' }}>
        <input
          type="checkbox"
          role="switch"
          checked={checked}
          disabled={disabled}
          onChange={(e) => onChange(e.target.checked)}
          aria-label={label}
          style={{ opacity: 0, width: 0, height: 0, position: 'absolute' }}
        />
        <span
          style={{
            position: 'absolute',
            cursor: disabled ? 'not-allowed' : 'pointer',
            top: 0, left: 0, right: 0, bottom: 0,
            backgroundColor: checked ? 'var(--color-primary)' : 'var(--color-text-muted)',
            borderRadius: '12px',
            transition: 'background-color var(--transition-fast)',
          }}
        >
          <span
            style={{
              position: 'absolute',
              height: '18px',
              width: '18px',
              left: checked ? '23px' : '3px',
              bottom: '3px',
              backgroundColor: 'var(--color-white)',
              borderRadius: '50%',
              transition: 'left var(--transition-fast)',
            }}
          />
        </span>
      </label>
    </div>
  );
}
