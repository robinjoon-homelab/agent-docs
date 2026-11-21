import type { Block } from '../../../../../domain/document/block';
import { useFlowCommentTargets } from './useFlowCommentTargets';
import type { ReactNode, ComponentProps } from 'react';
import { useDocumentPermission } from './DocumentPermissionContext';
import { canComment } from '../../../../../domain/space/permission';
import { useLayoutEffect, useRef } from 'react';
import type { Anchor, Thread, ThreadFilter } from '../../../../../domain/thread/thread';
import { THREAD_FILTER_LABEL, anchorQuote, filterThreads } from '../../../../../domain/thread/thread';
import { layoutRail } from '../../../../../domain/thread/railLayout';
import { Icon } from '../../common/Icon';
import type { ThreadActions } from './ThreadCard';
import { ThreadCard } from './ThreadCard';
import { CommentEditor } from './CommentEditor';

const FILTERS: readonly ThreadFilter[] = ['open', 'all', 'detached'];
const CARD_GAP = 10;
const PENDING_ID = '__pending__';

export type RailMeasure = {
  /** Viewport top of a thread's anchor, or null when it has no place in the text. */
  readonly threadTop: (thread: Thread) => number | null;
  readonly anchorTop: (anchor: Anchor) => number | null;
};

/**
 * Right-hand comment rail. On wide screens cards sit next to their anchors (pushed down when they
 * would overlap); in the drawer used on narrower screens they simply stack.
 */
function CommentRailContent({
  flowTargets,
  flowBlocks,
  flowScope,
  threadHeading,
  threads,
  filter,
  onFilter,
  pending,
  activeThreadId,
  login,
  mentionLogins,
  spaceSlug,
  actions,
  aligned,
  measure,
  layoutVersion,
  onActivate,
  onStart,
  onCancelPending,
  onDocumentComment,
  onClose,
  onCollapse,
}: {
  flowTargets?: Map<string, string>;
  flowBlocks?: readonly Block[];
  flowScope?: string;
  threadHeading?: (thread: Thread) => ReactNode;
  threads: readonly Thread[];
  filter: ThreadFilter;
  onFilter: (filter: ThreadFilter) => void;
  pending: Anchor | null;
  activeThreadId: string | null;
  login: string;
  mentionLogins: readonly string[];
  spaceSlug: string;
  actions: ThreadActions;
  aligned: boolean;
  measure: RailMeasure;
  layoutVersion: number;
  onActivate: (thread: Thread) => void;
  onStart: (body: string) => Promise<boolean>;
  onCancelPending: () => void;
  onDocumentComment: () => void;
  onClose?: () => void;
  /** Folds the docked rail away; given only where the rail sits next to the document. */
  onCollapse?: () => void;
}) {
  const commentable = canComment(useDocumentPermission());
  const targets = flowTargets ?? new Map<string, string>();
  const available = flowBlocks
    ? threads
        .filter((t) =>
          filter === 'detached'
            ? targets.get(t.id) === 'screenDeleted'
            : targets.get(t.id) !== 'screenDeleted',
        )
        .filter(
          (t) => flowScope === undefined || (t.anchor.kind !== 'document' && t.anchor.blockId === flowScope),
        )
    : threads;
  const visible = flowBlocks && filter === 'detached' ? available : filterThreads(available, filter);
  const listRef = useRef<HTMLDivElement>(null);
  const latestMeasure = useRef(measure);

  useLayoutEffect(() => {
    latestMeasure.current = measure;
  });

  useLayoutEffect(() => {
    const list = listRef.current;
    if (list === null) return;
    const cards = Array.from(list.querySelectorAll<HTMLElement>('[data-rail-id]'));
    const place = (): void => {
      if (!aligned) {
        for (const card of cards) card.style.top = '';
        list.style.minHeight = '';
        return;
      }
      const byId = new Map(threads.map((thread) => [thread.id, thread]));
      const origin = list.getBoundingClientRect().top;
      const relative = (top: number | null): number | null =>
        top === null ? null : Math.max(0, top - origin);
      const items = cards.map((card) => {
        const id = card.dataset.railId ?? '';
        const thread = byId.get(id);
        const anchorTop = relative(
          id === PENDING_ID
            ? pending === null
              ? null
              : latestMeasure.current.anchorTop(pending)
            : thread === undefined
              ? null
              : latestMeasure.current.threadTop(thread),
        );
        return { id, anchorTop, height: card.offsetHeight };
      });
      const placements = layoutRail(items, CARD_GAP);
      let bottom = 0;
      for (const placement of placements) {
        const card = cards.find((candidate) => candidate.dataset.railId === placement.id);
        if (card === undefined) continue;
        card.style.top = `${placement.top}px`;
        bottom = Math.max(bottom, placement.top + card.offsetHeight);
      }
      list.style.minHeight = `${bottom + 40}px`;
    };
    place();
    const observer = new ResizeObserver(place);
    for (const card of cards) observer.observe(card);
    return () => observer.disconnect();
  }, [threads, filter, pending, aligned, layoutVersion, activeThreadId]);

  const counts: Record<ThreadFilter, number> = {
    open: flowBlocks
      ? threads.filter((t) => t.status !== 'resolved' && targets.get(t.id) !== 'screenDeleted').length
      : filterThreads(threads, 'open').length,
    all: flowBlocks ? threads.filter((t) => targets.get(t.id) !== 'screenDeleted').length : threads.length,
    detached: flowBlocks
      ? threads.filter((t) => targets.get(t.id) === 'screenDeleted').length
      : filterThreads(threads, 'detached').length,
  };

  return (
    <aside
      className={`comment-rail${aligned ? ' is-aligned' : ''}${flowScope !== undefined ? ' is-flow-scoped' : ''}`}
      aria-label="댓글"
    >
      {/* The empty-state message is pinned with the header: the rail is as tall as the document, so in the list it
          would scroll away with the first screen. */}
      <div className="rail-top">
        <div className="rail-header">
          <div className="segmented" role="group" aria-label="댓글 필터">
            {FILTERS.map((option) => (
              <button
                key={option}
                type="button"
                aria-pressed={filter === option}
                onClick={() => onFilter(option)}
              >
                {flowBlocks && option === 'detached' ? '삭제된 화면' : THREAD_FILTER_LABEL[option]}{' '}
                {counts[option]}
              </button>
            ))}
          </div>
          <button
            type="button"
            className="icon-btn"
            onClick={onDocumentComment}
            disabled={!commentable}
            title="문서에 댓글"
            aria-label="문서에 댓글"
          >
            <Icon name="plus" />
          </button>
          {onCollapse === undefined ? null : (
            <button
              type="button"
              className="icon-btn"
              onClick={onCollapse}
              aria-label="댓글 접기"
              title="댓글 접기"
            >
              <Icon name="sidebar" />
            </button>
          )}
          {onClose === undefined ? null : (
            <button type="button" className="icon-btn" onClick={onClose} aria-label="댓글 닫기">
              <Icon name="close" />
            </button>
          )}
        </div>
        {flowBlocks && flowScope === undefined && (
          <p className="flow-comments-hint">
            {filter === 'detached'
              ? '삭제된 화면에 달렸던 댓글입니다. 누르면 댓글이 달린 때의 화면을 보여 줍니다.'
              : '노란 앵커를 누르면 그 화면과 요소로 이동합니다. 삭제된 요소의 댓글은 여기에서만 보입니다.'}
          </p>
        )}
        {visible.length === 0 && pending === null ? (
          <p className="rail-empty faint small">
            {threads.length === 0
              ? '댓글이 없습니다. 텍스트를 선택하고 댓글을 남기세요.'
              : filter === 'detached'
                ? '분리된 댓글이 없습니다.'
                : '열린 댓글이 없습니다.'}
          </p>
        ) : null}
      </div>
      <div className="rail-list" ref={listRef}>
        {pending === null ? null : (
          <div className="rail-item" data-rail-id={PENDING_ID}>
            <article className="thread-card is-active is-pending">
              <header className="thread-head">
                <span className="thread-quote">
                  {pending.kind === 'document'
                    ? '문서 전체'
                    : pending.kind === 'block'
                      ? '블록'
                      : (anchorQuote(pending) ?? '')}
                </span>
              </header>
              <fieldset disabled={!commentable} style={{ border: 0, padding: 0, margin: 0 }}>
                <CommentEditor
                  mentionLogins={mentionLogins}
                  initial=""
                  placeholder="댓글을 남기세요. @로 멘션 (⌘Enter)"
                  submitLabel="댓글"
                  autoFocus
                  onSubmit={onStart}
                  onCancel={onCancelPending}
                />
              </fieldset>
            </article>
          </div>
        )}
        {visible.map((thread) => (
          <div className="rail-item" key={thread.id} data-rail-id={thread.id}>
            {threadHeading?.(thread)}
            <ThreadCard
              compact={false}
              anchorText={
                flowBlocks
                  ? thread.anchor.kind === 'document'
                    ? '문서 전체'
                    : (flowBlocks.flatMap((b) =>
                        thread.anchor.kind !== 'document' && b.id === thread.anchor.blockId
                          ? b.type === 'flowScreen'
                            ? [`${b.screenId} ${b.name} · 화면 전체`]
                            : b.type === 'flowElement'
                              ? [b.label]
                              : []
                          : [],
                      )[0] ?? '삭제된 대상')
                  : undefined
              }
              thread={
                flowBlocks && thread.anchor.kind === 'block'
                  ? {
                      ...thread,
                      resolution: { state: 'attached' },
                      anchor: {
                        ...thread.anchor,
                        quote:
                          (
                            flowBlocks.find(
                              (b) => thread.anchor.kind === 'block' && b.id === thread.anchor.blockId,
                            ) as { desc?: string } | undefined
                          )?.desc ?? '',
                      },
                    }
                  : thread
              }
              active={thread.id === activeThreadId}
              login={login}
              mentionLogins={mentionLogins}
              spaceSlug={spaceSlug}
              actions={
                flowBlocks &&
                (targets.get(thread.id) === 'elementDeleted' || targets.get(thread.id) === 'screenDeleted')
                  ? { ...actions, suggest: undefined }
                  : actions
              }
              onActivate={() => onActivate(thread)}
            />
          </div>
        ))}
      </div>
    </aside>
  );
}

function FlowCommentRail(props: ComponentProps<typeof CommentRailContent>) {
  const targets = useFlowCommentTargets(props.threads, props.flowBlocks);
  return <CommentRailContent {...props} flowTargets={targets} />;
}
export function CommentRail(props: ComponentProps<typeof CommentRailContent>) {
  return props.flowBlocks ? <FlowCommentRail {...props} /> : <CommentRailContent {...props} />;
}
