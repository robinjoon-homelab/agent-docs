import { describe, expect, it } from 'vitest';
import type { Block } from '../../domain/document/block';
import { blocksEqual, withoutHash } from '../../domain/document/block';
import type { AgentDocument, SaveConflict } from '../../domain/document/document';
import { mergeAfterConflict } from '../../domain/document/merge';
import { document, paragraph, stored } from '../../test-support/fixtures';
import type { Clock } from '../shared/ports/Clock';
import { ApiError } from '../shared/ports/errors';
import type { DocumentGateway, MetaPatch, SaveContentResult } from './ports/DocumentGateway';
import type { ServerMerge } from './editorSession';
import { DocumentEditorSession } from './editorSession';

const clock: Clock = { now: () => new Date('2026-10-18T10:00:00Z') };

function changed(block: Block | undefined, other: Block | undefined): boolean {
  return block === undefined || other === undefined || !blocksEqual(block, other);
}

/**
 * In-memory document server that behaves like the backend: a save based on an older revision
 * conflicts on blocks both sides changed and otherwise merges, keeping other writers' blocks.
 * `failNext` makes the next save fail like a dropped connection.
 */
class FakeDocumentServer implements Partial<DocumentGateway> {
  doc: AgentDocument;
  failNext = false;
  private history = new Map<number, readonly Block[]>();
  private titles = new Map<number, string>();

  constructor(doc: AgentDocument) {
    this.doc = doc;
    this.remember();
  }

  /** Simulates an agent edit that lands on the server first. */
  agentWrites(change: Omit<Partial<AgentDocument>, 'blocks'> & { blocks?: Block[] }): void {
    const blocks = change.blocks ?? this.doc.blocks.map(withoutHash);
    this.doc = {
      ...this.doc,
      ...change,
      revision: this.doc.revision + 1,
      blocks: blocks.map((block) => stored(block)),
    };
    this.remember();
  }

  get = (): Promise<AgentDocument> => Promise.resolve(this.doc);

  saveContent = (_id: string, baseRevision: number, blocks: readonly Block[]): Promise<SaveContentResult> => {
    if (this.failNext) {
      this.failNext = false;
      return Promise.reject(new ApiError('network', 0, 'offline'));
    }
    const current = this.doc.blocks.map(withoutHash);
    const merged = baseRevision !== this.doc.revision;
    if (merged) {
      const base = this.history.get(baseRevision) ?? [];
      const byId = (list: readonly Block[], id: string) => list.find((block) => block.id === id);
      const conflicts: SaveConflict[] = current
        .filter(
          (block) =>
            changed(block, byId(base, block.id)) && changed(byId(blocks, block.id), byId(base, block.id)),
        )
        .filter((block) => changed(byId(blocks, block.id), block))
        .map((block) => ({
          blockId: block.id,
          currentHash: 'ffffffff',
          current: block,
          currentMarkdown: null,
        }));
      if (conflicts.length > 0) return Promise.resolve({ kind: 'conflict', conflicts });
    }
    const body = merged
      ? mergeAfterConflict(this.history.get(baseRevision) ?? [], blocks, current, new Map())
      : [...blocks];
    this.doc = { ...this.doc, revision: this.doc.revision + 1, blocks: body.map((block) => stored(block)) };
    this.remember();
    return Promise.resolve({
      kind: 'saved',
      revision: this.doc.revision,
      amended: false,
      merged,
      blocks: this.doc.blocks,
    });
  };

  /** Stale metadata changes conflict only when the same field changed since the base revision. */
  patch = (_id: string, baseRevision: number, patch: MetaPatch): Promise<AgentDocument> => {
    if (patch.title !== undefined && this.titles.get(baseRevision) !== this.doc.title) {
      return Promise.reject(new ApiError('conflict', 409, 'conflict'));
    }
    this.doc = { ...this.doc, ...patch, revision: this.doc.revision + 1 };
    this.remember();
    return Promise.resolve(this.doc);
  };

  private remember(): void {
    this.history.set(this.doc.revision, this.doc.blocks.map(withoutHash));
    this.titles.set(this.doc.revision, this.doc.title);
  }
}

function open(server: FakeDocumentServer): DocumentEditorSession {
  return new DocumentEditorSession(server as unknown as DocumentGateway, clock, server.doc);
}

const A = 'aaaaaaaaaa';
const B = 'bbbbbbbbbb';

describe('DocumentEditorSession', () => {
  it('sends only the newest blocks when saves pile up and tracks the revision', async () => {
    const server = new FakeDocumentServer(document({ revision: 3, blocks: [stored(paragraph(A, 'hello'))] }));
    const session = open(server);
    void session.saveContent([paragraph(A, 'hello w')]);
    void session.saveContent([paragraph(A, 'hello wo')]);
    expect(await session.saveContent([paragraph(A, 'hello world')])).toBe('saved');
    expect(server.doc.blocks.map(withoutHash)).toEqual([paragraph(A, 'hello world')]);
    expect(session.getState()).toMatchObject({ status: 'saved', revision: server.doc.revision });
  });

  it('does not create a revision when the content equals what the server has', async () => {
    const server = new FakeDocumentServer(document({ revision: 3, blocks: [stored(paragraph(A, 'hello'))] }));
    const session = open(server);
    await session.saveContent([paragraph(A, 'hello')]);
    expect(server.doc.revision).toBe(3);
    expect(session.getState().status).toBe('saved');
  });

  it('marks the document unsaved as soon as the user types', () => {
    const server = new FakeDocumentServer(document({ revision: 1 }));
    const session = open(server);
    session.markEdited();
    expect(session.getState().status).toBe('pending');
  });

  it("adopts the server's merge so the next save keeps the agent's change", async () => {
    const server = new FakeDocumentServer(
      document({ revision: 5, blocks: [stored(paragraph(A, 'one')), stored(paragraph(B, 'two'))] }),
    );
    const session = open(server);
    const merges: ServerMerge[] = [];
    session.onServerMerge((merge) => merges.push(merge));
    server.agentWrites({ blocks: [paragraph(A, 'one'), paragraph(B, 'two by agent')] });

    await session.saveContent([paragraph(A, 'one by me'), paragraph(B, 'two')]);
    expect(session.currentBlocks()).toEqual([paragraph(A, 'one by me'), paragraph(B, 'two by agent')]);
    expect(merges.map((merge) => merge.server)).toEqual([
      [paragraph(A, 'one by me'), paragraph(B, 'two by agent')],
    ]);

    await session.saveContent([paragraph(A, 'one by me again'), paragraph(B, 'two by agent')]);
    expect(server.doc.blocks.map(withoutHash)).toEqual([
      paragraph(A, 'one by me again'),
      paragraph(B, 'two by agent'),
    ]);
  });

  it('keeps my input on conflict, reports it and saves the merge of my picks', async () => {
    const server = new FakeDocumentServer(
      document({ revision: 3, blocks: [stored(paragraph(A, 'one')), stored(paragraph(B, 'two'))] }),
    );
    const session = open(server);
    server.agentWrites({ blocks: [paragraph(A, 'one by agent'), paragraph(B, 'two')] });

    expect(await session.saveContent([paragraph(A, 'one by me'), paragraph(B, 'two by me')])).toBe(
      'conflict',
    );
    expect(session.getState().conflicts.map((conflict) => conflict.blockId)).toEqual([A]);
    expect(session.currentBlocks()).toEqual([paragraph(A, 'one by me'), paragraph(B, 'two by me')]);

    const resolved = await session.resolveConflicts(new Map([[A, 'theirs' as const]]));
    expect(resolved).toEqual({
      blocks: [paragraph(A, 'one by agent'), paragraph(B, 'two by me')],
      outcome: 'saved',
    });
    expect(server.doc.blocks.map(withoutHash)).toEqual(resolved.blocks);
    expect(session.getState()).toMatchObject({
      status: 'saved',
      conflicts: [],
      revision: server.doc.revision,
    });
  });

  it('reports a failed save and succeeds when the same blocks are sent again', async () => {
    const server = new FakeDocumentServer(document({ revision: 1, blocks: [stored(paragraph(A, 'x'))] }));
    const session = open(server);
    server.failNext = true;
    expect(await session.saveContent([paragraph(A, 'y')])).toBe('error');
    expect(session.getState().errorMessage).toContain('서버에 연결할 수 없습니다');
    expect(await session.saveContent([paragraph(A, 'y')])).toBe('saved');
    expect(session.getState()).toMatchObject({ status: 'saved', errorMessage: null });
    expect(server.doc.blocks.map(withoutHash)).toEqual([paragraph(A, 'y')]);
  });

  it('saves metadata on a stale revision when nobody changed the same field', async () => {
    const server = new FakeDocumentServer(document({ revision: 1, title: 'Old' }));
    const session = open(server);
    server.agentWrites({ tags: ['agent'] });
    expect(await session.patchMeta({ title: 'New' })).toBe(true);
    expect(server.doc.title).toBe('New');
    expect(session.getState()).toMatchObject({ revision: server.doc.revision, errorMessage: null });
  });

  it('rolls the title back to the saved one when the server refuses the change', async () => {
    const server = new FakeDocumentServer(document({ revision: 1, title: 'Old' }));
    const session = open(server);
    server.agentWrites({ title: 'Agent title' });
    expect(await session.patchMeta({ title: 'Mine' })).toBe(false);
    expect(session.getState().meta.title).toBe('Old');
    expect(session.getState().errorMessage).not.toBeNull();
  });
});
