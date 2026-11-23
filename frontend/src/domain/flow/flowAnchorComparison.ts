import type { Block } from '../document/block';
import { equalDesignValues, type DesignJson, type DesignObject } from '../document/designBlock';
import type { Thread } from '../thread/thread';
import { designAddress, designAddressKey, type DesignAddress, type DesignOccurrence } from './designAddress';
import { resolveDesignScene } from './designScene';
import { asObject, asObjects } from './designValue';
import { designBlocks, flowAnchorLocation, type FlowAnchorLocation } from './flowAnchorLocation';
import { flowDifferences, flowTarget, type FlowDifference } from './flow';

export type FlowAnchorTarget = NonNullable<Thread['target']>;

function omit(fields: DesignObject, keys: readonly string[]): DesignObject {
  return Object.fromEntries(Object.entries(fields).filter(([key]) => !keys.includes(key)));
}

function occurrenceData(layer: DesignOccurrence): DesignObject {
  const children = layer.children.map((child) => ({
    key: designAddressKey(child.address),
    value: occurrenceData(child),
  }));
  // Freely placed siblings retain their anchors when only their overlap order changes.
  if (asObject(layer.node.layout).mode === 'free') children.sort((a, b) => a.key.localeCompare(b.key));
  return {
    node: omit(layer.node, ['group', 'locked']),
    children,
    ...(layer.maskSource ? { mask: occurrenceData(layer.maskSource) } : {}),
  };
}

function ancestry(layer: DesignOccurrence | null, address: DesignAddress): DesignOccurrence[] {
  if (!layer) return [];
  const key = designAddressKey(address);
  function visit(node: DesignOccurrence, parents: DesignOccurrence[]): DesignOccurrence[] {
    const chain = [...parents, node];
    if ([node.address, ...node.mounts].some((candidate) => designAddressKey(candidate) === key)) return chain;
    for (const child of node.maskSource ? [node.maskSource, ...node.children] : node.children) {
      const result = visit(child, chain);
      if (result.length) return result;
    }
    return [];
  }
  return visit(layer, []);
}

function appearance(root: DesignOccurrence | null, address: DesignAddress): DesignJson {
  const chain = ancestry(root, address);
  const target = chain.pop();
  if (!target) return null;
  const inherited = [
    'layout',
    'position',
    'size',
    'aspectRatio',
    'flexItem',
    'margin',
    'cell',
    'alignSelf',
    'themeMode',
    'localTransform',
    'visible',
  ];
  const styles = ['text', 'typography', 'transform', 'overflow', 'opacity'];
  return {
    ancestors: chain.map(({ node, address }) => ({
      address: designAddressKey(address),
      fields: Object.fromEntries(Object.entries(node).filter(([k]) => inherited.includes(k))),
      style: Object.fromEntries(Object.entries(asObject(node.style)).filter(([k]) => styles.includes(k))),
    })),
    target: occurrenceData(target),
  };
}

function signature(blocks: readonly Block[], where: FlowAnchorLocation, blockId: string): DesignJson {
  const data = designBlocks(blocks);
  const design = data.find((b) => b.id === blockId)?.design;
  if (!design) return null;
  if (where.kind === 'element') {
    const scene = resolveDesignScene(data, where.screenId, where.stateId);
    return { design, appearance: where.address ? appearance(scene.root, where.address) : null };
  }
  const scene = resolveDesignScene(data, where.screenId);
  if (where.kind === 'state') {
    const targets = asObjects(design.changes).flatMap((c) => {
      const at = asObject(c.at);
      return [c.target, at.parent, at.before, at.after].flatMap((raw) => {
        const a = designAddress(raw);
        return a ? [appearance(scene.root, a)] : [];
      });
    });
    return { design, targets };
  }
  const scoped = data.filter((b) => b.design.screen === where.screenId);
  return {
    design: omit(design, ['root']),
    appearance: scene.root ? occurrenceData(scene.root) : null,
    states: scoped.filter((b) => b.design.kind === 'state').map((b) => b.id),
    annotations: Object.fromEntries(
      scoped.filter((b) => b.design.kind === 'annotation' && !b.design.state).map((b) => [b.id, b.design]),
    ),
  };
}

/** The server uses the migration projection for cross-format comparisons. A stored result applies only to that version. */
export function flowAnchorTarget(
  before: readonly Block[],
  now: readonly Block[],
  id: string,
  thread?: Thread,
  currentVersion?: number,
): FlowAnchorTarget {
  if (thread?.target && currentVersion !== undefined && thread.targetVersion === currentVersion)
    return thread.target;
  if (before[0]?.type === 'flowSettings' && now[0]?.type === 'flowSettings')
    return flowTarget(before, now, id);
  const viewedStateId = thread?.anchor.kind === 'block' ? thread.anchor.viewedStateId : undefined;
  const old = flowAnchorLocation(before, id, viewedStateId);
  if (!old) return 'ok';
  const screen = flowAnchorLocation(now, old.screenBlockId);
  if (screen?.kind !== 'screen' || screen.screenId !== old.screenId) return 'screenDeleted';
  const current = flowAnchorLocation(now, id, viewedStateId);
  if (current?.screenBlockId !== old.screenBlockId || old.kind !== current.kind) return 'elementDeleted';
  if (before[0]?.type !== now[0]?.type) {
    return old.name === current.name && old.description === current.description ? 'ok' : 'changed';
  }
  const prior = signature(before, old, id),
    next = signature(now, current, id);
  if (old.kind === 'element' && asObject(prior).appearance && !asObject(next).appearance)
    return 'elementDeleted';
  return equalDesignValues(prior, next) ? 'ok' : 'changed';
}

export function flowAnchorDifferences(
  before: readonly Block[],
  now: readonly Block[],
  id: string,
  target: FlowAnchorTarget,
  viewedStateId?: string,
): FlowDifference[] {
  if (before[0]?.type === 'flowSettings' && now[0]?.type === 'flowSettings')
    return flowDifferences(before, now, id);
  if (target === 'ok') return [];
  const old = flowAnchorLocation(before, id, viewedStateId),
    latest = flowAnchorLocation(now, id, viewedStateId);
  if (!old) return [];
  const label = old.kind === 'screen' ? '화면' : old.kind === 'state' ? '상태' : '요소';
  if (target === 'screenDeleted') return [{ field: '화면', before: old.screenName, after: '삭제됨' }];
  if (
    target === 'elementDeleted' &&
    old.stateBlockId &&
    flowAnchorLocation(now, old.stateBlockId)?.screenBlockId !== old.screenBlockId
  )
    return [
      { field: '댓글을 달았던 상태', before: old.stateName ?? old.stateBlockId, after: '이 화면에 없음' },
    ];
  if (target === 'elementDeleted' || !latest) return [{ field: label, before: old.name, after: '삭제됨' }];
  const differences: FlowDifference[] = [];
  if (old.name !== latest.name)
    differences.push({ field: label + ' 이름', before: old.name, after: latest.name });
  if (old.description !== latest.description)
    differences.push({ field: '설명', before: old.description, after: latest.description });
  return differences;
}
