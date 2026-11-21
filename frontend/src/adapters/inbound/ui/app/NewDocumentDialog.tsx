import './newDocument.css';
import type { FlowFrame } from '../../../../domain/document/block';
import { FRAMES } from '../../../../domain/flow/flow';
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
  const [type, setType] = useState<'doc' | 'flow'>('doc');
  const [frame, setFrame] = useState<FlowFrame>('phone');
  const [idPrefix, setPrefix] = useState('M');
  const [errors, setErrors] = useState<readonly string[]>([]);
  const [busy, setBusy] = useState(false);

  const submit = async (): Promise<void> => {
    setBusy(true);
    try {
      const result = await services.documents.create(spaceSlug, path, title, { type, frame, idPrefix });
      if (!result.ok) {
        setErrors(result.error.map((error) => NEW_DOCUMENT_ERROR_TEXT[error]));
        return;
      }
      onCreated();
      onClose();
      void navigate(documentHref(result.value.space.slug, result.value.id), {
        state: { flowEdit: type === 'flow' },
      });
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
        <fieldset className="new-document-types">
          <legend>문서 유형</legend>
          {(['doc', 'flow'] as const).map((kind) => (
            <label key={kind} className={type === kind ? 'selected' : ''}>
              <input
                type="radio"
                name="document-type"
                value={kind}
                checked={type === kind}
                onChange={() => setType(kind)}
              />
              <span>
                <strong>{kind === 'doc' ? '일반 문서' : '화면 흐름 문서'}</strong>
                <small>
                  {kind === 'doc'
                    ? '블록 에디터로 글, 표, 이미지, 다이어그램을 씁니다.'
                    : '화면을 만들고 요소를 자유롭게 배치한 뒤, 요소마다 설명과 누르면 가는 화면을 적습니다.'}
                </small>
              </span>
            </label>
          ))}
        </fieldset>
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
        {type === 'flow' && (
          <div className="new-flow-settings">
            <label>
              문서 기본 틀
              <select value={frame} onChange={(e) => setFrame(e.target.value as FlowFrame)}>
                {Object.entries(FRAMES).map(([key, value]) => (
                  <option key={key} value={key}>
                    {value[0]} {value[1]}×{value[2]}
                  </option>
                ))}
              </select>
            </label>
            <label>
              화면 ID 접두어
              <input
                value={idPrefix}
                maxLength={4}
                onChange={(e) => setPrefix(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, ''))}
              />
            </label>
            <span>
              만들면 편집 모드로 열리고, 빈 화면 {idPrefix || 'S'}-01 하나가 들어 있습니다. 둘 다 나중에 화면
              속성에서 바꿀 수 있습니다.
            </span>
          </div>
        )}
        {errors.map((error) => (
          <p key={error} className="field-error">
            {error}
          </p>
        ))}
      </form>
    </Dialog>
  );
}
