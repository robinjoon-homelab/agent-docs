import type { GitHubContext } from '../document/githubReferences';

export type SpaceLinkKind = 'github_repository' | 'github_organization' | 'local_path' | 'url';

export type SpaceLink = { readonly kind: SpaceLinkKind; readonly value: string };

/** The document a space points agents to, offered only while it is the space's document outside the trash. */
export type SpaceGuide = { readonly documentId: string; readonly title: string; readonly path: string };

/** Why a guide the owner named is not offered: it is in the trash, or it was deleted or moved to another space. */
export type SpaceGuideIssue = 'trashed' | 'unavailable';

export type Space = {
  readonly id: string;
  readonly slug: string;
  readonly name: string;
  readonly description: string;
  readonly links: readonly SpaceLink[];
  readonly createdAt: string;
  /** Set while the owner keeps the space archived: it leaves space lists and pickers but still opens at its address. */
  readonly archivedAt: string | null;
  readonly lastActivityAt: string | null;
  readonly documentCount: number;
  /** Absent or null when the space names no guide or the named one is withheld (see agentGuideIssue). */
  readonly agentGuide?: SpaceGuide | null;
  readonly agentGuideIssue?: SpaceGuideIssue | null;
  /** The linked repository, as owner/repo, that short references stand for when several are linked. */
  readonly defaultRepository?: string | null;
};

export function isArchived(space: Space): boolean {
  return space.archivedAt !== null;
}

/** Spaces to offer for new work: the active ones, plus any archived space already chosen so it can be unchosen. */
export function pickableSpaces(spaces: readonly Space[], chosen: readonly string[] = []): Space[] {
  return spaces.filter((space) => !isArchived(space) || chosen.includes(space.id));
}

export type SpaceDraft = {
  readonly slug: string;
  readonly name: string;
  readonly description: string;
  readonly links: readonly SpaceLink[];
};

export const SPACE_LINK_KINDS: readonly SpaceLinkKind[] = [
  'github_repository',
  'github_organization',
  'local_path',
  'url',
];

export const SPACE_LINK_LABEL: Record<SpaceLinkKind, string> = {
  github_repository: 'GitHub 저장소',
  github_organization: 'GitHub 조직',
  local_path: '로컬 경로',
  url: 'URL',
};

export const MAX_SLUG_LENGTH = 50;
const SLUG = /^[a-z0-9](?:[a-z0-9-]{0,48}[a-z0-9])?$/;

export type SpaceDraftError = 'slug_invalid' | 'name_required' | 'name_too_long' | 'link_invalid';

export const SPACE_DRAFT_ERROR_TEXT: Record<SpaceDraftError, string> = {
  slug_invalid: '슬러그는 영문 소문자, 숫자, 하이픈으로 1~50자이며 하이픈으로 시작하거나 끝날 수 없습니다.',
  name_required: '이름을 입력하세요.',
  name_too_long: '이름은 100자 이하여야 합니다.',
  link_invalid:
    '외부 링크 값을 확인하세요. 저장소는 owner/repo, URL은 http(s)://, 로컬 경로는 /로 시작하는 절대 경로(pwd 결과)입니다.',
};

export function isValidSlug(slug: string): boolean {
  return SLUG.test(slug);
}

/** Suggests a slug from a name; non-latin names fall back to an empty suggestion. */
export function suggestSlug(name: string): string {
  return name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+/, '')
    .slice(0, MAX_SLUG_LENGTH)
    .replace(/-+$/, '');
}

function isValidLink(link: SpaceLink): boolean {
  const value = link.value.trim();
  if (value === '') return false;
  if (link.kind === 'github_repository') return /^[\w.-]+\/[\w.-]+$/.test(value);
  if (link.kind === 'github_organization') return /^[\w.-]+$/.test(value);
  if (link.kind === 'url') return /^https?:\/\/\S+$/.test(value);
  // Agents match spaces by their working directory (`pwd`), which is always absolute.
  return value.startsWith('/');
}

/** Matches the server, which stores local paths without a trailing slash. */
function normalizeLinkValue(link: SpaceLink): string {
  const value = link.value.trim();
  return link.kind === 'local_path' && value.length > 1 ? value.replace(/\/+$/, '') || '/' : value;
}

export function validateSpaceDraft(draft: SpaceDraft): SpaceDraftError[] {
  const errors: SpaceDraftError[] = [];
  if (!isValidSlug(draft.slug)) errors.push('slug_invalid');
  const name = draft.name.trim();
  if (name === '') errors.push('name_required');
  if (name.length > 100) errors.push('name_too_long');
  if (!draft.links.every(isValidLink)) errors.push('link_invalid');
  return errors;
}

export function normalizeSpaceDraft(draft: SpaceDraft): SpaceDraft {
  return {
    slug: draft.slug.trim(),
    name: draft.name.trim(),
    description: draft.description.trim(),
    links: draft.links.map((link) => ({ kind: link.kind, value: normalizeLinkValue(link) })),
  };
}

/** Where an external link chip points; local paths have no URL. */
export function linkHref(link: SpaceLink): string | null {
  switch (link.kind) {
    case 'github_repository':
    case 'github_organization':
      return `https://github.com/${link.value}`;
    case 'url':
      return link.value;
    case 'local_path':
      return null;
  }
}

export function sortSpacesByActivity(spaces: readonly Space[]): Space[] {
  return [...spaces].sort((a, b) =>
    (b.lastActivityAt ?? b.createdAt).localeCompare(a.lastActivityAt ?? a.createdAt),
  );
}

export function spaceInitial(name: string): string {
  return Array.from(name.trim())[0]?.toUpperCase() ?? '?';
}

export function draftOf(space: Space): SpaceDraft {
  return { slug: space.slug, name: space.name, description: space.description, links: space.links };
}

export const SPACE_GUIDE_ISSUE_TEXT: Record<SpaceGuideIssue, string> = {
  trashed:
    '지정한 지침 문서가 휴지통에 있어 에이전트에게 안내하지 않습니다. 복원하거나 다른 문서를 지정하세요.',
  unavailable:
    '지정한 지침 문서를 찾을 수 없어 에이전트에게 안내하지 않습니다. 삭제되었거나 다른 스페이스로 옮겨졌습니다. 다른 문서를 지정하세요.',
};

/** Only repository links name a repository; an organization link alone never stands for one of its repositories. */
export function linkedRepositories(space: Pick<Space, 'links'>): string[] {
  return space.links.filter((link) => link.kind === 'github_repository').map((link) => link.value);
}

/** What GitHub short references in the space's documents resolve against; the editor decorations take this value. */
export function githubContextOf(space: Pick<Space, 'links' | 'defaultRepository'>): GitHubContext {
  return { repositories: linkedRepositories(space), defaultRepository: space.defaultRepository ?? null };
}
