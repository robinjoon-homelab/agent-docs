import { useId, useLayoutEffect, useRef, useState } from 'react';
import type { KeyboardEvent } from 'react';
import type { MentionQuery } from '../../common/mentions';
import { filterMentionLogins, mentionAtCursor } from '../../common/mentions';
import { hasMod } from '../../hooks/useHotkey';

export function CommentEditor({
  initial,
  placeholder,
  submitLabel,
  mentionLogins,
  onSubmit,
  onCancel,
  autoFocus = false,
}: {
  initial: string;
  placeholder: string;
  submitLabel: string;
  mentionLogins: readonly string[];
  onSubmit: (body: string) => Promise<boolean>;
  onCancel?: () => void;
  autoFocus?: boolean;
}) {
  const [body, setBody] = useState(initial);
  const [busy, setBusy] = useState(false);
  const [mention, setMention] = useState<MentionQuery | null>(null);
  const [index, setIndex] = useState(0);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const nextCursor = useRef<number | null>(null);
  const menuId = useId();
  const candidates = mention === null ? [] : filterMentionLogins(mentionLogins, mention.query);
  const selectedIndex = Math.min(index, Math.max(0, candidates.length - 1));

  useLayoutEffect(() => {
    if (nextCursor.current === null) return;
    inputRef.current?.focus();
    inputRef.current?.setSelectionRange(nextCursor.current, nextCursor.current);
    nextCursor.current = null;
  }, [body]);

  const updateMention = (input: HTMLTextAreaElement): void => {
    setMention(mentionAtCursor(input.value, input.selectionStart, input.selectionEnd));
    setIndex(0);
  };
  const choose = (login: string): void => {
    if (mention === null) return;
    const inserted = `@${login} `;
    setBody(body.slice(0, mention.from) + inserted + body.slice(mention.to));
    nextCursor.current = mention.from + inserted.length;
    setMention(null);
  };
  const submit = async (): Promise<void> => {
    if (body.trim() === '' || busy) return;
    setBusy(true);
    const done = await onSubmit(body);
    setBusy(false);
    if (done) {
      setBody('');
      setMention(null);
    }
  };
  const keyDown = (event: KeyboardEvent<HTMLTextAreaElement>): void => {
    if (event.nativeEvent.isComposing) return;
    if (mention !== null && ['ArrowDown', 'ArrowUp', 'Enter', 'Tab', 'Escape'].includes(event.key)) {
      event.preventDefault();
      event.stopPropagation();
      if (event.key === 'Escape') setMention(null);
      else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        const count = candidates.length;
        if (count > 0) setIndex((selectedIndex + (event.key === 'ArrowDown' ? 1 : -1) + count) % count);
      } else {
        const login = candidates[selectedIndex];
        if (login !== undefined) choose(login);
      }
      return;
    }
    if (event.key === 'Enter' && hasMod(event)) {
      event.preventDefault();
      void submit();
    }
    if (event.key === 'Escape') onCancel?.();
  };

  return (
    <form
      className="comment-editor"
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <textarea
        ref={inputRef}
        className="textarea"
        value={body}
        placeholder={placeholder}
        autoFocus={autoFocus}
        rows={2}
        aria-autocomplete="list"
        aria-controls={mention === null ? undefined : menuId}
        aria-activedescendant={
          mention !== null && candidates.length > 0 ? `${menuId}-${selectedIndex}` : undefined
        }
        onChange={(event) => {
          setBody(event.target.value);
          updateMention(event.target);
        }}
        onSelect={(event) => updateMention(event.currentTarget)}
        onFocus={(event) => updateMention(event.currentTarget)}
        onBlur={() => setMention(null)}
        onKeyDown={keyDown}
      />
      {mention === null ? null : (
        <div id={menuId} className="comment-mentions" role="listbox" aria-label="멘션">
          {candidates.length === 0 ? (
            <div className="faint small">결과 없음</div>
          ) : (
            candidates.map((login, candidateIndex) => (
              <button
                id={`${menuId}-${candidateIndex}`}
                key={login}
                type="button"
                role="option"
                aria-selected={candidateIndex === selectedIndex}
                className="menu-item"
                onMouseDown={(event) => event.preventDefault()}
                onMouseEnter={() => setIndex(candidateIndex)}
                onClick={() => choose(login)}
              >
                @{login}
              </button>
            ))
          )}
        </div>
      )}
      <div className="comment-editor-actions">
        {onCancel === undefined ? null : (
          <button type="button" className="btn btn-small btn-ghost" onClick={onCancel}>
            취소
          </button>
        )}
        <button type="submit" className="btn btn-small btn-primary" disabled={busy || body.trim() === ''}>
          {submitLabel}
        </button>
      </div>
    </form>
  );
}
