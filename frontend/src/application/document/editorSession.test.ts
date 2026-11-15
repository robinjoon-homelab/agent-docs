import { describe, expect, it } from 'vitest';
import type { Block } from '../../domain/document/block';
import { withoutHash } from '../../domain/document/block';
import type { AgentDocument } from '../../domain/document/document';
import { document, paragraph, stored } from '../../test-support/fixtures';
import type { Clock } from '../shared/ports/Clock';
import { StaleDocumentError } from './ports/DocumentGateway';
import { ApiError } from '../shared/ports/errors';
import type { DocumentGateway, MetaPatch, SavedContent, SaveContentResult } from './ports/DocumentGateway';
import type { ServerMerge } from './editorSession';
import { DocumentEditorSession } from './editorSession';

const clock: Clock = { now: () => new Date('2026-10-18T10:00:00Z') };

class FakeDocumentServer implements Partial<DocumentGateway> {
  doc: AgentDocument;
  failNext = false;

  constructor(doc: AgentDocument) {
    this.doc = doc;
  }

  /** Simulates an agent edit that lands on the server first. */
  agentWrites(change: Omit<Partial<AgentDocument>, 'blocks'> & { blocks?: Block[] }): void {
    const blocks = change.blocks ?? this.doc.blocks.map(withoutHash);
    this.doc = {
      ...this.doc,
      ...change,
      version: this.doc.version + 1,
      revision: this.doc.revision + 1,
      blocks: blocks.map((block) => stored(block)),
    };
  }

  get = (): Promise<AgentDocument> => Promise.resolve(this.doc);

  saveContent = (_id: string, baseVersion: number, blocks: readonly Block[]): Promise<SaveContentResult> => {
    if (this.failNext) {
      this.failNext = false;
      return Promise.reject(new ApiError('network', 0, 'offline'));
    }
    if (baseVersion !== this.doc.version)
      return Promise.resolve({
        kind: 'conflict',
        currentVersion: this.doc.version,
        currentDocument: this.doc,
      });
    this.doc = { ...this.doc, version: this.doc.version + 1, blocks: blocks.map((block) => stored(block)) };
    return Promise.resolve({
      kind: 'saved',
      version: this.doc.version,
      revision: this.doc.revision,
      blocks: this.doc.blocks,
    });
  };

  /** Stale metadata changes conflict only when the same field changed since the base revision. */
  patch = (_id: string, baseVersion: number, patch: MetaPatch): Promise<AgentDocument> => {
    if (baseVersion !== this.doc.version) {
      return Promise.reject(new StaleDocumentError(this.doc.version, this.doc));
    }
    this.doc = { ...this.doc, ...patch, version: this.doc.version + 1 };
    return Promise.resolve(this.doc);
  };

  finalize = (_id: string, baseVersion: number): Promise<SavedContent> => {
    if (baseVersion === this.doc.version) this.doc = { ...this.doc, revision: this.doc.revision + 1 };
    return Promise.resolve({
      kind: 'saved',
      version: this.doc.version,
      revision: this.doc.revision,
      blocks: this.doc.blocks,
    });
  };
}

function open(server: FakeDocumentServer): DocumentEditorSession {
  return new DocumentEditorSession(server as unknown as DocumentGateway, clock, server.doc);
}

const A = 'aaaaaaaaaa';
const B = 'bbbbbbbbbb';

describe('DocumentEditorSession', () => {
  it('includes input inside the debounce window when a delayed 409 arrives', async () => {
    const server = new FakeDocumentServer(
      document({ version: 3, revision: 3, blocks: [stored(paragraph(A, 'A0')), stored(paragraph(B, 'B0'))] }),
    );
    let complete: (value: SaveContentResult) => void = () => {
      throw new Error('not initialized');
    };
    const delayed = new Promise<SaveContentResult>((resolve) => {
      complete = resolve;
    });
    const responses = [delayed];
    const save = server.saveContent;
    server.saveContent = (id, version, blocks) => responses.shift() ?? save(id, version, blocks);
    const session = open(server);
    const saving = session.saveContent([paragraph(A, 'A1'), paragraph(B, 'B0')]);
    await Promise.resolve();
    session.markEdited([paragraph(A, 'A1'), paragraph(B, 'B1')]);
    server.agentWrites({ blocks: [paragraph(A, 'A0'), paragraph(B, 'B2')] });
    complete({ kind: 'conflict', currentVersion: server.doc.version, currentDocument: server.doc });
    expect(await saving).toBe('conflict');
    expect(session.getState().conflicts.map((item) => item.blockId)).toEqual([B]);
    expect(session.currentBlocks()).toEqual([paragraph(A, 'A1'), paragraph(B, 'B1')]);
    expect(await session.saveContent(session.currentBlocks())).toBe('conflict');
    expect(server.doc.blocks.map(withoutHash)).toEqual([paragraph(A, 'A0'), paragraph(B, 'B2')]);
  });

  it('an unedited tab never finalizes another tabs pending input', async () => {
    const server = new FakeDocumentServer(document({ version: 10, revision: 3 }));
    const idleTab = open(server);
    await server.saveContent(server.doc.id, 10, [paragraph(A, 'other tab')]);
    expect(idleTab.hasPendingRevision()).toBe(false);
    await idleTab.finalize();
    expect(server.doc).toMatchObject({ version: 11, revision: 3 });
  });

  it('binds finalization to this tabs successful version and leaves a newer edit pending', async () => {
    const server = new FakeDocumentServer(document({ version: 10, revision: 3 }));
    const session = open(server);
    await session.saveContent([paragraph(A, 'this tab')]);
    await server.saveContent(server.doc.id, 11, [paragraph(A, 'newer tab')]);
    await session.finalize();
    expect(server.doc).toMatchObject({ version: 12, revision: 3 });
    expect(server.doc.blocks.map(withoutHash)).toEqual([paragraph(A, 'newer tab')]);
  });

  it('finalizes only once after this tabs successful save', async () => {
    const server = new FakeDocumentServer(document({ version: 10, revision: 3 }));
    const session = open(server);
    await session.saveContent([paragraph(A, 'this tab')]);
    await session.finalize();
    await session.finalize();
    expect(server.doc).toMatchObject({ version: 11, revision: 4 });
    expect(session.hasPendingRevision()).toBe(false);
  });

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

  it('takes the path a move elsewhere gave the document and keeps unsent typing', async () => {
    const server = new FakeDocumentServer(
      document({ revision: 3, path: 'payments', blocks: [stored(paragraph(A, 'hello'))] }),
    );
    const session = open(server);
    server.agentWrites({ path: 'billing/payments' });
    session.markEdited();
    void session.saveContent([paragraph(A, 'hello there')]);

    await session.refreshMeta();
    await session.idle();

    expect(session.getState().meta.path).toBe('billing/payments');
    expect(server.doc.blocks.map(withoutHash)).toEqual([paragraph(A, 'hello there')]);
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
      [paragraph(A, 'one'), paragraph(B, 'two by agent')],
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
  it('keeps the input base when metadata changes before delayed body input is submitted', async () => {
    const server = new FakeDocumentServer(document({ blocks: [stored(paragraph(A, 'old'))] }));
    const session = open(server);
    session.markEdited();
    server.agentWrites({ blocks: [paragraph(A, 'remote')] });
    expect(await session.patchMeta({ title: 'My title' })).toBe(true);
    expect(await session.saveContent([paragraph(A, 'local')])).toBe('conflict');
    expect(server.doc.blocks.map(withoutHash)).toEqual([paragraph(A, 'remote')]);
    expect(session.currentBlocks()).toEqual([paragraph(A, 'local')]);
  });

  it('rejects a concurrent change to tags instead of overwriting that field', async () => {
    const server = new FakeDocumentServer(document({ tags: ['old'] }));
    const session = open(server);
    server.agentWrites({ tags: ['remote'] });
    expect(await session.patchMeta({ tags: ['mine'] })).toBe(false);
    expect(server.doc.tags).toEqual(['remote']);
  });
  it('does not finalize while newer local input remains unsaved', async () => {
    const server = new FakeDocumentServer(document({ version: 10, revision: 3 }));
    const session = open(server);
    await session.saveContent([paragraph(A, 'saved body')]);
    session.markEdited();
    await session.finalize();
    expect(session.getState()).toMatchObject({ version: 11, revision: 3, status: 'pending' });
    expect(server.doc.blocks.map(withoutHash)).toEqual([paragraph(A, 'saved body')]);
  });
});
