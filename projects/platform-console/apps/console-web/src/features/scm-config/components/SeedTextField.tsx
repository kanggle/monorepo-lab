/**
 * A `<datalist>` option. A plain string renders/inserts itself (the original
 * shape — e.g. a currency code where the value IS the label). An object lets
 * the inserted VALUE differ from the displayed LABEL (TASK-PC-FE-337 —
 * `SupplierMapForm`'s supplier suggestions: the label shows "code · name ·
 * status" but only the **code** is ever inserted into the field, since the
 * mapping's `supplierId` must be sent as the supplier's business code).
 */
export type SeedTextFieldOption = string | { value: string; label: string };

export interface SeedTextFieldProps {
  id: string;
  label: string;
  testid: string;
  value: string;
  error?: string;
  onChange: (v: string) => void;
  /** Optional `<datalist>` autocomplete options (e.g. currency codes, or
   *  `{value, label}` pairs — see {@link SeedTextFieldOption}). */
  options?: readonly SeedTextFieldOption[];
  /** Optional max input length (e.g. 3 for an ISO-4217 currency code). */
  maxLength?: number;
  /** Uppercase the value before `onChange` + render the input uppercased
   *  (e.g. a currency code). */
  uppercase?: boolean;
  /** Optional `onFocus` passthrough (TASK-PC-FE-337 — the supplier-code
   *  field triggers its lazy, at-most-once suggestions fetch here). */
  onFocus?: () => void;
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
  onFocus,
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
        onFocus={onFocus}
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
          {options.map((o) => {
            const optValue = typeof o === 'string' ? o : o.value;
            const optLabel = typeof o === 'string' ? o : o.label;
            // `value` is what the browser inserts on pick; the child text
            // node is the suggestion's displayed label (reliably rendered by
            // every major browser's <datalist>, unlike the `label` attribute
            // alone). When value === label (e.g. a currency code) this is
            // the original plain-string rendering, byte-identical.
            return <option key={optValue} value={optValue}>{optLabel}</option>;
          })}
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
