import { useState } from 'react';
import type { Revision } from '../../../../../domain/document/document';
import { codeLinks } from '../../../../../domain/document/historyMetadata';
import { useServices } from '../../../../../config/ServicesContext';
import { useSession } from '../../app/SessionContext';
import { errorMessage } from '../../common/States';

export function RevisionLinks({ revision }: { revision: Revision }) {
  return (
    <div className="history-code-links">
      {revision.codeReferences?.flatMap(codeLinks).map((link) => (
        <a key={link.href} href={link.href} target="_blank" rel="noreferrer">
          {link.label}
        </a>
      ))}
    </div>
  );
}

export function RevisionDetails({
  documentId,
  revision,
  onChanged,
}: {
  documentId: string;
  revision: Revision;
  onChanged: () => void;
}) {
  const services = useServices();
  const { me } = useSession();
  const [name, setName] = useState(revision.name ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const save = async (): Promise<void> => {
    setBusy(true);
    setError(null);
    try {
      await services.documents.nameRevision(documentId, revision.revision, name);
      onChanged();
    } catch (failure) {
      setError(failure);
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="history-metadata">
      {me.owner ? (
        <form
          onSubmit={(event) => {
            event.preventDefault();
            void save();
          }}
        >
          <label>
            리비전 이름
            <input
              className="input"
              aria-label="리비전 이름"
              maxLength={100}
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          </label>
          <button type="submit" className="btn btn-small" disabled={busy}>
            이름 저장
          </button>
          <span className="muted small">비우면 이름이 제거됩니다.</span>
        </form>
      ) : (
        <strong>{revision.name}</strong>
      )}
      <RevisionLinks revision={revision} />
      {revision.importProvenance == null ? null : (
        <p className="muted small">
          가져온 곳: {revision.importProvenance.tool} · {revision.importProvenance.originalPath}
        </p>
      )}
      {error === null ? null : <p className="field-error">{errorMessage(error)}</p>}
    </div>
  );
}
