import { type FlowDesignBlock, type DesignObject } from '../document/designBlock';
import { type DesignAddress, designAddressKey } from './designAddress';
import {
  containerChildren,
  replaceContainerChildren,
  type DesignLayerContainer,
  copyDesignSiblings,
  cloneCanvasClipboard,
  type DesignClipboard,
} from './editDesignSelection';
import {
  editDesignCollection,
  editDesignNode,
  findDesignNode,
  object,
  records,
  sourceBlock,
  stringValue,
  type DesignEditScope,
  ownedChildren,
  readAddress,
} from './editDesign';
import { applyNodeChange } from './designChanges';

/** Only the screen's own editable children can be removed or copied as canonical nodes. Instance internals use local overrides. */
export function childParent(
  blocks: readonly FlowDesignBlock[],
  screen: string,
  addresses: readonly DesignAddress[],
): DesignLayerContainer | undefined {
  if (!addresses.length || addresses.some((a) => a.owner !== screen || a.via?.length)) return;
  const root = object(sourceBlock(blocks, screen, 'screen')?.design.root);
  const inverse = new Set(addresses.map((a) => a.node));
  const stack = root ? [root] : [];
  while (stack.length) {
    const n = stack.pop();
    if (!n) break;
    const parent = { owner: screen, node: stringValue(n.id) };
    const locations: DesignLayerContainer[] = [
      parent,
      ...Object.keys(object(n.slots) ?? {}).map((slot) => ({ ...parent, slot })),
    ];
    const present = locations.find(
      (place) =>
        containerChildren(n, place).filter((c) => inverse.has(stringValue(c.id))).length === inverse.size,
    );
    if (present) return present;
    stack.push(...ownedChildren(n));
  }
}
export function connectedAnnotations(
  blocks: readonly FlowDesignBlock[],
  scope: DesignEditScope,
  addresses: readonly DesignAddress[],
) {
  const ids = new Set(addresses.map(designAddressKey)),
    owned = new Set<string>();
  for (const a of addresses) {
    if ((a.owner !== scope.screen && a.owner !== scope.state) || a.via?.length) continue;
    const n = findDesignNode(blocks, a);
    const pending = n ? [n] : [];
    while (pending.length) {
      const next = pending.pop();
      if (!next) break;
      owned.add(designAddressKey({ owner: a.owner, node: stringValue(next.id) }));
      pending.push(...ownedChildren(next));
    }
  }
  function affected(a: DesignAddress) {
    return (
      ids.has(designAddressKey(a)) ||
      owned.has(designAddressKey({ owner: a.owner, node: a.node })) ||
      a.via?.some((hop) =>
        owned.has(
          designAddressKey({ owner: hop.owner, node: 'instance' in hop ? hop.instance : hop.repeat }),
        ),
      )
    );
  }
  return blocks.filter(
    (b) =>
      b.design.kind === 'annotation' &&
      b.design.screen === scope.screen &&
      (!scope.state || !b.design.state || b.design.state === scope.state) &&
      affectedSafe(b.design.target),
  );
  function affectedSafe(v: DesignObject[string] | undefined) {
    const a = readAddress(v);
    return a && affected(a);
  }
}

export function removeCanvasLayers(
  blocks: readonly FlowDesignBlock[],
  scope: DesignEditScope,
  addresses: readonly DesignAddress[],
) {
  let next = [...blocks];
  const deleted = new Set<string>();
  for (const address of addresses) {
    const state = scope.state && sourceBlock(next, scope.state, 'state');
    if (state && address.owner === state.design.id && !address.via?.length) {
      const changes = records(state.design.changes);
      const added = changes.find((op) => op.op === 'insert' && object(op.node)?.id === address.node);
      if (added) {
        for (const ann of connectedAnnotations(next, scope, [address])) deleted.add(ann.id);
        const affected = new Set<string>();
        function collect(node: DesignObject) {
          affected.add(stringValue(node.id));
          ownedChildren(node).forEach(collect);
        }
        collect(object(added.node) ?? {});
        const inside = (a: DesignAddress | undefined) =>
          a &&
          ((a.owner === address.owner && affected.has(a.node)) ||
            a.via?.some(
              (h) => h.owner === address.owner && affected.has('instance' in h ? h.instance : h.repeat),
            ));
        const kept = changes.filter(
          (op) =>
            op !== added && !inside(readAddress(op.target)) && !inside(readAddress(object(op.at)?.parent)),
        );
        next = next.map((b) => (b === state ? { ...b, design: { ...b.design, changes: kept } } : b));
        continue;
      }
    }
    const parent = !scope.state && childParent(next, scope.screen, [address]);
    if (parent) {
      for (const ann of connectedAnnotations(next, scope, [address])) deleted.add(ann.id);
      next = editDesignCollection(next, parent, (n) =>
        replaceContainerChildren(
          n,
          parent,
          containerChildren(n, parent).filter((c) => c.id !== address.node),
        ),
      );
    } else {
      if (!scope.state && !address.via?.length) throw Error('화면의 루트나 필수 구조는 삭제할 수 없습니다.');
      next = editDesignNode(next, scope, address, { kind: 'leaf', path: 'visible', value: false });
    }
  }
  return next.filter((b) => !deleted.has(b.id));
}

export function duplicateCanvasLayers(
  blocks: readonly FlowDesignBlock[],
  parent: DesignLayerContainer,
  clip: DesignClipboard,
  allocate: () => string,
  offset: number,
  rename: boolean,
  scope?: DesignEditScope,
): { blocks: FlowDesignBlock[]; addresses: DesignAddress[] } {
  const state = scope?.state,
    screen = scope?.screen ?? parent.owner;
  const { nodes, annotations } = cloneCanvasClipboard(
    clip,
    state ?? parent.owner,
    screen,
    allocate,
    offset,
    rename,
    state,
  );
  const addresses = nodes.map((n) => ({ owner: state ?? parent.owner, node: stringValue(n.id) }));
  if (state) {
    const target = sourceBlock(blocks, state, 'state');
    if (!target || target.design.screen !== screen) throw Error('붙여넣을 상태를 찾을 수 없습니다.');
    const at = {
      parent: {
        owner: parent.owner,
        node: parent.node,
        ...(parent.via?.length ? { via: parent.via.map((v) => ({ ...v })) } : {}),
      },
      edge: 'last',
    };
    const inserts = nodes.map((node) => ({ op: 'insert', at, node }));
    const next = blocks.map((b) =>
      b === target
        ? { ...b, design: { ...b.design, changes: [...records(b.design.changes), ...inserts] } }
        : b,
    );
    return { blocks: [...next, ...annotations], addresses };
  }
  const next = editDesignCollection(blocks, parent, (n) =>
    replaceContainerChildren(n, parent, [...containerChildren(n, parent), ...nodes]),
  );
  return { blocks: [...next, ...annotations], addresses };
}

export function copyCanvasLayers(
  blocks: readonly FlowDesignBlock[],
  sc: string,
  addresses: readonly DesignAddress[],
) {
  const parent = childParent(blocks, sc, addresses);
  if (!parent) throw Error('같은 상자에 속한 화면 요소를 선택하세요.');
  return copyDesignSiblings(
    blocks,
    parent,
    addresses.map((a) => a.node),
  );
}

/** Copy the rendered state's own overrides into a detached copy without changing the shared component or the original state. */
export function copyCanvasStateLayers(
  blocks: readonly FlowDesignBlock[],
  scope: DesignEditScope,
  addresses: readonly DesignAddress[],
): DesignClipboard {
  if (!scope.state) return copyCanvasLayers(blocks, scope.screen, addresses);
  const state = sourceBlock(blocks, scope.state, 'state');
  const owner = addresses[0]?.owner;
  if (
    !state ||
    state.design.screen !== scope.screen ||
    !owner ||
    ![scope.screen, scope.state].includes(owner) ||
    addresses.some((a) => a.owner !== owner || a.via?.length)
  ) {
    throw Error('복사하려는 화면 요소나 인스턴스를 선택하세요.');
  }
  const changes = records(state.design.changes);
  const ann = connectedAnnotations(blocks, scope, addresses);
  const nodeIds = new Set<string>();
  function copyNode(source: DesignObject): DesignObject {
    const id = stringValue(source.id);
    nodeIds.add(id);
    let node = source;
    for (const ch of changes) {
      const target = readAddress(ch.target);
      if (!target) continue;
      if (target.owner === owner && target.node === id && !target.via?.length) {
        if (ch.op === 'slot') {
          const prop = stringValue(ch.prop),
            assignment = object(ch.assignment);
          const slots = Object.fromEntries(
            Object.entries(object(node.slots) ?? {}).filter(([key]) => key !== prop),
          );
          if (assignment?.children) slots[prop] = assignment.children;
          node = { ...node, slots };
        } else node = applyNodeChange(node, ch, (v) => v);
      } else {
        const first = target.via?.[0];
        if (
          first &&
          'instance' in first &&
          first.owner === owner &&
          first.instance === id &&
          ch.op !== 'move'
        ) {
          const tail = target.via.slice(1);
          const local = {
            owner: target.owner,
            node: target.node,
            ...(tail.length ? { via: tail.map((h) => ({ ...h })) } : {}),
          };
          node = { ...node, overrides: [...records(node.overrides), { ...ch, target: local }] };
        }
      }
    }
    for (const field of ['children', 'operands', 'template', 'fallback']) {
      if (Array.isArray(node[field])) node = { ...node, [field]: records(node[field]).map(copyNode) };
    }
    const mask = object(node.source);
    if (mask) node = { ...node, source: copyNode(mask) };
    const slots = object(node.slots);
    if (slots)
      node = {
        ...node,
        slots: Object.fromEntries(
          Object.entries(slots).map(([name, children]) => [name, records(children).map(copyNode)]),
        ),
      };
    return node;
  }
  const nodes = addresses.map((address) => {
    const raw = findDesignNode(blocks, address);
    if (!raw) throw Error('선택한 요소가 바뀌었습니다.');
    return copyNode(raw);
  });
  const affects = (address: DesignAddress | undefined) =>
    address &&
    ((address.owner === owner && nodeIds.has(address.node)) ||
      address.via?.some((h) => h.owner === owner && 'instance' in h && nodeIds.has(h.instance)));
  if (
    changes.some(
      (ch) =>
        (ch.op === 'insert' || ch.op === 'move') &&
        (affects(readAddress(object(ch.at)?.parent)) ||
          (ch.op === 'move' &&
            affects(readAddress(ch.target)) &&
            !addresses.some((a) => ch.target && a.node === readAddress(ch.target)?.node))),
    )
  ) {
    throw Error('상태에서 위치가 재구성된 하위 트리는 구성 요소 단위로 복사하세요.');
  }
  const localTargets = new Set(
    ann.filter((b) => b.design.state === scope.state).map((b) => JSON.stringify(b.design.target)),
  );
  const annotations = ann
    .filter((b) => b.design.state || !localTargets.has(JSON.stringify(b.design.target)))
    .map((b) => b.design);
  return { owner, nodes, annotations };
}
