// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { pastedImageUrls } from './pastedImages';

describe('pastedImageUrls', () => {
  it('takes web images in page order once each, and leaves out this editor’s own and relative ones', () => {
    const html = `
      <p>Intro</p>
      <img src="https://example.com/a.png" alt="A">
      <figure data-asset-id="as1"><img src="/api/assets/as1/content"></figure>
      <p><img src="http://cdn.example.com/b.webp"><img src="https://example.com/a.png"></p>
      <img src="data:image/png;base64,AAAA">`;

    expect(pastedImageUrls(html)).toEqual(['https://example.com/a.png', 'http://cdn.example.com/b.webp']);
    expect(pastedImageUrls('<p>no images</p>')).toEqual([]);
  });
});
