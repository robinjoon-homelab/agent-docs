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
});
