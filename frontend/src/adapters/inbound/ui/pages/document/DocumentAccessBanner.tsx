import type { RevocationReason } from '../../../../../domain/document/documentEvents';
import { errorMessage } from '../../common/States';

export function DocumentAccessBanner({
  access,
  onCopy,
}: {
  access: {
    readonly writable: boolean;
    readonly revoked: RevocationReason | null;
    readonly resuming: boolean;
    readonly recoveryError: unknown;
    readonly refresh: () => Promise<void>;
  };
  onCopy: () => void;
}) {
  if (access.writable) return null;
  return (
    <div className="warning-box doc-banner" role="status">
      {access.revoked !== null
        ? access.revoked === 'unauthenticated'
          ? '로그인이 필요합니다. 저장하지 못한 입력은 이 탭에 남아 있습니다.'
          : '현재 문서를 열 수 없습니다. 저장하지 못한 입력은 이 탭에 남아 있습니다.'
        : '읽기 전용입니다. 저장하지 못한 입력은 이 탭에 남아 있습니다.'}
      <button type="button" className="btn btn-small" onClick={onCopy}>
        입력 복사
      </button>
      {access.recoveryError === null ? null : (
        <p role="alert">
          권한을 다시 확인하지 못했습니다. 본문과 손그림 입력은 보존됩니다.{' '}
          {errorMessage(access.recoveryError)}{' '}
          <button
            type="button"
            className="btn btn-small"
            disabled={access.resuming}
            onClick={() => void access.refresh()}
          >
            권한 다시 확인
          </button>
        </p>
      )}
    </div>
  );
}
