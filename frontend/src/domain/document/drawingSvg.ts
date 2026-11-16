/**
 * Addresses an exported drawing SVG would load by itself: stylesheet `url(...)` and `@import` targets, and the
 * `href`/`xlink:href`/`src` of anything but a hyperlink. Data URLs and in-document `#fragment`s are local. Namespace
 * declarations (`xmlns="http://www.w3.org/2000/svg"`) are names, not loads, and so are `<a href>` hyperlinks, which
 * never open by themselves. A drawing whose export still needs the network (typically a font that fell back to a
 * CDN) is not saved.
 */
export function externalReferences(svg: string): string[] {
  const found: string[] = [];
  found.push(...urlTargets(svg));
  if (/image-set\(/i.test(svg)) found.push('image-set(');
  for (const match of svg.matchAll(/@import\s+(['"])([^'"]+)\1/gi)) found.push(match[2] ?? '');
  for (const tag of svg.matchAll(/<([a-zA-Z][\w:.-]*)((?:\s+[^\s=>]+\s*=\s*(?:"[^"]*"|'[^']*'))*)\s*\/?>/g)) {
    if ((tag[1] ?? '').toLowerCase() === 'a') continue;
    for (const attribute of (tag[2] ?? '').matchAll(
      /\s((?:xlink:)?href|src)\s*=\s*(?:"([^"]*)"|'([^']*)')/gi,
    ))
      found.push(attribute[2] ?? attribute[3] ?? '');
  }
  return found.map((target) => target.trim()).filter((target) => !isLocal(target));
}

/**
 * Every `url(...)` target, read by position rather than by a regex so optional quotes and spaces cannot backtrack
 * into a wrong verdict: a quoted target runs to its closing quote, an unquoted one to `)`, an unterminated one to the end.
 */
function urlTargets(text: string): string[] {
  const lower = text.toLowerCase();
  const targets: string[] = [];
  let open = lower.indexOf('url(');
  while (open >= 0) {
    const [target, end] = urlTarget(text, open + 'url('.length);
    targets.push(target);
    open = lower.indexOf('url(', end);
  }
  return targets;
}

function urlTarget(text: string, from: number): [string, number] {
  let start = from;
  while (start < text.length && /\s/.test(text.charAt(start))) start += 1;
  if (start >= text.length) return ['', text.length];
  const first = text.charAt(start);
  const quote = first === '"' || first === "'" ? first : null;
  const close = quote === null ? text.indexOf(')', start) : text.indexOf(quote, start + 1);
  const end = close < 0 ? text.length : close;
  return [text.slice(quote === null ? start : start + 1, end).trim(), end];
}

function isLocal(target: string): boolean {
  const lower = target.toLowerCase();
  return lower === '' || lower.startsWith('#') || lower.startsWith('data:');
}
