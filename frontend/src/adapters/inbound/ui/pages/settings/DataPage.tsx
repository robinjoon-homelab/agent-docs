import { useState } from 'react';
import type { PortabilityUseCases } from '../../../../../application/portability/portabilityUseCases';
import type { FolderImportResult } from '../../../../../domain/portability/portability';
import { folderEntries } from './folderSelection';
import './data.css';

type Props = {
  readonly portability: PortabilityUseCases;
  readonly spaces: readonly { slug: string; name: string }[];
  readonly owner: boolean;
};
export function DataPage({ portability, spaces, owner }: Props) {
  const [space, setSpace] = useState(spaces[0]?.slug ?? '');
  const [tool, setTool] = useState('Markdown');
  const [files, setFiles] = useState<readonly File[]>([]);
  const [result, setResult] = useState<FolderImportResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError('');
    try {
      await action();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '작업에 실패했습니다.');
    } finally {
      setBusy(false);
    }
  }
  async function download(scope: string | null) {
    const bytes = await portability.exportZip(scope);
    const url = URL.createObjectURL(new Blob([bytes], { type: 'application/zip' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = 'agent-docs.zip';
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  return (
    <main className="data-page">
      <h1>데이터 가져오기·내보내기</h1>
      <label>
        스페이스
        <select value={space} onChange={(event) => setSpace(event.target.value)}>
          {spaces.map((entry) => (
            <option key={entry.slug} value={entry.slug}>
              {entry.name}
            </option>
          ))}
        </select>
      </label>
      <section>
        <h2>마크다운 폴더 가져오기</h2>
        <p>
          폴더 안의 마크다운과 이미지를 가져옵니다. 같은 경로의 문서는 덮어쓰지 않습니다. 실패한 파일이 있어도
          성공한 문서와 업로드된 이미지는 유지됩니다.
        </p>
        <label>
          원본 도구
          <input value={tool} onChange={(event) => setTool(event.target.value)} maxLength={100} />
        </label>
        <input
          type="file"
          multiple
          ref={(element) => {
            element?.setAttribute('webkitdirectory', '');
          }}
          onChange={(event) => {
            setFiles(Array.from(event.target.files ?? []));
            setResult(null);
          }}
          aria-label="가져올 폴더"
        />
        <p>{files.length}개 파일 선택. 로컬 절대 경로는 전송하지 않습니다.</p>
        <button
          disabled={busy || !space || !tool.trim() || files.length === 0}
          onClick={() => {
            void run(async () => {
              setResult(await portability.importFolder(space, tool, await folderEntries(files)));
            });
          }}
        >
          가져오기
        </button>
        {result !== null && (
          <ul>
            {result.files.map((entry, index) => (
              <li key={index}>
                {entry.path}: {entry.status}
                {entry.code === null ? '' : ` (${entry.code})`}
                {entry.relatedPath === null ? '' : `: ${entry.relatedPath}`}
              </li>
            ))}
          </ul>
        )}
      </section>
      {owner && (
        <section>
          <h2>ZIP 내보내기</h2>
          <p>휴지통과 보관 공간, 문서 이력과 첨부 원본을 포함합니다.</p>
          <button
            disabled={busy}
            onClick={() => {
              void run(() => download(null));
            }}
          >
            전체 내보내기
          </button>
          <button
            disabled={busy || !space}
            onClick={() => {
              void run(() => download(space));
            }}
          >
            선택한 스페이스 내보내기
          </button>
        </section>
      )}
      {busy && <p role="status">처리 중입니다.</p>}
      {error && <p role="alert">{error}</p>}
    </main>
  );
}
