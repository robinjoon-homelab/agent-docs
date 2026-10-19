/**
 * Inline text is stored as a GFM inline markdown string. This module converts it to a flat inline AST
 * (text runs with marks and an optional link) and back. The parser implements the platform subset:
 * `**bold**`, `*italic*`, `~~strike~~`, `` `code` ``, `[text](url)`, `[title](doc:<id>)`, `<br>`, and
 * backslash escapes. Mentions (`@login`) stay plain text. `_` is never emphasis on this platform.
 */

export type InlineMarks = {
  readonly bold: boolean;
  readonly italic: boolean;
  readonly strike: boolean;
  readonly code: boolean;
};

export type InlineRun =
  | {
      readonly kind: 'text';
      readonly text: string;
      readonly marks: InlineMarks;
      readonly href: string | null;
    }
  | { readonly kind: 'break' };

export type Inline = readonly InlineRun[];

const BOLD = 1;
const ITALIC = 2;
const STRIKE = 4;
const CODE = 8;

export const NO_MARKS: InlineMarks = { bold: false, italic: false, strike: false, code: false };

export function marksOf(partial: Partial<InlineMarks>): InlineMarks {
  return { ...NO_MARKS, ...partial };
}

export function textRun(
  text: string,
  marks: Partial<InlineMarks> = {},
  href: string | null = null,
): InlineRun {
  return { kind: 'text', text, marks: marksOf(marks), href };
}

export const BREAK: InlineRun = { kind: 'break' };

function toBits(marks: InlineMarks): number {
  return (
    (marks.bold ? BOLD : 0) |
    (marks.italic ? ITALIC : 0) |
    (marks.strike ? STRIKE : 0) |
    (marks.code ? CODE : 0)
  );
}

function fromBits(bits: number): InlineMarks {
  return {
    bold: (bits & BOLD) !== 0,
    italic: (bits & ITALIC) !== 0,
    strike: (bits & STRIKE) !== 0,
    code: (bits & CODE) !== 0,
  };
}

/* ---------------------------------------------------------------------------------------------- */
/* Character model shared by parser output, normalization and the serializer.                      */
/* ---------------------------------------------------------------------------------------------- */

type Char = { readonly ch: string; readonly bits: number; readonly href: string | null };

const BREAK_CHAR = '\n';

function toChars(inline: Inline): Char[] {
  const chars: Char[] = [];
  for (const run of inline) {
    if (run.kind === 'break') {
      chars.push({ ch: BREAK_CHAR, bits: 0, href: null });
      continue;
    }
    const bits = toBits(run.marks);
    for (const ch of run.text) chars.push({ ch, bits, href: run.href });
  }
  return chars;
}

function fromChars(chars: readonly Char[]): InlineRun[] {
  const runs: InlineRun[] = [];
  let buffer = '';
  let current: Char | null = null;
  const flush = (): void => {
    if (current !== null && buffer !== '') runs.push(textRun(buffer, fromBits(current.bits), current.href));
    buffer = '';
  };
  for (const char of chars) {
    if (char.ch === BREAK_CHAR) {
      flush();
      current = null;
      runs.push(BREAK);
      continue;
    }
    if (current === null || current.bits !== char.bits || current.href !== char.href) {
      flush();
      current = char;
    }
    buffer += char.ch;
  }
  flush();
  return runs;
}

/** Merges adjacent runs with identical marks and links and drops empty runs. */
export function compactInline(inline: Inline): Inline {
  return fromChars(toChars(inline));
}

export function inlinePlainText(inline: Inline): string {
  return inline.map((run) => (run.kind === 'break' ? '\n' : run.text)).join('');
}

export function inlineEquals(a: Inline, b: Inline): boolean {
  const left = compactInline(a);
  const right = compactInline(b);
  if (left.length !== right.length) return false;
  return left.every((run, index) => {
    const other = right[index];
    if (other === undefined || run.kind !== other.kind) return false;
    if (run.kind === 'break' || other.kind === 'break') return true;
    return run.text === other.text && run.href === other.href && toBits(run.marks) === toBits(other.marks);
  });
}

/* ---------------------------------------------------------------------------------------------- */
/* Parser                                                                                          */
/* ---------------------------------------------------------------------------------------------- */

type Node =
  | { t: 'text'; text: string; bits: number }
  | { t: 'code'; text: string; bits: number }
  | { t: 'break'; bits: number }
  | { t: 'link'; href: string; children: Char[]; bits: number }
  | Delim;

type Delim = {
  t: 'delim';
  ch: '*' | '~';
  remaining: number;
  readonly length: number;
  readonly canOpen: boolean;
  readonly canClose: boolean;
  bits: number;
};

const ASCII_PUNCTUATION = /[!-/:-@[-`{-~]/;
const BR_TAG = /^<br\s*\/?>/i;

function isWhitespace(ch: string | undefined): boolean {
  return ch === undefined || /\s/.test(ch);
}

function runLength(source: string, start: number, ch: string): number {
  let end = start;
  while (source[end] === ch) end++;
  return end - start;
}

/** Finds the closing backtick run of exactly `length` backticks; returns its index or -1. */
function findCodeClose(source: string, from: number, length: number): number {
  let index = source.indexOf('`', from);
  while (index !== -1) {
    const run = runLength(source, index, '`');
    if (run === length) return index;
    index = source.indexOf('`', index + run);
  }
  return -1;
}

function normalizeCodeContent(raw: string): string {
  const content = raw.replace(/\n/g, ' ');
  if (content.length >= 2 && content.startsWith(' ') && content.endsWith(' ') && content.trim() !== '') {
    return content.slice(1, -1);
  }
  return content;
}

/** Index of the `]` matching the `[` at `open`, skipping escapes and code spans; -1 if none. */
function findLinkTextEnd(source: string, open: number): number {
  let depth = 0;
  let index = open;
  while (index < source.length) {
    const ch = source[index];
    if (ch === '\\' && ASCII_PUNCTUATION.test(source[index + 1] ?? '')) {
      index += 2;
      continue;
    }
    if (ch === '`') {
      const run = runLength(source, index, '`');
      const close = findCodeClose(source, index + run, run);
      index = close === -1 ? index + run : close + run;
      continue;
    }
    if (ch === '[') depth++;
    if (ch === ']') {
      depth--;
      if (depth === 0) return index;
    }
    index++;
  }
  return -1;
}

type Destination = { readonly href: string; readonly end: number };

function unescapePunctuation(text: string): string {
  return text.replace(/\\([!-/:-@[-`{-~])/g, '$1');
}

/** Parses `(dest)` starting at the `(`; supports `<...>` and bare destinations with balanced parens. */
function parseDestination(source: string, open: number): Destination | null {
  if (source[open] !== '(') return null;
  let index = open + 1;
  if (source[index] === '<') {
    const close = source.indexOf('>', index + 1);
    if (close === -1 || source[close + 1] !== ')') return null;
    const raw = source.slice(index + 1, close);
    if (/[<\n]/.test(raw.replace(/\\[<>]/g, ''))) return null;
    return { href: unescapePunctuation(raw), end: close + 2 };
  }
  let depth = 0;
  const start = index;
  while (index < source.length) {
    const ch = source[index] ?? '';
    if (ch === '\\' && ASCII_PUNCTUATION.test(source[index + 1] ?? '')) {
      index += 2;
      continue;
    }
    if (/\s/.test(ch)) return null;
    if (ch === '(') depth++;
    if (ch === ')') {
      if (depth === 0) return { href: unescapePunctuation(source.slice(start, index)), end: index + 1 };
      depth--;
    }
    index++;
  }
  return null;
}

type Scan = { readonly node: Node; readonly next: number };

function scanBackslash(source: string, index: number): Scan {
  const next = source[index + 1];
  if (next !== undefined && ASCII_PUNCTUATION.test(next)) {
    return { node: { t: 'text', text: next, bits: 0 }, next: index + 2 };
  }
  return { node: { t: 'text', text: '\\', bits: 0 }, next: index + 1 };
}

function scanBackticks(source: string, index: number): Scan {
  const run = runLength(source, index, '`');
  const close = findCodeClose(source, index + run, run);
  if (close === -1) return { node: { t: 'text', text: '`'.repeat(run), bits: 0 }, next: index + run };
  const text = normalizeCodeContent(source.slice(index + run, close));
  return { node: { t: 'code', text, bits: 0 }, next: close + run };
}

function scanDelimiter(source: string, index: number, ch: '*' | '~'): Scan {
  const length = runLength(source, index, ch);
  const before = index === 0 ? undefined : source[index - 1];
  const after = source[index + length];
  if (ch === '~' && length !== 2) {
    return { node: { t: 'text', text: '~'.repeat(length), bits: 0 }, next: index + length };
  }
  const delim: Delim = {
    t: 'delim',
    ch,
    remaining: length,
    length,
    canOpen: !isWhitespace(after),
    canClose: !isWhitespace(before),
    bits: 0,
  };
  return { node: delim, next: index + length };
}

function scanLink(source: string, index: number, inLink: boolean): Scan | null {
  if (inLink) return null;
  const textEnd = findLinkTextEnd(source, index);
  if (textEnd === -1) return null;
  const destination = parseDestination(source, textEnd + 1);
  if (destination === null) return null;
  const children = toChars(parseWithin(source.slice(index + 1, textEnd), true));
  return { node: { t: 'link', href: destination.href, children, bits: 0 }, next: destination.end };
}

function scanAt(source: string, index: number, inLink: boolean): Scan | null {
  const ch = source[index];
  if (ch === '\\') return scanBackslash(source, index);
  if (ch === '`') return scanBackticks(source, index);
  if (ch === '*' || ch === '~') return scanDelimiter(source, index, ch);
  if (ch === '\n') return { node: { t: 'break', bits: 0 }, next: index + 1 };
  if (ch === '<') {
    const match = BR_TAG.exec(source.slice(index));
    if (match !== null) return { node: { t: 'break', bits: 0 }, next: index + match[0].length };
  }
  if (ch === '[') return scanLink(source, index, inLink);
  return null;
}

function tokenize(source: string, inLink: boolean): Node[] {
  const nodes: Node[] = [];
  let text = '';
  let index = 0;
  while (index < source.length) {
    const scan = scanAt(source, index, inLink);
    if (scan === null) {
      text += source[index] ?? '';
      index++;
      continue;
    }
    if (text !== '') nodes.push({ t: 'text', text, bits: 0 });
    text = '';
    nodes.push(scan.node);
    index = scan.next;
  }
  if (text !== '') nodes.push({ t: 'text', text, bits: 0 });
  return nodes;
}

/** CommonMark "rule of 3": a run that can both open and close must not pair into a multiple of 3. */
function canPair(opener: Delim, closer: Delim): boolean {
  if (opener.ch !== closer.ch || opener.remaining === 0) return false;
  if (opener.ch === '~') return opener.remaining === 2 && closer.remaining === 2;
  if (opener.canClose || closer.canOpen) {
    const sum = opener.length + closer.length;
    if (sum % 3 === 0 && !(opener.length % 3 === 0 && closer.length % 3 === 0)) return false;
  }
  return true;
}

function applyBits(nodes: Node[], from: number, to: number, bits: number): void {
  for (let index = from + 1; index < to; index++) {
    const node = nodes[index];
    if (node !== undefined) node.bits |= bits;
  }
}

function matchCloser(nodes: Node[], stack: number[], closerIndex: number): void {
  const closer = nodes[closerIndex] as Delim;
  while (closer.remaining > 0) {
    let position = stack.length - 1;
    while (position >= 0 && !canPair(nodes[stack[position] ?? -1] as Delim, closer)) position--;
    if (position < 0) return;
    const openerIndex = stack[position] ?? -1;
    const opener = nodes[openerIndex] as Delim;
    const use = closer.ch === '~' ? 2 : opener.remaining >= 2 && closer.remaining >= 2 ? 2 : 1;
    applyBits(nodes, openerIndex, closerIndex, closer.ch === '~' ? STRIKE : use === 2 ? BOLD : ITALIC);
    opener.remaining -= use;
    closer.remaining -= use;
    stack.splice(opener.remaining === 0 ? position : position + 1);
  }
}

function processEmphasis(nodes: Node[]): void {
  const stack: number[] = [];
  nodes.forEach((node, index) => {
    if (node.t !== 'delim') return;
    if (node.canClose) matchCloser(nodes, stack, index);
    if (node.remaining > 0 && node.canOpen) stack.push(index);
  });
}

function flatten(nodes: readonly Node[]): Char[] {
  const chars: Char[] = [];
  const pushText = (text: string, bits: number, href: string | null = null): void => {
    for (const ch of text) chars.push({ ch, bits, href });
  };
  for (const node of nodes) {
    if (node.t === 'text') pushText(node.text, node.bits);
    if (node.t === 'code') pushText(node.text, node.bits | CODE);
    if (node.t === 'delim') pushText(node.ch.repeat(node.remaining), node.bits);
    if (node.t === 'break') chars.push({ ch: BREAK_CHAR, bits: 0, href: null });
    if (node.t === 'link') {
      for (const child of node.children) {
        chars.push(
          child.ch === BREAK_CHAR ? child : { ...child, bits: child.bits | node.bits, href: node.href },
        );
      }
    }
  }
  return chars;
}

function parseWithin(source: string, inLink: boolean): InlineRun[] {
  const nodes = tokenize(source, inLink);
  processEmphasis(nodes);
  return fromChars(flatten(nodes));
}

export function parseInline(markdown: string): Inline {
  return parseWithin(markdown, false);
}

/** What the reader sees; anchors use offsets into this string. */
export function plainText(markdown: string): string {
  return inlinePlainText(parseInline(markdown));
}

/* ---------------------------------------------------------------------------------------------- */
/* Serializer                                                                                      */
/* ---------------------------------------------------------------------------------------------- */

const DELIMITER_MARKS = [STRIKE, BOLD, ITALIC] as const;
const OPEN_TOKEN: Record<number, string> = { [STRIKE]: '~~', [BOLD]: '**', [ITALIC]: '*' };

function isTrimmable(char: Char): boolean {
  return (char.ch === BREAK_CHAR || /\s/.test(char.ch)) && (char.bits & CODE) === 0;
}

function trimChars(chars: readonly Char[]): Char[] {
  let start = 0;
  let end = chars.length;
  while (start < end && isTrimmable(chars[start] as Char)) start++;
  while (end > start && isTrimmable(chars[end - 1] as Char)) end--;
  return chars.slice(start, end);
}

/** Emphasis cannot start or end on whitespace in markdown, so move such whitespace out of the mark. */
function shedEdgeWhitespace(chars: Char[], mark: number): void {
  let index = 0;
  while (index < chars.length) {
    if (((chars[index]?.bits ?? 0) & mark) === 0) {
      index++;
      continue;
    }
    let end = index;
    while (end < chars.length && ((chars[end]?.bits ?? 0) & mark) !== 0) end++;
    let left = index;
    while (left < end && /\s/.test(chars[left]?.ch ?? '')) {
      chars[left] = clearBit(chars[left], mark);
      left++;
    }
    let right = end - 1;
    while (right >= left && /\s/.test(chars[right]?.ch ?? '')) {
      chars[right] = clearBit(chars[right], mark);
      right--;
    }
    index = end;
  }
}

function clearBit(char: Char | undefined, mark: number): Char {
  const base = char ?? { ch: '', bits: 0, href: null };
  return { ...base, bits: base.bits & ~mark };
}

/** Canonical form of an AST before saving: trimmed, no newlines inside runs, emphasis edges tight. */
export function normalizeInline(inline: Inline): Inline {
  const chars = trimChars(toChars(inline));
  for (const mark of DELIMITER_MARKS) shedEdgeWhitespace(chars, mark);
  return fromChars(chars);
}

export type SavedSelection = { readonly text: string; readonly start: number; readonly end: number };

/**
 * Maps a selection in the editor's text onto the text that will be stored. Saving trims leading and
 * trailing whitespace, so anchors must use offsets into the trimmed text, not the editor's.
 */
export function savedSelection(inline: Inline, start: number, end: number): SavedSelection {
  const chars = toChars(inline);
  let lead = 0;
  while (lead < chars.length && isTrimmable(chars[lead] as Char)) lead++;
  const text = inlinePlainText(normalizeInline(inline));
  const clamp = (offset: number): number => Math.min(Math.max(offset - lead, 0), text.length);
  return { text, start: clamp(start), end: clamp(end) };
}

type EscapeLevel = 'minimal' | 'full';

function escapeText(text: string, level: EscapeLevel): string {
  let out = '';
  for (let index = 0; index < text.length; index++) {
    const ch = text[index] ?? '';
    const next = text[index + 1];
    if (ch === '\\') {
      const needed = level === 'full' || next === undefined || ASCII_PUNCTUATION.test(next);
      out += needed ? '\\\\' : '\\';
      continue;
    }
    if (ch === '<' && BR_TAG.test(text.slice(index))) {
      out += '\\<';
      continue;
    }
    if (level === 'full' && /[*~`[\]]/.test(ch)) {
      out += `\\${ch}`;
      continue;
    }
    out += ch;
  }
  return out;
}

function codeSpan(text: string): string {
  const longest = Math.max(0, ...Array.from(text.matchAll(/`+/g), (match) => match[0].length));
  const fence = '`'.repeat(longest + 1);
  const pad = text.includes('`') || (text.startsWith(' ') && text.endsWith(' ') && text.trim() !== '');
  return pad ? `${fence} ${text} ${fence}` : `${fence}${text}${fence}`;
}

function destination(href: string): string {
  if (href === '' || /[\s()<>]/.test(href)) return `<${href.replace(/[\\<>]/g, (ch) => `\\${ch}`)}>`;
  return href.replace(/\\/g, '\\\\');
}

type OpenMark = { readonly bit: number; readonly href: string | null };

function sameOpen(a: OpenMark, b: OpenMark): boolean {
  return a.bit === b.bit && a.href === b.href;
}

function desiredMarks(run: InlineRun): OpenMark[] {
  if (run.kind === 'break') return [];
  const bits = toBits(run.marks);
  // Emphasis wraps links ("**[a](u)**"), the form agents and people write most often.
  const marks: OpenMark[] = DELIMITER_MARKS.filter((mark) => (bits & mark) !== 0).map((bit) => ({
    bit,
    href: null,
  }));
  if (run.href !== null) marks.push({ bit: 0, href: run.href });
  return marks;
}

function closeToken(mark: OpenMark): string {
  return mark.href === null ? (OPEN_TOKEN[mark.bit] ?? '') : `](${destination(mark.href)})`;
}

function openToken(mark: OpenMark): string {
  return mark.href === null ? (OPEN_TOKEN[mark.bit] ?? '') : '[';
}

function hasMark(run: InlineRun | undefined, mark: OpenMark): boolean {
  if (run === undefined || run.kind === 'break') return false;
  return mark.href === null ? (toBits(run.marks) & mark.bit) !== 0 : run.href === mark.href;
}

/** How many runs from `index` on keep `mark`; longer-lived marks open first so they need no splitting. */
function extent(inline: Inline, index: number, mark: OpenMark): number {
  let end = index;
  while (hasMark(inline[end], mark)) end++;
  return end - index;
}

function serializeWith(inline: Inline, level: EscapeLevel): string {
  const active: OpenMark[] = [];
  let out = '';
  inline.forEach((run, index) => {
    const desired = run.kind === 'break' ? active.filter((mark) => mark.href !== null) : desiredMarks(run);
    let keep = 0;
    while (keep < active.length && desired.some((mark) => sameOpen(mark, active[keep] as OpenMark))) keep++;
    while (active.length > keep) out += closeToken(active.pop() as OpenMark);
    const opening = desired
      .filter((mark) => !active.some((open) => sameOpen(open, mark)))
      .sort((a, b) => extent(inline, index, b) - extent(inline, index, a));
    for (const mark of opening) {
      active.push(mark);
      out += openToken(mark);
    }
    if (run.kind === 'break') out += '<br>';
    else out += run.marks.code ? codeSpan(run.text) : escapeText(run.text, level);
  });
  while (active.length > 0) out += closeToken(active.pop() as OpenMark);
  return out;
}

/**
 * Canonical markdown for an inline AST. Tries minimal escaping first so typed text like "2 * 3" stays
 * readable for agents, and falls back to escaping every markup character when the minimal form would
 * parse differently.
 *
 * Known limit: marks that cross each other right next to markup characters or whitespace (for example
 * italic ending inside a bold run that starts with a space) can need adjacent delimiter runs that
 * markdown cannot express; that formatting is then simplified on the next load.
 */
export function serializeInline(inline: Inline): string {
  const normalized = normalizeInline(inline);
  const minimal = serializeWith(normalized, 'minimal');
  if (inlineEquals(parseInline(minimal), normalized)) return minimal;
  return serializeWith(normalized, 'full');
}

/**
 * Keeps the stored markdown when the reader-visible content did not change, so untouched blocks are
 * sent back byte-for-byte and the server does not see spurious edits.
 */
export function serializeInlinePreserving(inline: Inline, original: string | undefined): string {
  if (original !== undefined && inlineEquals(parseInline(original), inline)) return original;
  return serializeInline(inline);
}
