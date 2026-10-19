import { Link, useParams } from 'react-router';
import { activityHref } from '../../../../../domain/activity/activity';
import type { DocumentSummary } from '../../../../../domain/document/document';
import { documentHref, threadHref } from '../../../../../domain/document/document';
import type { PathNode } from '../../../../../domain/document/pathTree';
import { buildPathTree, sortByRecent } from '../../../../../domain/document/pathTree';
import type { Space } from '../../../../../domain/space/space';
import { spaceInitial } from '../../../../../domain/space/space';
import { commentText } from '../../../../../domain/thread/thread';
import { useServices } from '../../../../../config/ServicesContext';
import { useShell } from '../../app/ShellContext';
import { ActorChip } from '../../common/ActorChip';
import { Icon } from '../../common/Icon';
import { RelativeTime } from '../../common/RelativeTime';
import { EmptyState, ErrorBox, Loading, LoadError } from '../../common/States';
import { StatusBadge } from '../../common/StatusBadge';
import { useLoad } from '../../hooks/useLoad';
import { usePageTitle } from '../../hooks/usePageTitle';
import { useSpace } from '../../hooks/useSpace';
import { SpaceLinks } from './SpaceLinks';
import './space.css';

const RECENT_LIMIT = 8;
const THREADS_PER_DOCUMENT = 3;

function DocumentTree({ nodes, spaceSlug }: { nodes: readonly PathNode[]; spaceSlug: string }) {
  return (
    <ul className="space-tree">
      {nodes.map((node) => (
        <li key={node.path}>
          {node.document === null ? (
            <span className="space-tree-folder">
              <Icon name="folder" size={14} />
              {node.name}
            </span>
          ) : (
            <Link className="space-tree-doc" to={documentHref(spaceSlug, node.document.id)}>
              <Icon name="doc" size={14} />
              <span className="space-tree-title">
                {node.document.title === '' ? node.name : node.document.title}
              </span>
              <span className="faint small mono space-tree-path">{node.name}</span>
            </Link>
          )}
          {node.children.length > 0 ? <DocumentTree nodes={node.children} spaceSlug={spaceSlug} /> : null}
        </li>
      ))}
    </ul>
  );
}

function RecentDocuments({
  documents,
  spaceSlug,
}: {
  documents: readonly DocumentSummary[];
  spaceSlug: string;
}) {
  return (
    <ul className="list card space-list">
      {documents.map((document) => (
        <li key={document.id}>
          <Link className="list-row list-row-link" to={documentHref(spaceSlug, document.id)}>
            <div className="row-main">
              <div className="row-title">{document.title === '' ? document.path : document.title}</div>
              <div className="row-sub">{document.path}</div>
            </div>
            <span className="space-row-actor">
              <ActorChip actor={document.updatedBy} />
            </span>
            <StatusBadge status={document.status} />
            <span className="faint small space-row-time">
              <RelativeTime iso={document.updatedAt} />
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}

/** Unresolved threads on the most recently updated documents. */
function OpenComments({
  documents,
  spaceSlug,
}: {
  documents: readonly DocumentSummary[];
  spaceSlug: string;
}) {
  const services = useServices();
  const key = documents.map((document) => document.id).join(',');
  const summaries = useLoad(`space-home:threads:${key}`, () => services.threads.openAcross(documents));

  if (summaries.state === 'loading') return <Loading />;
  if (summaries.state === 'error') return <ErrorBox error={summaries.error} onRetry={summaries.reload} />;
  if (summaries.data.length === 0) return <p className="faint small">열린 댓글이 없습니다.</p>;
  return (
    <ul className="list card space-list">
      {summaries.data.map(({ document, threads }) => (
        <li key={document.id} className="space-thread-group">
          <div className="space-thread-head">
            <Link to={documentHref(spaceSlug, document.id)} className="row-title">
              {document.title === '' ? document.path : document.title}
            </Link>
            <span className="badge badge-neutral">열린 댓글 {threads.length}</span>
          </div>
          <ul className="space-thread-list">
            {threads.slice(0, THREADS_PER_DOCUMENT).map((thread) => {
              const first = thread.comments[0];
              return (
                <li key={thread.id}>
                  <Link className="space-thread-link" to={threadHref(spaceSlug, document.id, thread.id)}>
                    <Icon name="comment" size={13} />
                    <span className="space-thread-excerpt">
                      {first === undefined ? '댓글' : commentText(first)}
                    </span>
                  </Link>
                </li>
              );
            })}
          </ul>
        </li>
      ))}
    </ul>
  );
}

function RecentActivity({ spaceSlug }: { spaceSlug: string }) {
  const services = useServices();
  const activity = useLoad(`space-home:activity:${spaceSlug}`, () => services.activity.recent(spaceSlug, 20));

  if (activity.state === 'loading') return <Loading />;
  if (activity.state === 'error') return <ErrorBox error={activity.error} onRetry={activity.reload} />;
  if (activity.data.length === 0) return <p className="faint small">최근 활동이 없습니다.</p>;
  return (
    <ul className="list card space-list">
      {activity.data.map((entry) => {
        const href = activityHref(entry);
        const summary = (
          <span className="space-activity-summary">
            {entry.documentTitle === null ? null : <strong>{entry.documentTitle} </strong>}
            {entry.summary}
          </span>
        );
        return (
          <li key={entry.id} className="space-activity">
            <div className="space-activity-head">
              <ActorChip actor={entry.actor} />
              <span className="faint small space-row-time">
                <RelativeTime iso={entry.at} />
              </span>
            </div>
            {href === null ? summary : <Link to={href}>{summary}</Link>}
          </li>
        );
      })}
    </ul>
  );
}

function SpaceHome({ space }: { space: Space }) {
  const services = useServices();
  const shell = useShell();
  const documents = useLoad(`space-home:docs:${space.slug}`, () =>
    services.documents.listInSpace(space.slug),
  );
  usePageTitle(space.name);

  const newDocument = (
    <button type="button" className="btn btn-primary" onClick={() => shell.openNewDocument(space.slug)}>
      <Icon name="plus" size={14} />새 문서
    </button>
  );

  return (
    <div className="page">
      <header className="space-header">
        <div className="space-title">
          <span className="space-icon space-title-icon">{spaceInitial(space.name)}</span>
          <div className="space-title-text">
            <h1>{space.name}</h1>
            {space.description === '' ? null : <p className="muted">{space.description}</p>}
          </div>
          <div className="space-actions">
            {documents.state === 'ready' && documents.data.length > 0 ? newDocument : null}
            <Link className="btn" to={`/s/${encodeURIComponent(space.slug)}/settings`}>
              <Icon name="settings" size={14} />
              설정
            </Link>
          </div>
        </div>
        <SpaceLinks links={space.links} />
      </header>
      {documents.state === 'loading' ? <Loading /> : null}
      {documents.state === 'error' ? <ErrorBox error={documents.error} onRetry={documents.reload} /> : null}
      {documents.state === 'ready' && documents.data.length === 0 ? (
        <EmptyState
          title="아직 문서가 없습니다"
          actions={
            <>
              {newDocument}
              <Link className="btn" to="/settings/agents">
                <Icon name="agent" size={14} />
                에이전트 연결
              </Link>
            </>
          }
        >
          <p className="muted" style={{ margin: 0 }}>
            문서를 직접 쓰거나 에이전트를 연결해 이 스페이스에 쓰게 하세요.
          </p>
        </EmptyState>
      ) : null}
      {documents.state === 'ready' && documents.data.length > 0 ? (
        <div className="space-columns">
          <div>
            <section className="section" aria-labelledby="space-recent">
              <h2 className="section-title" id="space-recent">
                최근 문서
              </h2>
              <RecentDocuments
                documents={sortByRecent(documents.data).slice(0, RECENT_LIMIT)}
                spaceSlug={space.slug}
              />
            </section>
            <section className="section" aria-labelledby="space-tree">
              <h2 className="section-title" id="space-tree">
                문서 트리
              </h2>
              <div className="card">
                <DocumentTree nodes={buildPathTree(documents.data)} spaceSlug={space.slug} />
              </div>
            </section>
          </div>
          <div>
            <section className="section" aria-labelledby="space-threads">
              <h2 className="section-title" id="space-threads">
                열린 댓글
              </h2>
              <OpenComments
                documents={sortByRecent(documents.data).slice(0, RECENT_LIMIT)}
                spaceSlug={space.slug}
              />
            </section>
            <section className="section" aria-labelledby="space-activity">
              <h2 className="section-title" id="space-activity">
                최근 활동
              </h2>
              <RecentActivity spaceSlug={space.slug} />
            </section>
          </div>
        </div>
      ) : null}
    </div>
  );
}

/** S3: what is in a space and what needs attention. */
export function SpaceHomePage() {
  const { space: slug = '' } = useParams();
  const space = useSpace(slug);
  if (space.state === 'loading') return <Loading />;
  if (space.state === 'error') return <LoadError error={space.error} onRetry={space.reload} />;
  return <SpaceHome space={space.data} />;
}
