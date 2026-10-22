import { useState } from 'react';
import { FormControl } from '@alfalab/core-components-form-control';

type NativeSelectFieldProps = {
  id: string;
  name?: string;
  label: string;
  value: string;
  options: ReadonlyArray<{ key: string; content: string }>;
  onChange: (value: string) => void;
  size?: 48 | 56;
  disabled?: boolean;
  required?: boolean;
  error?: string;
};

/**
 * Core's public FormControl supplies the responsive field, label and error UI.
 * A native select supplies accessible naming and keyboard/OS selection. The
 * pinned Core Select has ARIA defects recorded in VERIFICATION.md; this adapter
 * does not patch its private DOM or suppress accessibility checks.
 */
export function NativeSelectField({ id, name, label, value, options, onChange, size = 48, disabled, required, error }: NativeSelectFieldProps) {
  const [focused, setFocused] = useState(false);
  const errorId = `${id}-error`;

  return <FormControl block size={size} className={`native-select-field native-select-field-${size}`}
    label={<label htmlFor={id}>{label}</label>} labelView="outer"
    filled={value !== ''} focused={focused} disabled={disabled}
    error={error ? <span id={errorId}>{error}</span> : undefined}>
    <select id={id} name={name} className="native-select-input" value={value}
      onChange={(event) => onChange(event.currentTarget.value)}
      onFocus={() => setFocused(true)} onBlur={() => setFocused(false)}
      disabled={disabled} required={required} aria-invalid={Boolean(error)}
      aria-describedby={error ? errorId : undefined}>
      {options.map((option) => <option key={option.key} value={option.key}>{option.content}</option>)}
    </select>
  </FormControl>;
}
