import { html, parseFragment, type DefaultTreeAdapterTypes as Tree } from 'parse5';
import type { DesignJson, DesignObject } from '../../../../../../../domain/document/designBlock';

export type Element = Tree.Element;
export type Fields = Record<string, DesignJson>;
export function requireMarkup(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}
export function identifier(value: string | undefined): string {
  requireMarkup(value && /^[A-Za-z_][\w-]*$/.test(value), '잘못된 식별자: ' + (value ?? ''));
  return value;
}
export function isElement(n: Tree.ChildNode): n is Element {
  return 'tagName' in n;
}
export function attributes(n: Element): Record<string, string> {
  return Object.fromEntries(
    n.attrs.map((a) => {
      requireMarkup(!a.prefix && !a.namespace, '이름 공간이 있는 속성은 지원하지 않습니다: ' + a.name);
      return [a.name, a.value];
    }),
  );
}
export function allowKeys(record: Readonly<Record<string, unknown>>, allowed: readonly string[]): void {
  for (const k of Object.keys(record)) requireMarkup(allowed.includes(k), '지원하지 않는 속성: ' + k);
}
export function elements(parent: Tree.ParentNode): Element[] {
  return parent.childNodes.filter((c): c is Element => {
    if (isElement(c)) return true;
    requireMarkup('value' in c && !c.value.trim(), '상자의 글자는 span 또는 p 안에 넣어 주세요.');
    return false;
  });
}
export function plainText(n: Element): string {
  return n.childNodes
    .map((c) => {
      requireMarkup('value' in c, '일반 텍스트만 사용할 수 있습니다.');
      return c.value;
    })
    .join('');
}
class LocatedError extends Error {}
export function at<T>(node: Element, action: () => T): T {
  try {
    return action();
  } catch (e) {
    if (e instanceof LocatedError || !(e instanceof Error)) throw e;
    const loc = node.sourceCodeLocation;
    throw new LocatedError(
      '<' +
        node.tagName +
        '> ' +
        (loc ? String(loc.startLine) + '행 ' + String(loc.startCol) + '열: ' : '') +
        e.message,
    );
  }
}
// parse5 returns an inert tree. Reject HTML recovery that would silently drop user markup.
export function parseMarkup(source: string): Element[] {
  const doc = parseFragment(source, {
    sourceCodeLocationInfo: true,
    onParseError(e) {
      throw new Error(String(e.startLine) + '행 ' + String(e.startCol) + '열: ' + e.code);
    },
  });
  const spans: { startOffset: number; endOffset: number }[] = [];
  function inspect(parent: Tree.ParentNode): void {
    for (const node of parent.childNodes) {
      const loc = node.sourceCodeLocation;
      if (!isElement(node)) {
        requireMarkup('value' in node && loc, '지원하지 않는 마크업입니다.');
        spans.push(loc);
        continue;
      }
      const position = node.sourceCodeLocation;
      requireMarkup(position?.startTag, '생략된 여는 태그: ' + node.tagName);
      spans.push(position.startTag);
      if (position.endTag) spans.push(position.endTag);
      else
        requireMarkup(
          node.namespaceURI === html.NS.SVG &&
            /\/\s*>$/.test(source.slice(position.startOffset, position.endOffset)),
          '닫는 태그가 필요합니다: ' + node.tagName,
        );
      for (const a of node.attrs)
        requireMarkup(!a.name.toLowerCase().startsWith('on'), '이벤트 속성은 지원하지 않습니다: ' + a.name);
      inspect('content' in node ? node.content : node);
    }
  }
  inspect(doc);
  let end = 0;
  for (const span of spans.sort((a, b) => a.startOffset - b.startOffset)) {
    requireMarkup(!source.slice(end, span.startOffset).trim(), '해석되지 않은 HTML이 있습니다.');
    end = Math.max(end, span.endOffset);
  }
  requireMarkup(!source.slice(end).trim(), '해석되지 않은 HTML이 있습니다.');
  return elements(doc);
}
export function designEntries(value: DesignJson | undefined): DesignObject[] {
  if (value === undefined) return [];
  requireMarkup(Array.isArray(value), '모델의 목록 형식이 잘못되었습니다.');
  return value.map((e: unknown) => {
    requireMarkup(e && typeof e === 'object' && !Array.isArray(e), '모델의 목록 항목이 잘못되었습니다.');
    return e as DesignObject;
  });
}
export function allocator(elements: Element[]): (requested?: string) => string {
  const reserved = new Set<string>(),
    used = new Set<string>();
  function reserve(node: Element): void {
    const key = attributes(node)['data-key'];
    if (key !== undefined) {
      requireMarkup(!reserved.has(identifier(key)), '중복된 레이어 ID: ' + key);
      reserved.add(key);
    }
    node.childNodes.filter(isElement).forEach(reserve);
  }
  elements.forEach(reserve);
  let next = 1;
  return (requested) => {
    if (requested !== undefined) {
      requireMarkup(!used.has(identifier(requested)), '중복된 레이어 ID: ' + requested);
      used.add(requested);
      return requested;
    }
    let k: string;
    do {
      k = 'n' + String(next++);
    } while (reserved.has(k) || used.has(k));
    used.add(k);
    return k;
  };
}
