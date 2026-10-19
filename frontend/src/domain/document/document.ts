import type { Actor } from '../shared/actor';
import type { Block, StoredBlock } from './block';

export type DocumentStatus = 'draft' | 'in_review' | 'confirmed' | 'archived';

export const DOCUMENT_STATUSES: readonly DocumentStatus[] = ['draft', 'in_review', 'confirmed', 'archived'];

export const STATUS_LABEL: Record<DocumentStatus, string> = {
  draft: '초안',
  in_review: '리뷰 중',
  confirmed: '확정',
  archived: '보관',
};

export type SpaceRef = { readonly id: string; readonly slug: string; readonly name: string };

export type DocumentSummary = {
  readonly id: string;
  readonly path: string;
  readonly title: string;
  readonly status: DocumentStatus;
  readonly tags: readonly string[];
  readonly assignee: string | null;
  readonly revision: number;
  readonly updatedAt: string;
  readonly updatedBy: Actor;
};

export type TrashedDocument = DocumentSummary & { readonly trashedAt: string };

export type BlockChange = {
  readonly blockId: string;
  readonly actor: Actor;
  readonly revision: number;
  readonly at: string;
};

export type DocumentMeta = {
  readonly title: string;
  readonly path: string;
  readonly status: DocumentStatus;
  readonly tags: readonly string[];
  readonly assignee: string | null;
};

export type AgentDocument = DocumentMeta & {
  readonly id: string;
  readonly space: SpaceRef;
  readonly revision: number;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly trashedAt: string | null;
  readonly blocks: readonly StoredBlock[];
  /** Last change per block, used for the agent change marker. */
  readonly blockChanges: readonly BlockChange[];
};

export type RevisionSource = 'web' | 'mcp' | 'rest';

export type Revision = {
  readonly revision: number;
  readonly actor: Actor;
  readonly source: RevisionSource;
  readonly summary: string;
  readonly changedBlockIds: readonly string[];
  readonly createdAt: string;
  readonly threadIds: readonly string[];
};

export type RevisionSnapshot = {
  readonly revision: number;
  readonly title: string;
  readonly path: string;
  readonly blocks: readonly StoredBlock[];
  readonly actor: Actor;
  readonly createdAt: string;
};

export type ChangeKind = 'added' | 'modified' | 'removed';

export type CellRef = { readonly rowId: string; readonly columnId: string };

export type BlockDiff = {
  readonly blockId: string;
  readonly change: ChangeKind;
  readonly before: Block | null;
  readonly after: Block | null;
  readonly changedCells: readonly CellRef[];
};

export type DocumentDiff = {
  readonly from: number;
  readonly to: number;
  readonly changes: readonly BlockDiff[];
  readonly unified: string;
};

export type SaveConflict = {
  readonly blockId: string;
  readonly currentHash: string | null;
  readonly current: Block | null;
  readonly currentMarkdown: string | null;
};

export function documentHref(spaceSlug: string, documentId: string): string {
  return `/s/${encodeURIComponent(spaceSlug)}/d/${encodeURIComponent(documentId)}`;
}

export function threadHref(spaceSlug: string, documentId: string, threadId: string): string {
  return `${documentHref(spaceSlug, documentId)}#thread-${encodeURIComponent(threadId)}`;
}

export function historyHref(spaceSlug: string, documentId: string, revision?: number): string {
  const base = `${documentHref(spaceSlug, documentId)}/history`;
  return revision === undefined ? base : `${base}?rev=${revision}`;
}

export function parseTags(input: string): string[] {
  const tags = input
    .split(',')
    .map((tag) => tag.trim())
    .filter((tag) => tag !== '');
  return [...new Set(tags)];
}

/** Paths are slash-separated segments without leading/trailing slashes or empty segments. */
export function normalizePath(input: string): string {
  return input
    .split('/')
    .map((segment) => segment.trim())
    .filter((segment) => segment !== '')
    .join('/');
}

/** Path after dropping a document onto a folder ('' is the space root): the folder plus its own name. */
export function movedPath(path: string, folder: string): string {
  const name = path.split('/').pop() ?? path;
  return folder === '' ? name : `${folder}/${name}`;
}

export const PATH_RULE_TEXT =
  '경로는 /로 나눈 이름 1~10개입니다. 이름은 글자, 숫자, . _ - 로 1~80자이고 전체 300자 이하입니다. 예: guides/setup';

export const MAX_PATH_SEGMENTS = 10;
export const MAX_PATH_LENGTH = 300;
const PATH_SEGMENT = /^[\p{L}\p{N}._-]{1,80}$/u;

/** 1..10 segments of letters, digits, '.', '_', '-' (1..80 each), at most 300 chars, no outer slashes. */
export function isValidPath(path: string): boolean {
  const segments = path.split('/');
  return (
    path.length <= MAX_PATH_LENGTH &&
    segments.length <= MAX_PATH_SEGMENTS &&
    segments.every((segment) => PATH_SEGMENT.test(segment))
  );
}
