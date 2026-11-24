import { useLayoutEffect, useRef } from 'react';

export function DesignDeleteConfirmation({
  label,
  title,
  notice,
  onCancel,
  onConfirm,
}: {
  label: string;
  title: string;
  notice: string;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const cancel = useRef<HTMLButtonElement>(null);
  useLayoutEffect(() => {
    const node = cancel.current;
    if (!node) return;
    node.focus({ preventScroll: true });
    const panel = node.closest<HTMLElement>('.flow-panel-body'),
      dialog = node.closest<HTMLElement>('[role=alertdialog]');
    if (panel && dialog) {
      const area = panel.getBoundingClientRect(),
        box = dialog.getBoundingClientRect();
      if (box.top < area.top) panel.scrollTop += box.top - area.top;
      else if (box.bottom > area.bottom) panel.scrollTop += box.bottom - area.bottom;
    }
  }, []);
  return (
    <div className="flow-delete-confirm" role="alertdialog" aria-label={label}>
      <strong>{title}</strong>
      <span>{notice}</span>
      <div>
        <button type="button" ref={cancel} onClick={onCancel}>
          취소
        </button>
        <button type="button" onClick={onConfirm}>
          삭제
        </button>
      </div>
    </div>
  );
}
