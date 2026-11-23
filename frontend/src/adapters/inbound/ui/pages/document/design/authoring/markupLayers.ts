import { html } from 'parse5';
import type { DesignJson, DesignObject } from '../../../../../../../domain/document/designBlock';
import { inline, nodeStyle, numberValue, surface } from './markupStyle';
import { pathNode, svgRotation } from './markupPath';
import {
  allocator,
  allowKeys,
  at,
  attributes,
  designEntries,
  elements,
  identifier,
  isElement,
  plainText,
  requireMarkup,
  type Element,
  type Fields,
} from './markupTree';

export type Resources = {
  components: Map<string, Map<string, DesignObject>>;
  tokens: Set<string>;
  styles: Set<string>;
};
const common = ['id', 'data-key', 'role', 'aria-label', 'class', 'style'];
function readText(n: Element, props: Set<string>, tokens: Set<string>): DesignJson {
  const a = attributes(n);
  if (a['data-bind'] !== undefined) {
    requireMarkup(
      props.has(a['data-bind']) && n.childNodes.length === 0,
      '선언된 글자 속성만 빈 요소에 연결할 수 있습니다.',
    );
    return { prop: a['data-bind'] };
  }
  if (n.childNodes.every((c) => 'value' in c)) return plainText(n);
  const reserved = new Set(
    n.childNodes
      .filter(isElement)
      .map((c) => attributes(c)['data-run-key'])
      .filter((v): v is string => v !== undefined),
  );
  let next = 1;
  const assigned = new Set<string>();
  function runId(given?: string): string {
    let id = given;
    if (id === undefined) {
      do {
        id = 'r' + String(next++);
      } while (reserved.has(id) || assigned.has(id));
    }
    requireMarkup(!assigned.has(identifier(id)), '중복된 글자 구간 ID: ' + id);
    assigned.add(id);
    return id;
  }
  return {
    runs: n.childNodes.map((c) => {
      if (!isElement(c)) {
        requireMarkup('value' in c, '지원하지 않는 인라인 요소입니다.');
        return { id: runId(), text: c.value } satisfies DesignObject;
      }
      return at(c, () => {
        requireMarkup(
          ['span', 'strong', 'em', 'del'].includes(c.tagName),
          '지원하지 않는 인라인 태그: ' + c.tagName,
        );
        const a = attributes(c);
        allowKeys(a, ['data-run-key', 'style', 'data-bind']);
        const rules = inline(a.style);
        if (c.tagName === 'strong') rules['font-weight'] ??= '700';
        if (c.tagName === 'em') rules['font-style'] ??= 'italic';
        if (c.tagName === 'del') rules['text-decoration-line'] ??= 'line-through';
        const style = surface(rules, tokens);
        requireMarkup(style.fill === undefined, '글자 구간에 배경색을 적용할 수 없습니다.');
        const value = plainText(c);
        const binding = a['data-bind'];
        if (binding !== undefined)
          requireMarkup(!value && props.has(binding), '글자 구간 바인딩이 잘못되었습니다.');
        return {
          id: runId(a['data-run-key']),
          text: binding === undefined ? value : { prop: binding },
          ...(style.text ? { style: style.text } : {}),
        } satisfies DesignObject;
      });
    }),
  };
}
function readInput(value: string, prop: DesignObject): DesignJson {
  switch (prop.type) {
    case 'text':
      return value;
    case 'choice':
      requireMarkup(
        designEntries(prop.choices).some((choice) => choice.id === value),
        '선언되지 않은 선택지: ' + value,
      );
      return value;
    case 'number':
      return numberValue(value);
    case 'boolean':
      requireMarkup(value === 'true' || value === 'false', '참·거짓 입력은 true 또는 false여야 합니다.');
      return value === 'true';
    default:
      throw new Error(
        '마크업에서 지원하지 않는 컴포넌트 입력: ' + (typeof prop.id === 'string' ? prop.id : ''),
      );
  }
}
export function readLayerTree(
  root: Element,
  context: Resources,
  props: Set<string> = new Set(),
): DesignObject {
  const allocate = allocator([root]);
  function node(t: Element): DesignObject {
    return at(t, () => build(t));
  }
  function build(t: Element): DesignObject {
    const a = attributes(t),
      k = t.tagName;
    const fields: Fields = { id: allocate(a['data-key']) };
    if (a.id !== undefined) fields.alias = identifier(a.id);
    if (a.class !== undefined) {
      requireMarkup(context.styles.has(a.class), '등록되지 않은 단일 클래스: ' + a.class);
      fields.sharedStyle = a.class;
    }
    if (a.role !== undefined) fields.role = a.role;
    if (a['aria-label'] !== undefined) fields.accessibleName = a['aria-label'];
    switch (k) {
      case 'div':
      case 'section':
      case 'button':
        allowKeys(a, k === 'button' ? [...common, 'type'] : common);
        if (k === 'button') {
          requireMarkup(a.type === undefined || a.type === 'button', '버튼 type은 button만 지원합니다.');
          fields.role = a.role ?? 'button';
        }
        return {
          ...fields,
          kind: 'box',
          ...nodeStyle(inline(a.style), true, context.tokens),
          children: elements(t).map(node),
        };
      case 'p':
      case 'span':
        allowKeys(a, [...common, 'data-bind']);
        return {
          ...fields,
          kind: 'text',
          ...nodeStyle(inline(a.style), false, context.tokens),
          text: readText(t, props, context.tokens),
        };
      case 'flow-use': {
        const args = Object.keys(a).filter((key) => key.startsWith('data-input-'));
        allowKeys(a, [...common, 'component', ...args]);
        const definition = context.components.get(a.component ?? '');
        requireMarkup(
          definition && !t.childNodes.length,
          '컴포넌트가 없거나 인스턴스가 비어 있지 않습니다: ' + (a.component ?? ''),
        );
        const inputs = Object.fromEntries(
          args.map((attr) => {
            const id = attr.slice(11),
              prop = definition.get(id);
            requireMarkup(prop, '컴포넌트에 없는 속성: ' + id);
            return [id, readInput(a[attr] as string, prop)];
          }),
        );
        return {
          ...fields,
          kind: 'instance',
          component: a.component as string,
          ...nodeStyle(inline(a.style), false, context.tokens),
          ...(args.length ? { inputs } : {}),
        };
      }
      case 'svg': {
        allowKeys(a, [...common, 'viewBox', 'width', 'height', 'transform']);
        const [x, y, width, height, ...rest] = (a.viewBox ?? '').trim().split(/\s+/).map(numberValue);
        requireMarkup(
          x !== undefined &&
            y !== undefined &&
            width !== undefined &&
            width > 0 &&
            height !== undefined &&
            height > 0 &&
            !rest.length,
          'viewBox에 x y 너비 높이를 지정해 주세요.',
        );
        const css = inline(a.style);
        for (const dim of ['width', 'height'])
          if (a[dim] !== undefined) {
            requireMarkup(!Object.hasOwn(css, dim), '중복 SVG 크기: ' + dim);
            css[dim] = String(numberValue(a[dim])) + 'px';
          }
        return {
          ...fields,
          kind: 'group',
          ...svgRotation(a.transform),
          viewBox: { x, y, width, height, fit: 'contain' },
          ...nodeStyle(css, false, context.tokens),
          children: elements(t).map((c) => {
            requireMarkup(c.tagName === 'path', 'SVG 안에서는 path를 지원합니다.');
            return node(c);
          }),
        };
      }
      case 'path':
        requireMarkup(t.namespaceURI === html.NS.SVG && !t.childNodes.length, '잘못된 SVG path입니다.');
        allowKeys(a, [
          'id',
          'data-key',
          'd',
          'fill',
          'stroke',
          'fill-rule',
          'stroke-width',
          'stroke-linecap',
          'stroke-linejoin',
          'data-contour-key',
          'data-points',
          'data-fill-key',
          'data-stroke-key',
          'data-ink-key',
          'transform',
        ]);
        return { ...fields, ...pathNode(a, context.tokens) };
      default:
        throw new Error('지원하지 않는 태그: ' + k);
    }
  }
  const result = node(root);
  requireMarkup(result.kind === 'box', '화면과 컴포넌트의 루트는 div, section 또는 button이어야 합니다.');
  return result;
}
