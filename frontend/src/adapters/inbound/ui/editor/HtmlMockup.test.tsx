// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { HtmlMockup } from './HtmlMockup';

afterEach(cleanup);

describe('HTML mockup frame', () => {
  it('isolates source scripts and switches device width without touching the source', async () => {
    const source = '<script>document.body.dataset.preview = "yes"</script><h1>시안</h1>';
    const options = { htmlSource: () => Promise.resolve(source), replaceHtml: () => Promise.resolve() };
    render(<HtmlMockup assetId="a" blockId="block00001" title="화면" editable options={options} />);
    await screen.findByTitle('화면');
    const frame = screen.getByTitle<HTMLIFrameElement>('화면');
    expect(frame.getAttribute('sandbox')).toBe('allow-scripts');
    expect(frame.srcdoc).toContain(source);
    fireEvent.click(screen.getByRole('button', { name: '모바일' }));
    expect(frame.style.width).toBe('375px');
    fireEvent.click(screen.getByRole('button', { name: 'HTML 원문' }));
    expect(document.querySelector('.html-mockup-source')?.textContent).toBe(source);
    await act(() => Promise.resolve());
  });
});
