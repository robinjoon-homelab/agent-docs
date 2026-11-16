// @vitest-environment jsdom
import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { permissionInSpace } from '../../../../domain/space/permission';
import type { Space } from '../../../../domain/space/space';
import { SpacePermissionsProvider, useSpacePermissions } from './SpacePermissionsContext';
afterEach(cleanup);
it('updates navigation policy from space responses without replacing grants in other spaces', () => {
  const first: Space = {
    id: 'A',
    slug: 'a',
    name: 'A',
    permission: 'edit',
    description: '',
    links: [],
    createdAt: '',
    archivedAt: null,
    lastActivityAt: null,
    documentCount: 0,
  };
  const second: Space = { ...first, id: 'B', permission: 'comment' };
  const hook = renderHook(
    () => {
      const live = useSpacePermissions();
      return {
        update: live.update,
        first: permissionInSpace(first, false, live.grants),
        second: permissionInSpace(second, false, live.grants),
      };
    },
    { wrapper: SpacePermissionsProvider },
  );
  expect([hook.result.current.first, hook.result.current.second]).toEqual(['edit', 'comment']);
  act(() => {
    hook.result.current.update({ ...first, permission: 'comment' });
  });
  expect([hook.result.current.first, hook.result.current.second]).toEqual(['comment', 'comment']);
  act(() => {
    hook.result.current.update({ ...first, permission: 'owner' });
  });
  expect([hook.result.current.first, hook.result.current.second]).toEqual(['owner', 'comment']);
});
