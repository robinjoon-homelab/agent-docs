import type { Editor } from '@tiptap/core';
import HardBreak from '@tiptap/extension-hard-break';
import { Dropcursor, Gapcursor, Placeholder, UndoRedo } from '@tiptap/extensions';
import { TextSelection } from '@tiptap/pm/state';
import type { EditorView } from '@tiptap/pm/view';
import { EditorContent, useEditor } from '@tiptap/react';
import type { Ref } from 'react';
import { useEffect, useImperativeHandle, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { UPLOAD_ERROR_TEXT } from '../../../../application/asset/assetUseCases';
import type { DocumentEditorSession, SaveOutcome } from '../../../../application/document/editorSession';
import type { Asset } from '../../../../domain/asset/asset';
import { IMAGE_MEDIA_TYPES, isExternalImageUrl, isImageMediaType } from '../../../../domain/asset/asset';
import { recentAgentChanges, AGENT_MARKER_VISIBLE_MS } from '../../../../domain/document/agentChanges';
import type { Block } from '../../../../domain/document/block';
import type { AgentDocument } from '../../../../domain/document/document';
import { toSavableBlocks } from '../../../../domain/document/draft';
import { mergeAfterConflict } from '../../../../domain/document/merge';
import { isAnchorError } from '../../../../domain/thread/anchor';
import type { Anchor, Thread } from '../../../../domain/thread/thread';
import { textRange } from '../../../../domain/thread/thread';
import type { Services } from '../../../../config/ServicesContext';
import { useServices } from '../../../../config/ServicesContext';
import { docLinkHref } from '../common/InlineText';
import { errorMessage } from '../common/States';
import { useDebouncedCallback } from '../hooks/useDebouncedCallback';
import { hasMod } from '../hooks/useHotkey';
import { BlockBehavior } from './behavior';
import { applyBlocks, insertBlock } from './commands';
import { blocksToDoc, docToDrafts } from './convert';
import { randomBlockId } from './ids';
import { CodeBlockWithView, ImageWithView } from './nodeViews';
import { BlockHandle, SelectionToolbar, TableToolbar } from './overlays';
import { blockDecorationsKey } from './plugins/blockDecorations';
import { clearDuplicateIds } from './plugins/blockIds';
import type { ThreadMarksInput } from './plugins/threadMarks';
import { threadMarksKey } from './plugins/threadMarks';
import { anchorForSelection, blockAtPos } from './positions';
import {
  BlockDocument,
  CalloutNode,
  CodeMark,
  DividerNode,
  DocLink,
  HeadingNode,
  ListItemNode,
  ParagraphNode,
  QuoteNode,
  TableCellNode,
  TableNode,
  TableRoles,
  TableRowNode,
} from './schema';
import { slashItems } from './slashItems';
import type { SuggestionItem } from './suggestions';
import { SuggestionPopup, SuggestionStore, suggestionExtension } from './suggestions';
import Bold from '@tiptap/extension-bold';
import Italic from '@tiptap/extension-italic';
import Strike from '@tiptap/extension-strike';
import Text from '@tiptap/extension-text';
import './editor.css';

export const AUTOSAVE_DELAY_MS = 1_000;

export type DocumentEditorHandle = {
  /**
   * Sends the editor's current blocks now (also the "save again" action) and reports how it ended;
   * callers that need saved content (anchors, trash) continue only on 'saved'.
   */
  readonly flushSave: () => Promise<SaveOutcome>;
  /** Brings the content to `blocks`, e.g. the merge result after a save conflict. */
  readonly setBlocks: (blocks: readonly Block[]) => void;
  /** Viewport top of a thread's highlighted anchor, or null when it is not in the document. */
  readonly anchorTop: (threadId: string) => number | null;
  readonly blockTop: (blockId: string) => number | null;
  readonly scrollToThread: (threadId: string) => void;
  readonly scrollToBlock: (blockId: string) => void;
  readonly commentOnSelection: () => void;
};

export type DocumentEditorProps = {
  readonly ref?: Ref<DocumentEditorHandle>;
  readonly document: AgentDocument;
  readonly session: DocumentEditorSession;
  readonly editable: boolean;
  readonly threads: readonly Thread[];
  readonly activeThreadId: string | null;
  readonly reanchoring: boolean;
  readonly mentionLogins: readonly string[];
  readonly onActivateThread: (threadId: string) => void;
  readonly onRequestComment: (anchor: Anchor) => void;
  readonly onReanchor: (anchor: Anchor) => void;
  readonly onContentChange: () => void;
  readonly onMessage: (message: string) => void;
};

function selectionFromDom(view: EditorView): void {
  const selection = window.getSelection();
  if (
    selection === null ||
    selection.isCollapsed ||
    selection.anchorNode === null ||
    selection.focusNode === null
  )
    return;
  if (!view.dom.contains(selection.anchorNode) || !view.dom.contains(selection.focusNode)) return;
  try {
    const anchor = view.posAtDOM(selection.anchorNode, selection.anchorOffset);
    const head = view.posAtDOM(selection.focusNode, selection.focusOffset);
    view.dispatch(view.state.tr.setSelection(TextSelection.create(view.state.doc, anchor, head)));
  } catch {
    // The DOM selection can point into a node view's own UI; there is nothing to comment on there.
  }
}

/** The editor content as it would be stored, keeping stored markdown for unchanged blocks. */
function savableBlocks(editor: Editor, session: DocumentEditorSession): Block[] {
  return toSavableBlocks(docToDrafts(editor.getJSON(), randomBlockId), session.baseBlocks());
}

/**
 * Mutable link from the long-lived editor extensions to the current React props. Extensions are built
 * once per editor, so they read from here instead of capturing props.
 */
class EditorBridge {
  mentionLogins: readonly string[] = [];
  private pick: () => void = () => undefined;

  readonly pickImage = (): void => {
    this.pick();
  };

  sync(props: DocumentEditorProps, pick: () => void): void {
    this.mentionLogins = props.mentionLogins;
    this.pick = pick;
  }
}

/** Built once per editor. */
function buildExtensions(services: Services, store: SuggestionStore, bridge: EditorBridge) {
  const docLinks = async (query: string): Promise<SuggestionItem[]> => {
    const hits = await services.search.titles(query);
    return hits.slice(0, 8).map((hit) => ({
      key: hit.documentId,
      label: hit.title,
      hint: hit.space.name,
      run: (editor, range) => {
        editor
          .chain()
          .focus()
          .deleteRange(range)
          .insertContent([
            {
              type: 'text',
              text: hit.title,
              marks: [{ type: 'link', attrs: { href: `doc:${hit.documentId}` } }],
            },
            { type: 'text', text: ' ' },
          ])
          .run();
      },
    }));
  };
  const mentions = (query: string): SuggestionItem[] =>
    bridge.mentionLogins
      .filter((login) => login.toLowerCase().startsWith(query.toLowerCase()))
      .slice(0, 8)
      .map((login) => ({
        key: login,
        label: `@${login}`,
        run: (editor, range) => {
          editor.chain().focus().deleteRange(range).insertContent(`@${login} `).run();
        },
      }));
  return [
    TableRoles,
    BlockDocument,
    Text,
    ParagraphNode,
    HeadingNode,
    ListItemNode,
    QuoteNode,
    CalloutNode,
    CodeBlockWithView,
    TableNode,
    TableRowNode,
    TableCellNode,
    ImageWithView.configure({ contentUrl: (assetId: string) => services.assets.contentUrl(assetId) }),
    DividerNode,
    HardBreak,
    Bold,
    Italic,
    Strike,
    CodeMark,
    DocLink,
    UndoRedo,
    Dropcursor.configure({ color: 'var(--focus)', width: 2 }),
    Gapcursor,
    Placeholder.configure({
      placeholder: ({ node }) =>
        node.type.name === 'paragraph' ? "'/'로 블록 추가 · '[['로 문서 링크 · '@'로 멘션" : '',
      showOnlyCurrent: true,
    }),
    BlockBehavior.configure({ newId: randomBlockId }),
    suggestionExtension(
      {
        name: 'slash',
        char: '/',
        title: '블록',
        startOfLine: true,
        allow: ({ state, range }) => {
          const $from = state.doc.resolve(range.from);
          return $from.depth === 1 && $from.parent.type.name === 'paragraph';
        },
        items: slashItems(randomBlockId, bridge.pickImage),
      },
      store,
    ),
    suggestionExtension(
      { name: 'doclink', char: '[[', title: '문서 링크', allowSpaces: true, items: docLinks },
      store,
    ),
    suggestionExtension({ name: 'mention', char: '@', title: '멘션', items: mentions }, store),
  ];
}

/**
 * The S4 block editor: Tiptap/ProseMirror on the flat block model, with autosave through the editor
 * session, comment anchors, slash menu, doc links, mentions, tables, images and mermaid.
 */
export function DocumentEditor(props: DocumentEditorProps) {
  const { ref, document, session, editable, threads, activeThreadId, reanchoring } = props;
  const services = useServices();
  const navigate = useNavigate();
  const latest = useRef(props);
  const containerRef = useRef<HTMLDivElement>(null);
  const editorRef = useRef<Editor | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const [store] = useState(() => new SuggestionStore());
  const [bridge] = useState(() => new EditorBridge());

  useEffect(() => {
    latest.current = props;
    bridge.sync(props, () => fileInput.current?.click());
  });

  const save = (): void => {
    const editor = editorRef.current;
    if (editor === null || editor.isDestroyed) return;
    void session.saveContent(savableBlocks(editor, session));
  };
  const autosave = useDebouncedCallback(save, AUTOSAVE_DELAY_MS);

  const insertAsset = (asset: Asset, at: number | null): void => {
    const editor = editorRef.current;
    if (editor === null) return;
    if (at !== null) editor.commands.setTextSelection(Math.min(at, editor.state.doc.content.size));
    insertBlock(editor, {
      type: 'image',
      attrs: { id: randomBlockId(), assetId: asset.id, alt: '', caption: '' },
    });
  };

  const uploadFiles = async (files: readonly File[], at: number | null): Promise<void> => {
    for (const file of files) {
      latest.current.onMessage(`${file.name} 올리는 중…`);
      try {
        const result = await services.assets.upload(document.space.slug, {
          name: file.name,
          type: file.type,
          size: file.size,
          data: file,
        });
        if (result.ok) insertAsset(result.value, at);
        else latest.current.onMessage(UPLOAD_ERROR_TEXT[result.error]);
      } catch (error) {
        latest.current.onMessage(errorMessage(error));
      }
    }
  };

  const importImage = async (url: string): Promise<void> => {
    try {
      insertAsset(await services.assets.importUrl(document.space.slug, url), null);
    } catch (error) {
      latest.current.onMessage(errorMessage(error));
    }
  };

  const follow = (href: string | null): void => {
    if (href === null) return;
    const internal = docLinkHref(href);
    if (internal !== null) void navigate(internal);
    else window.open(href, '_blank', 'noopener,noreferrer');
  };

  const requestComment = (forReanchor: boolean): void => {
    const editor = editorRef.current;
    if (editor === null) return;
    const anchor = anchorForSelection(editor.state);
    if (isAnchorError(anchor)) {
      latest.current.onMessage('댓글을 달 텍스트를 선택하세요.');
      return;
    }
    if (forReanchor) latest.current.onReanchor(anchor);
    else latest.current.onRequestComment(anchor);
  };

  const [extensions] = useState(() => buildExtensions(services, store, bridge));
  const [initialContent] = useState(() => blocksToDoc(document.blocks, randomBlockId));

  const editor = useEditor(
    {
      extensions,
      content: initialContent,
      editable,
      editorProps: {
        attributes: { class: 'doc-editor', 'aria-label': '문서 본문', spellcheck: 'true' },
        handleKeyDown: (_view, event) => {
          if (hasMod(event) && event.altKey && event.code === 'KeyM') {
            requestComment(latest.current.reanchoring);
            return true;
          }
          return false;
        },
        handleDOMEvents: {
          click: (view, event) => {
            const target = event.target as HTMLElement;
            const link = target.closest('a');
            if (link !== null && view.dom.contains(link)) {
              event.preventDefault();
              if (hasMod(event) || !view.editable) follow(link.getAttribute('href'));
            }
            const mark = target.closest('[data-thread-id]');
            const threadId = mark?.getAttribute('data-thread-id');
            if (threadId !== null && threadId !== undefined) latest.current.onActivateThread(threadId);
            return false;
          },
          mouseup: (view) => {
            if (!view.editable) setTimeout(() => selectionFromDom(view), 0);
            return false;
          },
        },
        transformPasted: (slice, view) => clearDuplicateIds(slice, view.state.doc),
        handlePaste: (view, event) => {
          if (!view.editable) return false;
          const files = Array.from(event.clipboardData?.files ?? []).filter((file) =>
            isImageMediaType(file.type),
          );
          if (files.length > 0) {
            void uploadFiles(files, null);
            return true;
          }
          const text = event.clipboardData?.getData('text/plain') ?? '';
          if (isExternalImageUrl(text)) {
            void importImage(text.trim());
            return true;
          }
          return false;
        },
        handleDrop: (view, event, _slice, moved) => {
          if (moved || !view.editable) return false;
          const files = Array.from(event.dataTransfer?.files ?? []).filter((file) =>
            isImageMediaType(file.type),
          );
          if (files.length === 0) return false;
          event.preventDefault();
          const at = view.posAtCoords({ left: event.clientX, top: event.clientY });
          const block = at === null ? null : blockAtPos(view.state.doc, at.pos);
          void uploadFiles(files, block === null ? null : block.pos + 1);
          return true;
        },
      },
      onUpdate: () => {
        session.markEdited();
        autosave.call();
        latest.current.onContentChange();
      },
    },
    [document.id],
  );

  useEffect(() => {
    editorRef.current = editor;
  }, [editor]);

  useEffect(() => {
    if (editor.isEditable !== editable) editor.setEditable(editable);
  }, [editor, editable]);

  // The server kept another writer's blocks in our save: show them, keeping blocks the user changed since.
  useEffect(
    () =>
      session.onServerMerge(({ submitted, server }) => {
        if (editor.isDestroyed) return;
        const current = savableBlocks(editor, session);
        applyBlocks(
          editor,
          current,
          mergeAfterConflict(submitted, current, server, new Map()),
          randomBlockId,
        );
      }),
    [editor, session],
  );

  useEffect(() => {
    const input: ThreadMarksInput = {
      marks: threads.map((thread) => ({
        threadId: thread.id,
        anchor: thread.anchor,
        range: textRange(thread),
      })),
      activeId: activeThreadId,
    };
    editor.view.dispatch(editor.state.tr.setMeta(threadMarksKey, input));
    latest.current.onContentChange();
  }, [editor, threads, activeThreadId]);

  useEffect(() => {
    const labels = new Map(
      [...recentAgentChanges(document.blockChanges, services.clock.now())].map(([id, mark]) => [
        id,
        mark.label,
      ]),
    );
    if (labels.size === 0) return;
    editor.view.dispatch(editor.state.tr.setMeta(blockDecorationsKey, { labels, fresh: true }));
    const timer = setTimeout(() => {
      if (!editor.isDestroyed)
        editor.view.dispatch(editor.state.tr.setMeta(blockDecorationsKey, { labels, fresh: false }));
    }, AGENT_MARKER_VISIBLE_MS);
    return () => clearTimeout(timer);
  }, [editor, document.blockChanges, services.clock]);

  useImperativeHandle(ref, (): DocumentEditorHandle => {
    const find = (selector: string): HTMLElement | null =>
      editor.view.dom.querySelector<HTMLElement>(selector);
    const topOf = (element: HTMLElement | null): number | null =>
      element === null ? null : element.getBoundingClientRect().top;
    return {
      flushSave: () => {
        autosave.cancel();
        return session.saveContent(savableBlocks(editor, session));
      },
      setBlocks: (blocks) => applyBlocks(editor, savableBlocks(editor, session), blocks, randomBlockId),
      anchorTop: (threadId) => topOf(find(`[data-thread-id="${CSS.escape(threadId)}"]`)),
      blockTop: (blockId) => topOf(find(`[data-block-id="${CSS.escape(blockId)}"]`)),
      scrollToThread: (threadId) =>
        find(`[data-thread-id="${CSS.escape(threadId)}"]`)?.scrollIntoView({
          block: 'center',
          behavior: 'smooth',
        }),
      scrollToBlock: (blockId) =>
        find(`[data-block-id="${CSS.escape(blockId)}"]`)?.scrollIntoView({
          block: 'center',
          behavior: 'smooth',
        }),
      commentOnSelection: () => requestComment(false),
    };
  }, [editor, session, autosave]);

  return (
    <div className="editor-shell" ref={containerRef}>
      <EditorContent editor={editor} />
      <SelectionToolbar
        editor={editor}
        container={containerRef}
        editable={editable}
        reanchoring={reanchoring}
        onComment={() => requestComment(false)}
        onReanchor={() => requestComment(true)}
      />
      <BlockHandle
        editor={editor}
        container={containerRef}
        editable={editable}
        onBlockComment={(anchor) => latest.current.onRequestComment(anchor)}
        onCellComment={(anchor) =>
          latest.current.reanchoring
            ? latest.current.onReanchor(anchor)
            : latest.current.onRequestComment(anchor)
        }
        onCopyBlockLink={(blockId) => {
          const url = `${window.location.origin}${window.location.pathname}#block-${blockId}`;
          void navigator.clipboard.writeText(url).then(
            () => latest.current.onMessage('블록 링크를 복사했습니다'),
            () => latest.current.onMessage('복사하지 못했습니다'),
          );
        }}
      />
      {editable ? <TableToolbar editor={editor} container={containerRef} newId={randomBlockId} /> : null}
      <SuggestionPopup store={store} />
      <input
        ref={fileInput}
        type="file"
        accept={IMAGE_MEDIA_TYPES.join(',')}
        hidden
        onChange={(event) => {
          const files = Array.from(event.target.files ?? []);
          event.target.value = '';
          void uploadFiles(files, null);
        }}
      />
    </div>
  );
}
