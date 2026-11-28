import { FlowIcon } from '../pages/document/FlowIcon';
import { useSpacePermissions } from './SpacePermissionsContext';
import { canEdit, permissionInSpace } from '../../../../domain/space/permission';
import type { DragEvent } from 'react';
import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, useMatch, useNavigate } from 'react-router';
import type { DocumentSummary } from '../../../../domain/document/document';
import type { PathNode } from '../../../../domain/document/pathTree';
import {
  buildPathTree,
  descendantCount,
  isFolderOpen,
  isWithinPath,
} from '../../../../domain/document/pathTree';
import { PATH_RULE_TEXT, documentHref } from '../../../../domain/document/document';
import type { Space } from '../../../../domain/space/space';
import { pickableSpaces, spaceInitial } from '../../../../domain/space/space';
import { useServices } from '../../../../config/ServicesContext';
import { ActorChip } from '../common/ActorChip';
import { Icon } from '../common/Icon';
import { errorMessage } from '../common/States';
import { useToast } from '../common/Toast';
import { TrashDocumentDialog } from '../common/TrashDocumentDialog';
import { useLoad } from '../hooks/useLoad';
import { useSession } from './SessionContext';
import { useOpenDocument, useShell } from './ShellContext';

function SpaceSwitcher({ current, spaces }: { current: Space | null; spaces: readonly Space[] }) {
  const { me } = useSession();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const close = (event: MouseEvent): void => {
      if (ref.current !== null && !ref.current.contains(event.target as Node)) setOpen(false);
    };
    window.addEventListener('mousedown', close);
    return () => window.removeEventListener('mousedown', close);
  }, [open]);

  return (
    <div ref={ref} style={{ position: 'relative', flex: 1, minWidth: 0 }}>
      <button
        type="button"
        className="nav-item space-switcher"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        <span className="space-icon">{current === null ? 'A' : spaceInitial(current.name)}</span>
        <span className="name">{current === null ? 'Agent Docs' : current.name}</span>
        <Icon name="chevron-down" size={14} />
      </button>
      {open ? (
        <div className="popover" role="menu" style={{ top: 36, left: 0, right: 0 }}>
          {pickableSpaces(spaces).map((space) => (
            <Link
              key={space.id}
              role="menuitem"
              className="menu-item"
              to={`/s/${space.slug}`}
              onClick={() => setOpen(false)}
            >
              <span className="space-icon">{spaceInitial(space.name)}</span>
              {space.name}
              {space.id === current?.id ? <Icon name="check" size={14} /> : null}
            </Link>
          ))}
          {pickableSpaces(spaces).length > 0 ? <div className="menu-separator" /> : null}
          {me.owner ? (
            <Link role="menuitem" className="menu-item" to="/?new=space" onClick={() => setOpen(false)}>
              <Icon name="plus" size={14} />새 스페이스
            </Link>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

/** Drag payload: the id of a document being moved in the tree. */
const DOCUMENT_DRAG_TYPE = 'application/x-agent-docs-document';
const TRASH_TARGET = Symbol('trash');

type Dragged = { readonly id: string; readonly path: string };
type DragSource = Dragged & { readonly treeVersion: number };
type DropTarget = string | typeof TRASH_TARGET;

type TreeDrag = {
  readonly target: DropTarget | null;
  readonly setTarget: (target: DropTarget | null) => void;
  readonly source: DocumentSummary | undefined;
  readonly setDragged: (dragged: Dragged | null) => void;
  readonly drop: (document: DocumentSummary, target: DropTarget) => void;
};

type TreeFolding = {
  readonly isOpen: (folder: string) => boolean;
  readonly toggle: (folder: string) => void;
};

function FoldIcon({ open }: { open: boolean }) {
  return <Icon name={open ? 'chevron-down' : 'chevron-right'} size={14} />;
}

function dropProps(target: DropTarget, drag: TreeDrag) {
  const accepts = (source: DocumentSummary): boolean =>
    target === TRASH_TARGET || !isWithinPath(target, source.path);
  return {
    onDragOver: (event: DragEvent) => {
      if (!event.dataTransfer.types.includes(DOCUMENT_DRAG_TYPE)) return;
      event.stopPropagation();
      // The browser only exposes the payload on drop, so use the local drag to check the source during hover.
      if (drag.source === undefined || !accepts(drag.source)) {
        event.dataTransfer.dropEffect = 'none';
        if (drag.target !== null) drag.setTarget(null);
        return;
      }
      event.preventDefault();
      event.dataTransfer.dropEffect = 'move';
      if (drag.target !== target) drag.setTarget(target);
    },
    onDrop: (event: DragEvent) => {
      if (!event.dataTransfer.types.includes(DOCUMENT_DRAG_TYPE)) return;
      event.preventDefault();
      event.stopPropagation();
      drag.setTarget(null);
      drag.setDragged(null);
      const source = drag.source;
      if (
        source !== undefined &&
        event.dataTransfer.getData(DOCUMENT_DRAG_TYPE) === source.id &&
        accepts(source)
      )
        drag.drop(source, target);
    },
  };
}

function TreeNodes({
  nodes,
  spaceSlug,
  drag,
  folding,
  onAddChild,
  editable,
}: {
  nodes: readonly PathNode[];
  spaceSlug: string;
  drag: TreeDrag;
  folding: TreeFolding;
  editable: boolean;
  onAddChild: (parent: string) => void;
}) {
  return (
    <ul className="tree">
      {nodes.map((node) => {
        const isFolder = node.children.length > 0;
        const open = isFolder && folding.isOpen(node.path);
        const targetClass = drag.source !== undefined && drag.target === node.path ? ' drop-target' : '';
        const name = node.document === null || node.document.title === '' ? node.name : node.document.title;
        return (
          <li key={node.path} {...(editable ? dropProps(node.path, drag) : {})}>
            {node.document === null ? (
              <button
                type="button"
                className={`nav-item tree-folder${targetClass}`}
                title={node.path}
                aria-expanded={open}
                onClick={() => folding.toggle(node.path)}
              >
                <FoldIcon open={open} />
                <Icon name="folder" size={14} />
                {node.name}
              </button>
            ) : (
              <div className="tree-row">
                {isFolder ? (
                  <button
                    type="button"
                    className="tree-toggle"
                    aria-expanded={open}
                    aria-label={`${name} ${open ? '접기' : '펼치기'}`}
                    onClick={() => folding.toggle(node.path)}
                  >
                    <FoldIcon open={open} />
                  </button>
                ) : null}
                <NavLink
                  className={({ isActive }) => `nav-item${isActive ? ' active' : ''}${targetClass}`}
                  to={documentHref(spaceSlug, node.document.id)}
                  title={editable ? `${node.path} · 다른 문서나 휴지통으로 끌어 옮기기` : node.path}
                  draggable={editable}
                  onDragStart={(event) => {
                    if (!editable || node.document === null) {
                      event.preventDefault();
                      return;
                    }
                    event.dataTransfer.setData(DOCUMENT_DRAG_TYPE, node.document.id);
                    event.dataTransfer.effectAllowed = 'move';
                    drag.setDragged({ id: node.document.id, path: node.path });
                  }}
                  onDragEnd={() => {
                    drag.setTarget(null);
                    drag.setDragged(null);
                  }}
                >
                  <span style={node.document.type === 'flow' ? { color: 'var(--link)' } : undefined}>
                    {node.document.type === 'flow' ? (
                      <FlowIcon name="flow" size={15} />
                    ) : (
                      <Icon name="doc" size={14} />
                    )}
                  </span>
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {name}
                  </span>
                </NavLink>
                {editable ? (
                  <button
                    type="button"
                    className="tree-add"
                    aria-label={`${name} 아래에 새 문서`}
                    title="하위 문서 추가"
                    onClick={() => onAddChild(node.path)}
                  >
                    <Icon name="plus" size={14} />
                  </button>
                ) : null}
              </div>
            )}
            {open ? (
              <TreeNodes
                editable={editable}
                nodes={node.children}
                spaceSlug={spaceSlug}
                drag={drag}
                folding={folding}
                onAddChild={onAddChild}
              />
            ) : null}
          </li>
        );
      })}
    </ul>
  );
}

function SpaceNavigation({
  spaceSlug,
  version,
  editable,
}: {
  spaceSlug: string;
  version: number;
  editable: boolean;
}) {
  const services = useServices();
  const shell = useShell();
  const openDocument = useOpenDocument();
  const { me } = useSession();
  const navigate = useNavigate();
  const toast = useToast();
  const [target, setTarget] = useState<DropTarget | null>(null);
  const [dragged, setDragged] = useState<DragSource | null>(null);
  const [pendingTrash, setPendingTrash] = useState<Dragged | null>(null);
  const [trashing, setTrashing] = useState(false);
  const [toggled, setToggled] = useState<ReadonlyMap<string, boolean>>(new Map());
  const currentId = useMatch('/s/:space/d/:id')?.params.id ?? null;
  const documents = useLoad(`tree:${spaceSlug}:${version}`, () => services.documents.listInSpace(spaceSlug));
  const listed = documents.data ?? [];
  const currentPath = listed.find((candidate) => candidate.id === currentId)?.path ?? null;
  const findSource = (source: Dragged | null) =>
    source === null
      ? undefined
      : listed.find((candidate) => candidate.id === source.id && candidate.path === source.path);
  const source = editable && !trashing && dragged?.treeVersion === version ? findSource(dragged) : undefined;
  const trashCandidate = editable ? findSource(pendingTrash) : undefined;

  const trash = async (document: DocumentSummary): Promise<void> => {
    if (!editable || trashing) return;
    const active = openDocument.currentDocument();
    const affectsOpenDocument =
      currentId === document.id ||
      (currentPath !== null && isWithinPath(currentPath, document.path)) ||
      (active?.spaceSlug === spaceSlug && isWithinPath(active.path(), document.path));
    const send = async (): Promise<void> => {
      await services.documents.trash(document.id);
      setPendingTrash(null);
      shell.refreshTree();
      toast(`‘${document.title || document.path}’ 문서를 휴지통으로 옮겼습니다`);
      if (
        affectsOpenDocument &&
        active?.spaceSlug === spaceSlug &&
        openDocument.currentDocument() === active &&
        (active.id === document.id || isWithinPath(active.path(), document.path))
      )
        void navigate(`/s/${spaceSlug}`);
    };
    setTrashing(true);
    try {
      if (!affectsOpenDocument) await send();
      else if (active?.id === currentId) await active.withSavedEdits(send);
      else toast('열린 문서가 준비된 뒤 다시 시도하세요.');
    } catch (error) {
      toast(errorMessage(error));
    } finally {
      setTrashing(false);
    }
  };

  const drop = (document: DocumentSummary, under: DropTarget): void => {
    const along = descendantCount(listed, document.path);
    if (under === TRASH_TARGET) {
      if (along > 0) setPendingTrash({ id: document.id, path: document.path });
      else void trash(document);
      return;
    }
    const parent = listed.find((candidate) => candidate.path === under);
    services.documents.move(document, under).then(
      (result) => {
        if (!result.ok) toast(PATH_RULE_TEXT);
        else if (result.value !== null) {
          const place = under === '' ? '최상위로' : `‘${parent?.title ?? under}’ 아래로`;
          const moved =
            along > 0
              ? `‘${result.value.title}’와(과) 하위 문서 ${along}개를`
              : `‘${result.value.title}’을(를)`;
          toast(`${moved} ${place} 옮겼습니다`);
          shell.refreshTree();
        }
      },
      (error: unknown) => toast(errorMessage(error)),
    );
  };
  const drag: TreeDrag = {
    target,
    setTarget,
    source,
    setDragged: (next) => setDragged(next === null ? null : { ...next, treeVersion: version }),
    drop,
  };
  const isOpen = (folder: string): boolean => isFolderOpen(folder, currentPath, toggled);
  const folding: TreeFolding = {
    isOpen,
    toggle: (folder) => setToggled((previous) => new Map(previous).set(folder, !isOpen(folder))),
  };
  return (
    <>
      {editable ? (
        <NavLink
          className={({ isActive }) =>
            `nav-item${isActive ? ' active' : ''}${source !== undefined && target === TRASH_TARGET ? ' drop-target' : ''}`
          }
          to={`/s/${spaceSlug}/trash`}
          {...dropProps(TRASH_TARGET, drag)}
          onDragLeave={(event) => {
            if (!event.currentTarget.contains(event.relatedTarget as Node | null))
              setTarget((previous) => (previous === TRASH_TARGET ? null : previous));
          }}
        >
          <Icon name="trash" />
          휴지통
        </NavLink>
      ) : null}
      <div className="nav-section" style={{ display: 'flex', alignItems: 'center' }}>
        <Link to={`/s/${spaceSlug}`} style={{ color: 'inherit' }}>
          문서
        </Link>
        {editable ? (
          <button
            type="button"
            className="icon-btn"
            style={{ marginLeft: 'auto', width: 22, height: 22 }}
            onClick={() => shell.openNewDocument(spaceSlug)}
            aria-label="새 문서"
            title="새 문서"
          >
            <Icon name="plus" size={14} />
          </button>
        ) : null}
      </div>
      {documents.state !== 'ready' ? null : listed.length === 0 ? (
        <p className="faint small" style={{ padding: '0 8px' }}>
          문서가 없습니다.
        </p>
      ) : (
        <div
          className={`tree-root${source !== undefined && target === '' ? ' drop-target' : ''}`}
          {...(editable ? dropProps('', drag) : {})}
          onDragLeave={(event) => {
            if (!event.currentTarget.contains(event.relatedTarget as Node | null))
              setTarget((previous) => (typeof previous === 'string' ? null : previous));
          }}
        >
          <TreeNodes
            editable={editable}
            nodes={buildPathTree(listed)}
            spaceSlug={spaceSlug}
            drag={drag}
            folding={folding}
            onAddChild={(parent) => shell.openNewDocument(spaceSlug, `${parent}/`)}
          />
        </div>
      )}
      {trashCandidate === undefined ? null : (
        <TrashDocumentDialog
          descendants={descendantCount(listed, trashCandidate.path)}
          owner={me.owner}
          busy={trashing}
          onCancel={() => setPendingTrash(null)}
          onConfirm={() => void trash(trashCandidate)}
        />
      )}
    </>
  );
}

function UnreadCount() {
  const services = useServices();
  const { inboxVersion } = useSession();
  const count = useLoad(`inbox-count:${inboxVersion}`, () => services.inbox.unreadCount());
  if (count.state !== 'ready' || count.data === 0) return null;
  return (
    <span className="nav-count" aria-label={`읽지 않은 알림 ${count.data}개`}>
      {count.data > 99 ? '99+' : count.data}
    </span>
  );
}

export function Sidebar({
  currentSlug,
  treeVersion,
  onCollapse,
  onNavigate,
}: {
  currentSlug: string | null;
  treeVersion: number;
  onCollapse: () => void;
  onNavigate: () => void;
}) {
  const services = useServices();
  const navigate = useNavigate();
  const { me } = useSession();
  const shell = useShell();
  const spaces = shell.spaces ?? [];
  const current = spaces.find((space) => space.slug === currentSlug) ?? null;
  const live = useSpacePermissions();
  const currentPermission = permissionInSpace(current, me.owner, live.grants);
  const editable = canEdit(currentPermission);
  const navClass = ({ isActive }: { isActive: boolean }): string => `nav-item${isActive ? ' active' : ''}`;

  return (
    <nav
      className="sidebar"
      aria-label="사이드바"
      onClick={(event) => (event.target as HTMLElement).closest('a') !== null && onNavigate()}
    >
      <div className="sidebar-top">
        <SpaceSwitcher current={current} spaces={spaces} />
        <button
          type="button"
          className="icon-btn"
          onClick={onCollapse}
          aria-label="사이드바 접기"
          title="사이드바 접기"
        >
          <Icon name="sidebar" />
        </button>
      </div>
      <div className="sidebar-scroll">
        <button type="button" className="nav-item" onClick={shell.openPalette}>
          <Icon name="search" />
          검색
          <span className="nav-kbd">⌘K</span>
        </button>
        <NavLink className={navClass} to="/" end>
          <Icon name="home" />홈
        </NavLink>
        <NavLink className={navClass} to="/inbox">
          <Icon name="inbox" />
          인박스
          <UnreadCount />
        </NavLink>
        <NavLink className={navClass} to="/m">
          <Icon name="doc" />
          빠른 메모
        </NavLink>
        {currentSlug === null ? null : (
          <SpaceNavigation
            editable={editable}
            key={currentSlug}
            spaceSlug={currentSlug}
            version={treeVersion}
          />
        )}
        <div className="nav-section">설정</div>
        {currentSlug === null || !me.owner ? null : (
          <NavLink className={navClass} to={`/s/${currentSlug}/settings`}>
            <Icon name="settings" />
            스페이스 설정
          </NavLink>
        )}
        {me.owner ? (
          <NavLink className={navClass} to="/settings/agents">
            <Icon name="agent" />
            에이전트 연결
          </NavLink>
        ) : null}
        {me.owner ? (
          <NavLink className={navClass} to="/settings/data">
            <Icon name="folder" />
            데이터
          </NavLink>
        ) : null}
        {me.owner ? (
          <NavLink className={navClass} to="/settings/webhooks">
            <Icon name="link" />
            Webhook
          </NavLink>
        ) : null}
      </div>
      <div className="sidebar-footer">
        <ActorChip actor={{ kind: 'human', login: me.login, agent: null }} />
        <button
          type="button"
          className="btn btn-ghost btn-small"
          style={{ marginLeft: 'auto' }}
          onClick={() => {
            void services.session.logout().finally(() => {
              void navigate('/login', { replace: true });
            });
          }}
        >
          로그아웃
        </button>
      </div>
    </nav>
  );
}
