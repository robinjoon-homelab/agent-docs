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

const COMMON_WEB_TLDS = new Set([
  'com',
  'net',
  'org',
  'edu',
  'gov',
  'io',
  'dev',
  'app',
  'xyz',
  'info',
  'biz',
  'co',
  'kr',
  'jp',
  'uk',
  'de',
  'fr',
  'ai',
  'me',
  'tv',
]);

const ROOT_EXTENSIONLESS = new Set([
  'Dockerfile',
  'Makefile',
  'Containerfile',
  'Gemfile',
  'Rakefile',
  'Procfile',
  'Jenkinsfile',
  'Vagrantfile',
  'Brewfile',
  'gradlew',
  'mvnw',
  'LICENSE',
  'NOTICE',
  'CODEOWNERS',
]);

function isRepoFile(value: string): boolean {
  const [path = '', line = ''] = value.split('#');
  if (path.split('/').some((part) => part === '.' || part === '..')) return false;
  if (path.includes('/')) return true;
  if (!path.includes('.')) return ROOT_EXTENSIONLESS.has(path);
  if (path.startsWith('.')) return path.length >= 2 && !path.endsWith('.');
  const dot = path.lastIndexOf('.');
  const stem = path.slice(0, dot);
  const ext = path.slice(dot + 1);
  if (stem.length === 1 && ext.length === 1) return false;
  return !(line === '' && COMMON_WEB_TLDS.has(ext.toLowerCase()));
}

/** Display-only spans. Callers exclude code and existing links before invoking this function. */
export function githubReferences(text: string, context: GitHubContext): ReferenceSpan[] {
  const repo = referenceRepository(context);
  if (repo === null) return [];
  const pattern =
    /(^|[\s(])(#\d+|[a-f0-9]{7,40}\b|(?:[\w.-]+\/)+[\w.-]*[\w-](?:#L\d+(?:-L\d+)?)?|(?:\.[A-Za-z][A-Za-z0-9_.-]*[A-Za-z0-9]|[A-Za-z0-9_][A-Za-z0-9_.-]*\.[A-Za-z][A-Za-z0-9]{0,15}|Dockerfile|Makefile|Containerfile|Gemfile|Rakefile|Procfile|Jenkinsfile|Vagrantfile|Brewfile|gradlew|mvnw|LICENSE|NOTICE|CODEOWNERS)(?:#L\d+(?:-L\d+)?)?(?![A-Za-z0-9_/]))/gi;
  return Array.from(text.matchAll(pattern))
    .filter((match) => {
      const value = match[2] ?? '';
      return value.startsWith('#') || /^[a-f0-9]{7,40}$/i.test(value) || isRepoFile(value);
    })
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
