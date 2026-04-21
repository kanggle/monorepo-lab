interface ProfileFormFieldProps {
  id: string;
  label: string;
  type: 'text' | 'tel' | 'url';
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  error?: string;
}

export function ProfileFormField({
  id,
  label,
  type,
  value,
  onChange,
  placeholder,
  error,
}: ProfileFormFieldProps) {
  return (
    <div className="form-group" style={{ marginBottom: 0 }}>
      <label htmlFor={id} className="label">{label}</label>
      <input
        id={id}
        type={type}
        className="input"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
      />
      {error && (
        <p
          role="alert"
          style={{
            color: 'var(--color-error)',
            fontSize: 'var(--font-size-xs)',
            margin: 'var(--space-1) 0 0',
          }}
        >
          {error}
        </p>
      )}
    </div>
  );
}
