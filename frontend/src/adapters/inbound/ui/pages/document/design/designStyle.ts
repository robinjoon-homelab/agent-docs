import type { CSSProperties } from 'react';
import type { DesignObject } from '../../../../../../domain/document/designBlock';
import { asNumber, asObject, asObjects } from '../../../../../../domain/flow/designValue';
import { layoutStyle } from './designStyleLayout';
import { backgroundStyle, compositeStyle } from './designStyleSurface';
import {
  designFontFaceName,
  validAssetId,
  quoted,
  designColor,
  colorWithOpacity,
  keyword,
  length,
  numbers,
  type AssetUrl,
  type StyleValue,
} from './designStyleValues';

export { designColor, designFontFaceName } from './designStyleValues';

const canvasSans = '"Agent Docs Design Sans"';
const canvasMono = '"Agent Docs Design Mono"';
const bodyFont = canvasSans + ', system-ui, sans-serif';
const monoFont = canvasMono + ', ' + canvasSans + ', ui-monospace, monospace';

function family(ref: DesignObject): string | undefined {
  if (
    typeof ref.local === 'string' &&
    ref.local.trim() &&
    !/^\s*(?:[a-z][a-z\d+.-]*:|\/\/|url\s*\()/i.test(ref.local)
  )
    return ref.local.trim().toLowerCase() === 'noto sans kr'
      ? canvasSans
      : ref.local.trim().toLowerCase() === 'jetbrains mono'
        ? canvasMono
        : quoted(ref.local);
  if (validAssetId(ref.asset))
    return `var(--${designFontFaceName(ref.asset, asNumber(ref.face))}, ${canvasSans})`;
  if (ref.system === 'body') return bodyFont;
  if (ref.system === 'mono') return monoFont;
  return keyword(ref.system, 'sans-serif', 'serif', 'monospace');
}

function fontSettings(value: StyleValue): string | undefined {
  const entries = Object.entries(asObject(value)).flatMap(([tag, v]) => {
    const n = asNumber(v);
    return /^[\x20-\x7e]{4}$/.test(tag) && n !== undefined ? [`${quoted(tag)} ${n}`] : [];
  });
  return entries.length ? entries.join(', ') : undefined;
}

function textPaint(p: DesignObject, assetUrl?: AssetUrl) {
  if (p.kind === 'solid') {
    const color = colorWithOpacity(p.color, p.opacity);
    return color ? `linear-gradient(${color}, ${color})` : undefined;
  }
  if (p.kind === 'image') {
    const id = asObject(p.image).asset;
    return validAssetId(id) && assetUrl ? `url(${quoted(assetUrl(id))})` : undefined;
  }
  const coords = numbers(p.matrix, 6) ?? [1, 0, 0, 1, 0, 0];
  const [a = 1, b = 0, c = 0, d = 1, e = 0, f = 0] = coords;
  const x = (a / 2 + c / 2 + e) * 100,
    y = (b / 2 + d / 2 + f) * 100;
  const angle = 90 + (Math.atan2(b, a) * 180) / Math.PI;
  const stops = asObjects(p.stops).flatMap((stop) => {
    const value = colorWithOpacity(stop.color, p.opacity),
      at = asNumber(stop.at);
    return value && at !== undefined ? [`${value} ${at * 100}%`] : [];
  });
  if (stops.length < 2) return undefined;
  if (p.kind === 'linear') return `linear-gradient(${angle}deg, ${stops.join(', ')})`;
  if (p.kind === 'radial')
    return `radial-gradient(ellipse ${Math.hypot(a, b) * 50}% ${Math.hypot(c, d) * 50}% at ${x}% ${y}%, ${stops.join(', ')})`;
  if (p.kind === 'angular') return `conic-gradient(from ${angle}deg at ${x}% ${y}%, ${stops.join(', ')})`;
  return undefined;
}

export function designTextStyle(text: DesignObject, assetUrl?: AssetUrl): CSSProperties {
  const fonts = [
    family(asObject(text.font)) ??
      (text.family === 'body' ? bodyFont : text.family === 'mono' ? monoFont : undefined),
    ...asObjects(text.fallback).map(family),
  ].filter(Boolean);
  const line = asObject(text.lineHeight),
    decoration = asObject(text.decoration);
  const paints = asObjects(text.fills).filter((p) => p.visible !== false),
    only = paints.length === 1 ? paints[0] : undefined;
  const images =
    only?.kind === 'solid' ? [] : paints.toReversed().flatMap((p) => textPaint(p, assetUrl) ?? []);
  const t: CSSProperties = {
    fontFamily: fonts.length ? fonts.join(', ') : undefined,
    fontSize: length(text.size),
    fontWeight: asNumber(text.weight),
    fontStyle: text.italic === undefined ? undefined : text.italic === true ? 'italic' : 'normal',
    fontVariationSettings: fontSettings(text.axes),
    fontFeatureSettings: fontSettings(text.features),
    fontKerning: keyword(text.kerning, 'auto', 'normal', 'none'),
    color: only?.kind === 'solid' ? colorWithOpacity(only.color, only.opacity) : designColor(text.color),
    lineHeight:
      asNumber(line.multiple) ??
      length(text.lineHeight) ??
      (text.lineHeight === 'auto' ? 'normal' : undefined),
    textAlign: keyword(text.align, 'left', 'center', 'right', 'justify', 'start', 'end'),
    letterSpacing: length(text.letterSpacing),
    wordSpacing: length(text.wordSpacing),
    verticalAlign: length(text.baselineShift),
    direction: keyword(text.direction, 'rtl', 'ltr'),
    unicodeBidi: text.direction === 'auto' ? 'plaintext' : undefined,
    writingMode: keyword(text.writingMode, 'horizontal-tb', 'vertical-rl', 'vertical-lr'),
    textTransform: keyword(text.textCase, 'none', 'uppercase', 'lowercase', 'capitalize'),
    whiteSpace: keyword(text.whiteSpace, 'normal', 'nowrap', 'pre-line', 'pre-wrap', 'pre'),
    wordBreak: keyword(text.wordBreak, 'normal', 'keep-all', 'break-all'),
    textDecorationLine:
      [text.underline === true ? 'underline' : '', text.strike === true ? 'line-through' : '']
        .filter(Boolean)
        .join(' ') || undefined,
    textDecorationColor: designColor(decoration.color),
    textDecorationThickness: length(decoration.thickness),
    textUnderlineOffset: length(decoration.underlineOffset),
    textIndent: length(text.indent),
    marginBlockStart: length(text.spaceBefore),
    marginBlockEnd: length(text.spaceAfter),
  };
  if (images.length) {
    t.backgroundImage = images.join(',');
    t.backgroundClip = 'text';
    t.color = 'transparent';
  }
  if (text.ellipsis === true) {
    t.whiteSpace = 'nowrap';
    t.overflow = 'hidden';
    t.textOverflow = 'ellipsis';
  }
  const lines = asNumber(text.maxLines);
  if (lines !== undefined) {
    t.lineClamp = lines;
    t.maxBlockSize = `${lines}lh`;
    t.overflow = 'hidden';
  }
  return t;
}

export function designNodeStyle(node: DesignObject, parentMode?: string, assetUrl?: AssetUrl): CSSProperties {
  const style = asObject(node.style);
  const text = designTextStyle(asObject(style.text), assetUrl);
  // Only defined text declarations participate; text clipping must not clear an authored surface fill.
  const declaredText = Object.fromEntries(
    Object.entries(text).filter(([, v]) => v !== undefined),
  ) as CSSProperties;
  const merged: CSSProperties = {
    ...layoutStyle(node, parentMode),
    ...backgroundStyle(node),
    ...compositeStyle(node),
    ...declaredText,
  };
  return Object.fromEntries(Object.entries(merged).filter(([, value]) => value !== undefined));
}
