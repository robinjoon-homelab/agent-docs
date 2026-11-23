import { describe, expect, it } from 'vitest';
import { parseInline, plainText, serializeInlinePreserving, serializeInline } from './inline';
import { githubReferences } from './githubReferences';
import { mathSpans } from './math';
import { assetReferences } from './assetReferences';

describe('content expressions', () => {
  it('keeps formula markup and TeX commands through the inline model', () => {
    const source = String.raw`값 $\frac{a*b}{c}$ 및 $$x^2$$`;
    const inline = parseInline(source);
    expect(serializeInlinePreserving(inline, source)).toBe(source);
    expect(serializeInline(inline)).toBe(source);
    expect(plainText(source)).toBe(source);
    expect(mathSpans(source).map((span) => span.source)).toEqual([String.raw`\frac{a*b}{c}`, 'x^2']);
  });
  it('does not invent a repository for absent or ambiguous connections', () => {
    expect(githubReferences('#123', { repositories: [] })).toEqual([]);
    expect(githubReferences('#123', { repositories: ['a/one', 'a/two'] })).toEqual([]);
    expect(
      githubReferences('#123', { repositories: ['a/one', 'a/two'], defaultRepository: 'else/other' }),
    ).toEqual([]);
  });
  it('uses selected repository and HEAD without altering visible text', () => {
    const text = '#123 abcdef123 src/main.kt#L4-L6';
    const spans = githubReferences(text, { repositories: ['a/one', 'a/two'], defaultRepository: 'a/two' });
    expect(spans.map((span) => span.href)).toEqual([
      'https://github.com/a/two/issues/123',
      'https://github.com/a/two/commit/abcdef123',
      'https://github.com/a/two/blob/HEAD/src/main.kt#L4-L6',
    ]);
    expect(spans.map((span) => text.slice(span.from, span.to))).toEqual([
      '#123',
      'abcdef123',
      'src/main.kt#L4-L6',
    ]);
  });
  it('recognizes repo root files such as README.md while keeping repo rules and normal prose safe', () => {
    const context = { repositories: ['a/one'] };
    const text = 'See README.md, README.md#L10-L20, build.gradle.kts, .gitignore, and Dockerfile.';
    const spans = githubReferences(text, context);
    expect(spans.map((span) => text.slice(span.from, span.to))).toEqual([
      'README.md',
      'README.md#L10-L20',
      'build.gradle.kts',
      '.gitignore',
      'Dockerfile',
    ]);
    expect(spans.map((span) => span.href)).toEqual([
      'https://github.com/a/one/blob/HEAD/README.md',
      'https://github.com/a/one/blob/HEAD/README.md#L10-L20',
      'https://github.com/a/one/blob/HEAD/build.gradle.kts',
      'https://github.com/a/one/blob/HEAD/.gitignore',
      'https://github.com/a/one/blob/HEAD/Dockerfile',
    ]);
    expect(githubReferences('README.md', { repositories: [] })).toEqual([]);
    expect(githubReferences('README.md', { repositories: ['a/one', 'a/two'] })).toEqual([]);
    expect(
      githubReferences(
        'Check e.g. v1.2.3, 0.18.1, example.com, under the license, or https://example.com/README.md',
        context,
      ),
    ).toEqual([]);
  });
  it('extracts reachable assets without treating code samples as attachments', () => {
    const id = '88888888-8888-4888-8888-888888888888';
    const other = '99999999-9999-4999-8999-999999999999';
    expect(
      assetReferences([
        { id: 'image00001', type: 'image', assetId: id, alt: '시안', caption: 'mockup' },
        { id: 'para000001', type: 'paragraph', text: `[첨부](asset:${id}) \`[코드](asset:${other})\`` },
        { id: 'code000001', type: 'code', language: '', code: `[코드](asset:${other})` },
      ]),
    ).toEqual([id]);
  });
  it('keeps nested component asset values and state changes without collecting UUIDs in text', () => {
    const image = '11111111-1111-4111-8111-111111111111';
    const stateImage = '22222222-2222-4222-8222-222222222222';
    const label = '33333333-3333-4333-8333-333333333333';
    const guide = '44444444-4444-4444-8444-444444444444';
    const ids = assetReferences([
      {
        id: 'screen01',
        type: 'flowDesign',
        design: {
          kind: 'screen',
          id: 'home',
          description: `[안내](asset:${guide})`,
          root: {
            kind: 'instance',
            id: 'grid',
            component: 'gallery',
            inputs: { rows: [{ itemKey: 'a', fields: { file: image, asset: label } }] },
          },
        },
      },
      {
        id: 'state001',
        type: 'flowDesign',
        design: {
          kind: 'state',
          id: 'active',
          screen: 'home',
          changes: [
            {
              op: 'itemSet',
              target: { owner: 'home', node: 'grid' },
              prop: 'rows',
              itemKey: 'a',
              field: 'file',
              value: stateImage,
            },
          ],
        },
      },
      {
        id: 'gallery1',
        type: 'flowDesign',
        design: {
          kind: 'component',
          id: 'gallery',
          props: [
            {
              id: 'rows',
              type: 'list',
              fields: [
                { id: 'file', type: 'asset' },
                { id: 'asset', type: 'text' },
              ],
            },
          ],
        },
      },
    ]);
    expect(new Set(ids)).toEqual(new Set([image, stateImage, guide]));
  });
});
