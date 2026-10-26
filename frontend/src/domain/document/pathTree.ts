import type { DocumentSummary } from './document';

export type PathNode = {
  readonly name: string;
  readonly path: string;
  readonly document: DocumentSummary | null;
  readonly children: readonly PathNode[];
};

type MutableNode = { name: string; path: string; document: DocumentSummary | null; children: MutableNode[] };

function childNamed(parent: MutableNode[], name: string, path: string): MutableNode {
  const existing = parent.find((node) => node.name === name);
  if (existing !== undefined) return existing;
  const created: MutableNode = { name, path, document: null, children: [] };
  parent.push(created);
  return created;
}

function sortTree(nodes: MutableNode[]): PathNode[] {
  return [...nodes]
    .sort((a, b) => {
      const folderFirst = Number(b.children.length > 0) - Number(a.children.length > 0);
      return folderFirst !== 0 ? folderFirst : a.name.localeCompare(b.name, 'ko');
    })
    .map((node) => ({ ...node, children: sortTree(node.children) }));
}

/** Path tree for the sidebar: folders from path segments, the document sits on its last segment. */
export function buildPathTree(documents: readonly DocumentSummary[]): PathNode[] {
  const roots: MutableNode[] = [];
  for (const document of documents) {
    const segments = document.path.split('/').filter((segment) => segment !== '');
    let level = roots;
    let node: MutableNode | null = null;
    for (const [index, segment] of segments.entries()) {
      node = childNamed(level, segment, segments.slice(0, index + 1).join('/'));
      level = node.children;
    }
    if (node !== null) node.document = document;
  }
  return sortTree(roots);
}

/**
 * Folders start closed, except those holding the open document, so a long tree stays short and the reader still sees
 * where they are. A folder the reader opened or closed keeps that choice.
 */
export function isFolderOpen(
  folder: string,
  currentPath: string | null,
  toggled: ReadonlyMap<string, boolean>,
): boolean {
  const choice = toggled.get(folder);
  if (choice !== undefined) return choice;
  return currentPath !== null && (currentPath === folder || currentPath.startsWith(`${folder}/`));
}

export function sortByRecent<T extends { readonly updatedAt: string }>(items: readonly T[]): T[] {
  return [...items].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
}
