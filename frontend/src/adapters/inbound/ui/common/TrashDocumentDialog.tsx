import { Link } from 'react-router';
import { Dialog } from './Dialog';

export function TrashDocumentDialog({
  descendants,
  owner,
  busy,
  onCancel,
  onConfirm,
}: {
  descendants: number;
  owner: boolean;
  busy: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <Dialog
      open
      title="휴지통으로 이동"
      onClose={() => {
        if (!busy) onCancel();
      }}
      footer={
        <>
          <button type="button" className="btn" disabled={busy} onClick={onCancel}>
            취소
          </button>
          <button type="button" className="btn btn-primary" disabled={busy} onClick={onConfirm}>
            휴지통으로 이동
          </button>
        </>
      }
    >
      <p>
        {descendants > 0
          ? `이 문서와 하위 문서 ${descendants}개를 휴지통으로 옮깁니다.`
          : '이 문서를 휴지통으로 옮깁니다.'}{' '}
        스페이스의 휴지통에서 복원할 수 있습니다.
      </p>
      {owner ? (
        <p>
          <Link to="/settings/data" target="_blank" rel="noreferrer">
            이동 전에 스페이스 내보내기
          </Link>
        </p>
      ) : null}
    </Dialog>
  );
}
