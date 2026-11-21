import type { FlowElementBlock } from '../../../../../domain/document/block';
import type { alignElements } from '../../../../../domain/flow/flow';
export function FlowMultiInspector({
  elements,
  onAlign,
  onGroup,
  onUngroup,
  onDuplicate,
  onDelete,
}: {
  elements: readonly FlowElementBlock[];
  onAlign: (operation: Parameters<typeof alignElements>[2]) => void;
  onGroup: () => void;
  onUngroup: () => void;
  onDuplicate: () => void;
  onDelete: () => void;
}) {
  const labels = {
    left: '왼쪽 맞춤',
    center: '가로 가운데',
    right: '오른쪽 맞춤',
    top: '위 맞춤',
    middle: '세로 가운데',
    bottom: '아래 맞춤',
  } as const;
  return (
    <div className="flow-inspector">
      <h2>요소 {elements.length}개 선택</h2>
      <p className="flow-multi-names">{elements.map((e) => e.label).join(', ')}</p>
      <div className="flow-inspector-box">
        <h3>정렬 (선택한 요소끼리)</h3>
        <div className="flow-align-grid">
          {Object.entries(labels).map(([key, label]) => (
            <button key={key} onClick={() => onAlign(key as keyof typeof labels)}>
              {label}
            </button>
          ))}
        </div>
        <div className="flow-inspector-buttons">
          <button
            disabled={elements.filter((e) => !e.pinned).length < 3}
            onClick={() => onAlign('horizontal')}
          >
            가로 간격 같게
          </button>
          <button disabled={elements.filter((e) => !e.pinned).length < 3} onClick={() => onAlign('vertical')}>
            세로 간격 같게
          </button>
        </div>
        <small>간격 맞추기는 3개 이상일 때 씁니다.</small>
        {elements.some((e) => e.pinned) && (
          <small className="flow-warn">위치가 잠긴 요소는 정렬·이동에서 빠집니다.</small>
        )}
      </div>
      <div className="flow-inspector-buttons">
        <button onClick={onGroup}>묶기 (⌘G)</button>
        <button disabled={!elements.some((e) => e.group)} onClick={onUngroup}>
          묶음 풀기 (⇧⌘G)
        </button>
        <button onClick={onDuplicate}>복제 (⌘D)</button>
        <button className="flow-danger-outline" onClick={onDelete}>
          모두 삭제
        </button>
      </div>
      <p className="flow-multi-hint">
        Shift+클릭으로 더하거나 빼고, 빈 곳을 끌어 범위로 고릅니다(잠긴 요소는 범위 선택에서 빠집니다). 선택한
        요소를 함께 끌어 옮기고 화살표 키로 움직입니다. ⌘A 모두 선택, ⌘C·⌘V 복사·붙여넣기, Delete 삭제, ⌘Z
        실행 취소.
      </p>
    </div>
  );
}
