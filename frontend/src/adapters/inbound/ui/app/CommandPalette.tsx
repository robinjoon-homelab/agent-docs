import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import type { TitleHit } from '../../../../domain/search/search';
import { documentHref } from '../../../../domain/document/document';
import { useServices } from '../../../../config/ServicesContext';
import { errorMessage } from '../common/States';
import { redirectIfUnauthenticated } from '../hooks/useLoad';

function searchHref(query: string, spaceSlug: string | null): string {
  const params = new URLSearchParams({ q: query });
  if (spaceSlug !== null) params.set('space', spaceSlug);
  return `/search?${params.toString()}`;
}

/**
 * Cmd+K title quick jump across every space. The full-text search it hands off to starts in the
 * current space.
 */
export function CommandPalette({
  open,
  spaceSlug,
  onClose,
}: {
  open: boolean;
  spaceSlug: string | null;
  onClose: () => void;
}) {
  const services = useServices();
  const navigate = useNavigate();
  const ref = useRef<HTMLDialogElement>(null);
  const [query, setQuery] = useState('');
  const [hits, setHits] = useState<readonly TitleHit[]>([]);
  const [selected, setSelected] = useState(0);
  const [failure, setFailure] = useState<string | null>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (dialog === null) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  useEffect(() => {
    if (!open) return;
    let current = true;
    const timer = setTimeout(() => {
      services.search.titles(query).then(
        (result) => {
          if (!current) return;
          setHits(result.slice(0, 20));
          setSelected(0);
          setFailure(null);
        },
        (error: unknown) => {
          if (!current || redirectIfUnauthenticated(error)) return;
          setHits([]);
          setFailure(errorMessage(error));
        },
      );
    }, 120);
    return () => {
      current = false;
      clearTimeout(timer);
    };
  }, [open, query, services]);

  const close = (): void => {
    setQuery('');
    setHits([]);
    setFailure(null);
    onClose();
  };

  const go = (to: string): void => {
    close();
    void navigate(to);
  };

  const onKeyDown = (event: React.KeyboardEvent<HTMLInputElement>): void => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setSelected((value) => Math.min(value + 1, Math.max(hits.length - 1, 0)));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setSelected((value) => Math.max(value - 1, 0));
    } else if (event.key === 'Enter') {
      event.preventDefault();
      const hit = hits[selected];
      if (hit !== undefined) go(documentHref(hit.space.slug, hit.documentId));
      else if (query.trim() !== '') go(searchHref(query.trim(), spaceSlug));
    }
  };

  return (
    <dialog
      ref={ref}
      className="palette"
      aria-label="문서 바로 가기"
      onCancel={(event) => {
        event.preventDefault();
        close();
      }}
      onClick={(event) => {
        if (event.target === ref.current) close();
      }}
    >
      {open ? (
        <>
          <input
            className="palette-input"
            placeholder="문서 제목으로 이동…"
            value={query}
            autoFocus
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={onKeyDown}
            role="combobox"
            aria-expanded={hits.length > 0}
            aria-controls="palette-results"
          />
          <div className="palette-results" id="palette-results" role="listbox">
            {failure === null ? null : (
              <div className="error-text palette-error" role="alert">
                검색하지 못했습니다: {failure}
              </div>
            )}
            {hits.map((hit, index) => (
              <button
                key={hit.documentId}
                type="button"
                role="option"
                aria-selected={index === selected}
                className="menu-item"
                onMouseEnter={() => setSelected(index)}
                onClick={() => go(documentHref(hit.space.slug, hit.documentId))}
              >
                <span className="row-main">
                  <span className="row-title" style={{ display: 'block' }}>
                    {hit.title}
                  </span>
                  <span className="row-sub" style={{ display: 'block' }}>
                    {hit.space.name} · {hit.path}
                  </span>
                </span>
              </button>
            ))}
            {query.trim() === '' ? null : (
              <button
                type="button"
                className="menu-item"
                onClick={() => go(searchHref(query.trim(), spaceSlug))}
              >
                <span className="muted">본문·댓글에서 “{query.trim()}” 검색</span>
                <span className="hint">전체 검색</span>
              </button>
            )}
          </div>
        </>
      ) : null}
    </dialog>
  );
}
