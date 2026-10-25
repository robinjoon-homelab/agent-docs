import type { ReactNode } from 'react';
import { createContext, useContext } from 'react';
import type { Space } from '../../../../domain/space/space';

export type Shell = {
  readonly spaces: readonly Space[] | null;
  /** The space the user is in, or was last in (search defaults to it). */
  readonly currentSpaceSlug: string | null;
  readonly refreshSpaces: () => void;
  /** Reloads the sidebar document tree after a create, rename, move or trash. */
  readonly refreshTree: () => void;
  readonly openPalette: () => void;
  readonly openNewDocument: (spaceSlug: string, path?: string) => void;
  /** Whether the comment rail beside documents is folded away; it stays so from one document to the next. */
  readonly railCollapsed: boolean;
  readonly setRailCollapsed: (collapsed: boolean) => void;
};

const ShellContext = createContext<Shell | null>(null);

export function ShellProvider({ shell, children }: { shell: Shell; children: ReactNode }) {
  return <ShellContext value={shell}>{children}</ShellContext>;
}

export function useShell(): Shell {
  const shell = useContext(ShellContext);
  if (shell === null) throw new Error('useShell must be used inside AppShell');
  return shell;
}
