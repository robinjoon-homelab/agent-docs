import type { Mermaid } from 'mermaid';
import { useEffect, useId, useState } from 'react';
import type { MermaidFailure } from './mermaidError';
import { describeMermaidError } from './mermaidError';

type Rendered = {
  readonly code: string;
  readonly svg: string | null;
  readonly failure: MermaidFailure | null;
};

let loading: Promise<Mermaid> | null = null;

function loadMermaid(): Promise<Mermaid> {
  loading ??= import('mermaid').then((module) => {
    const dark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    module.default.initialize({
      startOnLoad: false,
      securityLevel: 'strict',
      theme: dark ? 'dark' : 'neutral',
    });
    return module.default;
  });
  return loading;
}

/**
 * Renders a mermaid diagram. A broken diagram shows the error line and code in place of the diagram;
 * the rest of the document keeps rendering. Mermaid is loaded lazily because it is large.
 */
export function MermaidPreview({ code, debounceMs = 250 }: { code: string; debounceMs?: number }) {
  const id = `mermaid-${useId().replace(/[^a-zA-Z0-9]/g, '')}`;
  const [rendered, setRendered] = useState<Rendered | null>(null);

  useEffect(() => {
    let current = true;
    const timer = setTimeout(() => {
      loadMermaid()
        .then((mermaid) => mermaid.render(id, code))
        .then(
          (result) => {
            if (current) setRendered({ code, svg: result.svg, failure: null });
          },
          (error: unknown) => {
            document.getElementById(`d${id}`)?.remove();
            if (current) setRendered({ code, svg: null, failure: describeMermaidError(code, error) });
          },
        );
    }, debounceMs);
    return () => {
      current = false;
      clearTimeout(timer);
    };
  }, [code, id, debounceMs]);

  if (code.trim() === '')
    return <div className="mermaid-empty faint small">다이어그램 코드를 입력하세요.</div>;
  if (rendered === null) return <div className="mermaid-empty faint small">다이어그램을 그리는 중…</div>;
  if (rendered.failure !== null) {
    const { line, message, source } = rendered.failure;
    return (
      <div className="mermaid-error" role="alert">
        <strong>{line === null ? '다이어그램 오류' : `다이어그램 오류 · ${line}번째 줄`}</strong>
        {source === null ? null : <pre className="mono">{source}</pre>}
        <span className="small">{message}</span>
      </div>
    );
  }
  // mermaid runs with securityLevel "strict", which sanitizes labels before producing the SVG.
  return <div className="mermaid-diagram" dangerouslySetInnerHTML={{ __html: rendered.svg ?? '' }} />;
}
