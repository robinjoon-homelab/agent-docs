import type { ReactNode } from 'react';
import { createContext, useContext, useMemo, useRef } from 'react';
import type { Space } from '../../../../domain/space/space';

export type Shell = {
  readonly spaces: readonly Space[] | null;
  /** The space the user is in, or was last in (search defaults to it). */
  readonly currentSpaceSlug: string | null;
  readonly refreshSpaces: () => void;
  /** Reloads the sidebar document tree after a create, rename, move or trash. */
  readonly refreshTree: () => void;
  /** Counts refreshTree calls, so views built from the space's documents (child lists, breadcrumbs) reload too. */
  readonly treeVersion: number;
  readonly openPalette: () => void;
  readonly openNewDocument: (spaceSlug: string, path?: string) => void;
  /** Whether the comment rail beside documents is folded away; it stays so from one document to the next. */
  readonly railCollapsed: boolean;
  readonly setRailCollapsed: (collapsed: boolean) => void;
};

const ShellContext = createContext<Shell | null>(null);

export type OpenDocument = {
  readonly id: string;
  readonly spaceSlug: string;
  readonly path: () => string;
  readonly withSavedEdits: (action: () => Promise<void>) => Promise<void>;
};

const OpenDocumentContext = createContext<{
  readonly currentDocument: () => OpenDocument | null;
  readonly register: (document: OpenDocument) => () => void;
} | null>(null);

export function ShellProvider({ shell, children }: { shell: Shell; children: ReactNode }) {
  const openDocument = useRef<OpenDocument | null>(null);
  const registry = useMemo(
    () => ({
      currentDocument: () => openDocument.current,
      register: (document: OpenDocument) => {
        openDocument.current = document;
        return () => {
          if (openDocument.current === document) openDocument.current = null;
        };
      },
    }),
    [],
  );
  return (
    <ShellContext value={shell}>
      <OpenDocumentContext value={registry}>{children}</OpenDocumentContext>
    </ShellContext>
  );
}

export function useShell(): Shell {
  const shell = useContext(ShellContext);
  if (shell === null) throw new Error('useShell must be used inside AppShell');
  return shell;
}

export function useOpenDocument() {
  const openDocument = useContext(OpenDocumentContext);
  if (openDocument === null) throw new Error('useOpenDocument must be used inside AppShell');
  return openDocument;
}
