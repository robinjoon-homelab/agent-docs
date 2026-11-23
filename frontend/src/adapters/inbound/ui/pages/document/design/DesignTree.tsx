import { useState } from 'react';
import { type DesignAddress, designAddressKey } from '../../../../../../domain/flow/designAddress';
import {
  object,
  reorderDesignChild,
  sameAddress,
  stringValue,
  type DesignEditScope,
} from '../../../../../../domain/flow/editDesign';
import type { DesignRow } from './designLocation';
import { lookupRow } from './designLocation';
import type { DesignMutation } from './DesignNodeInspector';
export function DesignTree({
  rows,
  selected,
  editable,
  scope,
  mutate,
  select,
}: {
  rows: readonly DesignRow[];
  selected?: DesignAddress;
  editable: boolean;
  scope: DesignEditScope;
  mutate: DesignMutation;
  select: (a: DesignAddress) => void;
}) {
  const [filter, setFilter] = useState(''),
    [drag, setDrag] = useState<DesignAddress | null>(null);
  const byKey = new Map(rows.map((r) => [designAddressKey(r.address), r]));
  function movable(row: DesignRow) {
    const p = row.parent && byKey.get(designAddressKey(row.parent));
    return (
      editable &&
      !!p &&
      row.occurrence.node.locked !== true &&
      ['row', 'column', 'grid'].includes(stringValue(object(p.occurrence.node.layout)?.mode)) &&
      (!!scope.state ||
        (p.address.owner === scope.screen &&
          !p.address.via?.length &&
          row.address.owner === scope.screen &&
          !row.address.via?.length))
    );
  }
  function accepts(r: DesignRow) {
    const from = lookupRow(rows, drag ?? undefined);
    return (
      movable(r) &&
      !!from?.parent &&
      !!r.parent &&
      !sameAddress(r.address, from.address) &&
      sameAddress(r.parent, from.parent)
    );
  }
  return (
    <>
      <h3>레이어</h3>
      <input
        aria-label="레이어 찾기"
        placeholder="이름 또는 ID 검색"
        value={filter}
        onChange={(e) => setFilter(e.target.value)}
      />
      <div className="design-tree-scroll">
        {rows
          .filter((r) =>
            (r.label + ' ' + r.address.node).toLocaleLowerCase().includes(filter.toLocaleLowerCase()),
          )
          .map((r) => (
            <button
              type="button"
              key={designAddressKey(r.address)}
              aria-pressed={!!selected && sameAddress(selected, r.address)}
              title={r.label + ' · ' + r.address.node}
              style={{ paddingInlineStart: 8 + r.depth * 10 }}
              onClick={() => select(r.address)}
              draggable={movable(r)}
              onDragStart={(e) => {
                if (!movable(r)) {
                  e.preventDefault();
                  return;
                }
                setDrag(r.address);
                e.dataTransfer.effectAllowed = 'move';
                e.dataTransfer.setData('text/plain', r.address.node);
              }}
              onDragEnd={() => setDrag(null)}
              onDragOver={(e) => {
                if (accepts(r)) {
                  e.preventDefault();
                  e.dataTransfer.dropEffect = 'move';
                }
              }}
              onDrop={(e) => {
                if (!drag || !r.parent || !accepts(r)) return;
                e.preventDefault();
                const parent = r.parent,
                  source = drag,
                  rect = e.currentTarget.getBoundingClientRect(),
                  before = e.clientY < rect.top + rect.height / 2;
                mutate((bs) => reorderDesignChild(bs, scope, parent, source, r.address, before));
                setDrag(null);
              }}
            >
              {r.label}
            </button>
          ))}
      </div>
    </>
  );
}
