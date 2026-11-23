// @vitest-environment jsdom
import { render, fireEvent, cleanup, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import type { Services } from '../../../../../../config/ServicesContext';
import { ServicesProvider } from '../../../../../../config/ServicesContext';
import type { DesignObject, FlowDesignBlock } from '../../../../../../domain/document/designBlock';
import { useState } from 'react';
import type { DesignAddress } from '../../../../../../domain/flow/designScene';
import { asText } from '../../../../../../domain/flow/designValue';
import { DocumentAssetScope } from '../DocumentAssetScope';
import { MemoryRouter } from 'react-router';
import { inspectDesignCanvas } from './DesignCanvasDiagnostics';
import { DesignCanvas, DesignCanvasView } from './DesignCanvas';

afterEach(cleanup);
const block = (design: DesignObject): FlowDesignBlock => ({
  id: asText(design.id) ?? '',
  type: 'flowDesign',
  design,
});
const root = (children: readonly DesignObject[]) => ({
  kind: 'box',
  id: 'root',
  layout: { mode: 'column' },
  children,
});
const blocks = [
  block({ kind: 'settings', id: 'config', frame: 'phone' }),
  block({
    kind: 'screen',
    id: 'screen',
    root: root([
      {
        kind: 'box',
        id: 'button',
        role: 'button',
        accessibleName: '저장',
        layout: { mode: 'row' },
        children: [{ kind: 'text', id: 'caption', text: '기본 문구' }],
      },
      { kind: 'image', id: 'photo', asset: 'photo-file', alt: '첨부 사진' },
      { kind: 'icon', id: 'caret', icon: { builtin: 'chevron-down' }, ink: '#334455' },
      { kind: 'text', id: 'malicious', text: '<script>unused</script>' },
    ]),
  }),
  block({
    kind: 'state',
    id: 'changed',
    screen: 'screen',
    frame: { width: 400 },
    changes: [{ op: 'set', target: { owner: 'screen', node: 'caption' }, fields: { text: '바뀐 문구' } }],
  }),
  block({
    kind: 'annotation',
    id: 'note',
    screen: 'screen',
    target: { owner: 'screen', node: 'button' },
    name: '버튼',
  }),
];
const services: Partial<Services> = {
  assets: {
    contentUrl: (id, doc) => '/authorized/' + (doc ?? 'global') + '/' + id,
    upload: () => Promise.reject(new Error('read only')),
    importUrl: () => Promise.reject(new Error('read only')),
    htmlSource: () => Promise.reject(new Error('read only')),
    sceneSource: () => Promise.reject(new Error('read only')),
  },
};
it('renders the current state, resolves document-owned assets, and activates the closest annotated frame', () => {
  function InteractiveCanvas() {
    const [selection, select] = useState<DesignAddress | null>(null),
      [annotation, activate] = useState<DesignAddress | null>(null);
    return (
      <>
        <DesignCanvas
          blocks={blocks}
          screen="screen"
          state="changed"
          selection={selection}
          onSelect={select}
          onActivate={activate}
        />
        <output>{annotation?.node}</output>
      </>
    );
  }
  const mounted = render(
    <ServicesProvider services={services as Services}>
      <DocumentAssetScope documentId="doc-id">
        <InteractiveCanvas />
      </DocumentAssetScope>
    </ServicesProvider>,
  );
  expect(mounted.container.querySelector('.design-canvas')?.getAttribute('style')).toContain('400px');
  expect(mounted.container.querySelector('[data-design-node="caret"] path')?.getAttribute('d')).toBe(
    'M4 6l4 4 4-4',
  );
  expect(screen.getByAltText('첨부 사진').getAttribute('src')).toBe('/authorized/doc-id/photo-file');
  expect(screen.getByText('<script>unused</script>')).toBeTruthy();
  expect(mounted.container.querySelector('script')).toBeNull();
  fireEvent.click(screen.getByText('바뀐 문구'));
  expect(
    mounted.container.querySelector('.design-canvas-layer.is-selected')?.getAttribute('data-design-node'),
  ).toBe('caption');
  expect(mounted.container.querySelector('output')?.textContent).toBe('button');
});
it('renders vector paths as SVG drawing elements', () => {
  const drawing = [
    block({
      kind: 'screen',
      id: 's',
      root: root([
        {
          id: 'v',
          kind: 'vector',
          fillRule: 'nonzero',
          contours: [
            {
              id: 'c',
              closed: false,
              vertices: [
                { id: 'a', x: 0, y: 0, out: { x: 5, y: 10 } },
                { id: 'b', x: 30, y: 20, in: { x: -3, y: 2 } },
              ],
            },
          ],
          style: { strokes: [{ id: 's', width: 2, paint: { id: 'f', kind: 'solid', color: '#335577' } }] },
        },
      ]),
    }),
  ];
  const { container } = render(
    <ServicesProvider services={services as Services}>
      <DesignCanvas blocks={drawing} screen="s" />
    </ServicesProvider>,
  );
  expect(container.querySelector('svg path')?.getAttribute('d')).toContain('C');
});

it('renders document-scoped attachments and internal links on a note without application services', () => {
  const note = block({
    kind: 'screen',
    id: 'n',
    frame: 'note',
    description: '[파일](asset:picture) [문서](doc:other)',
  });
  render(
    <MemoryRouter>
      <DesignCanvasView blocks={[note]} screen="n" assetUrl={(id) => '/document-assets/' + id} />
    </MemoryRouter>,
  );
  expect(screen.getByRole('link', { name: '파일' }).getAttribute('href')).toBe('/document-assets/picture');
  expect(screen.getByRole('link', { name: '문서' }).getAttribute('href')).toBe('/d/other');
});

it('registers attachment faces before measuring, records a failed font and detaches it on unmount', async () => {
  let finish: () => void = () => undefined;
  const pending = new Promise<void>((resolve) => {
    finish = resolve;
  });
  class TestFont {
    constructor(
      readonly family: string,
      readonly source: string,
    ) {}
    async load() {
      await pending;
      throw Error('invalid font');
    }
  }
  const fontSet = Object.assign(new Set<TestFont>(), { ready: Promise.resolve() });
  const old = Object.getOwnPropertyDescriptor(document, 'fonts');
  vi.stubGlobal('FontFace', TestFont);
  Object.defineProperty(document, 'fonts', { configurable: true, value: fontSet });
  try {
    const custom = block({
      kind: 'screen',
      id: 's',
      root: root([
        { id: 'text', kind: 'text', text: '첨부', style: { text: { font: { asset: 'asset-font' } } } },
      ]),
    });
    const rendered = render(
      <DesignCanvasView blocks={[custom]} screen="s" assetUrl={(id) => '/authorized/' + id} />,
    );
    expect(fontSet.size).toBe(1);
    const el = rendered.container.querySelector<HTMLElement>('.design-canvas');
    if (!el) throw Error('missing canvas');
    const measured = inspectDesignCanvas(el);
    finish();
    expect((await measured).warnings).toContain('첨부 글꼴을 불러오지 못했습니다: asset-font');
    rendered.unmount();
    expect(fontSet.size).toBe(0);
  } finally {
    if (old) Object.defineProperty(document, 'fonts', old);
    else Reflect.deleteProperty(document, 'fonts');
    vi.unstubAllGlobals();
  }
});
