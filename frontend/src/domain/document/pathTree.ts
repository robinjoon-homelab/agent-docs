import type { DocumentSummary, TrashedDocument } from './document';

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

/**
 * Path tree for the sidebar. The leading segments of a path are the parent document, so a node holds the document at
 * its path and the documents under it. A node without a document is a folder from before parents were documents.
 */
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

/** The parent document's path, or null at the top of the space. */
export function parentPath(path: string): string | null {
  const slash = path.lastIndexOf('/');
  return slash < 0 ? null : path.slice(0, slash);
}

/** True for the path itself and everything under it: guides/deploy is within guides, guides-old is not. */
export function isWithinPath(path: string, ancestor: string): boolean {
  return path === ancestor || path.startsWith(`${ancestor}/`);
}

/** The documents right under a path, by title. */
export function childrenOf<T extends DocumentSummary>(documents: readonly T[], path: string): T[] {
  return documents
    .filter((document) => parentPath(document.path) === path)
    .sort((a, b) => a.title.localeCompare(b.title, 'ko'));
}

/** How many documents lie under a path at any depth, which go along when it moves or goes to the trash. */
export function descendantCount(documents: readonly DocumentSummary[], path: string): number {
  return documents.filter((document) => document.path !== path && isWithinPath(document.path, path)).length;
}

/**
 * The documents under one in the trash that went there at the same instant, as its trash move took them along. The
 * server restores and deletes them with it.
 */
export function trashedTogether(
  trash: readonly TrashedDocument[],
  document: TrashedDocument,
): TrashedDocument[] {
  return trash.filter(
    (item) =>
      item.id !== document.id &&
      item.trashedAt === document.trashedAt &&
      isWithinPath(item.path, document.path),
  );
}

export type Crumb = { readonly path: string; readonly title: string; readonly id: string | null };

/** The documents above a path, top first. One that has no document is named by its segment. */
export function breadcrumbOf(documents: readonly DocumentSummary[], path: string): Crumb[] {
  const byPath = new Map(documents.map((document) => [document.path, document]));
  const crumbs: Crumb[] = [];
  for (let parent = parentPath(path); parent !== null; parent = parentPath(parent)) {
    const document = byPath.get(parent);
    const name = parent.slice(parent.lastIndexOf('/') + 1);
    crumbs.unshift({ path: parent, title: document?.title ?? name, id: document?.id ?? null });
  }
  return crumbs;
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
