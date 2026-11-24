import { useEffect, useRef, type ReactNode } from 'react';
import type { Anchor } from '../../../../../../domain/thread/thread';
import { FlowIcon } from '../FlowIcon';
import { CommentEditor } from '../CommentEditor';

export function DesignSidePanel({
  tab,
  edit,
  onTab,
  onCollapse,
  onDocumentComment,
  openCount,
  allowComment,
  children,
  composerAnchor,
  composerLabel,
  composerFocus,
  mentionLogins,
  onStartComment,
}: {
  tab: 'details' | 'comments';
  edit: boolean;
  onTab: (tab: 'details' | 'comments') => void;
  onCollapse: () => void;
  onDocumentComment: () => void;
  openCount: number;
  allowComment: boolean;
  children: ReactNode;
  composerAnchor: Anchor;
  composerLabel: string;
  composerFocus: number;
  mentionLogins: readonly string[];
  onStartComment?: (anchor: Anchor, body: string) => Promise<boolean>;
}) {
  const composer = useRef<HTMLFieldSetElement>(null),
    lastFocus = useRef(composerFocus);
  useEffect(() => {
    if (lastFocus.current !== composerFocus) {
      composer.current?.querySelector('textarea')?.focus();
      lastFocus.current = composerFocus;
    }
  }, [composerFocus]);
  return (
    <aside className="flow-properties" aria-label="설명과 댓글">
      <div className="flow-panel-header">
        <div className="flow-segmented">
          <button type="button" aria-pressed={tab === 'details'} onClick={() => onTab('details')}>
            {edit ? '속성' : '설명'}
          </button>
          <button type="button" aria-pressed={tab === 'comments'} onClick={() => onTab('comments')}>
            댓글 {openCount}
          </button>
        </div>
        <button
          type="button"
          className="flow-panel-icon"
          disabled={!allowComment}
          aria-label="문서에 댓글"
          onClick={onDocumentComment}
        >
          <FlowIcon name="plus" />
        </button>
        <button type="button" className="flow-panel-icon" aria-label="패널 접기" onClick={onCollapse}>
          <FlowIcon name="panel" />
        </button>
      </div>
      <div className="flow-panel-body">{children}</div>
      <fieldset ref={composer} className="flow-composer" disabled={!allowComment || !onStartComment}>
        <label>{composerLabel}</label>
        <CommentEditor
          key={JSON.stringify(composerAnchor)}
          initial=""
          mentionLogins={mentionLogins}
          submitLabel="댓글"
          placeholder={
            allowComment ? '댓글을 남기세요. @로 멘션 (⌘Enter)' : '읽기 권한으로는 댓글을 달 수 없습니다.'
          }
          onSubmit={(body) => onStartComment?.(composerAnchor, body) ?? Promise.resolve(false)}
        />
      </fieldset>
    </aside>
  );
}
