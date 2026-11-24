import type { FlowElementBlock, FlowKind } from '../document/block';
import {
  equalDesignValues,
  type DesignJson,
  type DesignObject,
  type FlowDesignBlock,
} from '../document/designBlock';
import { type DesignAddress, type DesignOccurrence, designAddressKey } from './designAddress';
import { resolveDesignScene } from './designScene';
import { designAnnotationIndex } from './designAnnotations';
import { ensureWireComponent, wireInputs } from './addDesignElement';
import { KINDS, newElement } from './flow';
import {
  type DesignEditScope,
  clearDesignPictureSlot,
  editDesignCollection,
  editDesignNode,
  findDesignNode,
  object,
  records,
  sameAddress,
  sourceBlock,
  stringValue,
} from './editDesign';

export function wireKind(node: DesignObject): FlowKind | undefined {
  if (node.kind !== 'instance' || typeof node.component !== 'string' || !node.component.startsWith('wire:'))
    return;
  const k = node.component.slice(5);
  return Object.hasOwn(KINDS, k) ? (k as FlowKind) : undefined;
}

export type AppearanceValue = Pick<FlowElementBlock, 'kind' | 'label' | 'value' | 'look' | 'date' | 'items'>;
export function wireAppearance(kind: FlowKind, values: DesignObject, name: string): AppearanceValue {
  return {
    kind,
    label: stringValue(values.label, name),
    value: stringValue(values.value),
    look: typeof values.look === 'string' ? values.look : null,
    date: typeof values.date === 'string' ? values.date : null,
    items: kind === 'list' ? records(values.items).map((i) => stringValue(object(i.fields)?.text)) : null,
  };
}

export function scopedAnnotation(
  blocks: readonly FlowDesignBlock[],
  screen: string,
  state: string | undefined,
  address: DesignAddress,
) {
  return designAnnotationIndex(blocks, screen, state).get(designAddressKey(address));
}

/** A change authored in a state has its own anchor; earlier comments stay attached to the base annotation. */
export function editScopedAnnotation(
  blocks: readonly FlowDesignBlock[],
  scope: DesignEditScope,
  target: DesignAddress,
  expectedId: string,
  allocate: () => string,
  edit: (blocks: readonly FlowDesignBlock[], id: string) => FlowDesignBlock[],
): FlowDesignBlock[] {
  const current = scopedAnnotation(blocks, scope.screen, scope.state, target);
  if (!current || current.id !== expectedId) throw Error('선택한 요소 설명이 바뀌었습니다. 다시 선택하세요.');
  if (!scope.state || current.design.state === scope.state)
    return edit(blocks, stringValue(current.design.id));
  if (sourceBlock(blocks, scope.state, 'state')?.design.screen !== scope.screen)
    throw Error('현재 상태를 찾을 수 없습니다.');
  const id = allocate();
  return edit(
    [...blocks, { type: 'flowDesign', id, design: { ...current.design, id, state: scope.state } }],
    id,
  );
}

export function patchAnnotation(
  blocks: readonly FlowDesignBlock[],
  scope: DesignEditScope,
  target: DesignAddress,
  expectedId: string,
  fields: DesignObject,
  allocate: () => string,
) {
  return editScopedAnnotation(blocks, scope, target, expectedId, allocate, (bs, id) =>
    bs.map((b) => (b.design.id === id ? { ...b, design: { ...b.design, ...fields } } : b)),
  );
}

/** Preserve unchanged row keys across inserts, deletes and reordering, including duplicate text values. */
export function wireListRows(
  previous: DesignJson | undefined,
  lines: readonly string[],
  allocate: () => string,
): DesignObject[] {
  const old = records(previous);
  const used = new Set<number>();
  const candidates = lines.map((text, i) => {
    if (old.length === lines.length && object(old[i]?.fields)?.text === text) {
      used.add(i);
      return i;
    }
    return -1;
  });
  lines.forEach((text, i) => {
    if (candidates[i] !== -1) return;
    const exact = old.findIndex((row, j) => !used.has(j) && object(row.fields)?.text === text);
    if (exact >= 0) used.add(exact);
    candidates[i] = exact;
  });
  return lines.map((text, i) => {
    let index = candidates[i] ?? -1;
    if (index < 0 && old.length === lines.length && old[i] && !used.has(i)) {
      index = i;
      used.add(i);
    }
    const row = old[index];
    return { ...(row ?? { itemKey: allocate() }), fields: { ...object(row?.fields), text } };
  });
}
function imageInside(layer: DesignOccurrence): DesignOccurrence | undefined {
  if (layer.node.kind === 'image') return layer;
  for (const child of layer.children) {
    const found = imageInside(child);
    if (found) return found;
  }
}
function layerAtAddress(root: DesignOccurrence | null, address: DesignAddress): DesignOccurrence | undefined {
  if (!root) return;
  if (sameAddress(root.address, address) || root.mounts.some((m) => sameAddress(m, address))) return root;
  for (const c of root.children) {
    const found = layerAtAddress(c, address);
    if (found) return found;
  }
  return root.maskSource ? layerAtAddress(root.maskSource, address) : undefined;
}

function wireImageAsset(value: string) {
  return value.match(/^asset:([0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12})$/i)?.[1];
}

function wireImage(id: string, asset: string, alt: string): DesignObject {
  return {
    kind: 'image',
    id,
    asset,
    alt,
    fit: 'contain',
    size: { x: { mode: 'fill' }, y: { mode: 'fill' } },
  };
}

export function replaceDesignImageAsset(
  blocks: readonly FlowDesignBlock[],
  scope: DesignEditScope,
  address: DesignAddress,
  expected: DesignJson | undefined,
  asset: string,
) {
  const node = scope.original
    ? findDesignNode(blocks, address)
    : layerAtAddress(resolveDesignScene(blocks, scope.screen, scope.state).root, address)?.node;
  if (!node || node.kind !== 'image' || !equalDesignValues(node.asset ?? null, expected ?? null))
    throw Error('편집 중 이미지가 바뀌었습니다. 다시 확인하세요.');
  return editDesignNode(blocks, scope, address, { kind: 'leaf', path: 'asset', value: asset });
}

export function editWireAppearance(
  blocks: readonly FlowDesignBlock[],
  scope: DesignEditScope,
  address: DesignAddress,
  expected: AppearanceValue,
  patch: Partial<AppearanceValue>,
  frame: { width: number; height: number },
  allocate: () => string,
): FlowDesignBlock[] {
  const node = findDesignNode(blocks, address);
  if (!node || wireKind(node) !== expected.kind) throw Error('요소 종류가 바뀌었습니다. 다시 선택하세요.');
  const scene = resolveDesignScene(blocks, scope.screen, scope.state);
  const values = (scope.original ? object(node.inputs) : scene.inputs.get(designAddressKey(address))) ?? {};
  const actual = wireAppearance(expected.kind, values, stringValue(node.name));
  for (const field of Object.keys(patch) as (keyof AppearanceValue)[]) {
    if (!equalDesignValues(actual[field], expected[field]))
      throw Error('편집 중 요소 값이 바뀌었습니다. 다시 확인하세요.');
  }
  let next = [...blocks];
  const input = (prop: string, value: DesignJson) => {
    next = editDesignNode(next, scope, address, { kind: 'input', prop, value });
  };
  if (patch.kind && patch.kind !== expected.kind) {
    if (
      (address.via?.length && !scope.original) ||
      (scope.state && address.owner !== scope.state && !scope.original)
    )
      throw Error('종류는 요소를 소유한 화면이나 원본에서 바꿀 수 있습니다.');
    next = [...ensureWireComponent(next, patch.kind, allocate)];
    const replacement = patch.kind;
    const defaults = newElement(address.node, replacement, frame.width, frame.height);
    const updated = wireInputs({ ...defaults, label: actual.label, value: actual.value });
    next = editDesignCollection(next, address, (n) => {
      const output: Record<string, DesignJson> = { ...n, component: 'wire:' + replacement, inputs: updated };
      // Slot children and overrides belong to the old component definition.
      delete output.slots;
      delete output.overrides;
      const image = replacement === 'image' ? wireImageAsset(actual.value) : undefined;
      if (image) output.slots = { picture: [wireImage(allocate(), image, actual.label)] };
      return output;
    });
    return next;
  }
  if (patch.label !== undefined) {
    input('label', patch.label);
    next = editDesignNode(next, scope, address, { kind: 'leaf', path: 'name', value: patch.label });
    next = editDesignNode(next, scope, address, { kind: 'leaf', path: 'accessibleName', value: patch.label });
    const ann = scopedAnnotation(next, scope.screen, scope.state, address);
    if (ann && !scope.original && patch.label.trim())
      next = patchAnnotation(next, scope, address, ann.id, { name: patch.label }, allocate);
  }
  if (patch.look) input('look', patch.look);
  if (patch.date !== undefined) {
    input('date', patch.date ?? '');
    input('dateLayout', patch.date ? 'dated' : 'undated');
  }
  if (patch.items) input('items', wireListRows(values.items, patch.items, allocate));
  if (patch.value !== undefined) input('value', patch.value);
  if (actual.kind === 'image' && (patch.label !== undefined || patch.value !== undefined)) {
    const layer = layerAtAddress(scene.root, address);
    const picture = layer && imageInside(layer);
    const asset = wireImageAsset(patch.value ?? actual.value);
    const alt = patch.label ?? actual.label;
    if (asset) {
      if (picture && findDesignNode(next, picture.address) && !scope.original) {
        if (picture.node.asset !== asset)
          next = editDesignNode(next, scope, picture.address, { kind: 'leaf', path: 'asset', value: asset });
        if (picture.node.alt !== alt)
          next = editDesignNode(next, scope, picture.address, { kind: 'leaf', path: 'alt', value: alt });
      } else {
        const id = scope.original
          ? stringValue(records(object(node.slots)?.picture)[0]?.id) || allocate()
          : allocate();
        const img = wireImage(id, asset, alt);
        next = editDesignNode(next, scope, address, { kind: 'slot', prop: 'picture', children: [img] });
      }
    } else if (patch.value !== undefined && (picture || object(node.slots)?.picture)) {
      next = clearDesignPictureSlot(next, scope, address, picture?.address);
    }
  }
  return next;
}
