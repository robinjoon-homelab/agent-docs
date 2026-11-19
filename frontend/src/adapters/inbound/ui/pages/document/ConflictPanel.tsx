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

function orderPreview(side: readonly Block[], other: readonly Block[]): string {
  const ours = new Set(side.map((block) => block.id));
  const theirs = new Set(other.map((block) => block.id));
  const otherOrder = other.filter((block) => ours.has(block.id));
  const moved = side
    .filter((block) => theirs.has(block.id))
    .filter((block, index) => otherOrder[index]?.id !== block.id);
  const preview = moved.slice(0, 8).map((block) => blockPreview(block, 100) || block.id);
  return [...preview, ...(moved.length > preview.length ? ['…'] : [])].join('\n');
}

/**
 * Shown when someone (usually an agent) changed the same blocks first. The user's input stays in the
 * editor; for each block they pick their version or the current one, then the merge is saved.
 */
export function ConflictPanel({
  conflicts,
  orderConflict = null,
  mine,
  onResolve,
}: {
  conflicts: readonly SaveConflict[];
  orderConflict?: readonly Block[] | null;
  mine: readonly Block[];
  onResolve: (choices: ReadonlyMap<string, ConflictChoice>, orderChoice?: ConflictChoice) => Promise<void>;
}) {
  const [choices, setChoices] = useState<ReadonlyMap<string, ConflictChoice>>(new Map());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const mineById = new Map(mine.map((block) => [block.id, block]));
  const [orderSelection, setOrderSelection] = useState<{
    server: readonly Block[];
    choice: ConflictChoice;
  } | null>(null);
  const orderChoice = orderSelection?.server === orderConflict ? orderSelection.choice : undefined;
  const complete =
    conflicts.every((conflict) => choices.has(conflict.blockId)) &&
    (orderConflict === null || orderChoice !== undefined);
  const chooseOrder = (choice: ConflictChoice): void => {
    if (orderConflict !== null) setOrderSelection({ server: orderConflict, choice });
  };

  const choose = (blockId: string, choice: ConflictChoice): void => {
    setChoices((current) => new Map(current).set(blockId, choice));
  };

  return (
    <section className="conflict-panel" role="alert" aria-label="저장 충돌">
      <h2>다른 변경과 충돌했습니다</h2>
      <p className="small">입력한 내용은 그대로 있습니다. 변경이 겹친 항목에서 남길 쪽을 고르세요.</p>
      {orderConflict === null ? null : (
        <fieldset>
          <legend>블록 순서가 서로 다르게 바뀌었습니다</legend>
          <div className="conflict-row">
            {(['mine', 'theirs'] as const).map((side) => (
              <label key={side} className={`conflict-option${orderChoice === side ? ' is-chosen' : ''}`}>
                <input
                  type="radio"
                  name="conflict-order"
                  checked={orderChoice === side}
                  onChange={() => chooseOrder(side)}
                />
                <span className="conflict-label">{side === 'mine' ? '내 순서' : '현재 순서'}</span>
                <pre>
                  {orderPreview(
                    side === 'mine' ? mine : orderConflict,
                    side === 'mine' ? orderConflict : mine,
                  )}
                </pre>
              </label>
            ))}
          </div>
        </fieldset>
      )}
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
          onClick={() => {
            setChoices(new Map(conflicts.map((conflict) => [conflict.blockId, 'theirs' as const])));
            chooseOrder('theirs');
          }}
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
            const resolution =
              orderChoice === undefined ? onResolve(choices) : onResolve(choices, orderChoice);
            resolution.then(
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
