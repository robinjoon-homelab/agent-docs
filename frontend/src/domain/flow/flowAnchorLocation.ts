import type { Block, FlowDesignBlock } from '../document/block';
import { designAddress, type DesignAddress } from './designAddress';

/** Storage IDs select an immutable comment anchor. Portable design IDs select the layer it describes. */
export type FlowAnchorLocation = {
  readonly kind: 'screen' | 'state' | 'element';
  readonly screenBlockId: string;
  readonly screenId: string;
  readonly stateBlockId?: string;
  readonly stateId?: string;
  readonly stateName?: string;
  readonly elementId?: string;
  readonly address?: DesignAddress;
  readonly code: string;
  readonly screenName: string;
  readonly name: string;
  readonly description: string;
};

export function designBlocks(blocks: readonly Block[]): FlowDesignBlock[] {
  return blocks.filter((block) => block.type === 'flowDesign');
}

const text = (value: unknown): string => (typeof value === 'string' ? value : '');

export function flowAnchorLocation(
  blocks: readonly Block[],
  blockId: string,
  viewedStateId?: string,
): FlowAnchorLocation | undefined {
  const index = blocks.findIndex((b) => b.id === blockId);
  const block = blocks[index];
  if (!block || (viewedStateId && (block.type !== 'flowDesign' || block.design.kind !== 'annotation')))
    return;
  if (block.type === 'flowDesign') {
    const d = block.design;
    if (d.kind !== 'screen' && d.kind !== 'state' && d.kind !== 'annotation') return;
    const library = designBlocks(blocks);
    const screen =
      d.kind === 'screen'
        ? block
        : library.find((b) => b.design.kind === 'screen' && b.design.id === d.screen);
    if (!screen) return;
    const state =
      d.kind === 'state'
        ? block
        : library.find(
            (b) => b.design.kind === 'state' && b.design.screen === d.screen && b.design.id === d.state,
          );
    if (d.kind === 'annotation' && d.state && !state) return;
    const viewed = viewedStateId
      ? library.find(
          (b) => b.id === viewedStateId && b.design.kind === 'state' && b.design.screen === screen.design.id,
        )
      : undefined;
    if (viewedStateId && (!viewed || (state && viewed.id !== state.id))) return;
    const active = viewed ?? state;
    const target = d.kind === 'annotation' ? designAddress(d.target) : undefined;
    return {
      kind: d.kind === 'annotation' ? 'element' : d.kind,
      screenBlockId: screen.id,
      screenId: text(screen.design.id),
      ...(active
        ? { stateBlockId: active.id, stateId: text(active.design.id), stateName: text(active.design.name) }
        : {}),
      ...(target ? { elementId: target.node, address: target } : {}),
      code: text(screen.design.code),
      screenName: text(screen.design.name),
      name: text(d.name),
      description: text(d.description),
    };
  }
  if (block.type !== 'flowScreen' && block.type !== 'flowElement') return;
  const screen =
    block.type === 'flowScreen'
      ? block
      : blocks
          .slice(0, index)
          .reverse()
          .find((b) => b.type === 'flowScreen');
  if (!screen) return;
  return {
    kind: block.type === 'flowScreen' ? 'screen' : 'element',
    screenBlockId: screen.id,
    screenId: screen.id,
    code: screen.screenId,
    screenName: screen.name,
    ...(block.type === 'flowElement' ? { elementId: block.id } : {}),
    name: block.type === 'flowElement' ? block.label : screen.name,
    description: block.desc,
  };
}

export type DesignLocation = { screen: string; state?: string; address?: DesignAddress };

export function designQuery(search: string, next: DesignLocation): string {
  const params = new URLSearchParams(search);
  for (const key of ['flowScreen', 'flowState', 'flowOwner', 'flowElement', 'flowVia']) params.delete(key);
  params.set('flowScreen', next.screen);
  if (next.state) params.set('flowState', next.state);
  if (next.address) {
    params.set('flowElement', next.address.node);
    params.set('flowOwner', next.address.owner);
    if (next.address.via?.length) params.set('flowVia', JSON.stringify(next.address.via));
  }
  return '?' + params.toString();
}

export function flowAnchorSearch(
  blocks: readonly Block[],
  blockId: string,
  search = '',
  viewedStateId?: string,
): string | undefined {
  const where = flowAnchorLocation(blocks, blockId, viewedStateId);
  if (!where) return;
  const design = designQuery(search, {
    screen: where.screenBlockId,
    state: where.stateBlockId,
    address: where.address,
  });
  if (!where.elementId || where.address) return design;
  const query = new URLSearchParams(design);
  query.set('flowElement', where.elementId);
  return '?' + query.toString();
}
