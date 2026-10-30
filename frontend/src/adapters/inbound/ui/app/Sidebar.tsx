import type { DragEvent } from 'react';
import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, useMatch, useNavigate } from 'react-router';
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
import { useLoad } from '../hooks/useLoad';
import { useSession } from './SessionContext';
import { useShell } from './ShellContext';

function SpaceSwitcher({ current, spaces }: { current: Space | null; spaces: readonly Space[] }) {
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
          <Link role="menuitem" className="menu-item" to="/?new=space" onClick={() => setOpen(false)}>
            <Icon name="plus" size={14} />새 스페이스
          </Link>
        </div>
      ) : null}
    </div>
  );
}

/** Drag payload: the id of a document being moved in the tree. */
const DOCUMENT_DRAG_TYPE = 'application/x-agent-docs-document';

type Dragged = { readonly id: string; readonly path: string };

/**
 * `target` is the path the dragged document would go under ('' is the space root). The dragged document is kept
 * here because a drag event only reveals its data on drop, and hovering must already refuse the document itself and
 * what lies under it.
 */
type TreeDrag = {
  readonly target: string | null;
  readonly setTarget: (path: string | null) => void;
  readonly dragged: Dragged | null;
  readonly setDragged: (dragged: Dragged | null) => void;
  readonly drop: (documentId: string, under: string) => void;
};

type TreeFolding = {
  readonly isOpen: (folder: string) => boolean;
  readonly toggle: (folder: string) => void;
};

function FoldIcon({ open }: { open: boolean }) {
  return <Icon name={open ? 'chevron-down' : 'chevron-right'} size={14} />;
}

function dropProps(path: string, drag: TreeDrag) {
  return {
    onDragOver: (event: DragEvent) => {
      if (!event.dataTransfer.types.includes(DOCUMENT_DRAG_TYPE)) return;
      event.stopPropagation();
      // Not accepting the drop shows the no-drop cursor: a document cannot go under itself or its descendants.
      if (drag.dragged !== null && isWithinPath(path, drag.dragged.path)) {
        event.dataTransfer.dropEffect = 'none';
        if (drag.target !== null) drag.setTarget(null);
        return;
      }
      event.preventDefault();
      event.dataTransfer.dropEffect = 'move';
      if (drag.target !== path) drag.setTarget(path);
    },
    onDrop: (event: DragEvent) => {
      const documentId = event.dataTransfer.getData(DOCUMENT_DRAG_TYPE);
      if (documentId === '') return;
      event.preventDefault();
      event.stopPropagation();
      drag.setTarget(null);
      drag.setDragged(null);
      drag.drop(documentId, path);
    },
  };
}

function TreeNodes({
  nodes,
  spaceSlug,
  drag,
  folding,
  onAddChild,
}: {
  nodes: readonly PathNode[];
  spaceSlug: string;
  drag: TreeDrag;
  folding: TreeFolding;
  onAddChild: (parent: string) => void;
}) {
  return (
    <ul className="tree">
      {nodes.map((node) => {
        const isFolder = node.children.length > 0;
        const open = isFolder && folding.isOpen(node.path);
        const targetClass = drag.target === node.path ? ' drop-target' : '';
        const name = node.document === null || node.document.title === '' ? node.name : node.document.title;
        return (
          <li key={node.path} {...dropProps(node.path, drag)}>
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
                  title={`${node.path} · 다른 문서 위로 끌어 그 아래로 옮기기`}
                  draggable
                  onDragStart={(event) => {
                    event.dataTransfer.setData(DOCUMENT_DRAG_TYPE, node.document?.id ?? '');
                    event.dataTransfer.effectAllowed = 'move';
                    if (node.document !== null) drag.setDragged({ id: node.document.id, path: node.path });
                  }}
                  onDragEnd={() => {
                    drag.setTarget(null);
                    drag.setDragged(null);
                  }}
                >
                  <Icon name="doc" size={14} />
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {name}
                  </span>
                </NavLink>
                <button
                  type="button"
                  className="tree-add"
                  aria-label={`${name} 아래에 새 문서`}
                  title="하위 문서 추가"
                  onClick={() => onAddChild(node.path)}
                >
                  <Icon name="plus" size={14} />
                </button>
              </div>
            )}
            {open ? (
              <TreeNodes
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

/**
 * The space's document tree. Dropping a document on another one moves it under that one, with everything under it;
 * dropping it on the empty area moves it to the top.
 */
function DocumentTree({ spaceSlug, version }: { spaceSlug: string; version: number }) {
  const services = useServices();
  const shell = useShell();
  const toast = useToast();
  const [target, setTarget] = useState<string | null>(null);
  const [dragged, setDragged] = useState<Dragged | null>(null);
  const [toggled, setToggled] = useState<ReadonlyMap<string, boolean>>(new Map());
  const currentId = useMatch('/s/:space/d/:id')?.params.id ?? null;
  const documents = useLoad(`tree:${spaceSlug}:${version}`, () => services.documents.listInSpace(spaceSlug));
  if (documents.state !== 'ready') return null;
  if (documents.data.length === 0)
    return (
      <p className="faint small" style={{ padding: '0 8px' }}>
        문서가 없습니다.
      </p>
    );
  const drop = (documentId: string, under: string): void => {
    const document = documents.data.find((candidate) => candidate.id === documentId);
    if (document === undefined || isWithinPath(under, document.path)) return;
    const parent = documents.data.find((candidate) => candidate.path === under);
    const along = descendantCount(documents.data, document.path);
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
  const drag: TreeDrag = { target, setTarget, dragged, setDragged, drop };
  const currentPath = documents.data.find((candidate) => candidate.id === currentId)?.path ?? null;
  const isOpen = (folder: string): boolean => isFolderOpen(folder, currentPath, toggled);
  const folding: TreeFolding = {
    isOpen,
    toggle: (folder) => setToggled((previous) => new Map(previous).set(folder, !isOpen(folder))),
  };
  return (
    <div
      className={`tree-root${target === '' ? ' drop-target' : ''}`}
      {...dropProps('', drag)}
      onDragLeave={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setTarget(null);
      }}
    >
      <TreeNodes
        nodes={buildPathTree(documents.data)}
        spaceSlug={spaceSlug}
        drag={drag}
        folding={folding}
        onAddChild={(parent) => shell.openNewDocument(spaceSlug, `${parent}/`)}
      />
    </div>
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
        {currentSlug === null ? null : (
          <>
            <div className="nav-section" style={{ display: 'flex', alignItems: 'center' }}>
              <Link to={`/s/${currentSlug}`} style={{ color: 'inherit' }}>
                문서
              </Link>
              <button
                type="button"
                className="icon-btn"
                style={{ marginLeft: 'auto', width: 22, height: 22 }}
                onClick={() => shell.openNewDocument(currentSlug)}
                aria-label="새 문서"
                title="새 문서"
              >
                <Icon name="plus" size={14} />
              </button>
            </div>
            <DocumentTree key={currentSlug} spaceSlug={currentSlug} version={treeVersion} />
          </>
        )}
        <div className="nav-section">설정</div>
        {currentSlug === null ? null : (
          <NavLink className={navClass} to={`/s/${currentSlug}/settings`}>
            <Icon name="settings" />
            스페이스 설정
          </NavLink>
        )}
        <NavLink className={navClass} to="/settings/agents">
          <Icon name="agent" />
          에이전트 연결
        </NavLink>
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
