import type { FlowElementBlock, FlowKind } from '../document/block';
import type { DesignObject, FlowDesignBlock } from '../document/designBlock';
import { newElement, type Rect } from './flow';
import { object, sourceBlock, stringValue } from './editDesign';
import { addDesignChild } from './editDesignStructure';
import templates from './wireComponentTemplates.json';

// These portable definitions are the same as LegacyWireComponents.definition used for migration.
// An existing document's definition always wins, including user edits to its original.
export function ensureWireComponent(
  blocks: readonly FlowDesignBlock[],
  kind: FlowKind,
  allocate: () => string,
) {
  const design: DesignObject = templates[kind] as DesignObject;
  const existing = sourceBlock(blocks, stringValue(design.id));
  if (existing && existing.design.kind !== 'component') throw Error('같은 ID를 사용하는 블록이 있습니다.');
  return existing ? blocks : [...blocks, { id: allocate(), type: 'flowDesign' as const, design }];
}

export function wireInputs(e: FlowElementBlock): DesignObject {
  return {
    label: e.label,
    value: e.value,
    ...(e.look ? { look: e.look } : {}),
    ...(e.items
      ? { items: e.items.map((text, index) => ({ itemKey: 'row-' + String(index), fields: { text } })) }
      : {}),
    ...(e.kind === 'path' ? { dateLayout: 'undated' } : {}),
  };
}

export function addDesignElement(
  blocks: readonly FlowDesignBlock[],
  scope: { screen: string; state?: string },
  kind: FlowKind,
  frame: { width: number; height: number },
  after: Rect | undefined,
  allocate: () => string,
) {
  const screen = sourceBlock(blocks, scope.screen, 'screen');
  const root = object(screen?.design.root);
  if (!root || root.kind !== 'box') throw Error('요소를 넣을 수 있는 디자인 화면을 선택하세요.');
  const bs = ensureWireComponent(blocks, kind, allocate);
  const e = newElement(allocate(), kind, frame.width, frame.height, after);
  const owner = scope.state ?? scope.screen;
  const target = { owner, node: e.id };
  const instance = {
    id: e.id,
    kind: 'instance',
    component: 'wire:' + kind,
    name: e.label,
    accessibleName: e.label,
    inputs: wireInputs(e),
    size: { x: { mode: 'fixed', px: e.w }, y: { mode: 'fixed', px: e.h } },
    position: { mode: 'absolute', x: { start: e.x }, y: { start: e.y } },
  };
  const next = addDesignChild(
    bs,
    scope.screen,
    scope.state,
    { owner: scope.screen, node: stringValue(root.id) },
    instance,
  );
  const annotation: FlowDesignBlock = {
    type: 'flowDesign',
    id: e.id,
    design: {
      id: e.id,
      kind: 'annotation',
      screen: scope.screen,
      ...(scope.state ? { state: scope.state } : {}),
      name: e.label,
      description: '',
      enabled: e.annotated,
      target,
    },
  };
  return { blocks: [...next, annotation], target };
}
