import type { Block } from '../../../../../domain/document/block';
import { blockPlainText, supportsTextAnchor } from '../../../../../domain/document/blockText';
import { historyInlineDiff } from '../../../../../domain/document/historyInlineDiff';

export function InlineDiff({ before, after }: { before: Block; after: Block }) {
  if (!supportsTextAnchor(before) || !supportsTextAnchor(after)) return null;
  const changes = historyInlineDiff(blockPlainText(before), blockPlainText(after));
  if (changes.every((change) => change.kind === 'same')) return null;
  return (
    <div className="history-inline-diff" aria-label="본문 글자 변경">
      {changes.map((change, index) => {
        if (change.kind === 'added') return <ins key={index}>{change.text}</ins>;
        if (change.kind === 'removed') return <del key={index}>{change.text}</del>;
        return <span key={index}>{change.text}</span>;
      })}
    </div>
  );
}
