import type { Node as PMNode } from '@tiptap/pm/model';
import { Plugin, PluginKey } from '@tiptap/pm/state';
import { Decoration, DecorationSet } from '@tiptap/pm/view';
import type { Anchor, Thread } from '../../../../../domain/thread/thread';
import { textRange } from '../../../../../domain/thread/thread';
import { findBlock, offsetToPos } from '../positions';

export type ThreadMark = {
  readonly threadId: string;
  readonly anchor: Anchor;
  /** Current text range (server-resolved) for text anchors; null when detached. */
  readonly range: { readonly start: number; readonly end: number } | null;
};

export type ThreadMarksInput = { readonly marks: readonly ThreadMark[]; readonly activeId: string | null };

export function threadMarksInput(threads: readonly Thread[], activeId: string | null): ThreadMarksInput {
  return {
    marks: threads
      .filter((thread) => thread.status !== 'resolved' || thread.id === activeId)
      .map((thread) => ({ threadId: thread.id, anchor: thread.anchor, range: textRange(thread) })),
    activeId,
  };
}

export const threadMarksKey = new PluginKey<DecorationSet>('threadMarks');

function cellDecoration(doc: PMNode, mark: ThreadMark, attrs: Record<string, string>): Decoration | null {
  if (mark.anchor.kind !== 'cell') return null;
  const { rowId, columnId } = mark.anchor;
  const table = findBlock(doc, mark.anchor.blockId);
  if (table === null || table.node.type.name !== 'table') return null;
  const columns = (table.node.attrs.columns ?? []) as readonly { id: string }[];
  const columnIndex = columns.findIndex((column) => column.id === columnId);
  let decoration: Decoration | null = null;
  table.node.forEach((row, rowOffset) => {
    if (row.attrs.id !== rowId || columnIndex < 0 || columnIndex >= row.childCount) return;
    let cellPos = table.pos + 1 + rowOffset + 1;
    for (let index = 0; index < columnIndex; index++) cellPos += row.child(index).nodeSize;
    decoration = Decoration.node(cellPos, cellPos + row.child(columnIndex).nodeSize, {
      ...attrs,
      class: `${attrs.class ?? ''} comment-cell`,
    });
  });
  return decoration;
}

function decorationFor(doc: PMNode, mark: ThreadMark, active: boolean): Decoration | null {
  const attrs = { class: active ? 'comment-active' : '', 'data-thread-id': mark.threadId };
  const anchor = mark.anchor;
  if (anchor.kind === 'document') return null;
  if (anchor.kind === 'cell') return cellDecoration(doc, mark, attrs);
  const block = findBlock(doc, anchor.blockId);
  if (block === null) return null;
  if (anchor.kind === 'block' || mark.range === null || !block.node.isTextblock) {
    return Decoration.node(block.pos, block.pos + block.node.nodeSize, {
      ...attrs,
      class: `${attrs.class} comment-block`,
    });
  }
  const from = offsetToPos(block.node, block.pos, mark.range.start);
  const to = offsetToPos(block.node, block.pos, mark.range.end);
  if (to <= from) return null;
  return Decoration.inline(from, to, { ...attrs, class: `${attrs.class} comment-mark`, nodeName: 'span' });
}

function build(doc: PMNode, input: ThreadMarksInput): DecorationSet {
  const decorations = input.marks
    .map((mark) => decorationFor(doc, mark, mark.threadId === input.activeId))
    .filter((decoration): decoration is Decoration => decoration !== null);
  return DecorationSet.create(doc, decorations);
}

/**
 * Comment highlights. They are rebuilt from server ranges when threads change and otherwise mapped
 * through edits, so a highlight follows its text while the user keeps typing.
 */
export function threadMarksPlugin(): Plugin<DecorationSet> {
  return new Plugin<DecorationSet>({
    key: threadMarksKey,
    state: {
      init: () => DecorationSet.empty,
      apply(tr, set) {
        const input = tr.getMeta(threadMarksKey) as ThreadMarksInput | undefined;
        if (input !== undefined) return build(tr.doc, input);
        return set.map(tr.mapping, tr.doc);
      },
    },
    props: {
      decorations: (state) => threadMarksKey.getState(state),
    },
  });
}
