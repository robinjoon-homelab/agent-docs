import type { Editor } from '@tiptap/core';
import { TextSelection } from '@tiptap/pm/state';
import { useEditorState } from '@tiptap/react';
import type { DragEvent, RefObject } from 'react';
import { useEffect, useRef, useState } from 'react';
import type { ColumnAlign } from '../../../../domain/document/block';
import type { Anchor } from '../../../../domain/thread/thread';
import { Icon } from '../common/Icon';
import type { TableOp } from './commands';
import { deleteBlockAt, insertParagraphAfter, moveBlock, runTableOp, startBlockDrag } from './commands';
import { blockAtPos, cellAt, savedRange } from './positions';

function relativeTop(container: HTMLElement | null, rect: { top: number }): number {
  return rect.top - (container?.getBoundingClientRect().top ?? 0);
}

function relativeLeft(container: HTMLElement | null, rect: { left: number }): number {
  return rect.left - (container?.getBoundingClientRect().left ?? 0);
}

/* Selection toolbar ----------------------------------------------------------------------------- */

export function SelectionToolbar({
  editor,
  container,
  editable,
  reanchoring,
  onComment,
  onReanchor,
}: {
  editor: Editor;
  container: RefObject<HTMLDivElement | null>;
  editable: boolean;
  reanchoring: boolean;
  onComment: () => void;
  onReanchor: () => void;
}) {
  const state = useEditorState({
    editor,
    selector: ({ editor: current }) => {
      const { selection } = current.state;
      if (!(selection instanceof TextSelection) || selection.empty) return null;
      if (editable && !current.isFocused) return null;
      const start = current.view.coordsAtPos(selection.from);
      return {
        top: relativeTop(container.current, start),
        left: relativeLeft(container.current, start),
        bold: current.isActive('bold'),
        italic: current.isActive('italic'),
        strike: current.isActive('strike'),
        code: current.isActive('code'),
        link: current.isActive('link'),
      };
    },
  });
  if (state === null) return null;

  const mark = (
    name: 'bold' | 'italic' | 'strike' | 'code',
    label: string,
    active: boolean,
    text: string,
  ) => (
    <button
      type="button"
      className={`toolbar-btn${active ? ' is-active' : ''}`}
      aria-pressed={active}
      aria-label={label}
      title={label}
      onClick={() => editor.chain().focus().toggleMark(name).run()}
    >
      {text}
    </button>
  );

  const toggleLink = (): void => {
    if (state.link) {
      editor.chain().focus().unsetLink().run();
      return;
    }
    const href = window.prompt('링크 주소 (문서 링크는 [[ 로 넣을 수 있습니다)', 'https://');
    if (href !== null && href.trim() !== '' && href.trim() !== 'https://') {
      editor.chain().focus().extendMarkRange('link').setLink({ href: href.trim() }).run();
    }
  };

  return (
    <div
      className="floating-toolbar"
      style={{ top: Math.max(state.top - 44, 0), left: Math.max(state.left - 8, 0) }}
      onMouseDown={(event) => event.preventDefault()}
      role="toolbar"
      aria-label="선택한 텍스트"
    >
      {reanchoring ? (
        <button type="button" className="toolbar-btn toolbar-primary" onClick={onReanchor}>
          여기에 다시 달기
        </button>
      ) : (
        <button type="button" className="toolbar-btn toolbar-primary" onClick={onComment} title="댓글 (⌘⌥M)">
          <Icon name="comment" size={14} />
          댓글
        </button>
      )}
      {editable ? (
        <>
          <span className="toolbar-sep" />
          {mark('bold', '굵게', state.bold, 'B')}
          {mark('italic', '기울임', state.italic, 'I')}
          {mark('strike', '취소선', state.strike, 'S')}
          {mark('code', '코드', state.code, '<>')}
          <button
            type="button"
            className={`toolbar-btn${state.link ? ' is-active' : ''}`}
            aria-label="링크"
            title="링크"
            onClick={toggleLink}
          >
            <Icon name="link" size={14} />
          </button>
        </>
      ) : null}
    </div>
  );
}

/* Block handle and cell comment button ------------------------------------------------------- */

type Hovered = { readonly pos: number; readonly top: number };
type HoveredCell = { readonly pos: number; readonly top: number; readonly left: number };

export function BlockHandle({
  editor,
  container,
  editable,
  onBlockComment,
  onCellComment,
  onCopyBlockLink,
}: {
  editor: Editor;
  container: RefObject<HTMLDivElement | null>;
  editable: boolean;
  onBlockComment: (anchor: Anchor) => void;
  onCellComment: (anchor: Anchor) => void;
  onCopyBlockLink: (blockId: string) => void;
}) {
  const [hovered, setHovered] = useState<Hovered | null>(null);
  const [cell, setCell] = useState<HoveredCell | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const element = container.current;
    if (element === null) return;
    const onMove = (event: MouseEvent): void => {
      if (menuOpen) return;
      const target = event.target as HTMLElement;
      if (target.closest('.block-handle, .cell-comment-btn') !== null) return;
      const view = editor.view;
      const bounds = view.dom.getBoundingClientRect();
      const found = view.posAtCoords({ left: bounds.left + 24, top: event.clientY });
      const block =
        found === null ? null : blockAtPos(view.state.doc, found.inside >= 0 ? found.inside : found.pos);
      const dom = block === null ? null : view.nodeDOM(block.pos);
      setHovered(
        block !== null && dom instanceof HTMLElement
          ? { pos: block.pos, top: relativeTop(element, dom.getBoundingClientRect()) }
          : null,
      );
      const td = target.closest('td');
      if (td !== null && view.dom.contains(td)) {
        const rect = td.getBoundingClientRect();
        setCell({
          pos: view.posAtDOM(td, 0),
          top: relativeTop(element, rect),
          left: relativeLeft(element, rect) + rect.width,
        });
      } else setCell(null);
    };
    const onLeave = (): void => {
      if (!menuOpen) setHovered(null);
      setCell(null);
    };
    element.addEventListener('mousemove', onMove);
    element.addEventListener('mouseleave', onLeave);
    return () => {
      element.removeEventListener('mousemove', onMove);
      element.removeEventListener('mouseleave', onLeave);
    };
  }, [container, editor, menuOpen]);

  useEffect(() => {
    if (!menuOpen) return;
    const close = (event: MouseEvent): void => {
      if (menuRef.current !== null && !menuRef.current.contains(event.target as Node)) setMenuOpen(false);
    };
    window.addEventListener('mousedown', close);
    return () => window.removeEventListener('mousedown', close);
  }, [menuOpen]);

  const block = hovered === null ? null : editor.state.doc.nodeAt(hovered.pos);
  const blockId = typeof block?.attrs.id === 'string' ? block.attrs.id : null;

  const startDrag = (event: DragEvent): void => {
    if (hovered !== null) startBlockDrag(editor, hovered.pos, event.dataTransfer);
  };

  const commentOnCell = (): void => {
    if (cell === null) return;
    const location = cellAt(editor.state, cell.pos + 1);
    const cellNode = editor.state.doc.nodeAt(cell.pos);
    if (location === null || cellNode === null) return;
    onCellComment({
      kind: 'cell',
      blockId: String(location.table.node.attrs.id),
      rowId: location.rowId,
      columnId: location.columnId,
      quote: savedRange(cellNode, 0, 0).text,
    });
  };

  return (
    <>
      {hovered === null || blockId === null ? null : (
        <div className="block-handle" style={{ top: hovered.top }} ref={menuRef}>
          {editable ? (
            <>
              <button
                type="button"
                className="handle-btn"
                aria-label="아래에 블록 추가"
                title="아래에 블록 추가"
                onClick={() => insertParagraphAfter(editor, hovered.pos, '/')}
              >
                <Icon name="plus" size={14} />
              </button>
              <button
                type="button"
                className="handle-btn handle-drag"
                draggable
                onDragStart={startDrag}
                onDragEnd={() => setHovered(null)}
                aria-label="끌어서 이동"
                title="끌어서 이동"
              >
                <Icon name="drag" size={14} />
              </button>
            </>
          ) : null}
          <button
            type="button"
            className="handle-btn"
            aria-label="블록 메뉴"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((value) => !value)}
          >
            <Icon name="more" size={14} />
          </button>
          {menuOpen ? (
            <div className="popover block-menu" role="menu">
              <button
                type="button"
                role="menuitem"
                className="menu-item"
                onClick={() => {
                  setMenuOpen(false);
                  onBlockComment({ kind: 'block', blockId });
                }}
              >
                <Icon name="comment" size={14} />
                블록에 댓글
              </button>
              <button
                type="button"
                role="menuitem"
                className="menu-item"
                onClick={() => {
                  setMenuOpen(false);
                  onCopyBlockLink(blockId);
                }}
              >
                <Icon name="link" size={14} />
                블록 링크 복사
              </button>
              {editable ? (
                <>
                  <div className="menu-separator" />
                  <button
                    type="button"
                    role="menuitem"
                    className="menu-item"
                    onClick={() => moveBlock(editor, hovered.pos, -1)}
                  >
                    위로 이동
                  </button>
                  <button
                    type="button"
                    role="menuitem"
                    className="menu-item"
                    onClick={() => moveBlock(editor, hovered.pos, 1)}
                  >
                    아래로 이동
                  </button>
                  <button
                    type="button"
                    role="menuitem"
                    className="menu-item danger"
                    onClick={() => {
                      setMenuOpen(false);
                      setHovered(null);
                      deleteBlockAt(editor, hovered.pos);
                    }}
                  >
                    <Icon name="trash" size={14} />
                    삭제
                  </button>
                </>
              ) : null}
            </div>
          ) : null}
        </div>
      )}
      {cell === null ? null : (
        <button
          type="button"
          className="cell-comment-btn"
          style={{ top: cell.top + 2, left: cell.left - 24 }}
          onMouseDown={(event) => event.preventDefault()}
          onClick={commentOnCell}
          aria-label="셀에 댓글"
          title="셀에 댓글"
        >
          <Icon name="comment" size={12} />
        </button>
      )}
    </>
  );
}

/* Table toolbar --------------------------------------------------------------------------------- */

const ALIGN_LABEL: Record<ColumnAlign, string> = {
  none: '기본',
  left: '왼쪽',
  center: '가운데',
  right: '오른쪽',
};

export function TableToolbar({
  editor,
  container,
  newId,
}: {
  editor: Editor;
  container: RefObject<HTMLDivElement | null>;
  newId: () => string;
}) {
  const state = useEditorState({
    editor,
    selector: ({ editor: current }) => {
      const location = cellAt(current.state, current.state.selection.from);
      if (location === null) return null;
      const dom = current.view.nodeDOM(location.table.pos);
      if (!(dom instanceof HTMLElement)) return null;
      const columns = (location.table.node.attrs.columns ?? []) as readonly { align?: ColumnAlign }[];
      return {
        top: relativeTop(container.current, dom.getBoundingClientRect()),
        align: columns[location.columnIndex]?.align ?? 'none',
        header: location.rowIndex === 0,
      };
    },
  });
  if (state === null) return null;
  const run = (op: TableOp) => () => runTableOp(editor, op, newId);

  return (
    <div
      className="floating-toolbar table-toolbar"
      style={{ top: Math.max(state.top - 42, 0), left: 0 }}
      onMouseDown={(event) => {
        if ((event.target as HTMLElement).tagName !== 'SELECT') event.preventDefault();
      }}
      role="toolbar"
      aria-label="표 편집"
    >
      <span className="toolbar-label">행</span>
      <button
        type="button"
        className="toolbar-btn"
        onClick={run({ kind: 'insertRow', below: false })}
        disabled={state.header}
        title="위에 행 추가"
      >
        ↑+
      </button>
      <button
        type="button"
        className="toolbar-btn"
        onClick={run({ kind: 'insertRow', below: true })}
        title="아래에 행 추가"
      >
        ↓+
      </button>
      <button
        type="button"
        className="toolbar-btn"
        onClick={run({ kind: 'moveRow', by: -1 })}
        title="행 위로"
      >
        ▲
      </button>
      <button
        type="button"
        className="toolbar-btn"
        onClick={run({ kind: 'moveRow', by: 1 })}
        title="행 아래로"
      >
        ▼
      </button>
      <button type="button" className="toolbar-btn" onClick={run({ kind: 'deleteRow' })} title="행 삭제">
        <Icon name="trash" size={13} />
      </button>
      <span className="toolbar-sep" />
      <span className="toolbar-label">열</span>
      <button
        type="button"
        className="toolbar-btn"
        onClick={run({ kind: 'insertColumn', right: false })}
        title="왼쪽에 열 추가"
      >
        ←+
      </button>
      <button
        type="button"
        className="toolbar-btn"
        onClick={run({ kind: 'insertColumn', right: true })}
        title="오른쪽에 열 추가"
      >
        →+
      </button>
      <button
        type="button"
        className="toolbar-btn"
        onClick={run({ kind: 'moveColumn', by: -1 })}
        title="열 왼쪽으로"
      >
        ◀
      </button>
      <button
        type="button"
        className="toolbar-btn"
        onClick={run({ kind: 'moveColumn', by: 1 })}
        title="열 오른쪽으로"
      >
        ▶
      </button>
      <button type="button" className="toolbar-btn" onClick={run({ kind: 'deleteColumn' })} title="열 삭제">
        <Icon name="trash" size={13} />
      </button>
      <select
        className="toolbar-select"
        aria-label="열 정렬"
        value={state.align}
        onChange={(event) =>
          runTableOp(editor, { kind: 'align', align: event.target.value as ColumnAlign }, newId)
        }
      >
        {(Object.keys(ALIGN_LABEL) as ColumnAlign[]).map((align) => (
          <option key={align} value={align}>
            정렬: {ALIGN_LABEL[align]}
          </option>
        ))}
      </select>
    </div>
  );
}
