import { expect, it } from 'vitest';
import { seededStore } from '../shared/seed';
import { document } from '../../../../test-support/fixtures';
import { MemoryDocumentGateway } from './MemoryDocumentGateway';
import { MemorySpaceGateway } from '../space/MemorySpaceGateway';
it('returns separate edit and comment grants and hides ungranted spaces in the preview', async () => {
  const store = seededStore();
  const first = store.spaces[0];
  const second = store.spaces[1];
  if (!first || !second) throw new Error('seed requires two spaces');
  store.spaces = store.spaces.map((space) =>
    space.id === first.id
      ? { ...space, permission: 'edit' }
      : space.id === second.id
        ? { ...space, permission: 'comment' }
        : { ...space, permission: undefined },
  );
  store.me = { ...store.me, owner: false };
  const edit = document({ id: 'edit-doc', space: { id: first.id, slug: first.slug, name: first.name } });
  const comment = document({
    id: 'comment-doc',
    space: { id: second.id, slug: second.slug, name: second.name },
  });
  store.documents.set(edit.id, edit);
  store.documents.set(comment.id, comment);
  const docs = new MemoryDocumentGateway(store);
  expect((await docs.get(edit.id)).permission).toBe('edit');
  expect((await docs.get(comment.id)).permission).toBe('comment');
  await expect(docs.patch(edit.id, edit.version, { title: 'Edited' })).resolves.toMatchObject({
    title: 'Edited',
  });
  await expect(docs.patch(comment.id, comment.version, { title: 'Forbidden' })).rejects.toMatchObject({
    code: 'forbidden',
  });
  expect(store.documents.get(comment.id)?.title).toBe(comment.title);
  expect((await new MemorySpaceGateway(store).list()).map((space) => space.permission)).toEqual([
    'edit',
    'comment',
  ]);
  await expect(
    new MemorySpaceGateway(store).create({ slug: 'new', name: 'New', description: '', links: [] }),
  ).rejects.toMatchObject({ code: 'forbidden' });
  const hidden = document({ id: 'hidden-doc', space: { id: 'hidden', slug: 'hidden', name: 'Hidden' } });
  store.documents.set(hidden.id, hidden);
  await expect(docs.get(hidden.id)).rejects.toMatchObject({ code: 'not_found' });
  await expect(docs.revisions(hidden.id)).rejects.toMatchObject({ code: 'not_found' });
});
