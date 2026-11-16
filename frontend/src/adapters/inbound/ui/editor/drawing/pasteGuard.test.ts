// @vitest-environment jsdom
import { expect, it } from 'vitest';
import { hasRemoteImage } from './pasteGuard';

it('finds an image address even when character references or odd quoting hide it from the raw HTML', () => {
  const remote = [
    '<img src="https://cdn.test/a.png">',
    '<p>text</p><img alt="x" src=//cdn.test/b.png>',
    '<img src="h&#116;tps://cdn.test/c.png">',
    '<img src="&#104;ttps://cdn.test/d.png">',
    '<img\nsrc =\n"HTTPS://cdn.test/e.png">',
  ];

  expect(remote.filter((html) => !hasRemoteImage(html))).toEqual([]);
});

it('lets pasted HTML through when its images are inline data or it has none', () => {
  const local = [
    '',
    '<p>plain text</p>',
    '<img src="data:image/png;base64,iVBORw0KGgo=">',
    '<img alt="no source">',
  ];

  expect(local.filter((html) => hasRemoteImage(html))).toEqual([]);
});
