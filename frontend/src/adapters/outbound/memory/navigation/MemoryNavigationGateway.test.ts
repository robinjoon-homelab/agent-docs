import { describe, expect, it } from 'vitest';
import type { NavigationDocument } from '../../../../domain/navigation/navigation';
import { MemoryNavigationGateway } from './MemoryNavigationGateway';
import type { MemoryNavigationState } from './MemoryNavigationGateway';

const document: NavigationDocument = {
  id: 'visible',
  spaceId: 'space',
  spaceSlug: 'notes',
  spaceName: 'Notes',
  path: 'memo',
  title: 'Memo',
};

function fixture(visible: readonly NavigationDocument[]) {
  const state: MemoryNavigationState = { visits: new Map(), favorites: new Map(), preferences: new Map() };
  const gateway = new MemoryNavigationGateway(state, {
    userId: () => 'user',
    visible: () => visible,
    backlinks: () => [],
    writeMemo: () => Promise.resolve('memo'),
    now: () => 123,
    timeZone: 'Asia/Seoul',
  });
  return { state, gateway };
}

const actions: readonly [string, (gateway: MemoryNavigationGateway) => Promise<unknown>][] = [
  ['isFavorite', (gateway) => gateway.isFavorite('hidden')],
  ['backlinks', (gateway) => gateway.backlinks('hidden')],
  ['recordView', (gateway) => gateway.recordView('hidden')],
  ['favorite', (gateway) => gateway.favorite('hidden', true)],
  ['unfavorite', (gateway) => gateway.favorite('hidden', false)],
];

describe('MemoryNavigationGateway promise errors', () => {
  it.each(actions)(
    '%s returns a rejected Promise without throwing during the call',
    async (_name, action) => {
      const { state, gateway } = fixture([]);

      const result = action(gateway);

      expect(result).toBeInstanceOf(Promise);
      await expect(result).rejects.toThrow('문서를 찾을 수 없습니다.');
      expect(state.visits.size).toBe(0);
      expect(state.favorites.size).toBe(0);
    },
  );

  it('keeps successful reads and personal writes available through the Promise contract', async () => {
    const { gateway } = fixture([document]);

    await gateway.recordView(document.id);
    await gateway.favorite(document.id, true);

    expect(await gateway.isFavorite(document.id)).toBe(true);
    expect(await gateway.recent()).toEqual({ documents: [document], total: 1 });
    expect(await gateway.favorites()).toEqual({ documents: [document], total: 1 });
    expect(await gateway.backlinks(document.id)).toEqual({ documents: [], total: 0 });
    await gateway.favorite(document.id, false);
    expect(await gateway.isFavorite(document.id)).toBe(false);
  });
});
