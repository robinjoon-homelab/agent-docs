import { useState } from 'react';
import { useNavigate } from 'react-router';
import { NEW_DOCUMENT_ERROR_TEXT } from '../../../../application/document/documentUseCases';
import { documentHref } from '../../../../domain/document/document';
import { useServices } from '../../../../config/ServicesContext';
import { Dialog } from '../common/Dialog';
import { errorMessage } from '../common/States';

export function NewDocumentDialog({
  spaceSlug,
  initialPath,
  onClose,
  onCreated,
}: {
  spaceSlug: string;
  initialPath: string;
  onClose: () => void;
  onCreated: () => void;
}) {
  const services = useServices();
  const navigate = useNavigate();
  const [path, setPath] = useState(initialPath);
  const [title, setTitle] = useState('');
  const [errors, setErrors] = useState<readonly string[]>([]);
  const [busy, setBusy] = useState(false);

  const submit = async (): Promise<void> => {
    setBusy(true);
    try {
      const result = await services.documents.create(spaceSlug, path, title);
      if (!result.ok) {
        setErrors(result.error.map((error) => NEW_DOCUMENT_ERROR_TEXT[error]));
        return;
      }
      onCreated();
      onClose();
      void navigate(documentHref(result.value.space.slug, result.value.id));
    } catch (error) {
      setErrors([errorMessage(error)]);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog
      open
      title="새 문서"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            취소
          </button>
          <button type="submit" form="new-document" className="btn btn-primary" disabled={busy}>
            만들기
          </button>
        </>
      }
    >
      <form
        id="new-document"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        <div className="field">
          <label htmlFor="new-document-title">제목</label>
          <input
            id="new-document-title"
            className="input"
            value={title}
            autoFocus
            onChange={(event) => setTitle(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="new-document-path">경로</label>
          <input
            id="new-document-path"
            className="input mono"
            value={path}
            placeholder="guides/setup"
            onChange={(event) => setPath(event.target.value)}
          />
          <span className="field-hint">
            슬래시 앞부분은 상위 문서입니다. 예: guides/setup은 guides 문서 아래에 놓이고, 없는 상위 문서는 빈
            문서로 함께 만듭니다.
          </span>
        </div>
        {errors.map((error) => (
          <p key={error} className="field-error">
            {error}
          </p>
        ))}
      </form>
    </Dialog>
  );
}
