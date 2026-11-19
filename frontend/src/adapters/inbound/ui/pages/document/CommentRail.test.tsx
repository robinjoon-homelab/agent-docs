import { MemoryRouter } from 'react-router';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { fakeServicesOf } from '../../../../../config/fakeServices';
import { ServicesProvider } from '../../../../../config/ServicesContext';
import type { Thread, ThreadFilter } from '../../../../../domain/thread/thread';
import { human, thread } from '../../../../../test-support/fixtures';
import { MemoryStore } from '../../../../outbound/memory/shared/store';
import { SessionProvider } from '../../app/SessionContext';
import { DocumentPermissionContext } from './DocumentPermissionContext';
import { CommentRail } from './CommentRail';

function rail(threads: readonly Thread[], filter: ThreadFilter, onCollapse?: () => void): string {
  return renderToStaticMarkup(
    <MemoryRouter>
      <SessionProvider
        session={{
          me: {
            userId: '11111111-1111-4111-8111-111111111111',
            login: 'robin',
            name: 'Robin',
            avatarUrl: '/test-avatar.png',
            owner: true,
          },
          inboxVersion: 0,
          refreshInbox: () => undefined,
        }}
      >
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
        />
      </SessionProvider>
    </MemoryRouter>,
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

  it('shows thread resolve and comment edit/delete actions from server own flags after GitHub login rename', () => {
    const me = {
      userId: '11111111-1111-4111-8111-111111111111',
      login: 'robin',
      name: 'Robin',
      avatarUrl: '/test-avatar.png',
      owner: false,
    };
    const services = fakeServicesOf(new MemoryStore(me), 'UTC');
    const baseThread = thread({
      author: human('robin-old'),
      own: true,
      comments: [
        {
          id: 'c-own',
          author: human('robin-old'),
          own: true,
          body: '내 이전 댓글',
          createdAt: '2026-10-18T09:00:00Z',
          editedAt: null,
          deleted: false,
        },
        {
          id: 'c-other',
          author: human('robin'),
          own: false,
          body: '동명이인 댓글',
          createdAt: '2026-10-18T09:01:00Z',
          editedAt: null,
          deleted: false,
        },
      ],
    });
    const markup = renderToStaticMarkup(
      <MemoryRouter>
        <ServicesProvider services={services}>
          <SessionProvider
            session={{
              me,
              inboxVersion: 0,
              refreshInbox: () => undefined,
            }}
          >
            <DocumentPermissionContext value="comment">
              <CommentRail
                threads={[baseThread]}
                filter="open"
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
              />
            </DocumentPermissionContext>
          </SessionProvider>
        </ServicesProvider>
      </MemoryRouter>,
    );
    expect(markup).toContain('>해결</button>');
    expect(markup.match(/>수정</g)).toHaveLength(1);
    expect(markup.match(/>삭제</g)).toHaveLength(1);
  });
});
