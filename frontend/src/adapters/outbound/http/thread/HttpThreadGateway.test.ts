import { describe, expect, it } from 'vitest';
import { HttpClient } from '../shared/httpClient';
import { HttpThreadGateway } from './HttpThreadGateway';

const human = { kind: 'human', login: 'octocat', agent: null };

function gatewayReturning(body: unknown): HttpThreadGateway {
  const fetch = (): Promise<Response> =>
    Promise.resolve(
      new Response(JSON.stringify(body), { status: 200, headers: { 'content-type': 'application/json' } }),
    );
  return new HttpThreadGateway(new HttpClient({ fetch, cookies: () => '' }));
}

describe('HttpThreadGateway', () => {
  it('maps anchors of every kind and the resolved range', async () => {
    const base = {
      documentId: 'd1',
      status: 'open',
      author: human,
      comments: [],
      createdAt: 'x',
      updatedAt: 'x',
    };
    const threads = await gatewayReturning([
      {
        ...base,
        id: 't1',
        anchor: { kind: 'text', blockId: 'b', start: 1, end: 3, quote: 'el' },
        resolution: { state: 'attached', start: 4, end: 6 },
        linkedRevisions: [5],
      },
      {
        ...base,
        id: 't2',
        anchor: { kind: 'cell', blockId: 'b', rowId: 'r', columnId: 'c', quote: 'q' },
        resolution: { state: 'detached' },
      },
      { ...base, id: 't3', anchor: { kind: 'document' }, resolution: { state: 'attached' } },
    ]).list('d1');
    expect(threads.map((thread) => thread.anchor.kind)).toEqual(['text', 'cell', 'document']);
    expect(threads[0]?.resolution).toEqual({ state: 'attached', start: 4, end: 6 });
    expect(threads[0]?.linkedRevisions).toEqual([5]);
    expect(threads[1]?.resolution).toEqual({ state: 'detached' });
  });

  it('maps a deleted comment', async () => {
    const comment = await gatewayReturning({
      id: 'c1',
      author: human,
      body: '',
      createdAt: 'x',
      editedAt: null,
      deleted: true,
    }).reply('t1', 'x');
    expect(comment).toEqual({
      id: 'c1',
      author: { kind: 'human', login: 'octocat', agent: null },
      body: '',
      createdAt: 'x',
      editedAt: null,
      deleted: true,
    });
  });
});
