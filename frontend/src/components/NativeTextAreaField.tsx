import { useState } from 'react';
import { FormControl } from '@alfalab/core-components-form-control';

export function NativeTextAreaField({ id, label, value, onChange, disabled, error }: {
  id: string; label: string; value: string; onChange: (value: string) => void; disabled?: boolean; error?: string;
}) {
  const [focused, setFocused] = useState(false);
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;
  return <FormControl block size={56} className="native-textarea-field"
    label={<label htmlFor={id}>{label}</label>} labelView="outer"
    filled={value !== ''} focused={focused} disabled={disabled}
    hint={<span id={hintId}>От 3 до 1 000 символов. Только комментарий к синтетическому сценарию.</span>}
    error={error ? <span id={errorId}>{error}</span> : undefined}>
    <textarea id={id} className="native-textarea-input" rows={4} value={value} required maxLength={1000}
      onChange={(event) => onChange(event.currentTarget.value)}
      onFocus={() => setFocused(true)} onBlur={() => setFocused(false)}
      disabled={disabled} aria-invalid={Boolean(error)} aria-describedby={error ? errorId : hintId} />
  </FormControl>;
}
