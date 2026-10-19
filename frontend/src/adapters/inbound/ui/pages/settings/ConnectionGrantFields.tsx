import type { ConnectionScope } from '../../../../../domain/connection/connection';
import { CONNECTION_SCOPES, SCOPE_LABEL } from '../../../../../domain/connection/connection';
import type { Space } from '../../../../../domain/space/space';
import { pickableSpaces } from '../../../../../domain/space/space';

/** The spaces a connection may use. An archived space shows only while chosen, so it can be unchosen. */
export function SpaceCheckboxes({
  spaces,
  selected,
  onChange,
}: {
  spaces: readonly Space[];
  selected: readonly string[];
  onChange: (next: string[]) => void;
}) {
  const shown = pickableSpaces(spaces, selected);
  if (shown.length === 0) return <p className="faint small">스페이스가 없습니다.</p>;
  return (
    <div className="space-checkboxes">
      {shown.map((space) => (
        <label key={space.id} className="checkbox-row">
          <input
            type="checkbox"
            checked={selected.includes(space.id)}
            onChange={(event) =>
              onChange(
                event.target.checked ? [...selected, space.id] : selected.filter((id) => id !== space.id),
              )
            }
          />
          {space.name}
          <span className="faint small">/{space.slug}</span>
        </label>
      ))}
    </div>
  );
}

export function ScopePicker({
  value,
  onChange,
}: {
  value: ConnectionScope;
  onChange: (scope: ConnectionScope) => void;
}) {
  return (
    <div className="segmented" role="group" aria-label="권한">
      {CONNECTION_SCOPES.map((scope) => (
        <button key={scope} type="button" aria-pressed={value === scope} onClick={() => onChange(scope)}>
          {SCOPE_LABEL[scope]}
        </button>
      ))}
    </div>
  );
}
