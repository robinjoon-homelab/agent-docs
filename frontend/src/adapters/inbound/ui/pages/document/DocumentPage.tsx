import { DocumentAssetScope } from './DocumentAssetScope';
import { useEditorInputTime } from './useEditorInputTime';
import { TransferDialog } from './TransferDialog';
import { useDocumentEvents } from './useDocumentEvents';
import { RevisionNamePrompt } from '../history/RevisionNamePrompt';
import { EditTimeDetails } from '../../statistics/EditTimeDetails';
import { DocumentNavigation, useRecordDocumentView } from '../../navigation/DocumentNavigation';
import { githubContextOf } from '../../../../../domain/space/space';
import type { Space } from '../../../../../domain/space/space';
import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react';
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
import { useShell } from '../../app/ShellContext';
import { Icon } from '../../common/Icon';
import { ErrorBox, Loading, NotFound, errorMessage } from '../../common/States';
import { useToast } from '../../common/Toast';
import type { DocumentEditorHandle } from '../../editor/DocumentEditor';
import { DocumentEditor } from '../../editor/DocumentEditor';
import { useLoad } from '../../hooks/useLoad';
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
      services.threads.list(docId),
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
    return <Navigate to={`${documentHref(document.space.slug, document.id)}${location.hash}`} replace />;
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
  const toast = useToast();
  const [session] = useState(() => services.openEditor(document));
  useRecordDocumentView(services.navigation, document.id);
  const inputTime = useEditorInputTime(session, document.id, services.statistics);
  const spaceInfo = useLoad(`document-space:${document.space.slug}`, () =>
    services.spaces.get(document.space.slug),
  );
  const [transferSpaces, setTransferSpaces] = useState<readonly Space[] | null>(null);
  const [transferring, setTransferring] = useState(false);
  const state = useSyncExternalStore(session.subscribe, session.getState);
  const editorRef = useRef<DocumentEditorHandle>(null);
  const layoutRef = useRef<HTMLDivElement>(null);
  const {
    threads,
    start,
    update,
    actions,
    reload: reloadThreads,
  } = useThreadState(document.id, initialThreads, services.threads, () => session.refreshRemote());
  useDocumentEvents(document.id, services.documentEvents, {
    refreshDocument: () => session.refreshRemote(),
    reloadThreads,
    onError: (error) => toast(errorMessage(error)),
  });
  const [filter, setFilter] = useState<ThreadFilter>('open');
  const [activeThreadId, setActiveThreadId] = useState<string | null>(null);
  const [pending, setPending] = useState<Anchor | null>(null);
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
  const editable = me.owner && document.trashedAt === null && !transferring;
  const frame = useRef<number | null>(null);
  const spaceSlug = document.space.slug;
  const siblings = useLoad(`space-documents:${spaceSlug}:${shell.treeVersion}`, () =>
    services.documents.listInSpace(spaceSlug),
  );
  const spaceDocuments = siblings.state === 'ready' ? siblings.data : [];
  // A move made in the sidebar can take this document along; its new path then shows in the space's list first.
  const listedPath = spaceDocuments.find((candidate) => candidate.id === document.id)?.path;
  useEffect(() => {
    if (listedPath !== undefined && listedPath !== state.meta.path) void session.refreshMeta();
  }, [listedPath, state.meta.path, session]);

  useEffect(() => {
    if (state.lastSavedAt !== null) void reloadThreads();
  }, [state.lastSavedAt, reloadThreads]);

  useEffect(() => {
    if (!editable || state.status !== 'saved' || !session.hasPendingRevision()) return;
    const timer = window.setTimeout(() => void session.finalize(), session.finalizeDelay());
    return () => window.clearTimeout(timer);
  }, [editable, session, state.status, state.version, state.meta]);

  useEffect(() => {
    if (!editable) return;
    const editor = editorRef.current;
    const leave = (): void => {
      void session.finalize();
    };
    window.addEventListener('pagehide', leave);
    return () => {
      window.removeEventListener('pagehide', leave);
      const flush = editor?.flushSave();
      void Promise.resolve(flush).then(() => session.finalize());
    };
  }, [editable, session]);

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
      if (status !== 'saved') event.preventDefault();
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [session]);

  // Deep links: #thread-<id> opens the thread at its anchor, #block-<id> scrolls to the block.
  const hash = location.hash;
  useEffect(() => {
    const timer = setTimeout(() => {
      if (hash.startsWith('#thread-')) {
        const threadId = decodeURIComponent(hash.slice('#thread-'.length));
        setActiveThreadId(threadId);
        setFilter('all');
        showRail();
        editorRef.current?.scrollToThread(threadId);
      } else if (hash.startsWith('#block-')) {
        editorRef.current?.scrollToBlock(decodeURIComponent(hash.slice('#block-'.length)));
      }
    }, 50);
    return () => clearTimeout(timer);
  }, [hash, showRail]);

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
    setPending(anchor);
    setActiveThreadId(null);
    showRail();
  };

  /** Saves pending edits first; anchors and trashing assume the server has what the user sees. */
  const saveFirst = async (): Promise<boolean> => {
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
  };

  const startThread = async (body: string): Promise<boolean> => {
    if (pending === null) return false;
    // Anchors are offsets into the content the server has, so pending edits go out first.
    if (!(await saveFirst())) return false;
    const thread = await start(pending, body);
    if (thread === null) return false;
    setPending(null);
    setActiveThreadId(thread.id);
    if (filter === 'detached') setFilter('open');
    return true;
  };

  const reanchor = async (anchor: Anchor): Promise<void> => {
    if (reanchorThread === null) return;
    if (!(await saveFirst())) return;
    const done = await update(() => services.threads.reanchor(reanchorThread.id, anchor));
    if (done) {
      toast('댓글을 새 위치에 달았습니다');
      setActiveThreadId(reanchorThread.id);
      setReanchorThread(null);
      setFilter('open');
    }
  };

  const patch = (change: MetaPatch): void => {
    void session.patchMeta(change).then((saved) => {
      if (saved && (change.path !== undefined || change.title !== undefined)) shell.refreshTree();
    });
  };

  const openTransfer = async () => {
    if (!(await saveFirst())) return;
    try {
      setTransferSpaces(await services.spaces.list());
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

  const trash = async (): Promise<void> => {
    try {
      // Asked just now for the documents under it, not taken from the space's list on screen, which can be older and
      // stops at the most recent 1,000 documents.
      const along = (await services.documents.listInSpace(spaceSlug, { under: state.meta.path })).length;
      const question =
        along > 0
          ? `이 문서와 하위 문서 ${along}개를 휴지통으로 옮길까요? 스페이스 설정에서 함께 복원할 수 있습니다.`
          : '이 문서를 휴지통으로 옮길까요? 스페이스 설정에서 복원할 수 있습니다.';
      if (!window.confirm(question)) return;
      if (!(await saveFirst())) return;
      await services.documents.trash(document.id);
      shell.refreshTree();
      void navigate(`/s/${document.space.slug}`);
    } catch (error) {
      toast(errorMessage(error));
    }
  };

  const restoreFromTrash = async (): Promise<void> => {
    try {
      await services.documents.untrash(document.id);
      shell.refreshTree();
      window.location.reload();
    } catch (error) {
      toast(errorMessage(error));
    }
  };

  const resolveConflicts = async (choices: ReadonlyMap<string, ConflictChoice>): Promise<void> => {
    const { outcome } = await session.resolveConflicts(choices);
    if (outcome === 'saved') toast('선택한 내용으로 저장했습니다');
    else
      toast(
        outcome === 'conflict'
          ? '그사이 또 바뀌어 다시 골라야 합니다.'
          : '저장하지 못했습니다. 다시 저장하세요.',
      );
  };

  const openCount = threads.filter((thread) => thread.status !== 'resolved').length;
  const mentionLogins = mentionCandidates(me.login, document, threads);
  const highlightedThreadId = filterThreads(threads, filter).some((thread) => thread.id === activeThreadId)
    ? activeThreadId
    : null;
  const rail = (
    <CommentRail
      threads={threads}
      filter={filter}
      onFilter={setFilter}
      pending={pending}
      activeThreadId={activeThreadId}
      login={me.login}
      mentionLogins={mentionLogins}
      spaceSlug={document.space.slug}
      actions={actions((thread) => {
        setReanchorThread(thread);
        toast('본문에서 새 위치의 텍스트를 선택한 뒤 "여기에 다시 달기"를 누르세요');
        if (!wide) setRailOpen(false);
      })}
      aligned={wide}
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
    <div className={`doc-layout${docked ? '' : ' is-undocked'}`} ref={layoutRef}>
      <div className="doc-main">
        <div className="doc-body">
          <DocumentHeader
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
            onTrash={() => void trash()}
            onRetrySave={() => {
              void saveFirst().then((saved) => {
                if (saved) toast('저장했습니다');
              });
            }}
          />
          {document.trashedAt === null ? null : (
            <div className="warning-box doc-banner">
              휴지통에 있는 문서입니다. 읽기만 할 수 있습니다.{' '}
              <button type="button" className="btn btn-small" onClick={() => void restoreFromTrash()}>
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
              ”을(를) 다시 달 위치의 텍스트를 선택하세요.{' '}
              <button type="button" className="btn btn-small" onClick={() => setReanchorThread(null)}>
                취소
              </button>
            </div>
          )}
          {state.status === 'conflict' ? (
            <ConflictPanel
              conflicts={state.conflicts}
              mine={session.currentBlocks()}
              onResolve={resolveConflicts}
            />
          ) : null}
          {state.revisionNameSuggestion === null ? null : (
            <RevisionNamePrompt
              key={state.revisionNameSuggestion}
              documentId={document.id}
              revision={state.revisionNameSuggestion}
              onClose={() => session.dismissRevisionNameSuggestion()}
            />
          )}
          {transferSpaces === null ? null : (
            <TransferDialog
              spaces={transferSpaces}
              path={state.meta.path}
              onTransfer={transfer}
              onClose={() => setTransferSpaces(null)}
            />
          )}
          <DocumentEditor
            ref={editorRef}
            document={document}
            session={session}
            editable={editable}
            github={spaceInfo.state === 'ready' ? githubContextOf(spaceInfo.data) : undefined}
            onInput={() => inputTime.mark('body')}
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
      {docked ? (
        rail
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
          {railOpen ? <div className="rail-drawer">{rail}</div> : null}
        </>
      )}
    </div>
  );
}
