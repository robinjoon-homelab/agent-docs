export type CommentPart =
  | { readonly kind: 'line'; readonly text: string }
  | { readonly kind: 'code'; readonly language: string; readonly code: string };

const FENCE = /^\s*(`{3,}|~{3,})\s*([\w+#.-]*)\s*$/;

/**
 * Splits a comment body into inline-markdown lines and fenced code blocks (``` or ~~~). An unclosed
 * fence runs to the end of the comment, as in CommonMark.
 */
export function commentParts(markdown: string): CommentPart[] {
  const parts: CommentPart[] = [];
  let fence: { marker: string; language: string; lines: string[] } | null = null;
  for (const line of markdown.split('\n')) {
    const match = FENCE.exec(line);
    if (fence === null) {
      if (match === null) parts.push({ kind: 'line', text: line });
      else fence = { marker: match[1] ?? '```', language: match[2] ?? '', lines: [] };
      continue;
    }
    if (match !== null && (match[2] ?? '') === '' && (match[1] ?? '').startsWith(fence.marker)) {
      parts.push({ kind: 'code', language: fence.language, code: fence.lines.join('\n') });
      fence = null;
    } else fence.lines.push(line);
  }
  if (fence !== null) parts.push({ kind: 'code', language: fence.language, code: fence.lines.join('\n') });
  return parts;
}
