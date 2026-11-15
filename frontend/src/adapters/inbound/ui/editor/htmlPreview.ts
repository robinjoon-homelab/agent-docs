/** The policy is parsed before any uploaded markup; the stored original is never rewritten. */
export function htmlPreviewSource(source: string): string {
  const policy =
    "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data: blob:; font-src data:; connect-src 'none'; object-src 'none'; frame-src 'none'; worker-src 'none'; base-uri 'none'; form-action 'none'";
  const errors = `<script>addEventListener('error',function(e){var p=document.createElement('pre');p.setAttribute('role','alert');p.textContent='시안 오류: '+e.message;document.body.appendChild(p)});addEventListener('unhandledrejection',function(){var p=document.createElement('pre');p.setAttribute('role','alert');p.textContent='시안 오류: 비동기 작업에 실패했습니다.';document.body.appendChild(p)});</script>`;
  return `<!doctype html><html><head><meta http-equiv="Content-Security-Policy" content="${policy}"><meta name="referrer" content="no-referrer">${errors}</head><body>${source}</body></html>`;
}
