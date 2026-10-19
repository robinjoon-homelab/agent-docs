import { describe, expect, it } from 'vitest';
import type { Comment } from '../../domain/thread/thread';
import { human, summary, thread } from '../../test-support/fixtures';
import type { ThreadGateway } from './ports/ThreadGateway';
import { createThreadUseCases } from './threadUseCases';

const reply: Comment = {
  id: 'cm-2',
  author: human(),
  body: 'done',
  createdAt: '2026-10-18T11:00:00Z',
  editedAt: null,
  deleted: false,
};

function gateway(overrides: Partial<ThreadGateway>): ThreadGateway {
  return overrides as ThreadGateway;
}

describe('thread use cases', () => {
  it('rejects blank comments', async () => {
    const useCases = createThreadUseCases(gateway({}));
    expect(await useCases.start('doc-1', { kind: 'document' }, '  ')).toEqual({
      ok: false,
      error: 'body_required',
    });
    expect(await useCases.reply(thread(), '')).toEqual({ ok: false, error: 'body_required' });
  });

  it('appends a reply to the thread', async () => {
    const useCases = createThreadUseCases(gateway({ reply: () => Promise.resolve(reply) }));
    const result = await useCases.reply(thread(), ' done ');
    expect(result.ok && result.value.comments).toEqual([reply]);
    expect(result.ok && result.value.updatedAt).toBe(reply.createdAt);
  });

  it("reopens an answered or resolved thread on a person's reply, like the server", async () => {
    const useCases = createThreadUseCases(gateway({ reply: () => Promise.resolve(reply) }));
    const answered = await useCases.reply(thread({ status: 'answered' }), 'not yet');
    const resolved = await useCases.reply(thread({ status: 'resolved' }), 'again');
    expect([answered.ok && answered.value.status, resolved.ok && resolved.value.status]).toEqual([
      'open',
      'open',
    ]);
  });

  it('shows a deleted comment as deleted', async () => {
    const useCases = createThreadUseCases(gateway({ deleteComment: () => Promise.resolve() }));
    const updated = await useCases.deleteComment(thread({ comments: [reply] }), 'cm-2');
    expect(updated.comments[0]?.deleted).toBe(true);
  });

  it('summarizes unresolved threads per document and skips documents without any', async () => {
    const threadsByDoc: Record<string, ReturnType<typeof thread>[]> = {
      d1: [thread({ id: 't1', status: 'open' }), thread({ id: 't2', status: 'resolved' })],
      d2: [thread({ id: 't3', status: 'resolved' })],
      d3: [thread({ id: 't4', status: 'answered' })],
    };
    const useCases = createThreadUseCases(gateway({ list: (id) => Promise.resolve(threadsByDoc[id] ?? []) }));
    const result = await useCases.openAcross([
      summary({ id: 'd1' }),
      summary({ id: 'd2' }),
      summary({ id: 'd3' }),
    ]);
    expect(result.map((entry) => [entry.document.id, entry.threads.map((t) => t.id)])).toEqual([
      ['d1', ['t1']],
      ['d3', ['t4']],
    ]);
  });
});
