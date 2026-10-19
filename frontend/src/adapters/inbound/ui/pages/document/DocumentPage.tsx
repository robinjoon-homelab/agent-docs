import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react';
import { Navigate, useLocation, useNavigate, useParams } from 'react-router';
import type { MetaPatch } from '../../../../../application/document/ports/DocumentGateway';
import { isNotVisible } from '../../../../../application/shared/ports/errors';
import type { AgentDocument } from '../../../../../domain/document/document';
import { documentHref } from '../../../../../domain/document/document';
import type { ConflictChoice } from '../../../../../domain/document/merge';
import type { Anchor, Comment, Thread, ThreadFilter } from '../../../../../domain/thread/thread';
import { isDetached } from '../../../../../domain/thread/thread';
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
import type { ThreadActions } from './ThreadCard';
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
  return <DocumentWorkspace key={document.id} document={document} initialThreads={threads} />;
}

function replaceThread(threads: readonly Thread[], updated: Thread): Thread[] {
  return threads.map((thread) => (thread.id === updated.id ? updated : thread));
}

function useThreadState(documentId: string, initial: readonly Thread[]) {
  const services = useServices();
  const toast = useToast();
  const [threads, setThreads] = useState<readonly Thread[]>(initial);
  const guard = useCallback(
    async <T,>(work: () => Promise<T>): Promise<T | null> => {
      try {
        return await work();
      } catch (error) {
        toast(errorMessage(error));
        return null;
      }
    },
    [toast],
  );

  const start = async (anchor: Anchor, body: string): Promise<Thread | null> => {
    const result = await guard(() => services.threads.start(documentId, anchor, body));
    if (result === null || !result.ok) return null;
    setThreads((current) => [...current, result.value]);
    return result.value;
  };

  const update = async (work: () => Promise<Thread>): Promise<boolean> => {
    const updated = await guard(work);
    if (updated === null) return false;
    setThreads((current) => replaceThread(current, updated));
    return true;
  };

  const actions = (startReanchor: (thread: Thread) => void): ThreadActions => ({
    reply: async (thread: Thread, body: string) => {
      const result = await guard(() => services.threads.reply(thread, body));
      if (result === null || !result.ok) return false;
      setThreads((current) => replaceThread(current, result.value));
      return true;
    },
    resolve: async (thread: Thread) => {
      await update(() => services.threads.resolve(thread.id));
    },
    reopen: async (thread: Thread) => {
      await update(() => services.threads.reopen(thread.id));
    },
    editComment: async (thread: Thread, comment: Comment, body: string) => {
      const result = await guard(() => services.threads.editComment(thread, comment.id, body));
      if (result === null || !result.ok) return false;
      setThreads((current) => replaceThread(current, result.value));
      return true;
    },
    deleteComment: async (thread: Thread, comment: Comment) => {
      await update(() => services.threads.deleteComment(thread, comment.id));
    },
    startReanchor,
  });

  return { threads, start, update, actions };
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
  const state = useSyncExternalStore(session.subscribe, session.getState);
  const editorRef = useRef<DocumentEditorHandle>(null);
  const layoutRef = useRef<HTMLDivElement>(null);
  const { threads, start, update, actions } = useThreadState(document.id, initialThreads);
  const [filter, setFilter] = useState<ThreadFilter>('open');
  const [activeThreadId, setActiveThreadId] = useState<string | null>(null);
  const [pending, setPending] = useState<Anchor | null>(null);
  const [reanchorThread, setReanchorThread] = useState<Thread | null>(null);
  const [layoutVersion, setLayoutVersion] = useState(0);
  const wide = useMediaQuery('(min-width: 1280px)');
  const [railOpen, setRailOpen] = useState(false);
  const editable = me.owner && document.trashedAt === null;
  const frame = useRef<number | null>(null);

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
        setRailOpen(true);
        editorRef.current?.scrollToThread(threadId);
      } else if (hash.startsWith('#block-')) {
        editorRef.current?.scrollToBlock(decodeURIComponent(hash.slice('#block-'.length)));
      }
    }, 50);
    return () => clearTimeout(timer);
  }, [hash]);

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
    setRailOpen(true);
  };

  /** Saves pending edits first; anchors and trashing assume the server has what the user sees. */
  const saveFirst = async (): Promise<boolean> => {
    const outcome = (await editorRef.current?.flushSave()) ?? 'saved';
    if (outcome === 'saved') return true;
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

  const trash = async (): Promise<void> => {
    if (!window.confirm('이 문서를 휴지통으로 옮길까요? 스페이스 설정에서 복원할 수 있습니다.')) return;
    try {
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
    const { blocks, outcome } = await session.resolveConflicts(choices);
    editorRef.current?.setBlocks(blocks);
    if (outcome === 'saved') toast('선택한 내용으로 저장했습니다');
    else
      toast(
        outcome === 'conflict'
          ? '그사이 또 바뀌어 다시 골라야 합니다.'
          : '저장하지 못했습니다. 다시 저장하세요.',
      );
  };

  const openCount = threads.filter((thread) => thread.status !== 'resolved').length;
  const rail = (
    <CommentRail
      threads={threads}
      filter={filter}
      onFilter={setFilter}
      pending={pending}
      activeThreadId={activeThreadId}
      login={me.login}
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
    />
  );

  return (
    <div className={`doc-layout${wide ? '' : ' is-narrow'}`} ref={layoutRef}>
      <div className="doc-main">
        <div className="doc-body">
          <DocumentHeader
            document={document}
            state={state}
            editable={editable}
            onPatch={patch}
            onTrash={() => void trash()}
            onRetrySave={() => {
              void editorRef.current?.flushSave().then((outcome) => {
                if (outcome === 'saved') toast('저장했습니다');
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
          <DocumentEditor
            ref={editorRef}
            document={document}
            session={session}
            editable={editable}
            threads={threads}
            activeThreadId={activeThreadId}
            reanchoring={reanchorThread !== null}
            mentionLogins={mentionCandidates(me.login, document, threads)}
            onActivateThread={(threadId) => {
              setActiveThreadId(threadId);
              if (!wide) setRailOpen(true);
            }}
            onRequestComment={requestComment}
            onReanchor={(anchor) => void reanchor(anchor)}
            onContentChange={relayout}
            onMessage={toast}
          />
        </div>
      </div>
      {wide ? (
        rail
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
