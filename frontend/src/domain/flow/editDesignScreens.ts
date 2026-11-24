import {
  isDesignObject,
  type DesignJson,
  type DesignObject,
  type FlowDesignBlock,
} from '../document/designBlock';
import type { FlowFrame } from '../document/block';
import { object, ownedChildren, records, sourceBlock, stringValue } from './editDesign';
import { createDesignBlock } from './editDesignStructure';

const whenScreen = (b: FlowDesignBlock) => b.design.kind === 'screen';

function screenFamily(blocks: readonly FlowDesignBlock[], screen: string) {
  const source = sourceBlock(blocks, screen, 'screen');
  if (!source) throw Error('화면을 찾을 수 없습니다.');
  const children = blocks.filter(
    (b) => b.design.screen === screen && (b.design.kind === 'annotation' || b.design.kind === 'state'),
  );
  return [source, ...children];
}

export function moveDesignScreen(blocks: readonly FlowDesignBlock[], screen: string, direction: -1 | 1) {
  const screens = blocks.filter(whenScreen);
  const i = screens.findIndex((b) => b.design.id === screen);
  const other = screens[i + direction];
  const here = screens[i];
  if (!here || !other) throw Error('화면을 이 방향으로 이동할 수 없습니다.');
  return blocks.map((b) => (b === here ? other : b === other ? here : b));
}

export function deleteDesignScreen(blocks: readonly FlowDesignBlock[], screen: string) {
  const screens = blocks.filter(whenScreen);
  const index = screens.findIndex((b) => b.design.id === screen);
  if (screens.length < 2) throw Error('마지막 화면은 삭제할 수 없습니다.');
  const removed = new Set(screenFamily(blocks, screen));
  const fallback = screens[index + 1] ?? screens[index - 1];
  if (!fallback) throw Error('돌아갈 화면을 찾을 수 없습니다.');
  // Keep incoming actions so their authors can find and repair the missing destination.
  return { blocks: blocks.filter((b) => !removed.has(b)), screen: fallback.id };
}

type IdScope = { copy: string; nodes: Map<string, string>; groups: Map<string, string> };

function addedNodes(change: DesignObject): DesignObject[] {
  const inserted = change.op === 'insert' ? object(change.node) : undefined;
  const assignment = change.op === 'slot' ? object(change.assignment) : undefined;
  return [...(inserted ? [inserted] : []), ...records(assignment?.children)];
}

/** Assign IDs to owned nodes before rewriting addresses. A component reached through an instance keeps its own ID scope. */
export function duplicateDesignScreen(
  blocks: readonly FlowDesignBlock[],
  screen: string,
  allocate: () => string,
) {
  const [source, ...children] = screenFamily(blocks, screen);
  if (!source) throw Error('화면을 찾을 수 없습니다.');
  const clonedId = allocate();
  const next = createDesignBlock(
    blocks,
    'screen',
    stringValue(source.design.name, '화면').slice(0, 97) + ' 사본',
    clonedId,
    screen,
  );
  const placeholder = sourceBlock(next, clonedId, 'screen');
  if (!placeholder) throw Error('복제한 화면을 찾을 수 없습니다.');
  const nodeIds = new WeakMap<DesignObject, string>();
  const scopes = new Map<string, IdScope>();
  function collect(node: DesignObject, scope: IdScope) {
    const id = stringValue(node.id);
    if (!id) return;
    const fresh = scope.nodes.get(id) ?? allocate();
    scope.nodes.set(id, fresh);
    nodeIds.set(node, fresh);
    if (typeof node.group === 'string' && !scope.groups.has(node.group))
      scope.groups.set(node.group, allocate());
    ownedChildren(node).forEach((child) => collect(child, scope));
    records(node.overrides)
      .flatMap(addedNodes)
      .forEach((child) => collect(child, scope));
  }
  for (const b of [source, ...children]) {
    const owner = stringValue(b.design.id);
    const scope: IdScope = {
      copy: b === source ? clonedId : allocate(),
      nodes: new Map(),
      groups: new Map(),
    };
    scopes.set(owner, scope);
    const root = object(b.design.root);
    if (root) collect(root, scope);
    records(b.design.changes)
      .flatMap(addedNodes)
      .forEach((child) => collect(child, scope));
  }
  function rewrite(value: DesignJson, scope: IdScope): DesignJson {
    if (Array.isArray(value)) return (value as readonly DesignJson[]).map((v) => rewrite(v, scope));
    if (!isDesignObject(value)) return value;
    const result: Record<string, DesignJson> = Object.fromEntries(
      Object.entries(value).map(([key, val]) => [key, rewrite(val, scope)]),
    );
    const assigned = nodeIds.get(value);
    if (assigned) {
      result.id = assigned;
      if (typeof value.group === 'string') result.group = scope.groups.get(value.group) ?? value.group;
    }
    const target = scopes.get(stringValue(value.owner));
    if (
      target &&
      (typeof value.node === 'string' ||
        typeof value.instance === 'string' ||
        typeof value.repeat === 'string')
    ) {
      result.owner = target.copy;
      for (const field of ['node', 'instance', 'repeat']) {
        if (typeof value[field] === 'string') result[field] = target.nodes.get(value[field]) ?? value[field];
      }
    }
    if (typeof value.repeat === 'string' && typeof value.field === 'string' && !value.owner) {
      result.repeat = scope.nodes.get(value.repeat) ?? value.repeat;
    }
    return result;
  }
  const copies = [source, ...children].map((b) => {
    const old = b.design;
    const scope = scopes.get(stringValue(old.id));
    if (!scope) throw Error('복제할 범위를 찾을 수 없습니다.');
    const id = scope.copy;
    const design: Record<string, DesignJson> = { ...object(rewrite(old, scope)), id };
    if (b === source) {
      design.name = stringValue(placeholder.design.name);
      design.code = stringValue(placeholder.design.code);
    } else {
      design.screen = clonedId;
      if (typeof old.state === 'string') design.state = scopes.get(old.state)?.copy ?? old.state;
      if (old.kind === 'annotation' && Array.isArray(old.actions)) {
        design.actions = records(old.actions).map((a) => {
          const to = object(a.to);
          if (to?.screen !== screen) return a;
          return {
            ...a,
            to: {
              ...to,
              screen: clonedId,
              ...(typeof to.state === 'string' ? { state: scopes.get(to.state)?.copy ?? to.state } : {}),
            },
          };
        });
      }
    }
    return { type: 'flowDesign' as const, id, design };
  });
  next.splice(next.indexOf(placeholder), 1, ...copies);
  return { blocks: next, screen: clonedId };
}

export function canSetNote(blocks: readonly FlowDesignBlock[], screen: FlowDesignBlock): boolean {
  if (screen.design.frame === 'note') return true;
  const root = object(screen.design.root);
  return (
    !!root &&
    !ownedChildren(root).length &&
    !blocks.some(
      (b) =>
        b.design.screen === screen.design.id && (b.design.kind === 'state' || b.design.kind === 'annotation'),
    )
  );
}

export function setScreenPreset(
  blocks: readonly FlowDesignBlock[],
  screen: string,
  preset: FlowFrame | 'custom' | 'note' | '',
  size: { width: number; height: number },
) {
  const target = sourceBlock(blocks, screen, 'screen');
  if (!target) throw Error('화면을 찾을 수 없습니다.');
  const fields: Record<string, DesignJson> = { ...target.design };
  if (preset === 'note') {
    if (!canSetNote(blocks, target))
      throw Error('요소·상태·주석이 없는 화면만 글 페이지로 바꿀 수 있습니다.');
    delete fields.root;
  } else if (!fields.root)
    fields.root = { kind: 'box', id: 'canvas', name: '화면', layout: { mode: 'free' }, children: [] };
  if (preset) fields.frame = preset === 'custom' ? size : preset;
  else delete fields.frame;
  return blocks.map((b) => (b === target ? { ...b, design: fields } : b));
}
