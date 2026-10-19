import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { isApiError, isNotVisible } from '../../../../application/shared/ports/errors';

/** Inaccessible and nonexistent look the same, so a hidden space or document does not leak. */
export function NotFound() {
  return (
    <div className="center-screen">
      <h1>찾을 수 없습니다</h1>
      <p className="muted">주소가 잘못되었거나 접근할 수 없는 페이지입니다.</p>
      <Link className="btn" to="/">
        홈으로
      </Link>
    </div>
  );
}

export function NoAccessDocument() {
  return (
    <div className="center-screen">
      <h1>접근할 수 없는 문서</h1>
      <p className="muted">이 문서를 볼 권한이 없거나 문서가 삭제되었습니다.</p>
      <Link className="btn" to="/">
        홈으로
      </Link>
    </div>
  );
}

export function Loading({ label = '불러오는 중…' }: { label?: string }) {
  return (
    <div className="loading" role="status" aria-live="polite">
      {label}
    </div>
  );
}

export function errorMessage(error: unknown): string {
  if (isApiError(error)) return error.fix === null ? error.message : `${error.message} ${error.fix}`;
  return error instanceof Error ? error.message : '알 수 없는 오류가 발생했습니다.';
}

export function ErrorBox({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div className="error-box" role="alert">
      {errorMessage(error)}
      {onRetry === undefined ? null : (
        <>
          {' '}
          <button type="button" className="btn btn-small" onClick={onRetry}>
            다시 시도
          </button>
        </>
      )}
    </div>
  );
}

/** Error screen for a page load: not found/forbidden get the shared not-found screen. */
export function LoadError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  if (isNotVisible(error)) return <NotFound />;
  return (
    <div className="page">
      <ErrorBox error={error} onRetry={onRetry} />
    </div>
  );
}

export function EmptyState({
  title,
  children,
  actions,
}: {
  title: string;
  children?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className="empty-state">
      <strong>{title}</strong>
      {children}
      {actions === undefined ? null : <div className="empty-state-actions">{actions}</div>}
    </div>
  );
}
