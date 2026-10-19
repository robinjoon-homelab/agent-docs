import { useState } from 'react';
import { useNavigate } from 'react-router';
import { SPACE_DRAFT_ERROR_TEXT, suggestSlug } from '../../../../../domain/space/space';
import { useServices } from '../../../../../config/ServicesContext';
import { useShell } from '../../app/ShellContext';
import { Dialog } from '../../common/Dialog';
import { errorMessage } from '../../common/States';

/** "새 스페이스": the slug follows the name until the user edits it. */
export function NewSpaceDialog({ onClose }: { onClose: () => void }) {
  const services = useServices();
  const shell = useShell();
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [slugEdited, setSlugEdited] = useState(false);
  const [description, setDescription] = useState('');
  const [errors, setErrors] = useState<readonly string[]>([]);
  const [busy, setBusy] = useState(false);

  const submit = async (): Promise<void> => {
    setBusy(true);
    try {
      const result = await services.spaces.create({ slug, name, description, links: [] });
      if (!result.ok) {
        setErrors(result.error.map((error) => SPACE_DRAFT_ERROR_TEXT[error]));
        return;
      }
      shell.refreshSpaces();
      onClose();
      void navigate(`/s/${encodeURIComponent(result.value.slug)}`);
    } catch (error) {
      setErrors([errorMessage(error)]);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog
      open
      title="새 스페이스"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            취소
          </button>
          <button type="submit" form="new-space" className="btn btn-primary" disabled={busy}>
            만들기
          </button>
        </>
      }
    >
      <form
        id="new-space"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        <div className="field">
          <label htmlFor="new-space-name">이름</label>
          <input
            id="new-space-name"
            className="input"
            value={name}
            autoFocus
            onChange={(event) => {
              setName(event.target.value);
              if (!slugEdited) setSlug(suggestSlug(event.target.value));
            }}
          />
        </div>
        <div className="field">
          <label htmlFor="new-space-slug">슬러그</label>
          <input
            id="new-space-slug"
            className="input mono"
            value={slug}
            placeholder="agent-docs"
            onChange={(event) => {
              setSlug(event.target.value);
              setSlugEdited(true);
            }}
          />
          <span className="field-hint">
            주소에 쓰입니다: /s/{slug === '' ? '…' : slug}. 영문 소문자, 숫자, 하이픈.
          </span>
        </div>
        <div className="field">
          <label htmlFor="new-space-description">설명</label>
          <textarea
            id="new-space-description"
            className="textarea"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
          />
        </div>
        <p className="field-hint">
          에이전트 연결은 스페이스 단위로 허용합니다. 접근 범위가 다른 문서는 다른 스페이스에 두세요.
        </p>
        {errors.map((error) => (
          <p key={error} className="field-error">
            {error}
          </p>
        ))}
      </form>
    </Dialog>
  );
}
