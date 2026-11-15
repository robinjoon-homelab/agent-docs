import { useState } from 'react';
import type { Space } from '../../../../../domain/space/space';
import { isValidPath, normalizePath } from '../../../../../domain/document/document';
import { Dialog } from '../../common/Dialog';
import { errorMessage } from '../../common/States';

export function TransferDialog({
  spaces,
  path,
  onTransfer,
  onClose,
}: {
  spaces: readonly Space[];
  path: string;
  onTransfer: (space: string, path: string) => Promise<void>;
  onClose: () => void;
}) {
  const [space, setSpace] = useState(spaces[0]?.slug ?? '');
  const [destination, setDestination] = useState(path);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const submit = async () => {
    setBusy(true);
    try {
      await onTransfer(space, normalizePath(destination));
      onClose();
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Dialog
      open
      title="다른 스페이스로 이사"
      onClose={busy ? () => undefined : onClose}
      footer={
        <button
          className="btn btn-primary"
          disabled={busy || space === '' || !isValidPath(normalizePath(destination))}
          onClick={() => void submit()}
        >
          이사
        </button>
      }
    >
      <label>
        목적 스페이스
        <select disabled={busy} value={space} onChange={(event) => setSpace(event.target.value)}>
          {spaces.map((entry) => (
            <option key={entry.id} value={entry.slug}>
              {entry.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        목적 경로
        <input disabled={busy} value={destination} onChange={(event) => setDestination(event.target.value)} />
      </label>
      {error === null ? null : <p role="alert">{error}</p>}
    </Dialog>
  );
}
