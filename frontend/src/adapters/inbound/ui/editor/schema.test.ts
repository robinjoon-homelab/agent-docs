// @vitest-environment jsdom
import { getSchema } from '@tiptap/core';
import { DOMParser, DOMSerializer, Fragment, Node as PMNode } from '@tiptap/pm/model';
import { expect, it } from 'vitest';
import { drawing, paragraph } from '../../../../test-support/fixtures';
import { blocksToDoc } from './convert';
import { clearDuplicateIds } from './plugins/blockIds';
import { schemaExtensions } from './schema';

const schema = getSchema(schemaExtensions);

it('a drawing copied as HTML and pasted into its own document keeps both assets and its alt text under a new id', () => {
  const blocks = [
    paragraph('para000001', '앞'),
    drawing('draw000001', 'svg-1', 'scene-1', '배포 흐름 "초안"'),
  ];
  const doc = PMNode.fromJSON(
    schema,
    blocksToDoc(blocks, () => 'unused0001'),
  );
  const clipboard = document.createElement('div');
  clipboard.appendChild(DOMSerializer.fromSchema(schema).serializeFragment(Fragment.from(doc.child(1))));

  const pasted = clearDuplicateIds(DOMParser.fromSchema(schema).parseSlice(clipboard), doc);

  expect(pasted.content.childCount).toBe(1);
  expect(pasted.content.firstChild?.type.name).toBe('drawing');
  expect(pasted.content.firstChild?.attrs).toEqual({
    id: null,
    svgAssetId: 'svg-1',
    sceneAssetId: 'scene-1',
    alt: '배포 흐름 "초안"',
  });
  expect(doc.child(1).attrs.id).toBe('draw000001');
});
