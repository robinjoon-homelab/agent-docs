import { describe, expect, it } from 'vitest';
import type { InlineMarks, InlineRun } from './inline';
import {
  BREAK,
  normalizeInline,
  parseInline,
  plainText,
  savedSelection,
  serializeInline,
  serializeInlinePreserving,
  textRun,
} from './inline';

describe('parseInline', () => {
  it('parses marks, links, doc links and breaks into runs', () => {
    expect(parseInline('a **b** *c* ~~d~~ `e` [f](https://x.dev) [g](doc:abc)<br>h')).toEqual([
      textRun('a '),
      textRun('b', { bold: true }),
      textRun(' '),
      textRun('c', { italic: true }),
      textRun(' '),
      textRun('d', { strike: true }),
      textRun(' '),
      textRun('e', { code: true }),
      textRun(' '),
      textRun('f', {}, 'https://x.dev'),
      textRun(' '),
      textRun('g', {}, 'doc:abc'),
      BREAK,
      textRun('h'),
    ]);
  });

  it('keeps a star that is surrounded by spaces', () => {
    expect(parseInline('2 * 3 = 6')).toEqual([textRun('2 * 3 = 6')]);
  });

  it('treats a run as markup only when it opens before and closes after non-space text', () => {
    expect(parseInline('** a**')).toEqual([textRun('** a**')]);
    expect(parseInline('*a *')).toEqual([textRun('*a *')]);
  });

  it('nests bold inside italic', () => {
    expect(parseInline('*a **b** c*')).toEqual([
      textRun('a ', { italic: true }),
      textRun('b', { italic: true, bold: true }),
      textRun(' c', { italic: true }),
    ]);
  });

  it('parses a triple run as bold and italic', () => {
    expect(parseInline('***x***')).toEqual([textRun('x', { bold: true, italic: true })]);
  });

  it('resolves backslash escapes and keeps unknown escapes literal', () => {
    expect(parseInline('\\*not\\* \\[x\\] \\a')).toEqual([textRun('*not* [x] \\a')]);
  });

  it('keeps code span content literal and strips one padding space', () => {
    expect(parseInline('`` `a` ``')).toEqual([textRun('`a`', { code: true })]);
    expect(parseInline('`**x**`')).toEqual([textRun('**x**', { code: true })]);
  });

  it('applies outer marks to link text and keeps inner marks', () => {
    expect(parseInline('**[a *b*](u)**')).toEqual([
      textRun('a ', { bold: true }, 'u'),
      textRun('b', { bold: true, italic: true }, 'u'),
    ]);
  });

  it('treats brackets without a destination as text', () => {
    expect(parseInline('[a] (b)')).toEqual([textRun('[a] (b)')]);
  });

  it('accepts pointy destinations and balanced parentheses', () => {
    expect(parseInline('[a](<x y>) [b](f(1))')).toEqual([
      textRun('a', {}, 'x y'),
      textRun(' '),
      textRun('b', {}, 'f(1)'),
    ]);
  });

  it('accepts br variants', () => {
    expect(parseInline('a<br/>b<BR />c')).toEqual([textRun('a'), BREAK, textRun('b'), BREAK, textRun('c')]);
  });
});

describe('plainText', () => {
  it('returns what the reader sees', () => {
    expect(plainText('**Hello** [world](https://x) `x` a<br>b \\* 2 * 3')).toBe('Hello world x a\nb * 2 * 3');
  });

  it('counts unmatched delimiters as text', () => {
    expect(plainText('**open only')).toBe('**open only');
  });
});

// The server computes the same plain text (InlineMarkdownTest lists these cases), because text anchors are offsets
// into it. Change both sides together.
describe('plainText parity with the server', () => {
  it.each([
    ['**bold** and *italic* and ~~gone~~', 'bold and italic and gone'],
    ['run `./gradlew build` first', 'run ./gradlew build first'],
    ['see [the ADR](https://example.com/adr)', 'see the ADR'],
    ['![diagram](asset:1234) caption', '!diagram caption'],
    ['2 * 3 and a ** b', '2 * 3 and a ** b'],
    ['*unclosed emphasis', '*unclosed emphasis'],
    ['literal \\*star\\* and \\[bracket]', 'literal *star* and [bracket]'],
    ['[not a link] and `unclosed', '[not a link] and `unclosed'],
    ['[**bold link**](x)', 'bold link'],
    ['*foo**bar*', 'foo**bar'],
    ['**foo*bar**', 'foo*bar'],
    ['*foo**bar**baz*', 'foobarbaz'],
    ['***a**b*', 'ab'],
    ['a**b**c***d***', 'abcd'],
    ['foo*bar*', 'foobar'],
    ['* not emphasis *', '* not emphasis *'],
    ['**a *b* c**', 'a b c'],
    ['a\\qb and \\\\ and \\', 'a\\qb and \\ and \\'],
    ['`` a ` b `` and ` `', 'a ` b and  '],
    ['x<BR>y<br  />z<br\t/>w<bra>', 'x\ny\nz\nw<bra>'],
    ['~single~ and ~~~three~~~ and ~~two~~', '~single~ and ~~~three~~~ and two'],
    ['_under_ stays', '_under_ stays'],
    ['[outer [inner](a) text](b)', 'outer [inner](a) text'],
    ['[a](<dest with space>) and [b](c(d)e)', 'a and b'],
    ['[a](b c) and [x](y', '[a](b c) and [x](y'],
    ['[link `code]` x](y)', 'link code] x'],
    ['line\nbreak', 'line\nbreak'],
    ['`code with *star*` and *em*', 'code with *star* and em'],
    ['***', '***'],
    ['**a**b**', 'ab**'],
    ['\u00a0*nbsp*\u00a0', '\u00a0nbsp\u00a0'],
  ])('%j', (markdown, expected) => {
    expect(plainText(markdown)).toBe(expected);
  });
});

describe('serializeInline', () => {
  const platformTexts = [
    'plain text with @octocat mention',
    '**bold** and *italic* and ~~strike~~',
    'code `x = 1` here',
    '[link](https://example.com/a?b=c) and [문서](doc:a1b2c3d4e5)',
    'line one<br>line two',
    '2 * 3 = 6',
    '*a **b** c*',
    '***both***',
    '**[bold link](https://x.dev)**',
    'escaped \\*star\\* and \\[bracket\\](x)',
    'snake_case stays',
    '`` a`b ``',
    '한국어 **굵게** 문장',
  ];

  it.each(platformTexts)('round-trips platform text exactly: %s', (text) => {
    expect(serializeInline(parseInline(text))).toBe(text);
  });

  it('trims surrounding whitespace and breaks', () => {
    expect(serializeInline([BREAK, textRun('  hi  '), BREAK])).toBe('hi');
  });

  it('moves whitespace out of emphasis so it still parses', () => {
    expect(serializeInline([textRun('a'), textRun(' b ', { bold: true }), textRun('c')])).toBe('a **b** c');
  });

  it('escapes literal markup only when it would otherwise parse', () => {
    expect(serializeInline([textRun('a*b c*d')])).toBe('a\\*b c\\*d');
    expect(serializeInline([textRun('see [x](y)')])).toBe('see \\[x\\](y)');
    expect(serializeInline([textRun('literal <br> tag')])).toBe('literal \\<br> tag');
  });

  it('closes marks across a link boundary', () => {
    const inline = [textRun('a', { bold: true }), textRun('b', { bold: true }, 'u'), textRun('c')];
    const markdown = serializeInline(inline);
    expect(markdown).toBe('**a[b](u)**c');
    expect(parseInline(markdown)).toEqual(normalizeInline(inline));
  });

  it('keeps a trailing star inside bold by escaping it', () => {
    const inline = [textRun('a*', { bold: true })];
    expect(parseInline(serializeInline(inline))).toEqual(inline);
  });

  it('uses a pointy destination when the URL has spaces', () => {
    expect(serializeInline([textRun('a', {}, 'my file.md')])).toBe('[a](<my file.md>)');
  });
});

describe('savedSelection', () => {
  it('shifts editor offsets onto the trimmed text that is stored', () => {
    expect(savedSelection([BREAK, textRun('  hello world ')], 3, 8)).toEqual({
      text: 'hello world',
      start: 0,
      end: 5,
    });
    expect(savedSelection([textRun('  hi ')], 0, 5)).toEqual({ text: 'hi', start: 0, end: 2 });
  });
});

describe('serializeInlinePreserving', () => {
  it('returns the stored markdown when the visible content is unchanged', () => {
    const stored = 'a\\*b and __under__';
    expect(serializeInlinePreserving(parseInline(stored), stored)).toBe(stored);
  });

  it('serializes again when the content changed', () => {
    expect(serializeInlinePreserving([textRun('new', { bold: true })], 'old')).toBe('**new**');
  });
});

describe('serializeInline on generated content', () => {
  // Deterministic PRNG (mulberry32) so failures are reproducible.
  function random(seed: number): () => number {
    let state = seed >>> 0;
    return () => {
      state = (state + 0x6d2b79f5) >>> 0;
      let t = state;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  const words = [
    'word',
    'a*b',
    '2',
    '*',
    'x_y',
    '[x]',
    '(y)',
    '`tick`',
    '~',
    '~~',
    'c:\\dir',
    '<br>',
    '가나',
    '@login',
    'f(1)',
    '**',
  ];

  function generate(next: () => number): InlineRun[] {
    const pick = (): string => words[Math.floor(next() * words.length)] ?? '';
    const runs: InlineRun[] = [];
    const count = 1 + Math.floor(next() * 6);
    for (let index = 0; index < count; index++) {
      if (index > 0) runs.push(next() < 0.1 ? BREAK : textRun(' '));
      const text = next() < 0.3 ? `${pick()} ${pick()}` : pick();
      const marks: Partial<InlineMarks> = {
        bold: next() < 0.3,
        italic: next() < 0.3,
        strike: next() < 0.15,
        code: next() < 0.1,
      };
      runs.push(textRun(text, marks, next() < 0.15 ? 'https://x.dev/p' : null));
    }
    return runs;
  }

  it('parses back to the same content for word-level formatting', () => {
    const next = random(7);
    const mismatches: string[] = [];
    for (let index = 0; index < 5000; index++) {
      const runs = generate(next);
      const markdown = serializeInline(runs);
      if (JSON.stringify(parseInline(markdown)) !== JSON.stringify(normalizeInline(runs)))
        mismatches.push(markdown);
    }
    expect(mismatches).toEqual([]);
  });
});
