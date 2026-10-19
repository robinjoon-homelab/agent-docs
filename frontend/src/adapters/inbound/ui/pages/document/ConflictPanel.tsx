import { useState } from 'react';
import type { Block } from '../../../../../domain/document/block';
import { blockPreview } from '../../../../../domain/document/blockText';
import type { SaveConflict } from '../../../../../domain/document/document';
import type { ConflictChoice } from '../../../../../domain/document/merge';
import { errorMessage } from '../../common/States';

function blockMarkdown(block: Block | undefined): string | null {
  if (block === undefined) return null;
  if ('text' in block) return block.text;
  if (block.type === 'code') return block.code;
  return blockPreview(block, 400);
}

/**
 * Shown when someone (usually an agent) changed the same blocks first. The user's input stays in the
 * editor; for each block they pick their version or the current one, then the merge is saved.
 */
export function ConflictPanel({
  conflicts,
  mine,
  onResolve,
}: {
  conflicts: readonly SaveConflict[];
  mine: readonly Block[];
  onResolve: (choices: ReadonlyMap<string, ConflictChoice>) => Promise<void>;
}) {
  const [choices, setChoices] = useState<ReadonlyMap<string, ConflictChoice>>(new Map());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const mineById = new Map(mine.map((block) => [block.id, block]));
  const complete = conflicts.every((conflict) => choices.has(conflict.blockId));

  const choose = (blockId: string, choice: ConflictChoice): void => {
    setChoices((current) => new Map(current).set(blockId, choice));
  };

  return (
    <section className="conflict-panel" role="alert" aria-label="저장 충돌">
      <h2>다른 변경과 충돌했습니다</h2>
      <p className="small">
        내가 고친 블록을 다른 사람이나 에이전트가 먼저 바꿨습니다. 입력한 내용은 그대로 있습니다. 블록마다
        남길 쪽을 고르세요.
      </p>
      {conflicts.map((conflict) => {
        const myVersion = blockMarkdown(mineById.get(conflict.blockId));
        const current =
          conflict.current === null ? conflict.currentMarkdown : blockMarkdown(conflict.current);
        const choice = choices.get(conflict.blockId);
        return (
          <div key={conflict.blockId} className="conflict-row">
            <label className={`conflict-option${choice === 'mine' ? ' is-chosen' : ''}`}>
              <input
                type="radio"
                name={`conflict-${conflict.blockId}`}
                checked={choice === 'mine'}
                onChange={() => choose(conflict.blockId, 'mine')}
              />
              <span className="conflict-label">내 버전</span>
              <pre>{myVersion ?? '(내 쪽에서 삭제함)'}</pre>
            </label>
            <label className={`conflict-option${choice === 'theirs' ? ' is-chosen' : ''}`}>
              <input
                type="radio"
                name={`conflict-${conflict.blockId}`}
                checked={choice === 'theirs'}
                onChange={() => choose(conflict.blockId, 'theirs')}
              />
              <span className="conflict-label">현재 버전</span>
              <pre>{current ?? '(삭제됨)'}</pre>
            </label>
          </div>
        );
      })}
      {error === null ? null : <p className="error-text">{errorMessage(error)}</p>}
      <div className="conflict-actions">
        <button
          type="button"
          className="btn btn-small"
          onClick={() =>
            setChoices(new Map(conflicts.map((conflict) => [conflict.blockId, 'theirs' as const])))
          }
        >
          모두 현재 버전으로
        </button>
        <button
          type="button"
          className="btn btn-small btn-primary"
          disabled={!complete || busy}
          onClick={() => {
            setBusy(true);
            setError(null);
            onResolve(choices).then(
              () => setBusy(false),
              (cause: unknown) => {
                setBusy(false);
                setError(cause);
              },
            );
          }}
        >
          선택대로 저장
        </button>
      </div>
    </section>
  );
}
