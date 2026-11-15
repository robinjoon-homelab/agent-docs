import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import type { NavigationUseCases } from '../../../../application/navigation/navigationUseCases';
import { useLoad } from '../hooks/useLoad';
import { ErrorBox, Loading } from '../common/States';

export function useRecordDocumentView(navigation: NavigationUseCases, documentId: string): void {
  useEffect(() => {
    void navigation.recordView(documentId).catch(() => {
      /* Visit telemetry must not block reading. */
    });
  }, [navigation, documentId]);
}

export function DocumentNavigation({
  navigation,
  documentId,
}: {
  navigation: NavigationUseCases;
  documentId: string;
}) {
  const links = useLoad(`backlinks:${documentId}`, () => navigation.backlinks(documentId));
  const favorites = useLoad(`favorites:${documentId}`, () => navigation.isFavorite(documentId));
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const enabled = favorites.state === 'ready' && favorites.data;
  async function toggle(): Promise<void> {
    setBusy(true);
    try {
      await navigation.favorite(documentId, !enabled);
      favorites.reload();
      setError(null);
    } catch (failure) {
      setError(failure);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section aria-label="문서 탐색">
      <button
        type="button"
        className="btn"
        disabled={busy || favorites.state !== 'ready'}
        onClick={() => {
          void toggle();
        }}
      >
        {enabled ? '즐겨찾기 해제' : '즐겨찾기'}
      </button>
      {error !== null ? <ErrorBox error={error} /> : null}
      <h2>이 문서를 링크한 문서{links.state === 'ready' ? ` (${links.data.total})` : ''}</h2>
      {links.state === 'loading' ? <Loading /> : null}
      {links.state === 'error' ? <ErrorBox error={links.error} onRetry={links.reload} /> : null}
      {links.state === 'ready' ? (
        <ul>
          {links.data.documents.map((entry) => (
            <li key={entry.id}>
              <Link to={`/d/${encodeURIComponent(entry.id)}`}>
                {entry.spaceName} · {entry.title}
              </Link>
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}
