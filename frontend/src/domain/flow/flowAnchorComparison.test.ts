import { describe, expect, it } from 'vitest';
import type { DesignObject, FlowDesignBlock } from '../document/designBlock';
import { thread } from '../../test-support/fixtures';
import { flowAnchorTarget, flowAnchorDifferences } from './flowAnchorComparison';
import { flowAnchorLocation, flowAnchorSearch } from './flowAnchorLocation';
import { newFlow } from './flow';

const block = (id: string, design: DesignObject): FlowDesignBlock => ({ type: 'flowDesign', id, design });
const target = {
  owner: 'component',
  node: 'caption',
  via: [{ owner: 'page', instance: 'save', component: 'component' }],
};
function revision(caption = '저장', showButton = true) {
  return [
    block('settings', { kind: 'settings', id: 'settings', modelVersion: 4, frame: 'phone' }),
    block('page-block', {
      kind: 'screen',
      id: 'page',
      code: 'F-01',
      name: '작성',
      root: {
        kind: 'box',
        id: 'root',
        layout: { mode: 'free' },
        children: showButton ? [{ kind: 'instance', id: 'save', component: 'component' }] : [],
      },
    }),
    block('state-block', { kind: 'state', id: 'busy', screen: 'page', name: '처리 중', changes: [] }),
    block('note-block', {
      kind: 'annotation',
      id: 'note',
      name: '저장 안내',
      description: '누르면 저장합니다.',
      screen: 'page',
      state: 'busy',
      target,
    }),
    block('shared-block', {
      kind: 'component',
      id: 'component',
      root: { kind: 'text', id: 'caption', text: caption },
    }),
  ];
}
describe('화면 흐름 댓글 위치', () => {
  it('uses storage IDs for screen and state, and portable IDs for the nested layer', () => {
    const blocks = revision();
    expect(flowAnchorLocation(blocks, 'note-block')).toMatchObject({
      kind: 'element',
      screenId: 'page',
      screenBlockId: 'page-block',
      stateId: 'busy',
      address: target,
    });
    const query = new URLSearchParams(flowAnchorSearch(blocks, 'note-block', '?tab=all&flowElement=old'));
    expect(Object.fromEntries(query)).toEqual({
      tab: 'all',
      flowScreen: 'page-block',
      flowState: 'state-block',
      flowElement: 'caption',
      flowOwner: 'component',
      flowVia: JSON.stringify(target.via),
    });
  });
  it('classifies changes to the targeted occurrence, its removal, and deletion of its screen', () => {
    const past = revision();
    expect(flowAnchorTarget(past, revision(), 'note-block')).toBe('ok');
    expect(flowAnchorTarget(past, revision('수정'), 'note-block')).toBe('changed');
    expect(flowAnchorTarget(past, revision('저장', false), 'note-block')).toBe('elementDeleted');
    expect(
      flowAnchorTarget(
        past,
        past.filter((b) => b.id !== 'state-block'),
        'state-block',
      ),
    ).toBe('elementDeleted');
    expect(
      flowAnchorTarget(
        past,
        past.filter((b) => b.id !== 'page-block'),
        'note-block',
      ),
    ).toBe('screenDeleted');
  });
  it('a shared annotation in two viewed states has separate render and deletion status', () => {
    const shared = revision().map((b) =>
      b.design.kind === 'annotation' ? { ...b, design: { ...b.design, state: null } } : b,
    );
    const anchored = thread({
      anchor: { kind: 'block', blockId: 'note-block', viewedStateId: 'state-block' },
    });
    const onlyBase = shared.filter((b) => b.id !== 'state-block');
    expect(flowAnchorTarget(shared, onlyBase, 'note-block', anchored)).toBe('elementDeleted');
    expect(flowAnchorTarget(shared, onlyBase, 'note-block')).toBe('ok');
    expect(flowAnchorSearch(shared, 'note-block', '', 'state-block')).toContain('flowState=state-block');
    expect(flowAnchorSearch(onlyBase, 'note-block', '', 'state-block')).toBeUndefined();
    expect(flowAnchorSearch(shared, 'page-block', '', 'state-block')).toBeUndefined();
  });
  it('uses a server migration comparison only for the saved version that produced it', () => {
    const old = newFlow('settings', 'page');
    const now = [
      block('settings', { kind: 'settings', id: 'settings', modelVersion: 4 }),
      block('page', { kind: 'screen', id: 'page', name: '새 화면' }),
    ];
    const saved = thread({ target: 'changed', targetVersion: 4 });
    expect(flowAnchorTarget(old, now, 'page', saved, 4)).toBe('changed');
    expect(flowAnchorTarget(old, now, 'page', saved, 5)).toBe('ok');
    expect(flowAnchorDifferences(old, now, 'page', 'changed')).toEqual([]);
    expect(flowAnchorTarget(now, old, 'page', saved, 4)).toBe('changed');
  });
});
