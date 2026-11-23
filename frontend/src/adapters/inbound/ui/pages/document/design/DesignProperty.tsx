import { useState } from 'react';
export function DesignProperty({
  label,
  value,
  onCommit,
  type = 'text',
}: {
  label: string;
  value: string;
  onCommit: (v: string) => string | null;
  type?: 'text' | 'number' | 'long';
}) {
  const [draft, setDraft] = useState<{ base: string; value: string } | null>(null);
  const changed = draft !== null && draft.value !== value,
    stale = draft !== null && draft.base !== value;
  const props = {
    value: draft?.value ?? value,
    onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
      setDraft({ base: draft?.base ?? value, value: e.target.value }),
  };
  return (
    <form
      className="design-property"
      onSubmit={(e) => {
        e.preventDefault();
        if (changed && !stale) {
          const failure = onCommit(draft.value);
          if (!failure) setDraft(null);
        }
      }}
    >
      <label>
        <span>{label}</span>
        {type === 'long' ? (
          <textarea rows={3} {...props} />
        ) : (
          <input type={type} step={type === 'number' ? 'any' : undefined} {...props} />
        )}
      </label>
      {changed && !stale && (
        <button type="submit" className="btn btn-small">
          적용
        </button>
      )}
      {stale && (
        <button type="button" className="btn btn-small" onClick={() => setDraft(null)}>
          변경된 값 불러오기
        </button>
      )}
    </form>
  );
}
