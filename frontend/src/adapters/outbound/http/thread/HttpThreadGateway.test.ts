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
      {
        ...base,
        id: 't3',
        anchor: { kind: 'block', blockId: 'img', quote: '배포 순서' },
        resolution: { state: 'detached' },
      },
      { ...base, id: 't4', anchor: { kind: 'document' }, resolution: { state: 'attached' } },
    ]).list('d1');
    expect(threads.map((thread) => thread.anchor.kind)).toEqual(['text', 'cell', 'block', 'document']);
    expect(threads[2]?.anchor).toEqual({ kind: 'block', blockId: 'img', quote: '배포 순서' });
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

it('keeps claim timestamps, each actor reaction and the suggestion decision revision', async () => {
  const claim = {
    actor: { kind: 'agent', login: 'octocat', agent: { connectionId: 'a', clientName: 'Codex' } },
    acquiredAt: '2026-11-16T01:00:00Z',
    expiresAt: '2026-11-16T01:15:00Z',
  };
  const anchor = { kind: 'text', blockId: 'b', start: 0, end: 3, quote: 'old' };
  const decision = { actor: human, at: '2026-11-16T01:01:00Z', accepted: true, revision: 7 };
  const result = await gatewayReturning({
    id: 't',
    documentId: 'd',
    status: 'open',
    author: human,
    comments: [],
    createdAt: 'x',
    updatedAt: 'x',
    anchor,
    claim,
    reactions: [{ actor: human, emoji: '👍' }],
    suggestions: [{ commentId: 'c', anchor, hash: 'abcd1234', before: 'old', after: 'new', decision }],
  }).react('t', '👍', true);
  expect(result.claim).toEqual(claim);
  expect(result.reactions).toEqual([{ actor: human, emoji: '👍' }]);
  expect(result.suggestions?.[0]?.decision).toEqual(decision);
});
