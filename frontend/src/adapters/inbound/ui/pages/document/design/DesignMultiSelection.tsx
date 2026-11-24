import type { RefObject } from 'react';
import type { DesignAddress } from '../../../../../../domain/flow/designAddress';
import type { DesignCanvasCommands, CanvasCommand } from './DesignEditCommands';
import { lookupRow, type DesignRow } from './designLocation';
const alignment: readonly [CanvasCommand, string][] = [
  ['left', '왼쪽 맞춤'],
  ['center', '가로 가운데'],
  ['right', '오른쪽 맞춤'],
  ['top', '위 맞춤'],
  ['middle', '세로 가운데'],
  ['bottom', '아래 맞춤'],
];
export function DesignMultiSelection({
  selection,
  rows,
  commands,
}: {
  selection: readonly DesignAddress[];
  rows: readonly DesignRow[];
  commands: RefObject<DesignCanvasCommands | null>;
}) {
  const chosen = selection.map((a) => lookupRow(rows, a)).filter((r) => r !== undefined);
  const locked = chosen.some((r) => r.occurrence.node.locked === true);
  const count = chosen.filter((r) => !r.occurrence.node.locked).length;
  const exec = (c: CanvasCommand) => {
    commands.current?.execute(c);
  };
  return (
    <section className="flow-inspector">
      <h2>요소 {chosen.length}개 선택</h2>
      <p className="flow-multi-names">{chosen.map((r) => r.label).join(', ')}</p>
      <div className="flow-inspector-box">
        <h3>정렬 (선택한 요소끼리)</h3>
        <div className="flow-align-grid">
          {alignment.map(([cmd, name]) => (
            <button type="button" key={cmd} disabled={count < 2} onClick={() => exec(cmd)}>
              {name}
            </button>
          ))}
        </div>
        <div className="flow-inspector-buttons">
          <button type="button" disabled={count < 3} onClick={() => exec('horizontal')}>
            가로 간격 같게
          </button>
          <button type="button" disabled={count < 3} onClick={() => exec('vertical')}>
            세로 간격 같게
          </button>
        </div>
        <small>간격 맞추기는 3개 이상일 때 씁니다.</small>
        {locked && <small className="flow-warn">위치가 잠긴 요소는 정렬·이동에서 빠집니다.</small>}
      </div>
      <div className="flow-inspector-buttons">
        <button type="button" onClick={() => exec('group')}>
          묶기 (⌘G)
        </button>
        <button
          type="button"
          disabled={!chosen.some((r) => typeof r.occurrence.node.group === 'string')}
          onClick={() => exec('ungroup')}
        >
          묶음 풀기 (⇧⌘G)
        </button>
        <button type="button" onClick={() => exec(locked ? 'unlock' : 'lock')}>
          {locked ? '잠금 해제' : '위치 잠금'}
        </button>
        <button type="button" onClick={() => exec('duplicate')}>
          복제 (⌘D)
        </button>
        <button type="button" className="flow-danger-outline" onClick={() => exec('delete')}>
          모두 삭제
        </button>
      </div>
    </section>
  );
}
