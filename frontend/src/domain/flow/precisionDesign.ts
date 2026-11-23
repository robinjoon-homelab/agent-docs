import type { DesignJson, DesignObject } from '../document/designBlock';
import { applyNodeChange } from './designChanges';
import { asObject, asObjects, asText } from './designValue';

export type PrecisionPart = { readonly collection: string; readonly id: string };
export type PrecisionPath = readonly PrecisionPart[];
export type PrecisionEdit =
  | { readonly op: 'leaf'; readonly path: string; readonly value: DesignJson }
  | {
      readonly op: 'detailSet';
      readonly parts: PrecisionPath;
      readonly field: string;
      readonly value: DesignJson;
    }
  | {
      readonly op: 'detailMove';
      readonly parts: PrecisionPath;
      readonly at: { before: string } | { after: string };
    }
  | { readonly op: 'detailRemove'; readonly parts: PrecisionPath }
  | {
      readonly op: 'detailInsert';
      readonly parents: PrecisionPath;
      readonly collection: string;
      readonly at: { edge: 'last' };
      readonly item: DesignObject;
    };

export type PrecisionGroup = {
  readonly collection: string;
  readonly members: readonly DesignObject[];
};

function group(collection: string, value: DesignJson | undefined): PrecisionGroup {
  const members = collection === 'paint' && value ? [asObject(value)] : asObjects(value);
  return { collection, members: members.filter((member) => typeof member.id === 'string') };
}

function rootGroups(node: DesignObject): PrecisionGroup[] {
  const style = asObject(node.style);
  const rich = asObject(node.text);
  const network = asObject(node.network);
  return [
    group('fills', style.fills),
    group('strokes', style.strokes),
    group('effects', style.effects),
    group('textFills', asObject(style.text).fills),
    group('runs', rich.runs),
    group('paragraphs', rich.paragraphs),
    group('contours', node.contours),
    group('vertices', network.vertices),
    group('segments', network.segments),
    group('regions', network.regions),
  ];
}

function memberGroups(collection: string, member: DesignObject): PrecisionGroup[] {
  switch (collection) {
    case 'strokes':
      return [group('paint', member.paint)];
    case 'fills':
    case 'textFills':
    case 'paint':
      return [group('stops', member.stops)];
    case 'contours':
      return [group('vertices', member.vertices)];
    case 'paragraphs':
      return [group('runs', member.runs), group('textFills', asObject(member.style).fills)];
    case 'runs':
      return [group('textFills', asObject(member.style).fills)];
    case 'regions':
      return [group('fills', member.fills)];
    default:
      return [];
  }
}

/** Identical point IDs in different contours refer to distinct parts. Each step keeps its owner. */
export function precisionSelection(node: DesignObject, parts: PrecisionPath) {
  let member: DesignObject = node;
  let groups = rootGroups(node);
  for (const part of parts) {
    const next = groups.find((g) => g.collection === part.collection)?.members.find((m) => m.id === part.id);
    if (!next) return undefined;
    member = next;
    groups = memberGroups(part.collection, member);
  }
  return { member, groups: groups.filter((g) => g.members.length > 0) };
}

export function precisionValue(member: DesignObject, field: string): DesignJson | undefined {
  return field.split('.').reduce<DesignJson | undefined>((value, key) => asObject(value)[key], member);
}

export const precisionSet = (parts: PrecisionPath, field: string, value: DesignJson): PrecisionEdit =>
  parts.length ? { op: 'detailSet', parts, field, value } : { op: 'leaf', path: field, value };

export function precisionSiblings(node: DesignObject, parts: PrecisionPath): readonly DesignObject[] {
  const step = parts.at(-1);
  return (
    precisionSelection(node, parts.slice(0, -1))?.groups.find((g) => g.collection === step?.collection)
      ?.members ?? []
  );
}

export type PaintStack = 'fills' | 'strokes' | 'effects';
export function availablePaintStacks(node: DesignObject): PaintStack[] {
  const style = asObject(node.style);
  const stacks: PaintStack[] = [];
  if (style.fill === undefined && style.backgroundPattern === undefined) stacks.push('fills');
  if (style.border === undefined) stacks.push('strokes');
  if (style.shadows === undefined) stacks.push('effects');
  return stacks;
}

export function insertPaintPart(stack: PaintStack, kind: 'default' | 'linear', id: string): PrecisionEdit {
  const solid = { id: 'color', kind: 'solid', color: '#334155' };
  let item: DesignObject;
  if (stack === 'effects') {
    item = { id, kind: 'dropShadow', color: '#00000040', x: 0, y: 2, blur: 4 };
  } else if (stack === 'strokes') {
    item = { id, paint: solid, width: 1, align: 'center' };
  } else if (kind === 'linear') {
    item = {
      id,
      kind: 'linear',
      stops: [
        { id: 'start', at: 0, color: '#FFFFFF' },
        { id: 'end', at: 1, color: '#334155' },
      ],
    };
  } else {
    item = { ...solid, id };
  }
  return { op: 'detailInsert', parents: [], collection: stack, at: { edge: 'last' }, item };
}

export function applyPrecisionEdit(node: DesignObject, edit: PrecisionEdit): DesignObject {
  if (edit.op === 'detailInsert') {
    const selection = precisionSelection(node, edit.parents);
    const id = asText(edit.item.id);
    if (!selection || !id) throw Error('추가할 세부 요소의 위치를 찾을 수 없습니다.');
    const siblings = selection.groups.find((g) => g.collection === edit.collection)?.members;
    if (siblings?.some((m) => m.id === id)) throw Error('이미 같은 ID의 세부 요소가 있습니다.');
  } else if (edit.op !== 'leaf') {
    if (!edit.parts.length || !precisionSelection(node, edit.parts))
      throw Error('선택한 세부 요소가 변경되었습니다.');
    if (edit.op === 'detailMove') {
      const target = 'before' in edit.at ? edit.at.before : edit.at.after;
      if (
        target === edit.parts.at(-1)?.id ||
        !precisionSiblings(node, edit.parts).some((m) => m.id === target)
      ) {
        throw Error('순서를 바꿀 기준 요소가 변경되었습니다.');
      }
    }
  }
  return applyNodeChange(node, edit, (v) => v);
}
