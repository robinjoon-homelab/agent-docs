// @vitest-environment jsdom
import { render, screen, cleanup } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { InlineDiff } from './InlineDiff';

afterEach(cleanup);

describe('history inline changes', () => {
  it('renders deleted and inserted visible text without markdown syntax', () => {
    render(
      <InlineDiff
        before={{ id: 'p', type: 'paragraph', text: '**오늘** 배포' }}
        after={{ id: 'p', type: 'paragraph', text: '**내일** 배포' }}
      />,
    );
    const diff = screen.getByLabelText('본문 글자 변경');
    expect(diff.querySelector('del')?.textContent).toBe('오늘');
    expect(diff.querySelector('ins')?.textContent).toBe('내일');
    expect(diff.textContent).toBe('오늘내일 배포');
  });

  it('leaves table diffs and formatting-only changes with the existing block renderer', () => {
    const { container } = render(
      <InlineDiff
        before={{ id: 'p', type: 'paragraph', text: '**같음**' }}
        after={{ id: 'p', type: 'paragraph', text: '같음' }}
      />,
    );
    expect(container.textContent).toBe('');
  });
});
