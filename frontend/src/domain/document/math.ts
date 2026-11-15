export type MathSpan = {
  readonly from: number;
  readonly to: number;
  readonly source: string;
  readonly display: boolean;
};

function escapedAt(text: string, index: number): boolean {
  let count = 0;
  while (index > 0 && text[--index] === '\\') count++;
  return count % 2 === 1;
}

/** Delimiters remain in the editable text, so anchors use the exact same offsets on both sides. */
export function mathAt(text: string, from: number): MathSpan | null {
  if (text[from] !== '$') return null;
  const delimiter = text.startsWith('$$', from) ? '$$' : '$';
  const start = from + delimiter.length;
  let close = text.indexOf(delimiter, start);
  while (close >= 0 && escapedAt(text, close)) close = text.indexOf(delimiter, close + delimiter.length);
  if (close <= start || text.slice(start, close).trim() === '') return null;
  const source = text.slice(start, close);
  if (delimiter === '$' && (source.includes('\n') || /^\s|\s$/.test(source))) return null;
  return { from, to: close + delimiter.length, source, display: delimiter === '$$' };
}

export function mathSpans(text: string): MathSpan[] {
  const spans: MathSpan[] = [];
  for (let index = 0; index < text.length; index++) {
    if (text[index] === '\\') {
      index++;
      continue;
    }
    const span = mathAt(text, index);
    if (span !== null) {
      spans.push(span);
      index = span.to - 1;
    }
  }
  return spans;
}
