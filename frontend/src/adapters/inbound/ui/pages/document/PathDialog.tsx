import { useState } from 'react';
import { PATH_RULE_TEXT, isValidPath, normalizePath } from '../../../../../domain/document/document';
import { Dialog } from '../../common/Dialog';

/** Rename or move a document within its space by editing its path. */
export function PathDialog({
  path,
  onClose,
  onSave,
}: {
  path: string;
  onClose: () => void;
  onSave: (path: string) => void;
}) {
  const [value, setValue] = useState(path);
  const normalized = normalizePath(value);
  const valid = isValidPath(normalized);
  const submit = (): void => {
    if (!valid) return;
    if (normalized !== path) onSave(normalized);
    onClose();
  };
  return (
    <Dialog
      open
      title="경로 바꾸기"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
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
          onChange={(event) => setValue(event.target.value)}
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
