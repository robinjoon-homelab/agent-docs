import type { DesignJson } from '../../../../../../domain/document/designBlock';
import { asNumber, asObject } from '../../../../../../domain/flow/designValue';

export type StyleValue = DesignJson | undefined;
export type AssetUrl = (id: string) => string;

export function keyword<const T extends string>(value: StyleValue, ...allowed: T[]): T | undefined {
  return allowed.find((option) => option === value);
}

export function length(value: StyleValue): string | undefined {
  const number = asNumber(value);
  if (number !== undefined) return `${number}px`;
  const percent = asNumber(asObject(value).percent);
  return percent === undefined ? undefined : `${percent}%`;
}

export function numbers(value: StyleValue, count: number): number[] | undefined {
  if (!Array.isArray(value) || value.length !== count) return undefined;
  const finite = value.filter((v): v is number => typeof v === 'number' && Number.isFinite(v));
  return finite.length === count ? finite : undefined;
}

export function designColor(value: StyleValue): string | undefined {
  if (value === 'currentColor') return value;
  if (typeof value === 'string')
    return /^#(?:[\da-f]{3,4}|[\da-f]{6}|[\da-f]{8})$/i.test(value) ? value : undefined;
  const color = asObject(value);
  const channels = numbers(color.channels, 3);
  const alpha = asNumber(color.alpha) ?? 1;
  if (!channels || alpha < 0 || alpha > 1) return undefined;
  if (color.space === 'oklch') return `oklch(${channels.join(' ')} / ${alpha})`;
  const space = keyword(color.space, 'srgb', 'srgb-linear', 'display-p3');
  return space ? `color(${space} ${channels.join(' ')} / ${alpha})` : undefined;
}

export const validAssetId = (value: StyleValue): value is string =>
  typeof value === 'string' && /^[a-zA-Z0-9_-]+$/.test(value);

export function designFontFaceName(asset: string, face = 0): string {
  return `flow-font-${asset}-${face}`;
}

export function quoted(value: string): string {
  return (
    '"' +
    Array.from(value, (ch) => {
      const code = ch.charCodeAt(0);
      if (code < 32 || code === 127) return '\\' + code.toString(16) + ' ';
      return ch === '\\' || ch === '"' ? '\\' + ch : ch;
    }).join('') +
    '"'
  );
}

export function colorWithOpacity(value: StyleValue, opacity?: StyleValue): string | undefined {
  const color = designColor(value);
  const alpha = asNumber(opacity);
  return color && alpha !== undefined && alpha < 1
    ? `color-mix(in srgb, ${color} ${Math.max(0, alpha) * 100}%, transparent)`
    : color;
}

export function blendMode(value: StyleValue) {
  return keyword(
    value,
    'normal',
    'multiply',
    'screen',
    'overlay',
    'darken',
    'lighten',
    'color-dodge',
    'color-burn',
    'hard-light',
    'soft-light',
    'difference',
    'exclusion',
    'hue',
    'saturation',
    'color',
    'luminosity',
    'plus-lighter',
  );
}
