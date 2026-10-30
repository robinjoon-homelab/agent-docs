import { Fragment, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import type { EditorSessionState } from '../../../../../application/document/editorSession';
import type { MetaPatch } from '../../../../../application/document/ports/DocumentGateway';
import type { AgentDocument } from '../../../../../domain/document/document';
import { documentHref, historyHref, parseTags } from '../../../../../domain/document/document';
import type { Crumb } from '../../../../../domain/document/pathTree';
import { CopyButton } from '../../common/CopyButton';
import { Icon } from '../../common/Icon';
import { StatusSelect } from '../../common/StatusBadge';
import { useDebouncedCallback } from '../../hooks/useDebouncedCallback';
import { PathDialog } from './PathDialog';

const SAVE_LABEL: Record<EditorSessionState['status'], string> = {
  saved: '저장됨',
  pending: '저장 대기',
  saving: '저장 중…',
  conflict: '충돌',
  error: '저장 실패',
};

function MetaInput({
  label,
  value,
  placeholder,
  disabled,
  onCommit,
}: {
  label: string;
  value: string;
  placeholder: string;
  disabled: boolean;
  onCommit: (value: string) => void;
}) {
  const [draft, setDraft] = useState(value);
  const [source, setSource] = useState(value);
  if (source !== value) {
    setSource(value);
    setDraft(value);
  }
  return (
    <label className="meta-field">
      <span className="meta-label">{label}</span>
      <input
        className="meta-input"
        value={draft}
        placeholder={placeholder}
        disabled={disabled}
        onChange={(event) => setDraft(event.target.value)}
        onBlur={() => {
          if (draft !== value) onCommit(draft);
        }}
        onKeyDown={(event) => {
          if (event.key === 'Enter') (event.target as HTMLInputElement).blur();
        }}
      />
    </label>
  );
}

/** `crumbs` are the documents above this one, top first, as the breadcrumb shows them. */
export function DocumentHeader({
  document,
  crumbs,
  state,
  editable,
  onPatch,
  onTrash,
  onRetrySave,
}: {
  document: AgentDocument;
  crumbs: readonly Crumb[];
  state: EditorSessionState;
  editable: boolean;
  onPatch: (patch: MetaPatch) => void;
  onTrash: () => void;
  onRetrySave: () => void;
}) {
  const { meta } = state;
  const [title, setTitle] = useState(meta.title);
  const [seenTitle, setSeenTitle] = useState(meta.title);
  const [sentTitle, setSentTitle] = useState<string | null>(null);
  // A saved title that is not the one this input sent (a rollback after a refused save) replaces the text.
  if (meta.title !== seenTitle) {
    setSeenTitle(meta.title);
    if (meta.title !== sentTitle) setTitle(meta.title);
  }
  const [pathOpen, setPathOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const saveTitle = useDebouncedCallback((value: string) => {
    if (value.trim() === '' || value.trim() === meta.title) return;
    setSentTitle(value.trim());
    onPatch({ title: value.trim() });
  }, 800);
  const url = `${window.location.origin}${documentHref(document.space.slug, document.id)}`;

  useEffect(() => {
    if (!menuOpen) return;
    const close = (event: MouseEvent): void => {
      if (menuRef.current !== null && !menuRef.current.contains(event.target as Node)) setMenuOpen(false);
    };
    window.addEventListener('mousedown', close);
    return () => window.removeEventListener('mousedown', close);
  }, [menuOpen]);

  return (
    <header className="doc-header">
      <div className="doc-topbar">
        <nav className="doc-breadcrumb" aria-label="위치">
          <Link to={`/s/${document.space.slug}`}>{document.space.name}</Link>
          <span className="faint">·</span>
          {crumbs.map((crumb) => (
            <Fragment key={crumb.path}>
              {crumb.id === null ? (
                <span className="muted">{crumb.title}</span>
              ) : (
                <Link to={documentHref(document.space.slug, crumb.id)} title={crumb.path}>
                  {crumb.title}
                </Link>
              )}
              <span className="faint">/</span>
            </Fragment>
          ))}
          {editable ? (
            <button
              type="button"
              className="link-btn mono"
              onClick={() => setPathOpen(true)}
              title={`${meta.path} · 경로 바꾸기`}
            >
              {meta.path.slice(meta.path.lastIndexOf('/') + 1)}
            </button>
          ) : (
            <span className="mono muted" title={meta.path}>
              {meta.path.slice(meta.path.lastIndexOf('/') + 1)}
            </span>
          )}
        </nav>
        <div className="doc-topbar-actions">
          <StatusSelect value={meta.status} disabled={!editable} onChange={(status) => onPatch({ status })} />
          <span className={`save-state save-${state.status}`} title={state.errorMessage ?? undefined}>
            rev {state.revision} · {SAVE_LABEL[state.status]}
          </span>
          {state.status === 'error' ? (
            <button type="button" className="btn btn-small" onClick={onRetrySave}>
              다시 저장
            </button>
          ) : null}
          <Link className="btn btn-small btn-ghost" to={historyHref(document.space.slug, document.id)}>
            <Icon name="history" size={14} />
            기록
          </Link>
          <CopyButton
            text={url}
            label="링크 복사"
            done="문서 링크를 복사했습니다"
            className="btn btn-small btn-ghost"
          />
          {editable ? (
            <div ref={menuRef} style={{ position: 'relative' }}>
              <button
                type="button"
                className="icon-btn"
                aria-label="문서 메뉴"
                aria-expanded={menuOpen}
                onClick={() => setMenuOpen((value) => !value)}
              >
                <Icon name="more" />
              </button>
              {menuOpen ? (
                <div className="popover" role="menu" style={{ right: 0, top: 34 }}>
                  <button
                    type="button"
                    role="menuitem"
                    className="menu-item"
                    onClick={() => {
                      setMenuOpen(false);
                      setPathOpen(true);
                    }}
                  >
                    경로 바꾸기
                  </button>
                  <button
                    type="button"
                    role="menuitem"
                    className="menu-item danger"
                    onClick={() => {
                      setMenuOpen(false);
                      onTrash();
                    }}
                  >
                    <Icon name="trash" size={14} />
                    휴지통으로 이동
                  </button>
                </div>
              ) : null}
            </div>
          ) : null}
        </div>
      </div>
      {state.errorMessage !== null && state.status !== 'error' ? (
        <p className="error-text small">{state.errorMessage}</p>
      ) : null}
      <input
        className="doc-title"
        value={title}
        placeholder="제목 없음"
        aria-label="제목"
        readOnly={!editable}
        onChange={(event) => {
          setTitle(event.target.value);
          saveTitle.call(event.target.value);
        }}
        onBlur={() => saveTitle.flush()}
      />
      <div className="doc-meta">
        <MetaInput
          label="태그"
          value={meta.tags.join(', ')}
          placeholder="쉼표로 구분"
          disabled={!editable}
          onCommit={(value) => onPatch({ tags: parseTags(value) })}
        />
        <MetaInput
          label="담당"
          value={meta.assignee ?? ''}
          placeholder="GitHub 로그인"
          disabled={!editable}
          onCommit={(value) =>
            onPatch({
              assignee: value.trim().replace(/^@/, '') === '' ? null : value.trim().replace(/^@/, ''),
            })
          }
        />
      </div>
      {pathOpen ? (
        <PathDialog
          path={meta.path}
          onClose={() => setPathOpen(false)}
          onSave={(path) => onPatch({ path })}
        />
      ) : null}
    </header>
  );
}
