import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import type { SearchHit, SearchScope } from '../../../../../domain/search/search';
import {
  HIT_KIND_LABEL,
  highlightSnippet,
  hitHref,
  normalizeQuery,
} from '../../../../../domain/search/search';
import { useServices } from '../../../../../config/ServicesContext';
import { useShell } from '../../app/ShellContext';
import { Icon } from '../../common/Icon';
import { EmptyState, ErrorBox, Loading } from '../../common/States';
import { useDebouncedCallback } from '../../hooks/useDebouncedCallback';
import { useLoad } from '../../hooks/useLoad';
import { usePageTitle } from '../../hooks/usePageTitle';
import './search.css';

function Snippet({ text, query }: { text: string; query: string }) {
  return (
    <span className="search-snippet">
      {highlightSnippet(text, query).map((part, index) =>
        part.match ? <mark key={index}>{part.text}</mark> : <span key={index}>{part.text}</span>,
      )}
    </span>
  );
}

function HitRow({ hit, query }: { hit: SearchHit; query: string }) {
  return (
    <li>
      <Link className="list-row list-row-link search-hit" to={hitHref(hit)}>
        <span className={`badge ${hit.kind === 'comment' ? 'badge-warning' : 'badge-neutral'}`}>
          {HIT_KIND_LABEL[hit.kind]}
        </span>
        <span className="row-main">
          <span className="row-title search-hit-title">
            <Snippet text={hit.title} query={query} />
          </span>
          <span className="row-sub">
            {hit.space.name} · {hit.path}
          </span>
          {hit.snippet === '' || hit.kind === 'title' ? null : <Snippet text={hit.snippet} query={query} />}
        </span>
      </Link>
    </li>
  );
}

export function SearchPage() {
  const services = useServices();
  const [params, setParams] = useSearchParams();
  const query = params.get('q') ?? '';
  const spaceSlug = params.get('space');
  const [input, setInput] = useState(query);
  const [syncedQuery, setSyncedQuery] = useState(query);
  // Keep the box in step when the URL changes from outside (palette, back button).
  if (syncedQuery !== query) {
    setSyncedQuery(query);
    setInput(query);
  }
  const spaces = useLoad('search-spaces', () => services.spaces.list());
  const scope: SearchScope = spaceSlug === null ? { kind: 'all' } : { kind: 'space', slug: spaceSlug };
  const normalized = normalizeQuery(query);
  const hits = useLoad(`search:${normalized}:${spaceSlug ?? '*'}`, () =>
    services.search.search(normalized, scope),
  );
  usePageTitle(normalized === '' ? '검색' : `“${normalized}” 검색`);

  const update = (next: { q?: string; space?: string | null }): void => {
    const merged = new URLSearchParams(params);
    if (next.q !== undefined) {
      if (next.q.trim() === '') merged.delete('q');
      else merged.set('q', next.q);
    }
    if (next.space !== undefined) {
      if (next.space === null) merged.delete('space');
      else merged.set('space', next.space);
    }
    setParams(merged, { replace: true });
  };
  const debouncedQuery = useDebouncedCallback((value: string) => update({ q: value }), 300);

  const spaceList = spaces.state === 'ready' ? spaces.data : [];
  // "This space" is the one the user came from, not simply the first one in the list.
  const currentSpace = useShell().currentSpaceSlug ?? spaceList[0]?.slug ?? null;

  return (
    <div className="page page-narrow">
      <div className="page-header">
        <h1>검색</h1>
      </div>
      <form
        className="search-form"
        role="search"
        onSubmit={(event) => {
          event.preventDefault();
          debouncedQuery.cancel();
          update({ q: input });
        }}
      >
        <span className="search-input-wrap">
          <Icon name="search" />
          <input
            className="input"
            type="search"
            value={input}
            autoFocus
            placeholder="제목, 본문, 댓글 검색"
            aria-label="검색어"
            onChange={(event) => {
              setInput(event.target.value);
              debouncedQuery.call(event.target.value);
            }}
          />
        </span>
        <div className="search-scope">
          <div className="segmented" role="group" aria-label="검색 범위">
            <button
              type="button"
              aria-pressed={spaceSlug !== null}
              disabled={spaceSlug === null && currentSpace === null}
              onClick={() => update({ space: spaceSlug ?? currentSpace })}
            >
              이 스페이스
            </button>
            <button type="button" aria-pressed={spaceSlug === null} onClick={() => update({ space: null })}>
              모든 스페이스
            </button>
          </div>
          {spaceSlug === null ? null : (
            <select
              className="select search-space-select"
              value={spaceSlug}
              aria-label="검색할 스페이스"
              onChange={(event) => update({ space: event.target.value })}
            >
              {spaceList.some((space) => space.slug === spaceSlug) ? null : (
                <option value={spaceSlug}>{spaceSlug}</option>
              )}
              {spaceList.map((space) => (
                <option key={space.id} value={space.slug}>
                  {space.name}
                </option>
              ))}
            </select>
          )}
        </div>
      </form>

      {normalized === '' ? (
        <p className="muted">검색어를 입력하세요. 문서 제목으로 바로 가려면 ⌘K를 누르세요.</p>
      ) : null}
      {normalized !== '' && hits.state === 'loading' ? <Loading /> : null}
      {hits.state === 'error' ? <ErrorBox error={hits.error} onRetry={hits.reload} /> : null}
      {normalized !== '' && hits.state === 'ready' && hits.data.length === 0 ? (
        <EmptyState title="검색 결과가 없습니다">
          <span className="small">다른 검색어를 쓰거나 범위를 모든 스페이스로 넓혀 보세요.</span>
        </EmptyState>
      ) : null}
      {normalized !== '' && hits.state === 'ready' && hits.data.length > 0 ? (
        <>
          <p className="faint small">결과 {hits.data.length}개</p>
          <ul className="list search-results">
            {hits.data.map((hit, index) => (
              <HitRow
                key={`${hit.documentId}:${hit.kind}:${hit.blockId ?? hit.threadId ?? index}`}
                hit={hit}
                query={normalized}
              />
            ))}
          </ul>
        </>
      ) : null}
    </div>
  );
}
