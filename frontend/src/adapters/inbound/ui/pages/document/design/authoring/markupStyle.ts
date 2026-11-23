import postcss, { type Rule } from 'postcss';
import type { DesignJson, DesignObject } from '../../../../../../../domain/document/designBlock';
import { identifier, requireMarkup, type Fields } from './markupTree';

type Declarations = Record<string, string>;
const numericPattern = /^-?(?:\d+(?:\.\d+)?|\.\d+)$/;
export function numberValue(raw: string | undefined): number {
  requireMarkup(
    raw !== undefined && numericPattern.test(raw) && Number.isFinite(Number(raw)),
    '숫자 값이 필요합니다: ' + (raw ?? ''),
  );
  return Number(raw);
}
function positive(value: number): number {
  requireMarkup(value >= 0, '음수는 사용할 수 없습니다.');
  return value;
}
function px(raw: string): number {
  requireMarkup(raw === '0' || raw.endsWith('px'), 'px 단위를 사용해 주세요: ' + raw);
  return positive(numberValue(raw.replace(/px$/, '')));
}
const hex = /^#(?:[\da-f]{3}|[\da-f]{4}|[\da-f]{6}|[\da-f]{8})$/i;
export function paintColor(raw: string, colorTokens: Set<string>): DesignJson {
  if (hex.test(raw) || raw === 'currentColor') return raw;
  const id = /^var\(--([A-Za-z_][\w-]*)\)$/.exec(raw)?.[1];
  requireMarkup(id && colorTokens.has(id), '색상 또는 등록된 색 토큰이 필요합니다: ' + raw);
  return { token: id };
}
function declarations(rule: Rule): Declarations {
  const rows: [string, string][] = [];
  const names = new Set<string>();
  for (const d of rule.nodes) {
    requireMarkup(
      d.type === 'decl' && d.value.trim() && !d.important && !names.has(d.prop),
      '중복되거나 비어 있는 CSS 선언입니다.',
    );
    names.add(d.prop);
    rows.push([d.prop, d.value.trim()]);
  }
  return Object.fromEntries(rows);
}
export function inline(raw: string | undefined): Declarations {
  if (!raw?.trim()) return {};
  const root = postcss.parse('x{' + raw + '}'),
    first = root.first;
  requireMarkup(
    root.nodes.length === 1 && first?.type === 'rule' && first.selector === 'x',
    '잘못된 인라인 스타일입니다.',
  );
  return declarations(first);
}
export function surface(d: Declarations, tokens: Set<string>): Fields {
  const s: Fields = {},
    text: Fields = {};
  for (const [k, v] of Object.entries(d)) {
    switch (k) {
      case 'background-color':
        s.fill = paintColor(v, tokens);
        break;
      case 'color':
        text.color = paintColor(v, tokens);
        break;
      case 'font-size': {
        const n = px(v);
        requireMarkup(n > 0, '글자 크기는 0보다 커야 합니다.');
        text.size = n;
        break;
      }
      case 'font-weight': {
        const n = numberValue(v);
        requireMarkup(n >= 1 && n <= 1000, '글자 굵기는 1~1000입니다.');
        text.weight = n;
        break;
      }
      case 'font-style':
        requireMarkup(v === 'normal' || v === 'italic', '지원하지 않는 글자 스타일: ' + v);
        text.italic = v === 'italic';
        break;
      case 'text-decoration-line':
        requireMarkup(['line-through', 'underline', 'none'].includes(v), '지원하지 않는 텍스트 장식: ' + v);
        if (v === 'line-through') text.strike = true;
        else if (v === 'underline') text.underline = true;
        else {
          text.underline = false;
          text.strike = false;
        }
        break;
      default:
        throw new Error('지원하지 않는 CSS 속성: ' + k);
    }
  }
  if (Object.keys(text).length) s.text = text;
  return s;
}
export function stylesheet(
  sheets: readonly string[],
  known: Set<string>,
): { tokens: DesignObject[]; styles: DesignObject[] } {
  const tokens: DesignObject[] = [],
    styles: DesignObject[] = [],
    rules: { id: string; decl: Declarations }[] = [];
  for (const sheet of sheets)
    for (const rule of postcss.parse(sheet).nodes) {
      requireMarkup(rule.type === 'rule', 'CSS 선택자 선언만 사용할 수 있습니다.');
      const d = declarations(rule);
      if (rule.selector === ':root') {
        for (const [k, v] of Object.entries(d)) {
          requireMarkup(k.startsWith('--') && hex.test(v), '색상 토큰은 --이름과 16진 색상을 사용해 주세요.');
          const id = identifier(k.slice(2));
          tokens.push({ id, name: id, type: 'color', value: v });
          known.add(id);
        }
      } else {
        requireMarkup(
          /^\.[A-Za-z_][\w-]*$/.test(rule.selector),
          '단일 클래스 선택자만 지원합니다: ' + rule.selector,
        );
        rules.push({ id: rule.selector.slice(1), decl: d });
      }
    }
  for (const r of rules) styles.push({ id: r.id, name: r.id, value: surface(r.decl, known) });
  return { tokens, styles };
}
function basis(v: string): DesignJson {
  if (v === 'auto') return v;
  if (v.endsWith('%')) return { percent: positive(numberValue(v.slice(0, -1))) };
  return px(v);
}
function flex(v: string): DesignObject {
  const parts = v.trim().split(/\s+/);
  if (parts.length === 1) return { grow: positive(numberValue(parts[0])), shrink: 1, basis: { percent: 0 } };
  const [grow, shrink, b] = parts;
  requireMarkup(
    parts.length === 3 && b !== undefined,
    'flex에는 숫자 하나 또는 grow shrink basis를 지정해 주세요.',
  );
  return { grow: positive(numberValue(grow)), shrink: positive(numberValue(shrink)), basis: basis(b) };
}
function tracks(v: string): DesignJson[] {
  const words = v.match(/minmax\(0,\s*(?:\d+(?:\.\d+)?|\.\d+)fr\)|\S+/g) ?? [];
  requireMarkup(words.length > 0, '그리드 열을 지정해 주세요.');
  return words.map((w) => {
    const fit = /^minmax\(0,\s*(.+)fr\)$/.exec(w);
    if (!fit) return px(w);
    const fr = numberValue(fit[1]);
    requireMarkup(fr > 0, 'fr 값은 0보다 커야 합니다.');
    return { fr, min: 0 };
  });
}
export function nodeStyle(d: Declarations, isBox: boolean, tokens: Set<string>): Fields {
  const out: Fields = {},
    other: Declarations = {},
    layoutProps = ['display', 'flex-direction', 'gap', 'padding', 'grid-template-columns'];
  const s: Fields = {},
    flexProps: Fields = {};
  for (const [k, v] of Object.entries(d)) {
    if (layoutProps.includes(k)) {
      requireMarkup(isBox, k + ' 속성에는 상자가 필요합니다.');
      continue;
    }
    if (k === 'grid-column') {
      const match = /^span\s+(\d+)$/.exec(v);
      if (/^1\s*\/\s*-1$/.test(v)) out.cell = { column: 'all' };
      else {
        const n = Number(match?.[1]);
        requireMarkup(Number.isSafeInteger(n) && n >= 1, '그리드 열 범위가 잘못되었습니다.');
        out.cell = { column: { span: n } };
      }
    } else if (k === 'flex') {
      requireMarkup(
        !['flex-grow', 'flex-shrink', 'flex-basis'].some((p) => Object.hasOwn(d, p)),
        'flex 선언이 겹칩니다.',
      );
      out.flexItem = flex(v);
    } else if (k === 'flex-grow' || k === 'flex-shrink')
      flexProps[k === 'flex-grow' ? 'grow' : 'shrink'] = positive(numberValue(v));
    else if (k === 'flex-basis') flexProps.basis = basis(v);
    else if (k === 'box-sizing') {
      requireMarkup(v === 'border-box' || v === 'content-box', '잘못된 box-sizing');
      out.boxSizing = v;
    } else if (k === 'width' || k === 'height') s[k === 'width' ? 'x' : 'y'] = { mode: 'fixed', px: px(v) };
    else {
      requireMarkup(!['__proto__', 'constructor', 'prototype'].includes(k), '지원하지 않는 CSS 속성: ' + k);
      other[k] = v;
    }
  }
  if (Object.keys(s).length) out.size = s;
  if (Object.keys(flexProps).length) out.flexItem = flexProps;
  if (isBox) {
    const layout: Fields = {};
    if (d.display === 'grid') {
      requireMarkup(!d['flex-direction'] && d['grid-template-columns'], '그리드 열이 필요합니다.');
      layout.mode = 'grid';
      layout.columns = tracks(d['grid-template-columns']);
    } else {
      requireMarkup(
        !d['grid-template-columns'] &&
          (!d.display || d.display === 'flex') &&
          (!d['flex-direction'] || d.display === 'flex'),
        '지원하지 않는 배치 선언입니다.',
      );
      const mode = d['flex-direction'] ?? (d.display === 'flex' ? 'row' : 'column');
      requireMarkup(mode === 'row' || mode === 'column', '지원하지 않는 flex 방향입니다.');
      layout.mode = mode;
    }
    if (d.gap !== undefined) layout.gap = px(d.gap);
    if (d.padding !== undefined) {
      const n = px(d.padding);
      layout.padding = { top: n, right: n, bottom: n, left: n };
    }
    out.layout = layout;
  }
  const style = surface(other, tokens);
  if (Object.keys(style).length) out.style = style;
  return out;
}
