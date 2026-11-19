import type { Editor } from '@tiptap/core';
import HardBreak from '@tiptap/extension-hard-break';
import { Dropcursor, Gapcursor, Placeholder, UndoRedo } from '@tiptap/extensions';
import { TextSelection } from '@tiptap/pm/state';
import type { EditorView } from '@tiptap/pm/view';
import { EditorContent, useEditor } from '@tiptap/react';
import type { Ref } from 'react';
import { Suspense, lazy, useEffect, useImperativeHandle, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { UPLOAD_ERROR_TEXT } from '../../../../application/asset/assetUseCases';
import type {
  DocumentEditorSession,
  DrawingInputRecorder,
  SaveOutcome,
} from '../../../../application/document/editorSession';
import type { Asset } from '../../../../domain/asset/asset';
import { isExternalImageUrl, isImageMediaType, standaloneHttpUrl } from '../../../../domain/asset/asset';
import { recentAgentChanges, AGENT_MARKER_VISIBLE_MS } from '../../../../domain/document/agentChanges';
import type { Block } from '../../../../domain/document/block';
import type { AgentDocument } from '../../../../domain/document/document';
import { toSavableBlocks } from '../../../../domain/document/draft';
import { mergeAfterConflict } from '../../../../domain/document/merge';
import { isAnchorError } from '../../../../domain/thread/anchor';
import type { Anchor, Thread } from '../../../../domain/thread/thread';
import type { Services } from '../../../../config/ServicesContext';
import { useServices } from '../../../../config/ServicesContext';
import { docLinkHref } from '../common/InlineText';
import { errorMessage } from '../common/States';
import { filterMentionLogins } from '../common/mentions';
import { useDebouncedCallback } from '../hooks/useDebouncedCallback';
import { hasMod } from '../hooks/useHotkey';
import { useMediaQuery } from '../hooks/useMediaQuery';
import { BlockBehavior } from './behavior';
import { clipboardMarkdown } from './clipboardMarkdown';
import { applyBlocks, insertBlock } from './commands';
import { blocksToDoc, docToDrafts } from './convert';
import { DrawingWithView, NARROW_DRAWING_QUERY } from './drawing/DrawingView';
import type {
  DrawingPair,
  DrawingSaveRequest,
  DrawingSaveResult,
  DrawingTarget,
} from './drawing/drawingTypes';
import { CANNOT_EDIT, saveDrawingPair } from './drawing/drawingSave';
import { randomBlockId } from './ids';
import { CodeBlockWithView, ImageWithView } from './nodeViews';
import { BlockHandle, SelectionToolbar, TableToolbar } from './overlays';
import { blockDecorationsKey } from './plugins/blockDecorations';
import { clearDuplicateIds } from './plugins/blockIds';
import { unmergePastedTables } from './plugins/tableSpans';
import { pastedImageUrls } from './pastedImages';
import { pasteStructuredText } from './paste';
import { threadMarksInput, threadMarksKey } from './plugins/threadMarks';
import { anchorForSelection, blockAtPos, findBlock } from './positions';
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
import { threadAnchorElement } from './threadAnchors';
import Bold from '@tiptap/extension-bold';
import Italic from '@tiptap/extension-italic';
import Strike from '@tiptap/extension-strike';
import Text from '@tiptap/extension-text';
import './editor.css';
import { savedAssetReferences } from './savedAssetReferences';
import type { GitHubContext } from '../../../../domain/document/githubReferences';
import { ContentDecorations } from './contentDecorations';

// Excalidraw is large; it loads only when a drawing is opened.
const DrawingDialog = lazy(() => import('./drawing/DrawingDialog'));

/** Marks the editor transaction that puts a saved drawing pair into its block: an app action, not typing. */
export const DRAWING_APPLY_META = 'agentDocsDrawingApply';

export const AUTOSAVE_DELAY_MS = 1_000;

export type DocumentEditorHandle = {
  /**
   * Sends the editor's current blocks now (also the "save again" action) and reports how it ended;
   * callers that need saved content (anchors, trash) continue only on 'saved'.
   */
  readonly flushSave: () => Promise<SaveOutcome>;
  /** Brings the content to `blocks`, e.g. the merge result after a save conflict. */
  readonly setBlocks: (blocks: readonly Block[]) => void;
  /** Viewport top of a thread's anchor, or null when it is not in the document. */
  readonly anchorTop: (threadId: string) => number | null;
  readonly blockTop: (blockId: string) => number | null;
  readonly scrollToThread: (threadId: string) => void;
  readonly scrollToBlock: (blockId: string) => void;
  readonly commentOnSelection: () => void;
  /** An open drawing dialog holds something the server has not accepted (changed, saving, or kept while read-only). */
  readonly hasDrawingDraft: () => boolean;
};

export type DocumentEditorProps = {
  readonly ref?: Ref<DocumentEditorHandle>;
  readonly document: AgentDocument;
  readonly session: DocumentEditorSession;
  readonly editable: boolean;
  readonly github?: GitHubContext;
  readonly threads: readonly Thread[];
  readonly activeThreadId: string | null;
  readonly reanchoring: boolean;
  readonly mentionLogins: readonly string[];
  readonly onActivateThread: (threadId: string) => void;
  readonly onRequestComment: (anchor: Anchor) => void;
  readonly onReanchor: (anchor: Anchor) => void;
  readonly onInput?: () => void;
  /** Real drawing input per dialog, kept apart from body input so only the save holding the drawing adopts it. */
  readonly drawingInput?: DrawingInputRecorder;
  readonly onContentChange: () => void;
  readonly onMessage: (message: string) => void;
};

type PendingUrlImport = {
  readonly url: string;
  readonly documentId: string;
  readonly blockId: string | null;
};

function isHtmlClipboardFile(file: File): boolean {
  const mime = file.type.split(';')[0]?.trim().toLowerCase() ?? '';
  return mime === 'text/html' || /\.html?$/i.test(file.name);
}

function replacePastedUrlParagraph(
  editor: Editor,
  blockId: string | null,
  url: string,
  asset: Asset,
): boolean {
  if (blockId === null || !isImageMediaType(asset.mediaType)) return false;
  const found = findBlock(editor.state.doc, blockId);
  if (found === null || found.node.type.name !== 'paragraph' || found.node.textContent.trim() !== url)
    return false;
  const node = editor.state.schema.nodes.image?.create({
    id: randomBlockId(),
    assetId: asset.id,
    alt: '',
    caption: '',
  });
  if (node === undefined) return false;
  editor.view.dispatch(
    editor.state.tr.replaceWith(found.pos, found.pos + found.node.nodeSize, node).scrollIntoView(),
  );
  return true;
}

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
  github: GitHubContext = { repositories: [] };
  replaceHtml: (blockId: string, previousAsset: string, file: File) => Promise<void> = () =>
    Promise.reject(new Error('시안 교체 준비 중'));
  openDrawing: (request: { readonly blockId: string } | { readonly anchorBlockId: string | null }) => void =
    () => undefined;
  localPreview: (assetId: string) => string | null = () => null;
  private pick: () => void = () => undefined;

  setGithub(github?: GitHubContext): void {
    this.github = github ?? { repositories: [] };
  }

  setReplaceHtml(replace: (blockId: string, previousAsset: string, file: File) => Promise<void>): void {
    this.replaceHtml = replace;
  }

  setDrawing(
    open: (request: { readonly blockId: string } | { readonly anchorBlockId: string | null }) => void,
    preview: (assetId: string) => string | null,
  ): void {
    this.openDrawing = open;
    this.localPreview = preview;
  }

  readonly pickImage = (): void => {
    this.pick();
  };

  sync(props: DocumentEditorProps, pick: () => void): void {
    this.mentionLogins = props.mentionLogins;
    this.github = props.github ?? { repositories: [] };
    this.pick = pick;
  }
}

/** Built once per editor. */
function buildExtensions(
  services: Services,
  store: SuggestionStore,
  bridge: EditorBridge,
  session: DocumentEditorSession,
) {
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
    filterMentionLogins(bridge.mentionLogins, query).map((login) => ({
      key: login,
      label: `@${login}`,
      run: (editor, range) => {
        editor.chain().focus().deleteRange(range).insertContent(`@${login} `).run();
      },
    }));
  return [
    TableRoles,
    ContentDecorations.configure({ github: () => bridge.github }),
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
    ImageWithView.configure({
      contentUrl: (assetId: string) => services.assets.contentUrl(assetId),
      htmlSource: (assetId: string) => services.assets.htmlSource(assetId),
      savedReferences: savedAssetReferences(session),
      replaceHtml: (blockId: string, previousAsset: string, file: File) =>
        bridge.replaceHtml(blockId, previousAsset, file),
    }),
    DrawingWithView.configure({
      contentUrl: (assetId: string) => services.assets.contentUrl(assetId),
      savedReferences: savedAssetReferences(session),
      localPreview: (assetId: string) => bridge.localPreview(assetId),
      openDrawing: (blockId: string) => bridge.openDrawing({ blockId }),
    }),
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
        items: slashItems(randomBlockId, bridge.pickImage, (anchorBlockId) =>
          bridge.openDrawing({ anchorBlockId }),
        ),
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
  const [drawing, setDrawing] = useState<DrawingTarget | null>(null);
  const [pendingUrlImport, setPendingUrlImport] = useState<PendingUrlImport | null>(null);
  const [importingUrl, setImportingUrl] = useState(false);
  const drawingDraft = useRef(false);
  // What this tab uploaded for drawings whose save the server has not confirmed yet, so no route is asked early.
  const previews = useRef(new Map<string, string>());
  const scenes = useRef(new Map<string, string>());
  const dark = useMediaQuery('(prefers-color-scheme: dark)');

  useEffect(() => {
    latest.current = props;
    bridge.sync(props, () => fileInput.current?.click());
    bridge.setReplaceHtml(async (blockId, previousAsset, file) => {
      const editor = editorRef.current;
      if (editor === null || !editor.isEditable) throw new Error('편집할 수 없습니다.');
      if ((await session.saveContent(savableBlocks(editor, session))) !== 'saved')
        throw new Error('먼저 현재 입력을 저장해 주세요.');
      await session.finalize();
      if (session.getState().errorMessage !== null) throw new Error('기존 입력 기록을 확정하지 못했습니다.');
      const uploaded = await services.assets.upload(document.space.slug, {
        name: file.name,
        type: file.type || 'text/html',
        size: file.size,
        data: file,
      });
      if (!uploaded.ok) throw new Error(UPLOAD_ERROR_TEXT[uploaded.error]);
      if (uploaded.value.mediaType !== 'text/html') throw new Error('HTML 파일을 선택해 주세요.');
      const found = findBlock(editor.state.doc, blockId);
      if (found === null || found.node.attrs.assetId !== previousAsset)
        throw new Error('시안이 바뀌었습니다. 다시 읽어 주세요.');
      const { node, pos: position } = found;
      editor.view.dispatch(
        editor.state.tr.setNodeMarkup(position, undefined, {
          ...node.attrs,
          assetId: uploaded.value.id,
          caption: 'mockup',
        }),
      );
      if ((await session.saveContent(savableBlocks(editor, session))) !== 'saved')
        throw new Error('시안 변경을 저장하지 못했습니다.');
      await session.finalize();
      if (session.getState().errorMessage !== null)
        throw new Error(session.getState().errorMessage ?? '시안 기록을 확정하지 못했습니다.');
    });
    bridge.setDrawing(
      (request) => {
        const editor = editorRef.current;
        if (editor === null || !editor.isEditable) return;
        if (window.matchMedia(NARROW_DRAWING_QUERY).matches) {
          latest.current.onMessage('손그림은 넓은 화면에서 그릴 수 있습니다.');
          return;
        }
        const key = randomBlockId();
        if ('anchorBlockId' in request) {
          setDrawing((open) => open ?? { kind: 'new', key, anchorBlockId: request.anchorBlockId });
          return;
        }
        const found = findBlock(editor.state.doc, request.blockId);
        if (found?.node.type.name !== 'drawing') return;
        const pair = {
          svgAssetId: String(found.node.attrs.svgAssetId),
          sceneAssetId: String(found.node.attrs.sceneAssetId),
        };
        setDrawing((open) => open ?? { kind: 'edit', key, blockId: request.blockId, pair });
      },
      (assetId) => previews.current.get(assetId) ?? null,
    );
  });

  useEffect(() => {
    const urls = previews.current;
    return () => {
      for (const url of urls.values()) URL.revokeObjectURL(url);
    };
  }, []);

  // Reads a saved drawing's scene through this document only; a drawing this tab put in but the server has not
  // confirmed opens from the copy kept here, and anything else waits for the save instead of asking a route early.
  const loadDrawingScene = (target: DrawingTarget): Promise<string | null> => {
    if (target.kind === 'new') return Promise.resolve(null);
    const local = scenes.current.get(target.pair.sceneAssetId);
    if (local !== undefined) return Promise.resolve(local);
    if (!savedAssetReferences(session).contains(target.pair.sceneAssetId))
      return Promise.reject(new Error('문서 저장 후 이 그림을 편집할 수 있습니다.'));
    return services.assets.sceneSource(target.pair.sceneAssetId);
  };

  const uploadDrawingFile = async (name: string, type: string, data: Blob): Promise<Asset> => {
    const result = await services.assets.upload(document.space.slug, { name, type, size: data.size, data });
    if (!result.ok) throw new Error(UPLOAD_ERROR_TEXT[result.error]);
    if (result.value.mediaType !== type) throw new Error('그림 파일이 손그림 형식으로 저장되지 않았습니다.');
    return result.value;
  };

  // Puts the pair into its block: the same block id when editing, or a new block where the slash menu was opened.
  // Returns null when the block no longer holds the pair this dialog last saw.
  const placeDrawing = (editor: Editor, request: DrawingSaveRequest, pair: DrawingPair): string | null => {
    const { state } = editor;
    const editing = request.blockId ?? (request.target.kind === 'edit' ? request.target.blockId : null);
    if (editing !== null) {
      const found = findBlock(state.doc, editing);
      const attrs = found?.node.attrs;
      if (
        found?.node.type.name !== 'drawing' ||
        attrs?.svgAssetId !== request.expected?.svgAssetId ||
        attrs?.sceneAssetId !== request.expected?.sceneAssetId
      )
        return null;
      editor.view.dispatch(
        state.tr
          .setNodeMarkup(found.pos, undefined, { ...found.node.attrs, ...pair })
          .setMeta(DRAWING_APPLY_META, true),
      );
      return editing;
    }
    const id = randomBlockId();
    const node = state.schema.nodes.drawing?.create({ id, ...pair, alt: '' });
    if (node === undefined) return null;
    const anchor =
      request.target.kind === 'new' && request.target.anchorBlockId !== null
        ? findBlock(state.doc, request.target.anchorBlockId)
        : null;
    const tr = state.tr;
    if (anchor === null) tr.insert(state.doc.content.size, node);
    else if (anchor.node.type.name === 'paragraph' && anchor.node.content.size === 0)
      tr.replaceWith(anchor.pos, anchor.pos + anchor.node.nodeSize, node);
    else tr.insert(anchor.pos + anchor.node.nodeSize, node);
    editor.view.dispatch(tr.setMeta(DRAWING_APPLY_META, true));
    return id;
  };

  // The flow and its permission checks live in saveDrawingPair; this part only knows the editor and the session.
  const saveDrawing = (request: DrawingSaveRequest): Promise<DrawingSaveResult> => {
    const editor = editorRef.current;
    if (editor === null) return Promise.resolve({ kind: 'kept', message: CANNOT_EDIT });
    return saveDrawingPair(request, {
      canWrite: () => editor.isEditable && session.isWritable(),
      saveBody: (options) => session.saveContent(savableBlocks(editor, session), options),
      finalize: async () => {
        await session.finalize();
        return session.getState().errorMessage === null;
      },
      upload: uploadDrawingFile,
      place: (pair, svg) => {
        const preview = URL.createObjectURL(svg);
        previews.current.set(pair.svgAssetId, preview);
        scenes.current.set(pair.sceneAssetId, request.snapshot.sceneJson);
        const blockId = placeDrawing(editor, request, pair);
        if (blockId === null) {
          previews.current.delete(pair.svgAssetId);
          scenes.current.delete(pair.sceneAssetId);
          URL.revokeObjectURL(preview);
        }
        return blockId;
      },
    });
  };

  const closeDrawing = (input: 'discard' | 'keep'): void => {
    if (drawing !== null && input === 'discard') latest.current.drawingInput?.discard(drawing.key);
    drawingDraft.current = false;
    setDrawing(null);
  };

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
    if (isImageMediaType(asset.mediaType) || asset.mediaType === 'text/html') {
      insertBlock(editor, {
        type: 'image',
        attrs: {
          id: randomBlockId(),
          assetId: asset.id,
          alt: asset.mediaType === 'text/html' ? asset.fileName : '',
          caption: asset.mediaType === 'text/html' ? 'mockup' : '',
        },
      });
    } else {
      editor
        .chain()
        .focus()
        .insertContent([
          {
            type: 'text',
            text: asset.fileName,
            marks: [{ type: 'link', attrs: { href: `asset:${asset.id}` } }],
          },
          { type: 'text', text: ' ' },
        ])
        .run();
    }
  };

  const uploadFiles = async (files: readonly File[], at: number | null): Promise<void> => {
    for (const file of files) {
      latest.current.onMessage(`${file.name} 올리는 중…`);
      try {
        const result = await services.assets.upload(document.space.slug, {
          name: file.name,
          type: file.type || (isHtmlClipboardFile(file) ? 'text/html' : ''),
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

  const importPendingUrl = async (pending: PendingUrlImport): Promise<void> => {
    const startEditor = editorRef.current;
    if (
      startEditor === null ||
      startEditor.isDestroyed ||
      !startEditor.isEditable ||
      !session.isWritable() ||
      latest.current.document.id !== pending.documentId
    ) {
      setPendingUrlImport(null);
      return;
    }
    setImportingUrl(true);
    try {
      const asset = await services.assets.importUrl(document.space.slug, pending.url);
      setPendingUrlImport(null);
      const current = editorRef.current;
      if (
        current === null ||
        current !== startEditor ||
        current.isDestroyed ||
        !current.isEditable ||
        !session.isWritable() ||
        latest.current.document.id !== pending.documentId
      ) {
        return;
      }
      if (!replacePastedUrlParagraph(current, pending.blockId, pending.url, asset)) insertAsset(asset, null);
    } catch (error) {
      setPendingUrlImport(null);
      if (latest.current.document.id === pending.documentId) latest.current.onMessage(errorMessage(error));
    } finally {
      setImportingUrl(false);
    }
  };

  // One at a time, so the images land in page order after the pasted text.
  const importImages = async (urls: readonly string[]): Promise<void> => {
    for (const url of urls) await importImage(url);
  };

  const follow = (href: string | null): void => {
    if (href === null) return;
    const internal = docLinkHref(href);
    if (internal !== null) void navigate(internal);
    else
      window.open(
        href.startsWith('asset:') ? services.assets.contentUrl(href.slice(6)) : href,
        '_blank',
        'noopener,noreferrer',
      );
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

  const [extensions] = useState(() => buildExtensions(services, store, bridge, session));
  const [initialContent] = useState(() => blocksToDoc(document.blocks, randomBlockId));

  const editor = useEditor(
    {
      extensions,
      content: initialContent,
      editable,
      editorProps: {
        attributes: { class: 'doc-editor', 'aria-label': '문서 본문', spellcheck: 'true' },
        clipboardTextSerializer: (slice) =>
          clipboardMarkdown(
            slice,
            (assetId) => new URL(services.assets.contentUrl(assetId), window.location.href).href,
          ),
        handleKeyDown: (_view, event) => {
          if (hasMod(event) && event.altKey && event.code === 'KeyM') {
            requestComment(latest.current.reanchoring);
            return true;
          }
          return false;
        },
        handleDOMEvents: {
          copy: (view) => {
            if (!view.editable) selectionFromDom(view);
            return false;
          },
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
        transformPasted: (slice, view) => clearDuplicateIds(unmergePastedTables(slice), view.state.doc),
        handlePaste: (view, event) => {
          if (!view.editable) return false;
          const files = Array.from(event.clipboardData?.files ?? []).filter(
            (file) => isImageMediaType(file.type) || isHtmlClipboardFile(file),
          );
          if (files.length > 0) {
            setPendingUrlImport(null);
            void uploadFiles(files, null);
            return true;
          }
          const text = event.clipboardData?.getData('text/plain') ?? '';
          if (isExternalImageUrl(text)) {
            setPendingUrlImport(null);
            void importImage(text.trim());
            return true;
          }
          if (pasteStructuredText(view, event)) {
            setPendingUrlImport(null);
            return true;
          }
          // The editor drops <img> from pasted web content; the server downloads each image after the text is in.
          const images = pastedImageUrls(event.clipboardData?.getData('text/html') ?? '');
          if (images.length > 0) {
            setPendingUrlImport(null);
            setTimeout(() => void importImages(images), 0);
            return false;
          }
          const url = standaloneHttpUrl(text);
          if (url === null) {
            setPendingUrlImport(null);
          } else {
            const topNode = view.state.selection.$from.depth >= 1 ? view.state.selection.$from.node(1) : null;
            const blockId = typeof topNode?.attrs.id === 'string' ? topNode.attrs.id : null;
            setPendingUrlImport({ url, documentId: latest.current.document.id, blockId });
          }
          return false;
        },
        handleDrop: (view, event, _slice, moved) => {
          if (moved || !view.editable) return false;
          const files = Array.from(event.dataTransfer?.files ?? []);
          if (files.length === 0) return false;
          event.preventDefault();
          const at = view.posAtCoords({ left: event.clientX, top: event.clientY });
          const block = at === null ? null : blockAtPos(view.state.doc, at.pos);
          void uploadFiles(files, block === null ? null : block.pos + 1);
          return true;
        },
      },
      onUpdate: ({ editor, transaction }) => {
        if (transaction.getMeta(DRAWING_APPLY_META) !== true) latest.current.onInput?.();
        session.markEdited(savableBlocks(editor, session));
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
    bridge.setGithub(props.github);
    editor.view.dispatch(editor.state.tr);
  }, [editor, bridge, props.github]);

  useEffect(() => {
    if (editor.isEditable !== editable) editor.setEditable(editable);
  }, [editor, editable]);

  const activePendingUrl =
    editable && pendingUrlImport !== null && pendingUrlImport.documentId === document.id
      ? pendingUrlImport
      : null;

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
    const input = threadMarksInput(threads, activeThreadId);
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
    const findThread = (threadId: string): HTMLElement | null =>
      threadAnchorElement(editor.view.dom, latest.current.threads, threadId);
    const topOf = (element: HTMLElement | null): number | null =>
      element === null ? null : element.getBoundingClientRect().top;
    return {
      flushSave: () => {
        autosave.cancel();
        return session.saveContent(savableBlocks(editor, session));
      },
      setBlocks: (blocks) => applyBlocks(editor, savableBlocks(editor, session), blocks, randomBlockId),
      anchorTop: (threadId) => topOf(findThread(threadId)),
      blockTop: (blockId) => topOf(find(`[data-block-id="${CSS.escape(blockId)}"]`)),
      scrollToThread: (threadId) =>
        findThread(threadId)?.scrollIntoView({
          block: 'center',
          behavior: 'smooth',
        }),
      scrollToBlock: (blockId) =>
        find(`[data-block-id="${CSS.escape(blockId)}"]`)?.scrollIntoView({
          block: 'center',
          behavior: 'smooth',
        }),
      commentOnSelection: () => requestComment(false),
      hasDrawingDraft: () => drawingDraft.current,
    };
  }, [editor, session, autosave]);

  return (
    <div className="editor-shell" ref={containerRef}>
      {activePendingUrl !== null ? (
        <div className="editor-paste-import" role="status">
          <span className="editor-paste-import-text">
            붙여 넣은 주소를 이미지로 가져올 수 있습니다: <code>{activePendingUrl.url}</code>
          </span>
          <div className="editor-paste-import-actions">
            <button
              type="button"
              className="btn btn-small btn-primary"
              disabled={importingUrl}
              onClick={() => void importPendingUrl(activePendingUrl)}
            >
              {importingUrl ? '가져오는 중…' : '이미지로 가져오기'}
            </button>
            <button
              type="button"
              className="btn btn-small"
              disabled={importingUrl}
              onClick={() => setPendingUrlImport(null)}
            >
              유지
            </button>
          </div>
        </div>
      ) : null}
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
        accept="*/*"
        hidden
        onChange={(event) => {
          const files = Array.from(event.target.files ?? []);
          event.target.value = '';
          void uploadFiles(files, null);
        }}
      />
      {drawing !== null ? (
        <Suspense
          fallback={
            <div className="drawing-dialog-backdrop">
              <p role="status">손그림 편집기를 불러오는 중…</p>
            </div>
          }
        >
          <DrawingDialog
            key={drawing.key}
            target={drawing}
            editable={editable}
            theme={dark ? 'dark' : 'light'}
            loadScene={() => loadDrawingScene(drawing)}
            onMeaningfulInput={(at) => latest.current.drawingInput?.mark(drawing.key, at)}
            reserveInput={() => latest.current.drawingInput?.reserve(drawing.key) ?? null}
            onSave={saveDrawing}
            onClose={closeDrawing}
            onDraftChange={(draft) => {
              drawingDraft.current = draft;
            }}
          />
        </Suspense>
      ) : null}
    </div>
  );
}
