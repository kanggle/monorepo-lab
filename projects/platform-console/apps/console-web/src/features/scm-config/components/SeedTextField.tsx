export interface SeedTextFieldProps {
  id: string;
  label: string;
  testid: string;
  value: string;
  error?: string;
  onChange: (v: string) => void;
  /** Optional `<datalist>` autocomplete options (e.g. currency codes). */
  options?: readonly string[];
  /** Optional max input length (e.g. 3 for an ISO-4217 currency code). */
  maxLength?: number;
  /** Uppercase the value before `onChange` + render the input uppercased
   *  (e.g. a currency code). */
  uppercase?: boolean;
}

/**
 * Shared text field for the scm seed-config forms (TASK-PC-FE-191) — the text
 * sibling of `SeedNumberField` (FE-190). `SupplierMapForm` used inline
 * label+input+error blocks for both `supplierId` (plain) and `currency` (a
 * datalist-backed, uppercased, length-3 code field); this is that block
 * extracted once. Controlled — the parent owns the value/state. Markup + testids
 * (`<testid>` + `<testid>-error`) + aria wiring preserved verbatim.
 *
 * `uppercase` moves the currency transform here: `onChange` receives the
 * already-uppercased value (identical to the pre-extraction
 * `e.target.value.toUpperCase()`), and the input renders uppercased.
 */
export function SeedTextField({
  id,
  label,
  testid,
  value,
  error,
  onChange,
  options,
  maxLength,
  uppercase,
}: SeedTextFieldProps) {
  const errId = `${id}-err`;
  const optionsId = `${id}-options`;
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-foreground">
        {label}
      </label>
      <input
        id={id}
        type="text"
        list={options ? optionsId : undefined}
        maxLength={maxLength}
        value={value}
        onChange={(e) =>
          onChange(uppercase ? e.target.value.toUpperCase() : e.target.value)
        }
        data-testid={testid}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errId : undefined}
        className={
          uppercase
            ? 'mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm uppercase text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary'
            : 'mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary'
        }
      />
      {options && (
        <datalist id={optionsId}>
          {options.map((o) => (
            <option key={o} value={o} />
          ))}
        </datalist>
      )}
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
