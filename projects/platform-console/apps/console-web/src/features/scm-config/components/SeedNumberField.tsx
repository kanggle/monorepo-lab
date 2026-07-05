export interface SeedNumberFieldProps {
  id: string;
  label: string;
  testid: string;
  value: string;
  error?: string;
  onChange: (v: string) => void;
}

/**
 * Shared numeric field for the scm seed-config forms (TASK-PC-FE-190 split).
 * `PolicyForm` (reorderPoint / safetyStock / reorderQty) and `SupplierMapForm`
 * (defaultOrderQty / leadTimeDays) previously defined a byte-identical local
 * `NumberField`; this is that field extracted once. Label + non-negative number
 * input + inline `role="alert"` error, `aria-invalid` / `aria-describedby`
 * wired. Markup + testids (`<testid>` + `<testid>-error`) preserved verbatim.
 */
export function SeedNumberField({
  id,
  label,
  testid,
  value,
  error,
  onChange,
}: SeedNumberFieldProps) {
  const errId = `${id}-err`;
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-foreground">
        {label}
      </label>
      <input
        id={id}
        type="number"
        inputMode="numeric"
        min={0}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        data-testid={testid}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errId : undefined}
        className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
      />
      {error && (
        <p
          id={errId}
          role="alert"
          data-testid={`${testid}-error`}
          className="mt-1 text-sm text-destructive"
        >
          {error}
        </p>
      )}
    </div>
  );
}
