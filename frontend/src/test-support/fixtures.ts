import type { Block, StoredBlock } from '../domain/document/block';
import type { AgentDocument, DocumentSummary } from '../domain/document/document';
import type { Actor } from '../domain/shared/actor';
import type { Thread } from '../domain/thread/thread';

export function human(login = 'octocat'): Actor {
  return { kind: 'human', login, agent: null };
}

export function agent(clientName = 'Claude Code', login = 'octocat'): Actor {
  return { kind: 'agent', login, agent: { connectionId: 'c-1', clientName } };
}

export function paragraph(id: string, text: string): Block {
  return { id, type: 'paragraph', text };
}

export function stored(block: Block, hash = '00000000'): StoredBlock {
  return { ...block, hash };
}

export function summary(overrides: Partial<DocumentSummary> = {}): DocumentSummary {
  return {
    id: 'doc-1',
    path: 'guides/setup',
    title: 'Setup',
    status: 'draft',
    tags: [],
    assignee: null,
    revision: 1,
    updatedAt: '2026-10-18T09:00:00Z',
    updatedBy: human(),
    ...overrides,
  };
}

export function document(overrides: Partial<AgentDocument> = {}): AgentDocument {
  return {
    id: 'doc-1',
    space: { id: 'sp-1', slug: 'team', name: 'Team' },
    path: 'guides/setup',
    title: 'Setup',
    status: 'draft',
    tags: [],
    assignee: null,
    revision: 3,
    createdAt: '2026-10-01T09:00:00Z',
    updatedAt: '2026-10-18T09:00:00Z',
    trashedAt: null,
    blocks: [stored(paragraph('aaaaaaaaaa', 'hello'))],
    blockChanges: [],
    ...overrides,
  };
}

export function thread(overrides: Partial<Thread> = {}): Thread {
  return {
    id: 'th-1',
    documentId: 'doc-1',
    anchor: { kind: 'text', blockId: 'aaaaaaaaaa', start: 0, end: 5, quote: 'hello' },
    resolution: { state: 'attached' },
    status: 'open',
    author: human(),
    comments: [],
    linkedRevisions: [],
    createdAt: '2026-10-18T09:00:00Z',
    updatedAt: '2026-10-18T09:00:00Z',
    ...overrides,
  };
}
