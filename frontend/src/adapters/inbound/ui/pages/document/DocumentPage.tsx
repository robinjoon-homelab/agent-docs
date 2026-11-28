import { flowAnchorTarget } from '../../../../../domain/flow/flowAnchorComparison';
import { flowAnchorLocation, flowAnchorSearch } from '../../../../../domain/flow/flowAnchorLocation';
import { isDesignFlow } from '../../../../../domain/flow/newDesignFlow';
import { DesignFlowDocument } from './design/DesignFlowDocument';
import { flowPreview } from './flowPreview';
import type { Block } from '../../../../../domain/document/block';
import { FlowThreadHistory, FlowSnapshot } from './FlowThreadHistory';
import { FlowDocument, type FlowHandle } from './FlowDocument';
import { Dialog } from '../../common/Dialog';
import { DocumentAccessBanner } from './DocumentAccessBanner';
import { commentAnchorPreparation } from '../../../../../domain/document/commentAnchor';
import { RetainedDocumentContext } from '../../hooks/RetainedDocumentContext';
import { useSpacePermissions } from '../../app/SpacePermissionsContext';
import { useDocumentAccess } from './useDocumentAccess';
import { DocumentPermissionContext } from './DocumentPermissionContext';
import { canEdit, canComment } from '../../../../../domain/space/permission';
import { DocumentAssetScope } from './DocumentAssetScope';
import { useEditorInputTime } from './useEditorInputTime';
import { TransferDialog } from './TransferDialog';
import { useDocumentEvents } from './useDocumentEvents';
import { RevisionNamePrompt } from '../history/RevisionNamePrompt';
import { EditTimeDetails } from '../../statistics/EditTimeDetails';
import { DocumentNavigation, useRecordDocumentView } from '../../navigation/DocumentNavigation';
import { githubContextOf } from '../../../../../domain/space/space';
import type { Space } from '../../../../../domain/space/space';
import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  useSyncExternalStore,
} from 'react';
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router';
import type { MetaPatch } from '../../../../../application/document/ports/DocumentGateway';
import { isNotVisible } from '../../../../../application/shared/ports/errors';
import type { AgentDocument, DocumentSummary } from '../../../../../domain/document/document';
import { documentHref } from '../../../../../domain/document/document';
import { breadcrumbOf, childrenOf } from '../../../../../domain/document/pathTree';
import type { ConflictChoice } from '../../../../../domain/document/merge';
import type { Anchor, Thread, ThreadFilter } from '../../../../../domain/thread/thread';
import { filterThreads, isDetached } from '../../../../../domain/thread/thread';
import { useServices } from '../../../../../config/ServicesContext';
import { useSession } from '../../app/SessionContext';
import { useOpenDocument, useShell } from '../../app/ShellContext';
import { Icon } from '../../common/Icon';
import { ErrorBox, Loading, NotFound, errorMessage } from '../../common/States';
import { useToast } from '../../common/Toast';
import { TrashDocumentDialog } from '../../common/TrashDocumentDialog';
import type { DocumentEditorHandle } from '../../editor/DocumentEditor';
import { DocumentEditor } from '../../editor/DocumentEditor';
import { useLoad, type Load } from '../../hooks/useLoad';
import { useMediaQuery } from '../../hooks/useMediaQuery';
import { usePageTitle } from '../../hooks/usePageTitle';
import { CommentRail } from './CommentRail';
import { ConflictPanel } from './ConflictPanel';
import { DocumentHeader } from './DocumentHeader';
import { useThreadState } from './useThreadState';
import './document.css';

export function DocumentPage() {
  const { space: slug = '', docId = '' } = useParams();
  const location = useLocation();
  const services = useServices();
  const loaded = useLoad(`document:${docId}`, async () => {
    const [document, threads] = await Promise.all([
      services.documents.get(docId),
      services.threads.list(docId, true),
    ]);
    return { document, threads };
  });

  if (loaded.state === 'loading') return <Loading />;
  if (loaded.state === 'error') {
    if (isNotVisible(loaded.error)) return <NotFound />;
    return (
      <div className="page">
        <ErrorBox error={loaded.error} onRetry={loaded.reload} />
      </div>
    );
  }
  const { document, threads } = loaded.data;
  if (document.space.slug !== slug) {
    return (
      <Navigate
        to={`${documentHref(document.space.slug, document.id)}${location.search}${location.hash}`}
        replace
      />
    );
  }
  return (
    <DocumentAssetScope key={document.id} documentId={document.id}>
      <DocumentWorkspace document={document} initialThreads={threads} />
    </DocumentAssetScope>
  );
}

/** The documents under this one, from their paths, with a way to add one when the document is editable. */
function ChildDocuments({
  spaceSlug,
  documents,
  editable,
  onAdd,
}: {
  spaceSlug: string;
  documents: readonly DocumentSummary[];
  editable: boolean;
  onAdd: () => void;
}) {
  if (documents.length === 0 && !editable) return null;
  return (
    <section className="doc-children" aria-label="하위 문서">
      {documents.length === 0 ? null : (
        <>
          <h2 className="doc-children-title">하위 문서</h2>
          <ul className="doc-children-list">
            {documents.map((child) => (
              <li key={child.id}>
                <Link to={documentHref(spaceSlug, child.id)}>
                  <Icon name="doc" size={14} />
                  {child.title}
                </Link>
              </li>
            ))}
          </ul>
        </>
      )}
      {editable ? (
        <button type="button" className="btn btn-small btn-ghost" onClick={onAdd}>
          <Icon name="plus" size={14} />
          하위 문서 추가
        </button>
      ) : null}
    </section>
  );
}

function mentionCandidates(login: string, document: AgentDocument, threads: readonly Thread[]): string[] {
  const logins = [
    login,
    ...(document.assignee === null ? [] : [document.assignee]),
    ...threads.flatMap((thread) => thread.comments.map((comment) => comment.author.login)),
    ...document.blockChanges.map((change) => change.actor.login),
  ];
  return [...new Set(logins)];
}

function DocumentWorkspace({
  document,
  initialThreads,
}: {
  document: AgentDocument;
  initialThreads: readonly Thread[];
}) {
  const services = useServices();
  const navigate = useNavigate();
  const location = useLocation();
  const { me } = useSession();
  const shell = useShell();
  const openDocument = useOpenDocument();
  const toast = useToast();
  const [session] = useState(() => services.openEditor(document));
  useRecordDocumentView(services.navigation, document.id);
  const inputTime = useEditorInputTime(session, document.id, services.statistics);
  const live = useSpacePermissions();
  const closeEvents = useRef<() => void>(() => {});
  const access = useDocumentAccess(document, me.owner, session, () => services.documents.get(document.id), {
    revoked: () => closeEvents.current(),
    onError: (failure) => toast(errorMessage(failure)),
    spaces: { get: (slug) => services.spaces.get(slug), update: live.update },
  });
  const { revoke: revokeAccess, receivePermission: receiveAccessPermission } = access;
  useLayoutEffect(
    () =>
      session.onWriteAccess((change) => {
        if ('revoked' in change) revokeAccess(change.revoked);
        else void receiveAccessPermission(change.permission);
      }),
    [session, revokeAccess, receiveAccessPermission],
  );
  const currentAccess = access.currentAccess;
  const spaceInfo = useDocumentLookup(
    `document-space:${document.space.slug}`,
    () => services.spaces.get(document.space.slug),
    access.revoked === null,
    access.error,
  );
  const [transferSpaces, setTransferSpaces] = useState<readonly Space[] | null>(null);
  const [transferring, setTransferring] = useState(false);
  const [upgradeRequest, setUpgradeRequest] = useState<string | null>(null);
  const [upgrading, setUpgrading] = useState(false);
  const state = useSyncExternalStore(session.subscribe, session.getState);
  const visibleBlocks = useSyncExternalStore(session.subscribe, () => session.currentBlocks());
  const design = document.type === 'flow' && isDesignFlow(visibleBlocks);
  const editorRef = useRef<DocumentEditorHandle>(null);
  const flowRef = useRef<FlowHandle>(null);
  const [flowModeTarget, setFlowModeTarget] = useState<HTMLDivElement | null>(null);
  const [flowStatsTarget, setFlowStatsTarget] = useState<HTMLSpanElement | null>(null);
  const [snapshot, setFlowSnapshot] = useState<{
    blocks: readonly Block[];
    blockId: string;
    revision: number;
    threadId: string;
    route: string;
  } | null>(null);
  const flowSnapshot = snapshot?.route === location.key ? snapshot : null;
  const layoutRef = useRef<HTMLDivElement>(null);
  const {
    threads,
    start,
    update,
    actions,
    reload: reloadThreads,
  } = useThreadState(document.id, initialThreads, services.threads, () => access.refresh());
  const stopEvents = useDocumentEvents(document.id, services.documentEvents, {
    revoked: access.revoked !== null,
    refreshDocument: access.refresh,
    onPermission: access.receivePermission,
    onRevoked: access.revoke,
    reloadThreads,
    onError: (error) => {
      if (!access.error(error)) toast(errorMessage(error));
    },
  });
  useLayoutEffect(() => {
    closeEvents.current = stopEvents;
  }, [stopEvents]);
  const [filter, setFilter] = useState<ThreadFilter>(() =>
    flowPreview().filter === 'gone' ? 'detached' : flowPreview().filter === 'all' ? 'all' : 'open',
  );
  const [activeThreadId, setActiveThreadId] = useState<string | null>(() =>
    flowPreview().active ? `flow-${flowPreview().active}` : null,
  );
  const [pending, setPending] = useState<Anchor | null>(null);
  useEffect(() => {
    const preview = flowPreview();
    const thread = initialThreads.find((t) => t.id === `flow-${preview.snap}`);
    let active = true;
    if (thread && thread.anchoredRevision !== undefined && thread.anchor.kind !== 'document') {
      const blockId = thread.anchor.blockId;
      void services.documents.revision(document.id, thread.anchoredRevision).then((snapshot) => {
        if (active) {
          setFlowSnapshot({
            blocks: snapshot.blocks,
            blockId,
            revision: snapshot.revision,
            threadId: thread.id,
            route: location.key,
          });
        }
      });
    }
    return () => {
      active = false;
    };
  }, [document.id, initialThreads, services.documents, location.key]);
  const [reanchorThread, setReanchorThread] = useState<Thread | null>(null);
  const [layoutVersion, setLayoutVersion] = useState(0);
  const wide = useMediaQuery('(min-width: 1280px)');
  const [railOpen, setRailOpen] = useState(false);
  const docked = wide && !shell.railCollapsed;
  const { setRailCollapsed } = shell;
  // Pointing at a comment brings the rail back however it was put away: the drawer on narrow screens, folding on wide.
  const showRail = useCallback(() => {
    setRailOpen(true);
    setRailCollapsed(false);
  }, [setRailCollapsed]);
  const editable = access.writable && !transferring && !upgrading;
  const frame = useRef<number | null>(null);
  const spaceSlug = document.space.slug;
  const siblings = useDocumentLookup(
    `space-documents:${spaceSlug}:${shell.treeVersion}`,
    () => services.documents.listInSpace(spaceSlug),
    access.revoked === null,
    access.error,
  );
  const spaceDocuments = siblings.state === 'ready' ? siblings.data : [];
  // A move made in the sidebar can take this document along; its new path then shows in the space's list first.
  const listedPath = spaceDocuments.find((candidate) => candidate.id === document.id)?.path;
  useEffect(() => {
    if (currentAccess().revoked === null && listedPath !== undefined && listedPath !== state.meta.path)
      void session.refreshMeta();
  }, [listedPath, state.meta.path, session, currentAccess]);

  useEffect(() => {
    if (state.lastSavedAt !== null && currentAccess().revoked === null) void reloadThreads();
  }, [state.lastSavedAt, reloadThreads, currentAccess]);

  useEffect(() => {
    if (!editable || state.status !== 'saved' || !session.hasPendingRevision()) return;
    const timer = window.setTimeout(() => void session.finalize(), session.finalizeDelay());
    return () => window.clearTimeout(timer);
  }, [editable, session, state.status, state.version, state.meta]);

  useEffect(() => {
    const editor = editorRef.current;
    const mayWrite = () =>
      currentAccess().revoked === null && canEdit(currentAccess().permission) && session.isWritable();
    const leave = (): void => {
      if (mayWrite()) void session.finalize();
    };
    window.addEventListener('pagehide', leave);
    return () => {
      window.removeEventListener('pagehide', leave);
      if (!mayWrite()) return;
      const flush = editor?.flushSave();
      void Promise.resolve(flush).then(() => {
        if (mayWrite()) return session.finalize();
      });
    };
  }, [session, currentAccess]);

  usePageTitle(state.meta.title);

  const relayout = useCallback(() => {
    if (frame.current !== null) cancelAnimationFrame(frame.current);
    frame.current = requestAnimationFrame(() => setLayoutVersion((value) => value + 1));
  }, []);

  useEffect(() => {
    window.addEventListener('resize', relayout);
    return () => window.removeEventListener('resize', relayout);
  }, [relayout]);

  useEffect(() => {
    const warn = (event: BeforeUnloadEvent): void => {
      const status = session.getState().status;
      // 'error' counts too: a failed save leaves the typed text only in this tab.
      if (status !== 'saved' || editorRef.current?.hasDrawingDraft() === true) event.preventDefault();
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [session]);

  // Deep links: #thread-<id> opens the thread at its anchor, #block-<id> scrolls to the block.
  const hash = location.hash;
  useEffect(() => {
    if (!hash.startsWith('#thread-') && !hash.startsWith('#block-')) return;
    let id: string;
    try {
      id = decodeURIComponent(hash.slice(hash.indexOf('-') + 1));
    } catch {
      return;
    }
    const timer = setTimeout(() => {
      const thread = hash.startsWith('#thread-') ? threads.find((item) => item.id === id) : undefined;
      if (hash.startsWith('#thread-')) {
        setActiveThreadId(id);
        setFilter('all');
        showRail();
        editorRef.current?.scrollToThread(id);
      } else editorRef.current?.scrollToBlock(id);
      if (document.type !== 'flow') return;
      const block = hash.startsWith('#block-')
        ? id
        : thread && thread.anchor.kind !== 'document'
          ? thread.anchor.blockId
          : undefined;
      if (!block) return;
      if (design) {
        const viewedState = thread?.anchor.kind === 'block' ? thread.anchor.viewedStateId : undefined;
        const search = flowAnchorSearch(session.currentBlocks(), block, location.search, viewedState);
        if (search && search !== location.search)
          void navigate({ pathname: location.pathname, search, hash }, { replace: true });
      } else flowRef.current?.selectBlock(block);
    }, 50);
    return () => clearTimeout(timer);
  }, [hash, showRail, threads, design, session, location.pathname, location.search, navigate, document.type]);

  const measure = useMemo(
    () => ({
      threadTop: (thread: Thread) => {
        if (isDetached(thread) || thread.anchor.kind === 'document') return null;
        return editorRef.current?.anchorTop(thread.id) ?? null;
      },
      anchorTop: (anchor: Anchor) =>
        anchor.kind === 'document' ? null : (editorRef.current?.blockTop(anchor.blockId) ?? null),
    }),
    [],
  );

  const requestComment = (anchor: Anchor): void => {
    if (!canComment(currentAccess().permission)) return;
    setPending(anchor);
    setActiveThreadId(null);
    showRail();
  };

  /** Saves pending edits first; anchors and trashing assume the server has what the user sees. */
  const saveFirst = useCallback(async (): Promise<boolean> => {
    if (!canEdit(currentAccess().permission) || !session.isWritable()) return false;
    if (!(await session.flushMeta())) {
      toast('문서 정보를 저장하지 못했습니다. 입력을 확인하세요.');
      return false;
    }
    const outcome = (await editorRef.current?.flushSave()) ?? 'saved';
    if (outcome === 'saved' && (await session.idle()) === 'saved' && session.getState().status === 'saved')
      return true;
    toast(
      outcome === 'conflict'
        ? '저장 충돌을 먼저 해결하세요.'
        : '본문을 저장하지 못했습니다. 다시 저장한 뒤 시도하세요.',
    );
    return false;
  }, [currentAccess, session, toast]);

  const [sidebarTrashing, setSidebarTrashing] = useState(false);
  useLayoutEffect(() => {
    const active = {
      id: document.id,
      spaceSlug: document.space.slug,
      path: () => session.getState().meta.path,
      withSavedEdits: async (action: () => Promise<void>) => {
        if (editorRef.current?.hasDrawingDraft() === true) {
          toast('손그림을 먼저 저장하거나 취소하세요.');
          return;
        }
        // Keep input paused from the save through the trash request.
        setSidebarTrashing(true);
        try {
          if ((await saveFirst()) && openDocument.currentDocument() === active) await action();
        } finally {
          setSidebarTrashing(false);
        }
      },
    };
    return openDocument.register(active);
  }, [document.id, document.space.slug, openDocument, saveFirst, session, toast]);

  const prepareCommentAnchor = async (): Promise<boolean> => {
    if (!canComment(currentAccess().permission)) return false;
    const preparation = commentAnchorPreparation({
      writable: session.isWritable() && canEdit(currentAccess().permission),
      base: session.baseBlocks(),
      current: session.currentBlocks(),
      drawingDraft: editorRef.current?.hasDrawingDraft() === true,
    });
    if (preparation === 'ready') return true;
    if (preparation === 'save') return saveFirst();
    toast(
      '저장되지 않은 본문이나 손그림 초안이 있어 댓글 위치를 정할 수 없습니다. 입력은 보존됩니다. 먼저 저장하거나 복사해 두세요.',
    );
    return false;
  };

  const startThread = async (body: string, anchor: Anchor | null = pending): Promise<boolean> => {
    if (anchor === null) return false;
    // Anchors are offsets into the content the server has, so pending edits go out first.
    if (!canComment(currentAccess().permission)) return false;
    if (!(await prepareCommentAnchor())) return false;
    if (document.type === 'flow' && session.hasPendingRevision() && canEdit(currentAccess().permission)) {
      await session.finalize();
      if (session.getState().status !== 'saved') return false;
    }
    const thread = await start(anchor, body);
    if (thread === null) return false;
    setPending(null);
    setActiveThreadId(thread.id);
    if (filter === 'detached') setFilter('open');
    return true;
  };

  const reanchor = async (anchor: Anchor): Promise<void> => {
    if (reanchorThread === null) return;
    if (!(await prepareCommentAnchor())) return;
    if (document.type === 'flow' && session.hasPendingRevision() && canEdit(currentAccess().permission)) {
      await session.finalize();
      if (session.getState().status !== 'saved') return;
    }
    const done = await update(() => services.threads.reanchor(reanchorThread.id, anchor));
    if (done) {
      toast('댓글을 새 위치에 달았습니다');
      setActiveThreadId(reanchorThread.id);
      setReanchorThread(null);
      setFilter('open');
    }
  };

  const patch = (change: MetaPatch): void => {
    if (!canEdit(currentAccess().permission) || !session.isWritable()) return;
    void session.patchMeta(change).then((saved) => {
      if (saved && (change.path !== undefined || change.title !== undefined)) shell.refreshTree();
    });
  };

  const openTransfer = async () => {
    if (!(await saveFirst())) return;
    try {
      setTransferSpaces(
        (await services.spaces.list()).filter((space) =>
          canEdit(space.permission ?? (me.owner ? 'owner' : 'read')),
        ),
      );
    } catch (error) {
      toast(errorMessage(error));
    }
  };
  const transfer = async (space: string, path: string) => {
    setTransferring(true);
    try {
      if (!(await saveFirst())) throw new Error('저장하지 못했습니다. 입력을 확인하세요.');
      await session.finalize();
      if (session.getState().errorMessage !== null)
        throw new Error(session.getState().errorMessage ?? '확정 실패');
      const moved = await services.documents.transfer(document.id, session.getState().version, space, path);
      shell.refreshTree();
      window.location.assign(documentHref(moved.space.slug, moved.documentId));
    } finally {
      setTransferring(false);
    }
  };

  const [trashPreview, setTrashPreview] = useState<number | null>(null);
  const [trashing, setTrashing] = useState(false);

  const previewTrash = async (): Promise<void> => {
    if (!(await saveFirst())) return;
    try {
      const along = await services.documents.listInSpace(spaceSlug, { under: session.getState().meta.path });
      setTrashPreview(along.length);
    } catch (error) {
      toast(errorMessage(error));
    }
  };

  const trash = async (): Promise<void> => {
    setTrashing(true);
    try {
      if (!(await saveFirst())) return;
      await services.documents.trash(document.id);
      setTrashPreview(null);
      shell.refreshTree();
      void navigate(`/s/${document.space.slug}`);
    } catch (error) {
      toast(errorMessage(error));
    } finally {
      setTrashing(false);
    }
  };

  const restoreFromTrash = async (): Promise<void> => {
    if (!canEdit(currentAccess().permission)) return;
    try {
      await services.documents.untrash(document.id);
      shell.refreshTree();
      window.location.reload();
    } catch (error) {
      toast(errorMessage(error));
    }
  };

  const upgradeFlow = async (): Promise<void> => {
    if (!upgradeRequest || !editable || !services.documents.upgradeFlow) return;
    setUpgrading(true);
    try {
      if (!(await saveFirst())) return;
      await session.finalize();
      if (session.getState().status !== 'saved') return;
      await services.documents.upgradeFlow(document.id, session.getState().version, upgradeRequest);
      await session.refreshRemote();
      setUpgradeRequest(null);
    } catch (error) {
      if (!access.error(error)) toast(errorMessage(error));
    } finally {
      setUpgrading(false);
    }
  };

  const resolveConflicts = async (
    choices: ReadonlyMap<string, ConflictChoice>,
    orderChoice?: ConflictChoice,
  ): Promise<void> => {
    if (!access.beginConflictResolution()) return;
    const { outcome } = await session.resolveConflicts(choices, orderChoice);
    if (outcome === 'saved') {
      await access.refresh();
      toast('선택한 내용으로 저장했습니다');
    } else
      toast(
        outcome === 'conflict'
          ? '그사이 또 바뀌어 다시 골라야 합니다.'
          : '저장하지 못했습니다. 다시 저장하세요.',
      );
  };

  const openCount = threads.filter((thread) => thread.status !== 'resolved').length;
  const focusFlowBlock = (blockId: string, viewedStateId?: string) => {
    if (design) {
      const search = flowAnchorSearch(session.currentBlocks(), blockId, location.search, viewedStateId);
      if (search) void navigate({ pathname: location.pathname, search });
    } else flowRef.current?.selectBlock(blockId);
  };
  const mentionLogins = mentionCandidates(me.login, document, threads);
  const highlightedThreadId = filterThreads(threads, filter).some((thread) => thread.id === activeThreadId)
    ? activeThreadId
    : null;
  const rail = (flowScope?: string, onlyThreadId?: string) => (
    <CommentRail
      flowBlocks={document.type === 'flow' ? visibleBlocks : undefined}
      flowTargetVersion={state.status === 'saved' ? state.version : undefined}
      flowScope={flowScope}
      threadHeading={
        document.type === 'flow'
          ? (thread, past, target) => (
              <FlowThreadHistory
                past={past}
                target={target}
                currentVersion={state.status === 'saved' ? state.version : undefined}
                onSelect={(blockId, viewedStateId) => {
                  setActiveThreadId(thread.id);
                  focusFlowBlock(blockId, viewedStateId);
                }}
                thread={thread}
                blocks={visibleBlocks}
                onSnapshot={(blocks, blockId, revision) => {
                  const parent = flowAnchorLocation(blocks, blockId);
                  if (parent) flowRef.current?.openHistorical(parent.screenBlockId);
                  setFlowSnapshot({ blocks, blockId, revision, threadId: thread.id, route: location.key });
                  setActiveThreadId(thread.id);
                  setPending(null);
                }}
              />
            )
          : undefined
      }
      threads={onlyThreadId ? threads.filter((thread) => thread.id === onlyThreadId) : threads}
      filter={flowScope === undefined ? filter : 'all'}
      onFilter={setFilter}
      pending={pending}
      activeThreadId={activeThreadId}
      login={me.login}
      mentionLogins={mentionLogins}
      spaceSlug={document.space.slug}
      actions={actions((thread) => {
        setReanchorThread(thread);
        toast(
          document.type === 'flow'
            ? '다시 달 화면이나 요소를 선택하세요.'
            : '본문에서 새 위치의 텍스트를 선택한 뒤 "여기에 다시 달기"를 누르세요',
        );
        if (!wide) setRailOpen(false);
      })}
      aligned={document.type !== 'flow' && wide}
      measure={measure}
      layoutVersion={layoutVersion}
      onActivate={(thread) => {
        setActiveThreadId(thread.id);
        editorRef.current?.scrollToThread(thread.id);
      }}
      onStart={startThread}
      onCancelPending={() => setPending(null)}
      onDocumentComment={() => requestComment({ kind: 'document' })}
      onClose={wide ? undefined : () => setRailOpen(false)}
      onCollapse={wide ? () => setRailCollapsed(true) : undefined}
    />
  );

  return (
    <RetainedDocumentContext
      value={{
        documentId: document.id,
        revoked: access.revoked !== null,
        canLoad: () => currentAccess().revoked === null,
        unauthenticated: () => access.revoke('unauthenticated'),
      }}
    >
      <DocumentPermissionContext value={access.permission}>
        <div
          className={`doc-layout${document.type === 'flow' ? ' is-flow' : ''}${docked ? '' : ' is-undocked'}`}
          ref={layoutRef}
          inert={sidebarTrashing}
        >
          <div className="doc-main">
            <div className="doc-body">
              <DocumentAccessBanner
                access={access}
                onCopy={() => {
                  const text = JSON.stringify(
                    { metadata: session.getState().meta, blocks: session.currentBlocks() },
                    null,
                    2,
                  );
                  void navigator.clipboard.writeText(text).then(
                    () => toast('입력을 복사했습니다'),
                    () => toast('복사하지 못했습니다. 본문을 선택해 복사하세요.'),
                  );
                }}
              />
              {access.resuming ? (
                <p className="muted" role="status">
                  최신 문서를 확인하고 있습니다.
                </p>
              ) : null}
              <DocumentHeader
                flowModeSlot={document.type === 'flow' ? <div ref={setFlowModeTarget} /> : undefined}
                flowStatsSlot={document.type === 'flow' ? <span ref={setFlowStatsTarget} /> : undefined}
                document={document}
                crumbs={breadcrumbOf(spaceDocuments, state.meta.path)}
                state={state}
                editable={editable}
                onPatch={patch}
                onInput={(change) => {
                  for (const field of Object.keys(change) as (keyof MetaPatch)[]) inputTime.mark(field);
                  session.markMetaEdited(change);
                }}
                onCancelInput={(key) => {
                  session.cancelMetaEdited(key);
                  inputTime.discard(key);
                }}
                onTransfer={() => void openTransfer()}
                onUpgradeFlow={
                  editable && !design && document.type === 'flow' && services.documents.upgradeFlow
                    ? () => setUpgradeRequest(crypto.randomUUID())
                    : undefined
                }
                onTrash={() => void previewTrash()}
                onRetrySave={() => {
                  void saveFirst().then((saved) => {
                    if (saved) toast('저장했습니다');
                  });
                }}
              />
              {upgradeRequest && (
                <Dialog
                  open
                  title="레이어 디자인으로 전환"
                  onClose={() => {
                    if (!upgrading) setUpgradeRequest(null);
                  }}
                  footer={
                    <>
                      <button
                        type="button"
                        className="btn"
                        disabled={upgrading}
                        onClick={() => setUpgradeRequest(null)}
                      >
                        취소
                      </button>
                      <button
                        type="button"
                        className="btn btn-primary"
                        disabled={upgrading}
                        onClick={() => void upgradeFlow()}
                      >
                        전환
                      </button>
                    </>
                  }
                >
                  기존 화면과 요소를 편집 가능한 레이어로 옮깁니다. 댓글과 이전 리비전은 보존됩니다.
                </Dialog>
              )}
              {document.trashedAt === null ? null : (
                <div className="warning-box doc-banner">
                  휴지통에 있는 문서입니다. 읽기만 할 수 있습니다.{' '}
                  <button
                    disabled={!canEdit(access.permission)}
                    type="button"
                    className="btn btn-small"
                    onClick={() => void restoreFromTrash()}
                  >
                    복원
                  </button>
                </div>
              )}
              {reanchorThread === null ? null : (
                <div className="warning-box doc-banner">
                  “
                  {reanchorThread.anchor.kind === 'text' || reanchorThread.anchor.kind === 'cell'
                    ? reanchorThread.anchor.quote
                    : '댓글'}
                  ”을 다시 달 {document.type === 'flow' ? '화면이나 요소' : '텍스트'}를 선택하세요.{' '}
                  <button type="button" className="btn btn-small" onClick={() => setReanchorThread(null)}>
                    취소
                  </button>
                </div>
              )}
              {state.status === 'conflict' && canEdit(access.permission) && !access.resuming ? (
                <ConflictPanel
                  conflicts={state.conflicts}
                  orderConflict={state.orderConflict}
                  mine={visibleBlocks}
                  onResolve={resolveConflicts}
                />
              ) : null}
              {state.revisionNameSuggestion === null || !editable ? null : (
                <RevisionNamePrompt
                  key={state.revisionNameSuggestion}
                  documentId={document.id}
                  revision={state.revisionNameSuggestion}
                  onClose={() => session.dismissRevisionNameSuggestion()}
                />
              )}
              {trashPreview === null || !editable ? null : (
                <TrashDocumentDialog
                  descendants={trashPreview}
                  owner={me.owner}
                  busy={trashing}
                  onCancel={() => setTrashPreview(null)}
                  onConfirm={() => void trash()}
                />
              )}
              {transferSpaces === null || !editable ? null : (
                <TransferDialog
                  spaces={transferSpaces}
                  path={state.meta.path}
                  onTransfer={transfer}
                  onClose={() => setTransferSpaces(null)}
                />
              )}
              {document.type === 'flow' ? (
                <>
                  <div hidden={flowSnapshot !== null}>
                    {design ? (
                      <DesignFlowDocument
                        ref={flowRef}
                        statsTarget={flowStatsTarget}
                        threads={threads}
                        spaceSlug={document.space.slug}
                        onStartComment={
                          canComment(access.permission)
                            ? (anchor, body) => startThread(body, anchor)
                            : undefined
                        }
                        mentionLogins={mentionLogins}
                        reanchoring={reanchorThread !== null}
                        onReanchor={(anchor) => void reanchor(anchor)}
                        session={session}
                        editable={editable}
                        modeTarget={flowModeTarget}
                        onEditMode={() => setFlowSnapshot(null)}
                        onInput={() => inputTime.mark('body')}
                        onRequestComment={
                          canComment(access.permission)
                            ? (anchor) => (reanchorThread ? void reanchor(anchor) : requestComment(anchor))
                            : undefined
                        }
                        pendingComment={pending}
                        commentRail={flowSnapshot ? () => null : rail}
                      />
                    ) : (
                      <FlowDocument
                        spaceSlug={document.space.slug}
                        modeTarget={flowModeTarget}
                        statsTarget={flowStatsTarget}
                        ref={flowRef}
                        session={session}
                        editable={editable}
                        onEditMode={() => setFlowSnapshot(null)}
                        onInput={() => inputTime.mark('body')}
                        onRequestComment={requestComment}
                        commentRail={flowSnapshot ? () => null : rail}
                        reanchoring={reanchorThread !== null}
                        onReanchor={(anchor) => void reanchor(anchor)}
                        onStartComment={(anchor, body) => startThread(body, anchor)}
                        mentionLogins={mentionLogins}
                        threads={threads}
                      />
                    )}
                  </div>
                  {flowSnapshot && (
                    <>
                      <FlowSnapshot
                        threads={threads}
                        thread={threads.find((t) => t.id === flowSnapshot.threadId)}
                        currentVersion={state.status === 'saved' ? state.version : undefined}
                        onComments={() => {
                          flowRef.current?.showComments();
                          setFlowSnapshot(null);
                        }}
                        onInfo={() => {
                          flowRef.current?.showInfo();
                          setFlowSnapshot(null);
                        }}
                        onPanelCollapse={(collapsed) => flowRef.current?.setPanelCollapsed(collapsed)}
                        threadCard={rail(flowSnapshot.blockId, flowSnapshot.threadId)}
                        onSelectScreen={(blockId, viewedStateId) => {
                          focusFlowBlock(blockId, viewedStateId);
                          setFlowSnapshot(null);
                        }}
                        {...flowSnapshot}
                        current={visibleBlocks}
                        onClose={() => {
                          const current = session.currentBlocks();
                          const thread = threads.find((t) => t.id === flowSnapshot.threadId);
                          const target = flowAnchorTarget(
                            flowSnapshot.blocks,
                            current,
                            flowSnapshot.blockId,
                            thread,
                            state.status === 'saved' ? state.version : undefined,
                          );
                          if (target === 'changed' && flowAnchorLocation(current, flowSnapshot.blockId)) {
                            if (design)
                              focusFlowBlock(
                                flowSnapshot.blockId,
                                thread?.anchor.kind === 'block' ? thread.anchor.viewedStateId : undefined,
                              );
                            else flowRef.current?.selectBlock(flowSnapshot.blockId, false);
                          }
                          setFlowSnapshot(null);
                        }}
                      />
                    </>
                  )}
                </>
              ) : (
                <DocumentEditor
                  ref={editorRef}
                  document={document}
                  session={session}
                  editable={editable}
                  github={spaceInfo.state === 'ready' ? githubContextOf(spaceInfo.data) : undefined}
                  onInput={() => inputTime.mark('body')}
                  drawingInput={inputTime.drawing}
                  threads={threads}
                  activeThreadId={highlightedThreadId}
                  reanchoring={reanchorThread !== null}
                  mentionLogins={mentionLogins}
                  onActivateThread={(threadId) => {
                    setActiveThreadId(threadId);
                    showRail();
                  }}
                  onRequestComment={requestComment}
                  onReanchor={(anchor) => void reanchor(anchor)}
                  onContentChange={relayout}
                  onMessage={toast}
                />
              )}
              <DocumentNavigation navigation={services.navigation} documentId={document.id} />
              <EditTimeDetails statistics={services.statistics} documentId={document.id} />
              <ChildDocuments
                spaceSlug={spaceSlug}
                documents={childrenOf(spaceDocuments, state.meta.path)}
                editable={editable}
                onAdd={() => shell.openNewDocument(spaceSlug, `${state.meta.path}/`)}
              />
            </div>
          </div>
          {document.type === 'flow' ? null : docked ? (
            rail()
          ) : wide ? (
            <button
              type="button"
              className="rail-toggle"
              onClick={() => setRailCollapsed(false)}
              title="댓글 펼치기"
              aria-label={`댓글 펼치기, 열린 댓글 ${openCount}개`}
            >
              <Icon name="comment" />
              {openCount > 0 ? <span className="nav-count">{openCount}</span> : null}
            </button>
          ) : (
            <>
              <button
                type="button"
                className="rail-toggle"
                onClick={() => setRailOpen((value) => !value)}
                aria-expanded={railOpen}
                aria-label={`댓글 ${openCount}개`}
              >
                <Icon name="comment" />
                {openCount > 0 ? <span className="nav-count">{openCount}</span> : null}
              </button>
              {railOpen ? <div className="rail-drawer">{rail()}</div> : null}
            </>
          )}
        </div>
      </DocumentPermissionContext>
    </RetainedDocumentContext>
  );
}

/** Background lookups must never redirect an already open editor and discard its unsaved input. */
function useDocumentLookup<T>(
  key: string,
  loader: () => Promise<T>,
  enabled: boolean,
  onError: (error: unknown) => boolean,
): Load<T> {
  const [state, setState] = useState<Load<T>>({ state: 'loading', data: null, error: null });
  const latest = useRef({ loader, onError });
  useEffect(() => {
    latest.current = { loader, onError };
  });
  useEffect(() => {
    if (!enabled) return;
    let active = true;
    latest.current.loader().then(
      (data) => {
        if (active) setState({ state: 'ready', data, error: null });
      },
      (error: unknown) => {
        if (!active) return;
        latest.current.onError(error);
        setState({ state: 'error', data: null, error });
      },
    );
    return () => {
      active = false;
    };
  }, [key, enabled]);
  return state;
}
