import { useState } from 'react';
import { Link } from 'react-router';
import { useServices } from '../../../../../config/ServicesContext';
import type { TrashedDocument } from '../../../../../domain/document/document';
import { descendantCount } from '../../../../../domain/document/pathTree';
import { useSession } from '../../app/SessionContext';
import { useShell } from '../../app/ShellContext';
import { Dialog } from '../../common/Dialog';
import { RelativeTime } from '../../common/RelativeTime';
import { ErrorBox, Loading } from '../../common/States';
import { useToast } from '../../common/Toast';
import { useLoad } from '../../hooks/useLoad';
import './space.css';

export function TrashSection({ spaceSlug }: { spaceSlug: string }) {
  const { me } = useSession();
  const services = useServices();
  const shell = useShell();
  const toast = useToast();
  const trash = useLoad(`space:trash:${spaceSlug}`, () => services.documents.listTrash(spaceSlug));
  const [pendingDelete, setPendingDelete] = useState<TrashedDocument | null>(null);
  const [failure, setFailure] = useState<unknown>(null);
  const canConfirmDelete = me.owner && trash.state === 'ready' && pendingDelete !== null;
  const descendantDeleteCount =
    pendingDelete !== null && trash.state === 'ready' ? descendantCount(trash.data, pendingDelete.path) : 0;

  const restore = async (document: TrashedDocument): Promise<void> => {
    try {
      await services.documents.untrash(document.id);
      // Documents trashed together with it come back too, so the list is read again.
      trash.reload();
      shell.refreshTree();
      toast(`'${document.title}' 문서를 복원했습니다`);
    } catch (error) {
      setFailure(error);
    }
  };

  const destroy = async (document: TrashedDocument): Promise<void> => {
    if (!me.owner || trash.state !== 'ready') return;
    setPendingDelete(null);
    try {
      await services.documents.destroy(document.id);
      trash.reload();
      toast(`'${document.title}' 문서를 영구 삭제했습니다`);
    } catch (error) {
      setFailure(error);
    }
  };

  return (
    <>
      {failure === null ? null : <ErrorBox error={failure} />}
      {trash.state === 'loading' ? <Loading /> : null}
      {trash.state === 'error' ? <ErrorBox error={trash.error} onRetry={trash.reload} /> : null}
      {trash.state === 'ready' && trash.data.length === 0 ? (
        <p className="faint small">휴지통이 비어 있습니다.</p>
      ) : null}
      {trash.state === 'ready' && trash.data.length > 0 ? (
        <ul className="list card space-list">
          {trash.data.map((document) => (
            <li key={document.id} className="list-row settings-trash-row">
              <div className="row-main">
                <div className="row-title">{document.title === '' ? document.path : document.title}</div>
                <div className="row-sub">
                  {document.path} · 삭제 <RelativeTime iso={document.trashedAt} />
                </div>
              </div>
              <div className="settings-row-actions">
                <button type="button" className="btn btn-small" onClick={() => void restore(document)}>
                  복원
                </button>
                {me.owner ? (
                  <button
                    type="button"
                    className="btn btn-small btn-danger"
                    onClick={() => setPendingDelete(document)}
                  >
                    영구 삭제
                  </button>
                ) : null}
              </div>
            </li>
          ))}
        </ul>
      ) : null}
      <Dialog
        open={canConfirmDelete}
        title="영구 삭제"
        onClose={() => setPendingDelete(null)}
        footer={
          <>
            <button type="button" className="btn" onClick={() => setPendingDelete(null)}>
              취소
            </button>
            <button
              type="button"
              className="btn btn-danger"
              disabled={!canConfirmDelete}
              onClick={() => {
                if (canConfirmDelete) void destroy(pendingDelete);
              }}
            >
              영구 삭제
            </button>
          </>
        }
      >
        <p style={{ margin: 0 }}>
          ‘{pendingDelete?.title}’ 문서와 모든 리비전·댓글을 영구 삭제합니다.
          {descendantDeleteCount > 0
            ? ` 휴지통에 있는 하위 문서 ${descendantDeleteCount}개도 함께 지웁니다.`
            : ''}{' '}
          되돌릴 수 없습니다.
        </p>
        {me.owner ? (
          <p className="small muted" style={{ marginTop: 8, marginBottom: 0 }}>
            <Link to="/settings/data" target="_blank" rel="noreferrer">
              삭제 전에 스페이스 내보내기
            </Link>
          </p>
        ) : null}
      </Dialog>
    </>
  );
}
