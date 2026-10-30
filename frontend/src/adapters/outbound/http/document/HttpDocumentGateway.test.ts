import { describe, expect, it } from 'vitest';
import { PathTakenError } from '../../../../application/shared/ports/errors';
import { HttpClient } from '../shared/httpClient';
import { HttpDocumentGateway } from './HttpDocumentGateway';

const actor = { kind: 'agent', login: 'octocat', agent: { connectionId: 'c1', clientName: 'Claude Code' } };

function gatewayReturning(status: number, body: unknown): HttpDocumentGateway {
  const fetch = (): Promise<Response> =>
    Promise.resolve(
      new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } }),
    );
  return new HttpDocumentGateway(new HttpClient({ fetch, cookies: () => '' }));
}

describe('HttpDocumentGateway', () => {
  it('turns a path in use into an error naming that path, which a document moved along may hold', async () => {
    const gateway = gatewayReturning(409, {
      code: 'taken',
      detail: 'another document in the space already has the path billing/retry',
      path: 'billing/retry',
    });

    const refused = await gateway.patch('d1', 3, { path: 'billing' }).catch((error: unknown) => error);

    expect(refused).toBeInstanceOf(PathTakenError);
    expect((refused as PathTakenError).path).toBe('billing/retry');
    expect((refused as PathTakenError).message).toContain('billing/retry');
  });

  it('reads a revision written by the application itself', async () => {
    const [revision] = await gatewayReturning(200, [
      {
        revision: 1,
        actor: { kind: 'human', login: 'octocat', agent: null },
        source: 'system',
        summary: '폴더를 문서로 바꿈',
        changedBlockIds: [],
        createdAt: '2026-10-30T00:00:00Z',
        threadIds: [],
      },
    ]).revisions('d1');

    expect(revision?.source).toBe('system');
  });

  it('maps a document with every block type and the block change list', async () => {
    const document = await gatewayReturning(200, {
      id: 'd1',
      space: { id: 's1', slug: 'team', name: 'Team' },
      path: 'guides/setup',
      title: 'Setup',
      status: 'in_review',
      tags: ['ops'],
      assignee: null,
      revision: 7,
      createdAt: '2026-10-01T00:00:00Z',
      updatedAt: '2026-10-18T00:00:00Z',
      trashedAt: null,
      blocks: [
        { id: 'h000000000', type: 'heading', level: 2, text: 'Title', hash: 'aaaaaaaa' },
        {
          id: 'l000000000',
          type: 'listItem',
          style: 'task',
          depth: 1,
          checked: true,
          text: 'todo',
          hash: 'b',
        },
        { id: 'c000000000', type: 'code', language: 'mermaid', code: 'graph TD', hash: 'c' },
        {
          id: 't000000000',
          type: 'table',
          columns: [{ id: 'k1', align: 'center' }],
          rows: [{ id: 'r1', cells: ['x'] }],
          hash: 'd',
        },
        { id: 'i000000000', type: 'image', assetId: 'as1', alt: '', caption: 'cap', hash: 'e' },
        { id: 'v000000000', type: 'divider', hash: 'f' },
      ],
      blockChanges: [{ blockId: 'h000000000', actor, revision: 7, at: '2026-10-18T00:00:00Z' }],
    }).get('d1');

    expect(document.status).toBe('in_review');
    expect(document.blocks.map((block) => block.type)).toEqual([
      'heading',
      'listItem',
      'code',
      'table',
      'image',
      'divider',
    ]);
    expect(document.blocks[0]).toEqual({
      id: 'h000000000',
      type: 'heading',
      level: 2,
      text: 'Title',
      hash: 'aaaaaaaa',
    });
    expect(document.blocks[3]).toMatchObject({
      columns: [{ id: 'k1', align: 'center' }],
      rows: [{ id: 'r1', cells: ['x'] }],
    });
    expect(document.blockChanges[0]?.actor.agent?.clientName).toBe('Claude Code');
  });

  it('rejects block types this client does not know instead of guessing', async () => {
    const gateway = gatewayReturning(200, {
      id: 'd1',
      space: { id: 's1', slug: 'team', name: 'Team' },
      path: 'p',
      title: 't',
      status: 'draft',
      revision: 1,
      createdAt: '2026-10-01T00:00:00Z',
      updatedAt: '2026-10-01T00:00:00Z',
      blocks: [{ id: 'x000000000', type: 'html', html: '<b>' }],
    });
    await expect(gateway.get('d1')).rejects.toMatchObject({ code: 'unknown' });
  });

  it("returns the stored blocks and whether the server merged other writers' changes", async () => {
    const result = await gatewayReturning(200, {
      revision: 8,
      amended: false,
      merged: true,
      blocks: [
        { id: 'a000000000', type: 'paragraph', text: 'mine', hash: '12345678' },
        { id: 'b000000000', type: 'paragraph', text: 'agent edit', hash: '9abcdef0' },
      ],
    }).saveContent('d1', 7, []);
    expect(result).toEqual({
      kind: 'saved',
      revision: 8,
      amended: false,
      merged: true,
      blocks: [
        { id: 'a000000000', type: 'paragraph', text: 'mine', hash: '12345678' },
        { id: 'b000000000', type: 'paragraph', text: 'agent edit', hash: '9abcdef0' },
      ],
    });
  });

  it('turns a 409 with conflicts into a conflict result', async () => {
    const result = await gatewayReturning(409, {
      code: 'conflict',
      status: 409,
      title: 'Conflict',
      conflicts: [
        {
          blockId: 'a',
          currentHash: 'ffffffff',
          current: { id: 'a', type: 'paragraph', text: 'theirs' },
          currentMarkdown: 'theirs',
        },
        { blockId: 'b', currentHash: null, current: null, currentMarkdown: null },
      ],
    }).saveContent('d1', 7, []);
    expect(result).toEqual({
      kind: 'conflict',
      conflicts: [
        {
          blockId: 'a',
          currentHash: 'ffffffff',
          current: { id: 'a', type: 'paragraph', text: 'theirs' },
          currentMarkdown: 'theirs',
        },
        { blockId: 'b', currentHash: null, current: null, currentMarkdown: null },
      ],
    });
  });

  it('maps a diff with changed cells', async () => {
    const diff = await gatewayReturning(200, {
      from: 3,
      to: 5,
      changes: [
        {
          blockId: 't',
          change: 'modified',
          before: null,
          after: { id: 't', type: 'divider' },
          changedCells: [{ rowId: 'r1', columnId: 'k1' }],
        },
      ],
      unified: '--- rev 3\n+++ rev 5\n',
    }).diff('d1', 3);
    expect(diff.changes[0]).toEqual({
      blockId: 't',
      change: 'modified',
      before: null,
      after: { id: 't', type: 'divider' },
      changedCells: [{ rowId: 'r1', columnId: 'k1' }],
    });
    expect(diff.unified).toContain('+++ rev 5');
  });
});
