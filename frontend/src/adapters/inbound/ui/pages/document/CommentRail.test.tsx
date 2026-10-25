import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import type { Thread, ThreadFilter } from '../../../../../domain/thread/thread';
import { thread } from '../../../../../test-support/fixtures';
import { CommentRail } from './CommentRail';

function rail(threads: readonly Thread[], filter: ThreadFilter, onCollapse?: () => void): string {
  return renderToStaticMarkup(
    <CommentRail
      threads={threads}
      filter={filter}
      onFilter={() => undefined}
      pending={null}
      activeThreadId={null}
      login="robin"
      mentionLogins={['robin']}
      spaceSlug="team"
      actions={{
        reply: () => Promise.resolve(true),
        resolve: () => Promise.resolve(),
        reopen: () => Promise.resolve(),
        editComment: () => Promise.resolve(true),
        deleteComment: () => Promise.resolve(),
        startReanchor: () => undefined,
      }}
      aligned={false}
      measure={{ threadTop: () => null, anchorTop: () => null }}
      layoutVersion={0}
      onActivate={() => undefined}
      onStart={() => Promise.resolve(true)}
      onCancelPending={() => undefined}
      onDocumentComment={() => undefined}
      onCollapse={onCollapse}
    />,
  );
}

describe('comment rail empty states', () => {
  it.each<ThreadFilter>(['open', 'all', 'detached'])(
    'invites a first comment only when the document has no threads (%s)',
    (filter) => {
      expect(rail([], filter)).toContain('댓글이 없습니다. 텍스트를 선택하고 댓글을 남기세요.');
    },
  );

  it('explains that no open comments remain when all threads are resolved', () => {
    const markup = rail([thread({ status: 'resolved' })], 'open');
    expect(markup).toContain('열린 댓글이 없습니다.');
    expect(markup).not.toContain('텍스트를 선택하고 댓글을 남기세요.');
  });

  it('explains an empty detached filter without hiding existing threads from the all filter', () => {
    const threads = [thread({ status: 'resolved' })];
    expect(rail(threads, 'detached')).toContain('분리된 댓글이 없습니다.');
    const markup = rail(threads, 'all');
    expect(markup).toContain('댓글 스레드: hello');
    expect(markup).not.toContain('댓글이 없습니다.');
  });
});

describe('comment rail layout', () => {
  it('keeps the empty-state message in the pinned header, so scrolling the document leaves it in view', () => {
    const markup = rail([], 'open');
    const pinned = markup.slice(markup.indexOf('class="rail-top"'), markup.indexOf('class="rail-list"'));
    expect(pinned).toContain('댓글이 없습니다. 텍스트를 선택하고 댓글을 남기세요.');
  });

  it('offers to fold the rail only where folding is possible', () => {
    expect(rail([], 'open', () => undefined)).toContain('aria-label="댓글 접기"');
    expect(rail([], 'open')).not.toContain('댓글 접기');
  });
});
