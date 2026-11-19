import { describe, expect, it } from 'vitest';
import { document, paragraph, stored, thread } from '../../../../test-support/fixtures';
import { MemoryStore } from '../shared/store';
import { MemoryThreadGateway } from './MemoryThreadGateway';

function fixture() {
  const store = new MemoryStore({ userId: 'u', login: 'octocat', name: 'Owner', avatarUrl: '', owner: true });
  store.documents.set(
    'doc-1',
    document({
      blocks: [stored(paragraph('intro', 'hello world')), stored(paragraph('other', 'unchanged'))],
    }),
  );
  store.threads.set('doc-1', [
    thread({ id: 't', anchor: { kind: 'text', blockId: 'intro', start: 6, end: 11, quote: 'world' } }),
  ]);
  return { store, gateway: new MemoryThreadGateway(store) };
}

describe('thread collaboration preview', () => {
  it('accepts one range and connects its document revision', async () => {
    const { store, gateway } = fixture();
    const proposed = await gateway.suggest('t', {
      anchor: store.findThread('t').anchor,
      before: 'world',
      after: 'friend',
    });
    const id = proposed.suggestions?.[0]?.commentId ?? '';
    const result = await gateway.decide(id, true);
    const updated = store.document('doc-1');
    expect(updated.blocks[0]).toMatchObject({ text: 'hello friend' });
    expect(updated.blocks[1]).toMatchObject({ text: 'unchanged' });
    expect(result.linkedRevisions).toContain(updated.revision);
    expect(result.suggestions?.[0]?.decision?.revision).toBe(updated.revision);
  });

  it('does not toggle reactions on repeated explicit adds', async () => {
    const { gateway } = fixture();
    await gateway.react('t', '👍', true);
    const twice = await gateway.react('t', '👍', true);
    expect(twice.reactions).toHaveLength(1);
    expect((await gateway.react('t', '👍', false)).reactions).toEqual([]);
  });

  it('keeps thread, comment, and reaction ownership across GitHub login rename by stable userId and allows edit-role decision', async () => {
    const { store, gateway } = fixture();
    store.spaces = [
      {
        id: 'sp-1',
        slug: 'team',
        name: 'Team',
        description: '',
        links: [],
        createdAt: '2026-10-01T09:00:00Z',
        archivedAt: null,
        lastActivityAt: null,
        documentCount: 1,
        permission: 'comment',
      },
    ];
    store.me = { userId: 'u-alice', login: 'alice-old', name: 'Alice', avatarUrl: '', owner: false };
    const created = await gateway.create('doc-1', { kind: 'document' }, '처음 댓글');
    await gateway.react(created.id, '🎉', true);

    store.me = { ...store.me, login: 'alice-new' };
    const listed = await gateway.list('doc-1');
    const renamedView = listed.find((item) => item.id === created.id);
    expect(renamedView?.own).toBe(true);
    expect(renamedView?.comments[0]?.own).toBe(true);
    expect(renamedView?.reactions?.[0]?.own).toBe(true);

    const commentId = created.comments[0]?.id ?? '';
    const edited = await gateway.editComment(commentId, '이름 바꾼 뒤 수정');
    expect(edited.own).toBe(true);
    const afterRemove = await gateway.react(created.id, '🎉', false);
    expect(afterRemove.reactions).toEqual([]);
    const resolved = await gateway.update(created.id, { status: 'resolved' });
    expect(resolved.status).toBe('resolved');

    const proposed = await gateway.suggest('t', {
      anchor: { kind: 'text', blockId: 'intro', start: 6, end: 11, quote: 'world' },
      before: 'world',
      after: 'earth',
    });
    const suggestionCommentId = proposed.suggestions?.[0]?.commentId ?? '';
    await expect(gateway.decide(suggestionCommentId, true)).rejects.toMatchObject({ status: 403 });

    store.spaces = store.spaces.map((space) => ({ ...space, permission: 'edit' }));
    const decided = await gateway.decide(suggestionCommentId, true);
    expect(decided.suggestions?.[0]?.decision?.accepted).toBe(true);

    store.me = { userId: 'u-other', login: 'alice-old', name: 'Other', avatarUrl: '', owner: false };
    const asOther = (await gateway.list('doc-1')).find((item) => item.id === created.id);
    expect(asOther?.own).toBe(false);
    expect(asOther?.comments[0]?.own).toBe(false);
    await expect(gateway.editComment(commentId, '타인 수정')).rejects.toMatchObject({ status: 403 });
  });
});
