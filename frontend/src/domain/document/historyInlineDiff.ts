export type TextChange = { readonly kind: 'same' | 'added' | 'removed'; readonly text: string };

function append(changes: TextChange[], kind: TextChange['kind'], text: string): void {
  if (text === '') return;
  const last = changes[changes.length - 1];
  if (last?.kind === kind) changes[changes.length - 1] = { kind, text: last.text + text };
  else changes.push({ kind, text });
}

function matchingLengths(before: readonly string[], after: readonly string[]): Uint32Array[] {
  const lengths = Array.from({ length: before.length + 1 }, () => new Uint32Array(after.length + 1));
  for (let i = before.length - 1; i >= 0; i--) {
    for (let j = after.length - 1; j >= 0; j--) {
      const row = lengths[i];
      if (row === undefined) continue;
      row[j] =
        before[i] === after[j]
          ? 1 + (lengths[i + 1]?.[j + 1] ?? 0)
          : Math.max(lengths[i + 1]?.[j] ?? 0, lengths[i]?.[j + 1] ?? 0);
    }
  }
  return lengths;
}

function middleChanges(before: string[], after: string[]): TextChange[] {
  if (before.length === 0) return [{ kind: 'added', text: after.join('') }];
  if (after.length === 0) return [{ kind: 'removed', text: before.join('') }];
  // Bound quadratic work for large replacements; unchanged edges are still retained.
  if (before.length * after.length > 250_000)
    return [
      { kind: 'removed', text: before.join('') },
      { kind: 'added', text: after.join('') },
    ];
  const lengths = matchingLengths(before, after);
  const changes: TextChange[] = [];
  let i = 0;
  let j = 0;
  while (i < before.length || j < after.length) {
    if (i < before.length && j < after.length && before[i] === after[j]) {
      append(changes, 'same', before[i++] ?? '');
      j++;
    } else if (
      i < before.length &&
      (j === after.length || (lengths[i + 1]?.[j] ?? 0) >= (lengths[i]?.[j + 1] ?? 0))
    ) {
      append(changes, 'removed', before[i++] ?? '');
    } else append(changes, 'added', after[j++] ?? '');
  }
  return changes;
}

/** Unicode code points keep Korean syllables and surrogate pairs intact. */
export function historyInlineDiff(before: string, after: string): TextChange[] {
  const left = Array.from(before);
  const right = Array.from(after);
  let start = 0;
  let end = 0;
  while (start < Math.min(left.length, right.length) && left[start] === right[start]) start++;
  while (
    end < Math.min(left.length, right.length) - start &&
    left[left.length - end - 1] === right[right.length - end - 1]
  )
    end++;
  const changes: TextChange[] = [];
  append(changes, 'same', left.slice(0, start).join(''));
  for (const change of middleChanges(
    left.slice(start, left.length - end),
    right.slice(start, right.length - end),
  ))
    append(changes, change.kind, change.text);
  append(changes, 'same', end === 0 ? '' : left.slice(-end).join(''));
  return changes;
}
