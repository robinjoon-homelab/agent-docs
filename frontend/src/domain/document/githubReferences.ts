export type GitHubContext = {
  readonly repositories: readonly string[];
  readonly defaultRepository?: string | null;
};
export type ReferenceSpan = { readonly from: number; readonly to: number; readonly href: string };

export function referenceRepository(context: GitHubContext): string | null {
  const repositories = context.repositories.filter((repo) => /^[a-z0-9_.-]+\/[a-z0-9_.-]+$/i.test(repo));
  if (repositories.length === 1) return repositories[0] ?? null;
  return repositories.find((repo) => repo === context.defaultRepository) ?? null;
}

/** Display-only spans. Callers exclude code and existing links before invoking this function. */
export function githubReferences(text: string, context: GitHubContext): ReferenceSpan[] {
  const repo = referenceRepository(context);
  if (repo === null) return [];
  const pattern = /(^|[\s(])(#\d+|[a-f0-9]{7,40}\b|(?:[\w.-]+\/)+[\w.-]+(?:#L\d+(?:-L\d+)?)?)/gi;
  return Array.from(text.matchAll(pattern))
    .filter((match) => !(match[2] ?? '').split('/').some((part) => part === '.' || part === '..'))
    .map((match) => {
      const value = match[2] ?? '';
      const from = match.index + (match[1]?.length ?? 0);
      const base = `https://github.com/${repo}`;
      let target: string;
      if (value.startsWith('#')) target = `/issues/${value.slice(1)}`;
      else if (/^[a-f0-9]+$/i.test(value)) target = `/commit/${value}`;
      else {
        const [path = '', line = ''] = value.split('#');
        target = `/blob/HEAD/${path.split('/').map(encodeURIComponent).join('/')}${line === '' ? '' : `#${line}`}`;
      }
      return { from, to: from + value.length, href: base + target };
    });
}
