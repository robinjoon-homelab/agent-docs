// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { HtmlMockup } from './HtmlMockup';
import { savedReferenceFixture } from './savedAssetReferences.fixture';

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

it('waits for its own saved reference, including through failed and unrelated saves', async () => {
  const saved = savedReferenceFixture();
  const source = '<h1>saved HTML</h1>';
  let body = source;
  const options = {
    savedReferences: saved.references,
    htmlSource: (assetId: string) =>
      saved.references.contains(assetId) ? Promise.resolve(body) : Promise.reject(new Error('404')),
    replaceHtml: () => Promise.resolve(),
  };
  render(<HtmlMockup assetId="new" blockId="block00001" title="새 시안" editable options={options} />);
  await act(() => Promise.resolve());
  expect(screen.queryByTitle('새 시안')).toBeNull();
  expect(screen.queryByRole('alert')).toBeNull();
  expect(screen.getByText('문서 저장 후 시안을 불러옵니다.')).toBeDefined();
  await act(async () => {
    saved.notify();
    saved.save(['unrelated']);
    await Promise.resolve();
  });
  expect(screen.queryByTitle('새 시안')).toBeNull();
  await act(async () => {
    saved.save(['unrelated', 'new']);
    await Promise.resolve();
  });
  const frame = await screen.findByTitle<HTMLIFrameElement>('새 시안');
  expect(frame.srcdoc).toContain(source);
  body = '<h1>must not replace the running preview</h1>';
  await act(async () => {
    saved.notify();
    saved.save(['new', 'another']);
    await Promise.resolve();
  });
  expect(screen.getByTitle('새 시안')).toBe(frame);
  expect(frame.srcdoc).toContain(source);
  expect(frame.srcdoc).not.toContain(body);
});

it.each([403, 404])(
  'shows a real source %s after saving without a loading label or iframe',
  async (status) => {
    const saved = savedReferenceFixture(['denied']);
    const options = {
      savedReferences: saved.references,
      htmlSource: () => Promise.reject(new Error(String(status))),
      replaceHtml: () => Promise.resolve(),
    };
    render(<HtmlMockup assetId="denied" blockId="block00001" title="접근 거절" editable options={options} />);
    expect((await screen.findByRole('alert')).textContent).toBe('HTML 원문을 읽지 못했습니다.');
    expect(screen.queryByText('시안 불러오는 중…')).toBeNull();
    expect(screen.queryByTitle('접근 거절')).toBeNull();
  },
);

it('opens an existing logical reference and waits for a replacement without accepting a late old source', async () => {
  const saved = savedReferenceFixture(['original-logical-id']);
  let finishOld: (source: string) => void = () => undefined;
  const old = new Promise<string>((resolve) => {
    finishOld = resolve;
  });
  const options = {
    savedReferences: saved.references,
    htmlSource: (assetId: string) =>
      assetId === 'original-logical-id' ? old : Promise.resolve('<p>replacement</p>'),
    replaceHtml: () => Promise.resolve(),
  };
  const view = render(
    <HtmlMockup assetId="original-logical-id" blockId="same-block" title="시안" editable options={options} />,
  );
  expect(screen.getByText('시안 불러오는 중…')).toBeDefined();
  view.rerender(
    <HtmlMockup assetId="replacement" blockId="same-block" title="시안" editable options={options} />,
  );
  await act(async () => {
    finishOld('<p>late old source</p>');
    await Promise.resolve();
  });
  expect(screen.queryByTitle('시안')).toBeNull();
  await act(async () => {
    saved.save(['replacement']);
    await Promise.resolve();
  });
  expect((await screen.findByTitle<HTMLIFrameElement>('시안')).srcdoc).toContain('<p>replacement</p>');
  expect(screen.getByTitle<HTMLIFrameElement>('시안').srcdoc).not.toContain('late old source');
});

it('ignores a late response after unmount while a new component reads its own source', async () => {
  let finish: (source: string) => void = () => undefined;
  const pending = new Promise<string>((resolve) => {
    finish = resolve;
  });
  const view = render(
    <HtmlMockup
      assetId="old"
      blockId="block"
      title="이전"
      editable
      options={{ htmlSource: () => pending, replaceHtml: () => Promise.resolve() }}
    />,
  );
  view.unmount();
  render(
    <HtmlMockup
      assetId="current"
      blockId="block"
      title="현재"
      editable
      options={{ htmlSource: () => Promise.resolve('<p>current</p>'), replaceHtml: () => Promise.resolve() }}
    />,
  );
  await screen.findByTitle('현재');
  await act(async () => {
    finish('<p>unmounted source</p>');
    await Promise.resolve();
  });
  expect(screen.queryByTitle('이전')).toBeNull();
  expect(screen.getByTitle<HTMLIFrameElement>('현재').srcdoc).toContain('<p>current</p>');
});
