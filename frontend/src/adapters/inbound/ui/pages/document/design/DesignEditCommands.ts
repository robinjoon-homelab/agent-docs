import { useRef, type RefObject } from 'react';
import type { FlowDesignBlock } from '../../../../../../domain/document/designBlock';
import { designAddressKey, type DesignAddress } from '../../../../../../domain/flow/designAddress';
import {
  object,
  stringValue,
  sourceBlock,
  editDesignCollection,
  editDesignNode,
  clearCanvasGroup,
  sameAddress,
  reorderDesignChild,
  type DesignEditScope,
} from '../../../../../../domain/flow/editDesign';
import {
  type Arrange,
  markDesignSiblings,
  containerChildren,
  replaceContainerChildren,
  type DesignClipboard,
} from '../../../../../../domain/flow/editDesignSelection';
import {
  childParent,
  copyCanvasStateLayers,
  duplicateCanvasLayers,
  removeCanvasLayers,
} from '../../../../../../domain/flow/editCanvasChildren';
import { positionMovable, writeLayerRect } from '../../../../../../domain/flow/editCanvasGeometry';
import { alignElements } from '../../../../../../domain/flow/flow';
import { randomBlockId } from '../../../editor/ids';
import type { DesignMutation } from './useDesignEditHistory';
import { lookupRow, type DesignRow } from './designLocation';
import { measureLayers, selectedBoxes, selectableSiblings } from './designEditLayout';

export type CanvasCommand =
  | Arrange
  | 'front'
  | 'back'
  | 'duplicate'
  | 'copy'
  | 'paste'
  | 'group'
  | 'ungroup'
  | 'lock'
  | 'unlock'
  | 'delete';
export type DesignCanvasCommands = {
  execute: (command: CanvasCommand) => boolean;
  remove: (targets: readonly DesignAddress[]) => boolean;
};
export type EditCommandProps = {
  blocks: readonly FlowDesignBlock[];
  rows: readonly DesignRow[];
  scope: DesignEditScope;
  selection: readonly DesignAddress[];
  onSelect: (next: readonly DesignAddress[]) => void;
  mutate: DesignMutation;
  editable: boolean;
  onRequestDelete: (targets: readonly DesignAddress[]) => void;
  onMessage: (text: string) => void;
};
export function useEditCommands(host: RefObject<HTMLDivElement | null>, props: EditCommandProps) {
  const { blocks, rows, scope, selection, onSelect, mutate, editable, onRequestDelete, onMessage } = props;
  const clipboard = useRef<{ value: DesignClipboard; offset: number } | null>(null);
  function apply(change: Parameters<DesignMutation>[0]) {
    if (!editable) return false;
    const error = mutate(change);
    if (error) onMessage(error);
    return !error;
  }
  function remove(targets: readonly DesignAddress[]) {
    if (!targets.length) return false;
    const ok = apply((bs) => removeCanvasLayers(bs, scope, targets));
    if (ok) onSelect([]);
    return ok;
  }
  function execute(op: CanvasCommand) {
    if (!editable) return false;
    const first = selection[0];
    if (op === 'delete') {
      if (!first) return false;
      onRequestDelete(selection);
      return true;
    }
    if (!first && op !== 'paste') return false;
    if (op === 'copy') {
      try {
        clipboard.current = { value: copyCanvasStateLayers(blocks, scope, selection), offset: 16 };
        return true;
      } catch (e) {
        onMessage(e instanceof Error ? e.message : '복사할 수 없습니다.');
        return false;
      }
    }
    if (op === 'paste' || op === 'duplicate') {
      let copied: DesignAddress[] = [];
      const ok = apply((bs) => {
        const root = object(sourceBlock(bs, scope.screen)?.design.root);
        const parent =
          (scope.state ? lookupRow(rows, first)?.parent : childParent(bs, scope.screen, selection)) ??
          (root && { owner: scope.screen, node: stringValue(root.id) });
        if (!parent) throw Error('붙여넣을 화면을 선택하세요.');
        const clip =
          op === 'duplicate' ? copyCanvasStateLayers(bs, scope, selection) : clipboard.current?.value;
        if (!clip) throw Error('복사한 요소가 없습니다.');
        const data = duplicateCanvasLayers(
          bs,
          parent,
          clip,
          randomBlockId,
          op === 'duplicate' ? 16 : (clipboard.current?.offset ?? 16),
          op === 'duplicate',
          scope,
        );
        copied = data.addresses;
        return data.blocks;
      });
      if (ok) {
        if (op === 'paste' && clipboard.current) clipboard.current.offset += 16;
        onSelect(copied);
      }
      return ok;
    }
    if (op === 'front' || op === 'back' || op === 'group' || op === 'ungroup') {
      return apply((bs) => {
        const parent = childParent(bs, scope.screen, selection);
        const ids = selection.map((a) => a.node);
        const currentParent = lookupRow(rows, first)?.parent;
        if (
          !currentParent ||
          !selection.every((a) => {
            const p = lookupRow(rows, a)?.parent;
            return p && sameAddress(p, currentParent);
          })
        )
          throw Error('같은 상자에 속한 요소를 선택하세요.');
        if (op === 'group' || op === 'ungroup') {
          if (op === 'group' && ids.length < 2) throw Error('묶을 요소를 두 개 이상 선택하세요.');
          const group = op === 'group' ? randomBlockId() : null;
          if (!scope.state && parent) return markDesignSiblings(bs, parent, ids, 'group', group);
          return selection.reduce(
            (next, a) =>
              group
                ? editDesignNode(next, scope, a, { kind: 'leaf', path: 'group', value: group })
                : clearCanvasGroup(next, scope, a),
            [...bs],
          );
        }
        if (scope.state) {
          const siblings = rows.filter(
            (r) =>
              r.parent &&
              sameAddress(r.parent, currentParent) &&
              !selection.some((a) => sameAddress(a, r.address)),
          );
          const edge = op === 'front' ? siblings.at(-1) : siblings[0];
          if (!edge) return [...bs];
          let next = [...bs],
            sibling = edge.address;
          for (const a of op === 'front' ? selection : [...selection].reverse()) {
            next = reorderDesignChild(next, scope, currentParent, a, sibling, op === 'back');
            sibling = a;
          }
          return next;
        }
        if (!parent) {
          const siblings = rows.filter((r) => r.parent && sameAddress(r.parent, currentParent));
          const values = siblings
            .map((r) => object(r.occurrence.node.style)?.zIndex)
            .filter((n): n is number => typeof n === 'number');
          const level =
            op === 'front' ? Math.max(0, ...values) + 1 : Math.min(0, ...values) - selection.length;
          return selection.reduce(
            (next, a, i) =>
              editDesignNode(next, scope, a, { kind: 'leaf', path: 'style.zIndex', value: level + i }),
            [...bs],
          );
        }
        return editDesignCollection(bs, parent, (n) => {
          const children = containerChildren(n, parent),
            chosen = children.filter((x) => ids.includes(stringValue(x.id))),
            rest = children.filter((x) => !chosen.includes(x));
          return replaceContainerChildren(
            n,
            parent,
            op === 'front' ? [...rest, ...chosen] : [...chosen, ...rest],
          );
        });
      });
    }
    if (op === 'lock' || op === 'unlock') {
      return apply((bs) =>
        selection.reduce(
          (next, a) => editDesignNode(next, scope, a, { kind: 'leaf', path: 'locked', value: op === 'lock' }),
          [...bs],
        ),
      );
    }
    if (!host.current) return false;
    const boxes = selectedBoxes(measureLayers(host.current, rows), selection).filter(
      (b) => b.node.locked !== true,
    );
    if (
      boxes.length < (op === 'horizontal' || op === 'vertical' ? 3 : 2) ||
      !boxes.every((b) => b.parentElement === boxes[0]?.parentElement && positionMovable(b.node))
    )
      return false;
    const source = boxes.map((b) => ({ ...b.rect, id: designAddressKey(b.address) }));
    const aligned = alignElements(
      source,
      source.map((r) => r.id),
      op,
    );
    return apply((bs) =>
      boxes.reduce((next, b, i) => writeLayerRect(next, scope, b, aligned[i] ?? b.rect), [...bs]),
    );
  }
  function all() {
    if (!host.current || !editable) return;
    const boxes = measureLayers(host.current, rows),
      parent = selectedBoxes(boxes, selection)[0]?.row.parent ?? rows[0]?.address;
    onSelect(
      selectableSiblings(boxes, parent)
        .filter((b) => !b.node.locked)
        .map((b) => b.address),
    );
  }
  function nudge(dx: number, dy: number) {
    if (!host.current) return;
    const boxes = selectedBoxes(measureLayers(host.current, rows), selection).filter(
      (b) => b.node.locked !== true && positionMovable(b.node),
    );
    if (boxes.length)
      apply((bs) =>
        boxes.reduce(
          (next, b) => writeLayerRect(next, scope, b, { ...b.rect, x: b.rect.x + dx, y: b.rect.y + dy }),
          [...bs],
        ),
      );
  }
  return { execute, remove, all, nudge };
}
