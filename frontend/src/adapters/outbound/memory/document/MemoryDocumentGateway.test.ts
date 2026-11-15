import { describe, expect, it } from 'vitest';
import { document, paragraph } from '../../../../test-support/fixtures';
import { MemoryStore } from '../shared/store';
import { MemoryDocumentGateway } from './MemoryDocumentGateway';

function setup() {
  const store = new MemoryStore({ userId: 'u1', login: 'owner', name: 'Owner', avatarUrl: '', owner: true });
  const initial = document({ version: 10, revision: 3 });
  store.documents.set(initial.id, initial);
  return { gateway: new MemoryDocumentGateway(store), initial, store };
}

describe('memory document versions', () => {
  it('increments versions on edits and appends immutable revisions only on finalize', async () => {
    const { gateway, initial } = setup();
    const first = await gateway.saveContent(initial.id, 10, [paragraph('aaaaaaaaaa', 'first')]);
    expect(first).toMatchObject({ kind: 'saved', version: 11, revision: 3 });
    expect(await gateway.revisions(initial.id)).toEqual([]);
    expect(await gateway.finalize(initial.id, 11)).toMatchObject({ version: 11, revision: 4 });
    const snapshot = await gateway.revision(initial.id, 4);
    await gateway.saveContent(initial.id, 11, [paragraph('aaaaaaaaaa', 'second')]);
    expect(await gateway.finalize(initial.id, 12)).toMatchObject({ version: 12, revision: 5 });
    expect(await gateway.revision(initial.id, 4)).toEqual(snapshot);
    expect(await gateway.finalize(initial.id, 12)).toMatchObject({ version: 12, revision: 5 });
  });

  it('rejects stale versions without merging and keeps no-op writes unchanged', async () => {
    const { gateway, initial } = setup();
    expect(await gateway.saveContent(initial.id, 10, initial.blocks)).toMatchObject({
      version: 10,
      revision: 3,
    });
    await gateway.patch(initial.id, 10, { title: 'Changed' });
    expect(await gateway.saveContent(initial.id, 10, [])).toMatchObject({
      kind: 'conflict',
      currentVersion: 11,
      currentDocument: { title: 'Changed' },
    });
    expect((await gateway.get(initial.id)).blocks).toEqual(initial.blocks);
  });
});

it('transfers the document and descendants under the same IDs without rewriting immutable blocks', async () => {
  const { gateway, store, initial } = setup();
  const space = {
    description: '',
    links: [],
    createdAt: initial.createdAt,
    archivedAt: null,
    lastActivityAt: null,
    documentCount: 0,
  };
  store.spaces = [
    { ...space, ...initial.space },
    { ...space, id: 'sp2', slug: 'destination', name: 'Destination' },
  ];
  const child = document({
    id: 'child',
    path: `${initial.path}/child`,
    blocks: [
      { id: 'imageblock', type: 'image', assetId: 'original', alt: '', caption: '', hash: 'unchanged' },
    ],
  });
  store.documents.set(child.id, child);
  const result = await gateway.transfer(initial.id, initial.version, 'destination', 'moved');
  expect(result).toMatchObject({ documentId: initial.id, path: 'moved', space: { slug: 'destination' } });
  expect(await gateway.get(child.id)).toMatchObject({
    id: child.id,
    path: 'moved/child',
    blocks: child.blocks,
    space: { slug: 'destination' },
  });
  expect((await gateway.revision(child.id, child.revision + 1)).blocks).toEqual(child.blocks);
});

it('refuses every transfer target when a descendant path is already occupied', async () => {
  const { gateway, store, initial } = setup();
  const space = {
    description: '',
    links: [],
    createdAt: initial.createdAt,
    archivedAt: null,
    lastActivityAt: null,
    documentCount: 0,
  };
  store.spaces = [
    { ...space, ...initial.space },
    { ...space, id: 'sp2', slug: 'destination', name: 'Destination' },
  ];
  store.documents.set('child', document({ id: 'child', path: `${initial.path}/child` }));
  store.documents.set(
    'occupied',
    document({ id: 'occupied', space: store.spaces[1] ?? initial.space, path: 'moved/child' }),
  );
  await expect(gateway.transfer(initial.id, initial.version, 'destination', 'moved')).rejects.toThrow();
  expect(await gateway.get(initial.id)).toEqual(initial);
  expect((await gateway.get('child')).path).toBe(`${initial.path}/child`);
});
