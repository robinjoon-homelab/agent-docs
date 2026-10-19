import type { ReactNode } from 'react';
import { useEffect, useRef } from 'react';
import { Icon } from './Icon';

/** Modal built on the native <dialog>: focus trapping, Esc and the backdrop come from the browser. */
export function Dialog({
  open,
  title,
  onClose,
  children,
  footer,
  wide = false,
}: {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  wide?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (dialog === null) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      className="dialog"
      style={wide ? { width: 'min(760px, calc(100vw - 32px))' } : undefined}
      onClose={onClose}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      aria-label={title}
    >
      {open ? (
        <>
          <div className="dialog-header">
            <h2>{title}</h2>
            <button type="button" className="icon-btn" onClick={onClose} aria-label="닫기">
              <Icon name="close" />
            </button>
          </div>
          <div className="dialog-body">{children}</div>
          {footer === undefined ? null : <div className="dialog-footer">{footer}</div>}
        </>
      ) : null}
    </dialog>
  );
}
