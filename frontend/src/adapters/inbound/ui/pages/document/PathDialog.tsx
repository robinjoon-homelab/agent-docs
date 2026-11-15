import { useState } from 'react';
import { PATH_RULE_TEXT, isValidPath, normalizePath } from '../../../../../domain/document/document';
import { Dialog } from '../../common/Dialog';

/** Rename or move a document within its space by editing its path. */
export function PathDialog({
  path,
  onClose,
  onSave,
  onInput,
  onCancel,
}: {
  path: string;
  onClose: () => void;
  onSave: (path: string) => void;
  onInput?: (path: string) => void;
  onCancel?: () => void;
}) {
  const [value, setValue] = useState(path);
  const normalized = normalizePath(value);
  const valid = isValidPath(normalized);
  const cancel = () => {
    onCancel?.();
    onClose();
  };
  const submit = (): void => {
    if (!valid) return;
    onSave(normalized);
    onClose();
  };
  return (
    <Dialog
      open
      title="경로 바꾸기"
      onClose={cancel}
      footer={
        <>
          <button type="button" className="btn" onClick={cancel}>
            취소
          </button>
          <button type="button" className="btn btn-primary" disabled={!valid} onClick={submit}>
            바꾸기
          </button>
        </>
      }
    >
      <div className="field">
        <label htmlFor="document-path">경로</label>
        <input
          id="document-path"
          className="input mono"
          value={value}
          autoFocus
          onChange={(event) => {
            setValue(event.target.value);
            onInput?.(normalizePath(event.target.value));
          }}
          onKeyDown={(event) => {
            if (event.key === 'Enter') submit();
          }}
        />
        <span className="field-hint">폴더를 바꾸면 문서가 옮겨집니다. 예: guides/setup</span>
        {valid ? null : <span className="field-error">{PATH_RULE_TEXT}</span>}
      </div>
    </Dialog>
  );
}
