import { useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import type { Comparison } from '../../../../../application/document/documentUseCases';
import { isNotVisible } from '../../../../../application/shared/ports/errors';
import type { AgentDocument, Revision } from '../../../../../domain/document/document';
import { documentHref, threadHref } from '../../../../../domain/document/document';
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
import { useSpace } from '../../hooks/useSpace';
import './history.css';

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
            {revision.summary === '' ? '변경' : revision.summary}
          </span>
          <span className="faint small">
            rev {revision.revision} · {REVISION_SOURCE_LABEL[revision.source]}
            {current ? ' · 현재' : ''}
          </span>
        </span>
      </button>
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

function DiffBlocks({ comparison, onlyChanged }: { comparison: Comparison; onlyChanged: boolean }) {
  const rows = buildDiffRows(comparison.fromBlocks, comparison.toBlocks, comparison.diff);
  const ordinals = listOrdinals(rows.map((row) => row.block));
  const visible = onlyChanged ? rows.filter((row) => row.marker !== null) : rows;
  if (visible.length === 0) return <p className="muted">두 버전의 본문이 같습니다.</p>;
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
            <BlockView
              block={row.block}
              changedCells={row.changedCells}
              ordinal={ordinals.get(row.block.id)}
            />
            {row.marker === 'modified' && row.before !== null && row.block.type !== 'table' ? (
              <details className="history-before">
                <summary>이전 내용</summary>
                <BlockView block={row.before} />
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
  baseRevision,
  comparison,
  threads,
  onClose,
  onRestored,
}: {
  documentId: string;
  revision: number;
  /** The document revision this page showed; the server refuses the restore if it moved on. */
  baseRevision: number;
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
      await services.documents.restore(documentId, revision, baseRevision);
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
        복원은 새 리비전으로 저장되므로 지금 버전도 기록에 남습니다. 분리 수는 추정치이며 복원 후 서버가
        앵커를 다시 맞춥니다.
      </p>
      {error === null ? null : <p className="field-error">{errorMessage(error)}</p>}
    </Dialog>
  );
}

function HistoryView({ spaceSlug, document }: { spaceSlug: string; document: AgentDocument }) {
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

  const requestedText = searchParams.get('rev');
  const requested =
    requestedText === null || Number.isNaN(Number(requestedText)) ? null : Number(requestedText);
  const revisions = useLoad(`revisions:${document.id}:${filter}:${fromDate}:${toDate}`, () =>
    services.documents.revisions(document.id, {
      ...(filter === 'all' ? {} : { actor: filter }),
      ...revisionPeriod(fromDate, toDate),
    }),
  );
  const threads = useLoad(`history-threads:${document.id}`, () => services.threads.list(document.id));
  const selected =
    revisions.state === 'ready' ? defaultRevision(revisions.data, document.revision, requested) : null;
  const range = mode === 'pair' ? orderedPair(pair) : selected === null ? null : { from: selected, to: null };
  const comparisonKey = range === null ? 'none' : `${document.id}:${range.from}:${range.to ?? 'current'}`;
  const comparison = useLoad<Comparison | null>(`compare:${comparisonKey}`, () =>
    range === null ? Promise.resolve(null) : services.documents.compare(document.id, range.from, range.to),
  );

  const select = (revision: number): void => {
    if (mode === 'pair') {
      setPair((current) => togglePair(current, revision));
      return;
    }
    setSearchParams({ rev: String(revision) }, { replace: true });
  };

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
          {revisions.state === 'ready' && revisions.data.length === 0 ? (
            <p className="muted small">해당하는 리비전이 없습니다.</p>
          ) : null}
          {revisions.state === 'ready' ? (
            <ul className="list history-revisions">
              {revisions.data.map((revision) => (
                <RevisionRow
                  key={revision.revision}
                  revision={revision}
                  current={revision.revision === document.revision}
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
          <div className="history-main-header">
            <strong>
              {range === null
                ? mode === 'pair'
                  ? '비교할 리비전 두 개를 고르세요'
                  : '리비전을 고르세요'
                : `rev ${range.from} → ${range.to === null ? `현재 (rev ${document.revision})` : `rev ${range.to}`}`}
            </strong>
            {counts === null ? null : (
              <span className="history-counts small">
                <span className="history-count-added">+{counts.added}</span>
                <span className="history-count-modified">~{counts.modified}</span>
                <span className="history-count-removed">−{counts.removed}</span>
              </span>
            )}
            <span className="history-main-actions">
              <label className="checkbox-row small">
                <input
                  type="checkbox"
                  checked={onlyChanged}
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
              {mode === 'current' && selected !== null && selected !== document.revision ? (
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
              <DiffBlocks comparison={comparison.data} onlyChanged={onlyChanged} />
            )
          ) : null}
        </section>
      </div>

      {restoreOpen && selected !== null && comparison.state === 'ready' && comparison.data !== null ? (
        <RestoreDialog
          documentId={document.id}
          revision={selected}
          baseRevision={document.revision}
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
  const space = useSpace(slug);
  const document = useLoad(`history-document:${docId}`, () => services.documents.get(docId));
  usePageTitle(document.state === 'ready' ? `${document.data.title} 기록` : '기록');

  if (space.state === 'error') return <LoadError error={space.error} onRetry={space.reload} />;
  if (document.state === 'error') {
    return isNotVisible(document.error) ? (
      <NoAccessDocument />
    ) : (
      <LoadError error={document.error} onRetry={document.reload} />
    );
  }
  if (space.state === 'loading' || document.state === 'loading') return <Loading />;
  return <HistoryView spaceSlug={space.data.slug} document={document.data} />;
}
