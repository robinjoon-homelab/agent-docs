import type { Node as PMNode } from '@tiptap/pm/model';
import { Plugin, PluginKey } from '@tiptap/pm/state';
import { Decoration, DecorationSet } from '@tiptap/pm/view';
import type { ListStyle } from '../../../../../domain/document/block';
import { listOrdinals } from '../../../../../domain/document/listOrdinals';

export type AgentMarks = { readonly labels: ReadonlyMap<string, string>; readonly fresh: boolean };

type State = { readonly agent: AgentMarks; readonly doc: PMNode | null; readonly set: DecorationSet };

export const blockDecorationsKey = new PluginKey<State>('blockDecorations');

function tableDecorations(table: PMNode, tablePos: number): Decoration[] {
  const columns = (table.attrs.columns ?? []) as readonly { align?: string }[];
  const decorations: Decoration[] = [];
  table.forEach((row, rowOffset) => {
    let cellPos = tablePos + 1 + rowOffset + 1;
    row.forEach((cell, _offset, index) => {
      const align = columns[index]?.align ?? 'none';
      if (align !== 'none')
        decorations.push(Decoration.node(cellPos, cellPos + cell.nodeSize, { 'data-align': align }));
      cellPos += cell.nodeSize;
    });
  });
  return decorations;
}

function build(doc: PMNode, agent: AgentMarks): DecorationSet {
  const blocks: { id: string; type: string; style?: ListStyle; depth?: number }[] = [];
  doc.forEach((node) => {
    blocks.push({
      id: String(node.attrs.id),
      type: node.type.name,
      style: node.attrs.style as ListStyle,
      depth: node.attrs.depth as number,
    });
  });
  const ordinals = listOrdinals(blocks);
  const decorations: Decoration[] = [];
  doc.forEach((node, pos) => {
    const id = String(node.attrs.id);
    const attrs: Record<string, string> = {};
    const ordinal = ordinals.get(id);
    if (ordinal !== undefined) attrs['data-ordinal'] = `${ordinal}.`;
    const label = agent.labels.get(id);
    if (label !== undefined) {
      attrs.class = agent.fresh ? 'agent-changed agent-fresh' : 'agent-changed';
      attrs.title = label;
    }
    if (Object.keys(attrs).length > 0) decorations.push(Decoration.node(pos, pos + node.nodeSize, attrs));
    if (node.type.name === 'table') decorations.push(...tableDecorations(node, pos));
  });
  return DecorationSet.create(doc, decorations);
}

/**
 * Decorations derived from the whole document: list numbering, column alignment and the agent
 * change marker. Rebuilt on every content change; documents are small enough for that.
 */
export function blockDecorationsPlugin(): Plugin<State> {
  const empty: AgentMarks = { labels: new Map(), fresh: false };
  return new Plugin<State>({
    key: blockDecorationsKey,
    state: {
      init: (_config, state) => ({ agent: empty, doc: state.doc, set: build(state.doc, empty) }),
      apply(tr, value, _old, state) {
        const agent = (tr.getMeta(blockDecorationsKey) as AgentMarks | undefined) ?? value.agent;
        if (agent === value.agent && value.doc === state.doc) return value;
        return { agent, doc: state.doc, set: build(state.doc, agent) };
      },
    },
    props: {
      decorations: (state) => blockDecorationsKey.getState(state)?.set,
    },
  });
}
