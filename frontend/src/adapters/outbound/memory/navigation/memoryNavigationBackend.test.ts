import { describe, expect, it } from 'vitest';
import { isApiError } from '../../../../application/shared/ports/errors';
import type { MemoCommand } from '../../../../domain/navigation/navigation';
import { MemoryDocumentGateway } from '../document/MemoryDocumentGateway';
import { seededStore } from '../shared/seed';
import { MemorySpaceGateway } from '../space/MemorySpaceGateway';
import { MemoryNavigationGateway } from './MemoryNavigationGateway';
import { memoryNavigationBackend, memoryNavigationState } from './memoryNavigationBackend';

function preview() {
  const store = seededStore();
  const documents = new MemoryDocumentGateway(store);
  const navigation = new MemoryNavigationGateway(
    memoryNavigationState(),
    memoryNavigationBackend(store, documents, 'Asia/Seoul'),
  );
  return { store, documents, navigation, spaces: new MemorySpaceGateway(store) };
}

function memo(overrides: Partial<MemoCommand> = {}): MemoCommand {
  return {
    opId: 'op-1',
    blockId: 'memoblock1',
    space: 'personal',
    path: '일기/2026-11-16',
    text: ' 첫 줄\n둘째 줄 ',
    ...overrides,
  };
}

describe('fake quick memo', () => {
  it('creates the document at the exact path when none is there', async () => {
    const { store, navigation } = preview();

    const id = await navigation.writeMemo(memo());

    const created = store.document(id);
    expect(created.space.slug).toBe('personal');
    expect(created.path).toBe('일기/2026-11-16');
    expect(created.title).toBe('2026-11-16');
    expect(created.blocks.map((block) => ('text' in block ? block.text : null))).toEqual([
      '첫 줄<br>둘째 줄',
    ]);
    expect(created.revision).toBe(1);
  });

  it('appends after the existing blocks as a new confirmed revision', async () => {
    const { store, documents, navigation } = preview();
    const first = await navigation.writeMemo(memo());

    const second = await navigation.writeMemo(
      memo({ opId: 'op-2', blockId: 'memoblock2', text: '저녁 메모' }),
    );

    expect(second).toBe(first);
    const diary = store.document(first);
    expect(diary.blocks.map((block) => block.id)).toEqual(['memoblock1', 'memoblock2']);
    expect(diary.revision).toBe(2);
    expect((await documents.revisions(first)).map((entry) => entry.revision)).toEqual([2, 1]);
  });

  it('confirms unconfirmed editor input as its own revision before the memo', async () => {
    const { store, documents, navigation } = preview();
    const id = await navigation.writeMemo(memo());
    const typed = store.document(id);
    await documents.saveContent(id, typed.version, [
      ...typed.blocks,
      { id: 'typed00001', type: 'paragraph', text: '입력 중' },
    ]);

    await navigation.writeMemo(memo({ opId: 'op-2', blockId: 'memoblock2', text: '저녁 메모' }));

    const revisions = await documents.revisions(id);
    expect(revisions.map((entry) => entry.revision)).toEqual([3, 2, 1]);
    const beforeMemo = await documents.revision(id, 2);
    expect(beforeMemo.blocks.map((block) => block.id)).toEqual(['memoblock1', 'typed00001']);
    expect(store.document(id).blocks.map((block) => block.id)).toEqual([
      'memoblock1',
      'typed00001',
      'memoblock2',
    ]);
  });

  it('answers a retried opId with the first result and does not append again', async () => {
    const { store, navigation } = preview();
    const first = await navigation.writeMemo(memo());

    const retried = await navigation.writeMemo(memo({ text: '다시 보낸 본문' }));

    expect(retried).toBe(first);
    expect(store.document(first).blocks).toHaveLength(1);
  });

  it('does not remember a failed memo, so the same opId can succeed later', async () => {
    const { navigation, spaces, store } = preview();
    const failed = await navigation.writeMemo(memo({ space: 'later' })).catch((error: unknown) => error);
    expect(isApiError(failed, 'not_found')).toBe(true);

    await spaces.create({ slug: 'later', name: '나중', description: '', links: [] });
    const id = await navigation.writeMemo(memo({ space: 'later' }));

    expect(store.document(id).space.slug).toBe('later');
  });
});

describe('fake backlinks and personal lists', () => {
  it('lists linking documents of archived spaces but not those in the trash', async () => {
    const { documents, navigation, spaces } = preview();
    await spaces.update('agent-docs', { archived: true });

    const archived = await navigation.backlinks('doc-setup');
    expect(archived.documents.map((entry) => [entry.id, entry.spaceSlug])).toEqual([
      ['doc-arch', 'agent-docs'],
    ]);

    await navigation.recordView('doc-arch');
    await documents.trash('doc-arch');
    expect((await navigation.backlinks('doc-setup')).total).toBe(0);
    expect((await navigation.recent()).total).toBe(0);
  });

  it('counts canonical document paths and excludes unauthorized referring docs', async () => {
    const { store, navigation } = preview();
    store.me = { ...store.me, owner: false };
    store.spaces = store.spaces.map((space) =>
      space.id === 'space-agent' ? { ...space, permission: 'read' as const } : space,
    );
    const setupDoc = store.document('doc-setup');
    store.documents.set('doc-arch', {
      ...store.document('doc-arch'),
      blocks: [
        {
          id: 'link000001',
          type: 'paragraph',
          text: `[설정](/s/agent-docs/d/${setupDoc.id}#block-p1)`,
          hash: 'h1',
        },
      ],
    });
    store.spaces = [
      ...store.spaces,
      {
        id: 'space-secret',
        slug: 'secret',
        name: '비밀',
        description: '',
        links: [],
        createdAt: '2026-01-01T00:00:00Z',
        archivedAt: null,
        lastActivityAt: null,
        documentCount: 1,
      },
    ];
    store.documents.set('doc-secret', {
      ...setupDoc,
      id: 'doc-secret',
      space: { id: 'space-secret', slug: 'secret', name: '비밀' },
      path: 'secret',
      title: '비밀 문서',
      blocks: [{ id: 'sec0000001', type: 'paragraph', text: `[설정](doc:${setupDoc.id})`, hash: 'h2' }],
    });

    const backlinks = await navigation.backlinks(setupDoc.id);
    expect(backlinks.documents.map((entry) => entry.id)).toEqual(['doc-arch']);
  });

  it('shows a viewed document under its current space name', async () => {
    const { navigation, spaces } = preview();
    await navigation.recordView('doc-setup');
    await spaces.update('agent-docs', { name: '에이전트 문서' });

    const recent = await navigation.recent();

    expect(recent.documents.map((entry) => [entry.id, entry.spaceName])).toEqual([
      ['doc-setup', '에이전트 문서'],
    ]);
  });
});
