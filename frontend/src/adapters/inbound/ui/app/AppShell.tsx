import { useCallback, useMemo, useState } from 'react';
import { Outlet, useMatch } from 'react-router';
import { useServices } from '../../../../config/ServicesContext';
import { Icon } from '../common/Icon';
import { useHotkey } from '../hooks/useHotkey';
import { useLoad } from '../hooks/useLoad';
import { useMediaQuery } from '../hooks/useMediaQuery';
import { CommandPalette } from './CommandPalette';
import { NewDocumentDialog } from './NewDocumentDialog';
import type { Shell } from './ShellContext';
import { ShellProvider } from './ShellContext';
import { Sidebar } from './Sidebar';

/**
 * Desktop-first layout: 260px collapsible sidebar; below 1024px the sidebar becomes an overlay
 * opened from the top bar.
 */
export function AppShell() {
  const services = useServices();
  const spaceMatch = useMatch('/s/:space/*');
  const currentSlug = spaceMatch?.params.space ?? null;
  const narrow = useMediaQuery('(max-width: 1023px)');
  const [collapsed, setCollapsed] = useState(false);
  const [overlayOpen, setOverlayOpen] = useState(false);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [treeVersion, setTreeVersion] = useState(0);
  const [newDocument, setNewDocument] = useState<{ spaceSlug: string; path: string } | null>(null);
  const spaces = useLoad('spaces', () => services.spaces.list());
  const [lastSlug, setLastSlug] = useState(currentSlug);
  if (currentSlug !== null && currentSlug !== lastSlug) setLastSlug(currentSlug);

  useHotkey({ key: 'k', mod: true }, () => setPaletteOpen(true));

  const refreshTree = useCallback(() => setTreeVersion((value) => value + 1), []);
  const openPalette = useCallback(() => setPaletteOpen(true), []);
  const openNewDocument = useCallback(
    (spaceSlug: string, path = '') => setNewDocument({ spaceSlug, path }),
    [],
  );
  const shell: Shell = useMemo(
    () => ({
      spaces: spaces.state === 'ready' ? spaces.data : null,
      currentSpaceSlug: lastSlug,
      refreshSpaces: spaces.reload,
      refreshTree,
      openPalette,
      openNewDocument,
    }),
    [spaces.state, spaces.data, spaces.reload, lastSlug, refreshTree, openPalette, openNewDocument],
  );

  const classes = ['app-shell'];
  if (!narrow && collapsed) classes.push('sidebar-collapsed');
  if (narrow && overlayOpen) classes.push('sidebar-open');

  return (
    <ShellProvider shell={shell}>
      <div className={classes.join(' ')}>
        <Sidebar
          currentSlug={currentSlug}
          treeVersion={treeVersion}
          onCollapse={() => (narrow ? setOverlayOpen(false) : setCollapsed(true))}
          onNavigate={() => setOverlayOpen(false)}
        />
        {narrow && overlayOpen ? (
          <div className="sidebar-scrim" onClick={() => setOverlayOpen(false)} />
        ) : null}
        <main className="app-main" key={currentSlug ?? 'root'}>
          {narrow || collapsed ? (
            <div className="mobile-bar" style={narrow ? undefined : { display: 'flex' }}>
              <button
                type="button"
                className="icon-btn"
                aria-label="사이드바 열기"
                onClick={() => (narrow ? setOverlayOpen(true) : setCollapsed(false))}
              >
                <Icon name="menu" />
              </button>
              <button type="button" className="icon-btn" aria-label="검색" onClick={openPalette}>
                <Icon name="search" />
              </button>
            </div>
          ) : null}
          <Outlet />
        </main>
      </div>
      <CommandPalette open={paletteOpen} spaceSlug={lastSlug} onClose={() => setPaletteOpen(false)} />
      {newDocument === null ? null : (
        <NewDocumentDialog
          spaceSlug={newDocument.spaceSlug}
          initialPath={newDocument.path}
          onClose={() => setNewDocument(null)}
          onCreated={refreshTree}
        />
      )}
    </ShellProvider>
  );
}
