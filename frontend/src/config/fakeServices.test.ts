import { describe, expect, it } from 'vitest';
import { simulateAgentEdit } from '../adapters/outbound/memory/document/MemoryDocumentGateway';
import { seededStore } from '../adapters/outbound/memory/shared/seed';
import { emptyEditStatistics } from '../domain/statistics/statistics';
import { fakeServicesOf } from './fakeServices';

function fake() {
  const store = seededStore();
  return { store, services: fakeServicesOf(store, 'Asia/Seoul') };
}

/** The store reports changes after the request's synchronous work, like an event arriving after a commit. */
const settled = (): Promise<void> => new Promise((resolve) => setTimeout(resolve, 0));

describe('fake services', () => {
  it('signals an open document when an agent edits it, and stops after the page closes', async () => {
    const { store, services } = fake();
    const signals: string[] = [];
    const stop = services.documentEvents.watch('doc-arch', () => signals.push('doc-arch'));
    services.documentEvents.watch('doc-setup', () => signals.push('doc-setup'));
    expect(signals).toEqual(['doc-arch', 'doc-setup']);

    simulateAgentEdit(store, 'doc-arch', 'p1archintr', '에이전트가 고친 문장');
    await settled();
    expect(signals).toEqual(['doc-arch', 'doc-setup', 'doc-arch']);

    stop();
    simulateAgentEdit(store, 'doc-arch', 'p1archintr', '닫힌 뒤의 수정');
    await settled();
    expect(signals).toEqual(['doc-arch', 'doc-setup', 'doc-arch']);
  });

  it('signals the document when one of its threads changes', async () => {
    const { services } = fake();
    let signals = 0;
    services.documentEvents.watch('doc-arch', () => {
      signals += 1;
    });

    await services.threads.react('th-cell', '👍', true);
    await settled();

    expect(signals).toBe(2);
  });

  it('saves a quick memo where the editor reads it', async () => {
    const { services } = fake();

    const id = await services.navigation.writeMemo({
      opId: 'op-memo',
      blockId: 'memoblock1',
      space: 'personal',
      path: '메모/2026-11-16-a1',
      text: '장보기',
    });

    const memo = await services.documents.get(id);
    expect([memo.space.slug, memo.path, memo.blocks.map((block) => block.id)]).toEqual([
      'personal',
      '메모/2026-11-16-a1',
      ['memoblock1'],
    ]);
  });

  it('keeps unsupported import and export visibly failing and invents no statistics', async () => {
    const { services } = fake();

    await expect(services.portability.exportZip(null)).rejects.toThrow('서버 연결이 필요합니다');
    await expect(services.portability.importFolder('personal', 'Markdown', [])).rejects.toThrow(
      '서버 연결이 필요합니다',
    );
    expect(await services.statistics.get('doc-arch')).toEqual(emptyEditStatistics());
  });
});
