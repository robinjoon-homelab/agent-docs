import type { CodeReference } from './document';

/** Builds links from checked values so a stored URL cannot change the destination. */
export function codeLinks(reference: CodeReference): { readonly label: string; readonly href: string }[] {
  if (!/^[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}\/[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}$/u.test(reference.repository))
    return [];
  const links: { label: string; href: string }[] = [];
  if (reference.commit !== null && /^[a-fA-F0-9]{40}$/u.test(reference.commit))
    links.push({
      label: `${reference.repository}@${reference.commit.slice(0, 7)}`,
      href: `https://github.com/${reference.repository}/commit/${reference.commit}`,
    });
  if (
    reference.pullRequest !== null &&
    Number.isSafeInteger(reference.pullRequest) &&
    reference.pullRequest > 0
  )
    links.push({
      label: `${reference.repository}#${reference.pullRequest}`,
      href: `https://github.com/${reference.repository}/pull/${reference.pullRequest}`,
    });
  return links;
}
