import type { ReactNode } from 'react';
import { Fragment } from 'react';
import { Link } from 'react-router';
import type { Inline, InlineRun } from '../../../../domain/document/inline';
import { parseInline } from '../../../../domain/document/inline';
import { commentParts } from '../../../../domain/thread/commentBody';

/** Where a `doc:<id>` link points inside the app. The id alone is enough: the document page resolves its space. */
export function docLinkHref(href: string): string | null {
  return href.startsWith('doc:') ? `/d/${encodeURIComponent(href.slice(4))}` : null;
}

function wrap(run: Extract<InlineRun, { kind: 'text' }>, key: number) {
  let node: ReactNode = run.text;
  if (run.marks.code) node = <code>{node}</code>;
  if (run.marks.strike) node = <s>{node}</s>;
  if (run.marks.italic) node = <em>{node}</em>;
  if (run.marks.bold) node = <strong>{node}</strong>;
  if (run.href !== null) {
    const internal = docLinkHref(run.href);
    node =
      internal === null ? (
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
