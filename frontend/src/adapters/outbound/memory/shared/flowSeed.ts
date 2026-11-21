import previewCases from './flowPreviewCases.json';
import { readFlow, flowBlocks } from '../../../../domain/flow/flow';
import type { Block, StoredBlock } from '../../../../domain/document/block';
import type { AgentDocument } from '../../../../domain/document/document';
import type { Thread, ThreadReaction } from '../../../../domain/thread/thread';
import type { MemoryStore } from './store';
import example from './flowExample.json';
const hashed = (blocks: readonly Block[]): StoredBlock[] =>
  blocks.map((b, i) => ({ ...b, hash: (0x20000000 + i).toString(16) }));
function seedReactions(value: Record<string, readonly string[] | undefined> | undefined): ThreadReaction[] {
  const rows: Record<string, readonly string[] | undefined> = value ?? {};
  return Object.entries(rows).flatMap(([emoji, logins]) =>
    (logins ?? []).map((login) => ({ emoji, actor: { kind: 'human' as const, login, agent: null } })),
  );
}
type ExampleThread = {
  id: string;
  rev: number;
  anchorId: string | null;
  blocks: Block[];
  status: string;
  comments: {
    who: string;
    body: string;
    time: string;
    agent?: boolean;
    edited?: boolean;
    deleted?: boolean;
  }[];
  revisions?: number[];
  reactions?: Record<string, string[]>;
  claim?: { who: string; until: string };
  suggestions?: { before: string; after: string; decision: null }[];
};
function sampleTime(label: string): string {
  const hours = label.includes('지난주')
    ? 168
    : label.includes('어제')
      ? 24
      : Number(label.match(/(\d+)시간/)?.[1] ?? 0);
  const minutes = Number(label.match(/(\d+)분/)?.[1] ?? 0);
  return new Date(Date.now() - (hours * 60 + minutes) * 60000).toISOString();
}
function claimExpiry(): string {
  const date = new Date();
  date.setHours(15, 30, 0, 0);
  if (date.getTime() < Date.now()) date.setDate(date.getDate() + 1);
  return date.toISOString();
}
export function seedFlow(store: MemoryStore, base: AgentDocument): void {
  const number =
    typeof window === 'undefined' ? 0 : Number(new URLSearchParams(window.location.search).get('flowCase'));
  const scenario = previewCases.cases.find((c) => c.number === number);
  store.me = { ...store.me, login: scenario?.ui.viewer ?? 'pm-lee', name: scenario?.ui.viewer ?? 'pm-lee' };
  const source = {
    blocks: scenario ? (previewCases.documents[scenario.document] ?? example.blocks) : example.blocks,
    threads: scenario ? (previewCases.threadsets[scenario.threadset] ?? example.threads) : example.threads,
  } as unknown as { blocks: Block[]; threads: ExampleThread[] };
  if (scenario && scenario.ui.permission !== 'owner') {
    store.me = { ...store.me, owner: false, login: scenario.ui.viewer };
    store.spaces = store.spaces.map((space) => ({
      ...space,
      permission:
        scenario.ui.permission === 'read'
          ? 'read'
          : scenario.ui.permission === 'comment'
            ? 'comment'
            : 'edit',
    }));
  }

  const doc: AgentDocument = {
    ...base,
    id: 'doc-flow',
    type: 'flow',
    path: '기획/화면-흐름/mobile-quick-memo',
    title: '모바일 빠른 메모 화면 흐름',
    status: scenario?.ui.blank ? 'draft' : 'in_review',
    tags: scenario?.ui.blank ? [] : ['기획', '모바일'],
    assignee: scenario?.ui.blank ? null : 'pm-lee',
    version: 14,
    revision: scenario?.ui.blank ? 1 : 14,
    blocks: hashed(source.blocks),
    blockChanges: [],
  };
  store.documents.set(doc.id, doc);
  const threads: Thread[] = source.threads.map((t) => ({
    anchoredRevision: t.rev,
    id: `flow-${t.id}`,
    documentId: doc.id,
    anchor: t.anchorId ? { kind: 'block', blockId: t.anchorId } : { kind: 'document' },
    resolution: { state: 'attached' },
    status: t.status === 'resolved' ? 'resolved' : t.status === 'answered' ? 'answered' : 'open',
    author: { kind: 'human', login: t.comments[0]?.who ?? 'pm-lee', agent: null },
    comments: t.comments.map((c, i) => ({
      id: `flow-${t.id}-${i}`,
      author:
        'agent' in c && c.agent
          ? {
              kind: 'agent',
              login: 'pm-lee',
              agent: { connectionId: 'flow-example-agent', clientName: c.who },
            }
          : { kind: 'human', login: c.who, agent: null },
      body: c.body,
      createdAt: sampleTime(c.time),
      editedAt: 'edited' in c && c.edited ? doc.updatedAt : null,
      deleted: c.deleted ?? false,
    })),
    linkedRevisions: t.revisions ?? [],
    claim: t.claim
      ? {
          actor: {
            kind: 'agent',
            login: 'pm-lee',
            agent: { connectionId: 'flow-example-agent', clientName: t.claim.who },
          },
          acquiredAt: new Date(Date.now() - 600000).toISOString(),
          expiresAt: claimExpiry(),
        }
      : null,
    suggestions: (t.suggestions ?? []).map((suggestion) => ({
      commentId: `flow-${t.id}-${t.comments.length - 1}`,
      anchor: t.anchorId ? { kind: 'block' as const, blockId: t.anchorId } : { kind: 'document' as const },
      hash: doc.blocks.find((b) => b.id === t.anchorId)?.hash ?? '',
      before: suggestion.before,
      after: suggestion.after,
      decision: null,
    })),
    reactions: seedReactions(t.reactions),
    createdAt: doc.createdAt,
    updatedAt: doc.updatedAt,
  }));
  store.threads.set(doc.id, threads);
  const revisions = [
    ...new Set([
      ...source.threads.map((t) => t.rev),
      ...source.threads.flatMap((t) => t.revisions ?? []),
      doc.revision,
    ]),
  ].sort((a, b) => b - a);
  store.revisions.set(
    doc.id,
    revisions.map((revision) => {
      const entries = source.threads.filter((t) => t.rev === revision);
      const flow = readFlow(doc.blocks);
      for (const entry of entries) {
        for (const past of readFlow(entry.blocks).screens) {
          const at = flow.screens.findIndex((screen) => screen.screen.id === past.screen.id);
          if (at < 0) flow.screens.push(past);
          else flow.screens[at] = past;
        }
      }
      return {
        meta: {
          revision,
          actor: store.actor,
          source: 'web' as const,
          summary: '댓글 작성 시점',
          changedBlockIds: [],
          createdAt: doc.createdAt,
          threadIds: entries.map((t) => `flow-${t.id}`),
        },
        snapshot: { ...doc, revision, actor: store.actor, blocks: hashed(flowBlocks(flow)) },
      };
    }),
  );
}
