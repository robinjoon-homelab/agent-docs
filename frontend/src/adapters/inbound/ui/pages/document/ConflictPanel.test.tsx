// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import type { ListStyle } from '../../../../../domain/document/block';
import { ConflictPanel } from './ConflictPanel';

afterEach(cleanup);

const onResolve = (): Promise<void> => Promise.resolve();

describe('conflict version previews', () => {
  it.each<{ style: ListStyle; currentMarkdown: string }>([
    { style: 'bullet', currentMarkdown: '- 현재 **항목**' },
    { style: 'numbered', currentMarkdown: '1. 현재 **항목**' },
    { style: 'task', currentMarkdown: '- [x] 현재 **항목**' },
  ])('uses the same inline representation for both $style versions', ({ style, currentMarkdown }) => {
    const mine = {
      id: 'l000000001',
      type: 'listItem' as const,
      style,
      depth: 0,
      checked: true,
      text: '내 **항목**',
    };
    render(
      <ConflictPanel
        mine={[mine]}
        conflicts={[
          {
            blockId: mine.id,
            currentHash: '12345678',
            current: { ...mine, text: '현재 **항목**' },
            currentMarkdown,
          },
        ]}
        onResolve={onResolve}
      />,
    );

    expect(screen.getByText('내 **항목**').tagName).toBe('PRE');
    expect(screen.getByText('현재 **항목**').tagName).toBe('PRE');
  });

  it('shows code content without adding a fence to the current version', () => {
    const mine = { id: 'c000000001', type: 'code' as const, language: 'js', code: 'const mine = 1;' };
    render(
      <ConflictPanel
        mine={[mine]}
        conflicts={[
          {
            blockId: mine.id,
            currentHash: '12345678',
            current: { ...mine, code: 'const current = 2;' },
            currentMarkdown: '```js\nconst current = 2;\n```',
          },
        ]}
        onResolve={onResolve}
      />,
    );

    expect(screen.getByText('const mine = 1;').tagName).toBe('PRE');
    expect(screen.getByText('const current = 2;').tagName).toBe('PRE');
  });

  it.each(['- 서버에 남은 내용', ''])(
    'keeps the markdown fallback %j when no current block exists',
    (currentMarkdown) => {
      const { container } = render(
        <ConflictPanel
          mine={[]}
          conflicts={[{ blockId: 'p000000001', currentHash: null, current: null, currentMarkdown }]}
          onResolve={onResolve}
        />,
      );

      expect(Array.from(container.querySelectorAll('pre'), (preview) => preview.textContent)).toEqual([
        '(내 쪽에서 삭제함)',
        currentMarkdown,
      ]);
    },
  );

  it('preserves both deletion labels when neither version exists', () => {
    render(
      <ConflictPanel
        mine={[]}
        conflicts={[{ blockId: 'p000000001', currentHash: null, current: null, currentMarkdown: null }]}
        onResolve={onResolve}
      />,
    );

    expect(screen.getByText('(내 쪽에서 삭제함)').tagName).toBe('PRE');
    expect(screen.getByText('(삭제됨)').tagName).toBe('PRE');
  });

  it('keeps an empty current block instead of replacing it with markdown or a deletion label', () => {
    const mine = { id: 'p000000001', type: 'paragraph' as const, text: '내 내용' };
    const { container } = render(
      <ConflictPanel
        mine={[mine]}
        conflicts={[
          {
            blockId: mine.id,
            currentHash: '12345678',
            current: { ...mine, text: '' },
            currentMarkdown: '이전 내용',
          },
        ]}
        onResolve={onResolve}
      />,
    );

    expect(Array.from(container.querySelectorAll('pre'), (preview) => preview.textContent)).toEqual([
      '내 내용',
      '',
    ]);
  });
});
