import { useSearchParams } from 'react-router';
import { GITHUB_AUTHORIZATION_PATH, loginErrorOf } from '../../../../../domain/session/me';
import { rememberNext } from '../../app/nextPath';
import { usePageTitle } from '../../hooks/usePageTitle';
import './login.css';

const ERROR_TEXT = {
  not_allowed:
    '이 GitHub 계정은 허용 목록에 없습니다. 서버 관리자에게 계정을 허용 목록에 추가해 달라고 요청하세요.',
  unknown: '로그인하지 못했습니다. 잠시 후 다시 시도하세요.',
} as const;

function GitHubMark() {
  return (
    <svg width="18" height="18" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
      <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8Z" />
    </svg>
  );
}

/** S1: GitHub login is a full-page navigation to Spring Security's OAuth2 entry point. */
export function LoginPage() {
  const [params] = useSearchParams();
  const error = loginErrorOf(params.get('error'));
  usePageTitle('로그인');

  const login = (): void => {
    rememberNext(params.get('next'));
    window.location.assign(GITHUB_AUTHORIZATION_PATH);
  };

  return (
    <main className="login-screen">
      <div className="login-card">
        <div className="login-logo" aria-hidden="true">
          A
        </div>
        <h1>Agent Docs</h1>
        <p className="muted">사람과 에이전트가 같은 문서를 읽고 씁니다.</p>
        {error === null ? null : (
          <div className="error-box login-error" role="alert">
            {ERROR_TEXT[error]}
          </div>
        )}
        <button type="button" className="btn btn-primary login-button" onClick={login}>
          <GitHubMark />
          GitHub로 로그인
        </button>
      </div>
    </main>
  );
}
