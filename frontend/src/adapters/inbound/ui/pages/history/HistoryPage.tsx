import { DocumentPermissionContext } from '../document/DocumentPermissionContext';
import { canEdit, effectivePermission } from '../../../../../domain/space/permission';
import { DocumentAssetScope } from '../document/DocumentAssetScope';
import type { ComponentProps } from 'react';
import { useMemo, useState } from 'react';
import { HtmlMockup } from '../../editor/HtmlMockup';
import { Link, Navigate, useLocation, useNavigate, useParams, useSearchParams } from 'react-router';
import type { Comparison } from '../../../../../application/document/documentUseCases';
import { isNotVisible } from '../../../../../application/shared/ports/errors';
import type { AgentDocument, Revision } from '../../../../../domain/document/document';
import { documentHref, historyHref, threadHref } from '../../../../../domain/document/document';
import { buildDiffRows, DIFF_MARKER, diffCounts } from '../../../../../domain/document/diffView';
import type { RevisionActorFilter } from '../../../../../domain/document/history';
import {
  defaultRevision,
  orderedPair,
  REVISION_FILTER_LABEL,
  REVISION_SOURCE_LABEL,
  revisionPeriod,
  togglePair,
} from '../../../../../domain/document/history';
import { listOrdinals } from '../../../../../domain/document/listOrdinals';
import type { Thread } from '../../../../../domain/thread/thread';
import { anchorQuote } from '../../../../../domain/thread/thread';
import { estimateRestore } from '../../../../../domain/thread/restoreImpact';
import { useServices } from '../../../../../config/ServicesContext';
import { BlockView } from '../../blocks/BlockView';
import { ActorChip } from '../../common/ActorChip';
import { Dialog } from '../../common/Dialog';
import { Icon } from '../../common/Icon';
import { RelativeTime } from '../../common/RelativeTime';
import { ErrorBox, errorMessage, Loading, LoadError, NoAccessDocument } from '../../common/States';
import { useLoad } from '../../hooks/useLoad';
import { usePageTitle } from '../../hooks/usePageTitle';
import './history.css';
import { InlineDiff } from './InlineDiff';
import { RevisionDetails, RevisionLinks } from './RevisionDetails';
import { useSession } from '../../app/SessionContext';

const FILTERS: readonly RevisionActorFilter[] = ['all', 'human', 'agent'];

type Mode = 'current' | 'pair';

function threadLabel(threads: readonly Thread[], threadId: string): string {
  const thread = threads.find((candidate) => candidate.id === threadId);
  if (thread === undefined) return '스레드';
  const quote = anchorQuote(thread.anchor);
  const text = quote ?? thread.comments[0]?.body ?? '스레드';
  return text.length > 24 ? `${text.slice(0, 23)}…` : text;
}

function RevisionRow({
  revision,
  current,
  mode,
  selected,
  onSelect,
  spaceSlug,
  documentId,
  threads,
}: {
  revision: Revision;
  current: boolean;
  mode: Mode;
  selected: boolean;
  onSelect: () => void;
  spaceSlug: string;
  documentId: string;
  threads: readonly Thread[];
}) {
  return (
    <li className={`history-revision${selected ? ' selected' : ''}`}>
      <button type="button" className="history-revision-main" onClick={onSelect} aria-pressed={selected}>
        {mode === 'pair' ? (
          <input type="checkbox" checked={selected} readOnly tabIndex={-1} aria-hidden />
        ) : null}
        <span className="history-revision-text">
          <span className="history-revision-top">
            <ActorChip actor={revision.actor} />
            <span className="faint small">
              <RelativeTime iso={revision.createdAt} />
            </span>
          </span>
          <span className="history-revision-summary">
            {revision.name == null ? null : <strong>{revision.name} · </strong>}
            {revision.summary === '' ? '변경' : revision.summary}
          </span>
          <span className="faint small">
            rev {revision.revision} · {REVISION_SOURCE_LABEL[revision.source]}
            {current ? ' · 현재' : ''}
          </span>
        </span>
      </button>
      <RevisionLinks revision={revision} />
      {revision.threadIds.length === 0 ? null : (
        <div className="history-threads">
          {revision.threadIds.map((threadId) => (
            <Link key={threadId} className="chip" to={threadHref(spaceSlug, documentId, threadId)}>
              <Icon name="comment" size={12} />
              {threadLabel(threads, threadId)}
            </Link>
          ))}
        </div>
      )}
    </li>
  );
}

function UnifiedDiff({ text }: { text: string }) {
  return (
    <pre className="history-unified">
      {text.split('\n').map((line, index) => {
        const kind =
          line.startsWith('+++') || line.startsWith('---')
            ? 'meta'
            : line.startsWith('+')
              ? 'add'
              : line.startsWith('-')
                ? 'del'
                : line.startsWith('@@')
                  ? 'meta'
                  : 'ctx';
        return (
          <span key={index} className={`history-unified-${kind}`}>
            {line}
            {'\n'}
          </span>
        );
      })}
    </pre>
  );
}

function HistoryBlock(props: ComponentProps<typeof BlockView>) {
  const { assets } = useServices();
  const options = useMemo(
    () => ({
      htmlSource: (assetId: string) => assets.htmlSource(assetId),
      replaceHtml: () => Promise.reject(new Error('이력은 읽기 전용입니다.')),
    }),
    [assets],
  );
  const { block } = props;
  return block.type === 'image' && block.caption === 'mockup' ? (
    <HtmlMockup
      assetId={block.assetId}
      blockId={block.id}
      title={block.alt}
      editable={false}
      options={options}
    />
  ) : (
    <BlockView {...props} />
  );
}

function DiffBlocks({
  comparison,
  onlyChanged,
  initialRevision,
  onRestore,
}: {
  comparison: Comparison;
  onlyChanged: boolean;
  initialRevision: boolean;
  onRestore?: (blockId: string) => void;
}) {
  const rows = buildDiffRows(comparison.fromBlocks, comparison.toBlocks, comparison.diff);
  const ordinals = listOrdinals(rows.map((row) => row.block));
  const visible = onlyChanged ? rows.filter((row) => row.marker !== null) : rows;
  if (visible.length === 0)
    return (
      <p className="muted">
        {initialRevision ? '첫 리비전에 본문이 없습니다.' : '두 버전의 본문이 같습니다.'}
      </p>
    );
  return (
    <div className="history-diff">
      {visible.map((row) => (
        <div
          key={`${row.block.id}:${row.marker ?? 'same'}`}
          className={`history-row history-row-${row.marker ?? 'same'}`}
        >
          <span className="history-marker" aria-label={row.marker ?? undefined}>
            {row.marker === null ? '' : DIFF_MARKER[row.marker]}
          </span>
          <div className="history-row-body">
            {onRestore === undefined ||
            !comparison.fromBlocks.some((block) => block.id === row.block.id) ? null : (
              <button type="button" className="btn btn-small" onClick={() => onRestore(row.block.id)}>
                이 블록만 복원
              </button>
            )}
            <HistoryBlock
              block={row.block}
              changedCells={row.changedCells}
              ordinal={ordinals.get(row.block.id)}
            />
            {row.marker === 'modified' && row.before !== null && row.block.type !== 'table' ? (
              <InlineDiff before={row.before} after={row.block} />
            ) : null}
            {row.marker === 'modified' && row.before !== null && row.block.type !== 'table' ? (
              <details className="history-before">
                <summary>이전 내용</summary>
                <HistoryBlock block={row.before} />
              </details>
            ) : null}
          </div>
        </div>
      ))}
    </div>
  );
}

function RestoreDialog({
  documentId,
  revision,
  baseVersion,
  comparison,
  threads,
  onClose,
  onRestored,
}: {
  documentId: string;
  revision: number;
  /** The document version this page showed; the server refuses the restore if it moved on. */
  baseVersion: number;
  comparison: Comparison;
  threads: readonly Thread[];
  onClose: () => void;
  onRestored: () => void;
}) {
  const services = useServices();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const impact = estimateRestore(comparison.diff, comparison.fromBlocks, threads);

  const restore = async (): Promise<void> => {
    setBusy(true);
    setError(null);
    try {
      await services.documents.restore(documentId, revision, baseVersion);
      onRestored();
    } catch (failure) {
      setError(failure);
      setBusy(false);
    }
  };

  return (
    <Dialog
      open
      title={`rev ${revision}(으)로 복원`}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            취소
          </button>
          <button type="button" className="btn btn-primary" disabled={busy} onClick={() => void restore()}>
            복원
          </button>
        </>
      }
    >
      <p>
        블록 {impact.revertedBlocks}개가 되돌아가고 스레드 {impact.detachedThreads}개가 분리됩니다.
      </p>
      <p className="muted small">
        선택한 리비전의 본문, 제목, 상태, 태그, 담당자를 현재 스페이스와 경로에 새 리비전으로 저장합니다. 분리
        수는 추정치이며 복원 후 서버가 앵커를 다시 맞춥니다.
      </p>
      {error === null ? null : <p className="field-error">{errorMessage(error)}</p>}
    </Dialog>
  );
}

function HistoryView({ spaceSlug, document }: { spaceSlug: string; document: AgentDocument }) {
  const { me } = useSession();
  const editable = canEdit(effectivePermission(document.permission, me.owner));
  const services = useServices();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [filter, setFilter] = useState<RevisionActorFilter>('all');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [mode, setMode] = useState<Mode>('current');
  const [pair, setPair] = useState<readonly number[]>([]);
  const [view, setView] = useState<'blocks' | 'markdown'>('blocks');
  const [onlyChanged, setOnlyChanged] = useState(false);
  const [restoreOpen, setRestoreOpen] = useState(false);
  const [blockError, setBlockError] = useState<unknown>(null);
  const [blockBusy, setBlockBusy] = useState(false);

  const replyRevision = Number(searchParams.get('replyRev'));
  const linkedReply = Number.isSafeInteger(replyRevision) && replyRevision > 0;
  const requestedText = searchParams.get('rev');
  const requested =
    requestedText === null || Number.isNaN(Number(requestedText)) ? null : Number(requestedText);
  const revisions = useLoad(`revisions:${document.id}:${filter}:${fromDate}:${toDate}`, async () => {
    const items = await services.documents.revisions(document.id, {
      ...(filter === 'all' ? {} : { actor: filter }),
      ...revisionPeriod(fromDate, toDate),
    });
    // Leaving the editor can finalize a revision after the page's first document read.
    const current = await services.documents.get(document.id);
    return { documentId: document.id, items, currentRevision: current.revision };
  });
  const currentRevision = revisions.state === 'ready' ? revisions.data.currentRevision : document.revision;
  const threads = useLoad(`history-threads:${document.id}`, () => services.threads.list(document.id));
  const selected = linkedReply
    ? replyRevision
    : revisions.state === 'ready'
      ? defaultRevision(revisions.data.items, currentRevision, requested)
      : null;
  const range =
    mode === 'pair'
      ? orderedPair(pair)
      : linkedReply
        ? { from: Math.max(1, replyRevision - 1), to: replyRevision }
        : selected === null
          ? null
          : { from: selected, to: null };
  const comparisonKey =
    range === null ? 'none' : `${document.id}:${range.from}:${range.to ?? `current:${currentRevision}`}`;
  const comparison = useLoad<Comparison | null>(`compare:${comparisonKey}`, async () => {
    if (range === null) return null;
    const result = await services.documents.compare(document.id, range.from, range.to);
    if (range.to === null) {
      revisions.setData((current) =>
        current.documentId === document.id && result.diff.to > current.currentRevision
          ? { ...current, currentRevision: Math.max(current.currentRevision, result.diff.to) }
          : current,
      );
    }
    return result;
  });

  const initialRevision = linkedReply && mode === 'current' && range?.to === 1;
  const compared = comparison.state === 'ready' ? comparison.data?.diff : undefined;
  const displayedCurrentRevision =
    mode === 'current' && compared !== undefined ? Math.max(currentRevision, compared.to) : currentRevision;

  const select = (revision: number): void => {
    if (mode === 'pair') {
      setPair((current) => togglePair(current, revision));
      return;
    }
    setSearchParams({ rev: String(revision) }, { replace: true });
  };

  const restoreBlock = async (blockId: string): Promise<void> => {
    if (range === null || blockBusy) return;
    setBlockBusy(true);
    setBlockError(null);
    try {
      await services.documents.restoreBlock(document.id, range.from, blockId, document.version);
      void navigate(documentHref(spaceSlug, document.id));
    } catch (failure) {
      setBlockError(failure);
      setBlockBusy(false);
    }
  };

  const selectedRevision =
    revisions.state === 'ready'
      ? revisions.data.items.find(
          (revision) => revision.revision === (linkedReply && mode === 'current' ? range?.to : range?.from),
        )
      : undefined;
  const threadList = threads.state === 'ready' ? threads.data : [];
  const counts =
    comparison.state === 'ready' && comparison.data !== null ? diffCounts(comparison.data.diff) : null;

  return (
    <div className="page history-page">
      <div className="page-header">
        <div>
          <div className="faint small">
            <Link to={`/s/${spaceSlug}`}>{document.space.name}</Link> · {document.path}
          </div>
          <h1>{document.title === '' ? '제목 없음' : document.title} · 기록</h1>
        </div>
        <Link className="btn" to={documentHref(spaceSlug, document.id)}>
          문서로 돌아가기
        </Link>
      </div>

      <div className="history-layout">
        <aside className="history-sidebar">
          <div className="history-controls">
            <div className="segmented" role="group" aria-label="작성자 필터">
              {FILTERS.map((value) => (
                <button
                  key={value}
                  type="button"
                  aria-pressed={filter === value}
                  onClick={() => setFilter(value)}
                >
                  {REVISION_FILTER_LABEL[value]}
                </button>
              ))}
            </div>
            <label className="checkbox-row small">
              <input
                type="checkbox"
                checked={mode === 'pair'}
                onChange={(event) => {
                  setMode(event.target.checked ? 'pair' : 'current');
                  setPair(selected === null ? [] : [selected]);
                }}
              />
              두 개 비교
            </label>
            <div className="history-period" role="group" aria-label="기간">
              <input
                type="date"
                className="input"
                aria-label="시작일"
                value={fromDate}
                max={toDate === '' ? undefined : toDate}
                onChange={(event) => setFromDate(event.target.value)}
              />
              <span className="faint">~</span>
              <input
                type="date"
                className="input"
                aria-label="종료일"
                value={toDate}
                min={fromDate === '' ? undefined : fromDate}
                onChange={(event) => setToDate(event.target.value)}
              />
              {fromDate === '' && toDate === '' ? null : (
                <button
                  type="button"
                  className="btn btn-ghost btn-small"
                  onClick={() => {
                    setFromDate('');
                    setToDate('');
                  }}
                >
                  전체 기간
                </button>
              )}
            </div>
          </div>
          {revisions.state === 'loading' ? <Loading /> : null}
          {revisions.state === 'error' ? (
            <ErrorBox error={revisions.error} onRetry={revisions.reload} />
          ) : null}
          {revisions.state === 'ready' && revisions.data.items.length === 0 ? (
            <p className="muted small">해당하는 리비전이 없습니다.</p>
          ) : null}
          {revisions.state === 'ready' ? (
            <ul className="list history-revisions">
              {revisions.data.items.map((revision) => (
                <RevisionRow
                  key={revision.revision}
                  revision={revision}
                  current={revision.revision === displayedCurrentRevision}
                  mode={mode}
                  selected={
                    mode === 'pair' ? pair.includes(revision.revision) : revision.revision === selected
                  }
                  onSelect={() => select(revision.revision)}
                  spaceSlug={spaceSlug}
                  documentId={document.id}
                  threads={threadList}
                />
              ))}
            </ul>
          ) : null}
        </aside>

        <section className="history-main" aria-label="비교">
          {selectedRevision === undefined ? null : (
            <RevisionDetails
              key={`${selectedRevision.revision}:${selectedRevision.name ?? ''}`}
              documentId={document.id}
              revision={selectedRevision}
              onChanged={revisions.reload}
            />
          )}
          {blockError === null ? null : <ErrorBox error={blockError} />}
          {blockBusy ? <p className="muted">블록을 복원하고 있습니다.</p> : null}
          <div className="history-main-header">
            <strong>
              {range === null
                ? mode === 'pair'
                  ? '비교할 리비전 두 개를 고르세요'
                  : '리비전을 고르세요'
                : initialRevision
                  ? 'rev 1 · 첫 리비전'
                  : `rev ${compared?.from ?? range.from} → ${range.to === null ? `현재 (rev ${compared?.to ?? currentRevision})` : `rev ${compared?.to ?? range.to}`}`}
            </strong>
            {counts === null ? null : (
              <span className="history-counts small">
                <span className="history-count-added">+{counts.added}</span>
                <span className="history-count-modified">~{counts.modified}</span>
                <span className="history-count-removed">−{counts.removed}</span>
                {counts.moved === 0 ? null : <span aria-label="순서 변경">↕{counts.moved}</span>}
              </span>
            )}
            <span className="history-main-actions">
              <label className="checkbox-row small">
                <input
                  type="checkbox"
                  checked={onlyChanged && !initialRevision}
                  disabled={initialRevision}
                  onChange={(event) => setOnlyChanged(event.target.checked)}
                />
                변경만
              </label>
              <div className="segmented" role="group" aria-label="보기 형식">
                <button type="button" aria-pressed={view === 'blocks'} onClick={() => setView('blocks')}>
                  블록
                </button>
                <button type="button" aria-pressed={view === 'markdown'} onClick={() => setView('markdown')}>
                  마크다운
                </button>
              </div>
              {editable &&
              mode === 'current' &&
              !linkedReply &&
              selected !== null &&
              selected !== displayedCurrentRevision ? (
                <button
                  type="button"
                  className="btn btn-small"
                  disabled={comparison.state !== 'ready' || threads.state !== 'ready'}
                  onClick={() => setRestoreOpen(true)}
                >
                  <Icon name="history" size={14} />이 버전으로 복원
                </button>
              ) : null}
            </span>
          </div>
          {comparison.state === 'loading' ? <Loading /> : null}
          {comparison.state === 'error' ? (
            <ErrorBox error={comparison.error} onRetry={comparison.reload} />
          ) : null}
          {comparison.state === 'ready' && comparison.data !== null ? (
            view === 'markdown' ? (
              <UnifiedDiff text={comparison.data.diff.unified} />
            ) : (
              <DiffBlocks
                comparison={comparison.data}
                onlyChanged={onlyChanged && !initialRevision}
                initialRevision={initialRevision}
                onRestore={
                  mode === 'current' && !linkedReply && editable && !blockBusy
                    ? (blockId) => void restoreBlock(blockId)
                    : undefined
                }
              />
            )
          ) : null}
        </section>
      </div>

      {restoreOpen && selected !== null && comparison.state === 'ready' && comparison.data !== null ? (
        <RestoreDialog
          documentId={document.id}
          revision={selected}
          baseVersion={document.version}
          comparison={comparison.data}
          threads={threadList}
          onClose={() => setRestoreOpen(false)}
          onRestored={() => void navigate(documentHref(spaceSlug, document.id))}
        />
      ) : null}
    </div>
  );
}

export function HistoryPage() {
  const { space: slug = '', docId = '' } = useParams();
  const services = useServices();
  const location = useLocation();
  const document = useLoad(`history-document:${docId}`, () => services.documents.get(docId));
  usePageTitle(document.state === 'ready' ? `${document.data.title} 기록` : '기록');

  if (document.state === 'error') {
    return isNotVisible(document.error) ? (
      <NoAccessDocument />
    ) : (
      <LoadError error={document.error} onRetry={document.reload} />
    );
  }
  if (document.state === 'loading') return <Loading />;
  const currentSlug = document.data.space.slug;
  if (currentSlug !== slug) {
    return (
      <Navigate
        replace
        to={`${historyHref(currentSlug, document.data.id)}${location.search}${location.hash}`}
      />
    );
  }
  return (
    <DocumentAssetScope documentId={document.data.id}>
      <HistoryAccess document={document.data}>
        <HistoryView spaceSlug={currentSlug} document={document.data} />
      </HistoryAccess>
    </DocumentAssetScope>
  );
}

function HistoryAccess({ document, children }: { document: AgentDocument; children: React.ReactNode }) {
  const { me } = useSession();
  return (
    <DocumentPermissionContext value={effectivePermission(document.permission, me.owner)}>
      {children}
    </DocumentPermissionContext>
  );
}
