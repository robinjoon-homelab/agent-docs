// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { thread } from '../../../../test-support/fixtures';
import { threadAnchorElement } from './threadAnchors';

describe('thread anchor positions without a highlight', () => {
  it('uses the anchor block for an unhighlighted resolved thread and the exact highlight once selected', () => {
    const root = document.createElement('div');
    root.innerHTML = '<p data-block-id="aaaaaaaaaa">hello</p>';
    const resolved = thread({ status: 'resolved' });
    expect(threadAnchorElement(root, [resolved], resolved.id)).toBe(root.firstElementChild);

    root.innerHTML = '<p data-block-id="aaaaaaaaaa"><span data-thread-id="th-1">hello</span></p>';
    expect(threadAnchorElement(root, [resolved], resolved.id)).toBe(root.querySelector('span'));
  });

  it('has no anchor element for unknown threads, missing blocks, or document comments', () => {
    const root = document.createElement('div');
    const resolved = thread({ status: 'resolved' });
    expect(threadAnchorElement(root, [resolved], resolved.id)).toBeNull();
    expect(threadAnchorElement(root, [resolved], 'unknown')).toBeNull();
    expect(threadAnchorElement(root, [thread({ anchor: { kind: 'document' } })], 'th-1')).toBeNull();
  });
});
