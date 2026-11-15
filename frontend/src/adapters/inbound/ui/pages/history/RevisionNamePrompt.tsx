import { useState } from 'react';
import { useServices } from '../../../../../config/ServicesContext';
import { errorMessage } from '../../common/States';
import './history.css';

/** Optional inline suggestion after a newly confirmed revision exists. */
export function RevisionNamePrompt({
  documentId,
  revision,
  onClose,
}: {
  documentId: string;
  revision: number;
  onClose: () => void;
}) {
  const services = useServices();
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const save = async (): Promise<void> => {
    setBusy(true);
    try {
      await services.documents.nameRevision(documentId, revision, name);
      onClose();
    } catch (failure) {
      setError(failure);
      setBusy(false);
    }
  };
  return (
    <aside className="history-metadata" aria-label="리비전 이름 제안">
      <p>확정한 rev {revision}에 이름을 붙여 두세요.</p>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void save();
        }}
      >
        <input
          className="input"
          aria-label="리비전 이름"
          maxLength={100}
          value={name}
          onChange={(event) => setName(event.target.value)}
        />
        <button type="submit" className="btn btn-small" disabled={busy}>
          이름 저장
        </button>
        <button type="button" className="btn btn-small btn-ghost" onClick={onClose}>
          나중에
        </button>
      </form>
      {error === null ? null : <p className="field-error">{errorMessage(error)}</p>}
    </aside>
  );
}
