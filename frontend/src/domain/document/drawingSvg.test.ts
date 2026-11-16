import { describe, expect, it } from 'vitest';
import { externalReferences } from './drawingSvg';

describe('externalReferences', () => {
  it('passes an exported drawing with inlined fonts, embedded images, fragments, namespaces and hyperlinks', () => {
    const svg = [
      '<svg version="1.1" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="0 0 10 10">',
      '<defs><style class="style-fonts">@font-face { font-family: Excalifont; src: url(data:font/woff2;base64,d09GMg==); }</style>',
      '<symbol id="image-f1"><image href="data:image/png;base64,iVBORw0KGgo=" width="100%"/></symbol></defs>',
      '<use href="#image-f1" mask="url(#mask-1)"/><a href="https://example.com/plan"><rect width="5" height="5"/></a></svg>',
    ].join('');
    expect(externalReferences(svg)).toEqual([]);
  });

  it('finds a font that fell back to a CDN and any image or stylesheet the SVG would fetch', () => {
    const svg = [
      '<svg xmlns="http://www.w3.org/2000/svg"><defs><style>',
      '@font-face { font-family: Excalifont; src: url(https://esm.sh/@excalidraw/excalidraw@0.18.1/dist/prod/fonts/a.woff2); }',
      '@import "//cdn.test/a.css";</style></defs>',
      "<image xlink:href='http://images.test/a.png'/></svg>",
    ].join('');
    expect(externalReferences(svg)).toEqual([
      'https://esm.sh/@excalidraw/excalidraw@0.18.1/dist/prod/fonts/a.woff2',
      '//cdn.test/a.css',
      'http://images.test/a.png',
    ]);
  });

  it('accepts quoted or spaced data and fragment urls and still finds every other target', () => {
    const local = [
      "<style>.a { src: url('data:font/woff2;base64,AA'); }</style>",
      '<style>.b { fill: url( #gradient ); } .c { fill: url("#gradient"); }</style>',
      '<style>.d { src: URL( "DATA:font/woff2;base64,AA" ); }</style>',
    ];
    expect(externalReferences(`<svg xmlns="http://www.w3.org/2000/svg">${local.join('')}</svg>`)).toEqual([]);
    const outside = [
      "<style>.e { src: url('https://cdn.test/a.woff2'); }",
      '.f { src: url( //cdn.test/b.woff2 ); } .g { fill: url(images/c.png); }',
      '.h { background: image-set("https://cdn.test/e.png" 1x); } .i { src: url("https://cdn.test/d.woff2</style>',
    ];
    expect(externalReferences(`<svg xmlns="http://www.w3.org/2000/svg">${outside.join('')}</svg>`)).toEqual([
      'https://cdn.test/a.woff2',
      '//cdn.test/b.woff2',
      'images/c.png',
      'https://cdn.test/d.woff2</style></svg>',
      'image-set(',
    ]);
  });
});
