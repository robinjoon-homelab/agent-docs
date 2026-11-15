import type { ReactNode } from 'react';
import { Fragment, useEffect, useRef } from 'react';
import katex from 'katex';
import { mathSpans } from '../../../../domain/document/math';
import { useServices } from '../../../../config/ServicesContext';
import 'katex/dist/katex.min.css';
import { Link } from 'react-router';
import type { Inline, InlineRun } from '../../../../domain/document/inline';
import { parseInline } from '../../../../domain/document/inline';
import { commentParts } from '../../../../domain/thread/commentBody';

/** Where a `doc:<id>` link points inside the app. The id alone is enough: the document page resolves its space. */
export function docLinkHref(href: string): string | null {
  return href.startsWith('doc:') ? `/d/${encodeURIComponent(href.slice(4))}` : null;
}

export function MathPreview({ source, display = false }: { source: string; display?: boolean }) {
  const ref = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    if (ref.current !== null)
      katex.render(source, ref.current, {
        displayMode: display,
        trust: false,
        throwOnError: false,
        strict: 'error',
        maxSize: 20,
        maxExpand: 1000,
      });
  }, [source, display]);
  return <span className="math-preview" ref={ref} aria-label={source} />;
}

function mathText(text: string): ReactNode {
  const spans = mathSpans(text);
  if (spans.length === 0) return text;
  const parts: ReactNode[] = [];
  let cursor = 0;
  for (const span of spans) {
    parts.push(
      text.slice(cursor, span.from),
      <MathPreview key={span.from} source={span.source} display={span.display} />,
    );
    cursor = span.to;
  }
  parts.push(text.slice(cursor));
  return parts;
}

function AttachmentLink({ href, children }: { href: string; children: ReactNode }) {
  const services = useServices();
  return (
    <a href={services.assets.contentUrl(href.slice(6))} download rel="noreferrer noopener">
      {children}
    </a>
  );
}

function wrap(run: Extract<InlineRun, { kind: 'text' }>, key: number) {
  let node: ReactNode = run.marks.code || run.href !== null ? run.text : mathText(run.text);
  if (run.marks.code) node = <code>{node}</code>;
  if (run.marks.strike) node = <s>{node}</s>;
  if (run.marks.italic) node = <em>{node}</em>;
  if (run.marks.bold) node = <strong>{node}</strong>;
  if (run.href !== null) {
    const internal = docLinkHref(run.href);
    node = run.href.startsWith('asset:') ? (
      <AttachmentLink href={run.href}>{node}</AttachmentLink>
    ) : internal === null ? (
      <a href={run.href} target="_blank" rel="noreferrer noopener">
        {node}
      </a>
    ) : (
      <Link to={internal}>{node}</Link>
    );
  }
  return <Fragment key={key}>{node}</Fragment>;
}

export function InlineRuns({ inline }: { inline: Inline }) {
  return <>{inline.map((run, index) => (run.kind === 'break' ? <br key={index} /> : wrap(run, index)))}</>;
}

/** Read-only rendering of an inline markdown string. */
export function InlineText({ markdown }: { markdown: string }) {
  return <InlineRuns inline={parseInline(markdown)} />;
}

/** Comment bodies: fenced code blocks, otherwise one paragraph per line with inline markdown. */
export function CommentBody({ markdown }: { markdown: string }) {
  return (
    <div className="comment-body">
      {commentParts(markdown).map((part, index) =>
        part.kind === 'code' ? (
          <pre key={index} className="comment-code" data-language={part.language || undefined}>
            <code>{part.code}</code>
          </pre>
        ) : (
          <p key={index}>{part.text === '' ? '\u00a0' : <InlineText markdown={part.text} />}</p>
        ),
      )}
    </div>
  );
}
