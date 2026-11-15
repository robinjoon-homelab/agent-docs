import { useEffect, useRef, useState } from 'react';
import { htmlPreviewSource } from './htmlPreview';

export type MockupOptions = {
  readonly htmlSource: (assetId: string) => Promise<string>;
  readonly replaceHtml: (blockId: string, previousAsset: string, file: File) => Promise<void>;
};

type Loaded = { readonly assetId: string; readonly source?: string; readonly error?: string };

export function HtmlMockup({
  assetId,
  blockId,
  title,
  editable,
  options,
}: {
  assetId: string;
  blockId: string;
  title: string;
  editable: boolean;
  options: MockupOptions;
}) {
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [mobile, setMobile] = useState(false);
  const [showSource, setShowSource] = useState(false);
  const [replacement, setReplacement] = useState<string | null>(null);
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => {
    let active = true;
    void options.htmlSource(assetId).then(
      (source) => {
        if (active) setLoaded({ assetId, source });
      },
      () => {
        if (active) setLoaded({ assetId, error: 'HTML 원문을 읽지 못했습니다.' });
      },
    );
    return () => {
      active = false;
    };
  }, [assetId, options]);
  const source = loaded?.assetId === assetId ? loaded.source : undefined;
  return (
    <div className="html-mockup" contentEditable={false}>
      <div className="code-block-bar">
        <button type="button" aria-pressed={!mobile} onClick={() => setMobile(false)}>
          데스크톱
        </button>
        <button type="button" aria-pressed={mobile} onClick={() => setMobile(true)}>
          모바일
        </button>
        <button type="button" aria-pressed={showSource} onClick={() => setShowSource(!showSource)}>
          HTML 원문
        </button>
        {editable ? (
          <button type="button" disabled={replacement === '교체 중…'} onClick={() => input.current?.click()}>
            시안 교체
          </button>
        ) : null}
      </div>
      {loaded?.assetId === assetId && loaded.error !== undefined ? <p role="alert">{loaded.error}</p> : null}
      {replacement !== null ? <p role="status">{replacement}</p> : null}
      {source === undefined ? (
        <p>시안 불러오는 중…</p>
      ) : (
        <>
          <div className="html-mockup-scroll">
            <iframe
              title={title || 'HTML 시안'}
              sandbox="allow-scripts"
              referrerPolicy="no-referrer"
              srcDoc={htmlPreviewSource(source)}
              style={{ width: mobile ? 375 : 1024, maxWidth: mobile ? 'none' : '100%', height: 600 }}
            />
          </div>
          {showSource ? (
            <pre className="html-mockup-source">
              <code>{source}</code>
            </pre>
          ) : null}
        </>
      )}
      <input
        ref={input}
        hidden
        type="file"
        accept="text/html,.html,.htm"
        onChange={(event) => {
          const file = event.target.files?.[0];
          event.target.value = '';
          if (file === undefined) return;
          setReplacement('교체 중…');
          void options.replaceHtml(blockId, assetId, file).then(
            () => setReplacement('시안을 교체했습니다.'),
            (error: unknown) =>
              setReplacement(error instanceof Error ? error.message : '시안을 교체하지 못했습니다.'),
          );
        }}
      />
    </div>
  );
}
