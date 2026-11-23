import { useState } from 'react';
import type { DesignJson, DesignObject } from '../../../../../../domain/document/designBlock';
import { asNumber, asObject, asText } from '../../../../../../domain/flow/designValue';
import {
  precisionSelection,
  precisionSet,
  precisionSiblings,
  precisionValue,
  type PrecisionEdit,
  type PrecisionPath,
} from '../../../../../../domain/flow/precisionDesign';
import { DesignProperty } from './DesignProperty';

type Field = {
  path: string;
  label: string;
  type: 'number' | 'color' | 'text' | 'flag' | 'choice';
  initial?: string | number | boolean;
  min?: number;
  max?: number;
  options?: Readonly<Record<string, string>>;
};
export type ColorToken = { id: string; name: string };
type FieldsProps = {
  node: DesignObject;
  parts: PrecisionPath;
  onChange: (edit: PrecisionEdit) => void;
  colorTokens?: readonly ColorToken[];
};
const numeric = (path: string, label: string, initial = 0, min?: number, max?: number): Field => ({
  path,
  label,
  type: 'number',
  initial,
  min,
  max,
});
const color = (path: string, label: string): Field => ({ path, label, type: 'color' });
const flag = (path: string, label: string, initial = false): Field => ({
  path,
  label,
  type: 'flag',
  initial,
});
const choice = (path: string, label: string, options: Record<string, string>, initial: string): Field => ({
  path,
  label,
  type: 'choice',
  options,
  initial,
});
const opacity = numeric('opacity', '불투명도', 1, 0, 1);
const hex = /^#(?:[\da-f]{3,4}|[\da-f]{6}|[\da-f]{8})$/i;

function coordinates(prefix: string, name: string): Field[] {
  return [numeric(prefix + 'x', name + ' X'), numeric(prefix + 'y', name + ' Y')];
}

function rootFields(node: DesignObject): Field[] {
  if (node.kind === 'boolean') {
    return [
      choice(
        'operation',
        '도형 연산',
        { union: '합치기', subtract: '빼기', intersect: '교차', xor: '배타합' },
        'union',
      ),
    ];
  }
  if (node.kind === 'mask') {
    return [choice('mode', '마스크 방식', { alpha: '알파', luminance: '휘도', outline: '윤곽' }, 'alpha')];
  }
  if (node.kind === 'vector' && node.contours) {
    return [choice('fillRule', '채움 규칙', { nonzero: '감김 수', evenodd: '홀짝' }, 'nonzero')];
  }
  if (node.kind === 'text') return typography('style.text.');
  if (node.kind !== 'shape') return [];
  const shape = asObject(node.geometry);
  if (shape.kind === 'rect')
    return [
      ...coordinates('geometry.origin.', '원점'),
      numeric('geometry.width', '너비', 0, 0),
      numeric('geometry.height', '높이', 0, 0),
    ];
  if (shape.kind === 'line')
    return [...coordinates('geometry.from.', '시작'), ...coordinates('geometry.to.', '끝')];
  const fields = [
    ...coordinates('geometry.center.', '중심'),
    numeric('geometry.radii.x', '가로 반지름', 0, 0),
    numeric('geometry.radii.y', '세로 반지름', 0, 0),
  ];
  if (shape.kind === 'arc')
    fields.push(
      numeric('geometry.startAngle', '시작 각도'),
      numeric('geometry.sweepAngle', '호 각도', 0, -360, 360),
    );
  if (shape.kind === 'polygon' || shape.kind === 'star')
    fields.push(numeric('geometry.rotation', '회전 각도'));
  if (shape.kind === 'star') fields.push(numeric('geometry.innerRatio', '내부 반경 비율', 0.5, 0, 1));
  return fields;
}

function typography(prefix: string): Field[] {
  return [
    numeric(prefix + 'size', '글자 크기', 14, 0.01),
    numeric(prefix + 'weight', '굵기', 400, 1, 1000),
    numeric(prefix + 'letterSpacing', '자간'),
    color(prefix + 'color', '글자색'),
    flag(prefix + 'italic', '기울임'),
    flag(prefix + 'underline', '밑줄'),
    flag(prefix + 'strike', '취소선'),
  ];
}

function partFields(node: DesignObject, parts: PrecisionPath, member: DesignObject): Field[] {
  const collection = parts.at(-1)?.collection;
  if (!collection) return rootFields(node);
  const adjacent = precisionSiblings(node, parts);
  const index = adjacent.findIndex((x) => x.id === member.id);
  const parent = precisionSelection(node, parts.slice(0, -1))?.member;
  switch (collection) {
    case 'vertices': {
      const fields = coordinates('', '꼭짓점');
      if (parts.at(-2)?.collection === 'contours') {
        const previous = adjacent[index - 1] ?? (parent?.closed === true ? adjacent.at(-1) : undefined);
        if (previous && !previous.arcToNext) fields.push(...coordinates('in.', '들어오는 손잡이'));
        if ((index < adjacent.length - 1 || parent?.closed === true) && !member.arcToNext)
          fields.push(...coordinates('out.', '나가는 손잡이'));
        if (member.arcToNext)
          fields.push(
            numeric('arcToNext.rx', '호 가로 반지름', 1, 0.01),
            numeric('arcToNext.ry', '호 세로 반지름', 1, 0.01),
            numeric('arcToNext.rotation', '호 회전 각도'),
          );
      }
      return fields;
    }
    case 'segments':
      return [...coordinates('out.', '시작점 손잡이'), ...coordinates('in.', '끝점 손잡이')];
    case 'regions':
      return [choice('fillRule', '면의 채움 규칙', { nonzero: '감김 수', evenodd: '홀짝' }, 'nonzero')];
    case 'paint':
    case 'fills':
    case 'textFills':
      return [
        ...(member.kind === 'solid' ? [color('color', '색')] : []),
        opacity,
        flag('visible', '표시', true),
      ];
    case 'stops':
      return [
        numeric(
          'at',
          '그라데이션 위치',
          0,
          asNumber(adjacent[index - 1]?.at) ?? 0,
          asNumber(adjacent[index + 1]?.at) ?? 1,
        ),
        color('color', '정지점 색'),
      ];
    case 'strokes':
      return [
        numeric('width', '선 두께', 1, 0),
        choice('cap', '선 끝', { butt: '평평하게', round: '둥글게', square: '사각으로' }, 'butt'),
        choice('join', '선 이음', { miter: '각지게', round: '둥글게', bevel: '깎기' }, 'miter'),
        flag('nonScaling', '확대해도 선 두께 유지'),
        opacity,
        flag('visible', '표시', true),
      ];
    case 'effects':
      return member.kind === 'dropShadow' || member.kind === 'innerShadow'
        ? [
            ...coordinates('', '그림자 이동'),
            numeric('blur', '흐림', 0, 0),
            numeric('spread', '확산'),
            color('color', '그림자 색'),
            flag('visible', '표시', true),
          ]
        : [numeric('radius', '흐림 반경', 0, 0), flag('visible', '표시', true)];
    case 'runs':
      return [
        ...(typeof member.text === 'string'
          ? [{ path: 'text', label: '구간 문구', type: 'text' } as const]
          : []),
        ...typography('style.'),
      ];
    case 'paragraphs':
      return [
        choice(
          'style.align',
          '문단 정렬',
          { start: '시작', end: '끝', left: '왼쪽', center: '가운데', right: '오른쪽', justify: '양쪽' },
          'start',
        ),
        numeric('style.spaceBefore', '문단 앞 간격'),
        numeric('style.spaceAfter', '문단 뒤 간격'),
      ];
    default:
      return [];
  }
}

function fieldEdit(
  parts: PrecisionPath,
  member: DesignObject,
  path: string,
  value: DesignJson,
): PrecisionEdit {
  // A new handle needs both coordinates. Existing handles inherit the untouched coordinate.
  const [stem, axis] = path.split('.');
  if ((stem === 'in' || stem === 'out') && !member[stem] && (axis === 'x' || axis === 'y')) {
    return precisionSet(parts, stem, { x: 0, y: 0, [axis]: value });
  }
  return precisionSet(parts, path, value);
}

function colorComponents(value: string): { rgb: string; alpha: string } {
  const digits = value.slice(1);
  const expanded = digits.length <= 4 ? digits.replace(/[\da-f]/gi, '$&$&') : digits;
  return { rgb: '#' + expanded.slice(0, 6), alpha: expanded.slice(6) };
}

function Control({
  field,
  value,
  commit,
  tokens,
}: {
  field: Field;
  value: DesignJson | undefined;
  commit: (v: DesignJson) => void;
  tokens: readonly ColorToken[];
}) {
  const [error, setError] = useState('');
  const scalar = value ?? field.initial;
  const shown = typeof scalar === 'string' || typeof scalar === 'number' ? String(scalar) : '';
  const reference = asText(asObject(value).token);
  if (field.type === 'flag')
    return (
      <label className="design-checkbox">
        <input type="checkbox" checked={scalar === true} onChange={(e) => commit(e.target.checked)} />
        {field.label}
      </label>
    );
  if (field.type === 'choice')
    return (
      <label className="design-property">
        <span>{field.label}</span>
        <select value={shown} onChange={(e) => commit(e.target.value)}>
          {Object.entries(field.options ?? {}).map(([v, title]) => (
            <option key={v} value={v}>
              {title}
            </option>
          ))}
        </select>
      </label>
    );
  function submit(input: string) {
    const n = Number(input);
    if (
      field.type === 'number' &&
      (!input.trim() ||
        !Number.isFinite(n) ||
        (field.min !== undefined && n < field.min) ||
        (field.max !== undefined && n > field.max))
    ) {
      const failure =
        field.label +
        '의 범위를 확인하세요.' +
        (field.min !== undefined ? ' 최소 ' + String(field.min) + '.' : '') +
        (field.max !== undefined ? ' 최대 ' + String(field.max) + '.' : '');
      setError(failure);
      return failure;
    }
    if (field.type === 'color' && !hex.test(input) && input !== 'currentColor') {
      const failure = '16진수 색 또는 currentColor를 입력하세요.';
      setError(failure);
      return failure;
    }
    setError('');
    commit(field.type === 'number' ? n : input);
    return null;
  }
  const mode = reference ? 'token' : shown ? 'literal' : 'unset';
  const { rgb, alpha } = colorComponents(hex.test(shown) ? shown : '#000000');
  return (
    <div>
      <DesignProperty
        label={field.label}
        type={field.type === 'number' ? 'number' : 'text'}
        value={shown}
        onCommit={submit}
      />
      {field.type === 'color' && (
        <div className="design-precision-colors">
          <label>
            <span className="visually-hidden">{field.label} 고르기</span>
            <input type="color" value={rgb} onChange={(e) => commit(e.target.value + alpha)} />
          </label>
          {(tokens.length > 0 || reference) && (
            <select
              aria-label={field.label + ' 토큰'}
              value={mode === 'token' ? reference : ''}
              onChange={(e) => {
                if (e.target.value) commit({ token: e.target.value });
              }}
            >
              <option value="">색 토큰 선택</option>
              {reference && !tokens.some((t) => t.id === reference) && (
                <option value={reference}>{reference}</option>
              )}
              {tokens.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          )}
        </div>
      )}
      {error && <small role="alert">{error}</small>}
    </div>
  );
}

export function DesignPrecisionFields({ node, parts, onChange, colorTokens = [] }: FieldsProps) {
  const selected = precisionSelection(node, parts);
  if (!selected) return null;
  return partFields(node, parts, selected.member).map((field) => (
    <Control
      key={field.path}
      field={field}
      value={precisionValue(selected.member, field.path)}
      tokens={colorTokens}
      commit={(v) => onChange(fieldEdit(parts, selected.member, field.path, v))}
    />
  ));
}
